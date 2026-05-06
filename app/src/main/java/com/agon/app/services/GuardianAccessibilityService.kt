package com.agon.app.services

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
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

class GuardianAccessibilityService : AccessibilityService() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var repository: GuardianRepository
    private var currentState: GuardianState = GuardianState()

    private var debounceJob: Job? = null
    private var foregroundTrackerJob: Job? = null



    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = GuardianRepository(applicationContext)
        repository.guardianStateFlow.onEach { state ->
            currentState = state
        }.launchIn(scope)

        startForegroundTracker()
    }

    // Layer 1: Polling with UsageStatsManager (Runs every 1.5 seconds)
    private fun startForegroundTracker() {
        foregroundTrackerJob?.cancel()
        foregroundTrackerJob = scope.launch {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            while (isActive) {
                if (currentState.isShieldActive) {
                    val time = System.currentTimeMillis()
                    val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 2000, time)
                    if (stats != null && stats.isNotEmpty()) {
                        val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
                        val foregroundApp = sortedStats.firstOrNull()?.packageName
                        
                        if (foregroundApp != null && isFullBlocked(foregroundApp)) {
                            executeFullBlock(foregroundApp)
                        }
                    }
                }
                delay(1500) // Poll every 1.5s
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !currentState.isShieldActive) return
        val packageName = event.packageName?.toString() ?: return

        if (currentState.whitelistApps.contains(packageName)) return

        // Layer 2: Instant Window State Change (Full Block)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (isFullBlocked(packageName)) {
                executeFullBlock(packageName)
                return
            }
        }

        // Layer 3: Window Content Changed (Partial Block with Debounce)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            
            // Fast State Detection Check First
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val className = event.className?.toString() ?: ""
                
                if (packageName.contains("youtube") && currentState.youtubeMode == "shorts") {
                    if (className.contains("Shorts", ignoreCase = true) || className.contains("ReelPlayerFragment", ignoreCase = true)) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        return
                    }
                }
                
                if (packageName.contains("facebook") && currentState.facebookMode == "reels") {
                    if (className.contains("Reel", ignoreCase = true) || className.contains("Clips", ignoreCase = true)) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        return
                    }
                }
            }

            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(150) // 150ms Debounce to save battery and wait for UI to settle
                
                val root = rootInActiveWindow ?: return@launch
                
                // Partial Block: YouTube Shorts
                if (packageName.contains("youtube") && currentState.youtubeMode == "shorts") {
                    val score = calculateShortsScore(root, packageName)
                    if (score >= 40) {
                        Log.d("GuardianService", "YouTube Shorts Detected (Score: $score) - Forcing Back")
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        return@launch
                    }
                }
                
                // Partial Block: Facebook Reels
                if (packageName.contains("facebook") && currentState.facebookMode == "reels") {
                    val score = calculateShortsScore(root, packageName)
                    if (score >= 40) {
                        Log.d("GuardianService", "Facebook Reels Detected (Score: $score) - Forcing Back")
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        return@launch
                    }
                }
            }
        }
    }

    private fun isFullBlocked(packageName: String): Boolean {
        if (currentState.whitelistApps.contains(packageName)) return false
        
        return (packageName == "com.instagram.android" && currentState.instagramBlocked) ||
               (packageName == "com.snapchat.android" && currentState.snapchatBlocked) ||
               (packageName == "com.twitter.android" && currentState.twitterBlocked) ||
               ((packageName == "com.zhiliaoapp.musically" || packageName == "com.ss.android.ugc.trill") && currentState.tiktokBlocked) ||
               (packageName == "com.google.android.youtube" && currentState.youtubeMode == "full") ||
               (packageName == "com.facebook.katana" && currentState.facebookMode == "full") ||
               currentState.blacklistApps.contains(packageName)
    }

    private fun executeFullBlock(packageName: String) {
        // 1. Force Home
        performGlobalAction(GLOBAL_ACTION_HOME)
        
        // 2. Launch Block Screen Overlay
        val intent = Intent(this, BlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("APP_NAME", getAppNameFromPackage(packageName))
        }
        startActivity(intent)
        
        // 3. Increment Stats
        scope.launch {
            repository.updateBlocksCount(currentState.blocksCount + 1)
        }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return when (packageName) {
            "com.instagram.android" -> "Instagram"
            "com.snapchat.android" -> "Snapchat"
            "com.twitter.android" -> "X (Twitter)"
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> "TikTok"
            "com.google.android.youtube" -> "YouTube"
            "com.facebook.katana" -> "Facebook"
            "com.reddit.frontpage" -> "Reddit"
            "com.whatsapp" -> "WhatsApp"
            "org.telegram.messenger" -> "Telegram"
            "com.discord" -> "Discord"
            "com.netflix.mediaclient" -> "Netflix"
            "com.spotify.music" -> "Spotify"
            "tv.twitch.android.app" -> "Twitch"
            "com.pinterest" -> "Pinterest"
            "com.linkedin.android" -> "LinkedIn"
            else -> packageName.split(".").last().replaceFirstChar { it.uppercase() }
        }
    }

    private fun calculateShortsScore(root: AccessibilityNodeInfo, pkg: String): Int {
        var score = 0
        
        // Use Stack to avoid deep recursion memory issues
        val stack = java.util.Stack<AccessibilityNodeInfo>()
        stack.push(root)

        while (stack.isNotEmpty()) {
            val node = stack.pop()
            
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

            if (pkg.contains("youtube")) {
                if (viewId.contains("reel_watch_fragment_root") || viewId.contains("reel_recycler")) score += 40
                if (viewId.contains("reel_player_view")) score += 20
                if (viewId.contains("shorts_inner_container")) score += 20
                if (contentDesc.contains("shorts") && node.isClickable) score += 10
            } else if (pkg.contains("facebook")) {
                if (viewId.contains("reels_viewer_root") || viewId.contains("reels_swipe_refresh_layout")) score += 40
                if (viewId.contains("video_timeline_fragment")) score += 20
                if (contentDesc.contains("reels") && node.isClickable) score += 10
            }

            // Early exit if block threshold reached
            if (score >= 40) return score

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.push(it) }
            }
        }
        return score
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
