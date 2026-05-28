package com.agon.app.viewmodel

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.utils.AccessibilityUtils
import com.agon.app.FacebookBlockerService
import com.agon.app.GuardianApp
import com.agon.app.data.local.dao.MostBlockedApp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SocialViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as GuardianApp).repository
    private val settings = repo.getAppSettings()
    private val context = application

    val instagram: StateFlow<Boolean> = settings.socialInstagramFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val snapchat: StateFlow<Boolean> = settings.socialSnapchatFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val twitter: StateFlow<Boolean> = settings.socialTwitterFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val tiktok: StateFlow<Boolean> = settings.socialTiktokFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val youtubeMode: StateFlow<String> = settings.youtubeModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "off")
    val facebookMode: StateFlow<String> = settings.facebookModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "off")

    val blocksToday: StateFlow<Int> = repo.blocksTodayFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val blocksPerApp: StateFlow<List<MostBlockedApp>> = repo.blocksTodayPerApp().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Both YouTube Shorts & Facebook Reels are now handled by the unified FacebookBlockerService
    val youtubeServiceRunning: StateFlow<Boolean> = flow {
        while (true) {
            emit(AccessibilityUtils.isServiceEnabled(context, FacebookBlockerService::class.java))
            kotlinx.coroutines.delay(5000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val facebookServiceRunning: StateFlow<Boolean> = flow {
        while (true) {
            emit(AccessibilityUtils.isServiceEnabled(context, FacebookBlockerService::class.java))
            kotlinx.coroutines.delay(5000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setInstagram(v: Boolean) = viewModelScope.launch { settings.setSocialInstagram(v) }
    fun setSnapchat(v: Boolean) = viewModelScope.launch { settings.setSocialSnapchat(v) }
    fun setTwitter(v: Boolean) = viewModelScope.launch { settings.setSocialTwitter(v) }
    fun setTiktok(v: Boolean) = viewModelScope.launch { settings.setSocialTiktok(v) }
    fun setYoutubeMode(v: String) = viewModelScope.launch { settings.setYoutubeMode(v) }
    fun setFacebookMode(v: String) = viewModelScope.launch { settings.setFacebookMode(v) }
}
