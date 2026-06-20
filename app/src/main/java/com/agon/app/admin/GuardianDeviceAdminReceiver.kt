package com.agon.app.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.agon.app.GuardianApp
import com.agon.app.R
import com.agon.app.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Timber.i("Device Admin enabled")
        Toast.makeText(context, R.string.tamper_admin_enabled_toast, Toast.LENGTH_SHORT).show()
        
        // Persist that device admin is active
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as GuardianApp
                app.repository.getAppSettings().setUninstallProtectionEnabled(true)
            } catch (e: Exception) {
                Timber.w(e, "Failed to persist device admin state")
            }
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // This is shown to the user when they try to disable device admin
        // Return a warning message to discourage disabling
        return context.getString(R.string.device_admin_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Timber.w("Device Admin disabled - protection lost")
        Toast.makeText(context, R.string.tamper_admin_disabled_toast, Toast.LENGTH_LONG).show()
        
        // Persist that device admin is disabled
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as GuardianApp
                app.repository.getAppSettings().setUninstallProtectionEnabled(false)
                
                // Broadcast to immediately relaunch the device admin activation screen
                val reactivateIntent = Intent(context, com.agon.app.MainActivity::class.java)
                reactivateIntent.action = "com.agon.app.REACTIVATE_DEVICE_ADMIN"
                reactivateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                reactivateIntent.putExtra("reason", "device_admin_disabled")
                context.startActivity(reactivateIntent)
            } catch (e: Exception) {
                Timber.w(e, "Failed to handle device admin disable")
            }
        }
    }

    override fun onPasswordFailed(context: Context, intent: Intent, userHandle: android.os.UserHandle) {
        super.onPasswordFailed(context, intent, userHandle)
        Timber.w("Failed password attempt detected")
        
        // Log the event to Room for activity history
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as GuardianApp
                app.repository.recordBlock(
                    "system",
                    "Device Admin",
                    "password_failed"
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to log password failure")
            }
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent, userHandle: android.os.UserHandle) {
        super.onPasswordSucceeded(context, intent, userHandle)
        Timber.d("Password succeeded")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Timber.d("DeviceAdminReceiver onReceive: ${intent.action}")
    }
}