package com.agon.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.BlurMaskFilter
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.agon.app.data.repository.AppRepository
import com.agon.app.nn.NsfwDetector
import com.agon.app.ui.screens.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiScannerService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 5001
        const val EXTRA_PROJECTION_INTENT = "EXTRA_PROJECTION_INTENT"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        private const val CAPTURE_WIDTH = 360
        private const val CAPTURE_HEIGHT = 640
        private const val INFERENCE_INTERVAL_MS = 2_000L
        private const val BAN_DURATION_MS = 15 * 60 * 1000L
        private const val OVERLAY_DISMISS_MS = 3_000L
        private const val OVERLAY_DISMISS_STRIKE2_MS = 5_000L
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var captureJob: Job? = null

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var nsfwDetector: NsfwDetector? = null

    private var currentDayStrikes = 0
    private var lastStrikeDate = ""
    private var bannedUntilTimestamp = 0L

    private var overlayView: View? = null

    private val repo: AppRepository by lazy {
        (applicationContext as GuardianApp).repository
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        nsfwDetector = NsfwDetector(this).also { it.initialize() }
        Timber.d("AiScannerService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }

        val shieldActive = try {
            kotlinx.coroutines.runBlocking { repo.getAppSettings().isShieldActive() }
        } catch (_: Exception) { false }
        if (!shieldActive) {
            Timber.w("AiScannerService: shield is off, not starting")
            stopSelf()
            return START_NOT_STICKY
        }

        ForegroundServiceHelper.startForegroundCompat(
            this, NOTIFICATION_ID, createNotification()
        )

        val projectionIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_PROJECTION_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_PROJECTION_INTENT)
        }

        if (projectionIntent != null) {
            startCapture(projectionIntent)
        } else {
            Timber.w("AiScannerService: Started without projection token extra, stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissOverlay()
        stopCapture()
        serviceScope.cancel()
        nsfwDetector?.close()
        Timber.d("AiScannerService destroyed")
    }

    private fun startCapture(projectionIntent: Intent) {
        stopCapture()
        try {
            mediaProjection = mediaProjectionManager?.getMediaProjection(Activity.RESULT_OK, projectionIntent)
            if (mediaProjection == null) {
                Timber.e("AiScannerService: failed to obtain media projection token")
                return
            }

            imageReader = ImageReader.newInstance(CAPTURE_WIDTH, CAPTURE_HEIGHT, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                CAPTURE_WIDTH, CAPTURE_HEIGHT, 1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            Timber.d("AiScannerService: media projection screen capture session active")
            startCaptureLoop()
        } catch (e: Exception) {
            Timber.e(e, "AiScannerService: failed to start media projection capture")
        }
    }

    private fun startCaptureLoop() {
        captureJob?.cancel()
        captureJob = serviceScope.launch {
            while (isActive) {
                delay(INFERENCE_INTERVAL_MS)
                try {
                    val reader = imageReader ?: continue
                    val image = reader.acquireLatestImage() ?: continue

                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * CAPTURE_WIDTH

                    val bitmap = Bitmap.createBitmap(
                        CAPTURE_WIDTH + rowPadding / pixelStride,
                        CAPTURE_HEIGHT,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, CAPTURE_WIDTH, CAPTURE_HEIGHT)
                    bitmap.recycle()

                    val nsfwScore = runInference(croppedBitmap)

                    val sensitivity = try {
                        repo.getAppSettings().aiSensitivityFlow.first() / 100f
                    } catch (_: Exception) { 0.75f }

                    Timber.d("AiScannerService: Score=$nsfwScore, threshold=$sensitivity")

                    if (nsfwScore > sensitivity) {
                        handleNsfwViolation(croppedBitmap, nsfwScore)
                    } else {
                        croppedBitmap.recycle()
                    }
                } catch (e: Exception) {
                    Timber.w(e, "AiScannerService: frame capture error")
                }
            }
        }
    }

    private fun runInference(bitmap: Bitmap): Float {
        val detector = nsfwDetector
        if (detector == null) {
            bitmap.recycle()
            return 0.0f
        }
        return detector.detect(bitmap)
    }

    private fun handleNsfwViolation(frame: Bitmap, score: Float) {
        val now = System.currentTimeMillis()

        if (now < bannedUntilTimestamp) {
            frame.recycle()
            Timber.d("AiScannerService: User is banned until ${bannedUntilTimestamp - now}ms from now")
            return
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        if (today != lastStrikeDate) {
            currentDayStrikes = 0
            lastStrikeDate = today
        }

        currentDayStrikes++
        Timber.w("AiScannerService: Strike $currentDayStrikes/3 for $today (score=$score)")

        serviceScope.launch {
            try {
                repo.recordBlock("NSFW_Screen", "AI Screen Filter", "ai_nsfw_block")
            } catch (_: Exception) {}
        }

        val useBlur = try {
            kotlinx.coroutines.runBlocking {
                repo.getAppSettings().aiOverlayModeFlow.first()
            }
        } catch (_: Exception) { false }

        if (useBlur) {
            val delay = if (currentDayStrikes >= 2) OVERLAY_DISMISS_STRIKE2_MS else OVERLAY_DISMISS_MS
            showBlurOverlay(frame, score, delay)
            showWarningToast("Content blurred (${(score * 100).toInt()}% confidence)")
            return
        }

        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        when (currentDayStrikes) {
            1 -> {
                showPixelationOverlay(frame)
                showWarningToast("Warning: Explicit content detected (Strike 1/3)")
            }
            2 -> {
                showPixelationOverlay(frame, strike2 = true)
                showWarningToast("Final warning: Explicit content detected (Strike 2/3)")
                showStrikeNotification()
            }
            3 -> {
                frame.recycle()
                bannedUntilTimestamp = now + BAN_DURATION_MS
                currentDayStrikes = 0
                mainHandler.post {
                    val intent = Intent(this, BlockActivity::class.java).apply {
                        putExtra("APP_NAME", "AI Screen Monitor")
                        putExtra("BLOCK_REASON", "ai_repeat_offender")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                }
            }
            else -> frame.recycle()
        }
    }

    private fun showBlurOverlay(bitmap: Bitmap, score: Float, dismissMs: Long) {
        dismissOverlay()

        val blurRadius = ((score - 0.5f) * 80f).coerceIn(10f, 50f).toInt()
        val blurred = applyGaussianBlur(bitmap, blurRadius)
        bitmap.recycle()

        mainHandler.post {
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )

                val imageView = ImageView(this).apply {
                    setImageBitmap(blurred)
                    scaleType = ImageView.ScaleType.FIT_XY
                }

                wm.addView(imageView, layoutParams)
                overlayView = imageView

                mainHandler.postDelayed({
                    dismissOverlay()
                }, dismissMs)
            } catch (e: Exception) {
                Timber.w(e, "AiScannerService: failed to show blur overlay")
            }
        }
    }

    private fun applyGaussianBlur(source: Bitmap, radius: Int): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint().apply {
            isFilterBitmap = true
            maskFilter = BlurMaskFilter(radius.toFloat(), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun showPixelationOverlay(bitmap: Bitmap, strike2: Boolean = false) {
        dismissOverlay()

        val pixelated = createPixelatedBitmap(bitmap)
        bitmap.recycle()

        mainHandler.post {
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )

                val imageView = ImageView(this).apply {
                    setImageBitmap(pixelated)
                    scaleType = ImageView.ScaleType.FIT_XY
                }

                wm.addView(imageView, layoutParams)
                overlayView = imageView

                val delay = if (strike2) OVERLAY_DISMISS_STRIKE2_MS else OVERLAY_DISMISS_MS
                mainHandler.postDelayed({
                    dismissOverlay()
                }, delay)
            } catch (e: Exception) {
                Timber.w(e, "AiScannerService: failed to show pixelation overlay")
            }
        }
    }

    private fun createPixelatedBitmap(source: Bitmap): Bitmap {
        val blockSize = 24
        val smallW = source.width / blockSize
        val smallH = source.height / blockSize
        val small = Bitmap.createScaledBitmap(source, smallW.coerceAtLeast(1), smallH.coerceAtLeast(1), true)
        return Bitmap.createScaledBitmap(small, source.width, source.height, false)
    }

    private fun dismissOverlay() {
        try {
            val overlay = overlayView
            if (overlay != null) {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(overlay)
            }
        } catch (_: Exception) {}
        overlayView = null
    }

    private fun showWarningToast(message: String) {
        try {
            val notification = NotificationCompat.Builder(this, AppNotificationChannels.APP_BLOCKER)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(message)
                .setAutoCancel(true)
                .setTimeoutAfter(4000L)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(5002, notification)
        } catch (e: Exception) {
            Timber.w(e, "AiScannerService: failed to show message notification")
        }
    }

    private fun showStrikeNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(this, AppNotificationChannels.AI_SCANNER)
                .setContentTitle("AI Scanner — Final Warning")
                .setContentText("Explicit content detected. Next strike will block all apps for 15 minutes.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(5002, notification)
        } catch (e: Exception) {
            Timber.w(e, "AiScannerService: failed to show strike notification")
        }
    }

    private fun stopCapture() {
        captureJob?.cancel()
        captureJob = null

        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null

        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null

        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null

        Timber.d("AiScannerService: Media projection screen capture session terminated")
    }

    private fun createNotification(): Notification {
        return ForegroundServiceHelper.buildSilentNotification(
            context = this,
            title = "Guardian AI Screen Monitor",
            text = "Actively scanning screen for explicit content"
        )
    }
}
