package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.AppBlockerService
import com.agon.app.AiScannerService
import com.agon.app.DnsVpnService
import com.agon.app.GuardianApp
import com.agon.app.data.local.dao.MostBlockedApp
import com.agon.app.utils.SecurityUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as GuardianApp).repository
    private val settings = repo.getAppSettings()

    val shieldActive: StateFlow<Boolean> = settings.shieldActiveFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val trialMode: StateFlow<Boolean> = settings.trialModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val deactivationDelay: StateFlow<Int> = settings.deactivationDelayFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val strictMode: StateFlow<Boolean> = settings.strictModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val totalBlocks: StateFlow<Int> = repo.totalBlocksFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val blocksToday: StateFlow<Int> = repo.blocksTodayFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val mostBlockedApp: StateFlow<MostBlockedApp?> = repo.mostBlockedAppFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val streakCount: StateFlow<Int> = settings.streakCountFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val daysActive: StateFlow<Int> = repo.getAllBlockEvents().map { events ->
        events.map { it.timestamp / 86400000L }.distinct().count()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val profileName: StateFlow<String> = settings.profileNameFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val hasPin: StateFlow<Boolean> = settings.pinHashFlow.map { it.isNotBlank() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val xpPoints: StateFlow<Int> = settings.xpPointsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val level: StateFlow<Int> = settings.levelFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val pornBlockerActive: StateFlow<Boolean> = settings.pornBlockerFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val aiScannerActive: StateFlow<Boolean> = settings.aiScannerFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val uninstallProtectionActive: StateFlow<Boolean> = settings.uninstallProtectionFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val facebookMode: StateFlow<String> = settings.facebookModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "off")
    val youtubeMode: StateFlow<String> = settings.youtubeModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "off")
    val socialInstagram: StateFlow<Boolean> = settings.socialInstagramFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val socialSnapchat: StateFlow<Boolean> = settings.socialSnapchatFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val socialTwitter: StateFlow<Boolean> = settings.socialTwitterFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val socialTiktok: StateFlow<Boolean> = settings.socialTiktokFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val blockedLinksToday: StateFlow<Int> = repo.getAllBlockEvents().map { events ->
        val todayStart = getTodayStart()
        events.count { it.timestamp >= todayStart && it.blockType == "dns_filter" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val blockedAppsToday: StateFlow<Int> = repo.getAllBlockEvents().map { events ->
        val todayStart = getTodayStart()
        events.count { it.timestamp >= todayStart && it.blockType != "dns_filter" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val recentEvents: StateFlow<List<com.agon.app.data.local.entity.BlockEventEntity>> =
        repo.getRecentBlockEvents(10).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentLevel: Int get() = calculateLevel(xpPoints.value)
    val xpForNextLevel: Int get() = calculateXpForLevel(currentLevel + 1)
    val xpForCurrentLevel: Int get() = calculateXpForLevel(currentLevel)
    val xpProgress: Float get() {
        val current = xpPoints.value - xpForCurrentLevel
        val needed = xpForNextLevel - xpForCurrentLevel
        return if (needed > 0) current.toFloat() / needed else 0f
    }

    private val _countdownActive = MutableStateFlow(false)
    val countdownActive: StateFlow<Boolean> = _countdownActive.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _showPinDialog = MutableStateFlow(false)
    val showPinDialog: StateFlow<Boolean> = _showPinDialog.asStateFlow()

    private val _pinError = MutableStateFlow(false)
    val pinError: StateFlow<Boolean> = _pinError.asStateFlow()

    private var countdownJob: Job? = null

    companion object {
        private const val XP_PER_STREAK_DAY = 50
        private const val XP_PER_BLOCK_PREVENTED = 10
        private const val XP_PER_DAY_ACTIVE = 25
        private const val BASE_XP_PER_LEVEL = 100
        private const val LEVEL_MULTIPLIER = 1.5
    }

    private fun calculateLevel(xp: Int): Int {
        var level = 1
        var xpNeeded = BASE_XP_PER_LEVEL
        var totalXp = 0
        while (totalXp + xpNeeded <= xp) {
            totalXp += xpNeeded
            level++
            xpNeeded = (xpNeeded * LEVEL_MULTIPLIER).toInt()
        }
        return level
    }

    private fun calculateXpForLevel(level: Int): Int {
        var xp = 0
        var xpNeeded = BASE_XP_PER_LEVEL
        for (i in 1 until level) {
            xp += xpNeeded
            xpNeeded = (xpNeeded * LEVEL_MULTIPLIER).toInt()
        }
        return xp
    }

    fun addXp(amount: Int) {
        viewModelScope.launch {
            val newXp = xpPoints.value + amount
            settings.setXpPoints(newXp)
            val newLevel = calculateLevel(newXp)
            if (newLevel > level.value) {
                settings.setLevel(newLevel)
            }
        }
    }

    fun toggleShield() {
        viewModelScope.launch {
            val current = shieldActive.value
            if (!current) {
                val now = System.currentTimeMillis()
                val lastActive = settings.lastActiveDateFlow.first()
                val lastDay = lastActive / 86_400_000L
                val today = now / 86_400_000L

                if (lastActive == 0L) {
                    settings.setStreakCount(1)
                } else if (today - lastDay == 1L) {
                    settings.setStreakCount(streakCount.value + 1)
                } else if (today - lastDay > 1L) {
                    settings.setStreakCount(1)
                }
                settings.setLastActiveDate(now)
                settings.setShieldActive(true)
                val context = getApplication<GuardianApp>()
                AppBlockerService.start(context)
                addXp(XP_PER_DAY_ACTIVE)
            }
        }
    }

    fun startDeactivation() {
        if (trialMode.value) {
            completeDeactivation()
            return
        }
        val delayMin = deactivationDelay.value
        if (delayMin <= 0) {
            completeDeactivation()
            return
        }
        _countdownActive.value = true
        _remainingSeconds.value = delayMin * 60
        countdownJob = viewModelScope.launch {
            while (_remainingSeconds.value > 0) {
                delay(1000L)
                _remainingSeconds.value = _remainingSeconds.value - 1
            }
            _countdownActive.value = false
            if (_showPinDialog.value) return@launch
            val strict = strictMode.value
            val pin = hasPin.value
            if (strict && pin) {
                _showPinDialog.value = true
                _pinError.value = false
            } else {
                completeDeactivation()
            }
        }
    }

    fun cancelDeactivation() {
        countdownJob?.cancel()
        _countdownActive.value = false
        _remainingSeconds.value = 0
        _showPinDialog.value = false
        _pinError.value = false
    }

    fun verifyPin(input: String) {
        viewModelScope.launch {
            val storedHash = settings.getPinHash()
            val inputHash = SecurityUtils.hashPin(input)
            if (inputHash == storedHash) {
                _showPinDialog.value = false
                _pinError.value = false
                completeDeactivation()
            } else {
                _pinError.value = true
            }
        }
    }

    fun dismissPinDialog() {
        _showPinDialog.value = false
        _pinError.value = false
    }

    private fun completeDeactivation() {
        viewModelScope.launch {
            val context = getApplication<GuardianApp>()

            AppBlockerService.stop(context)

            if (settings.isPornBlockerActive()) {
                DnsVpnService.stop(context)
                settings.setPornBlocker(false)
            }

            if (settings.aiScannerFlow.first()) {
                val stopIntent = android.content.Intent(context, AiScannerService::class.java).apply {
                    action = AiScannerService.ACTION_STOP
                }
                context.startService(stopIntent)
                settings.setAiScanner(false)
            }

            settings.setShieldActive(false)
            settings.setStreakCount(0)
            _countdownActive.value = false
            _remainingSeconds.value = 0
        }
    }

    fun setStrictMode(v: Boolean) {
        viewModelScope.launch { settings.setStrictMode(v) }
    }

    fun setTrialMode(v: Boolean) {
        viewModelScope.launch { settings.setTrialMode(v) }
    }

    fun setDeactivationDelay(minutes: Int) {
        viewModelScope.launch { settings.setDeactivationDelay(minutes) }
    }

    fun setPornBlocker(v: Boolean) {
        viewModelScope.launch {
            val context = getApplication<GuardianApp>()
            if (v) {
                val intent = android.net.VpnService.prepare(context)
                if (intent == null) {
                    settings.setPornBlocker(true)
                }
            } else {
                com.agon.app.DnsVpnService.stop(context)
                settings.setPornBlocker(false)
            }
        }
    }

    fun setFacebookMode(mode: String) {
        viewModelScope.launch { settings.setFacebookMode(mode) }
    }

    fun setAiScanner(v: Boolean) {
        viewModelScope.launch {
            val context = getApplication<GuardianApp>()
            if (v) {
                settings.setAiScanner(true)
            } else {
                val intent = android.content.Intent(context, com.agon.app.AiScannerService::class.java).apply {
                    action = com.agon.app.AiScannerService.ACTION_STOP
                }
                context.startService(intent)
                settings.setAiScanner(false)
            }
        }
    }

    fun setUninstallProtection(v: Boolean) {
        viewModelScope.launch { settings.setUninstallProtection(v) }
    }

    private fun getTodayStart(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
