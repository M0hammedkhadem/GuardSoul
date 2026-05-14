package com.agon.app.services

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.agon.app.data.GuardianRepository
import com.agon.app.data.GuardianState
import com.agon.app.ui.screens.BlockActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class AIExplorerService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var repository: GuardianRepository
    private var currentState: GuardianState = GuardianState()
    private var interpreter: Interpreter? = null
    private val inputSize = 224
    private val violationMap = mutableMapOf<String, MutableList<Long>>()
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    companion object {
        val bannedAppsStatic = mutableMapOf<String, Long>()

        fun isAppBanned(packageName: String): Boolean {
            val banUntil = bannedAppsStatic[packageName] ?: return false
            if (System.currentTimeMillis() > banUntil) {
                bannedAppsStatic.remove(packageName)
                return false
            }
            return true
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = GuardianRepository(applicationContext)
        loadTfliteModel()
        startForegroundNotification()
        repository.guardianStateFlow.onEach { currentState = it }.launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)
        setupImageReader()
        startScanning()
        return START_STICKY
    }

    private fun setupImageReader() {
        val metrics = resources.displayMetrics
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AIExplorer",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun startScanning() {
        scope.launch {
            while (isActive && currentState.aiExplorerActive) {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (!pm.isInteractive) {
                    delay(3000)
                    continue
                }

                val bitmap = captureScreen()
                if (bitmap != null) {
                    val result = runInference(bitmap)
                    bitmap.recycle()
                    if (result.isUnsafe) {
                        handleUnsafeContent(result.confidence)
                    }
                }
                delay(2000)
            }
            stopSelf()
        }
    }

    private fun captureScreen(): Bitmap? {
        return try {
            val reader = imageReader ?: return null
            val image = reader.acquireLatestImage() ?: return null
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        } catch (e: Exception) {
            Log.e("AIExplorer", "captureScreen failed", e)
            null
        }
    }

    data class InferenceResult(val isUnsafe: Boolean, val confidence: Float, val label: String)

    private fun runInference(bitmap: Bitmap): InferenceResult {
        val interp = interpreter ?: return InferenceResult(false, 0f, "neutral")

        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            inputBuffer.putFloat((pixel and 0xFF) / 255.0f)
        }
        resized.recycle()

        val outputArray = Array(1) { FloatArray(5) }
        interp.run(inputBuffer, outputArray)

        val scores = outputArray[0]
        val labels = listOf("drawings", "hentai", "neutral", "porn", "sexy")
        val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: 2
        val maxScore = scores[maxIdx]

        val isUnsafe = (maxIdx == 1 && maxScore > 0.6f) ||
                (maxIdx == 3 && maxScore > 0.6f) ||
                (maxIdx == 4 && maxScore > 0.6f)

        return InferenceResult(isUnsafe, maxScore, labels[maxIdx])
    }

    private fun handleUnsafeContent(confidence: Float) {
        val currentApp = getCurrentForegroundApp() ?: return
        if (currentApp.startsWith("android") || currentApp == packageName) return

        val now = System.currentTimeMillis()

        val intent = Intent(this, BlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("APP_NAME", getAppLabel(currentApp))
            putExtra("BLOCK_REASON", "ai_scan")
        }
        startActivity(intent)

        val timestamps = violationMap.getOrPut(currentApp) { mutableListOf() }
        timestamps.add(now)
        val twoMinutesAgo = now - 120_000
        timestamps.removeAll { it < twoMinutesAgo }

        if (timestamps.size >= 3) {
            val banUntil = now + 15 * 60 * 1000
            bannedAppsStatic[currentApp] = banUntil
            timestamps.clear()
            showBanNotification(currentApp, 15)
        }

        scope.launch { repository.updateBlocksCount(currentState.blocksCount + 1) }
    }

    private fun getCurrentForegroundApp(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5000, now)
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            null
        }
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun loadTfliteModel() {
        try {
            val assetFileDescriptor = assets.openFd("nsfw_model.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            interpreter = Interpreter(mappedByteBuffer, Interpreter.Options().apply { setNumThreads(2) })
            fileChannel.close()
            inputStream.close()
            assetFileDescriptor.close()
            Log.d("AIExplorer", "TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.e("AIExplorer", "Failed to load TFLite model", e)
        }
    }

    private fun showBanNotification(packageName: String, minutes: Int) {
        val channelId = "ban_notifications"
        val channel = NotificationChannel(
            channelId, "App Bans",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for banned apps"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("App Banned")
            .setContentText("${getAppLabel(packageName)} has been banned for $minutes minutes due to repeated violations")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(3, notification)
    }

    private fun startForegroundNotification() {
        val channelId = "ai_scanner"
        val channel = NotificationChannel(
            channelId, "AI Explorer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "AI Explorer is scanning for unsafe content"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Guardian AI Explorer")
            .setContentText("AI Explorer is scanning for unsafe content")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(2, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        interpreter?.close()
        job.cancel()
    }
}
