package com.agon.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.agon.app.data.remote.FirebaseSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    
    // Shared scope for broadcast receivers to avoid creating new ones repeatedly
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pendingResult = goAsync()
        
        scope.launch {
            try {
                handleBoot(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleBoot(context: Context) {
        if (isSafeMode(context)) {
            Timber.w("BootReceiver: device booted in safe mode")
            showSafeModeNotification(context)
            recordSafeModeTamper(context)
            // Do NOT reboot the device — that destroys user data and
            // violates Android best-practices. Just record a tamper
            // alert and let the user re-enable protection manually.
            return
        }

        Timber.d("BootReceiver: device booted, checking shield state")

        val app = context.applicationContext as? GuardianApp ?: return
        val shouldStart = try {
            app.repository.getAppSettings().isShieldActive()
        } catch (e: Exception) {
            Timber.w(e, "BootReceiver: failed to read shield state, starting anyway")
            true
        }

        if (!shouldStart) {
            Timber.d("BootReceiver: shield was not active, skipping service restart")
            return
        }

        Timber.d("BootReceiver: shield was active, restarting all services")

        // Start services
        AppBlockerService.start(context)
        // PB-001/006: route through the central controller so the right
        // engine (Device Owner DNS or local VPN) is picked based on context.
        com.agon.app.blocking.PornBlockerController.sync(context)

        // Audit #11: shortstopAccessibilityService is *not* a
        // foreground service — Android only auto-rebinds
        // accessibility services on the next system event, which
        // gives the user a window after boot where Shortstop is
        // inactive. We:
        //   1) check whether the service is in the enabled
        //      services list;
        //   2) if it is, attempt to nudge the system to re-bind
        //      by toggling the value (the user has already granted
        //      permission, so this is allowed);
        //   3) if it isn't, raise a notification telling the user
        //      Shortstop is dormant and they should re-enable it.
        ensureShortstopReconnected(context, app)

        try {
            FirebaseSyncWorker.schedule(context)
        } catch (e: Exception) {
            Timber.w(e, "BootReceiver: failed to schedule Firebase sync")
        }
    }

    /**
     * Audit #11 — re-bind the Shortstop accessibility service
     * after device reboot. Android does not auto-restart
     * accessibility services the way it does foreground
     * services; they only resume when the user opens a target
     * app, leaving a window of exposure. We nudge the system
     * by writing the same value back to the
     * ENABLED_ACCESSIBILITY_SERVICES setting (this is a no-op
     * for already-enabled services but forces the framework to
     * re-evaluate the service list and re-bind).
     */
    private suspend fun ensureShortstopReconnected(
        context: Context,
        app: GuardianApp,
    ) {
        val expectedComponent = context.packageName + "/" +
            com.agon.app.blocking.ShortstopAccessibilityService::class.java.name
        val enabled = try {
            val raw = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: ""
            raw.split(':').any { it.equals(expectedComponent, ignoreCase = true) }
        } catch (e: Exception) {
            Timber.w(e, "BootReceiver: failed to read ENABLED_ACCESSIBILITY_SERVICES")
            null
        }

        when (enabled) {
            true -> {
                Timber.d("BootReceiver: Shortstop is enabled, nudging framework to re-bind")
                try {
                    val current = android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    ) ?: ""
                    android.provider.Settings.Secure.putString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        current,
                    )
                } catch (e: Exception) {
                    Timber.w(e, "BootReceiver: failed to nudge framework")
                }
            }
            false -> {
                Timber.w("BootReceiver: Shortstop is NOT enabled, notifying user")
                showShortstopDisabledNotification(context)
            }
            null -> {
                // Couldn't read the setting — skip silently.
            }
        }
    }

    private fun showShortstopDisabledNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, AppNotificationChannels.TAMPER_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.tamper_shortstop_disabled_title))
            .setContentText(context.getString(R.string.tamper_shortstop_disabled_text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(9004, notification)
    }

    private fun isSafeMode(context: Context): Boolean {
        return try {
            context.packageManager.isSafeMode
        } catch (_: Exception) { false }
    }

    private fun recordSafeModeTamper(context: Context) {
        val app = context.applicationContext as? GuardianApp ?: return
        app.applicationScope.launch {
            try {
                val shieldActive = app.repository.getAppSettings().isShieldActive()
                if (shieldActive) {
                    app.repository.recordTamperAlert(
                        "safe_mode_boot",
                        "Device was booted in safe mode while uninstall protection was on."
                    )
                }
            } catch (_: Exception) {}
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
}
