package com.agon.app.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.GuardianApp
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
    val grantAllProgress: Int = 0,
    val grantAllTotal: Int = 0,
    val isGrantingAll: Boolean = false,
    val currentGrantingPermission: String? = null,
)

class PermissionsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GuardianApp
    private val ctx = application
    val settings = app.repository.getAppSettings()

    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    fun refreshPermissionStates() {
        viewModelScope.launch {
            val acc = PermissionUtils.isAccessibilityGranted(ctx)
            val vpn = PermissionUtils.isVpnGranted(ctx)
            val admin = PermissionUtils.isDeviceAdminGranted(ctx)
            val overlay = PermissionUtils.isOverlayGranted(ctx)
            val usage = PermissionUtils.isUsageAccessGranted(ctx)
            val notif = PermissionUtils.isNotificationGranted(ctx)

            _uiState.update {
                it.copy(
                    accessibilityGranted = acc,
                    vpnGranted = vpn,
                    deviceAdminGranted = admin,
                    overlayGranted = overlay,
                    usageAccessGranted = usage,
                    notificationGranted = notif,
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
        else -> false
    }

    fun startGrantAll() {
        val s = _uiState.value
        val ungranted = mutableListOf<String>().apply {
            if (!s.overlayGranted) add("overlay")
            if (!s.usageAccessGranted) add("usage_access")
            if (!s.notificationGranted) add("notifications")
            if (!s.accessibilityGranted) add("accessibility")
            if (!s.vpnGranted) add("vpn")
            if (!s.deviceAdminGranted) add("device_admin")
        }
        if (ungranted.isEmpty()) return

        _uiState.update {
            it.copy(
                isGrantingAll = true,
                grantAllProgress = 0,
                grantAllTotal = ungranted.size,
                currentGrantingPermission = ungranted.first()
            )
        }
    }

    fun advanceGrantAll() {
        val s = _uiState.value
        val current = s.currentGrantingPermission ?: run { finishGrantAll(); return }

        if (isPermissionGranted(current)) {
            val nextIndex = s.grantAllProgress + 1
            if (nextIndex >= s.grantAllTotal) {
                _uiState.update {
                    it.copy(
                        grantAllProgress = nextIndex,
                        isGrantingAll = false,
                        currentGrantingPermission = null,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        grantAllProgress = nextIndex,
                        currentGrantingPermission = getUngrantedAtIndex(nextIndex),
                    )
                }
            }
        } else {
            val nextIndex = s.grantAllProgress + 1
            if (nextIndex >= s.grantAllTotal) {
                _uiState.update {
                    it.copy(
                        grantAllProgress = nextIndex,
                        isGrantingAll = false,
                        currentGrantingPermission = null,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        grantAllProgress = nextIndex,
                        currentGrantingPermission = getUngrantedAtIndex(nextIndex),
                    )
                }
            }
        }
    }

    private fun getUngrantedAtIndex(index: Int): String? {
        val s = _uiState.value
        val ungranted = mutableListOf<String>().apply {
            if (!s.overlayGranted) add("overlay")
            if (!s.usageAccessGranted) add("usage_access")
            if (!s.notificationGranted) add("notifications")
            if (!s.accessibilityGranted) add("accessibility")
            if (!s.vpnGranted) add("vpn")
            if (!s.deviceAdminGranted) add("device_admin")
        }
        return ungranted.getOrNull(index)
    }

    private fun finishGrantAll() {
        _uiState.update {
            it.copy(isGrantingAll = false, currentGrantingPermission = null)
        }
    }

    fun getSettingsIntent(permission: String): Intent? = when (permission) {
        "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${ctx.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        "usage_access" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        "notifications" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else null
        }
        else -> null
    }
}
