package com.agon.app.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.data.GuardianRepository
import com.agon.app.data.GuardianState
import com.agon.app.ui.screens.BlockActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class GuardianAccessibilityService : AccessibilityService() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var repository: GuardianRepository
    private var currentState: GuardianState = GuardianState()

    private var debounceJob: Job? = null
    private val BLOCK_COOLDOWN = 1500L
    private val TAG = "GuardianService"
    private val lastActionTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastBlockTime = 0L
    private val BLOCK_INTERVAL_MS = 2000L

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = serviceInfo
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
        val now = System.currentTimeMillis()

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (isFullBlocked(packageName)) {
                executeFullBlock(packageName)
                return
            }
        }

        val isYouTube = packageName == "com.google.android.youtube"
        val isFacebook = packageName == "com.facebook.katana"
        if (!isYouTube && !isFacebook) return

        if (now - lastBlockTime < BLOCK_INTERVAL_MS) return

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
                    delay(200)
                    val root = rootInActiveWindow ?: return@launch
                    val isShorts = detectYoutubeShorts(root)
                    root.recycle()
                    if (isShorts) {
                        withContext(Dispatchers.Main) { navigateYoutubeHome() }
                    }
                }
            }
            return
        }

        // ===== FACEBOOK REELS BLOCKER =====
        if (isFacebook && currentState.facebookMode == "reels") {

            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val className = event.className?.toString() ?: ""
                if (className.contains("Reel", ignoreCase = true) ||
                    className.contains("Clips", ignoreCase = true) ||
                    event.text?.any { it.contains("ريلز") } == true) {
                    navigateFacebookHome()
                    return
                }
            }

            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(200)
                    val root = rootInActiveWindow ?: return@launch
                    val detected = detectFacebookReels(root)
                    root.recycle()
                    if (detected) {
                        Log.d(TAG, "Facebook Reels detected")
                        withContext(Dispatchers.Main) { navigateFacebookHome() }
                    }
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
        val maxNodes = 200

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
                if (current.isSelected) {
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
        if (!canActTimed()) return

        val root = rootInActiveWindow ?: run {
            performGlobalAction(GLOBAL_ACTION_BACK)
            mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 300)
            return
        }

        try {
            val found = findAndClickNode(root, setOf("Home"), setOf("home_tab", "pivot_bar_item"))
            if (!found) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 300)
            }
        } finally {
            root.recycle()
        }

        lastBlockTime = System.currentTimeMillis()
        scope.launch { repository.updateBlocksCount(currentState.blocksCount + 1) }
    }

    private fun detectFacebookReels(root: AccessibilityNodeInfo): Boolean {
        val stack = java.util.Stack<AccessibilityNodeInfo>()
        stack.push(root)

        while (stack.isNotEmpty()) {
            val node = stack.pop()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val nodeText = node.text?.toString()?.lowercase() ?: ""

            // SIGNAL 1: Reels tab selected
            val isReelsTab = (contentDesc == "reels" || contentDesc.contains("reels") || contentDesc.contains("video"))
                && (node.isSelected || node.isChecked || node.isFocused)
            if (isReelsTab) return true

            // SIGNAL 2: "Reels" text on selected element
            val isReelsText = (nodeText == "reels" || nodeText.contains("reels"))
                && (node.isSelected || node.isChecked)
            if (isReelsText) return true

            // SIGNAL 3: Internal view IDs
            if (viewId.contains("reels_viewer_root") || viewId.contains("reels_swipe_refresh")) return true
            if (viewId.contains("reels_tab") || viewId.contains("video_channel")) return true
            if (viewId.contains("video_timeline_fragment") || viewId.contains("clips_viewer")) return true

            // SIGNAL 5: Video tab selected
            val isVideoTab = (contentDesc == "video" || nodeText == "video")
                && (node.isSelected || node.isChecked || node.isFocused)
            if (isVideoTab) return true

            // SIGNAL 6: Arabic "ريلز" with state
            val isArabicReels = (contentDesc.contains("ريلز") || nodeText.contains("ريلز"))
                && (node.isSelected || node.isChecked || node.isFocused)
            if (isArabicReels) return true

            // SIGNAL 7: Arabic "فيديو" with state
            val isArabicVideoTab = (contentDesc.contains("فيديو") || nodeText.contains("فيديو"))
                && (node.isSelected || node.isChecked || node.isFocused)
            if (isArabicVideoTab) return true

            // SIGNAL 8: Full-screen layout (left vertical buttons)
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val isVerticalReelsButton = bounds.left < 200 && bounds.top > 800
            if (isVerticalReelsButton && node.isClickable &&
                (contentDesc.contains("like") || contentDesc.contains("comment") ||
                 contentDesc.contains("إعجاب") || contentDesc.contains("تعليق"))) return true

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.push(it) }
            }
        }
        return false
    }

    private fun navigateFacebookHome() {
        if (!canActTimed()) return

        val root = rootInActiveWindow ?: run {
            performGlobalAction(GLOBAL_ACTION_BACK)
            mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 300)
            return
        }

        try {
            val found = findAndClickNode(root, setOf("Home", "News Feed"), setOf("tab"))
            if (!found) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 300)
            }
        } finally {
            root.recycle()
        }

        lastBlockTime = System.currentTimeMillis()
        scope.launch { repository.updateBlocksCount(currentState.blocksCount + 1) }
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

    private fun canActTimed(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < BLOCK_INTERVAL_MS) return false
        lastBlockTime = now
        return true
    }

    private fun isFullBlocked(packageName: String): Boolean {
        if (currentState.whitelistApps.contains(packageName)) return false

        return (packageName == "com.instagram.android" && currentState.instagramBlocked) ||
               (packageName == "com.snapchat.android" && currentState.snapchatBlocked) ||
               (packageName == "com.twitter.android" && currentState.twitterBlocked) ||
               ((packageName == "com.zhiliaoapp.musically" || packageName == "com.ss.android.ugc.trill") && currentState.tiktokBlocked) ||
               (packageName == "com.google.android.youtube" && currentState.youtubeMode == "full") ||
               (packageName == "com.facebook.katana" && currentState.facebookMode == "full") ||
               currentState.blacklistApps.contains(packageName) ||
               AIExplorerService.isAppBanned(packageName)
    }

    private fun executeFullBlock(packageName: String) {
        val now = System.currentTimeMillis()
        val last = lastActionTimes[packageName] ?: 0L
        if (now - last < BLOCK_COOLDOWN) return
        lastActionTimes[packageName] = now

        performGlobalAction(GLOBAL_ACTION_HOME)

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
            "com.facebook.katana" -> "Facebook"
            else -> "App"
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
