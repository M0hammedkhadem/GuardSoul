package com.agon.app.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.GuardianApp
import com.agon.app.blocking.BlockOverlayHandler
import com.agon.app.blocking.FeedBlockOverlay
import com.agon.app.blocking.ShortstopEngine
import com.agon.app.ui.BlockActivity
import timber.log.Timber

class GuardSoulAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile private var instance: GuardSoulAccessibilityService? = null
        val current: GuardSoulAccessibilityService? get() = instance
    }

    private lateinit var shortstop: ShortstopEngine
    private lateinit var feedBlockOverlay: FeedBlockOverlay
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_SELECTED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 50
            packageNames = null
        }
        setServiceInfo(info)

        feedBlockOverlay = FeedBlockOverlay(this)

        shortstop = ShortstopEngine(this).also {
            it.blockOverlayHandler = BlockOverlayHandler { pkg -> blockApp(pkg) }
            it.feedBlockOverlay = feedBlockOverlay
            it.start()
        }

        Timber.d("GuardSoulAccessibilityService connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        if (::shortstop.isInitialized) shortstop.stop()
        if (::feedBlockOverlay.isInitialized) feedBlockOverlay.dismiss()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        if (::shortstop.isInitialized) shortstop.stop()
        if (::feedBlockOverlay.isInitialized) feedBlockOverlay.dismiss()
        return true
    }

    override fun onInterrupt() {}

    fun blockApp(pkg: String) {
        val appName = try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkg
        }

        Timber.w("Blocking App: $appName ($pkg)")
        performGlobalAction(GLOBAL_ACTION_BACK)

        mainHandler.postDelayed({
            try {
                val intent = Intent(this, BlockActivity::class.java).apply {
                    putExtra(BlockActivity.EXTRA_APP_NAME, appName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start BlockActivity")
            }
        }, 150L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val app = applicationContext as? GuardianApp ?: return
        if (!app.repository.getAppSettings().isShieldActiveSync()) return

        val pkg = event.packageName?.toString() ?: return
        var root: AccessibilityNodeInfo? = null

        try {
            root = rootInActiveWindow
            if (::shortstop.isInitialized && root != null) {
                shortstop.onAccessibilityEvent(event, root)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing event from $pkg")
        } finally {
            try {
                root?.recycle()
            } catch (_: Exception) {}
        }
    }
}