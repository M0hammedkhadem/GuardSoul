package com.agon.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.agon.app.blocking.PornBlockerController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        val attempts = inputData.getInt("restart_attempts", 0)
        Timber.w(TAG, "VPN revocation detected (Attempt $attempts) — sending security alert")

        sendSecurityNotification()

        if (attempts < MAX_RESTART_ATTEMPTS) {
            delay(AUTO_RESTART_DELAY_MS)
            attemptAutoRestart(attempts + 1)
        } else {
            Timber.w(TAG, "Max restart attempts reached, stopping auto-restart")
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

        // VpnStateMonitor was a dead broadcast receiver — replaced with a
        // direct call to the porn-blocker controller so the notification's
        // "Restart" action takes effect immediately.
        val restartPending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + 1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AppNotificationChannels.VPN_SECURITY_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(context.getString(R.string.vpn_alert_title))
            .setContentText(context.getString(R.string.vpn_alert_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.vpn_alert_big_text)))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setColor(0xFFD32F2F.toInt())
            .addAction(android.R.drawable.ic_menu_rotate, context.getString(R.string.vpn_alert_restart), restartPending)
            .setContentIntent(pendingIntent)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun attemptAutoRestart(nextAttempt: Int) {
        if (PornBlockerService.wasStoppedIntentionally()) return

        val app = context.applicationContext as? GuardianApp ?: return
        val repo = app.repository
        
        try {
            if (repo.getAppSettings().isPornBlockerActive()) {
                Timber.d(TAG, "Attempting auto-restart $nextAttempt")
                PornBlockerService.start(context)
            }
        } catch (e: Exception) {
            Timber.e(e, "Auto-restart failed")
            // Re-schedule via Monitor if direct start fails
            VpnStateMonitor.scheduleRevocationWork(context, nextAttempt)
        }
    }
}
