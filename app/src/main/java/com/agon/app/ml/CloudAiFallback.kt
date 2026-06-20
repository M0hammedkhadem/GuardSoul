package com.agon.app.ml

import android.content.Context
import android.graphics.Bitmap
import com.agon.app.GuardianApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * CloudAiFallback - تصنيف سحابي احتياطي (Fallback)
 *
 * عندما لا يكون نموذج TensorFlow Lite متاحاً أو يعطي نتائج غير واثقة،
 * يتم إرسال الصورة إلى API سحابي للتحليل.
 *
 * Note: هذا الكود يحتاج إلى خادم Cloud API. يجب استبدال BASE_URL
 * بعنوان خادمك الفعلي.
 */
class CloudAiFallback private constructor(context: Context) {

    companion object {
        @Volatile private var instance: CloudAiFallback? = null

        fun getInstance(context: Context): CloudAiFallback {
            return instance ?: synchronized(this) {
                instance ?: CloudAiFallback(context.applicationContext).also { instance = it }
            }
        }

        private const val BASE_URL = "https://api.guardsoul.example.com"
        private const val TIMEOUT_SECONDS = 15L
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * تحليل صورة عبر API السحابي.
     *
     * @param bitmap الصورة المراد تحليلها
     * @param apiKey مفتاح API (من AppSettings)
     * @return نتيجة التصنيف، أو null إذا فشل الاتصال
     */
    suspend fun classify(bitmap: Bitmap, apiKey: String? = null): CloudClassificationResult? {
        return withContext(Dispatchers.IO) {
            try {
                // Compress bitmap to JPEG (max 512KB)
                val jpegBytes = bitmapToJpeg(bitmap, maxSizeBytes = 512 * 1024)

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "image",
                        "screenshot.jpg",
                        jpegBytes.toRequestBody("image/jpeg".toMediaType())
                    )
                    .addFormDataPart("model", "nsfw-v2")
                    .build()

                val requestBuilder = Request.Builder()
                    .url("$BASE_URL/v1/classify")
                    .post(requestBody)

                if (!apiKey.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $apiKey")
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        json.decodeFromString<CloudClassificationResult>(body)
                    } else null
                } else {
                    Timber.w("CloudAiFallback: HTTP ${response.code}")
                    null
                }
            } catch (e: Exception) {
                Timber.w(e, "CloudAiFallback: classification failed")
                null
            }
        }
    }

    /**
     * تحليل نص (مفيد للـ content descriptions و text nodes).
     */
    suspend fun classifyText(text: String, apiKey: String? = null): CloudTextResult? {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = json.encodeToString(TextRequest(text))
                    .toRequestBody("application/json".toMediaType())

                val requestBuilder = Request.Builder()
                    .url("$BASE_URL/v1/classify-text")
                    .post(requestBody)

                if (!apiKey.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $apiKey")
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        json.decodeFromString<CloudTextResult>(body)
                    } else null
                } else {
                    Timber.w("CloudAiFallback: text classify HTTP ${response.code}")
                    null
                }
            } catch (e: Exception) {
                Timber.w(e, "CloudAiFallback: text classification failed")
                null
            }
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private fun bitmapToJpeg(bitmap: Bitmap, maxSizeBytes: Int): ByteArray {
        var quality = 90
        var bytes: ByteArray
        val stream = java.io.ByteArrayOutputStream()
        do {
            stream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            bytes = stream.toByteArray()
            quality -= 10
        } while (bytes.size > maxSizeBytes && quality > 30)
        return bytes
    }

    // ─── Data Models ────────────────────────────────────────────────────

    @Serializable
    data class CloudClassificationResult(
        val isNsfw: Boolean,
        val confidence: Float,
        val categories: Map<String, Float>
    )

    @Serializable
    data class CloudTextResult(
        val isNsfw: Boolean,
        val confidence: Float,
        val matchedKeywords: List<String>
    )

    @Serializable
    private data class TextRequest(val text: String)
}
