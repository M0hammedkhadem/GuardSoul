package com.agon.app

import android.app.Activity
import android.app.Notification
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

class AiScannerService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 5001
        const val EXTRA_PROJECTION_INTENT = "EXTRA_PROJECTION_INTENT"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        
        private const val CAPTURE_WIDTH = 360
        private const val CAPTURE_HEIGHT = 640
        private const val INFERENCE_INTERVAL_MS = 2_500L
        private const val VIOLATIONS_FOR_BAN = 3
        private const val VIOLATION_WINDOW_MS = 4 * 60 * 1000L
        private const val BAN_DURATION_MS = 15 * 60 * 1000L
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var captureJob: Job? = null
    
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var tflite: org.tensorflow.lite.Interpreter? = null

    private val repo: AppRepository by lazy {
        (applicationContext as GuardianApp).repository
    }

    private val violationTimestamps = mutableListOf<Long>()
    private var bannedUntilTimestamp = 0L
    private var lastViolationPkg = ""

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
                Timber.w("AiScannerService: nsfw.tflite not found in assets, running in simulation fallback mode")
            }
        } catch (e: Exception) {
            Timber.e(e, "AiScannerService: failed to initialize TFLite, running in fallback mode")
        }
    }

    private fun loadModelFile(): java.nio.MappedByteBuffer? {
        return try {
            val fileDescriptor = assets.openFd("nsfw.tflite")
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
                    croppedBitmap.recycle()
                    
                    val sensitivity = try {
                        repo.getAppSettings().aiSensitivityFlow.first() / 100f
                    } catch (_: Exception) { 0.75f }
                    
                    Timber.d("AiScannerService: Score=$nsfwScore, threshold=$sensitivity")
                    
                    if (nsfwScore > sensitivity) {
                        handleNsfwViolation()
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
            // Safe simulation mode: default clean score
            return 0.01f
        }

        // MobileNet input: 224 x 224 x 3 Float values
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
            
            // Standard float normalization [0, 1]
            byteBuffer.putFloat(r / 255.0f)
            byteBuffer.putFloat(g / 255.0f)
            byteBuffer.putFloat(b / 255.0f)
        }
        
        // Output array (2 classes: Safe, NSFW)
        val outputVal = Array(1) { FloatArray(2) }
        
        try {
            interpreter.run(byteBuffer, outputVal)
            return outputVal[0][1] // Probability of explicit class
        } catch (e: Exception) {
            Timber.e(e, "AiScannerService: TFLite run failed")
            return 0.0f
        }
    }

    private fun handleNsfwViolation() {
        val now = System.currentTimeMillis()

        if (now < bannedUntilTimestamp) {
            Timber.d("AiScannerService: User is banned until ${bannedUntilTimestamp - now}ms from now, skipping")
            return
        }

        violationTimestamps.removeAll { now - it > VIOLATION_WINDOW_MS }
        violationTimestamps.add(now)

        serviceScope.launch {
            try {
                repo.recordBlock("NSFW_Screen", "AI Screen Filter", "ai_nsfw_block")
            } catch (_: Exception) {}
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)

        if (violationTimestamps.size >= VIOLATIONS_FOR_BAN) {
            Timber.w("AiScannerService: ${VIOLATIONS_FOR_BAN} violations in window, banning for ${BAN_DURATION_MS / 1000}s")
            bannedUntilTimestamp = now + BAN_DURATION_MS
            violationTimestamps.clear()
            mainHandler.post {
                val intent = Intent(this, BlockActivity::class.java).apply {
                    putExtra("APP_NAME", "AI Screen Monitor")
                    putExtra("BLOCK_REASON", "ai_repeat_offender")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
        } else {
            mainHandler.post {
                val intent = Intent(this, BlockActivity::class.java).apply {
                    putExtra("APP_NAME", "AI Screen Monitor")
                    putExtra("BLOCK_REASON", "ai_nsfw_block")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
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
