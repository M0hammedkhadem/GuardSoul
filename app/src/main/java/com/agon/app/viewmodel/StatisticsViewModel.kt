package com.agon.app.viewmodel

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.guardianApp
import com.agon.app.GuardianApp
import com.agon.app.data.local.dao.MostBlockedApp
import com.agon.app.data.local.entity.AppLimitEntity
import com.agon.app.data.local.entity.BlockEventEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

data class DayStats(val label: String, val count: Int)
data class DailyBlockCount(val label: String, val count: Int)
data class AppBlockCount(val packageName: String, val appLabel: String, val count: Int)
data class AppStats(val appName: String, val packageName: String, val blockCount: Int)
data class CategoryStats(val category: String, val count: Int)

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as GuardianApp).repository
    private val settings = repo.getAppSettings()
    private val appContext = getApplication<GuardianApp>()

    val totalBlocks: StateFlow<Int> = repo.totalBlocksFlow()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val blocksToday: StateFlow<Int> = repo.blocksTodayFlow()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val mostBlockedApp: StateFlow<MostBlockedApp?> = repo.mostBlockedAppFlow()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val recentEvents: StateFlow<List<BlockEventEntity>> = repo.getRecentBlockEvents(50)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val daysActive: StateFlow<Int> = repo.getAllBlockEvents().map { events ->
        events.map { it.timestamp / 86400000L }.distinct().count()
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val currentStreak: StateFlow<Int> = combine(
        repo.getAllBlockEvents(),
        settings.lastShieldEnabledDayFlow
    ) { events, lastShieldEnabledDay ->
        // STALE-SHIELD-CHECK: pass the last shield-enabled day
        // to the streak calculator so days where the shield
        // was off don't count as "clean" (a user who had
        // unfiltered access to blocked content shouldn't be
        // rewarded with a streak).
        calculateCleanStreak(events, lastShieldEnabledDay)
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val longestStreak: StateFlow<Int> = settings.longestStreakFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val blockedAppsToday: StateFlow<List<AppStats>> = repo.getAllBlockEvents().map { events ->
        val todayStart = getTodayStart()
        events.filter { it.timestamp >= todayStart }
            .groupBy { it.packageName }
            .map { (pkg, list) ->
                val first = list.first()
                AppStats(first.appLabel.ifBlank { pkg }, pkg, list.size)
            }
            .sortedByDescending { it.blockCount }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weeklyBlocks: StateFlow<List<DayStats>> = repo.getAllBlockEvents().map { events ->
        (6 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 86400000L
            val count = events.count { it.timestamp in dayStart until dayEnd }
            DayStats(SimpleDateFormat("E", Locale.getDefault()).format(Date(dayStart)), count)
        }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topBlockedCategories: StateFlow<List<CategoryStats>> = repo.getAllBlockEvents().map { events ->
        events.groupBy { it.blockType }
            .map { (type, list) -> CategoryStats(type, list.size) }
            .sortedByDescending { it.count }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyBlocksData: StateFlow<List<DailyBlockCount>> = repo.getAllBlockEvents().map { events ->
        (6 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 86400000L
            val count = events.count { it.timestamp in dayStart until dayEnd }
            val label = SimpleDateFormat("E", Locale.getDefault()).format(Date(dayStart))
            DailyBlockCount(label, count)
        }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockDistribution: StateFlow<List<AppBlockCount>> = repo.getAllBlockEvents().map { events ->
        events.groupBy { it.packageName to it.appLabel }
            .map { (key, list) -> AppBlockCount(key.first, key.second, list.size) }
            .sortedByDescending { it.count }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streakHistoryData: StateFlow<List<DailyBlockCount>> = repo.getAllBlockEvents().map { events ->
        (13 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 86400000L
            val count = events.count { it.timestamp in dayStart until dayEnd }
            val label = SimpleDateFormat("M/d", Locale.getDefault()).format(Date(dayStart))
            DailyBlockCount(label, count)
        }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appLimits: StateFlow<List<AppLimitEntity>> = repo.getAllAppLimits()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _usageStats = MutableStateFlow<Map<String, Long>>(emptyMap())
    val usageStats: StateFlow<Map<String, Long>> = _usageStats.asStateFlow()

    init {
        viewModelScope.launch {
            refreshUsageStats()
            refreshStreak()
        }
    }

    private suspend fun refreshUsageStats() {
        val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val now = System.currentTimeMillis()
        val begin = now - 7 * 24 * 60 * 60 * 1000L
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, now)
        _usageStats.value = stats?.groupBy { it.packageName }?.mapValues { (_, list) ->
            list.sumOf { it.totalTimeInForeground }
        } ?: emptyMap()
    }

    fun refreshStreak() {
        viewModelScope.launch {
            val allEvents = repo.getAllBlockEvents().first()
            val streak = calculateCleanStreak(allEvents)
            settings.setStreakCount(streak)
            settings.setLastActiveDate(System.currentTimeMillis())
            if (streak > (longestStreak.value)) {
                settings.setLongestStreak(streak)
            }
        }
    }

    fun resetStatistics() {
        viewModelScope.launch {
            repo.clearAllEvents()
            settings.setStreakCount(0)
            settings.setLongestStreak(0)
        }
    }

    fun exportStatsAsCsv() {
        viewModelScope.launch {
            try {
                val events = repo.getAllBlockEvents().first()
                val csv = buildString {
                    appendLine("Date,App,Package,Block Type,Timestamp")
                    events.forEach { e ->
                        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(e.timestamp))
                        val app = e.appLabel.replace(",", " ")
                        val pkg = e.packageName
                        val type = e.blockType
                        appendLine("$date,$app,$pkg,$type,${e.timestamp}")
                    }
                }
                val file = File(appContext.cacheDir, "guardsoul_stats.csv")
                file.writeText(csv)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                        appContext, "${appContext.packageName}.fileprovider", file
                    ))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                appContext.startActivity(Intent.createChooser(intent, "Share Stats"))
            } catch (e: Exception) {
                timber.log.Timber.e(e, "StatsVM: CSV export failed")
            }
        }
    }

    private fun calculateCleanStreak(
        events: List<BlockEventEntity>,
        lastShieldEnabledDay: Long = 0L
    ): Int {
        if (events.isEmpty()) return 0
        var streak = 0
        var currentDate = LocalDate.now()
        // STALE-SHIELD-CHECK: the floor for the streak walk.
        // Any day earlier than `lastShieldEnabledDay` had no
        // active shield, so it must not be credited as a clean
        // day. Default 0L preserves the legacy behavior for
        // tests / first-run users.
        val floorDay: LocalDate = if (lastShieldEnabledDay > 0L) {
            java.time.Instant.ofEpochMilli(lastShieldEnabledDay)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        } else {
            // No shield-on recorded — credit nothing until the
            // user has actually enabled the shield at least
            // once.
            return 0
        }
        while (true) {
            // Don't count days before the shield was last
            // turned on.
            if (currentDate.isBefore(floorDay)) break
            val dayStart = currentDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dayEnd = currentDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dayEvents = events.filter { it.timestamp in dayStart until dayEnd }
            if (dayEvents.isEmpty()) {
                streak++
                currentDate = currentDate.minusDays(1)
            } else {
                break
            }
            if (streak > 365) break
        }
        return streak
    }

    private fun getTodayStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
