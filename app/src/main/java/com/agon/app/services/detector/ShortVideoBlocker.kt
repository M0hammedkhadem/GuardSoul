package com.agon.app.services.detector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.data.GuardianRepository
import com.agon.app.data.GuardianState
import com.agon.app.services.AIExplorerService
import com.agon.app.services.GuardianAccessibilityService
import com.agon.app.services.GuardianVpnService
import com.agon.app.utils.NodeUtils
import com.agon.app.utils.TraversalAction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

private fun CharSequence.containsAny(vararg terms: String): Boolean =
    terms.any { this.contains(it, ignoreCase = true) }

class ShortVideoBlocker(
    private val service: GuardianAccessibilityService,
    private val repository: GuardianRepository,
    private val getState: () -> GuardianState,
    private val setState: (GuardianState) -> Unit
) {
    private val scope get() = service.scope
    private val currentState get() = getState()
    private val rootInActiveWindow get() = service.rootInActiveWindow
    private val packageManager get() = service.packageManager
    private val applicationContext get() = service.applicationContext

    private var lastYoutubeBlockTime = 0L
    private var lastFacebookContentBlockTime = 0L
    private var lastFacebookClickBlockTime = 0L
    private val YOUTUBE_BLOCK_INTERVAL_MS = 2000L
    private val FACEBOOK_CONTENT_BLOCK_INTERVAL_MS = 1500L
    private val FACEBOOK_CLICK_BLOCK_INTERVAL_MS = 2000L
    private val FACEBOOK_BOOT_GRACE_MS = 3000L
    private val facebookBootTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val swipeBlockLastTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val SWIPE_BLOCK_INTERVAL_MS = 2000L
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val TAG = "GuardianService"

    companion object {
        val FacebookPackages = setOf(
            "com.facebook.katana",
            "com.facebook.orca",
            "com.facebook.lite",
            "com.facebook.mlite"
        )
        val InstagramPackages = setOf("com.instagram.android")

        private val REELS_VIEW_IDS = listOf(
            "com.facebook.katana:id/reel_viewer",
            "com.facebook.katana:id/reel_player",
            "com.facebook.katana:id/clips_viewer",
            "com.facebook.katana:id/clips_container",
            "com.facebook.katana:id/reel_container",
            "com.facebook.katana:id/reels_feed"
        )

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

        private val INSTAGRAM_REELS_VIEW_IDS = listOf(
            "com.instagram.android:id/reels_tab",
            "com.instagram.android:id/clips_tab",
            "com.instagram.android:id/reel_viewer",
            "com.instagram.android:id/clips_viewer",
            "com.instagram.android:id/reel_container"
        )

        private val INSTAGRAM_HOME_VIEW_IDS = listOf(
            "com.instagram.android:id/feed_tab",
            "com.instagram.android:id/home_tab",
            "com.instagram.android:id/tab_bar"
        )

        private const val CONFIDENCE_THRESHOLD = 70
        private const val CONF_FULLSCREEN_VIDEO = 35
        private const val CONF_NEXT_TEXT = 30
        private const val CONF_EDGE_BUTTON = 8
        private const val CONF_VERTICAL_PAGER = 15
        private const val ANTIF_TAB_BAR = 15
        private const val ANTIF_FEED_CONTENT = 10
        private const val ANTIF_POST_CONTAINER = 10
        private const val ANTIF_LIVE = 60
        private const val ANTIF_STORIES = 20
        private const val ANTIF_SPONSORED = 30
        private const val ANTIF_RECYCLERVIEW = 5
    }

    fun onServiceConnected() {
        val info = service.serviceInfo
        info.eventTypes = info.eventTypes or AccessibilityEvent.TYPE_VIEW_CLICKED
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        service.serviceInfo = info

        repository.guardianStateFlow.onEach { state ->
            val wasPornBlockerActive = currentState.pornBlockerActive
            val wasShieldActive = currentState.isShieldActive
            setState(state)

            if (wasShieldActive && !state.isShieldActive) {
                service.startService(Intent(service, GuardianVpnService::class.java).apply { action = "STOP" })
                service.startService(Intent(service, AIExplorerService::class.java).apply { action = "STOP" })
                return@onEach
            }

            if (state.pornBlockerActive && !wasPornBlockerActive) {
                val vpnIntent = Intent(service, GuardianVpnService::class.java)
                service.startService(vpnIntent)
            } else if (!state.pornBlockerActive && wasPornBlockerActive) {
                val stopIntent = Intent(service, GuardianVpnService::class.java).apply { action = "STOP" }
                service.startService(stopIntent)
            }
        }.launchIn(scope)
    }

    fun handleYoutubeShorts(event: AccessibilityEvent, packageName: String) {
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
            service.debounceJob?.cancel()
            service.debounceJob = scope.launch {
                delay(120)
                val detected = withContext(Dispatchers.Main) {
                    val root = rootInActiveWindow ?: return@withContext false
                    detectYoutubeShorts(root)
                }
                if (detected) {
                    navigateYoutubeHome()
                }
            }
        }
    }

    private fun detectYoutubeShorts(root: AccessibilityNodeInfo): Boolean {
        val targetViewIdSubstrings = listOf(
            "shorts_shelf_item_container", "reel_watch_fragment_root",
            "shorts_detail_fragment_root", "shorts_inner_container", "shorts_header_container"
        )
        return NodeUtils.bfs(root, maxNodes = 150) { node ->
            val viewId = node.viewIdResourceName ?: ""
            for (sub in targetViewIdSubstrings) {
                if (viewId.contains(sub, ignoreCase = true)) return@bfs TraversalAction.STOP
            }
            val contentDesc = node.contentDescription?.toString() ?: ""
            if (contentDesc.lowercase() == "shorts" && (node.isSelected || node.isFocused)) return@bfs TraversalAction.STOP
            val text = node.text?.toString() ?: ""
            if (text.lowercase().contains("shorts")) {
                val cn = node.className?.toString() ?: ""
                if (cn.contains("Tab", ignoreCase = true) || cn.contains("Button", ignoreCase = true) || node.isSelected) return@bfs TraversalAction.STOP
            }
            TraversalAction.CONTINUE
        }
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
            NodeUtils.safeRecycle(root)
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.youtube.com/")).apply {
                setPackage("com.google.android.youtube")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            service.startActivity(intent)
        } catch (_: Exception) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            mainHandler.postDelayed({ service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }, 400)
        }

        scope.launch { repository.updateBlocksCount(currentState.blocksCount + 1) }
    }

    fun handleFacebookReelsEvent(event: AccessibilityEvent, packageName: String) {
        if (packageName != "com.facebook.katana") return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            facebookBootTimes.putIfAbsent(packageName, System.currentTimeMillis())
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                if (isFacebookReelsTabClicked(event) && canActFacebookClickTimed()) {
                    Timber.tag(TAG).d("Layer 1: Blocking Reels tab click")
                    navigateFacebookHome(packageName)
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (shouldBlockHorizontalSwipe(event) && canActFacebookSwipeTimed(packageName)) {
                    Timber.tag(TAG).d("Layer 2: Blocking horizontal swipe toward Reels")
                    navigateFacebookHome(packageName)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleFacebookLayer3(event, packageName)
            }
        }
    }

    private fun handleFacebookLayer3(event: AccessibilityEvent, packageName: String) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString() ?: ""
            val isReelsScreen = className.containsAny(
                "ReelsFragment", "ReelsTab", "ReelsActivity",
                "ClipsActivity", "ClipsFragment", "ReelsFeedFragment"
            )
            if (isReelsScreen) {
                Timber.tag(TAG).d("Facebook Reels detected via className: $className")
                if (canActFacebookContentTimed()) {
                    navigateFacebookHome(packageName)
                }
                return
            }
        }

        service.debounceJob?.cancel()
        service.debounceJob = scope.launch {
            val delayMs = if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) 400L else 150L
            delay(delayMs)

            val bootTime = facebookBootTimes[packageName] ?: 0L
            val useFullDetection = System.currentTimeMillis() - bootTime >= FACEBOOK_BOOT_GRACE_MS

            val detected = withContext(Dispatchers.Main) {
                val root = rootInActiveWindow ?: return@withContext false
                detectFacebookReels(root, useFullDetection, packageName)
            }

            if (detected && canActFacebookContentTimed()) {
                Timber.tag(TAG).d("Layer 3: Reels detected via content/state change")
                navigateFacebookHome(packageName)
            }
        }
    }

    private fun detectFacebookReels(
        root: AccessibilityNodeInfo,
        useFullDetection: Boolean = true,
        facebookPackage: String = "com.facebook.katana"
    ): Boolean {
        val screenHeight = applicationContext.resources.displayMetrics.heightPixels
        val screenWidth = applicationContext.resources.displayMetrics.widthPixels
        var absBypassLive = false

        if (isReelsPivotTabSelected(root, facebookPackage)) {
            Timber.tag(TAG).d("Reels pivot tab selected → blocking")
            NodeUtils.safeRecycle(root)
            return true
        }

        val dynamicFeedReelsIds = FEED_REELS_VIEW_IDS.map { it.replace("com.facebook.katana", facebookPackage) }
        for (feedViewId in dynamicFeedReelsIds) {
            val matches = try { root.findAccessibilityNodeInfosByViewId(feedViewId) }
                          catch (_: Exception) { emptyList() }
            if (matches.isNotEmpty()) {
                NodeUtils.recycleAll(matches)
                Timber.tag(TAG).d("In-feed Reels detected → SKIP (not full-screen)")
                NodeUtils.safeRecycle(root)
                return false
            }
        }

        val dynamicReelsIds = REELS_VIEW_IDS.map { it.replace("com.facebook.katana", facebookPackage) }
        for (viewId in dynamicReelsIds) {
            val matches = try { root.findAccessibilityNodeInfosByViewId(viewId) } catch (_: Exception) { emptyList() }
            if (matches.isNotEmpty()) {
                for (node in matches) {
                    if (node.isVisibleToUser) {
                        val bounds = Rect()
                        node.getBoundsInScreen(bounds)
                        if (bounds.height() > screenHeight * 0.5) {
                            NodeUtils.recycleAll(matches)
                            Timber.tag(TAG).d("Reels viewId=$viewId covers ${bounds.height()}px → blocking")
                            NodeUtils.safeRecycle(root)
                            return true
                        }
                    }
                }
                NodeUtils.recycleAll(matches)
            }
        }

        if (!useFullDetection) {
            Timber.tag(TAG).d("Skipping tree traversal — boot grace period active")
            NodeUtils.safeRecycle(root)
            return false
        }

        var confidence = 0

        NodeUtils.dfs(root, maxNodes = 400) { node ->
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val className = node.className?.toString()?.lowercase() ?: ""
            val nodeText = node.text?.toString()?.lowercase() ?: ""

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val h = bounds.height()
            val w = bounds.width()

            if (nodeText.containsAny("مباشر", "live now", "بث مباشر") || contentDesc.contains("live")) {
                absBypassLive = true
                confidence -= ANTIF_LIVE
                return@dfs TraversalAction.SKIP_CHILDREN
            }
            if (contentDesc.contains("sponsored") || nodeText.containsAny("sponsored", "إعلان", "مُموَّل")) {
                confidence -= ANTIF_SPONSORED
                return@dfs TraversalAction.SKIP_CHILDREN
            }
            if (viewId.containsAny("story_viewer", "story_tray")) {
                absBypassLive = true
                confidence -= ANTIF_STORIES
                return@dfs TraversalAction.SKIP_CHILDREN
            }

            if (viewId.containsAny("tab_bar", "bottom_tab", "pivot_bar", "navigation_bar")) confidence -= ANTIF_TAB_BAR
            if (viewId.containsAny("feed_story", "newsfeed", "feed_content", "timeline")) confidence -= ANTIF_FEED_CONTENT
            if (viewId.containsAny("post_container", "story_container", "stories_root")) confidence -= ANTIF_POST_CONTAINER
            if (className.contains("recyclerview") && h > screenHeight * 0.25) confidence -= ANTIF_RECYCLERVIEW

            if (className.containsAny("surfaceview", "textureview")
                && h > screenHeight * 0.82
                && w > screenWidth * 0.5) {
                Timber.tag(TAG).d("TREE: Full-screen video h=$h (${(h * 100 / screenHeight)}%) → +$CONF_FULLSCREEN_VIDEO")
                confidence += CONF_FULLSCREEN_VIDEO
            }

            if (h in (40..screenHeight / 14)
                && bounds.top > screenHeight * 0.40
                && node.isClickable
                && (bounds.left < screenWidth * 0.15 || bounds.right > screenWidth * 0.85)) {
                confidence += CONF_EDGE_BUTTON
            }

            if (nodeText.contains("التالي")) {
                Timber.tag(TAG).d("TREE: Found 'التالي' → +$CONF_NEXT_TEXT")
                confidence += CONF_NEXT_TEXT
            }

            if (nodeText.containsAny("share", "comment", "تعليق", "مشاركة")
                && (bounds.left < screenWidth * 0.15 || bounds.right > screenWidth * 0.85)) {
                confidence += CONF_EDGE_BUTTON / 2
            }

            if (viewId.contains("recyclerview") || className.contains("recyclerview")) {
                if (h > screenHeight * 0.70 && !viewId.containsAny("newsfeed", "feed")) {
                    confidence += CONF_VERTICAL_PAGER
                }
            }

            if (confidence >= CONFIDENCE_THRESHOLD + 30) return@dfs TraversalAction.STOP
            TraversalAction.CONTINUE
        }

        if (absBypassLive) {
            Timber.tag(TAG).d("TREE SKIP: Live/Story/Sponsored immunity (confidence=$confidence)")
            return false
        }

        val result = confidence >= CONFIDENCE_THRESHOLD
        Timber.tag(TAG).d(if (result) "TREE BLOCK: confidence=$confidence ≥ $CONFIDENCE_THRESHOLD"
                   else "TREE SKIP: confidence=$confidence < $CONFIDENCE_THRESHOLD")
        return result
    }

    private fun navigateFacebookHome(packageName: String = "com.facebook.katana") {
        Timber.tag(TAG).d("navigateFacebookHome: executing")

        val root = rootInActiveWindow

        try {
            if (root != null) {
                for (viewId in HOME_VIEW_IDS) {
                    val hits = root.findAccessibilityNodeInfosByViewId(viewId)
                    if (hits.isNotEmpty()) {
                        val target = hits.first()
                        if (target.isSelected) {
                            NodeUtils.recycleAll(hits)
                            scope.launch { repository.updateBlocksCount(currentState.blocksCount + 1) }
                            return
                        }
                        if (target.isClickable) {
                            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            NodeUtils.recycleAll(hits)
                            scope.launch { repository.updateBlocksCount(currentState.blocksCount + 1) }
                            return
                        }
                        NodeUtils.recycleAll(hits)
                    }
                }

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
            NodeUtils.safeRecycle(root)
        }

        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        try {
            val fbIntent = packageManager.getLaunchIntentForPackage(packageName)
                ?: return
            fbIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            service.startActivity(fbIntent)
        } catch (_: Exception) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
        scope.launch { repository.updateBlocksCount(currentState.blocksCount + 1) }
    }

    private fun findAndClickNode(root: AccessibilityNodeInfo, targetContentDesc: Set<String>, targetViewIdSubstrings: Set<String>): Boolean {
        return NodeUtils.bfs(root, maxNodes = 200) { node ->
            val contentDesc = node.contentDescription?.toString() ?: ""
            val className = node.className?.toString() ?: ""
            val viewId = node.viewIdResourceName ?: ""

            val matchesContent = contentDesc in targetContentDesc
            val matchesClass = targetViewIdSubstrings.any { className.contains(it, ignoreCase = true) }
            val matchesViewId = targetViewIdSubstrings.any { viewId.contains(it, ignoreCase = true) }

            if (matchesContent && (matchesClass || matchesViewId || node.isClickable)) {
                if (!node.isSelected) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return@bfs TraversalAction.STOP
                }
                return@bfs TraversalAction.STOP
            }

            if (matchesContent && !node.isSelected && !matchesClass && !matchesViewId) {
                val parent = node.parent
                if (parent != null) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    NodeUtils.safeRecycle(parent)
                    return@bfs TraversalAction.STOP
                }
            }

            TraversalAction.CONTINUE
        }
    }

    private fun canActYoutubeTimed(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastYoutubeBlockTime < YOUTUBE_BLOCK_INTERVAL_MS) return false
        lastYoutubeBlockTime = now
        return true
    }

    private fun canActFacebookContentTimed(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastFacebookContentBlockTime < FACEBOOK_CONTENT_BLOCK_INTERVAL_MS) return false
        lastFacebookContentBlockTime = now
        return true
    }

    private fun canActFacebookClickTimed(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastFacebookClickBlockTime < FACEBOOK_CLICK_BLOCK_INTERVAL_MS) return false
        lastFacebookClickBlockTime = now
        return true
    }

    private fun canActFacebookSwipeTimed(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        val last = swipeBlockLastTimes[packageName] ?: 0L
        if (now - last < SWIPE_BLOCK_INTERVAL_MS) return false
        swipeBlockLastTimes[packageName] = now
        return true
    }

    private fun isOnFacebookHomeTab(): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            val fbPkg = "com.facebook.katana"

            val homeTabs = root.findAccessibilityNodeInfosByViewId("$fbPkg:id/tab_home")
            try {
                if (homeTabs.any { it.isSelected }) return true
            } finally {
                NodeUtils.recycleAll(homeTabs)
            }

            val homeNodes = root.findAccessibilityNodeInfosByViewId("$fbPkg:id/home_tab")
            try {
                if (homeNodes.any { it.isSelected }) return true
            } finally {
                NodeUtils.recycleAll(homeNodes)
            }

            val texts = root.findAccessibilityNodeInfosByText("Home")
                .plus(root.findAccessibilityNodeInfosByText("الرئيسية"))
                .plus(root.findAccessibilityNodeInfosByText("News Feed"))
            try {
                return texts.any { it.isSelected }
            } finally {
                NodeUtils.recycleAll(texts)
            }
        } finally {
            NodeUtils.safeRecycle(root)
        }
    }

    private fun shouldBlockHorizontalSwipe(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return false

        val fromIndex = event.fromIndex ?: return false
        val toIndex = event.toIndex ?: return false
        val scrollX = event.scrollX ?: 0
        val scrollY = event.scrollY ?: 0

        val isHorizontal = scrollX != 0 && scrollY == 0
        if (!isHorizontal) return false

        val isOnHome = isOnFacebookHomeTab()
        if (!isOnHome) return false

        val goingLeft = fromIndex < toIndex || scrollX < 0
        return goingLeft
    }

    private fun detectReelsTab(node: AccessibilityNodeInfo): Boolean {
        val viewId = node.viewIdResourceName ?: ""
        if (viewId.contains("reels", ignoreCase = true) ||
            viewId.contains("video_channel_tab", ignoreCase = true) ||
            viewId.contains("clips", ignoreCase = true)) {
            return true
        }

        val desc = node.contentDescription?.toString() ?: ""
        if (desc.equals("Reels", ignoreCase = true) ||
            desc.equals("ريلز", ignoreCase = true) ||
            desc.equals("Clips", ignoreCase = true)) {
            return true
        }

        if (node.isSelected &&
            (node.text?.toString()?.contains("Reels", ignoreCase = true) == true ||
             node.text?.toString()?.contains("ريلز", ignoreCase = true) == true ||
             node.text?.toString()?.contains("Clips", ignoreCase = true) == true)) {
            return true
        }

        return false
    }

    private fun isReelsPivotTabSelected(root: AccessibilityNodeInfo, facebookPackage: String): Boolean {
        val pivotBars = root.findAccessibilityNodeInfosByViewId("$facebookPackage:id/pivot_bar")
        try {
            for (pivotBar in pivotBars) {
                for (i in 0 until pivotBar.childCount) {
                    val child = pivotBar.getChild(i) ?: continue
                    try {
                        if (child.isSelected && detectReelsTab(child)) {
                            return true
                        }
                    } finally {
                        child.recycle()
                    }
                }
            }
            return false
        } finally {
            NodeUtils.recycleAll(pivotBars)
        }
    }

    private fun isFacebookReelsTabClicked(event: AccessibilityEvent): Boolean {
        val source = event.source ?: return false
        try {
            val viewId = source.viewIdResourceName ?: ""
            if (viewId.contains("reels", ignoreCase = true) ||
                viewId.contains("video_channel_tab", ignoreCase = true)) {
                return true
            }

            val desc = source.contentDescription?.toString() ?: ""
            if (desc.equals("Reels", ignoreCase = true) ||
                desc.equals("ريلز", ignoreCase = true)) {
                return true
            }

            return false
        } finally {
            source.recycle()
        }
    }

    private var lastInstagramReelsBlockTime = 0L
    private val INSTAGRAM_REELS_BLOCK_INTERVAL_MS = 2000L

    private fun canActInstagramReelsTimed(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastInstagramReelsBlockTime < INSTAGRAM_REELS_BLOCK_INTERVAL_MS) return false
        lastInstagramReelsBlockTime = now
        return true
    }

    fun handleInstagramReelsEvent(event: AccessibilityEvent, packageName: String) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val source = event.source ?: return
                val viewId = source.viewIdResourceName ?: ""
                val desc = source.contentDescription?.toString() ?: ""
                NodeUtils.safeRecycle(source)

                val isReelsTap = viewId.containsAny("reels_tab", "clips_tab", "reel") ||
                    desc.equals("Reels", ignoreCase = true) ||
                    desc.equals("ريلز", ignoreCase = true)

                if (isReelsTap && canActInstagramReelsTimed()) {
                    Timber.tag(TAG).d("Instagram Layer 1: Reels tab click blocked")
                    navigateInstagramHome()
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (canActInstagramReelsTimed()) {
                    val scrollX = event.scrollX ?: 0
                    if (scrollX != 0) {
                        Timber.tag(TAG).d("Instagram Layer 2: Horizontal swipe blocked")
                        navigateInstagramHome()
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleInstagramLayer3(event, packageName)
            }
        }
    }

    private fun handleInstagramLayer3(event: AccessibilityEvent, packageName: String) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString() ?: ""
            if (className.containsAny("ClipsTab", "ReelsTab", "ReelsFragment", "ClipsFragment")) {
                Timber.tag(TAG).d("Instagram Layer 3: Reels screen detected via className")
                if (canActInstagramReelsTimed()) {
                    navigateInstagramHome()
                }
                return
            }
        }

        service.debounceJob?.cancel()
        service.debounceJob = scope.launch {
            delay(300L)
            val detected = withContext(Dispatchers.Main) {
                val root = rootInActiveWindow ?: return@withContext false
                detectInstagramReels(root)
            }
            if (detected && canActInstagramReelsTimed()) {
                Timber.tag(TAG).d("Instagram Layer 3: Reels detected via tree traversal")
                navigateInstagramHome()
            }
        }
    }

    private fun detectInstagramReels(root: AccessibilityNodeInfo): Boolean {
        val screenHeight = applicationContext.resources.displayMetrics.heightPixels

        for (viewId in INSTAGRAM_REELS_VIEW_IDS) {
            val matches = try { root.findAccessibilityNodeInfosByViewId(viewId) }
                catch (_: Exception) { emptyList() }
            if (matches.isNotEmpty()) {
                for (node in matches) {
                    if (node.isVisibleToUser) {
                        val bounds = Rect()
                        node.getBoundsInScreen(bounds)
                        if (bounds.height() > screenHeight * 0.4) {
                            NodeUtils.recycleAll(matches)
                            NodeUtils.safeRecycle(root)
                            return true
                        }
                    }
                }
                NodeUtils.recycleAll(matches)
            }
        }

        var fullscreenVideo = false
        var reelsLabel = false

        NodeUtils.bfs(root, maxNodes = 300) { node ->
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val className = node.className?.toString()?.lowercase() ?: ""
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

            if (viewId.containsAny("reels_tab", "clips_tab", "reel_viewer", "clips_viewer")) {
                reelsLabel = true
            }
            if (contentDesc.containsAny("reels", "ريلز", "clips")) {
                reelsLabel = true
            }

            if (className.containsAny("surfaceview", "textureview")) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.height() > screenHeight * 0.6) {
                    fullscreenVideo = true
                }
            }

            if (fullscreenVideo && reelsLabel) return@bfs TraversalAction.STOP
            TraversalAction.CONTINUE
        }

        return fullscreenVideo && reelsLabel
    }

    private fun navigateInstagramHome() {
        val root = rootInActiveWindow
        try {
            if (root != null) {
                for (viewId in INSTAGRAM_HOME_VIEW_IDS) {
                    val hits = root.findAccessibilityNodeInfosByViewId(viewId)
                    if (hits.isNotEmpty()) {
                        val target = hits.first()
                        if (target.isClickable && !target.isSelected) {
                            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                        NodeUtils.recycleAll(hits)
                        return
                    }
                }
            }
        } finally {
            NodeUtils.safeRecycle(root)
        }

        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }
}
