package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.guardianApp
import com.agon.app.utils.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionsUiState(
    val accessibilityGranted: Boolean = false,
    val vpnGranted: Boolean = false,
    val deviceAdminGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val notificationGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val writeSettingsGranted: Boolean = false,
    val exactAlarmGranted: Boolean = false,
    val grantAllProgress: Int = 0,
    val grantAllTotal: Int = 0,
    val isGrantingAll: Boolean = false,
    val currentGrantingPermission: String? = null,
)

class PermissionsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.guardianApp()!!
    private val ctx = application
    val settings = app.repository.getAppSettings()

    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    // Cache the list of permissions to grant when starting "Grant All"
    private var permissionsToGrant = listOf<String>()

    fun refreshPermissionStates() {
        viewModelScope.launch {
            val acc = PermissionUtils.isAccessibilityGranted(ctx)
            val vpn = PermissionUtils.isVpnGranted(ctx)
            val admin = PermissionUtils.isDeviceAdminGranted(ctx)
            val overlay = PermissionUtils.isOverlayGranted(ctx)
            val usage = PermissionUtils.isUsageAccessGranted(ctx)
            val notif = PermissionUtils.isNotificationGranted(ctx)
            val battery = PermissionUtils.isBatteryOptimizationIgnored(ctx)
            val write = PermissionUtils.isWriteSettingsGranted(ctx)
            val alarm = PermissionUtils.isExactAlarmGranted(ctx)

            _uiState.update {
                it.copy(
                    accessibilityGranted = acc,
                    vpnGranted = vpn,
                    deviceAdminGranted = admin,
                    overlayGranted = overlay,
                    usageAccessGranted = usage,
                    notificationGranted = notif,
                    batteryOptimizationIgnored = battery,
                    writeSettingsGranted = write,
                    exactAlarmGranted = alarm
                )
            }

            PermissionUtils.syncPermissionsWithCache(ctx, settings)
        }
    }

    fun isPermissionGranted(key: String): Boolean = when (key) {
        "accessibility" -> _uiState.value.accessibilityGranted
        "vpn" -> _uiState.value.vpnGranted
        "device_admin" -> _uiState.value.deviceAdminGranted
        "overlay" -> _uiState.value.overlayGranted
        "usage_access" -> _uiState.value.usageAccessGranted
        "notifications" -> _uiState.value.notificationGranted
        "battery" -> _uiState.value.batteryOptimizationIgnored
        "write_settings" -> _uiState.value.writeSettingsGranted
        "exact_alarm" -> _uiState.value.exactAlarmGranted
        else -> false
    }

    fun startGrantAll() {
        permissionsToGrant = getUngrantedList()
        if (permissionsToGrant.isEmpty()) return

        _uiState.update {
            it.copy(
                isGrantingAll = true,
                grantAllProgress = 0,
                grantAllTotal = permissionsToGrant.size,
                currentGrantingPermission = permissionsToGrant.first()
            )
        }
    }

    // Issue #136: Fixed logic to advance through the cached list of ungranted permissions
    fun advanceGrantAll() {
        val s = _uiState.value
        if (!s.isGrantingAll) return
        
        val current = s.currentGrantingPermission ?: run { finishGrantAll(); return }

        if (isPermissionGranted(current)) {
            val nextIndex = s.grantAllProgress + 1
            if (nextIndex >= s.grantAllTotal) {
                finishGrantAll()
            } else {
                _uiState.update {
                    it.copy(
                        grantAllProgress = nextIndex,
                        currentGrantingPermission = permissionsToGrant.getOrNull(nextIndex),
                    )
                }
            }
        }
    }

    private fun getUngrantedList(): List<String> {
        val s = _uiState.value
        return mutableListOf<String>().apply {
            if (!s.notificationGranted) add("notifications")
            if (!s.overlayGranted) add("overlay")
            if (!s.usageAccessGranted) add("usage_access")
            if (!s.batteryOptimizationIgnored) add("battery")
            if (!s.writeSettingsGranted) add("write_settings")
            if (!s.exactAlarmGranted) add("exact_alarm")
            if (!s.accessibilityGranted) add("accessibility")
            if (!s.vpnGranted) add("vpn")
            if (!s.deviceAdminGranted) add("device_admin")
        }
    }

    private fun finishGrantAll() {
        _uiState.update {
            it.copy(isGrantingAll = false, currentGrantingPermission = null, grantAllProgress = it.grantAllTotal)
        }
        permissionsToGrant = emptyList()
    }
}
