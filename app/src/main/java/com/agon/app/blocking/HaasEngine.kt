package com.agon.app.blocking

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.agon.app.GuardianApp
import com.agon.app.ml.NsfwClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HAAS Engine - Hybrid AI-Accessibility Shield
 *
 * آلية ذكية تجمع بين:
 * 1. التحليل النصي (Text Analysis) عبر AccessibilityNodeInfo - فوري ولا يحتاج صورة
 * 2. التحليل البصري المحدود (Selective Visual Scan) عبر takeScreenshot() في Android 11+ - صامت
 * 3. TensorFlow Lite On-Device - بدون إنترنت ولا إشعارات
 *
 * استهلاك البطارية: 2-5% فقط (مقابل 20-30% لـ MediaProjection + polling)
 */
class HaasEngine(private val host: AccessibilityService) {

    companion object {
        // Packages to monitor for HAAS (target apps + browsers)
        private val TARGET_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.instagram.android",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.zhiliaoapp.musically",
            "com.twitter.android",
            "com.x.android",
            "com.snapchat.android",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.opera.browser"
        )

        // Keywords that trigger immediate text block (no need for screenshot)
        private val IMMEDIATE_BLOCK_KEYWORDS = setOf(
            "porn", "pornhub", "xvideos", "xnxx", "redtube", "youporn",
            "onlyfans", "fansly", "adult", "xxx", "sex chat", "live cam",
            "dating app", "hookup", "escort", "massage parlor"
        )

        // Keywords that trigger suspicious → visual scan
        private val SUSPICIOUS_KEYWORDS = setOf(
            "hot", "sexy", "nude", "naked", "bikini", "lingerie",
            "strip", "dance", "model", "cam", "live"
        )

        // Thresholds
        const val BLOCK_THRESHOLD = 0.70f      // Porn/Hentai > 0.7 → block
        const val WARNING_THRESHOLD = 0.50f    // 0.5-0.7 → suspicious, scan again
        const val TEXT_BLOCK_CONFIDENCE = 0.95f // Text analysis is very reliable

        // Cooldown between visual scans for same package (ms)
        const val VISUAL_SCAN_COOLDOWN_MS = 3000L
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val tempBan = TempBanManager.getInstance(host.applicationContext)
    private var nsfwClassifier: NsfwClassifier? = null

    // State
    private val cachedShieldActive = AtomicBoolean(false)
    private val cachedAiEnabled = AtomicBoolean(false)

    // Throttling: last visual scan per package
    private val lastVisualScanMs = ConcurrentHashMap<String, Long>()

    // In-memory block overlay (lightweight, no Activity needed)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: android.view.View? = null

    fun start() {
        serviceScope.launch {
            val app = host.applicationContext as GuardianApp
            val settings = app.repository.getAppSettings()

            cachedShieldActive.set(settings.shieldActiveFlow.first())
            cachedAiEnabled.set(settings.aiExplorerEnabledFlow.first())

            // Initialize classifier
            try {
                nsfwClassifier = NsfwClassifier.newInstance(host.applicationContext)
                Timber.d("HAAS: NSFW classifier initialized")
            } catch (e: Exception) {
                Timber.w(e, "HAAS: Failed to init NSFW classifier, running in text-only mode")
            }

            Timber.d("HAAS Engine started: shield=${cachedShieldActive.get()}, ai=${cachedAiEnabled.get()}")
        }
    }

