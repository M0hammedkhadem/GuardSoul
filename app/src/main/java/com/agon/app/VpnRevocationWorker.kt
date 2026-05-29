package com.agon.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import timber.log.Timber

class VpnRevocationWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val NOTIFICATION_ID = 5001
        private const val TAG = "VpnRevocationWorker"
        private const val AUTO_RESTART_DELAY_MS = 3000L
        private const val MAX_RESTART_ATTEMPTS = 2
    }

    override suspend fun doWork(): Result {
        Timber.w(TAG, "VPN revocation detected — sending security alert")

        sendSecurityNotification()

        val attempts = inputData.getInt("restart_attempts", 0)
        if (attempts < MAX_RESTART_ATTEMPTS) {
            delay(AUTO_RESTART_DELAY_MS)
            attemptAutoRestart(attempts + 1)
        } else {
            Timber.w(TAG, "Max restart attempts reached, not retrying")
        }

        return Result.success()
    }

    private fun sendSecurityNotification() {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_vpn_settings", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val restartIntent = Intent(context, VpnStateMonitor::class.java).apply {
            action = VpnStateMonitor.ACTION_VPN_RESTART
        }
        val restartPending = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID + 1,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AppNotificationChannels.VPN_SECURITY_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(context.getString(R.string.vpn_alert_title))
            .setContentText(context.getString(R.string.vpn_alert_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.vpn_alert_big_text))
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setColor(0xFFD32F2F.toInt())
            .addAction(
                android.R.drawable.ic_menu_rotate,
                context.getString(R.string.vpn_alert_restart),
                restartPending
            )
            .setContentIntent(pendingIntent)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
        Timber.w(TAG, "Security notification sent (id=$NOTIFICATION_ID)")
    }

    private fun attemptAutoRestart(attemptNumber: Int) {
        if (DnsVpnService.wasStoppedIntentionally()) {
            Timber.d(TAG, "Service was stopped intentionally, skipping restart")
            return
        }

        val repo = (context.applicationContext as GuardianApp).repository
        val shouldRun = try {
            kotlinx.coroutines.runBlocking {
                repo.getAppSettings().isPornBlockerActive()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check porn blocker state")
            false
        }

        if (!shouldRun) {
            Timber.d(TAG, "Porn blocker is disabled, no restart needed")
            return
        }

        Timber.d(TAG, "Auto-restart attempt $attemptNumber/$MAX_RESTART_ATTEMPTS")
        try {
            DnsVpnService.start(context)
        } catch (e: Exception) {
            Timber.e(e, "Auto-restart attempt $attemptNumber failed")
        }
    }
}
