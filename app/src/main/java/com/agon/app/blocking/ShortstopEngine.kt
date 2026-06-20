package com.agon.app.blocking

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.GuardianApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/** Called when a full app block is triggered */
fun interface BlockOverlayHandler {
    fun showBlockOverlay(pkg: String)
}

class ShortstopEngine(private val host: AccessibilityService) {

    @Volatile var blockOverlayHandler: BlockOverlayHandler? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val matcher = PatternMatcher()
    private val tempBan = TempBanManager.getInstance(host.applicationContext)

    // Cached YouTube signature for hot path — avoids map lookup on every event
    private val youtubeSig: PatternMatcher.Signature? = matcher.signatureFor("com.google.android.youtube")

    private var ruleEngine = RuleEngine()
    @Volatile private var currentConfig = RuleEngine.Config()

    private val lastRedirectMs = ConcurrentHashMap<String, Long>()
    private val feedBlockCooldownMs = 500L
    private val youtubeFeedBlockCooldownMs = 200L  // aggressive for YouTube Shorts
    private val fullBlockCooldownMs = 1500L

    // Throttle WINDOW_CONTENT_CHANGED per package (fires rapidly in YouTube/IG)
    private val lastContentChangeMs = ConcurrentHashMap<String, Long>()
    private val contentChangeThrottleMs = 200L

    private var activeTrackingJob: Job? = null
    private var lastTrackedPkg: String? = null

    // Guardian Pattern: periodic re-verification every 1000ms
    private val guardianHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var guardianRunnable: Runnable? = null
    private var isGuardianRunning = false

    @Volatile private var cachedShieldActive = false
    @Volatile private var cachedInstagramMode = "off"
    @Volatile private var cachedYoutubeMode = "off"
    @Volatile private var cachedFacebookMode = "off"
    @Volatile private var cachedSnapchatBlocked = false
    @Volatile private var cachedTwitterBlocked = false
    @Volatile private var cachedTiktokBlocked = false
    @Volatile private var cachedBlockedApps: Set<String> = emptySet()

    fun start() {
        serviceScope.launch {
            val app = host.applicationContext as GuardianApp
            val settings = app.repository.getAppSettings()

            // Initialize from current values to avoid race condition
            cachedShieldActive = settings.shieldActiveFlow.first()
            cachedInstagramMode = settings.instagramModeFlow.first()
            cachedYoutubeMode = settings.youtubeModeFlow.first()
            cachedFacebookMode = settings.facebookModeFlow.first()
            cachedSnapchatBlocked = settings.socialSnapchatFlow.first()
            cachedTwitterBlocked = settings.socialTwitterFlow.first()
            cachedTiktokBlocked = settings.socialTiktokFlow.first()
            cachedBlockedApps = settings.blockedAppsFlow.first()
            Timber.d("ShortstopEngine initialized: shield=$cachedShieldActive, tiktok=$cachedTiktokBlocked, blockedApps=${cachedBlockedApps.size}")

            launch { settings.shieldActiveFlow.collect { cachedShieldActive = it } }
            launch { settings.instagramModeFlow.collect { cachedInstagramMode = it } }
            launch { settings.youtubeModeFlow.collect { cachedYoutubeMode = it } }
            launch { settings.facebookModeFlow.collect { cachedFacebookMode = it } }
            launch { settings.socialSnapchatFlow.collect { cachedSnapchatBlocked = it } }
            launch { settings.socialTwitterFlow.collect { cachedTwitterBlocked = it } }
            launch { settings.socialTiktokFlow.collect { cachedTiktokBlocked = it } }
            launch { settings.blockedAppsFlow.collect { cachedBlockedApps = it } }

            launch {
                combine(
                    settings.shortstopDailyQuotaExceededFlow,
                    settings.shortstopBreakActiveFlow,
                    settings.shortstopMinutesSpentTodayFlow,
                    settings.shortstopDailyQuotaMinutesFlow,
                    settings.shortstopBreakIntervalMinutesFlow,
                    settings.shortstopBreakLengthMinutesFlow,
                    settings.shortstopBlockedHourActiveFlow
                ) { flows: Array<Any> ->
                    RuleEngine.Config(
                        dailyQuotaExceeded = flows[0] as Boolean,
                        breakActive = flows[1] as Boolean,
                        minutesSpentToday = flows[2] as Int,
                        dailyQuotaMinutes = flows[3] as Int,
                        breakIntervalMinutes = flows[4] as Int,
                        breakLengthMinutes = flows[5] as Int,
                        blockedHourActive = flows[6] as Boolean
                    )
                }.collect {
                    currentConfig = it
                    ruleEngine = RuleEngine(it)
                }
            }

            // Start Guardian Pattern: periodic re-verification every 1000ms
            startGuardianPattern()
        }
    }

