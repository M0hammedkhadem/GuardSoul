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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SocialViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.guardianApp()
        ?: throw IllegalStateException("Application must be GuardianApp")
    private val repo = app.repository
    private val settings = repo.getAppSettings()
    private val context = application

    val youtubeMode: StateFlow<String> = settings.youtubeModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "off")

    val blocksToday: StateFlow<Int> = repo.blocksTodayFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val blockedApps: StateFlow<Set<String>> = settings.blockedAppsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val instagramBlocked: StateFlow<Boolean> = settings.instagramBlockedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val snapchatBlocked: StateFlow<Boolean> = settings.snapchatBlockedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val twitterBlocked: StateFlow<Boolean> = settings.twitterBlockedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val tiktokBlocked: StateFlow<Boolean> = settings.tiktokBlockedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val facebookMode: StateFlow<String> = settings.facebookModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "off")

    private val _blockerServiceRunning = MutableStateFlow(false)
    val accessibilityServiceRunning: StateFlow<Boolean> = _blockerServiceRunning.asStateFlow()

    val youtubeServiceRunning = combine(accessibilityServiceRunning, youtubeMode) { r, m -> r && m == "shorts" }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val facebookServiceRunning = combine(accessibilityServiceRunning, facebookMode) { r, m -> r && (m == "reels" || m == "full") }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val accessibilityObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { _blockerServiceRunning.value = AccessibilityUtils.isServiceEnabled(context, GuardSoulAccessibilityService::class.java) }
    }

    init {
        context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, accessibilityObserver)
        _blockerServiceRunning.value = AccessibilityUtils.isServiceEnabled(context, GuardSoulAccessibilityService::class.java)
    }

    private suspend fun enforceStrictMode(): Boolean {
        if (settings.isShieldActive() && settings.isStrictMode()) {
            return false
        }
        return true
    }

    fun setYoutubeMode(v: String) = viewModelScope.launch { if (enforceStrictMode()) settings.setYoutubeMode(v) }

    fun toggleBlockedApp(pkg: String) {
        viewModelScope.launch {
            if (!enforceStrictMode()) return@launch
            val current = settings.blockedAppsFlow.first()
            if (current.contains(pkg)) settings.setBlockedApps(current - pkg)
            else settings.setBlockedApps(current + pkg)
        }
    }

    fun toggleInstagram() = viewModelScope.launch {
        if (!enforceStrictMode()) return@launch
        settings.setInstagramBlocked(!instagramBlocked.value)
        val pkg = "com.instagram.android"
        val current = settings.blockedAppsFlow.first()
        if (instagramBlocked.value) settings.setBlockedApps(current + pkg)
        else settings.setBlockedApps(current - pkg)
    }

    fun toggleSnapchat() = viewModelScope.launch {
        if (!enforceStrictMode()) return@launch
        settings.setSnapchatBlocked(!snapchatBlocked.value)
        val pkg = "com.snapchat.android"
        val current = settings.blockedAppsFlow.first()
        if (snapchatBlocked.value) settings.setBlockedApps(current + pkg)
        else settings.setBlockedApps(current - pkg)
    }

    fun toggleTwitter() = viewModelScope.launch {
        if (!enforceStrictMode()) return@launch
        settings.setTwitterBlocked(!twitterBlocked.value)
        val pkg = "com.twitter.android"
        val current = settings.blockedAppsFlow.first()
        if (twitterBlocked.value) settings.setBlockedApps(current + pkg)
        else settings.setBlockedApps(current - pkg)
    }

    fun toggleTiktok() = viewModelScope.launch {
        if (!enforceStrictMode()) return@launch
        settings.setTiktokBlocked(!tiktokBlocked.value)
        val pkg = "com.zhiliaoapp.musically"
        val current = settings.blockedAppsFlow.first()
        if (tiktokBlocked.value) settings.setBlockedApps(current + pkg)
        else settings.setBlockedApps(current - pkg)
    }

    fun setFacebookMode(v: String) = viewModelScope.launch {
        if (!enforceStrictMode()) return@launch
        settings.setFacebookMode(v)
        val pkg = "com.facebook.katana"
        val current = settings.blockedAppsFlow.first()
        if (v == "full") settings.setBlockedApps(current + pkg)
        else settings.setBlockedApps(current - pkg)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.contentResolver.unregisterContentObserver(accessibilityObserver)
        } catch (_: Exception) {}
    }
}
