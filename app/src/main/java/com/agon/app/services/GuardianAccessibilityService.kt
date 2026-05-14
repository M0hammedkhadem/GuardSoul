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
    private var lastFacebookBlockTime = 0L
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

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString() ?: ""
            if (isYouTube && currentState.youtubeMode == "shorts") {
                if (className.contains("Shorts", ignoreCase = true) ||
                    className.contains("ReelPlayerFragment", ignoreCase = true)) {
                    navigateYoutubeHome()
                    return
                }
            }
            if (isFacebook && currentState.facebookMode == "reels") {
                if (className.contains("Reel", ignoreCase = true) ||
                    className.contains("Clips", ignoreCase = true)) {
                    navigateFacebookHome()
                    return
                }
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(200)
                val root = rootInActiveWindow ?: return@launch
                try {
                    if (isYouTube && currentState.youtubeMode == "shorts") {
                        if (detectYoutubeShorts(root)) {
                            Log.d(TAG, "YouTube Shorts detected")
                            withContext(Dispatchers.Main) { navigateYoutubeHome() }
                            return@launch
                        }
                    }
                    if (isFacebook && currentState.facebookMode == "reels") {
                        val score = calculateShortsScore(root, packageName)
                        if (score >= 40) {
                            val now = System.currentTimeMillis()
                            if (now - lastFacebookBlockTime > 2500) {
                                lastFacebookBlockTime = now
                                Log.d(TAG, "Facebook Reels Detected (Score: $score) - Going Home")
                                performGlobalAction(GLOBAL_ACTION_HOME)
                            }
                            return@launch
                        }
                    }
                } finally {
                    root.recycle()
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

    private fun calculateShortsScore(root: AccessibilityNodeInfo, pkg: String): Int {
        var score = 0
        val stack = java.util.Stack<AccessibilityNodeInfo>()
        stack.push(root)

        while (stack.isNotEmpty()) {
            val node = stack.pop()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val nodeText = node.text?.toString()?.lowercase() ?: ""

            if (pkg.contains("youtube")) {
                // YouTube Shorts signals
                if (viewId.contains("reel_watch_fragment_root") || viewId.contains("reel_recycler")) score += 40
                if (viewId.contains("reel_player_view") || viewId.contains("shorts_inner_container")) score += 20
                if (viewId.contains("shorts_header_container") || viewId.contains("shorts_shelf_item_container")) score += 40
                if (contentDesc == "shorts" && (node.isSelected || node.isFocused || node.isChecked)) score += 40
                if (contentDesc.contains("shorts") && node.isClickable) score += 15

            } else if (pkg.contains("facebook")) {

                // SIGNAL 1: Tab "Reels" selected (any state indicator)
                val isReelsTab = (contentDesc == "reels" || contentDesc.contains("reels") || contentDesc.contains("video"))
                    && (node.isSelected || node.isChecked || node.isFocused || node.isActivated)
                if (isReelsTab) score += 40

                // SIGNAL 2: Text "Reels" on any selected/checked navigation element
                val isReelsText = (nodeText == "reels" || nodeText.contains("reels"))
                    && (node.isSelected || node.isChecked || node.isActivated)
                if (isReelsText) score += 40

                // SIGNAL 3: Internal view IDs
                if (viewId.contains("reels_viewer_root") || viewId.contains("reels_swipe_refresh")) score += 40
                if (viewId.contains("reels_tab") || viewId.contains("video_channel")) score += 40
                if (viewId.contains("video_timeline_fragment") || viewId.contains("clips_viewer")) score += 20

                // SIGNAL 4: "Reels" anywhere clickable — رُفع الوزن من 15 إلى 40
                // زائد check لأن زر Reels دائماً موجود في الـ bottom nav ومرئي حتى على الـ feed العادي
                // لذا نضيف شرط إضافي: حجم العقدة يجب أن يكون صغيراً (tab icon) أو نحسب عدد المرات
                if (contentDesc == "reels" && node.isClickable) score += 40  // exact match = tab icon
                if (nodeText == "reels" && node.isClickable) score += 40      // exact match

                // SIGNAL 5: Facebook Video tab (فيسبوك أعاد تسمية Reels أحياناً لـ "Video")
                val isVideoTab = (contentDesc == "video" || nodeText == "video")
                    && (node.isSelected || node.isChecked || node.isActivated || node.isFocused)
                if (isVideoTab) score += 40
            }

            if (score >= 40) return score

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.push(it) }
            }
        }
        return score
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
