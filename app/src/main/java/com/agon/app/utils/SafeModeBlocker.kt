package com.agon.app.utils

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.agon.app.AppNotificationChannels
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

        return tryReboot(context)
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

    private fun tryReboot(context: Context): Boolean {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.reboot(null)
            return true
        } catch (e: Exception) {
            Timber.w(e, "SafeModeBlocker: reboot failed")
        }
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
            return true
        } catch (e: Exception) {
            Timber.w(e, "SafeModeBlocker: root reboot failed")
        }
        return false
    }

    fun shouldBlockSafeModeBoot(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences("guardianship", Context.MODE_PRIVATE)
            prefs.getBoolean("block_safe_mode", false)
        } catch (_: Exception) {
            false
        }
    }
}
