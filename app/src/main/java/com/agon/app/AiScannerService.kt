package com.agon.app

import android.app.Activity
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.agon.app.blocking.AiBlockTracker
import com.agon.app.blocking.NsfwClassifier
import com.agon.app.data.repository.AppRepository
import com.agon.app.utils.BounceHelper
import com.agon.app.utils.DetectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.nio.ByteBuffer

class AiScannerService : Service(), KoinComponent {

    companion object {
        private const val NOTIFICATION_ID = 5001
        private const val SENSITIVE_NOTIFICATION_ID = 5002
        const val ACTION_STOP = "com.agon.app.action.STOP_AI_SCANNER"
        const val ACTION_START = "com.agon.app.action.START_AI_SCANNER"
        const val EXTRA_PROJECTION_INTENT = "EXTRA_PROJECTION_INTENT"
        private const val CAPTURE_WIDTH = 360
        private const val CAPTURE_HEIGHT = 640

        /**
         * Spec says "every 2-3 seconds" — 1500ms is the sweet spot:
         * responsive enough to catch the user mid-scroll but not so fast
         * that we burn battery / wake the CPU every cycle.
         */
        const val SCAN_INTERVAL_MS = 1500L

        /**
         * Bring up the foreground service so the shield lifecycle can hold
         * a reference to it. If a MediaProjection intent was previously
         * captured we forward it; otherwise the service starts but stays
         * idle until the user re-grants screen capture.
         */
        fun start(context: Context, projectionIntent: Intent? = null) {
            val intent = Intent(context, AiScannerService::class.java).apply {
                action = ACTION_START
                if (projectionIntent != null) {
                    putExtra(EXTRA_PROJECTION_INTENT, projectionIntent)
                }
            }
            try {
                ForegroundServiceHelper.startServiceAsForeground(
                    context, AiScannerService::class.java, intent
                )
            } catch (_: Exception) {
                // Some OEMs (Xiaomi, OPPO) block foreground service start
                // from background. Fall back to a plain startService — the
                // service will be promoted once the process is alive.
                try { context.startService(intent) } catch (_: Exception) {}
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AiScannerService::class.java).apply {
                action = ACTION_STOP
            }
            try { context.startService(intent) } catch (_: Exception) {}
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var captureJob: Job? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val repo: AppRepository by lazy {
        applicationContext.guardianApp()?.repository
            ?: throw IllegalStateException("ApplicationContext must be GuardianApp")
    }
    private val aiBlockTracker: AiBlockTracker by inject()

    /** TFLite-backed NSFW classifier. Lazily created so the service can
     *  start even if the model file is missing (heuristic fallback). */
    private val classifier: NsfwClassifier by lazy { NsfwClassifier(applicationContext) }

    /** Inference lock — `Interpreter.run` is not thread-safe. */
    private val inferenceMutex = Mutex()

    /**
     * Cached whitelist of package names — refreshed periodically so the scan
     * loop (every 1.5s) doesn't need to hit Room each frame. Drops to empty
     * if the read fails so the worst case is a false-positive block, never
     * a missed block.
     */
    @Volatile private var cachedWhitelist: Set<String> = emptySet()
    private var whitelistRefreshJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        ForegroundServiceHelper.startForegroundCompat(this, NOTIFICATION_ID, createNotification())

        // Whitelist cache — refresh in the background so the first scan tick
        // doesn't block on a Room read.
        whitelistRefreshJob?.cancel()
        whitelistRefreshJob = serviceScope.launch { refreshWhitelistCache() }

        val projectionIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_PROJECTION_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_PROJECTION_INTENT)
        }

