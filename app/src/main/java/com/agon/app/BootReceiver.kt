package com.agon.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.agon.app.data.remote.FirebaseSyncWorker
import com.agon.app.utils.SafeModeBlocker
import com.agon.app.utils.ScheduleEnforcer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {

    // Shared scope for broadcast receivers to avoid creating new ones repeatedly
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        // We handle three triggers:
        //   - BOOT_COMPLETED  : device reboot
        //   - QUICKBOOT_POWERON: some legacy HTC/Huawei ROMs use this
        //     instead of BOOT_COMPLETED.
        //   - MY_PACKAGE_REPLACED : the user just updated the app.
        //     The system wipes all AlarmManager alarms owned by the
        //     previous APK on every install/update, so without
        //     handling this action the next scheduled transition
        //     would never fire (Issue: ScheduleReceiver had no
        //     intent-filter and no BootReceiver reschedule).
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()

        scope.launch {
            try {
                handleBoot(context, intent.action)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleBoot(context: Context, triggerAction: String?) {
        if (isSafeMode(context)) {
            Timber.w("BootReceiver: device booted in safe mode")
            showSafeModeNotification(context)
            recordSafeModeTamper(context)
            // SAFEMODE-FORCED-LOCK: previously the boot path just
            // recorded a tamper event and bailed. The risk was that
            // a user who boots into safe mode to disable protection
            // (bypassing app-blocker / DNS / a11y) gets a free
            // window: a11y and AppBlockerService are both inactive
            // in safe mode, so the user could uninstall, then
            // reboot normally. We now:
            //   1) re-arm the shield state in the data store
            //      (so a normal-mode boot picks it up),
            //   2) call SafeModeBlocker.tryLockDevice() — if the
            //      app is still a registered device admin, the
            //      device locks immediately. This denies the
            //      user the safe-mode console.
            // We do NOT reboot the device — that destroys user
            // data and violates Android best-practices.
            forceShieldOnAfterSafeModeBoot(context)
            SafeModeBlocker.detectAndHandle(context)
            return
        }

        val triggerLabel = when (triggerAction) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> "app updated"
            else -> "device booted"
        }
        Timber.d("BootReceiver: $triggerLabel, checking shield state")

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

        // Audit #11: GuardSoulAccessibilityService is *not* a
        // foreground service — Android only auto-rebinds
        // accessibility services on the next system event, which
        // gives the user a window after boot where Shortstop is
        // inactive. We:
        //   1) check whether the service is in the enabled
        //      services list;
        //   2) if it is, verify the system has actually bound
        //      it (the enabled list ≠ bound list);
        //   3) if enabled but not bound, force a re-evaluation
        //      by writing an empty value then the original back
        //      (the framework debounces same-value writes as a
        //      no-op, so we must change the value);
        //   4) as a last resort, raise a notification with a
        //      deep link to Accessibility settings — opening
        //      that screen triggers the framework to re-evaluate.
        ensureShortstopReconnected(context, app)

        // Re-arm the schedule alarms. ScheduleReceiver is registered
        // with explicit PendingIntents to AlarmManager.setAlarmClock
        // (which survive reboot on most ROMs) BUT they are wiped on:
        //   - factory reset (followed by a BOOT_COMPLETED, but we
        //     have no rule-bound state to recover until we
        //     re-schedule),
        //   - app update (MY_PACKAGE_REPLACED; the system clears all
        //     alarms owned by the previous APK),
        //   - some OEM aggressive battery savers.
        // ScheduleEnforcer.rescheduleAll is idempotent: it cancels the
        // request-code range 1000-1099 then re-arms the next 10
        // upcoming transitions. No-op when the user has no enabled
        // rules.
        try {
            ScheduleEnforcer.rescheduleAll(context, app.repository)
        } catch (e: Exception) {
            Timber.w(e, "BootReceiver: failed to reschedule schedule alarms")
        }

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
     * app, leaving a window of exposure.
     *
     * The previous implementation wrote the same value back
     * to ENABLED_ACCESSIBILITY_SERVICES, which the framework
     * debounces as a no-op. We now do a real value-change
     * write (empty → original) so the framework actually
     * re-evaluates the service list.
     *
     * On Android 13+ (API 33+), writing to
     * ENABLED_ACCESSIBILITY_SERVICES requires the signature
     * permission WRITE_SECURE_SETTINGS. If the app does not
     * have it, the putString throws SecurityException; we
     * catch it and fall back to a notification with a deep
     * link to Accessibility settings, which the user can
     * confirm to trigger the framework to re-evaluate.
     */
    private suspend fun ensureShortstopReconnected(
        context: Context,
        app: GuardianApp,
    ) {
        val expectedComponent = context.packageName + "/" +
            com.agon.app.services.GuardSoulAccessibilityService::class.java.name

        val currentValue = try {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: ""
        } catch (e: Exception) {
            Timber.w(e, "BootReceiver: failed to read ENABLED_ACCESSIBILITY_SERVICES")
            return
        }

        val isEnabled = currentValue.split(':')
            .any { it.equals(expectedComponent, ignoreCase = true) }

        if (!isEnabled) {
            Timber.w("BootReceiver: Shortstop is NOT enabled, notifying user")
            showShortstopDisabledNotification(context)
            return
        }

        val a11yManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as android.view.accessibility.AccessibilityManager
        val isBound = isAccessibilityServiceBound(context, a11yManager)

        if (isBound) {
            Timber.d("BootReceiver: Shortstop is enabled and bound, no action needed")
            return
        }

        Timber.d("BootReceiver: Shortstop is enabled but NOT bound, attempting toggle reconnect")
        val toggled = tryToggleReconnect(context, currentValue)

        if (toggled) {
            // Give the framework a moment to process the value change
            // and bind the service.
            kotlinx.coroutines.delay(3_000L)
            val rebound = isAccessibilityServiceBound(context, a11yManager)
            if (rebound) {
                Timber.d("BootReceiver: Shortstop rebound successfully via toggle")
                return
            }
            Timber.w("BootReceiver: toggle did not result in a re-bind")
        }

        // Last-resort fallback: notify the user with a deep link
        // to Accessibility settings. Opening that screen forces
        // the framework to re-evaluate the service list and bind
        // our service.
        Timber.w("BootReceiver: automatic re-bind failed, asking user to confirm in Settings")
        showShortstopDisabledNotification(context)
    }

    /**
     * Returns true if our accessibility service is in the
     * system's currently-bound (running) list — distinct from
     * the enabled list, since the system may not have bound a
     * freshly-enabled service yet (the post-boot blind window
     * this whole function exists to fix).
     */
    private fun isAccessibilityServiceBound(
        context: Context,
        manager: android.view.accessibility.AccessibilityManager,
    ): Boolean {
        return try {
            manager.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).any { info ->
                android.content.ComponentName.unflattenFromString(info.id)
                    ?.packageName == context.packageName
            }
        } catch (e: Exception) {
            Timber.w(e, "BootReceiver: failed to query bound service list")
            false
        }
    }

    /**
     * Forces the framework to re-evaluate the accessibility
     * service list by writing a different value first, then
     * the original value back.
     *
     * The framework debounces same-value writes as a no-op,
     * so we must actually change the value. Writing "" (empty)
     * then the original triggers a real change and the
     * framework re-binds the service.
     *
     * On Android 13+ this requires WRITE_SECURE_SETTINGS. If
     * the app does not have it, SecurityException is caught
     * here and `false` is returned to the caller.
     */
    private fun tryToggleReconnect(
        context: Context,
        originalValue: String,
    ): Boolean {
        return try {
            // Step 1: write empty value to clear the list
            android.provider.Settings.Secure.putString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                "",
            )
            // Step 2: write the original value back
            android.provider.Settings.Secure.putString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                originalValue,
            )
            true
        } catch (e: SecurityException) {
            Timber.w(e, "BootReceiver: cannot write ENABLED_ACCESSIBILITY_SERVICES — missing WRITE_SECURE_SETTINGS")
            false
        } catch (e: Exception) {
            Timber.w(e, "BootReceiver: toggle reconnect threw unexpected exception")
            false
        }
    }

    private fun showShortstopDisabledNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Deep link: opening Accessibility settings (and dismissing it)
        // triggers the framework to re-evaluate the service list and bind
        // our service. This is the user-driven fallback when the
        // automatic toggle did not work (typically because the app does
        // not hold WRITE_SECURE_SETTINGS on Android 13+).
        val openSettingsIntent = Intent(
            android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val openSettingsPending = android.app.PendingIntent.getActivity(
            context,
            0,
            openSettingsIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, AppNotificationChannels.TAMPER_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.tamper_shortstop_disabled_title))
            .setContentText(context.getString(R.string.tamper_shortstop_disabled_text))
            .setContentIntent(openSettingsPending)
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

    /**
     * SAFEMODE-FORCED-LOCK helper: re-arm the shield in the data
     * store after a safe-mode boot so a subsequent normal-mode
     * boot will start AppBlockerService + a11y + DNS. The state
     * was almost certainly already on (otherwise the user would
     * not have hit our receiver), but a defensive write costs
     * nothing and survives a partial-data-store read.
     */
    private suspend fun forceShieldOnAfterSafeModeBoot(context: Context) {
        val app = context.applicationContext as? GuardianApp ?: return
        try {
            val settings = app.repository.getAppSettings()
            if (!settings.isShieldActive()) {
                settings.setShieldActive(true)
                Timber.w("BootReceiver: shield was off after safe-mode boot, forced on")
            }
        } catch (e: Exception) {
            Timber.w(e, "BootReceiver: forceShieldOnAfterSafeModeBoot failed")
        }
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
            } catch (e: Exception) {
                // Silent DataStore failure is the most common cause
                // (Room migration in progress during boot, or
                // DataStore coroutine cancelled). Surface it to logcat
                // so we don't ship "tamper alerts silently lost"
                // regressions.
                Timber.w(e, "BootReceiver: recordSafeModeTamper failed")
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
}
