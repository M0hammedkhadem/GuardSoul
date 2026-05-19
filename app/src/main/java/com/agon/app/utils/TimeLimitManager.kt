package com.agon.app.utils

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.agon.app.data.DailyTimeLimit
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

class TimeLimitManager(private val context: Context) {

    companion object {
        private const val TAG = "TimeLimitManager"
        private const val CACHE_TTL_MS = 30_000L
    }

    private val usageCache = ConcurrentHashMap<String, CachedUsage>()

    private data class CachedUsage(val minutes: Int, val cachedAt: Long)

    fun hasUsageAccess(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun hasExceededLimit(packageName: String, limits: List<DailyTimeLimit>): Boolean {
        if (!hasUsageAccess()) return false
        val limit = limits.find { it.packageName == packageName } ?: return false
        val usedMinutes = getTodayUsageMinutesCached(packageName)
        val exceeded = usedMinutes >= limit.dailyMinutes
        if (exceeded) {
            Timber.tag(TAG).d("$packageName: used ${usedMinutes}m / ${limit.dailyMinutes}m limit → BLOCK")
        }
        return exceeded
    }

    fun minutesRemaining(packageName: String, limits: List<DailyTimeLimit>): Int {
        val limit = limits.find { it.packageName == packageName } ?: return Int.MAX_VALUE
        return maxOf(0, limit.dailyMinutes - getTodayUsageMinutesCached(packageName))
    }

    fun invalidateCache() {
        usageCache.clear()
    }

    private fun getTodayUsageMinutesCached(packageName: String): Int {
        val cached = usageCache[packageName]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.cachedAt < CACHE_TTL_MS) {
            return cached.minutes
        }
        val minutes = getTodayUsageMinutes(packageName)
        usageCache[packageName] = CachedUsage(minutes, now)
        return minutes
    }

    private fun getTodayUsageMinutes(packageName: String): Int {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis
            val now = System.currentTimeMillis()

            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
            val appStat = stats?.find { it.packageName == packageName }
            if (appStat != null) {
                val totalTime = appStat.totalTimeInForeground
                (totalTime / 60000).toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("Failed to get usage for $packageName: ${e.message}")
            0
        }
    }

    fun getDailyResetEpoch(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
}