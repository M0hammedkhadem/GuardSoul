package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.AiScannerService
import com.agon.app.blocking.PornBlockerController
import com.agon.app.guardianApp
import com.agon.app.data.settings.AppSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val shieldActive: Boolean = false,
    val pornBlockerActive: Boolean = false,
    val aiScannerActive: Boolean = false,
    val uninstallProtection: Boolean = false,
    val strongProtection: Boolean = false,
    val blockSafeMode: Boolean = false,
    val strictMode: Boolean = false,
    val profileName: String = "",
    val facebookMode: String = "off",
    val youtubeMode: String = "off",
    val instagramMode: String = "off",
    val remoteMonitoring: Boolean = false,
    val isLoading: Boolean = true
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.guardianApp()!!.repository
    private val settings = repo.getAppSettings()

    // Issue #198: Combine all flows into a single StateFlow to prevent excessive recompositions
    val uiState: StateFlow<SettingsUiState> = combine(
        settings.shieldActiveFlow,
        settings.pornBlockerFlow,
        settings.aiScannerFlow,
        settings.uninstallProtectionFlow,
        settings.strongProtectionFlow,
        settings.blockSafeModeFlow,
        settings.strictModeFlow,
        settings.profileNameFlow,
        settings.facebookModeFlow,
        settings.youtubeModeFlow,
        settings.instagramModeFlow,
        settings.remoteMonitoringEnabledFlow
    ) { args ->
        SettingsUiState(
            shieldActive = args[0] as Boolean,
            pornBlockerActive = args[1] as Boolean,
            aiScannerActive = args[2] as Boolean,
            uninstallProtection = args[3] as Boolean,
            strongProtection = args[4] as Boolean,
            blockSafeMode = args[5] as Boolean,
            strictMode = args[6] as Boolean,
            profileName = args[7] as String,
            facebookMode = args[8] as String,
            youtubeMode = args[9] as String,
            instagramMode = args[10] as String,
            remoteMonitoring = args[11] as Boolean,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState() // Issue #200: Default values match initial expected state
    )

    fun setShieldActive(v: Boolean) = viewModelScope.launch { settings.setShieldActive(v) }
    fun setPornBlocker(v: Boolean) = viewModelScope.launch {
        settings.setPornBlocker(v)
        PornBlockerController.sync(getApplication())
    }
    fun setAiScanner(v: Boolean) = viewModelScope.launch {
        settings.setAiScanner(v)
        if (!v) AiScannerService.stop(getApplication())
    }
    fun setUninstallProtection(v: Boolean) = viewModelScope.launch { settings.setUninstallProtection(v) }
    fun setStrongProtection(v: Boolean) = viewModelScope.launch { settings.setStrongProtection(v) }
    fun setBlockSafeMode(v: Boolean) = viewModelScope.launch { settings.setBlockSafeMode(v) }
    fun setStrictMode(v: Boolean) = viewModelScope.launch { settings.setStrictMode(v) }
    fun setRemoteMonitoring(v: Boolean) = viewModelScope.launch { settings.setRemoteMonitoringEnabled(v) }
    
    fun resetAllSettings() = viewModelScope.launch {
        repo.resetAllSettings()
    }

    fun resetStatistics() = viewModelScope.launch {
        repo.clearAllEvents()
    }
}
