package com.agon.app.blocking

import android.accessibilityservice.AccessibilityService
import androidx.annotation.RequiresApi
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.agon.app.AppNotificationChannels
import com.agon.app.R
import com.agon.app.guardianApp
import com.agon.app.utils.BounceHelper
import com.agon.app.utils.DetectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * AI Explorer engine — on-device NSFW image classification driven
 * entirely by the unified accessibility service.
 *
 * **Why this replaces the old MediaProjection-based path**
 *
 * The previous design used a dedicated foreground service
 * ([com.agon.app.AiScannerService]) that:
 *  - requested `MediaProjection` consent (system dialog every time
 *    the user enabled the feature, and again after every reboot),
 *  - captured the full screen every 1.5 s via `VirtualDisplay` +
 *    `ImageReader`,
 *  - ran TFLite inference on the resulting bitmap,
 *  - and required `MEDIA_PROJECTION`,
 *    `FOREGROUND_SERVICE_MEDIA_PROJECTION`, and
 *    `foregroundServiceType="mediaProjection"` — all red flags in
 *    Google Play review.
 *
 * The replacement uses the accessibility tree to identify the
 * foreground window and `AccessibilityNodeInfo.takeScreenshot()`
 * (API 33+) to grab a frame. There is no consent dialog, no
 * foreground-service promotion, and no special permissions.
 * On pre-API-33 devices the engine silently no-ops the
 * classification step; the rest of the engine (settings
 * subscription, strike counting, temp-block plumbing) continues
 * to function.
 *
 * **The strike flow is unchanged.** Three hits inside 4 minutes
 * flips a 15-minute temp block via [AiBlockTracker], which is
 * read by [com.agon.app.AppBlockerService.shouldBlock] and surfaces
 * as `ai_temp_block` to the user.
 *
 * @see NsfwClassifier
 * @see AiBlockTracker
 */
class AiExplorerEngine(private val host: AccessibilityService) {

    companion object {
        private const val TAG = "AiExplorerEngine"

        /**
         * Per-package cooldown for screenshot capture. Matches the
         * 1.5 s interval used by the old MediaProjection loop and
         * also keeps us well under the system's own screenshot
         * throttle (typically ~10/s across the whole service).
         */
        const val SCAN_INTERVAL_MS: Long = 1500L

        /** How long to back off when the system throttles us. */
        const val THROTTLE_BACKOFF_MS: Long = 10_000L

        private const val SENSITIVE_NOTIFICATION_ID = 5002
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(host)
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val app get() = host.guardianApp()!!
    private val repo get() = app.repository
    private val settings get() = repo.getAppSettings()
    private val aiBlockTracker get() = app.aiBlockTracker

    /**
     * TFLite-backed NSFW classifier. Lazily created so the engine
     * can start even if the model file is missing (heuristic
     * fallback path is still useful for testing).
     */
    private val classifier: NsfwClassifier by lazy { NsfwClassifier(host) }

    /** Inference lock — `Interpreter.run` is not thread-safe. */
    private val inferenceMutex = Mutex()

    /** Per-package last-screenshot timestamp. */
    private val lastScanAt = ConcurrentHashMap<String, Long>()

    /** Cached whitelist of exempted package names. */
    @Volatile private var cachedWhitelist: Set<String> = emptySet()

    /** Cached `aiScannerFlow` value. */
    @Volatile private var aiScannerEnabled: Boolean = false

    /**
     * When the system throttles a `takeScreenshot` call we set
     * this and refuse to issue more for [THROTTLE_BACKOFF_MS].
     */
    @Volatile private var screenshotThrottled: Boolean = false

    fun start() {
        serviceScope.launch {
            // Seed the cached values once on start.
            try { aiScannerEnabled = settings.aiScannerFlow.first() } catch (_: Exception) {}
            try {
                cachedWhitelist = repo.getBlocklist("whitelist", "apps")
                    .map { it.value }.toHashSet()
            } catch (_: Exception) { cachedWhitelist = emptySet() }
            try { aiBlockTracker.refreshFromStorage() } catch (_: Exception) {}

            // Subscribe to runtime changes.
            launch {
                settings.aiScannerFlow
                    .distinctUntilChanged()
                    .collect { aiScannerEnabled = it }
            }
        }
    }

    /**
     * Suspending teardown that waits for any in-flight inference to
     * complete before closing the TFLite interpreter. Cancelling
     * serviceScope is cooperative — an in-progress `classify()` call
     * would not be interrupted, so closing the interpreter while the
     * native side is still reading pixel bytes crashes the host
     * process (AE-002).
     */
    suspend fun stopAndJoin() {
        val job = serviceScope.coroutineContext[kotlinx.coroutines.Job]
        serviceScope.cancel()
        try { job?.children?.forEach { it.join() } } catch (_: Exception) {}
        try { classifier.close() } catch (_: Exception) {}
    }

    fun onInterrupt() {
        // Nothing long-running beyond `serviceScope` (which `stop()` handles).
    }

    /**
     * Called from the unified accessibility service for every
     * event. We early-out aggressively: the engine only does
     * real work when (a) AI Explorer is on, (b) the package isn't
     * self / system / whitelisted, and (c) the per-package
     * cooldown has elapsed.
     *
     * On API 33+, [AccessibilityNodeInfo.takeScreenshot] is used
     * to grab a frame of the active window. On older API levels
     * the call is unavailable and we silently skip the
     * classification — the engine is still alive so the rest of
     * the strike / temp-block plumbing continues to function.
     */
    fun onAccessibilityEvent(
        event: AccessibilityEvent,
        preFetchedRoot: AccessibilityNodeInfo? = null,
    ) {
        if (!aiScannerEnabled) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == host.packageName) return
        if (pkg in cachedWhitelist) return
        if (aiBlockTracker.isTempBlocked(pkg)) return

        val now = System.currentTimeMillis()
        val last = lastScanAt[pkg] ?: 0L
        if (now - last < SCAN_INTERVAL_MS) return
        lastScanAt[pkg] = now

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (screenshotThrottled) return

        // CF-001: prefer the pre-fetched root from the host; otherwise
        // call `host.rootInActiveWindow` and own the node.
        val ownedRoot = if (preFetchedRoot == null) host.rootInActiveWindow else null
        val root = preFetchedRoot ?: ownedRoot ?: return
        try {
            captureAndClassify(root, pkg)
        } finally {
            if (ownedRoot != null) {
                try { ownedRoot.recycle() } catch (_: Exception) {}
            }
        }
    }

