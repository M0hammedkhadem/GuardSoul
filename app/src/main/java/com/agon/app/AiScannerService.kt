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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.agon.app.data.repository.AppRepository
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
        private const val PIXEL_BLOCK_SIZE = 24
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var captureJob: Job? = null

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var tflite: org.tensorflow.lite.Interpreter? = null

    private var currentDayStrikes = 0
    private var lastStrikeDate = ""
    private var bannedUntilTimestamp = 0L

    private var pixelationOverlay: View? = null

    private val repo: AppRepository by lazy {
        (applicationContext as GuardianApp).repository
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        initTfLite()
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

        startForeground(NOTIFICATION_ID, createNotification())

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
        dismissPixelationOverlay()
        stopCapture()
        serviceScope.cancel()
        tflite?.close()
        Timber.d("AiScannerService destroyed")
    }

    private fun initTfLite() {
        try {
            val modelFile = loadModelFile()
            if (modelFile != null) {
                val options = org.tensorflow.lite.Interpreter.Options().apply {
                    try {
                        addDelegate(org.tensorflow.lite.nnapi.NnApiDelegate())
                        Timber.d("AiScannerService: NNAPI Delegate added successfully")
                    } catch (e: Exception) {
                        Timber.w("AiScannerService: NNAPI Delegate failed, falling back to CPU")
                    }
                    setNumThreads(4)
                }
                tflite = org.tensorflow.lite.Interpreter(modelFile, options)
                Timber.d("AiScannerService: TFLite model loaded successfully")
            } else {
                Timber.w("AiScannerService: nsfw_model.tflite not found in assets, running in simulation fallback mode")
            }
        } catch (e: Exception) {
            Timber.e(e, "AiScannerService: failed to initialize TFLite, running in fallback mode")
        }
    }

    private fun loadModelFile(): java.nio.MappedByteBuffer? {
        return try {
            val fileDescriptor = assets.openFd("nsfw_model.tflite")
            val inputStream = java.io.FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            null
        }
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
                        handleNsfwViolation(croppedBitmap)
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
        val interpreter = tflite
        if (interpreter == null) {
            return 0.01f
        }

        val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val byteBuffer = ByteBuffer.allocateDirect(224 * 224 * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        val intValues = IntArray(224 * 224)
        resized.getPixels(intValues, 0, resized.width, 0, 0, resized.width, resized.height)
        resized.recycle()

        byteBuffer.rewind()
        for (pixel in intValues) {
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff

            byteBuffer.putFloat(r / 255.0f)
            byteBuffer.putFloat(g / 255.0f)
            byteBuffer.putFloat(b / 255.0f)
        }

        val outputVal = Array(1) { FloatArray(2) }

        try {
            interpreter.run(byteBuffer, outputVal)
            return outputVal[0][1]
        } catch (e: Exception) {
            Timber.e(e, "AiScannerService: TFLite run failed")
            return 0.0f
        }
    }

    private fun handleNsfwViolation(frame: Bitmap) {
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
        Timber.w("AiScannerService: Strike $currentDayStrikes/3 for $today")

        serviceScope.launch {
            try {
                repo.recordBlock("NSFW_Screen", "AI Screen Filter", "ai_nsfw_block")
            } catch (_: Exception) {}
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

    private fun showPixelationOverlay(bitmap: Bitmap, strike2: Boolean = false) {
        dismissPixelationOverlay()

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
                pixelationOverlay = imageView

                val delay = if (strike2) OVERLAY_DISMISS_STRIKE2_MS else OVERLAY_DISMISS_MS
                mainHandler.postDelayed({
                    dismissPixelationOverlay()
                }, delay)
            } catch (e: Exception) {
                Timber.w(e, "AiScannerService: failed to show pixelation overlay")
            }
        }
    }

    private fun createPixelatedBitmap(source: Bitmap): Bitmap {
        val smallW = source.width / PIXEL_BLOCK_SIZE
        val smallH = source.height / PIXEL_BLOCK_SIZE
        val small = Bitmap.createScaledBitmap(source, smallW.coerceAtLeast(1), smallH.coerceAtLeast(1), true)
        return Bitmap.createScaledBitmap(small, source.width, source.height, false)
    }

    private fun dismissPixelationOverlay() {
        try {
            val overlay = pixelationOverlay
            if (overlay != null) {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(overlay)
            }
        } catch (_: Exception) {}
        pixelationOverlay = null
    }

    private fun showWarningToast(message: String) {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AppNotificationChannels.APP_BLOCKER)
            .setContentTitle("Guardian AI Screen Monitor")
            .setContentText("Actively scanning screen for explicit content")
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
