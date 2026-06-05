package com.agon.app.viewmodel

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.AiScannerService
import com.agon.app.AppBlockerService
import com.agon.app.PornBlockerService
import com.agon.app.blocking.PornBlockerController
import com.agon.app.guardianApp
import com.agon.app.GuardianDeviceAdminReceiver
import com.agon.app.GuardianApp
import com.agon.app.services.DeviceOwnerService
import com.agon.app.utils.KnoxManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ContentViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = (application as GuardianApp).repository.getAppSettings()
    private val context = application

    val pornBlocker: StateFlow<Boolean> = settings.pornBlockerFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val nextDnsProfileId: StateFlow<String> = settings.nextDnsProfileIdFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val aiScanner: StateFlow<Boolean> = settings.aiScannerFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val uninstallProtection: StateFlow<Boolean> = settings.uninstallProtectionFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val strongProtection: StateFlow<Boolean> = settings.strongProtectionFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val safeSearchMode: StateFlow<String> = settings.safeSearchModeFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "basic")

    val isKnoxDevice: Boolean = KnoxManager.isKnoxDevice()

    val isDeviceOwner: StateFlow<Boolean> = flow { emit(DeviceOwnerService.isDeviceOwner(context)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setPornBlocker(v: Boolean) = viewModelScope.launch {
        settings.setPornBlocker(v)
        PornBlockerController.sync(getApplication())
    }

    fun setNextDnsProfileId(v: String) = viewModelScope.launch {
        settings.setNextDnsProfileId(v)
    }

    fun setAiScanner(v: Boolean) = viewModelScope.launch {
        settings.setAiScanner(v)
        if (!v) {
            AiScannerService.stop(getApplication())
        }
    }

    fun setSafeSearchMode(mode: String) = viewModelScope.launch {
        settings.setSafeSearchMode(mode)
    }

    fun startAiScannerWithProjection(projectionIntent: Intent) = viewModelScope.launch {
        settings.setAiScanner(true)
        if (!settings.isShieldActive()) return@launch
        // Delegate to the companion's start() so we get the same
        // foreground-service-promotion + OEM fallback logic.
        AiScannerService.start(getApplication(), projectionIntent)
    }

    fun setUninstallProtection(v: Boolean) = viewModelScope.launch {
        settings.setUninstallProtection(v)
        if (v) {
            GuardianDeviceAdminReceiver.setProtectionEnabled(context, true)
        } else {
            val dpm = context.getSystemService(Application.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(component)) {
                GuardianDeviceAdminReceiver.setProtectionEnabled(context, false)
            }
        }
    }

    fun setStrongProtection(v: Boolean) = viewModelScope.launch {
        settings.setStrongProtection(v)
        if (v) {
            GuardianDeviceAdminReceiver.setProtectionEnabled(context, true)
        }
    }

    fun isDeviceAdminGranted(): Boolean {
        val dpm = context.getSystemService(Application.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(component)
    }

    fun getAdminComponent(): ComponentName {
        return ComponentName(context, GuardianDeviceAdminReceiver::class.java)
    }

    fun isDeviceOwner(): Boolean = DeviceOwnerService.isDeviceOwner(context)
}
