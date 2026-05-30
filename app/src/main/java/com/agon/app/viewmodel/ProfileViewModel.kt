package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.GuardianApp
import com.agon.app.data.BadgeWithState
import com.agon.app.data.BadgesData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as GuardianApp).repository
    private val settings = repo.getAppSettings()

    val profileName: StateFlow<String> = settings.profileNameFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val shieldActive: StateFlow<Boolean> = settings.shieldActiveFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val totalBlocks: StateFlow<Int> = repo.totalBlocksFlow()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val hasPin: StateFlow<Boolean> = settings.pinHashFlow.map { it.isNotBlank() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val trialMode: StateFlow<Boolean> = settings.trialModeFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val xpPoints: StateFlow<Int> = settings.xpPointsFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val level: StateFlow<Int> = settings.levelFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)
    val streakCount: StateFlow<Int> = settings.streakCountFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _xpForNextLevel = MutableStateFlow(100)
    val xpForNextLevel: StateFlow<Int> = _xpForNextLevel.asStateFlow()

    private val _xpProgress = MutableStateFlow(0f)
    val xpProgress: StateFlow<Float> = _xpProgress.asStateFlow()

    init {
        viewModelScope.launch {
            combine(xpPoints, level) { xp, lvl ->
                val currentXp = calculateXpForLevel(lvl)
                val nextXp = calculateXpForLevel(lvl + 1)
                val needed = nextXp - currentXp
                val progress = if (needed > 0) (xp - currentXp).toFloat() / needed else 0f
                _xpProgress.value = progress
                _xpForNextLevel.value = nextXp
            }.collect()
        }
    }

    val daysActive: StateFlow<Int> = repo.getAllBlockEvents().map { events ->
        events.map { it.timestamp / 86400000L }.distinct().count()
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val heatmapData: StateFlow<Map<Long, Int>> = repo.getAllBlockEvents().map { events ->
        val oneYearAgo = System.currentTimeMillis() - 365L * 86400000L
        events.filter { it.timestamp >= oneYearAgo }
            .groupBy { it.timestamp / 86400000L }
            .mapValues { it.value.size }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val badges: StateFlow<List<BadgeWithState>> = combine(
        settings.xpPointsFlow,
        settings.levelFlow,
        settings.streakCountFlow,
        repo.totalBlocksFlow(),
        daysActive
    ) { xp, lvl, streak, blocks, days ->
        BadgesData.allBadges.map { badge ->
            BadgeWithState(badge, badge.isUnlocked(xp, lvl, streak, blocks, days))
        }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun calculateXpForLevel(level: Int): Int {
        var xp = 0
        var xpNeeded = 100
        for (i in 1 until level) {
            xp += xpNeeded
            xpNeeded = (xpNeeded * 1.5).toInt()
        }
        return xp
    }

    fun saveName(name: String) {
        viewModelScope.launch { settings.setProfileName(name) }
    }
}
