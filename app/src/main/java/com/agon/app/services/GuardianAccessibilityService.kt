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

private fun CharSequence.containsAny(vararg terms: String): Boolean =
    terms.any { this.contains(it, ignoreCase = true) }

class GuardianAccessibilityService : AccessibilityService() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var repository: GuardianRepository
    private var currentState: GuardianState = GuardianState()

    private var debounceJob: Job? = null
    private val BLOCK_COOLDOWN = 1500L
    private val FULL_BLOCK_COOLDOWN = 300L
    private val TAG = "GuardianService"
    private val lastActionTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val fullBlockLastTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastYoutubeBlockTime = 0L
    private var lastFacebookBlockTime = 0L
    private val YOUTUBE_BLOCK_INTERVAL_MS = 2000L
    private val FACEBOOK_BLOCK_INTERVAL_MS = 1500L

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

            // PATH A: Direct Reels/Video tab navigation (fast path, no debounce)
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val className = event.className?.toString() ?: ""
                if (className.containsAny("Reel", "Clips", "Video", "Player") ||
                    event.text?.any { it.containsAny("ريلز", "reels") } == true) {
                    navigateFacebookHome()
                    return
                }
            }

            // PATH B: User tapped something → check if it leads to Reels/Fullscreen video
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val sourceNode = event.source ?: return
                val clickedViewId = sourceNode.viewIdResourceName?.lowercase() ?: ""
                val clickedDesc = sourceNode.contentDescription?.toString()?.lowercase() ?: ""
                val clickedText = sourceNode.text?.toString()?.lowercase() ?: ""
                sourceNode.recycle()

                val isReelsTap = clickedViewId.containsAny(
                    "reel", "reels_tab", "video_tab", "clips", "video_home", "reels_viewer",
                    "video_container", "video_thumbnail", "video_player", "full_screen",
                    "inline_video"
                ) || clickedDesc.containsAny("reels", "ريلز", "فيديو", "video")
                  || clickedText.containsAny("reels", "ريلز")

                if (isReelsTap) {
                    navigateFacebookHome()
                    return
                }
            }

            // PATH C: Content changed → debounced scan, only structural Reels signals
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(200)
                    val root = rootInActiveWindow ?: return@launch
                    val detected = detectFacebookReelsSection(root)
                    root.recycle()
                    if (detected) {
                        Log.d(TAG, "Facebook Reels section detected")
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
        if (!canActYoutubeTimed()) return

        val root = rootInActiveWindow ?: run {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        try {
            val found = findAndClickNode(root, setOf("Home"), setOf("home_tab", "pivot_bar_item"))
            if (!found) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } finally {
            root.recycle()
        }

        lastYoutubeBlockTime = System.currentTimeMillis()
        scope.launch { repository.updateBlocksCount(currentState.blocksCount + 1) }
    }

    private fun detectFacebookReelsSection(root: AccessibilityNodeInfo): Boolean {
        val stack = java.util.Stack<AccessibilityNodeInfo>()
        stack.push(root)
        var count = 0
        val maxNodes = 100

        while (stack.isNotEmpty() && count < maxNodes) {
            val node = stack.pop()
            count++
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val nodeText = node.text?.toString()?.lowercase() ?: ""

            // SIGNAL 1: Reels/Video tab is SELECTED (user navigated there)
            val isSelectedTab = (node.isSelected || node.isChecked) &&
                (contentDesc.containsAny("reels", "ريلز", "video", "فيديو") ||
                 nodeText.containsAny("reels", "ريلز"))
            if (isSelectedTab) return true

            // SIGNAL 2: Full-screen Reels/Video player view IDs
            if (viewId.containsAny(
                "reels_viewer_root", "reels_swipe_refresh", "reel_viewer",
                "reel_player", "reels_tab", "video_channel", "clips_viewer",
                "video_timeline_fragment", "reel_container", "clips_container",
                "video_player", "full_screen", "fullscreen"
            )) return true

            for (i in 0 until node.childCount) {
                if (count >= maxNodes) break
                node.getChild(i)?.let { stack.push(it) }
            }
        }
        return false
    }

    private fun navigateFacebookHome() {
        if (!canActFacebookTimed()) return

        val root = rootInActiveWindow ?: run {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        try {
            val found = findAndClickNode(
                root,
                targetContentDesc = setOf("Home", "News Feed", "الرئيسية", "الصفحة الرئيسية"),
                targetViewIdSubstrings = setOf("home_tab", "tab_home", "tab")
            )
            if (!found) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } finally {
            root.recycle()
        }

        lastFacebookBlockTime = System.currentTimeMillis()
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
               (packageName == "com.facebook.katana" && currentState.facebookMode == "full") ||
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
            "com.facebook.katana" -> "Facebook"
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
