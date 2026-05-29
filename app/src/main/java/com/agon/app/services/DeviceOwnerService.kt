package com.agon.app.services

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import com.agon.app.GuardianDeviceAdminReceiver
import timber.log.Timber

object DeviceOwnerService {

    private const val PREFS_NAME = "device_owner_prefs"
    private const val KEY_SETUP_COMPLETE = "device_owner_setup_complete"

    fun isDeviceOwner(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
            if (!dpm.isAdminActive(component)) return false
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    fun isDeviceOwnerSetupComplete(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SETUP_COMPLETE, false)
    }

    fun markDeviceOwnerSetupComplete(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETUP_COMPLETE, true)
            .apply()
    }

    fun getSetupWizardIntent(context: Context): Intent {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        return Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, component.flattenToString())
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun verifyAndSetup(context: Context) {
        if (isDeviceOwnerSetupComplete(context)) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(component)) {
            Timber.d("DeviceOwnerService: device admin not active")
            return
        }
        if (isDeviceOwner(context)) {
            markDeviceOwnerSetupComplete(context)
            Timber.d("DeviceOwnerService: device owner active")
        }
    }

    fun isAdminUser(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val um = context.getSystemService(Context.USER_SERVICE) as UserManager
                um.isAdminUser()
            } else {
                @Suppress("DEPRECATION")
                val um = context.getSystemService(Context.USER_SERVICE) as UserManager
                um.isAdminUser()
            }
        } catch (_: Exception) {
            true
        }
    }

    fun suspendApps(context: Context, packageNames: List<String>) {
        if (!isDeviceOwner(context)) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        for (pkg in packageNames) {
            try {
                dpm.setApplicationHidden(component, pkg, true)
            } catch (e: Exception) {
                Timber.w(e, "DeviceOwnerService: failed to hide $pkg")
            }
        }
    }

    fun unsuspendApps(context: Context, packageNames: List<String>) {
        if (!isDeviceOwner(context)) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        for (pkg in packageNames) {
            try {
                dpm.setApplicationHidden(component, pkg, false)
            } catch (e: Exception) {
                Timber.w(e, "DeviceOwnerService: failed to unhide $pkg")
            }
        }
    }

    fun lockScreen(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(component)) {
            dpm.lockNow()
        }
    }

    fun getAdminComponent(context: Context): ComponentName {
        return ComponentName(context, GuardianDeviceAdminReceiver::class.java)
    }
}
