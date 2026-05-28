package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.GuardianApp
import com.agon.app.data.local.dao.MostBlockedApp
import com.agon.app.data.local.entity.BlockEventEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as GuardianApp).repository
    private val settings = repo.getAppSettings()

    val totalBlocks: StateFlow<Int> = repo.totalBlocksFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val blocksToday: StateFlow<Int> = repo.blocksTodayFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val mostBlockedApp: StateFlow<MostBlockedApp?> = repo.mostBlockedAppFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val recentEvents: StateFlow<List<BlockEventEntity>> = repo.getRecentBlockEvents(50).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val streakCount: StateFlow<Int> = settings.streakCountFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
