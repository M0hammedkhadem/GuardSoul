package com.agon.app.blocking

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

private val Context.tempBanStore by preferencesDataStore(name = "temp_ban")

/**
 * TempBanManager - إدارة الحظر المؤقت (3 ضربات في 4 دقائق = حظر 15 دقيقة)
 *
 * كل تطبيق يحتوي على عداد ضربات. عند تسجيل حظر ( strike ):
 * 1. يتم إضافة الوقت الحالي إلى قائمة الضربات
 * 2. يتم إزالة الضربات الأقدم من 4 دقائق
 * 3. إذا وصلت الضربات إلى 3 → يتم تفعيل الحظر المؤقت لمدة 15 دقيقة
 * 4. خلال فترة الحظر المؤقت → أي محاولة فتح التطبيق يتم حظرها مباشرة
 */
class TempBanManager private constructor(private val context: Context) {

    companion object {
        @Volatile private var instance: TempBanManager? = null

        fun getInstance(context: Context): TempBanManager {
            return instance ?: synchronized(this) {
                instance ?: TempBanManager(context.applicationContext).also { instance = it }
            }
        }

        const val STRIKES_WINDOW_MS = 4L * 60L * 1000L // 4 دقائق
        const val COOLDOWN_MS = 15L * 60L * 1000L      // 15 دقيقة
        const val STRIKES_THRESHOLD = 3

        private fun strikesKey(pkg: String) = stringPreferencesKey("strikes:$pkg")
        private fun cooldownKey(pkg: String) = longPreferencesKey("cooldown:$pkg")
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // In-memory cache for fast reads (AccessibilityService main thread safe)
    private val cooldownCache = mutableMapOf<String, Long>()

    init {
        // FIX: Warm the in-memory cache from DataStore on first creation.
        // This ensures cooldowns survive process restarts.
        scope.launch {
            try {
                val prefs = context.tempBanStore.data.first()
                val now = System.currentTimeMillis()
                prefs.asMap().forEach { (key, value) ->
                    if (key.name.startsWith("cooldown:") && value is Long) {
                        val pkg = key.name.removePrefix("cooldown:")
                        if (value > now) {
                            cooldownCache[pkg] = value
                        }
                    }
                }
                Timber.d("TempBan: warmed cache with ${cooldownCache.size} active cooldowns")
            } catch (e: Exception) {
                Timber.w(e, "TempBan: failed to warm cache")
            }
        }
    }

    /**
     * تسجيل ضربة جديدة للتطبيق.
     * يتم استدعاء هذا عند كل حظر (من ShortstopEngine, FacebookReelsEngine, أو AI Scanner).
     * يعيد true إذا وصل العدد إلى 3 وتم تفعيل الحظر المؤقت.
     */
    fun recordStrike(pkg: String, onTempBanTriggered: ((String) -> Unit)? = null): Boolean {
        val now = System.currentTimeMillis()

        scope.launch {
            try {
                context.tempBanStore.edit { prefs ->
                    val raw = prefs[strikesKey(pkg)] ?: "[]"
                    val strikes = try {
                        Json.decodeFromString<MutableList<Long>>(raw)
                    } catch (_: Exception) {
                        mutableListOf<Long>()
                    }

                    // إضافة الضربة الجديدة
                    strikes.add(now)

                    // إزالة الضربات الأقدم من 4 دقائق
                    val cutoff = now - STRIKES_WINDOW_MS
                    strikes.removeAll { it < cutoff }

                    // حفظ القائمة المحدثة
                    prefs[strikesKey(pkg)] = Json.encodeToString(strikes)

                    Timber.d("TempBan: ${strikes.size} strikes for $pkg (window=${STRIKES_WINDOW_MS / 1000}s)")

                    // إذا وصلت 3 ضربات → تفعيل الحظر المؤقت
                    if (strikes.size >= STRIKES_THRESHOLD) {
                        val cooldownEnd = now + COOLDOWN_MS
                        prefs[cooldownKey(pkg)] = cooldownEnd
                        cooldownCache[pkg] = cooldownEnd
                        strikes.clear()
                        prefs[strikesKey(pkg)] = Json.encodeToString(strikes)

                        Timber.w("TempBan: 🔒 TRIGGERED for $pkg - blocked for ${COOLDOWN_MS / 1000 / 60} minutes!")
                        onTempBanTriggered?.invoke(pkg)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "TempBan: failed to record strike for $pkg")
            }
        }

        // فحص سريع: هل التطبيق في فترة حظر حالياً؟
        return isInCooldown(pkg)
    }

    /**
     * فحص سريع (main-thread safe) هل التطبيق في فترة حظر مؤقت.
     * يستخدم cache في الذاكرة للسرعة القصوى.
     */
    fun isInCooldown(pkg: String): Boolean {
        val cached = cooldownCache[pkg]
        if (cached != null) {
            if (System.currentTimeMillis() < cached) return true
            // انتهت فترة الحظر → إزالة من cache
            cooldownCache.remove(pkg)
        }
        return false
    }

    /**
     * فحص شامل (async) - يتحقق من DataStore ويُحدّث cache.
     * يُستخدم عند بدء التطبيق أو عند تغيير الحالة.
     */
    suspend fun checkCooldown(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        val prefs = context.tempBanStore.data.first()
        val end = prefs[cooldownKey(pkg)] ?: 0L
        return if (end > now) {
            cooldownCache[pkg] = end
            true
        } else {
            cooldownCache.remove(pkg)
            false
        }
    }

    /**
     * حذف الحظر المؤقت لتطبيق معين (مثلاً بعد انتهاء المدة أو يدوياً).
     */
    fun clearCooldown(pkg: String) {
        cooldownCache.remove(pkg)
        scope.launch {
            try {
                context.tempBanStore.edit { prefs ->
                    prefs.remove(cooldownKey(pkg))
                    prefs.remove(strikesKey(pkg))
                }
                Timber.d("TempBan: cleared for $pkg")
            } catch (e: Exception) {
                Timber.w(e, "TempBan: failed to clear for $pkg")
            }
        }
    }

    /**
     * حذف جميع الحظرات المؤقتة (مثلاً عند إيقاف الدرع).
     */
    fun clearAllCooldowns() {
        cooldownCache.clear()
        scope.launch {
            try {
                context.tempBanStore.edit { prefs ->
                    prefs.clear()
                }
                Timber.d("TempBan: cleared all cooldowns")
            } catch (e: Exception) {
                Timber.w(e, "TempBan: failed to clear all")
            }
        }
    }

    /**
     * الحصول على قائمة التطبيقات المحظورة حالياً.
     */
    fun getActiveCooldowns(): List<Pair<String, Long>> {
        val now = System.currentTimeMillis()
        return cooldownCache.mapNotNull { (pkg, end) ->
            if (end > now) pkg to (end - now) else null
        }
    }

    /**
     * فحص دوري لتنظيف الـ cache من الانتهيات (يُستدعى كل دقيقة).
     */
    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val expired = cooldownCache.filter { (_, end) -> end <= now }.keys
        expired.forEach { cooldownCache.remove(it) }
        if (expired.isNotEmpty()) {
            Timber.d("TempBan: cleaned up ${expired.size} expired cooldowns")
        }
    }
}
