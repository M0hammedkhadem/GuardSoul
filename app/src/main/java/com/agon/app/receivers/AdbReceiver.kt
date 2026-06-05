package com.agon.app.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.agon.app.AppNotificationChannels
import com.agon.app.guardianApp
import com.agon.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class AdbReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.hardware.usb.action.USB_STATE") return

        val connected = intent.getBooleanExtra("connected", false)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (!connected) {
            // Issue #281: Remove notification when USB is disconnected
            manager.cancel(9002)
            return
        }

        val app = context.guardianApp() ?: return
        val pendingResult = goAsync()
        
        // Issue #169: Use applicationScope to prevent leak
        app.applicationScope.launch(Dispatchers.IO) {
            try {
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
                    showAdbWarningNotification(context, manager)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showAdbWarningNotification(context: Context, manager: NotificationManager) {
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
