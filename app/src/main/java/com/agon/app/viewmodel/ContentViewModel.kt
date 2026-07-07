package com.agon.app.viewmodel

import android.app.Application
import android.net.VpnService
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.guardianApp
import com.agon.app.services.PornBlockerVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ContentViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.guardianApp()
        ?: throw IllegalStateException("Application must be GuardianApp")
    private val settings = app.repository.getAppSettings()
    private val ctx = application

    val pornBlockerEnabled: StateFlow<Boolean> = settings.pornBlockerEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val aiExplorerEnabled: StateFlow<Boolean> = settings.aiExplorerEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val uninstallProtectionEnabled: StateFlow<Boolean> = settings.uninstallProtectionEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _vpnActive = MutableStateFlow(false)
    val vpnActive: StateFlow<Boolean> = _vpnActive.asStateFlow()

    init {
        _vpnActive.value = PornBlockerVpnService.isVpnActive(ctx)
    }

    fun refreshVpnState() {
        _vpnActive.value = PornBlockerVpnService.isVpnActive(ctx)
    }

    fun requestVpnPermission(vpnPermissionLauncher: ActivityResultLauncher<android.content.Intent>) {
        val intent = VpnService.prepare(ctx)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpn()
        }
    }

    fun startVpn() {
        PornBlockerVpnService.start(ctx)
        _vpnActive.value = true
    }

    fun stopVpn() {
        PornBlockerVpnService.stop(ctx)
        _vpnActive.value = false
    }

    fun togglePornBlocker(vpnPermissionLauncher: ActivityResultLauncher<android.content.Intent>?) = viewModelScope.launch {
        if (pornBlockerEnabled.value) {
            settings.setPornBlockerEnabled(false)
            stopVpn()
        } else {
            settings.setPornBlockerEnabled(true)
            if (vpnPermissionLauncher != null) {
                requestVpnPermission(vpnPermissionLauncher)
            } else {
                startVpn()
            }
        }
    }

    fun toggleAiExplorer() = viewModelScope.launch {
        settings.setAiExplorerEnabled(!aiExplorerEnabled.value)
    }

    fun toggleUninstallProtection() = viewModelScope.launch {
        settings.setUninstallProtectionEnabled(!uninstallProtectionEnabled.value)
    }
}
