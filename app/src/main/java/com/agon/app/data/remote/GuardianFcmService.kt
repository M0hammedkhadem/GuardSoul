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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class GuardianFcmService : FirebaseMessagingService() {

    /**
     * Per-service coroutine scope. Using a dedicated scope (not
     * the [GuardianApp.applicationScope]) means we can cancel
     * in-flight work on [onDestroy] without affecting the rest
     * of the app. The previous implementation used [runBlocking]
     * on `onNewToken` and `onMessageReceived`, which both run
     * on the FCM main thread; `runBlocking` on that thread can
     * deadlock when the DataStore dispatcher tries to re-enter
     * the looper.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("GuardianFcmService: new FCM token: $token")
        scope.launch {
            try {
                val app = application as GuardianApp
                val repo = app.repository
                val settings = repo.getAppSettings()
                if (settings.isRemoteMonitoringEnabled()) {
                    val firebaseManager = FirebaseManager(this@GuardianFcmService, repo.blockEventDao, repo.appLimitDao, settings)
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

        // FCM-VALIDATION: validate the payload BEFORE scheduling
        // any side effects. Unknown command? Drop. Missing
        // required field? Drop. Previous code dispatched on
        // `data["command"] ?: return` but accepted every other
        // key, so a malformed `message` field with thousands of
        // bytes would still be passed to showNotification.
        val command = data["command"] ?: run {
            Timber.w("GuardianFcmService: missing 'command' field, dropping")
            return
        }

        // Validate: only known commands. "lock", "unlock",
        // "enable_shield", "disable_shield", "alert" are the
        // whitelisted actions; anything else is dropped silently
        // (logged as a warning so we can spot typos in the
        // dashboard before they cause silent data loss).
        when (command) {
            "lock" -> {
                showNotification("Device Locked", "Remote lock command received")
                lockDevice()
            }
            "unlock" -> {
                showNotification("Device Unlocked", "Remote unlock command received")
            }
            "enable_shield" -> {
                val app = application as GuardianApp
                scope.launch {
                    try {
                        app.repository.getAppSettings().setShieldActive(true)
                    } catch (e: Exception) {
                        Timber.e(e, "GuardianFcmService: enable_shield failed")
                    }
                }
                showNotification("Shield Enabled", "Remote command: shield activated")
            }
            "disable_shield" -> {
                val app = application as GuardianApp
                scope.launch {
                    try {
                        app.repository.getAppSettings().setShieldActive(false)
                    } catch (e: Exception) {
                        Timber.e(e, "GuardianFcmService: disable_shield failed")
                    }
                }
                showNotification("Shield Disabled", "Remote command: shield deactivated")
            }
            "alert" -> {
                val alertMessage = data["message"] ?: "Alert from parent dashboard"
                // Cap the message length to keep notifications from
                // ballooning. FCM payloads are 4 KB total, so this is
                // mostly defensive.
                val safe = if (alertMessage.length > 256) alertMessage.take(256) + "..." else alertMessage
                showNotification("Parent Alert", safe)
            }
            else -> {
                Timber.w("GuardianFcmService: unknown command '$command', dropping")
            }
        }
    }

    private fun lockDevice() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isDeviceIdleMode) return
            if (pm.isInteractive) {
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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