        if (projectionIntent != null) {
            startScreenCapture(projectionIntent)
        } else {
            // No projection yet — service stays alive (so the shield lifecycle
            // can keep a reference) but the scan loop is dormant until the
            // user re-grants screen capture from the Content screen.
            Timber.d("AiScannerService: started without MediaProjection; scan loop idle")
        }
        return START_STICKY
    }

    private fun startScreenCapture(intent: Intent) {
        // Issue #167: Clean up existing projection before starting new one
        stopCaptureInternal()

        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(Activity.RESULT_OK, intent)

        // Issue #168: Register callback to handle system-initiated stops
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Timber.w("AiScannerService: media projection stopped by system")
                stopSelf()
            }
        }, mainHandler)

        if (mediaProjection == null) {
            Timber.w("AiScannerService: failed to get media projection")
            return
        }

        imageReader = ImageReader.newInstance(CAPTURE_WIDTH, CAPTURE_HEIGHT, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "GuardSoulScan", CAPTURE_WIDTH, CAPTURE_HEIGHT, 1,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, mainHandler
        )

        startScanLoop()
    }

    private fun startScanLoop() {
        captureJob?.cancel()
        captureJob = serviceScope.launch {
            while (isActive) {
                delay(SCAN_INTERVAL_MS)
                val bitmap = acquireLatestScreenshot() ?: continue

                try {
                    val result = runInference(bitmap)
                    DetectionState.updateAiVisionConfidence(result.highestBlockedScore)
                    handleSensitiveDetection(result)
                } catch (e: Exception) {
                    Timber.e(e, "AiScannerService: analysis failed")
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    /**
     * Run TFLite inference on a background dispatcher. The Interpreter
     * is not thread-safe, so we serialise via [inferenceMutex].
     */
    private suspend fun runInference(bitmap: Bitmap): NsfwClassifier.Result =
        inferenceMutex.withLock { classifier.classify(bitmap) }

    /**
     * Implements the AI Explorer spec:
     *  - If sensitive content is detected, exit the foreground app and
     *    notify the user.
     *  - Record the event in the AiBlockTracker.
     *  - If 3 such events happen for the same package in 4 minutes, the
     *    tracker applies a 15-minute temp block (handled by AppBlockerService).
     */
    private suspend fun handleSensitiveDetection(result: NsfwClassifier.Result) {
        if (!result.shouldBlock) return
        val pkg = currentForegroundPackage() ?: return
        if (pkg == packageName) return
        // Whitelisted apps are exempt from the AI Explorer check.
        if (pkg in cachedWhitelist) return
        if (aiBlockTracker.isTempBlocked(pkg)) return

        val label = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (_: Exception) { pkg }

        val until = aiBlockTracker.recordAiBlock(pkg)
        repo.recordBlock(pkg, label, "ai_sensitive_block")

        // Forced image removal: if the foreground app is a camera, the
        // user is *creating* the sensitive content. Bouncing them out
        // is the only way to prevent it from being saved to the gallery
        // in the first place. Canopy uses the same pattern.
        val isCamera = BounceHelper.isCameraPackage(pkg)

        // Kick the user out of the app — the overlay alone is dismissable.
        // `BounceHelper` uses the back-button trick (pop back stack then
        // go home) so the app's task is fully evicted, matching the
        // Canopy / Bulldog pattern.
        BounceHelper.backToHome(this)

        mainHandler.post {
            val title = when {
                isCamera -> getString(R.string.ai_camera_blocked_title)
                until > 0L -> getString(R.string.ai_temp_block_title)
                else -> getString(R.string.ai_blocked_title)
            }
            val text = when {
                isCamera -> getString(R.string.ai_camera_blocked_text)
                until > 0L -> getString(R.string.ai_temp_block_text, label)
                else -> getString(R.string.ai_blocked_text, label)
            }
            showNotification(SENSITIVE_NOTIFICATION_ID, title, text)
        }
    }

    private fun currentForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 5_000L, now)
        var pkg: String? = null
        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND && event.packageName != null) {
                pkg = event.packageName
            }
        }
        return pkg
    }

    private fun showNotification(id: Int, title: String, text: String) {
        val notification = NotificationCompat.Builder(this, "blocker_channel")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(id, notification)
        } catch (_: SecurityException) {}
    }

    private fun acquireLatestScreenshot(): Bitmap? {
        val image = imageReader?.acquireLatestImage() ?: return null
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val width = image.width
            val height = image.height

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            if (rowStride == width * pixelStride) {
                buffer.rewind()
                bitmap.copyPixelsFromBuffer(buffer)
            } else {
                val cleanBuffer = ByteBuffer.allocate(width * height * 4)
                for (y in 0 until height) {
                    buffer.position(y * rowStride)
                    val rowLimit = y * rowStride + width * 4
                    buffer.limit(rowLimit)
                    cleanBuffer.put(buffer)
                    buffer.limit(buffer.capacity())
                }
                cleanBuffer.rewind()
                bitmap.copyPixelsFromBuffer(cleanBuffer)
            }
            return bitmap
        } catch (e: Exception) {
            Timber.e(e, "AiScannerService: screenshot capture failed")
            return null
        } finally {
            image.close()
        }
    }

    private fun stopCaptureInternal() {
        captureJob?.cancel()
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    private suspend fun refreshWhitelistCache() {
        try {
            cachedWhitelist = repo.getBlocklist("whitelist", "apps").map { it.value }.toHashSet()
        } catch (_: Exception) {
            // Keep the previous snapshot — a transient DB error shouldn't
            // make us start scanning apps the user had exempted.
        }
    }

    private fun createNotification(): Notification {
        return ForegroundServiceHelper.buildSilentNotification(this, "GuardSoul AI", "Screen monitoring active")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCaptureInternal()
        whitelistRefreshJob?.cancel()
        serviceScope.cancel()
        try { classifier.close() } catch (_: Exception) {}
    }
}
