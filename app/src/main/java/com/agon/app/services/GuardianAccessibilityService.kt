package com.agon.app.services

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.agon.app.AppBlockerService
import com.agon.app.FacebookBlockerService
import com.agon.app.GuardianApp
import com.agon.app.YouTubeBlockerService
import com.agon.app.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class GuardianAccessibilityService : AccessibilityService() {

    companion object {
        private const val ANTI_SCROLL_WINDOW_MS = 3_000L
        private const val ANTI_SCROLL_THRESHOLD = 5
        private const val ANTI_SCROLL_COOLDOWN_MS = 10_000L

        private val FEED_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.instagram.android",
            "com.snapchat.android",
            "com.twitter.android",
            "com.zhiliaoapp.musically",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.tiktok.tiktok"
        )
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repo: AppRepository by lazy {
        (applicationContext as GuardianApp).repository
    }

    private val scrollTimestamps = mutableMapOf<String, MutableList<Long>>()
    private var lastAntiScrollBlock = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.d("GuardianAccessibilityService connected")
        startCoordinatedServices()
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
        scrollTimestamps.clear()
        Timber.d("GuardianAccessibilityService destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        if (packageName.startsWith("com.agon.")) return
        if (packageName == "com.android.systemui") return
        if (packageName == "android") return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handleScrollEvent(packageName)
            }
        }
    }

    private fun handleScrollEvent(packageName: String) {
        if (packageName !in FEED_PACKAGES) return

        val now = System.currentTimeMillis()

        ioScope.launch {
            val shieldActive = try {
                repo.getAppSettings().isShieldActive()
            } catch (_: Exception) { false }
            if (!shieldActive) return@launch
        }

        val timestamps = scrollTimestamps.getOrPut(packageName) { mutableListOf() }
        timestamps.add(now)

        timestamps.removeAll { now - it > ANTI_SCROLL_WINDOW_MS }

        if (timestamps.size >= ANTI_SCROLL_THRESHOLD) {
            if (now - lastAntiScrollBlock > ANTI_SCROLL_COOLDOWN_MS) {
                lastAntiScrollBlock = now
                Timber.d("Anti-scroll triggered for $packageName (${timestamps.size} scrolls in ${ANTI_SCROLL_WINDOW_MS}ms)")
                triggerAntiScrollBlock(packageName)
            }
            timestamps.clear()
        }
    }

    private fun triggerAntiScrollBlock(packageName: String) {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun startCoordinatedServices() {
        val context = applicationContext
        ioScope.launch {
            try {
                val shieldActive = repo.getAppSettings().isShieldActive()
                if (!shieldActive) return@launch

                startService(context, Intent(context, AppBlockerService::class.java))
            } catch (_: Exception) {}
        }
    }

    private fun startService(context: Context, intent: Intent) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Timber.w(e, "GuardianAccessibilityService: failed to start service")
        }
    }

    override fun onInterrupt() {
        Timber.d("GuardianAccessibilityService interrupted")
    }
}
