package com.agon.app

import android.app.NotificationManager
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.agon.app.data.settings.AppSettings
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import android.widget.Toast

class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, R.string.device_admin_enabled_toast, Toast.LENGTH_SHORT).show()
        Timber.d("GuardianDeviceAdminReceiver: enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        val app = context.applicationContext as GuardianApp
        val hadPin = runBlocking {
            try { app.repository.getAppSettings().hasPin() } catch (_: Exception) { false }
        }
        if (hadPin) {
            showTamperNotification(context, "device_admin_disabled")
        }
        Toast.makeText(context, R.string.device_admin_disabled_toast, Toast.LENGTH_SHORT).show()
        Timber.w("GuardianDeviceAdminReceiver: disabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val hasPin = runBlocking {
            try {
                val settings = AppSettings(context)
                settings.hasPin()
            } catch (_: Exception) { false }
        }
        return if (hasPin) {
            context.getString(R.string.device_admin_disable_pin_warning)
        } else {
            context.getString(R.string.device_admin_disable_warning)
        }
    }

    private fun showTamperNotification(context: Context, reason: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, AppNotificationChannels.TAMPER_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(context.getString(R.string.tamper_admin_disabled_title))
            .setContentText(context.getString(R.string.tamper_admin_disabled_text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(9005, notification)
    }
}
