package com.agon.app.viewmodel

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.AppBlockerService
import com.agon.app.GuardianApp
import com.agon.app.data.local.entity.AppLimitEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppTimeUsage(
    val packageName: String,
    val appLabel: String,
    val totalTimeInForeground: Long
)

class TimeLimitsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as GuardianApp).repository

    val appLimits: StateFlow<List<AppLimitEntity>> = repo.getAllAppLimits()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _appUsage = MutableStateFlow<List<AppTimeUsage>>(emptyList())
    val appUsage: StateFlow<List<AppTimeUsage>> = _appUsage.asStateFlow()

    fun addLimit(packageName: String, appLabel: String, dailyMinutes: Int) {
        viewModelScope.launch {
            repo.setAppLimit(packageName, appLabel, dailyMinutes)
            AppBlockerService.scheduleTimeLimitCheck(getApplication())
        }
    }

    fun removeLimit(limit: AppLimitEntity) {
        viewModelScope.launch {
            repo.removeAppLimit(limit)
            AppBlockerService.scheduleTimeLimitCheck(getApplication())
        }
    }

    fun refreshUsage() {
        viewModelScope.launch {
            val usm = getApplication<Application>().getSystemService(android.content.Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 86400000L
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            val pm = getApplication<Application>().packageManager
            val usage = stats.mapNotNull { stat ->
                val label = try {
                    val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) { stat.packageName }
                AppTimeUsage(stat.packageName, label, stat.totalTimeInForeground)
            }.sortedByDescending { it.totalTimeInForeground }.take(20)
            _appUsage.value = usage
        }
    }
}
