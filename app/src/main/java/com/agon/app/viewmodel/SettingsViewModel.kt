package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.AppBlockerService
import com.agon.app.AiScannerService
import com.agon.app.DnsVpnService
import com.agon.app.GuardianApp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as GuardianApp).repository
    private val settings = repo.getAppSettings()

    val shieldActive: StateFlow<Boolean> = settings.shieldActiveFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val vpnActive: StateFlow<Boolean> = settings.pornBlockerFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val aiActive: StateFlow<Boolean> = settings.aiScannerFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val uninstallProt: StateFlow<Boolean> = settings.uninstallProtectionFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val instagram: StateFlow<Boolean> = settings.socialInstagramFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
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

    fun resetStatistics() {
        viewModelScope.launch {
            repo.clearAllEvents()
            settings.setStreakCount(0)
        }
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            val context = getApplication<GuardianApp>()
            AppBlockerService.stop(context)
            if (settings.isPornBlockerActive()) {
                DnsVpnService.stop(context)
            }
            if (settings.aiScannerFlow.first()) {
                val stopIntent = android.content.Intent(context, AiScannerService::class.java).apply {
                    action = AiScannerService.ACTION_STOP
                }
                context.startService(stopIntent)
            }
            repo.resetAllSettings()
        }
    }
}
