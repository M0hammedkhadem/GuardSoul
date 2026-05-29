package com.agon.app.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.agon.app.AppNotificationChannels
import com.agon.app.GuardianApp
import com.agon.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class AdbReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UsbManager.ACTION_USB_STATE) return

        val connected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false)
        if (!connected) return

        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as GuardianApp
            val shieldActive = try {
                app.repository.getAppSettings().isShieldActive()
            } catch (_: Exception) { false }
            if (!shieldActive) return@launch

            val adbEnabled = try {
                Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.ADB_ENABLED
                ) == 1
            } catch (_: Exception) { false }

            if (adbEnabled) {
                Timber.w("AdbReceiver: ADB is enabled while USB connected")
                showAdbWarningNotification(context)
            }
        }
    }

    private fun showAdbWarningNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, AppNotificationChannels.TAMPER_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.tamper_adb_title))
            .setContentText(context.getString(R.string.tamper_adb_text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(9002, notification)
    }
}
