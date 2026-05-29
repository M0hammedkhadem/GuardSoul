package com.agon.app.nn

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class NsfwModelDownloader(private val context: Context) {

    companion object {
        private const val MODEL_DIR = "models"
        private const val MODEL_FILENAME = "nsfw_model.tflite"
        private const val MODEL_URL = "https://github.com/agon/guardian/releases/download/v1.0/nsfw_model_quant.tflite"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 30_000
    }

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    fun getModelFile(): File {
        val dir = File(context.filesDir, MODEL_DIR)
        return File(dir, MODEL_FILENAME)
    }

    fun isModelDownloaded(): Boolean {
        return getModelFile().exists()
    }

    suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        if (isModelDownloaded()) {
            _downloadProgress.value = 100
            return@withContext true
        }

        _isDownloading.value = true
        _downloadProgress.value = 0

        try {
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                doInput = true
                connect()
            }

            val contentLength = connection.contentLength
            val inputStream = connection.inputStream

            val modelDir = File(context.filesDir, MODEL_DIR)
            if (!modelDir.exists()) modelDir.mkdirs()

            val outputFile = getModelFile()
            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        _downloadProgress.value = progress.coerceIn(0, 100)
                    }
                }
            }

            inputStream.close()
            connection.disconnect()

            _downloadProgress.value = 100
            Timber.d("NsfwModelDownloader: model downloaded to ${outputFile.absolutePath}")
            return@withContext true
        } catch (e: Exception) {
            Timber.e(e, "NsfwModelDownloader: download failed")
            _downloadProgress.value = -1
            return@withContext false
        } finally {
            _isDownloading.value = false
        }
    }

    suspend fun deleteModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            getModelFile().delete()
            true
        } catch (e: Exception) {
            Timber.e(e, "NsfwModelDownloader: delete failed")
            false
        }
    }
}
