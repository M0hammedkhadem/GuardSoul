package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.guardianApp
import com.agon.app.utils.PermissionUtils
import com.agon.app.utils.ServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermissionsUiState(
    val accessibilityGranted: Boolean = false,
    val vpnPrepared: Boolean = false,
    val deviceAdminActive: Boolean = false,
    val overlayPermission: Boolean = false,
    val usageAccess: Boolean = false,
    val batteryOptimization: Boolean = false,
    val notificationsGranted: Boolean = false,
)

class PermissionsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.guardianApp()!!
    private val ctx = application
    val settings = app.repository.getAppSettings()

    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    init {
        refreshPermissionStates()
    }

    fun refreshPermissionStates() {
        viewModelScope.launch {
            val acc = PermissionUtils.isAccessibilityGranted(ctx)
            val vpn = withContext(Dispatchers.IO) { ServiceManager.isVpnPrepared(ctx) }
            val admin = withContext(Dispatchers.IO) { ServiceManager.isDeviceAdminActive(ctx) }
            val overlay = PermissionUtils.isOverlayGranted(ctx)
            val usage = PermissionUtils.isUsageAccessGranted(ctx)
            val battery = PermissionUtils.isBatteryOptimizationDisabled(ctx)
            val notifications = PermissionUtils.isNotificationsGranted(ctx)

            _uiState.update {
                it.copy(
                    accessibilityGranted = acc,
                    vpnPrepared = vpn,
                    deviceAdminActive = admin,
                    overlayPermission = overlay,
                    usageAccess = usage,
                    batteryOptimization = battery,
                    notificationsGranted = notifications
                )
            }
            PermissionUtils.syncPermissionsWithCache(ctx, settings)
        }
    }
}