    /** Fast path: full app block using only package name — no rootInActiveWindow needed. */
    fun tryFullBlock(pkg: String): Boolean {
        if (!cachedShieldActive) {
            Timber.d("tryFullBlock: shield inactive, skip $pkg")
            return false
        }
        // Check temp ban first — if app is in cooldown, block immediately
        if (tempBan.isInCooldown(pkg)) {
            Timber.w("tryFullBlock: $pkg is in TEMP BAN cooldown, blocking immediately")
            redirectToHome(pkg, "temp_ban_cooldown")
            return true
        }
        // Facebook Reels now handled by dedicated FacebookReelsEngine
        if (pkg == "com.facebook.katana" || pkg == "com.facebook.lite") return false

        if (matcher.signatureFor(pkg) == null) {
            if (isFullBlockRequired(pkg)) {
                Timber.d("tryFullBlock: block $pkg (no signature)")
                redirectToHome(pkg, "full_block_window_change")
                return true
            }
            Timber.d("tryFullBlock: no block needed for $pkg (no signature)")
            return false
        }
        if (isFullBlockRequired(pkg)) {
            Timber.d("tryFullBlock: block $pkg (has signature)")
            redirectToHome(pkg, "full_block")
            return true
        }
        Timber.d("tryFullBlock: feed-only $pkg, not full-blocked")
        return false
    }

    fun onAccessibilityEvent(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo?,
        sourceIsEventSource: Boolean = false
    ) {
        if (!cachedShieldActive) return
        val pkg = event.packageName?.toString() ?: return
        // Check temp ban for any targeted package
        if (tempBan.isInCooldown(pkg)) {
            Timber.w("Shortstop: $pkg is in TEMP BAN, forcing HOME")
            redirectToHome(pkg, "temp_ban_feed")
            return
        }
        // Facebook Reels now handled by dedicated FacebookReelsEngine
        if (pkg == "com.facebook.katana" || pkg == "com.facebook.lite") return
        val now = System.currentTimeMillis()
        val eventType = event.eventType

        // Fast path: VIEW_CLICKED — user tapped a Shorts thumbnail/tab. Check event.source directly.
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val source = event.source
            if (source != null) {
                // Fast pre-check: YouTube Shorts tokens on the clicked node itself
                if (pkg == "com.google.android.youtube" && cachedYoutubeMode == "shorts" && !isFullBlockRequired(pkg)) {
                    if (hasYouTubeShortsToken(source)) {
                        if (lastTrackedPkg != pkg) startQuotaTracking(pkg)
                        handleFeedBlock(pkg, now)
                        return
                    }
                    // Also walk up to 2 ancestors (Shorts player often parent/sibling of thumbnail)
                    var ancestor = source.parent
                    var depth = 0
                    while (ancestor != null && depth < 2) {
                        if (hasYouTubeShortsToken(ancestor)) {
                            if (lastTrackedPkg != pkg) startQuotaTracking(pkg)
                            handleFeedBlock(pkg, now)
                            return
                        }
                        ancestor = ancestor.parent
                        depth++
                    }
                }

                // General case for other curated packages
                val sig = matcher.signatureFor(pkg)
                if (sig != null && isFeedBlockingEnabled(pkg) && !isFullBlockRequired(pkg)) {
                    // Check clicked node + up to 2 ancestors
                    if (matcher.findFeedViewId(source, pkg, sig) != null) {
                        if (lastTrackedPkg != pkg) startQuotaTracking(pkg)
                        handleFeedBlock(pkg, now)
                        return
                    }
                    var ancestor = source.parent
                    var depth = 0
                    while (ancestor != null && depth < 2) {
                        if (matcher.findFeedViewId(ancestor, pkg, sig) != null) {
                            if (lastTrackedPkg != pkg) startQuotaTracking(pkg)
                            handleFeedBlock(pkg, now)
                            return
                        }
                        ancestor = ancestor.parent
                        depth++
                    }
                }
            }
            // Also handle full-block check for clicks on full-blocked apps
            if (isFullBlockRequired(pkg)) {
                redirectToHome(pkg, "full_block_click")
                return
            }
            return
        }

