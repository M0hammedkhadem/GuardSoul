package com.agon.app.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
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

    val instagramMode: StateFlow<String> = settings.instagramModeFlow
        .distinctUntilChanged()
        .map { _uiState.value = SocialUiState.Ready; it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "off")
    val snapchat: StateFlow<Boolean> = settings.socialSnapchatFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val twitter: StateFlow<Boolean> = settings.socialTwitterFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val tiktok: StateFlow<Boolean> = settings.socialTiktokFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val youtubeMode: StateFlow<String> = settings.youtubeModeFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "off")
    val facebookMode: StateFlow<String> = settings.facebookModeFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "off")

    val blocksToday: StateFlow<Int> = repo.blocksTodayFlow()
        .distinctUntilChanged()
        .catch { _uiState.value = SocialUiState.Error("Failed to load blocks") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val blocksPerApp: StateFlow<List<MostBlockedApp>> = repo.blocksTodayPerApp()
        .distinctUntilChanged()
        .catch { _uiState.value = SocialUiState.Error("Failed to load block stats") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _blockerServiceRunning = MutableStateFlow(false)

    private val accessibilityObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            _blockerServiceRunning.value = AccessibilityUtils.isServiceEnabled(context, GuardSoulAccessibilityService::class.java)
        }
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            _blockerServiceRunning.value = AccessibilityUtils.isServiceEnabled(context, GuardSoulAccessibilityService::class.java)
        }
    }

    init {
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            accessibilityObserver
        )
        _blockerServiceRunning.value = AccessibilityUtils.isServiceEnabled(context, GuardSoulAccessibilityService::class.java)
    }

    val accessibilityServiceRunning: StateFlow<Boolean> = _blockerServiceRunning.asStateFlow()

    /**
     * Per-app partial-block readiness. Returns true only when the
     * accessibility service is enabled AND the user has selected a partial
     * block mode that actually requires it ("reels"/"shorts"). For "full"
     * mode the AppBlockerService handles the block, and for "off" the
     * service is irrelevant.
     */
    val instagramServiceRunning: StateFlow<Boolean> = combine(
        accessibilityServiceRunning, instagramMode
    ) { running, mode -> running && mode == "reels" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val youtubeServiceRunning: StateFlow<Boolean> = combine(
        accessibilityServiceRunning, youtubeMode
    ) { running, mode -> running && mode == "shorts" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val facebookServiceRunning: StateFlow<Boolean> = combine(
        accessibilityServiceRunning, facebookMode
    ) { running, mode -> running && mode == "reels" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // --- Shortstop scheduling + quota --------------------------------
    val shortstopDailyQuotaMinutes: StateFlow<Int> = settings.shortstopDailyQuotaMinutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)
    val shortstopBreakIntervalMinutes: StateFlow<Int> = settings.shortstopBreakIntervalMinutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)
    val shortstopBreakLengthMinutes: StateFlow<Int> = settings.shortstopBreakLengthMinutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)
    val shortstopMinutesSpentToday: StateFlow<Int> = settings.shortstopMinutesSpentTodayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val shortstopBlockedHourActive: StateFlow<Boolean> = settings.shortstopBlockedHourActiveFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val shortstopDailyQuotaExceeded: StateFlow<Boolean> = settings.shortstopDailyQuotaExceededFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val shortstopBreakActive: StateFlow<Boolean> = settings.shortstopBreakActiveFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val shortstopBreakEndsAt: StateFlow<Long> = settings.shortstopBreakEndsAtFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun setShortstopDailyQuota(minutes: Int) = viewModelScope.launch {
        try { settings.setShortstopDailyQuotaMinutes(minutes.coerceIn(0, 240)) }
        catch (e: Exception) { timber.log.Timber.w(e, "SocialViewModel: setShortstopDailyQuota failed") }
    }
    fun setShortstopBreakInterval(minutes: Int) = viewModelScope.launch {
        try { settings.setShortstopBreakIntervalMinutes(minutes.coerceIn(0, 120)) }
        catch (e: Exception) { timber.log.Timber.w(e, "SocialViewModel: setShortstopBreakInterval failed") }
    }
    fun setShortstopBreakLength(minutes: Int) = viewModelScope.launch {
        try { settings.setShortstopBreakLengthMinutes(minutes.coerceIn(0, 60)) }
        catch (e: Exception) { timber.log.Timber.w(e, "SocialViewModel: setShortstopBreakLength failed") }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.contentResolver.unregisterContentObserver(accessibilityObserver)
        } catch (e: Exception) {
            // unregisterContentObserver can throw IllegalArgumentException
            // if the observer was already detached. Log and swallow.
            timber.log.Timber.w(e, "SocialViewModel: unregisterContentObserver failed")
        }
    }

    fun dismissError() {
        _uiState.value = SocialUiState.Ready
    }

    fun setInstagramMode(v: String) = viewModelScope.launch {
        try {
            settings.setInstagramMode(v)
        } catch (e: Exception) {
            _uiState.value = SocialUiState.Error("Failed to update Instagram setting")
        }
    }
    fun setSnapchat(v: Boolean) = viewModelScope.launch {
        try {
            settings.setSocialSnapchat(v)
            ensureAppBlockerRunning()
        } catch (e: Exception) {
            _uiState.value = SocialUiState.Error("Failed to update Snapchat setting")
        }
    }
    fun setTwitter(v: Boolean) = viewModelScope.launch {
        try {
            settings.setSocialTwitter(v)
            ensureAppBlockerRunning()
        } catch (e: Exception) {
            _uiState.value = SocialUiState.Error("Failed to update Twitter setting")
        }
    }
    fun setTiktok(v: Boolean) = viewModelScope.launch {
        try {
            settings.setSocialTiktok(v)
            ensureAppBlockerRunning()
        } catch (e: Exception) {
            _uiState.value = SocialUiState.Error("Failed to update TikTok setting")
        }
    }
    fun setYoutubeMode(v: String) = viewModelScope.launch {
        try {
            settings.setYoutubeMode(v)
            ensureAppBlockerRunning()
        } catch (e: Exception) {
            _uiState.value = SocialUiState.Error("Failed to update YouTube mode")
        }
    }
    fun setFacebookMode(v: String) = viewModelScope.launch {
        try {
            settings.setFacebookMode(v)
            ensureAppBlockerRunning()
        } catch (e: Exception) {
            _uiState.value = SocialUiState.Error("Failed to update Facebook mode")
        }
    }

    /**
     * If the shield is on and a full-mode social setting is enabled, make
     * sure the foreground AppBlockerService is running so the new setting
     * takes effect on the next poll. Without this, the user could enable
     * Snapchat blocking and still get unrestricted access until the 15-min
     * WorkManager tick fires.
     */
    private suspend fun ensureAppBlockerRunning() {
        if (!settings.isShieldActive()) return
        val anyFullModeOn = settings.isSnapchatBlocked() ||
            settings.isTwitterBlocked() ||
            settings.isTiktokBlocked() ||
            settings.getYoutubeMode() == "full" ||
            settings.getFacebookMode() == "full"
        if (anyFullModeOn) {
            com.agon.app.AppBlockerService.start(context)
        }
    }
}
