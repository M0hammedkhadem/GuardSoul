package com.agon.app.data.patterns

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * PatternUpdateManager - إدارة تحديث توقيعات التطبيقات عبر الإنترنت (OTA)
 *
 * 1. يحمل التوقيعات الافتراضية من assets/patterns.json
 * 2. يتحقق من وجود تحديثات على Firebase / Remote URL
 * 3. يحفظ التوقيعات المحدثة في files/patterns.json
 * 4. PatternMatcher يحمل التوقيعات من الملف المحدث أولاً
 */
class PatternUpdateManager(private val context: Context) {

    companion object {
        private const val ASSET_PATH = "patterns.json"
        private const val LOCAL_FILE = "patterns.json"
        private const val REMOTE_URL = "https://guardsoul-cdn.example.com/patterns.json"
        private const val UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L // 24 hours
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val localFile: File
        get() = File(context.filesDir, LOCAL_FILE)

    /**
     * تحميل التوقيعات الحالية (من الملف المحلي أو assets).
     */
    fun loadPatterns(): PatternDatabase? {
        return try {
            // Try local file first (OTA updated)
            if (localFile.exists()) {
                val content = localFile.readText()
                json.decodeFromString<PatternDatabase>(content).also {
                    Timber.d("PatternUpdateManager: loaded ${it.signatures.size} signatures from local file")
                }
            } else {
                // Fallback to assets
                val assetContent = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
                json.decodeFromString<PatternDatabase>(assetContent).also {
                    Timber.d("PatternUpdateManager: loaded ${it.signatures.size} signatures from assets")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "PatternUpdateManager: failed to load patterns")
            null
        }
    }

    /**
     * التحقق من وجود تحديثات وتحميلها.
     */
    suspend fun checkForUpdates(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val lastCheck = getLastCheckTime()
                val now = System.currentTimeMillis()
                if (now - lastCheck < UPDATE_INTERVAL_MS) {
                    Timber.d("PatternUpdateManager: update check skipped (too recent)")
                    return@withContext false
                }

                val remotePatterns = fetchRemotePatterns() ?: return@withContext false
                val localPatterns = loadPatterns()

                if (localPatterns == null || remotePatterns.version > localPatterns.version) {
                    savePatterns(remotePatterns)
                    setLastCheckTime(now)
                    Timber.i("PatternUpdateManager: updated to version ${remotePatterns.version}")
                    true
                } else {
                    setLastCheckTime(now)
                    Timber.d("PatternUpdateManager: no update needed (remote=${remotePatterns.version}, local=${localPatterns.version})")
                    false
                }
            } catch (e: Exception) {
                Timber.w(e, "PatternUpdateManager: update check failed")
                false
            }
        }
    }

    /**
     * تحميل التوقيعات من URL بعيد (Firebase / CDN).
     */
    private fun fetchRemotePatterns(): PatternDatabase? {
        return try {
            val url = URL(REMOTE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<PatternDatabase>(content)
            } else {
                Timber.w("PatternUpdateManager: remote returned $responseCode")
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "PatternUpdateManager: remote fetch failed")
            null
        }
    }

    /**
     * حفظ التوقيعات في الملف المحلي.
     */
    private fun savePatterns(database: PatternDatabase) {
        try {
            val content = json.encodeToString(PatternDatabase.serializer(), database)
            localFile.writeText(content)
            Timber.d("PatternUpdateManager: saved ${database.signatures.size} signatures to local file")
        } catch (e: Exception) {
            Timber.w(e, "PatternUpdateManager: failed to save patterns")
        }
    }

    private fun getLastCheckTime(): Long {
        return try {
            val prefs = context.getSharedPreferences("pattern_updates", Context.MODE_PRIVATE)
            prefs.getLong("last_check", 0L)
        } catch (e: Exception) {
            0L
        }
    }

    private fun setLastCheckTime(time: Long) {
        try {
            val prefs = context.getSharedPreferences("pattern_updates", Context.MODE_PRIVATE)
            prefs.edit().putLong("last_check", time).apply()
        } catch (e: Exception) {
            Timber.w(e, "PatternUpdateManager: failed to save last check time")
        }
    }
}

// ─── Data Models ─────────────────────────────────────────────────────────

@Serializable
data class PatternDatabase(
    val version: Int,
    val lastUpdated: String,
    val signatures: List<PatternSignature>
)

@Serializable
data class PatternSignature(
    val id: String,
    val packageName: String,
    val label: String,
    val feedViewIds: List<String>,
    val contentDescriptions: List<String>,
    val classNames: List<String>
)