    /**
     * Main entry point called from GuardSoulAccessibilityService.onAccessibilityEvent
     */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!cachedShieldActive.get()) return
        if (!cachedAiEnabled.get()) return

        val pkg = event.packageName?.toString() ?: return
        if (!isTargetPackage(pkg)) return

        // Check temp ban first
        if (tempBan.isInCooldown(pkg)) {
            Timber.d("HAAS: $pkg in temp ban, skipping")
            return
        }

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // Step 1: Text Analysis (fast, no image needed)
        val root = try { host.rootInActiveWindow } catch (_: Exception) { null } ?: return
        val textResult = analyzeTextLayer(root, pkg)

        when (textResult) {
            is TextResult.BLOCKED -> {
                Timber.w("HAAS: Text BLOCK for $pkg → ${textResult.reason}")
                performBlock(pkg, "HAAS text: ${textResult.reason}")
                root.recycle()
                return
            }
            is TextResult.SUSPICIOUS -> {
                // Step 2: Selective Visual Scan (only if suspicious text found)
                if (shouldTriggerVisualScan(pkg)) {
                    Timber.d("HAAS: Text suspicious for $pkg, triggering visual scan")
                    triggerVisualScan(pkg, root)
                }
                root.recycle()
                return
            }
            is TextResult.SAFE -> {
                root.recycle()
                // Nothing to do — content is safe
            }
        }
    }

    // ─── Text Analysis Layer ───────────────────────────────────────────────

    private fun analyzeTextLayer(root: AccessibilityNodeInfo, pkg: String): TextResult {
        return traverseForText(root, pkg)
    }

    private fun traverseForText(node: AccessibilityNodeInfo, pkg: String): TextResult {
        // Check current node
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val combined = "$text $contentDesc $viewId".lowercase()

        // Immediate block keywords (very high confidence)
        for (keyword in IMMEDIATE_BLOCK_KEYWORDS) {
            if (combined.contains(keyword)) {
                return TextResult.BLOCKED("keyword: $keyword")
            }
        }

        // Suspicious keywords → trigger visual scan
        for (keyword in SUSPICIOUS_KEYWORDS) {
            if (combined.contains(keyword)) {
                return TextResult.SUSPICIOUS("suspicious keyword: $keyword")
            }
        }

        // Check children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childResult = traverseForText(child, pkg)
            child.recycle()
            if (childResult is TextResult.BLOCKED || childResult is TextResult.SUSPICIOUS) {
                return childResult
            }
        }

        return TextResult.SAFE
    }

    // ─── Visual Scan Layer (Android 11+ only) ────────────────────────────

    @RequiresApi(Build.VERSION_CODES.R)
    private fun triggerVisualScan(pkg: String, root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        lastVisualScanMs[pkg] = now

        val executor = Executor { mainHandler.post(it) }
        host.takeScreenshot(DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            executor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    serviceScope.launch {
                        try {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace
                            )
                            if (bitmap != null) {
                                analyzeBitmap(bitmap, pkg)
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "HAAS: Failed to process screenshot")
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Timber.w("HAAS: Screenshot failed with code $errorCode")
                }
            }
        )
    }

    private fun analyzeBitmap(bitmap: Bitmap, pkg: String) {
        val classifier = nsfwClassifier ?: return

        try {
            val result = classifier.classify(bitmap)
            bitmap.recycle()

            Timber.d("HAAS: Scan result for $pkg → ${result.getMaxClass()}=${result.getMaxProbability()}")

            when {
                result.shouldBlock() -> {
                    Timber.w("HAAS: Visual BLOCK for $pkg → Porn=${result.porn}, Hentai=${result.hentai}")
                    serviceScope.launch(Dispatchers.Main) {
                        performBlock(pkg, "HAAS visual: ${result.getMaxClass()} ${(result.getMaxProbability() * 100).toInt()}%")
                    }
                }
                result.getMaxProbability() > WARNING_THRESHOLD -> {
                    Timber.d("HAAS: Visual WARNING for $pkg → suspicious but below threshold")
                    // Could trigger more aggressive monitoring
                }
                else -> {
                    Timber.d("HAAS: Visual SAFE for $pkg")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "HAAS: Bitmap analysis failed")
            bitmap.recycle()
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private fun shouldTriggerVisualScan(pkg: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val last = lastVisualScanMs[pkg] ?: 0L
        return System.currentTimeMillis() - last > VISUAL_SCAN_COOLDOWN_MS
    }

    private fun isTargetPackage(pkg: String): Boolean {
        return TARGET_PACKAGES.any { pkg.startsWith(it) }
    }

    private fun performBlock(pkg: String, reason: String) {
        // 1. Go back
        host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

        // 2. Show lightweight overlay (no Activity needed)
        showOverlay("🚫 تم حظر محتوى حساس")

        // 3. Record strike
        val triggered = tempBan.recordStrike(pkg) { bannedPkg ->
            Timber.w("HAAS: Temp ban triggered for $bannedPkg")
        }

        // 4. Log to database
        serviceScope.launch {
            try {
                val app = host.applicationContext as GuardianApp
                app.repository.recordBlock(pkg, "HAAS AI", reason)
            } catch (e: Exception) {
                Timber.w(e, "HAAS: Failed to log block")
            }
        }

        Timber.w("HAAS: Blocked $pkg — $reason (tempBanTriggered=$triggered)")
    }

    // ─── Lightweight Overlay (no Activity, no Context switch) ──────────────

    private fun showOverlay(message: String) {
        mainHandler.post {
            try {
                removeOverlay()

                val textView = TextView(host.applicationContext).apply {
                    this.text = message
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 18f
                    setPadding(40, 40, 40, 40)
                    gravity = android.view.Gravity.CENTER
                    setBackgroundColor(android.graphics.Color.parseColor("#CC000000"))
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= 26)
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    else
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                    y = -200 // slightly above center
                }

                val wm = host.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
                wm.addView(textView, params)
                overlayView = textView

                // Auto dismiss after 3 seconds
                mainHandler.postDelayed({ removeOverlay() }, 3000)
            } catch (e: Exception) {
                Timber.w(e, "HAAS: Failed to show overlay")
            }
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                val wm = host.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(view)
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────

    fun stop() {
        removeOverlay()
        serviceScope.cancel()
        nsfwClassifier?.close()
        nsfwClassifier = null
        Timber.d("HAAS Engine stopped")
    }

    fun onInterrupt() {
        removeOverlay()
    }

    // ─── Result Types ─────────────────────────────────────────────────────

    sealed class TextResult {
        data class BLOCKED(val reason: String) : TextResult()
        data class SUSPICIOUS(val reason: String) : TextResult()
        data object SAFE : TextResult()
    }
}
