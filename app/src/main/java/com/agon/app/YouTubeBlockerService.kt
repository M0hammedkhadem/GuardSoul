package com.agon.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.agon.app.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class YouTubeBlockerService : AccessibilityService() {

    companion object {
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
        private const val REEL_PLAYER_ID = "com.google.android.youtube:id/reel_player"
        private const val BLOCK_COOLDOWN_MS = 1000L
        private const val VIDEO_BLOCK_NOTIFICATION_ID = 3001
        private const val MODE_REFRESH_INTERVAL_MS = 5000L

        private val SHORTS_DESCRIPTIONS = listOf("shorts", "شورت")
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val repo: AppRepository by lazy {
        (applicationContext as GuardianApp).repository
    }

    private var lastBlockTime = 0L
    private var youtubeMode = "off"
    private var lastModeRefresh = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshMode()
        Timber.d("YouTubeBlockerService connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName != YOUTUBE_PACKAGE && packageName != YOUTUBE_MUSIC_PACKAGE) return
        if (packageName == this.packageName) return

        refreshModeIfStale()

        if (youtubeMode != "shorts") return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val root = rootInActiveWindow ?: return
                try {
                    if (isShortsContent(root)) {
                        blockShorts(packageName)
                    }
                } finally {
                    root.recycle()
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val source = event.source ?: return
                try {
                    val desc = source.contentDescription?.toString() ?: ""
                    if (SHORTS_DESCRIPTIONS.any { desc.contains(it, true) }) {
                        blockShorts(packageName)
                    }
                } finally {
                    source.recycle()
                }
            }
        }
    }

    private fun refreshMode() {
        ioScope.launch {
            try {
                val settings = repo.getAppSettings()
                youtubeMode = settings.getYoutubeMode()
                lastModeRefresh = System.currentTimeMillis()
            } catch (_: Exception) {}
        }
    }

    private fun refreshModeIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastModeRefresh > MODE_REFRESH_INTERVAL_MS) {
            refreshMode()
        }
    }

    private fun isShortsContent(root: AccessibilityNodeInfo): Boolean {
        return hasNodeInTree(root) { node ->
            val cd = node.contentDescription?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName ?: ""
            val className = node.className?.toString() ?: ""

            val hasShortsDescription = SHORTS_DESCRIPTIONS.any { cd.contains(it) }
            val hasReelPlayerId = viewId == REEL_PLAYER_ID
            val hasShortsPlayerClass = className.contains("ShortsPlayer", true) ||
                    className.contains("ReelPlayer", true) ||
                    (className == "android.widget.FrameLayout" &&
                            (cd.contains("shorts") || viewId.contains("reel")))

            hasShortsDescription || hasReelPlayerId || hasShortsPlayerClass
        }
    }

    private fun hasNodeInTree(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (predicate(node)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (hasNodeInTree(child, predicate)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun blockShorts(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < BLOCK_COOLDOWN_MS) return
        lastBlockTime = now

        Timber.d("YouTubeBlockerService: blocking Shorts in $packageName")

        ioScope.launch {
            try {
                repo.recordBlock(packageName, getAppLabel(packageName), "shorts_reels_block")
            } catch (e: Exception) {
                Timber.e(e, "YouTubeBlockerService: failed to record block")
            }
        }

        performGlobalAction(GLOBAL_ACTION_BACK)

        showBlockNotification()
    }

    private fun showBlockNotification() {
        val notification = NotificationCompat.Builder(this, AppNotificationChannels.YOUTUBE_SHORTS)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle(getString(R.string.notification_youtube_shorts_title))
            .setContentText(getString(R.string.notification_youtube_shorts_text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(VIDEO_BLOCK_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Timber.w(e, "Notification permission not granted")
        }
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            pkg
        }
    }

    override fun onInterrupt() {
        Timber.d("YouTubeBlockerService interrupted")
    }
}
