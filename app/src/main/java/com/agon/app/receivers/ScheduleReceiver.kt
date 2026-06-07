package com.agon.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.agon.app.guardianApp
import com.agon.app.GuardianApp
import com.agon.app.utils.AppLogger
import com.agon.app.utils.ScheduleEnforcer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val transitionTime = intent.getLongExtra("TRANSITION_TIME", 0L)
        val transitionType = intent.getStringExtra("TRANSITION_TYPE") ?: "unknown"
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED ->
                AppLogger.i("ScheduleReceiver: self-rescuing after app update (alarms were wiped by the system)")
            else ->
                AppLogger.i("ScheduleReceiver: Schedule transition ($transitionType) at $transitionTime")
        }

        val app = context.guardianApp()
        if (app == null) {
            // Critical: if we early-return after goAsync() without
            // finish(), the BroadcastReceiver is held for ~10 s
            // before the system gives up. Always finish before any
            // unconditional return.
            pendingResult.finish()
            return
        }
        val repository = app.repository
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                ScheduleEnforcer.rescheduleAll(context, repository)

                // Reload blocklist without stopping the service
                try {
                    val settings = repository.getAppSettings()
                    if (settings.isShieldActive()) {
                        com.agon.app.AppBlockerService.reloadBlocklist(context)
                    }
                } catch (e: Exception) {
                    AppLogger.w(e, "ScheduleReceiver: failed to reload AppBlockerService blocklist")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
