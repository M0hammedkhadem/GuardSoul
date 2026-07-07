package com.agon.app.blocking

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.GuardianApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

class ShortstopEngine(private val host: AccessibilityService) {

    @Volatile var blockOverlayHandler: BlockOverlayHandler? = null
    @Volatile var feedBlockOverlay: FeedBlockOverlay? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val matcher = PatternMatcher()

    @Volatile private var cachedShieldActive = false
    @Volatile private var cachedYoutubeMode = "off"
    @Volatile private var cachedFacebookMode = "off"
    @Volatile private var cachedBlockedApps: Set<String> = emptySet()

    private val facebookPackages = setOf("com.facebook.katana", "com.facebook.lite")

    private var whitelist: WhitelistCache? = null

    @Volatile private var blockedReelsSurface: String? = null
    @Volatile private var blockedShortsSurface: String? = null
    @Volatile private var blockedFullSurface: String? = null

    private val lastReelsBlockMs = ConcurrentHashMap<String, Long>()
    private val lastShortsBlockMs = ConcurrentHashMap<String, Long>()
    private val lastFullBlockMs = ConcurrentHashMap<String, Long>()

    private val baseCooldownMs = 2000L
    private val shortsCooldownMs = 1500L
    private val fullBlockCooldownMs = 1000L

    // ----- Escalation system -----
    private val consecutiveAttempts = ConcurrentHashMap<String, Int>()
    private val attemptWindowStart = ConcurrentHashMap<String, Long>()
    private val slidingWindowMs = 30_000L
    private val maxAttemptsBeforeHome = 2
    private val maxAttemptsBeforeTempFullBlock = 3

    private val tempFullBlockUntil = ConcurrentHashMap<String, Long>()
    private val tempFullBlockDurationMs = 30_000L

    private enum class EscalationLevel {
        BACK,
        DOUBLE_BACK,
        BACK_THEN_HOME,
        TEMPORARY_FULL_BLOCK,
        ALREADY_TEMP_BLOCKED,
    }

    fun start() {
        val app = host.applicationContext as? GuardianApp ?: run {
            Timber.e("Shortstop: host application is not GuardianApp")
            return
        }
        whitelist = WhitelistCache(app, serviceScope)

        serviceScope.launch {
            val settings = app.repository.getAppSettings()
            launch { settings.shieldActiveFlow.collect { cachedShieldActive = it } }
            launch { settings.youtubeModeFlow.collect { cachedYoutubeMode = it } }
            launch { settings.facebookModeFlow.collect { cachedFacebookMode = it } }
            launch { settings.blockedAppsFlow.collect { cachedBlockedApps = it } }
        }
    }

    fun stop() {
        serviceScope.cancel()
    }

    fun onAccessibilityEvent(event: AccessibilityEvent, root: AccessibilityNodeInfo?) {
        if (!cachedShieldActive) return

        val pkg = event.packageName?.toString() ?: return
        whitelist?.let { if (it.isAppAllowed(pkg)) return }

        val now = System.currentTimeMillis()

        // Check temporary full block first.
        val tempBlockUntil = tempFullBlockUntil[pkg] ?: 0L
        if (now < tempBlockUntil) {
            if (pkg in facebookPackages) {
                handleTempFullBlock(pkg, now)
            }
            return
        }

        if (isFullBlockRequired(pkg)) {
            handleFullBlock(pkg, now)
            return
        }

        // YouTube Shorts.
        if (pkg == "com.google.android.youtube" && cachedYoutubeMode == "shorts") {
            if (root != null) handleYouTubeShorts(root, pkg, now)
            return
        }

        // Facebook Reels (partial block).
        if (pkg in facebookPackages && cachedFacebookMode == "reels") {
            handleFacebookReels(event, root, pkg, now)
        }
    }

    private fun handleYouTubeShorts(root: AccessibilityNodeInfo, pkg: String, now: Long) {
        val sig = matcher.signatureFor(pkg) ?: return
        val result = matcher.findFeedViewIdWithConfidence(root, pkg, sig)
        if (result != null && result.isReliable) {
            Timber.d("[Shortstop] YouTube Shorts matched: ${result.token} (${result.method.label}, conf=${result.confidence})")
            handleFeedBlock(pkg, now)
        } else {
            blockedShortsSurface = null
        }
    }

