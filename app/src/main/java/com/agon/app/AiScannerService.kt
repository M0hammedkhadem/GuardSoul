package com.agon.app

import android.app.Activity
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
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
import com.agon.app.data.repository.AppRepository
import com.agon.app.utils.DetectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class AiScannerService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 5001
        const val ACTION_STOP = "com.agon.app.action.STOP_AI_SCANNER"
        const val EXTRA_PROJECTION_INTENT = "EXTRA_PROJECTION_INTENT"
        private const val CAPTURE_WIDTH = 360
        private const val CAPTURE_HEIGHT = 640
        private const val SCAN_INTERVAL_MS = 1500L
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var captureJob: Job? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val repo: AppRepository by lazy { (applicationContext as GuardianApp).repository }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        ForegroundServiceHelper.startForegroundCompat(this, NOTIFICATION_ID, createNotification())

        val projectionIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_PROJECTION_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_PROJECTION_INTENT)
        }

        if (projectionIntent != null) {
            startScreenCapture(projectionIntent)
        }
        return START_STICKY
    }

    private fun startScreenCapture(intent: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(Activity.RESULT_OK, intent)
        
        imageReader = ImageReader.newInstance(CAPTURE_WIDTH, CAPTURE_HEIGHT, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "GuardSoulScan", CAPTURE_WIDTH, CAPTURE_HEIGHT, 1,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null
        )

        startScanLoop()
    }

    private fun startScanLoop() {
        captureJob?.cancel()
        captureJob = serviceScope.launch {
            while (isActive) {
                delay(SCAN_INTERVAL_MS)
                val bitmap = acquireLatestScreenshot() ?: continue
                
                // 1. Reels Layout Detection (Heuristic AI)
                // We check for vertical interactive patterns common in Reels/Shorts
                val reelsConfidence = analyzeForShortVideoLayout(bitmap)
                DetectionState.updateAiVisionConfidence(reelsConfidence)
                
                bitmap.recycle()
            }
        }
    }

    private fun analyzeForShortVideoLayout(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return 0f

        var confidence = 0f

        // 1. Aspect Ratio Check (vertical video: height >> width)
        val aspectRatio = height.toFloat() / width.toFloat()
        if (aspectRatio > 1.6f) {
            confidence += 0.3f // Vertical video (9:16 is ~1.78)
        }

        // 2. Right-side button strip detection (Like/Comment/Share are typically on right 15%)
        val rightStripX = (width * 0.85f).toInt()
        var rightBrightPixels = 0
        var rightTotalPixels = 0
        val sampleStep = 4 // Sample every 4th pixel for speed
        for (y in 0 until height step sampleStep) {
            for (x in rightStripX until width step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val brightness = (r + g + b) / 3
                if (brightness > 150) rightBrightPixels++
                rightTotalPixels++
            }
        }
        val rightBrightRatio = if (rightTotalPixels > 0) rightBrightPixels.toFloat() / rightTotalPixels else 0f
        // Reels typically have bright icon buttons on dark background on the right side
        if (rightBrightRatio in 0.15f..0.50f) {
            confidence += 0.3f
        }

        // 3. Bottom progress bar detection (thin horizontal bar at bottom ~5-10% region)
        val bottomStartY = (height * 0.90f).toInt()
        var horizontalLineCount = 0
        var bottomSamplePixels = 0
        for (y in bottomStartY until height step 2) {
            var lineStrength = 0
            for (x in 0 until width step 4) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val brightness = (r + g + b) / 3
                if (brightness < 80) lineStrength++
            }
            val lineRatio = lineStrength.toFloat() / (width / 4)
            if (lineRatio > 0.6f) horizontalLineCount++
            bottomSamplePixels++
        }
        val bottomDarkRatio = if (bottomSamplePixels > 0) horizontalLineCount.toFloat() / bottomSamplePixels else 0f
        if (bottomDarkRatio > 0.4f) {
            confidence += 0.25f
        }

        // 4. Dark background dominance (reels often have dark video bg)
        var darkPixels = 0
        var totalSampled = 0
        val centerL = (width * 0.2f).toInt()
        val centerR = (width * 0.8f).toInt()
        val centerT = (height * 0.2f).toInt()
        val centerB = (height * 0.6f).toInt()
        for (y in centerT until centerB step 6) {
            for (x in centerL until centerR step 6) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val brightness = (r + g + b) / 3
                if (brightness < 60) darkPixels++
                totalSampled++
            }
        }
        val darkRatio = if (totalSampled > 0) darkPixels.toFloat() / totalSampled else 0f
        if (darkRatio > 0.4f) {
            confidence += 0.15f
        }

        return confidence.coerceIn(0f, 1f)
    }

    private fun acquireLatestScreenshot(): Bitmap? {
        val image = imageReader?.acquireLatestImage() ?: return null
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * CAPTURE_WIDTH
        val bitmap = Bitmap.createBitmap(CAPTURE_WIDTH + rowPadding / pixelStride, CAPTURE_HEIGHT, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()
        return Bitmap.createBitmap(bitmap, 0, 0, CAPTURE_WIDTH, CAPTURE_HEIGHT)
    }

    private fun createNotification(): Notification {
        return ForegroundServiceHelper.buildSilentNotification(this, "GuardSoul AI", "Screen monitoring active")
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        mediaProjection?.stop()
        serviceScope.cancel()
    }
}
