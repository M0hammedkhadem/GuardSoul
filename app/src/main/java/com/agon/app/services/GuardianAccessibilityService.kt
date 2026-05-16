package com.agon.app.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.data.GuardianRepository
import com.agon.app.data.GuardianState
import com.agon.app.ui.screens.BlockActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private fun CharSequence.containsAny(vararg terms: String): Boolean =
    terms.any { this.contains(it, ignoreCase = true) }

class GuardianAccessibilityService : AccessibilityService() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var repository: GuardianRepository
    private var currentState: GuardianState = GuardianState()

    private var debounceJob: Job? = null
    private val FULL_BLOCK_COOLDOWN = 300L
    private val TAG = "GuardianService"
    private val fullBlockLastTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var lastYoutubeBlockTime = 0L
    private var lastFacebookBlockTime = 0L
    private val YOUTUBE_BLOCK_INTERVAL_MS = 2000L
    private val FACEBOOK_BLOCK_INTERVAL_MS = 1500L
    private val FACEBOOK_BOOT_GRACE_MS = 3000L
    private val facebookBootTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    companion object {
        val FacebookPackages = setOf(
            "com.facebook.katana",
            "com.facebook.orca",
            "com.facebook.lite",
            "com.facebook.mlite"
        )

        // View IDs for the full-screen Reels player (NOT feed-embedded reels)
        private val REELS_VIEW_IDS = listOf(
            "com.facebook.katana:id/reel_viewer",
            "com.facebook.katana:id/reel_player",
            "com.facebook.katana:id/clips_viewer",
            "com.facebook.katana:id/reel_container",
            "com.facebook.katana:id/clips_container",
            "com.facebook.katana:id/reels_feed"
        )

        // View IDs for the in-feed Reels (should NOT trigger block)
        private val FEED_REELS_VIEW_IDS = listOf(
            "com.facebook.katana:id/reels_in_feed",
            "com.facebook.katana:id/clips_in_feed"
        )

        private val HOME_VIEW_IDS = listOf(
            "com.facebook.katana:id/home_tab",
            "com.facebook.katana:id/tab_home",
            "com.facebook.katana:id/news_feed_tab",
            "com.facebook.katana:id/feed_tab"
        )

        // Confidence Score constants
        // Threshold = 70 ensures at least 2 different Reels indicators must fire,
        // while feed indicators (anti-scores) keep a normal feed well below 70.
        private const val CONFIDENCE_THRESHOLD = 70
        private const val CONF_FULLSCREEN_VIDEO = 35
        private const val CONF_NEXT_TEXT = 30
        private const val CONF_EDGE_BUTTON = 8
        private const val CONF_VERTICAL_PAGER = 15
        // Anti-scores (subtract from confidence)
        private const val ANTIF_TAB_BAR = 15
        private const val ANTIF_FEED_CONTENT = 10
        private const val ANTIF_POST_CONTAINER = 10
        private const val ANTIF_LIVE = 60
        private const val ANTIF_STORIES = 20
        private const val ANTIF_SPONSORED = 30
        private const val ANTIF_RECYCLERVIEW = 5
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = serviceInfo
        info.eventTypes = info.eventTypes or AccessibilityEvent.TYPE_VIEW_CLICKED
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo = info

        repository = GuardianRepository(applicationContext)
        repository.guardianStateFlow.onEach { state ->
            val wasPornBlockerActive = currentState.pornBlockerActive
            currentState = state

            if (state.pornBlockerActive && !wasPornBlockerActive) {
                val vpnIntent = Intent(this, GuardianVpnService::class.java)
                startService(vpnIntent)
            } else if (!state.pornBlockerActive && wasPornBlockerActive) {
                val stopIntent = Intent(this, GuardianVpnService::class.java).apply { action = "STOP" }
                startService(stopIntent)
            }
        }.launchIn(scope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !currentState.isShieldActive) return
        val packageName = event.packageName?.toString() ?: return
        if (currentState.whitelistApps.contains(packageName)) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (isFullBlocked(packageName)) {
                executeFullBlock(packageName)
                return
            }
        }

        Log.d(TAG, "Event: type=${event.eventType} pkg=$packageName")

        val isYouTube = packageName == "com.google.android.youtube"
        val isFacebook = packageName in FacebookPackages
        if (!isYouTube && !isFacebook) return

        // ===== YOUTUBE SHORTS BLOCKER =====
        if (isYouTube && currentState.youtubeMode == "shorts") {

            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val className = event.className?.toString() ?: ""
                if (className.contains("Shorts", ignoreCase = true) ||
                    className.contains("ReelPlayerFragment", ignoreCase = true)) {
                    navigateYoutubeHome()
                    return
                }
            }

            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(120)
                    val detected = withContext(Dispatchers.Main) {
                        val root = rootInActiveWindow ?: return@withContext false
                        val result = detectYoutubeShorts(root)
                        root.recycle()
                        result
                    }
                    if (detected) {
                        navigateYoutubeHome()
                    }
                }
            }
            return
        }

        // ===== FACEBOOK REELS BLOCKER =====
        if (isFacebook && currentState.facebookMode == "reels") {

            // Only the main Facebook app has Reels; skip Messenger/Lite
            if (packageName != "com.facebook.katana") return

            // Track boot time: record the first event when Facebook opens
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                facebookBootTimes.putIfAbsent(packageName, System.currentTimeMillis())
            }

            // Fast path: check className directly on TYPE_WINDOW_STATE_CHANGED
            // (like YouTube does) — catches Reels activity/fragment changes
            // NOTE: only match specific patterns, NOT "reel"/"clip" substrings
            // to avoid false positives on the main feed activity
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val className = event.className?.toString() ?: ""
                val isReelsScreen = className.containsAny(
                    "ReelsFragment", "ReelsTab", "ReelsActivity",
                    "ClipsActivity", "ClipsFragment", "ReelsFeedFragment"
                )
                if (isReelsScreen) {
                    Log.d(TAG, "Facebook Reels detected via className: $className")
                    navigateFacebookHome()
                    return
                }
            }

            // Handle relevant events only
            val isStateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            val isClick = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
            val isContentChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

            // CONTENT_CHANGED fires constantly during feed scroll — only act if scroll has stopped
            // (i.e., debounce will prevent rapid fire; the 400ms delay is our throttle)
            if (!isStateChange && !isClick && !isContentChange) return

            debounceJob?.cancel()
            debounceJob = scope.launch {
                val delayMs = when {
                    isClick -> 500L           // Wait for Reels animation to complete after tap
                    isContentChange -> 400L   // Longer debounce to avoid scroll false positives
                    else -> 150L              // STATE_CHANGED: fast response
                }
                delay(delayMs)

                // Grace period: first 3 seconds after Facebook opens → feed still loading,
                // skip unreliable tree traversal (but keep fast checks like tab/text/viewId)
                val bootTime = facebookBootTimes[packageName] ?: 0L
                val useFullDetection = System.currentTimeMillis() - bootTime >= FACEBOOK_BOOT_GRACE_MS

                // IMPORTANT: All AccessibilityNodeInfo operations MUST be on Main
                // thread (Android 12+ restriction)
                val detected = withContext(Dispatchers.Main) {
                    val root = rootInActiveWindow ?: return@withContext false
                    val result = detectFacebookReels(root, useFullDetection, facebookPackage = packageName)
                    root.recycle()
                    result
                }
                if (detected) {
                    Log.d(TAG, "Facebook Reels detected (event=${event.eventType})")
                    navigateFacebookHome(packageName)
                }
            }
        }
    }

    private fun detectYoutubeShorts(root: AccessibilityNodeInfo): Boolean {
        val targetViewIdSubstrings = listOf(
            "shorts_shelf_item_container", "reel_watch_fragment_root",
            "shorts_detail_fragment_root", "shorts_inner_container", "shorts_header_container"
        )

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var count = 0
        val maxNodes = 150

        while (queue.isNotEmpty() && count < maxNodes) {
            val current = queue.removeFirst()
            count++

            val viewId = current.viewIdResourceName ?: ""
            for (sub in targetViewIdSubstrings) {
                if (viewId.contains(sub, ignoreCase = true)) {
                    queue.forEach { it.recycle() }
                    return true
                }
            }

            val contentDesc = current.contentDescription?.toString() ?: ""
            if (contentDesc.lowercase() == "shorts" && (current.isSelected || current.isFocused)) {
                queue.forEach { it.recycle() }
                return true
            }

            val text = current.text?.toString() ?: ""
            if (text.lowercase().contains("shorts")) {
                val cn = current.className?.toString() ?: ""
                if (cn.contains("Tab", ignoreCase = true) || cn.contains("Button", ignoreCase = true) || current.isSelected) {
                    queue.forEach { it.recycle() }
                    return true
                }
            }

            for (i in 0 until current.childCount) {
                if (count >= maxNodes) break
                val child = current.getChild(i) ?: continue
                queue.addLast(child)
            }
        }

        queue.forEach { it.recycle() }
        return false
    }

    private fun navigateYoutubeHome() {
        if (!canActYoutubeTimed()) return

        val root = rootInActiveWindow

        try {
            if (root != null) {
                val found = findAndClickNode(root, setOf("Home"), setOf("home_tab", "pivot_bar_item"))
                if (found) {
                    scope.launch { repository.updateBlocksCount(currentState.blocksCount + 1) }
                    return
                }
            }
        } finally {
            root?.recycle()
        }

        // Fallback: deep link يُبقي المستخدم داخل يوتيوب
        try {
            val intent = Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.youtube.com/")).apply {
                setPackage("com.google.android.youtube")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } catch (_: Exception) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 400)
        }

        scope.launch { repository.updateBlocksCount(currentState.blocksCount + 1) }
    }

    private fun detectFacebookReels(
        root: AccessibilityNodeInfo,
        useFullDetection: Boolean = true,
        facebookPackage: String = "com.facebook.katana"
    ): Boolean {
        val screenHeight = applicationContext.resources.displayMetrics.heightPixels
        val screenWidth = applicationContext.resources.displayMetrics.widthPixels
        var absBypassLive = false  // absolute immunity: if true, NEVER block

        // ---- FAST CHECK 1: Reels tab selected in pivot bar (highest confidence) ----
        val allNodes = root.findAccessibilityNodeInfosByViewId("$facebookPackage:id/pivot_bar")
        if (allNodes.isNotEmpty()) {
            for (pivotBar in allNodes) {
                for (i in 0 until pivotBar.childCount) {
                    val child = pivotBar.getChild(i) ?: continue
                    if (child.isSelected) {
                        val text = child.text?.toString()?.lowercase() ?: ""
                        val desc = child.contentDescription?.toString()?.lowercase() ?: ""
                        if ("reels" in text || "clips" in text || "ريلز" in text ||
                            "reels" in desc || "clips" in desc) {
                            allNodes.forEach { it.recycle() }
                            child.recycle()
                            Log.d(TAG, "Reels tab is selected → blocking")
                            return true
                        }
                    }
                    child.recycle()
                }
            }
            allNodes.forEach { it.recycle() }
        }

        // ---- FAST CHECK 2: In-feed Reels guard (skip if embedded in feed) ----
        val dynamicFeedReelsIds = FEED_REELS_VIEW_IDS.map { it.replace("com.facebook.katana", facebookPackage) }
        for (feedViewId in dynamicFeedReelsIds) {
            val matches = try { root.findAccessibilityNodeInfosByViewId(feedViewId) }
                          catch (_: Exception) { emptyList() }
            if (matches.isNotEmpty()) {
                matches.forEach { it.recycle() }
                Log.d(TAG, "In-feed Reels detected → SKIP (not full-screen)")
                return false
            }
        }

        // ---- FAST CHECK 3: Reels view IDs covering >50% screen ----
        val dynamicReelsIds = REELS_VIEW_IDS.map { it.replace("com.facebook.katana", facebookPackage) }
        for (viewId in dynamicReelsIds) {
            val matches = try { root.findAccessibilityNodeInfosByViewId(viewId) } catch (_: Exception) { emptyList() }
            if (matches.isNotEmpty()) {
                for (node in matches) {
                    if (node.isVisibleToUser) {
                        val bounds = android.graphics.Rect()
                        node.getBoundsInScreen(bounds)
                        if (bounds.height() > screenHeight * 0.5) {
                            matches.forEach { it.recycle() }
                            Log.d(TAG, "Reels viewId=$viewId covers ${bounds.height()}px → blocking")
                            return true
                        }
                    }
                }
                matches.forEach { it.recycle() }
            }
        }

        // ---- TREE TRAVERSAL with Confidence Score ----
        if (!useFullDetection) {
            Log.d(TAG, "Skipping tree traversal — boot grace period active")
            return false
        }

        var confidence = 0
        var count = 0
        val maxNodes = 400
        val stack = java.util.Stack<AccessibilityNodeInfo>()
        stack.push(root)

        while (stack.isNotEmpty() && count < maxNodes) {
            val node = stack.pop()
            count++

            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val className = node.className?.toString()?.lowercase() ?: ""
            val nodeText = node.text?.toString()?.lowercase() ?: ""

            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val h = bounds.height()
            val w = bounds.width()

            // ── ABSOLUTE IMMUNITY: Live, Stories, Sponsored ─────────────────
            if (nodeText.containsAny("مباشر", "live now", "بث مباشر") || contentDesc.contains("live")) {
                absBypassLive = true
                confidence -= ANTIF_LIVE
                continue
            }
            if (contentDesc.contains("sponsored") || nodeText.containsAny("sponsored", "إعلان", "مُموَّل")) {
                confidence -= ANTIF_SPONSORED
                continue
            }
            if (viewId.containsAny("story_viewer", "story_tray")) {
                absBypassLive = true
                confidence -= ANTIF_STORIES
                continue
            }

            // ── ANTI-SCORE: feed indicators subtract confidence ──────────────
            if (viewId.containsAny("tab_bar", "bottom_tab", "pivot_bar", "navigation_bar")) confidence -= ANTIF_TAB_BAR
            if (viewId.containsAny("feed_story", "newsfeed", "feed_content", "timeline")) confidence -= ANTIF_FEED_CONTENT
            if (viewId.containsAny("post_container", "story_container", "stories_root")) confidence -= ANTIF_POST_CONTAINER
            if (className.contains("recyclerview") && h > screenHeight * 0.25) confidence -= ANTIF_RECYCLERVIEW

            // ── REELS SIGNALS: each adds to confidence ───────────────────────

            // Full-screen video in non-Live context → strong signal
            if (className.containsAny("surfaceview", "textureview")
                && h > screenHeight * 0.82
                && w > screenWidth * 0.5) {
                Log.d(TAG, "TREE: Full-screen video h=$h (${(h * 100 / screenHeight)}%) → +$CONF_FULLSCREEN_VIDEO")
                confidence += CONF_FULLSCREEN_VIDEO
            }

            // Edge action buttons (LTR or RTL) → moderate signal
            if (h in (40..screenHeight / 14)
                && bounds.top > screenHeight * 0.40
                && node.isClickable
                && (bounds.left < screenWidth * 0.15 || bounds.right > screenWidth * 0.85)) {
                confidence += CONF_EDGE_BUTTON
            }

            // "التالي" text in Reels mini-player → strong signal
            if (nodeText.contains("التالي")) {
                Log.d(TAG, "TREE: Found 'التالي' → +$CONF_NEXT_TEXT")
                confidence += CONF_NEXT_TEXT
            }

            // Share/comment text in edge position → Reels action bar
            if (nodeText.containsAny("share", "comment", "تعليق", "مشاركة")
                && (bounds.left < screenWidth * 0.15 || bounds.right > screenWidth * 0.85)) {
                confidence += CONF_EDGE_BUTTON / 2
            }

            // Vertical pager (Reels swipe container)
            if (viewId.contains("recyclerview") || className.contains("recyclerview")) {
                if (h > screenHeight * 0.70 && !viewId.containsAny("newsfeed", "feed")) {
                    confidence += CONF_VERTICAL_PAGER
                }
            }

            // Early exit: strongly confident
            if (confidence >= CONFIDENCE_THRESHOLD + 30) {
                break
            }
            // Early exit: clearly not Reels (anti-score domination)
            if (confidence <= -CONFIDENCE_THRESHOLD) {
                break
            }

            for (i in 0 until node.childCount) {
                if (count >= maxNodes) break
                node.getChild(i)?.let { stack.push(it) }
            }
        }

        drainAndRecycle(stack)

        // Absolute immunity: NEVER block Live, Stories, or Sponsored content
        if (absBypassLive) {
            Log.d(TAG, "TREE SKIP: Live/Story/Sponsored immunity (confidence=$confidence)")
            return false
        }

        val result = confidence >= CONFIDENCE_THRESHOLD
        Log.d(TAG, if (result) "TREE BLOCK: confidence=$confidence ≥ $CONFIDENCE_THRESHOLD"
                   else "TREE SKIP: confidence=$confidence < $CONFIDENCE_THRESHOLD")
        return result
    }

    private fun navigateFacebookHome(packageName: String = "com.facebook.katana") {
        if (!canActFacebookTimed()) {
            Log.d(TAG, "navigateFacebookHome: rate limited")
            return
        }

        Log.d(TAG, "navigateFacebookHome: executing")

        val root = rootInActiveWindow

        try {
            if (root != null) {
                // 1) Try known Home tab view IDs
                for (viewId in HOME_VIEW_IDS) {
                    val hits = root.findAccessibilityNodeInfosByViewId(viewId)
                    if (hits.isNotEmpty()) {
                        val target = hits.first()
                        if (target.isSelected) {
                            hits.forEach { it.recycle() }
                            scope.launch { repository.updateBlocksCount(currentState.blocksCount + 1) }
                            return
                        }
                        if (target.isClickable) {
                            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            hits.forEach { it.recycle() }
                            scope.launch { repository.updateBlocksCount(currentState.blocksCount + 1) }
                            return
                        }
                        hits.forEach { it.recycle() }
                    }
                }

                // 2) Fallback: find Home tab by content description
                val found = findAndClickNode(
                    root,
                    targetContentDesc = setOf("Home", "News Feed", "الرئيسية", "الصفحة الرئيسية", "Feed"),
                    targetViewIdSubstrings = setOf("home_tab", "tab_home", "tab", "pivot_bar")
                )
                if (found) {
                    scope.launch { repository.updateBlocksCount(currentState.blocksCount + 1) }
                    return
                }
            }
        } finally {
            root?.recycle()
        }

        // 3) Last resort: أعد تشغيل Facebook على صفحته الرئيسية
        performGlobalAction(GLOBAL_ACTION_BACK)
        try {
            val fbIntent = packageManager.getLaunchIntentForPackage(packageName)
                ?: return
            fbIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(fbIntent)
        } catch (_: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        scope.launch { repository.updateBlocksCount(currentState.blocksCount + 1) }
    }

    private fun drainAndRecycle(stack: java.util.Stack<AccessibilityNodeInfo>) {
        while (stack.isNotEmpty()) {
            try { stack.pop().recycle() } catch (_: Exception) {}
        }
    }

    private fun findAndClickNode(root: AccessibilityNodeInfo, targetContentDesc: Set<String>, targetViewIdSubstrings: Set<String>): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var count = 0
        val maxNodes = 200

        while (queue.isNotEmpty() && count < maxNodes) {
            val current = queue.removeFirst()
            count++

            val contentDesc = current.contentDescription?.toString() ?: ""
            val className = current.className?.toString() ?: ""
            val viewId = current.viewIdResourceName ?: ""

            val matchesContent = contentDesc in targetContentDesc
            val matchesClass = targetViewIdSubstrings.any { className.contains(it, ignoreCase = true) }
            val matchesViewId = targetViewIdSubstrings.any { viewId.contains(it, ignoreCase = true) }

            if (matchesContent && (matchesClass || matchesViewId || current.isClickable)) {
                if (!current.isSelected) {
                    current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    queue.forEach { it.recycle() }
                    return true
                }
                // Already selected (already on home) → success
                queue.forEach { it.recycle() }
                return true
            }

            if (matchesContent && !current.isSelected && !matchesClass && !matchesViewId) {
                val parent = current.parent
                if (parent != null) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    parent.recycle()
                    queue.forEach { it.recycle() }
                    return true
                }
            }

            for (i in 0 until current.childCount) {
                if (count >= maxNodes) break
                val child = current.getChild(i) ?: continue
                queue.addLast(child)
            }
        }

        queue.forEach { it.recycle() }
        return false
    }

    private fun canActYoutubeTimed(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastYoutubeBlockTime < YOUTUBE_BLOCK_INTERVAL_MS) return false
        lastYoutubeBlockTime = now
        return true
    }

    private fun canActFacebookTimed(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastFacebookBlockTime < FACEBOOK_BLOCK_INTERVAL_MS) return false
        lastFacebookBlockTime = now
        return true
    }

    private fun isFullBlocked(packageName: String): Boolean {
        if (currentState.whitelistApps.contains(packageName)) return false

        return (packageName == "com.instagram.android" && currentState.instagramBlocked) ||
               (packageName == "com.snapchat.android" && currentState.snapchatBlocked) ||
               (packageName == "com.twitter.android" && currentState.twitterBlocked) ||
               ((packageName == "com.zhiliaoapp.musically" || packageName == "com.ss.android.ugc.trill") && currentState.tiktokBlocked) ||
               (packageName == "com.google.android.youtube" && currentState.youtubeMode == "full") ||
               (packageName in FacebookPackages && currentState.facebookMode == "full") ||
               currentState.blacklistApps.contains(packageName) ||
               AIExplorerService.isAppBanned(packageName)
    }

    private fun executeFullBlock(packageName: String) {
        val now = System.currentTimeMillis()
        val last = fullBlockLastTimes[packageName] ?: 0L
        if (now - last < FULL_BLOCK_COOLDOWN) return
        fullBlockLastTimes[packageName] = now

        val intent = Intent(this, BlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("APP_NAME", getAppNameFromPackage(packageName))
            if (AIExplorerService.isAppBanned(packageName)) {
                putExtra("BLOCK_REASON", "ai_scan")
            }
        }
        startActivity(intent)

        scope.launch {
            repository.updateBlocksCount(currentState.blocksCount + 1)
        }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return when (packageName) {
            "com.google.android.youtube" -> "YouTube"
            "com.facebook.katana", "com.facebook.orca",
            "com.facebook.lite", "com.facebook.mlite" -> "Facebook"
            "com.instagram.android" -> "Instagram"
            "com.snapchat.android" -> "Snapchat"
            "com.twitter.android" -> "X (Twitter)"
            "com.zhiliaoapp.musically" -> "TikTok"
            "com.ss.android.ugc.trill" -> "TikTok"
            else -> packageName.substringAfterLast('.')
                .replaceFirstChar { it.uppercase() }
                .ifEmpty { "App" }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
