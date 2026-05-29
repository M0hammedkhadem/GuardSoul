package com.agon.app.viewmodel

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.GuardianApp
import com.agon.app.data.local.dao.MostBlockedApp
import com.agon.app.data.local.entity.AppLimitEntity
import com.agon.app.data.local.entity.BlockEventEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class DailyBlockCount(val label: String, val count: Int)
data class AppBlockCount(val packageName: String, val appLabel: String, val count: Int)

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as GuardianApp).repository
    private val settings = repo.getAppSettings()

    val totalBlocks: StateFlow<Int> = repo.totalBlocksFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val blocksToday: StateFlow<Int> = repo.blocksTodayFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val mostBlockedApp: StateFlow<MostBlockedApp?> = repo.mostBlockedAppFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val recentEvents: StateFlow<List<BlockEventEntity>> = repo.getRecentBlockEvents(50).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val streakCount: StateFlow<Int> = settings.streakCountFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val daysActive: StateFlow<Int> = repo.getAllBlockEvents().map { events ->
        events.map { it.timestamp / 86400000L }.distinct().count()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockDistribution: StateFlow<List<AppBlockCount>> = repo.getAllBlockEvents().map { events ->
        events.groupBy { it.packageName to it.appLabel }
            .map { (key, list) -> AppBlockCount(key.first, key.second, list.size) }
            .sortedByDescending { it.count }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appLimits: StateFlow<List<AppLimitEntity>> = repo.getAllAppLimits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _usageStats = MutableStateFlow<Map<String, Long>>(emptyMap())
    val usageStats: StateFlow<Map<String, Long>> = _usageStats.asStateFlow()

    init {
        viewModelScope.launch {
            refreshUsageStats()
        }
    }

    private suspend fun refreshUsageStats() {
        val usm = getApplication<GuardianApp>().getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val now = System.currentTimeMillis()
        val begin = now - 7 * 24 * 60 * 60 * 1000L
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, now)
        _usageStats.value = stats?.groupBy { it.packageName }?.mapValues { (_, list) ->
            list.sumOf { it.totalTimeInForeground }
        } ?: emptyMap()
    }

    fun refreshStreak() {
        viewModelScope.launch {
            val streak = repo.calculateStreak()
            settings.setStreakCount(streak)
        }
    }

    fun resetStatistics() {
        viewModelScope.launch {
            repo.clearAllEvents()
            settings.setStreakCount(0)
        }
    }
}