        val sig = matcher.signatureFor(pkg)
        if (sig == null) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                handleFullBlockCheck(pkg, now)
            }
            return
        }

        if (isFullBlockRequired(pkg)) {
            redirectToHome(pkg, "full_block")
            return
        }

        if (!isFeedBlockingEnabled(pkg)) return

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (root == null) return
            // Smart Ignore: Skip Instagram Reels blocking if user is in DMs
            if (pkg.startsWith("com.instagram") && isInInstagramDmsContext(root, pkg)) {
                Timber.d("Shortstop: Skipping IG Reels block - DM context detected")
                return
            }
            // YouTube-specific fast pre-check: scan for Shorts tokens before full search
            val onFeed = if (pkg == "com.google.android.youtube" && cachedYoutubeMode == "shorts") {
                hasAnyYouTubeShortsToken(root) || matcher.findFeedViewId(root, pkg, sig) != null
            } else {
                matcher.findFeedViewId(root, pkg, sig) != null
            }
            if (onFeed) {
                if (lastTrackedPkg != pkg) startQuotaTracking(pkg)
                handleFeedBlock(pkg, now)
            } else if (lastTrackedPkg == pkg) {
                stopQuotaTracking()
            }
        } else if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Throttle content changes — YouTube/IG fire these very frequently
            val last = lastContentChangeMs[pkg] ?: 0L
            if (now - last < contentChangeThrottleMs) return
            lastContentChangeMs[pkg] = now

            if (root == null) return
            // Smart Ignore: Skip Instagram Reels blocking if user is in DMs
            if (pkg.startsWith("com.instagram") && isInInstagramDmsContext(root, pkg)) {
                Timber.d("Shortstop: Skipping IG Reels block content change - DM context")
                return
            }
            val onFeed = matcher.findFeedViewId(root, pkg, sig) != null
            if (onFeed) {
                if (lastTrackedPkg != pkg) startQuotaTracking(pkg)
                handleFeedBlock(pkg, now)
            } else if (lastTrackedPkg == pkg) {
                stopQuotaTracking()
            }
        }
    }

    private fun handleFullBlockCheck(pkg: String, now: Long) {
        if (isFullBlockRequired(pkg)) {
            redirectToHome(pkg, "full_block_window_change")
        }
    }

    private fun isFeedBlockingEnabled(pkg: String): Boolean = when {
        pkg.startsWith("com.instagram") -> cachedInstagramMode == "reels"
        pkg == "com.google.android.youtube" -> cachedYoutubeMode == "shorts"
        pkg.startsWith("com.facebook") -> cachedFacebookMode == "reels"
        pkg == "com.snapchat.android" -> cachedSnapchatBlocked
        pkg == "com.twitter.android" || pkg == "com.x.android" -> cachedTwitterBlocked
        pkg.startsWith("com.zhiliaoapp") || pkg.startsWith("com.ss.android") -> cachedTiktokBlocked
        else -> false
    }

    private fun isFullBlockRequired(pkg: String): Boolean {
        if (cachedBlockedApps.contains(pkg)) return true
        return when {
            pkg == "com.snapchat.android" -> cachedSnapchatBlocked
            pkg == "com.twitter.android" || pkg == "com.x.android" -> cachedTwitterBlocked
            pkg.startsWith("com.zhiliaoapp") || pkg.startsWith("com.ss.android") -> cachedTiktokBlocked
            pkg == "com.google.android.youtube" && cachedYoutubeMode == "full" -> true
            pkg.startsWith("com.facebook") && cachedFacebookMode == "full" -> true
            pkg.startsWith("com.instagram") && cachedInstagramMode == "full" -> true
            else -> false
        }
    }

    private fun handleFeedBlock(pkg: String, now: Long) {
        val last = lastRedirectMs[pkg] ?: 0L
        val cooldown = if (pkg == "com.google.android.youtube") youtubeFeedBlockCooldownMs else feedBlockCooldownMs
        if (now - last < cooldown) return
        lastRedirectMs[pkg] = now

        val verdict = ruleEngine.evaluate(now)
        host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        val reason = if (verdict.shouldBlock) verdict.message else "permanent"
        Timber.d("Shortstop: BACK from $pkg — $reason")

        // Record strike for temp-ban logic
        tempBan.recordStrike(pkg)

        serviceScope.launch {
            val app = host.applicationContext as GuardianApp
            app.repository.recordBlock(pkg, matcher.surfaceFor(pkg).label, "shortstop_feed")
        }
    }

    private fun redirectToHome(pkg: String, reason: String) {
        val now = System.currentTimeMillis()
        val last = lastRedirectMs[pkg] ?: 0L
        if (now - last < fullBlockCooldownMs) {
            Timber.d("redirectToHome: cooldown active for $pkg (${now - last}ms ago)")
            return
        }
        lastRedirectMs[pkg] = now

        val handler = blockOverlayHandler
        if (handler != null) {
            Timber.d("redirectToHome: calling overlay handler for $pkg")
            handler.showBlockOverlay(pkg)
        } else {
            Timber.d("redirectToHome: no handler, HOME directly for $pkg")
            host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
        Timber.w("Shortstop: block $pkg ($reason)")

        // Record strike for temp-ban logic
        tempBan.recordStrike(pkg)

        serviceScope.launch {
            val app = host.applicationContext as GuardianApp
            app.repository.recordBlock(pkg, "full_block", "shortstop_full")
        }
    }

    private fun startQuotaTracking(pkg: String) {
        lastTrackedPkg = pkg
        activeTrackingJob?.cancel()
        activeTrackingJob = serviceScope.launch {
            val settings = (host.applicationContext as GuardianApp).repository.getAppSettings()
            while (isActive && lastTrackedPkg == pkg) {
                delay(60_000L)
                val spent = settings.shortstopMinutesSpentTodayFlow.first()
                settings.setShortstopMinutesSpentToday(spent + 1)

                if (ruleEngine.shouldForceBreak(spent + 1)) {
                    settings.setShortstopBreakActive(true)
                    settings.setShortstopBreakEndsAt(
                        System.currentTimeMillis() + currentConfig.breakLengthMinutes * 60_000L
                    )
                }
                if (ruleEngine.quotaExceeded(spent + 1)) {
                    settings.setShortstopDailyQuotaExceeded(true)
                }
            }
        }
    }

    private fun stopQuotaTracking() {
        activeTrackingJob?.cancel()
        activeTrackingJob = null
        lastTrackedPkg = null
    }

    /** Ultra-fast YouTube Shorts token check — scans viewIdResourceName for known Shorts tokens. */
    private fun hasYouTubeShortsToken(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val viewId = node.viewIdResourceName
        if (viewId == null) return false
        return viewId.contains("reel_watch_fragment_root") ||
               viewId.contains("shorts_player") ||
               viewId.contains("reel_player") ||
               viewId.contains("shorts_video_player_view") ||
               viewId.contains("reels_player") ||
               viewId.contains("reel_recycler") ||
               viewId.contains("reel_player_page_controller")
    }

    /** Scans the entire tree for any YouTube Shorts token — fast pre-check before full search. */
    private fun hasAnyYouTubeShortsToken(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (hasYouTubeShortsToken(node)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (hasAnyYouTubeShortsToken(child)) {
                    child.recycle()
                    return true
                }
                child.recycle()
            }
        }
        return false
    }

    // ─── Guardian Pattern: Periodic Re-verification (every 1000ms) ─────────

    private fun startGuardianPattern() {
        if (isGuardianRunning) return
        isGuardianRunning = true
        
        guardianRunnable = object : Runnable {
            override fun run() {
                if (!isGuardianRunning) return
                
                serviceScope.launch {
                    checkForegroundApp()
                }
                
                guardianHandler.postDelayed(this, 1000L)
            }
        }
        
        guardianHandler.post(guardianRunnable!!)
        Timber.d("Guardian Pattern started: periodic app verification every 1000ms")
    }

    private fun stopGuardianPattern() {
        isGuardianRunning = false
        guardianRunnable?.let { guardianHandler.removeCallbacks(it) }
        guardianRunnable = null
        Timber.d("Guardian Pattern stopped")
    }

    /**
     * Guardian Pattern: Check current foreground app against blocked list.
     * This runs every 1000ms to catch apps that slip through event-based detection.
     * Uses rootInActiveWindow package (reliable on API 21+) instead of the
     * deprecated ActivityManager.getRunningTasks() which only returns own tasks on API 29+.
     */
    private fun checkForegroundApp() {
        if (!cachedShieldActive) return
        if (cachedBlockedApps.isEmpty()) return

        try {
            val root = host.rootInActiveWindow ?: return
            val topPkg = root.packageName?.toString()
            try { root.recycle() } catch (_: Exception) {}
            if (topPkg != null && cachedBlockedApps.contains(topPkg)) {
                Timber.w("Guardian Pattern: blocked app $topPkg detected in foreground, blocking")
                redirectToHome(topPkg, "guardian_pattern")
            }
        } catch (e: Exception) {
            Timber.w(e, "Guardian Pattern check failed")
        }

        // Also cleanup expired temp bans periodically
        tempBan.cleanupExpired()
    }

    private fun isInInstagramDmsContext(root: AccessibilityNodeInfo?, pkg: String): Boolean {
        if (root == null || !pkg.startsWith("com.instagram")) return false
        // Instagram DM indicators (view IDs that indicate conversation/DM context)
        val dmViewIds = listOf(
            "direct_container", "thread_container", "message_composer",
            "direct_thread", "inbox", "message_input", "composer_edit_text",
            "direct_message_list", "thread_title", "message_bubble"
        )
        for (viewId in dmViewIds) {
            val matches = root.findAccessibilityNodeInfosByViewId("$pkg:id/$viewId")
            if (matches.isNotEmpty()) {
                matches.forEach { it.recycle() }
                return true
            }
        }
        // Fallback: check for content descriptions indicating DM context
        fun checkNode(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (contentDesc.contains("direct message") ||
                contentDesc.contains("dm") ||
                contentDesc.contains("chat") ||
                contentDesc.contains("conversation") ||
                contentDesc.contains("message") && contentDesc.contains("thread")) {
                return true
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (checkNode(child)) {
                    child.recycle()
                    return true
                }
                child.recycle()
            }
            return false
        }
        return checkNode(root)
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────

    fun stop() {
        serviceScope.cancel()
        stopQuotaTracking()
        stopGuardianPattern()
    }

    fun onInterrupt() = Unit
}