    private fun handleFacebookReels(event: AccessibilityEvent, root: AccessibilityNodeInfo?, pkg: String, now: Long) {
        val sig = matcher.signatureFor(pkg) ?: return

        // Phase 1: Check window title (fast, no tree traversal needed).
        val titleResult = matcher.detectFromWindowTitle(event, pkg, sig)
        Timber.d("[Shortstop] FB window title check: ${getWindowTitleSafe(event)} -> $titleResult")

        // Phase 2: Check view tree (if root available).
        val treeResult = if (root != null) {
            matcher.findFeedViewIdWithConfidence(root, pkg, sig)
        } else null

        // Combine results: use the highest confidence.
        val bestResult = listOfNotNull(titleResult, treeResult).maxByOrNull { it.confidence }

        if (bestResult != null && bestResult.isReliable) {
            Timber.d("[Shortstop] FB Reels detected: ${bestResult.token} (${bestResult.method.label}, conf=${bestResult.confidence})")
            handleFacebookReelsBlock(now, pkg, bestResult.token)
            return
        }

        // Edge case: two medium signals can combine to cross threshold.
        if (treeResult != null && titleResult != null) {
            val combined = treeResult.confidence + titleResult.confidence * (1f - treeResult.confidence)
            if (combined >= 0.7f) {
                Timber.d("[Shortstop] FB Reels combined detection: conf=$combined")
                handleFacebookReelsBlock(now, pkg, "combined")
                return
            }
        }

        // Clear blocked surface after delay (only if no new block occurred).
        if (blockedReelsSurface == pkg) {
            val priorLast = lastReelsBlockMs[pkg] ?: 0L
            serviceScope.launch {
                delay(1200)
                val currentLast = lastReelsBlockMs[pkg] ?: 0L
                if (currentLast <= priorLast) {
                    blockedReelsSurface = null
                    Timber.d("[Shortstop] FB Reels surface cleared for $pkg")
                }
            }
        }
    }

