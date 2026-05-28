package com.agon.app.utils

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.agon.app.utils.AccessibilityUtils
import com.agon.app.FacebookBlockerService
import com.agon.app.GuardianDeviceAdminReceiver
import com.agon.app.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PermissionUtils {

    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun areAllPermissionsGranted(context: Context): Boolean {
        return isAccessibilityGranted(context) &&
                isVpnGranted(context) &&
                isDeviceAdminGranted(context) &&
                isOverlayGranted(context) &&
                isUsageAccessGranted(context) &&
                isNotificationGranted(context)
    }

    /**
     * Synchronizes the actual system permission status with the internal AppSettings cache.
     */
    fun syncPermissionsWithCache(context: Context, appSettings: AppSettings) {
        syncScope.launch {
            appSettings.setPermAccessibility(isAccessibilityGranted(context))
            appSettings.setPermVpn(isVpnGranted(context))
            appSettings.setPermAdmin(isDeviceAdminGranted(context))
            appSettings.setPermOverlay(isOverlayGranted(context))
            appSettings.setPermUsage(isUsageAccessGranted(context))
            appSettings.setPermNotifications(isNotificationGranted(context))
        }
    }

    fun isAccessibilityGranted(context: Context): Boolean =
        AccessibilityUtils.isServiceEnabled(context, FacebookBlockerService::class.java)

    fun isVpnGranted(context: Context): Boolean {
        return try {
            // On some newer Android versions, we check the app op for VPN binding
            if (Build.VERSION.SDK_INT >= 34) {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = appOps.checkOpNoThrow("android:bind_vpn", Process.myUid(), context.packageName)
                mode == AppOpsManager.MODE_ALLOWED
            } else {
                android.net.VpnService.prepare(context) == null
            }
        } catch (_: Exception) {
            android.net.VpnService.prepare(context) == null
        }
    }

    fun isDeviceAdminGranted(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(component)
    }

    fun isOverlayGranted(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun isUsageAccessGranted(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun isNotificationGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
