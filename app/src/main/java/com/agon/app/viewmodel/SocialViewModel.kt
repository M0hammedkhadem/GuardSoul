package com.agon.app.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.utils.AccessibilityUtils
import com.agon.app.services.GuardSoulAccessibilityService
import com.agon.app.guardianApp
import com.agon.app.data.local.dao.MostBlockedApp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface SocialUiState {
    data object Loading : SocialUiState
    data object Ready : SocialUiState
    data class Error(val message: String) : SocialUiState
}

class SocialViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.guardianApp()
        ?: throw IllegalStateException("Application must be GuardianApp")
    private val repo = app.repository
    private val settings = repo.getAppSettings()
    private val context = application

    private val _uiState = MutableStateFlow<SocialUiState>(SocialUiState.Loading)
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    val instagramMode: StateFlow<String> = settings.instagramModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "off")
    val snapchat: StateFlow<Boolean> = settings.socialSnapchatFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val twitter: StateFlow<Boolean> = settings.socialTwitterFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val tiktok: StateFlow<Boolean> = settings.socialTiktokFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val youtubeMode: StateFlow<String> = settings.youtubeModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "off")
    val facebookMode: StateFlow<String> = settings.facebookModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "off")

    val blocksToday: StateFlow<Int> = repo.blocksTodayFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val blocksPerApp: StateFlow<List<MostBlockedApp>> = repo.blocksTodayPerApp().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedApps: StateFlow<Set<String>> = settings.blockedAppsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val shortstopDailyQuotaMinutes = settings.shortstopDailyQuotaMinutesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)
    val shortstopBreakIntervalMinutes = settings.shortstopBreakIntervalMinutesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)
    val shortstopBreakLengthMinutes = settings.shortstopBreakLengthMinutesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)
    val shortstopMinutesSpentToday = settings.shortstopMinutesSpentTodayFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val shortstopDailyQuotaExceeded = settings.shortstopDailyQuotaExceededFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val shortstopBreakActive = settings.shortstopBreakActiveFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val shortstopBlockedHourActive = settings.shortstopBlockedHourActiveFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _blockerServiceRunning = MutableStateFlow(false)
    val accessibilityServiceRunning: StateFlow<Boolean> = _blockerServiceRunning.asStateFlow()

    val instagramServiceRunning = combine(accessibilityServiceRunning, instagramMode) { r, m -> r && m == "reels" }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val youtubeServiceRunning = combine(accessibilityServiceRunning, youtubeMode) { r, m -> r && m == "shorts" }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val facebookServiceRunning = combine(accessibilityServiceRunning, facebookMode) { r, m -> r && m == "reels" }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val snapchatServiceRunning = combine(accessibilityServiceRunning, snapchat) { r, b -> r && b }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val twitterServiceRunning = combine(accessibilityServiceRunning, twitter) { r, b -> r && b }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val tiktokServiceRunning = combine(accessibilityServiceRunning, tiktok) { r, b -> r && b }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val accessibilityObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { _blockerServiceRunning.value = AccessibilityUtils.isServiceEnabled(context, GuardSoulAccessibilityService::class.java) }
    }

    init {
        context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, accessibilityObserver)
        _blockerServiceRunning.value = AccessibilityUtils.isServiceEnabled(context, GuardSoulAccessibilityService::class.java)
    }

    private suspend fun enforceStrictMode(): Boolean {
        if (settings.isShieldActive() && settings.isStrictMode()) {
            _uiState.value = SocialUiState.Error("Strict Mode is active. Settings are locked.")
            return false
        }
        return true
    }

    fun setInstagramMode(v: String) = viewModelScope.launch { if (enforceStrictMode()) settings.setInstagramMode(v) }
    fun setYoutubeMode(v: String) = viewModelScope.launch { if (enforceStrictMode()) settings.setYoutubeMode(v) }
    fun setFacebookMode(v: String) = viewModelScope.launch { if (enforceStrictMode()) settings.setFacebookMode(v) }
    fun setSnapchat(v: Boolean) = viewModelScope.launch { if (enforceStrictMode()) settings.setSocialSnapchat(v) }
    fun setTwitter(v: Boolean) = viewModelScope.launch { if (enforceStrictMode()) settings.setSocialTwitter(v) }
    fun setTiktok(v: Boolean) = viewModelScope.launch { if (enforceStrictMode()) settings.setSocialTiktok(v) }

    fun toggleBlockedApp(pkg: String) {
        viewModelScope.launch {
            if (!enforceStrictMode()) return@launch
            val current = settings.blockedAppsFlow.first()
            if (current.contains(pkg)) settings.setBlockedApps(current - pkg)
            else settings.setBlockedApps(current + pkg)
        }
    }

    // FIX: Apply Strict Mode to Shortstop settings too
    fun setShortstopDailyQuota(m: Int) = viewModelScope.launch { if (enforceStrictMode()) settings.setShortstopDailyQuotaMinutes(m.coerceIn(0, 240)) }
    fun setShortstopBreakInterval(m: Int) = viewModelScope.launch { if (enforceStrictMode()) settings.setShortstopBreakIntervalMinutes(m.coerceIn(0, 120)) }
    fun setShortstopBreakLength(m: Int) = viewModelScope.launch { if (enforceStrictMode()) settings.setShortstopBreakLengthMinutes(m.coerceIn(0, 60)) }

    override fun onCleared() {
        super.onCleared()
        try {
            context.contentResolver.unregisterContentObserver(accessibilityObserver)
        } catch (_: Exception) {}
    }

    fun dismissError() {
        _uiState.value = SocialUiState.Ready
    }
}
