package com.agon.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.agon.app.data.remote.FirebaseSyncWorker
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        if (isSafeMode(context)) {
            Timber.w("BootReceiver: device booted in safe mode, rebooting")
            showSafeModeNotification(context)
            rebootDevice(context)
            return
        }

        Timber.d("BootReceiver: device booted, checking shield state")

        val shouldStart = try {
            runBlocking {
                val app = context.applicationContext as GuardianApp
                app.repository.getAppSettings().isShieldActive()
            }
        } catch (e: Exception) {
            Timber.w(e, "BootReceiver: failed to read shield state, starting anyway")
            true
        }

        if (!shouldStart) {
            Timber.d("BootReceiver: shield was not active, skipping service restart")
            return
        }

        Timber.d("BootReceiver: shield was active, restarting all services")

        AppBlockerService.start(context)
        startServiceSafe(context, Intent(context, DnsVpnService::class.java))
        startServiceSafe(context, Intent(context, AiScannerService::class.java))
        startServiceSafe(context, Intent(context, FacebookBlockerService::class.java))
        startServiceSafe(context, Intent(context, YouTubeBlockerService::class.java))

        try {
            FirebaseSyncWorker.schedule(context)
        } catch (e: Exception) {
            Timber.w(e, "BootReceiver: failed to schedule Firebase sync")
        }
    }

    private fun isSafeMode(context: Context): Boolean {
        return try {
            context.packageManager.isSafeMode
        } catch (_: Exception) { false }
    }

    private fun rebootDevice(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.reboot(null)
        } catch (e: Exception) {
            Timber.w(e, "BootReceiver: reboot failed, trying root")
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
            } catch (e2: Exception) {
                Timber.w(e2, "BootReceiver: root reboot also failed")
            }
        }
    }

    private fun showSafeModeNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, AppNotificationChannels.TAMPER_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.tamper_safemode_title))
            .setContentText(context.getString(R.string.tamper_safemode_text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(9003, notification)
    }

    private fun startServiceSafe(context: Context, intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Timber.w(e, "BootReceiver: failed to start ${intent.component}")
        }
    }
}