    private fun handleFacebookReelsBlock(now: Long, pkg: String, token: String) {
        if (blockedReelsSurface == pkg) return

        val last = lastReelsBlockMs[pkg] ?: 0L
        val adaptiveCooldown = if (consecutiveAttempts[pkg] ?: 0 > 0) {
            baseCooldownMs * ((consecutiveAttempts[pkg] ?: 1).coerceAtMost(5))
        } else baseCooldownMs

        if (now - last < adaptiveCooldown) return

        blockedReelsSurface = pkg
        lastReelsBlockMs[pkg] = now

        val label = if (pkg == "com.facebook.lite") "Facebook Lite Reels" else "Facebook Reels"

        val level = escalationLevelFor(pkg, now)
        Timber.w("Shortstop: $label Block level=$level token=$token attempts=${consecutiveAttempts[pkg]}")

        when (level) {
            EscalationLevel.BACK -> {
                host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
            EscalationLevel.DOUBLE_BACK -> {
                host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                serviceScope.launch {
                    delay(150)
                    host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }
            }
            EscalationLevel.BACK_THEN_HOME -> {
                host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                serviceScope.launch {
                    delay(200)
                    host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }
            }
            EscalationLevel.TEMPORARY_FULL_BLOCK -> {
                tempFullBlockUntil[pkg] = now + tempFullBlockDurationMs
                host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                blockOverlayHandler?.showBlockOverlay(pkg)
                serviceScope.launch {
                    delay(tempFullBlockDurationMs)
                    tempFullBlockUntil.remove(pkg)
                    Timber.d("[Shortstop] FB temporary full block expired for $pkg")
                }
            }
            EscalationLevel.ALREADY_TEMP_BLOCKED -> return
        }

        feedBlockOverlay?.show()

        serviceScope.launch {
            runCatching {
                (host.applicationContext as GuardianApp)
                    .repository.recordBlock(pkg, label, if (level == EscalationLevel.TEMPORARY_FULL_BLOCK) "temp_full_block" else "social_feed")
            }.onFailure { Timber.e(it, "recordBlock failed") }
        }
    }

    private fun escalationLevelFor(pkg: String, now: Long): EscalationLevel {
        // Check if temporarily full-blocked.
        if (now < (tempFullBlockUntil[pkg] ?: 0L)) {
            return EscalationLevel.ALREADY_TEMP_BLOCKED
        }

        // Reset attempt counter if window expired.
        val windowStart = attemptWindowStart[pkg] ?: now
        if (now - windowStart > slidingWindowMs) {
            consecutiveAttempts[pkg] = 0
            attemptWindowStart[pkg] = now
        } else if (attemptWindowStart[pkg] == null) {
            attemptWindowStart[pkg] = now
        }

        val attempts = (consecutiveAttempts[pkg] ?: 0) + 1
        consecutiveAttempts[pkg] = attempts

        return when {
            attempts > maxAttemptsBeforeTempFullBlock -> {
                consecutiveAttempts[pkg] = 0
                EscalationLevel.TEMPORARY_FULL_BLOCK
            }
            attempts > maxAttemptsBeforeHome -> EscalationLevel.BACK_THEN_HOME
            attempts > 1 -> EscalationLevel.DOUBLE_BACK
            else -> EscalationLevel.BACK
        }
    }

    private fun handleTempFullBlock(pkg: String, now: Long) {
        if (blockedFullSurface == pkg) return

        val last = lastFullBlockMs[pkg] ?: 0L
        if (now - last < fullBlockCooldownMs) return

        blockedFullSurface = pkg
        lastFullBlockMs[pkg] = now

        Timber.w("Shortstop: Temp Full Block for $pkg")
        host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        blockOverlayHandler?.showBlockOverlay(pkg)

        serviceScope.launch {
            delay(fullBlockCooldownMs)
            blockedFullSurface = null
        }
    }

    private fun isFullBlockRequired(pkg: String): Boolean {
        if (cachedBlockedApps.contains(pkg)) return true
        if (pkg == "com.google.android.youtube" && cachedYoutubeMode == "full") return true
        if (pkg in facebookPackages && cachedFacebookMode == "full") return true
        return false
    }

    private fun handleFullBlock(pkg: String, now: Long) {
        if (blockedFullSurface == pkg) return

        val last = lastFullBlockMs[pkg] ?: 0L
        if (now - last < fullBlockCooldownMs) return

        blockedFullSurface = pkg
        lastFullBlockMs[pkg] = now

        Timber.w("Shortstop: Full Block for $pkg")
        blockOverlayHandler?.showBlockOverlay(pkg)

        serviceScope.launch {
            runCatching {
                (host.applicationContext as GuardianApp)
                    .repository.recordBlock(pkg, "Full Block", "app_list_block")
            }.onFailure { Timber.e(it, "recordBlock failed") }
        }

        serviceScope.launch {
            delay(fullBlockCooldownMs)
            blockedFullSurface = null
        }
    }

    private fun handleFeedBlock(pkg: String, now: Long) {
        if (blockedShortsSurface == pkg) return

        val last = lastShortsBlockMs[pkg] ?: 0L
        if (now - last < shortsCooldownMs) return

        blockedShortsSurface = pkg
        lastShortsBlockMs[pkg] = now

        Timber.w("Shortstop: YouTube Shorts Block")

        host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        feedBlockOverlay?.show()

        serviceScope.launch {
            runCatching {
                (host.applicationContext as GuardianApp)
                    .repository.recordBlock(pkg, "YouTube Shorts", "social_feed")
            }.onFailure { Timber.e(it, "recordBlock failed") }
        }
    }
}

fun interface BlockOverlayHandler {
    fun showBlockOverlay(pkg: String)
}

private fun getWindowTitleSafe(event: AccessibilityEvent): String? {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return null
    return try {
        val getter = AccessibilityEvent::class.java.getMethod("getWindowTitle")
        getter.invoke(event)?.toString()
    } catch (_: Exception) {
        null
    }
}