    private fun captureAndClassify(node: AccessibilityNodeInfo, pkg: String) {
        // API 30 is the floor for the public screenshot API on
        // AccessibilityService. Extracted into its own function so
        // the @RequiresApi(30) annotation scopes the new symbols
        // — `TakeScreenshotCallback`, `ScreenshotResult`, etc. —
        // to one function body and the Kotlin API-level checker
        // is happy at the call sites where minSdk = 26.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            captureAndClassifyApi30(node, pkg)
        }
    }

    /**
     * Public-API screenshot path. Uses
     * [AccessibilityService.takeScreenshot] (API 30+, deprecated
     * in API 33 but still functional) instead of the hidden
     * [AccessibilityNodeInfo.takeScreenshot] (API 33+, marked
     * `@SystemApi` and absent from the public SDK jar). The
     * deprecated API is the only public way to capture a frame
     * of the foreground display from an accessibility service.
     *
     * The callback is invoked on [mainExecutor]. The
     * [AccessibilityService.ScreenshotResult] exposes a
     * [android.hardware.HardwareBuffer] (not a software Bitmap
     * in current SDKs), so we wrap it back into a Bitmap and
     * convert to a software bitmap for TFLite inference.
     *
     * The throttle signal is
     * `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT` (the public
     * equivalent of the API 33 `ERROR_TAKE_SCREENSHOT_THROTTLED`
     * on the hidden [AccessibilityNodeInfo.takeScreenshot]
     * path).
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureAndClassifyApi30(node: AccessibilityNodeInfo, pkg: String) {
        try {
            host.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val buffer = result.hardwareBuffer ?: return
                        try {
                            val hardwareBitmap = try {
                                Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            } catch (e: Exception) {
                                Timber.w(e, "$TAG: wrapHardwareBuffer failed for $pkg")
                                null
                            } ?: return
                            // TFLite's getPixels needs a software
                            // bitmap; the hardware one is a thin
                            // reference into the HardwareBuffer.
                            val software = try {
                                hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            } catch (e: Exception) {
                                Timber.w(e, "$TAG: bitmap copy failed for $pkg")
                                null
                            }
                            hardwareBitmap.recycle()
                            if (software == null) return

                            serviceScope.launch {
                                try {
                                    val r = inferenceMutex.withLock { classifier.classify(software) }
                                    if (!software.isRecycled) software.recycle()
                                    DetectionState.updateAiVisionConfidence(r.highestBlockedScore)
                                    if (r.shouldBlock) handleDetection(pkg, r)
                                } catch (e: Exception) {
                                    Timber.e(e, "$TAG: classification failed for $pkg")
                                    if (!software.isRecycled) software.recycle()
                                }
                            }
                        } finally {
                            try { buffer.close() } catch (_: Exception) {}
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                            Timber.w("$TAG: screenshot throttled for $pkg, backing off")
                            screenshotThrottled = true
                            serviceScope.launch {
                                delay(THROTTLE_BACKOFF_MS)
                                screenshotThrottled = false
                            }
                        } else {
                            Timber.w("$TAG: screenshot failed for $pkg (errorCode=$errorCode)")
                        }
                    }
                },
            )
        } catch (e: Exception) {
            Timber.w(e, "$TAG: takeScreenshot threw for $pkg")
        }
    }

    private suspend fun handleDetection(pkg: String, result: NsfwClassifier.Result) {
        val label = try {
            host.packageManager.getApplicationLabel(
                host.packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (_: Exception) { pkg }

        val until = aiBlockTracker.recordAiBlock(pkg)
        repo.recordBlock(pkg, label, "ai_sensitive_block")

        // Forced image removal: if the foreground app is a camera,
        // the user is *creating* the sensitive content. Bouncing
        // them out is the only way to prevent it from being saved
        // to the gallery in the first place. Canopy uses the same
        // pattern.
        val isCamera = BounceHelper.isCameraPackage(pkg)

        // Kick the user out of the app — the overlay alone is dismissable.
        BounceHelper.backToHome(host)

        mainHandler.post {
            val title = when {
                isCamera -> host.getString(R.string.ai_camera_blocked_title)
                until > 0L -> host.getString(R.string.ai_temp_block_title)
                else -> host.getString(R.string.ai_blocked_title)
            }
            val text = when {
                isCamera -> host.getString(R.string.ai_camera_blocked_text)
                until > 0L -> host.getString(R.string.ai_temp_block_text, label)
                else -> host.getString(R.string.ai_blocked_text, label)
            }
            showNotification(title, text)
        }
    }

    private fun showNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(host, AppNotificationChannels.AI_SCANNER)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(host).notify(SENSITIVE_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }
}
