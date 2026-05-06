package com.agon.app.utils

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import com.agon.app.receivers.GuardianDeviceAdminReceiver
import com.agon.app.services.GuardianAccessibilityService

object PermissionUtils {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, GuardianAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }

    fun isVpnPrepared(context: Context): Boolean {
        // If prepare returns null, the VPN permission is already granted.
        return VpnService.prepare(context) == null
    }

    fun isDeviceAdminEnabled(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(componentName)
    }

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isUsageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
