package com.agon.app.data.remote

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.agon.app.AppNotificationChannels
import com.agon.app.GuardianApp
import com.agon.app.MainActivity
import com.agon.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class GuardianFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("GuardianFcmService: new FCM token: $token")
        runBlocking {
            try {
                val app = application as GuardianApp
                val repo = app.repository
                val settings = repo.getAppSettings()
                if (settings.isRemoteMonitoringEnabled()) {
                    val firebaseManager = FirebaseManager(this@GuardianFcmService, repo.blockEventDao, repo.appLimitDao)
                    if (firebaseManager.initialize()) {
                        firebaseManager.syncDeviceInfo()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "GuardianFcmService: failed to sync new token")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.d("GuardianFcmService: message received ${message.data}")

        val data = message.data
        val command = data["command"] ?: return
        val app = application as GuardianApp

        when (command) {
            "lock" -> {
                showNotification("Device Locked", "Remote lock command received")
                lockDevice()
            }
            "unlock" -> {
                showNotification("Device Unlocked", "Remote unlock command received")
            }
            "enable_shield" -> {
                runBlocking { app.repository.getAppSettings().setShieldActive(true) }
                showNotification("Shield Enabled", "Remote command: shield activated")
            }
            "disable_shield" -> {
                runBlocking { app.repository.getAppSettings().setShieldActive(false) }
                showNotification("Shield Disabled", "Remote command: shield deactivated")
            }
            "alert" -> {
                val alertMessage = data["message"] ?: "Alert from parent dashboard"
                showNotification("Parent Alert", alertMessage)
            }
            else -> {
                Timber.w("GuardianFcmService: unknown command $command")
            }
        }
    }

    private fun lockDevice() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isDeviceIdleMode) return
            val pm2 = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm2.isInteractive) {
                val activity = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(activity)
            }
        } catch (e: Exception) {
            Timber.e(e, "GuardianFcmService: lockDevice failed")
        }
    }

    private fun showNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, AppNotificationChannels.REMOTE_COMMANDS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(9100, notification)
        } catch (e: Exception) {
            Timber.w(e, "GuardianFcmService: notification failed")
        }
    }
}
