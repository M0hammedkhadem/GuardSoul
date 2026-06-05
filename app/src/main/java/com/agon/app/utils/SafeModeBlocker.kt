package com.agon.app.utils

import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.core.app.NotificationCompat
import com.agon.app.AppNotificationChannels
import com.agon.app.GuardianDeviceAdminReceiver
import com.agon.app.R
import timber.log.Timber

object SafeModeBlocker {

    fun detectAndHandle(context: Context): Boolean {
        val isSafeMode = try {
            context.packageManager.isSafeMode
        } catch (_: Exception) {
            false
        }

        if (!isSafeMode) return false

        Timber.w("SafeModeBlocker: safe mode detected")

        showSafeModeNotification(context)

        // Issue #130: Replace dangerous root reboot with safe device lock
        return tryLockDevice(context)
    }

    private fun showSafeModeNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, AppNotificationChannels.TAMPER_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.tamper_safemode_title))
            .setContentText(context.getString(R.string.tamper_safemode_text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
        manager.notify(9003, notification)
    }

    private fun tryLockDevice(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                dpm.lockNow()
                true
            } else false
        } catch (e: Exception) {
            Timber.w(e, "SafeModeBlocker: failed to lock device")
            false
        }
    }

    fun shouldBlockSafeModeBoot(context: Context): Boolean {
        // This is a placeholder for checking if safe mode protection is enabled in settings
        return true
    }
}
