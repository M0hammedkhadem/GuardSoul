package com.agon.app.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.guardianApp
import com.agon.app.utils.ServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ContentViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application.guardianApp()!!).repository
    private val settings = repo.getAppSettings()

    // Porn Blocker UI toggle → Safe Search (VPN) service
    val pornBlockerEnabled: StateFlow<Boolean> = settings.safeSearchEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val aiExplorerEnabled: StateFlow<Boolean> = settings.aiExplorerEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val uninstallProtectionEnabled: StateFlow<Boolean> = settings.uninstallProtectionEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val safeSearchDnsStatus: StateFlow<String> = settings.safeSearchDnsStatusFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "inactive")

    private val _dnsStatusMessage = MutableStateFlow("")
    val dnsStatusMessage: StateFlow<String> = _dnsStatusMessage.asStateFlow()

    fun togglePornBlocker(enabled: Boolean) {
        ServiceManager.setSafeSearchActive(getApplication(), enabled)
    }

    fun toggleAiExplorer(enabled: Boolean, requestCode: Int = 1001) {
        viewModelScope.launch {
            settings.setAiExplorerEnabled(enabled)
            ServiceManager.setAiScannerActive(getApplication(), enabled, requestCode)
        }
    }

    fun handleAiScannerResult(activity: Activity, resultCode: Int, data: android.content.Intent?, requestCode: Int) {
        ServiceManager.handleMediaProjectionResult(activity, resultCode, data, requestCode)
    }

    fun toggleUninstallProtection(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                ServiceManager.activateDeviceAdmin(getApplication())
            } else {
                settings.setUninstallProtectionEnabled(false)
            }
        }
    }

    // Called when user returns from DeviceAdmin settings
    fun refreshUninstallProtectionState() {
        viewModelScope.launch {
            val active = ServiceManager.isDeviceAdminActive(getApplication())
            settings.setUninstallProtectionEnabled(active)
        }
    }
}
