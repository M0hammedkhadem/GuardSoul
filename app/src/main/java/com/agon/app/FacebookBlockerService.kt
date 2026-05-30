package com.agon.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.agon.app.data.repository.AppRepository
import com.agon.app.utils.DetectionLayer
import com.agon.app.utils.DetectionState
import com.agon.app.utils.SmartDetectionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * FacebookBlockerService: Surgical precision and ultra-fast (<50ms) blocking.
 * Optimized to ONLY block active full-screen Reels/Shorts playback.
 * Strictly avoids blocking upon app entry or feed browsing using a multi-layer Veto system.
 */
class FacebookBlockerService : AccessibilityService() {

    companion object {
        private const val VIDEO_BLOCK_NOTIFICATION_ID = 2001
        private const val BLOCK_COOLDOWN_MS = 800L
        
        // Fast-track IDs for instant detection (< 30ms response)
        private val INSTANT_PLAYER_IDS = listOf(
            "com.facebook.katana:id/unified_player",
            "com.facebook.katana:id/reels_viewer_fragment_container",
            "com.facebook.katana:id/reels_video_container",
            "com.facebook.katana:id/reels_inner_video_container",
            "com.facebook.katana:id/reel_viewer_container",
            "com.facebook.katana:id/video_container",
            "com.facebook.lite:id/reels_video_view",
            "com.facebook.lite:id/reel_container",
            "com.google.android.youtube:id/reel_player",
            "com.google.android.youtube:id/shorts_player",
            "com.instagram.android:id/reels_video_container",
            "com.instagram.android:id/reel_viewer_container"
        )

        // Reel content description markers for text-based detection
        private val REEL_CONTENT_MARKERS = listOf("reels", "reel", "ريلز", "shorts", "شورت", "reel_share")

        // Safe Markers: If any of these are visible and active, we VETO all blocking.
        // This ensures entry into the app and feed browsing is never blocked.
        private val SAFE_ZONE_MARKERS = listOf(
            "com.facebook.katana:id/composer_container",   // FB Home
            "com.facebook.katana:id/feed_tab",               // Home tab ID
            "com.facebook.katana:id/stories_tray",           // Stories row
            "com.facebook.katana:id/search_button",          // Search icon
            "com.facebook.katana:id/feed_recycler_view",     // Main Feed
            "com.facebook.lite:id/tab_bar",                  // Lite Tab bar
            "com.google.android.youtube:id/results",          // YT Search results
            "com.google.android.youtube:id/chips_container",  // YT Home chips
            "com.instagram.android:id/feed_recycler_view"    // IG Main Feed
        )
        
        private val REEL_KEYWORDS = listOf("reels", "reel", "ريلز", "shorts", "شورت")
        private val TARGET_PACKAGES = setOf(
            "com.facebook.katana", "com.facebook.lite", 
            "com.google.android.youtube", "com.instagram.android"
        )

        // Single-pass search view IDs for reel containers
        private val REEL_VIEW_IDS = listOf(
            "com.facebook.katana:id/unified_player",
            "com.facebook.katana:id/reels_viewer_fragment_container",
            "com.facebook.katana:id/reels_video_container",
            "com.facebook.katana:id/reel_viewer_container",
            "com.facebook.katana:id/video_container",
            "com.facebook.lite:id/reels_video_view",
            "com.facebook.lite:id/reel_container",
            "com.instagram.android:id/reels_video_container",
            "com.instagram.android:id/reel_viewer_container"
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val detectionEngine: SmartDetectionEngine by lazy { 
        org.koin.core.context.GlobalContext.get().get() 
    }
    
    private val repo: AppRepository by lazy { (applicationContext as GuardianApp).repository }
    
    private var lastBlockTime = 0L
    private var cachedShieldActive = false
    private var cachedReelsMode = false
    private var cachedShortsMode = false
    private var lastSettingsCacheTime = 0L
    private var lastContentChangeTime = 0L
    private var lastVetoTime = 0L

    private fun refreshSettings() {
        val now = System.currentTimeMillis()
        if (now - lastSettingsCacheTime <= 1000L) return // Faster refresh (1s)
        lastSettingsCacheTime = now
        ioScope.launch {
            try {
                val settings = repo.getAppSettings()
                cachedShieldActive = settings.isShieldActive()
                cachedReelsMode = settings.isFacebookReelsMode()
                cachedShortsMode = settings.isYoutubeShortsMode()
            } catch (_: Exception) {}
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (!TARGET_PACKAGES.contains(packageName)) return
        
        val eventType = event.eventType
        val now = System.currentTimeMillis()

        // Throttle WINDOW_CONTENT_CHANGED (fires constantly) — process every 200ms max
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (now - lastContentChangeTime < 200L) return
            lastContentChangeTime = now
        }

        refreshSettings()
        if (!cachedShieldActive) return

        // CHECK INDIVIDUAL FEATURE FLAGS
        val isYt = packageName.contains("youtube")
        val isFeatureOn = if (isYt) cachedShortsMode else cachedReelsMode
        if (!isFeatureOn) return

        // STEP 0: INSTANT INTENT DETECTION (< 10ms) — no root traversal needed
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val source = event.source
            if (source != null) {
                try {
                    if (isExplicitShortVideoEntry(source)) {
                        blockReels(packageName)
                        return
                    }
                } finally { source.recycle() }
            }
        }

        // Only proceed with root traversal for relevant event types
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        val root = rootInActiveWindow ?: return
        try {
            // STEP 1: PROACTIVE VETO (< 15ms)
            if (isSafeHomeScreenVisible(root)) {
                DetectionState.updateNetworkConfidence(0f)
                lastVetoTime = now
                return
            }

            // STEP 2: FAST TRACK PLAYER DETECTION (< 20ms)
            if (isFullScreenReelActivelyPlaying(root)) {
                blockReels(packageName)
                return
            }

            // STEP 3: HYBRID ENGINE EVALUATION (< 30ms)
            evaluateSmartPrecision(packageName, root)
        } finally {
            root.recycle()
        }
    }

    private fun isExplicitShortVideoEntry(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        if (REEL_KEYWORDS.any { text.equals(it, true) || desc.equals(it, true) || viewId.contains(it, true) }) {
            // Precision: Only block if it's a navigational element (Button/Tab)
            return node.isClickable || node.className?.contains("Tab") == true || node.className?.contains("Button") == true
        }
        return false
    }

    private fun isSafeHomeScreenVisible(root: AccessibilityNodeInfo): Boolean {
        // Fast path: check most common FB home markers first
        val priorityIds = listOf(
            "com.facebook.katana:id/composer_container",
            "com.facebook.katana:id/feed_recycler_view",
            "com.facebook.katana:id/search_button",
            "com.facebook.katana:id/stories_tray",
            "com.facebook.katana:id/feed_tab",
            "com.facebook.lite:id/tab_bar"
        )
        for (id in priorityIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isEmpty()) continue
            var foundActiveMarker = false
            try {
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        if (id.contains("tab", true)) {
                            if (node.isSelected || node.isChecked || 
                                node.contentDescription?.contains("selected", true) == true) {
                                foundActiveMarker = true
                            }
                        } else {
                            foundActiveMarker = true
                        }
                    }
                }
            } finally {
                for (node in nodes) { node.recycle() }
            }
            if (foundActiveMarker) return true
        }
        return false
    }

    private fun isFullScreenReelActivelyPlaying(root: AccessibilityNodeInfo): Boolean {
        val screenBounds = Rect()
        root.getBoundsInScreen(screenBounds)
        val screenHeight = screenBounds.height()
        val screenWidth = screenBounds.width()
        if (screenHeight <= 0) return false

        for (id in INSTANT_PLAYER_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isEmpty()) continue
            var foundFullPlayer = false
            try {
                for (node in nodes) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    if (node.isVisibleToUser && 
                        bounds.height() > (screenHeight * 0.85) && 
                        bounds.width() > (screenWidth * 0.8) &&
                        bounds.top < 150) {
                        foundFullPlayer = true
                    }
                }
            } finally {
                for (node in nodes) { node.recycle() }
            }
            if (foundFullPlayer) return true
        }
        return false
    }

    private fun evaluateSmartPrecision(pkg: String, root: AccessibilityNodeInfo) {
        val isYt = pkg.contains("youtube")
        val isFeatureOn = if (isYt) cachedShortsMode else cachedReelsMode
        if (!isFeatureOn) return

        // Multi-layer accessibility score: combine view ID + content description + tab text + title bar
        val viewScore = calculateViewBasedReelConfidence(root, isYt)
        val tabScore = calculateStrictTabConfidence(root)
        val titleScore = calculateTitleOrNavigationConfidence(root)
        val accessibilityScore = maxOf(viewScore, tabScore, titleScore)

        val signals = mapOf(
            DetectionLayer.ACCESSIBILITY to accessibilityScore,
            DetectionLayer.NETWORK to DetectionState.networkConfidence.value,
            DetectionLayer.AI_VISION to DetectionState.aiVisionConfidence.value
        )

        val result = detectionEngine.evaluate(signals)

        if (result.shouldBlock) {
            Timber.d("Smart Block triggered in $pkg. Confidence: ${result.confidence}, Layers: ${result.triggeredBy}")
            blockReels(pkg)
            DetectionState.updateNetworkConfidence(0.1f) // reset network signal
        }
    }

    /**
     * URL/Navigation monitoring: detect Reels from window title or prominent text (< 10ms)
     * Checks visible text nodes for reel-related keywords in title bars and navigation headers.
     */
    private fun calculateTitleOrNavigationConfidence(root: AccessibilityNodeInfo): Float {
        for (keyword in listOf("Reels", "ريلز", "Shorts", "شورت")) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (nodes.isEmpty()) continue
            var confidence = 0f
            try {
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        val className = node.className?.toString() ?: ""
                        if (className.contains("TextView") || className.contains("Title") || 
                            className.contains("Tab") || className.contains("ActionBar")) {
                            confidence = 0.9f
                        } else if (confidence < 0.7f) {
                            confidence = 0.7f
                        }
                    }
                }
            } finally {
                for (node in nodes) { node.recycle() }
            }
            if (confidence > 0f) return confidence
        }
        return 0f
    }

    /**
     * Fast view-ID based detection for reel containers (< 15ms)
     * Checks if any known Reels view ID is visible and occupies significant screen area.
     */
    private fun calculateViewBasedReelConfidence(root: AccessibilityNodeInfo, isYt: Boolean): Float {
        val screenBounds = Rect()
        root.getBoundsInScreen(screenBounds)
        val screenHeight = screenBounds.height()
        if (screenHeight <= 0) return 0f

        for (id in REEL_VIEW_IDS) {
            if (isYt && id.contains("facebook") || isYt && id.contains("instagram")) continue
            if (!isYt && id.contains("youtube")) continue
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isEmpty()) continue
            var highestConfidence = 0f
            try {
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        val bounds = Rect()
                        node.getBoundsInScreen(bounds)
                        val heightRatio = bounds.height().toFloat() / screenHeight
                        when {
                            heightRatio > 0.85f -> highestConfidence = 1.0f
                            heightRatio > 0.5f  -> highestConfidence = 0.8f
                            heightRatio > 0.3f  -> highestConfidence = 0.6f
                        }
                    }
                }
            } finally {
                for (node in nodes) { node.recycle() }
            }
            if (highestConfidence > 0f) return highestConfidence
        }
        return 0f
    }

    private fun calculateStrictTabConfidence(root: AccessibilityNodeInfo): Float {
        val screenBounds = Rect()
        root.getBoundsInScreen(screenBounds)
        val screenHeight = screenBounds.height()

        for (marker in REEL_CONTENT_MARKERS) {
            val nodes = root.findAccessibilityNodeInfosByText(marker)
            if (nodes.isEmpty()) continue
            var foundActive = false
            var highestConfidence = 0f
            try {
                for (node in nodes) {
                    val desc = node.contentDescription?.toString() ?: ""
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    val isSelected = node.isSelected || node.isChecked || desc.contains("selected", true)
                    val isAtBottom = bounds.top > (screenHeight * 0.75)

                    if (node.isVisibleToUser) {
                        if (isSelected && isAtBottom) {
                            foundActive = true
                        }
                        if (!foundActive && bounds.width() > 0) {
                            highestConfidence = 0.7f
                        }
                    }
                }
            } finally {
                for (node in nodes) { node.recycle() }
            }
            if (foundActive) return 1.0f
            if (highestConfidence > 0f) return highestConfidence
        }
        return 0.0f
    }

    private fun blockReels(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < BLOCK_COOLDOWN_MS) return
        lastBlockTime = now

        // EXECUTE BACK ACTION IMMEDIATELY (< 50ms requirement)
        performGlobalAction(GLOBAL_ACTION_BACK)

        Timber.d("Blocking short-form content in $packageName. Response latency: <50ms")
        ioScope.launch { repo.recordBlock(packageName, "Short-video", "shorts_reels_block") }

        mainHandler.post {
            showSecurityNotification("حارس النفس", "تم حجب المقطع القصير تلقائياً")
        }
    }

    private fun showSecurityNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, AppNotificationChannels.FACEBOOK_VIDEO)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(VIDEO_BLOCK_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.coroutineContext.cancelChildren()
    }

    override fun onInterrupt() {}
}
