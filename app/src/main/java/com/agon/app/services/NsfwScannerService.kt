package com.agon.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.agon.app.GuardianApp
import com.agon.app.R
import com.agon.app.data.repository.AppRepository
import com.agon.app.ml.NsfwClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class NsfwScannerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var nsfwClassifier: NsfwClassifier? = null
    private var isScanning = false
    private val scanIntervalMs = 2500L // As per report: scan every 2-3 seconds
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationId = 2001
    private val channelId = "nsfw_scanner_channel"
    private var shieldCheckRunnable: Runnable? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initClassifier()
        Timber.d("NsfwScannerService created")
    }

    private fun initClassifier() {
        nsfwClassifier = NsfwClassifier.newInstance(this)
        if (!nsfwClassifier!!.isModelLoaded()) {
            Timber.w("NSFW model not loaded - scanner will not function")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("NsfwScannerService onStartCommand")
        
        // Check if MediaProjection was stored in companion object
        val mediaProjection = consumePendingProjection()
        if (mediaProjection != null) {
            startScanning(mediaProjection)
        }
        
        return START_STICKY
    }

    fun startScanning(mediaProjection: MediaProjection) {
        serviceScope.launch {
            val app = applicationContext as GuardianApp
            val settings = app.repository.getAppSettings()
            
            // Both shield and AI Explorer must be active
            val shieldActive = settings.isShieldActive()
            val aiExplorerEnabled = settings.aiExplorerEnabledFlow.first()
            
            if (!shieldActive || !aiExplorerEnabled) {
                Timber.d("Shield inactive or AI Explorer disabled, not starting scanner")
                stopSelf()
                return@launch
            }

            if (nsfwClassifier?.isModelLoaded() != true) {
                Timber.w("NSFW model not loaded — running in Mock mode (no actual scanning). Replace app/src/main/assets/nsfw_model.tflite with a real MobileNetV2 model.")
                // Continue in mock mode — service stays alive but reports no violations
            }

            this@NsfwScannerService.mediaProjection = mediaProjection
            setupDisplayMetrics()
            setupImageReader()
            createVirtualDisplay()
            startPeriodicScan()
            startShieldCheck()
            isScanning = true
            startForeground(notificationId, buildNotification())
            Timber.i("NsfwScannerService started scanning")
        }
    }

    private fun setupDisplayMetrics() {
        val displayMetrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        screenDensity = displayMetrics.densityDpi
        Timber.d("Display metrics: ${screenWidth}x${screenHeight} @ ${screenDensity}dpi")
    }

    private fun setupImageReader() {
        // Use RGBA_8888 for better compatibility
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
    }

    private fun createVirtualDisplay() {
        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "NsfwScanner",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            mainHandler
        )
    }

    private fun startPeriodicScan() {
        mainHandler.post(scanRunnable)
    }

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (!isScanning) return
            
            serviceScope.launch {
                performScan()
            }
            
            mainHandler.postDelayed(this, scanIntervalMs)
        }
    }

    private fun performScan() {
        val bitmap = captureScreen()
        if (bitmap == null) return

        try {
            val result = nsfwClassifier!!.classify(bitmap)
            bitmap.recycle() // CRITICAL: always recycle bitmap
            
            if (result.shouldBlock()) {
                Timber.w("NSFW detected: Porn=${result.porn}, Hentai=${result.hentai} - blocking!")
                performBlockingAction()
            } else {
                Timber.d("Scan clean: max=${result.getMaxClass()} (${result.getMaxProbability()})")
            }
        } catch (e: Exception) {
            Timber.e(e, "Scan failed")
            bitmap.recycle()
        }
    }

    private fun captureScreen(): Bitmap? {
        val image = imageReader?.acquireLatestImage() ?: return null
        
        try {
            val buffer = image.planes[0].buffer
            val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            return bitmap
        } catch (e: Exception) {
            Timber.e(e, "Failed to capture screen")
            image.close()
            return null
        }
    }

    private fun performBlockingAction() {
        // 1. Perform GLOBAL_ACTION_BACK to exit the current content
        val accessibilityService = GuardSoulAccessibilityService.current
        accessibilityService?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        
        // 2. Log incident to Room for activity reports
        serviceScope.launch {
            try {
                val app = applicationContext as GuardianApp
                app.repository.recordBlock(
                    "nsfw_scanner",
                    "AI Scanner",
                    "nsfw_detected"
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to log NSFW block event")
            }
        }
        
        // 3. Optionally show overlay warning (could be implemented later)
        Timber.w("Blocking action performed for NSFW content")
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, com.agon.app.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_blocker_title))
            .setContentText("AI Scanner active - monitoring screen")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.channel_scanner),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_scanner_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun stopScanning() {
        if (!isScanning) return
        
        isScanning = false
        stopShieldCheck()
        mainHandler.removeCallbacks(scanRunnable)
        
        virtualDisplay?.release()
        virtualDisplay = null
        
        imageReader?.close()
        imageReader = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        stopForeground(true)
        Timber.d("NsfwScannerService stopped scanning")
    }

    private fun startShieldCheck() {
        shieldCheckRunnable = object : Runnable {
            override fun run() {
                val thisRunnable = this
                serviceScope.launch {
                    val app = applicationContext as GuardianApp
                    val settings = app.repository.getAppSettings()
                    val shieldActive = settings.isShieldActive()
                    val aiExplorerEnabled = settings.aiExplorerEnabledFlow.first()
                    
                    if (!shieldActive || !aiExplorerEnabled) {
                        Timber.d("Shield or AI Explorer disabled, stopping scanner")
                        stopScanning()
                        stopSelf()
                        return@launch
                    }
                    
                    // Reschedule check every 5 seconds
                    mainHandler.postDelayed(thisRunnable, 5000)
                }
            }
        }
        mainHandler.post(shieldCheckRunnable!!)
    }

    private fun stopShieldCheck() {
        shieldCheckRunnable?.let { mainHandler.removeCallbacks(it) }
        shieldCheckRunnable = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        nsfwClassifier?.close()
        serviceScope.cancel()
        Timber.d("NsfwScannerService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile private var pendingMediaProjection: MediaProjection? = null

        fun startWithProjection(context: Context, mediaProjection: MediaProjection) {
            pendingMediaProjection = mediaProjection
            val intent = Intent(context, NsfwScannerService::class.java)
            context.startForegroundService(intent)
        }

        internal fun consumePendingProjection(): MediaProjection? {
            return pendingMediaProjection?.also { pendingMediaProjection = null }
        }
    }
}