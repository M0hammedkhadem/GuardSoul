package com.agon.app.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import com.agon.app.GuardianApp
import com.agon.app.R
import com.agon.app.blocking.BlockOverlayHandler
import com.agon.app.blocking.FacebookReelsEngine
import com.agon.app.blocking.HaasEngine
import com.agon.app.blocking.KeywordDetector
import com.agon.app.blocking.SettingsBlockOverlay
import com.agon.app.blocking.ShortstopEngine
import com.agon.app.ui.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class GuardSoulAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile private var instance: GuardSoulAccessibilityService? = null
        val current: GuardSoulAccessibilityService? get() = instance
    }

    private lateinit var shortstop: ShortstopEngine
    private lateinit var facebookReels: FacebookReelsEngine
    private lateinit var keywordDetector: KeywordDetector
    private var haasEngine: HaasEngine? = null
    private var settingsBlockOverlay: SettingsBlockOverlay? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // Fallback overlay (non-interactive, used if BlockActivity fails)
    private var fallbackOverlayView: View? = null
    private var fallbackOverlayDismiss: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = serviceInfo
        if (info != null) {
            info.apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                notificationTimeout = 50
                packageNames = null
            }
            setServiceInfo(info)
        }

        shortstop = ShortstopEngine(this).also {
            it.blockOverlayHandler = BlockOverlayHandler { pkg -> blockApp(pkg) }
            it.start()
        }

        facebookReels = FacebookReelsEngine(this).also {
            it.start(this.applicationContext as com.agon.app.GuardianApp)
        }

        keywordDetector = KeywordDetector(this).also {
            it.start()
        }

        // HAAS Engine: Hybrid AI-Accessibility Shield (replaces NsfwScannerService polling)
        haasEngine = HaasEngine(this).also {
            it.start()
        }

        // Settings Block Overlay: prevents access to GuardSoul's own app info / device admin settings
        settingsBlockOverlay = SettingsBlockOverlay(this).also {
            // Observe uninstall protection setting and enable/disable accordingly
            val settings = (applicationContext as GuardianApp).repository.getAppSettings()
            scope.launch {
                settings.uninstallProtectionEnabledFlow.collect { enabled ->
                    it.setEnabled(enabled)
                }
            }
        }

        createFallbackOverlay()
        Timber.d("GuardSoulAccessibilityService connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFallbackOverlay()
        if (instance === this) instance = null
        try { shortstop.stop() } catch (_: Exception) {}
        try { facebookReels.stop() } catch (_: Exception) {}
        try { keywordDetector.shutdown() } catch (_: Exception) {}
        try { haasEngine?.stop() } catch (_: Exception) {}
        try { settingsBlockOverlay?.destroy() } catch (_: Exception) {}
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Timber.d("GuardSoulAccessibilityService: onUnbind")
        hideFallbackOverlay()
        try { shortstop.stop() } catch (_: Exception) {}
        try { facebookReels.stop() } catch (_: Exception) {}
        try { keywordDetector.shutdown() } catch (_: Exception) {}
        try { haasEngine?.stop() } catch (_: Exception) {}
        try { settingsBlockOverlay?.destroy() } catch (_: Exception) {}
        if (instance === this) instance = null
        return true
    }

    override fun onInterrupt() {
        try { shortstop.onInterrupt() } catch (_: Exception) {}
        try { facebookReels.onInterrupt() } catch (_: Exception) {}
        try { keywordDetector.shutdown() } catch (_: Exception) {}
        try { haasEngine?.onInterrupt() } catch (_: Exception) {}
        try { settingsBlockOverlay?.hideOverlay() } catch (_: Exception) {}
    }

    fun blockApp(pkg: String) {
        val appName = try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) { pkg }

        Timber.d("Blocking $appName ($pkg)")

        performGlobalAction(GLOBAL_ACTION_HOME)

        // Show block screen after HOME is processed
        mainHandler.postDelayed({
            try {
                val intent = Intent(this, BlockActivity::class.java).apply {
                    putExtra(BlockActivity.EXTRA_APP_NAME, appName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(intent)
                Timber.d("BlockActivity started for $appName")
            } catch (e: Exception) {
                Timber.w(e, "BlockActivity failed, showing fallback overlay")
                showFallbackOverlay(appName)
            }
        }, 150L)
    }

    // ── Fallback overlay (TYPE_ACCESSIBILITY_OVERLAY, non-interactive) ──

    private fun createFallbackOverlay() {
        try {
            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            fallbackOverlayView = inflater.inflate(R.layout.activity_block, null)
            val type = if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                flags,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.FILL }
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(fallbackOverlayView, params)
            fallbackOverlayView?.visibility = View.GONE
            Timber.d("Fallback overlay created")
        } catch (e: Exception) {
            Timber.w(e, "Fallback overlay creation failed")
            fallbackOverlayView = null
        }
    }

    private fun showFallbackOverlay(appName: String) {
        if (fallbackOverlayView == null) return
        try {
            fallbackOverlayView?.findViewById<TextView>(R.id.tv_overlay_app)?.text =
                "$appName is currently blocked by GuardSoul."
            fallbackOverlayView?.visibility = View.VISIBLE
            Timber.d("Fallback overlay shown for $appName")

            fallbackOverlayDismiss?.let { mainHandler.removeCallbacks(it) }
            val r = Runnable {
                fallbackOverlayView?.visibility = View.GONE
                Timber.d("Fallback overlay auto-dismissed")
            }
            fallbackOverlayDismiss = r
            mainHandler.postDelayed(r, 10000L)
        } catch (e: Exception) {
            Timber.w(e, "showFallbackOverlay failed")
        }
    }

    private fun hideFallbackOverlay() {
        fallbackOverlayDismiss?.let { mainHandler.removeCallbacks(it) }
        fallbackOverlayDismiss = null
        fallbackOverlayView?.visibility = View.GONE
    }

    // ── Accessibility events ──

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Check shield state first - all features require shield to be active
        val settings = (applicationContext as GuardianApp).repository.getAppSettings()
        if (!settings.isShieldActiveSync()) return

        val eventType = event.eventType
        val pkg = event.packageName?.toString() ?: return

        // Settings Block Overlay: prevent access to GuardSoul's own settings
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val root = rootFromEvent(event, eventType)
            try {
                settingsBlockOverlay?.checkAndBlock(root.root, pkg)
            } finally {
                root.recycle()
            }
        }

        // 0. Keyword Detection (runs on text changes for browsers and input fields)
        if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            ::keywordDetector.isInitialized) {
            keywordDetector.onAccessibilityEvent(event)
        }

        // 0.5 HAAS Engine: Hybrid AI-Accessibility Shield (event-driven, battery-efficient)
        haasEngine?.onAccessibilityEvent(event)

        // 1. Facebook Reels — separate engine, handles its own full-block logic
        if (::facebookReels.isInitialized) {
            val rootInfo = rootFromEvent(event, eventType)
            try {
                facebookReels.onAccessibilityEvent(event, rootInfo.root)
            } finally {
                rootInfo.recycle()
            }
            // If Facebook handled it (full block or feed block), skip Shortstop for this event
            if (pkg == "com.facebook.katana" || pkg == "com.facebook.lite") return
        }

        // 2. Shortstop (YouTube, Instagram, TikTok, Snapchat, Twitter/X)
        if (!::shortstop.isInitialized) return

        if (shortstop.tryFullBlock(pkg)) return

        // Use event.source when available (VIEW_CLICKED, some window events) — avoids expensive rootInActiveWindow IPC
        val source = event.source
        val useSource = source != null && (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)

        var root: AccessibilityNodeInfo?
        var shouldRecycle = false
        if (useSource) {
            root = source
        } else {
            try {
                root = rootInActiveWindow
                shouldRecycle = root != null
            } catch (e: Exception) {
                Timber.w(e, "rootInActiveWindow failed")
                root = null
            }
        }
        try {
            shortstop.onAccessibilityEvent(event, root, useSource)
        } finally {
            if (shouldRecycle && root != null) {
                try { root.recycle() } catch (_: Exception) {}
            }
            // Don't recycle event.source — framework owns it
        }
    }

    private class RootRef(val root: AccessibilityNodeInfo?, private val owned: Boolean) {
        fun recycle() { if (owned && root != null) try { root.recycle() } catch (_: Exception) {} }
    }

    private fun rootFromEvent(event: AccessibilityEvent, eventType: Int): RootRef {
        val source = event.source
        if (source != null && (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
            return RootRef(source, false)
        }
        return try { RootRef(rootInActiveWindow, true) } catch (e: Exception) {
            Timber.w(e, "rootInActiveWindow failed")
            RootRef(null, false)
        }
    }
}
