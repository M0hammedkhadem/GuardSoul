package com.agon.app.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.agon.app.data.ScheduleRule
import com.agon.app.data.GuardianRepository
import com.agon.app.services.GuardianVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar

class ScheduleManager(private val context: Context) {

    companion object {
        const val ACTION_SCHEDULE_START = "com.agon.app.SCHEDULE_START"
        const val ACTION_SCHEDULE_END = "com.agon.app.SCHEDULE_END"
        const val EXTRA_RULE_ID = "rule_id"
        private const val TAG = "ScheduleManager"
    }

    fun registerAll(rules: List<ScheduleRule>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (rule in rules) {
            if (!rule.enabled) continue
            for (day in rule.daysOfWeek) {
                registerDayAlarm(alarmManager, rule, day)
            }
        }
        Timber.tag(TAG).d("Registered ${rules.size} schedule rules")
    }

    fun unregisterAll() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val startIntent = Intent(context, ScheduleReceiver::class.java).apply { action = ACTION_SCHEDULE_START }
        val endIntent = Intent(context, ScheduleReceiver::class.java).apply { action = ACTION_SCHEDULE_END }
        alarmManager.cancel(PendingIntent.getBroadcast(context, 0, startIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        alarmManager.cancel(PendingIntent.getBroadcast(context, 0, endIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    private fun registerDayAlarm(alarmManager: AlarmManager, rule: ScheduleRule, dayOfWeek: Int) {
        val now = System.currentTimeMillis()

        // Start time
        val startCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek + 1) // Calendar uses 1=Sun, our days 1=Mon
            set(Calendar.HOUR_OF_DAY, rule.startHour)
            set(Calendar.MINUTE, rule.startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMillis = startCal.timeInMillis
        if (startMillis > now) {
            val startIntent = Intent(context, ScheduleReceiver::class.java).apply {
                action = ACTION_SCHEDULE_START
                putExtra(EXTRA_RULE_ID, rule.id)
            }
            val startPending = PendingIntent.getBroadcast(
                context, (rule.id.hashCode() and 0x7fffffff) + dayOfWeek * 10,
                startIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startMillis, startPending)
        }

        // End time (may be next day if endHour < startHour)
        val endCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek + 1)
            set(Calendar.HOUR_OF_DAY, rule.endHour)
            set(Calendar.MINUTE, rule.endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (rule.endHour < rule.startHour || (rule.endHour == rule.startHour && rule.endMinute <= rule.startMinute)) {
            endCal.add(Calendar.DAY_OF_MONTH, 1)
        }
        val endMillis = endCal.timeInMillis
        if (endMillis > now) {
            val endIntent = Intent(context, ScheduleReceiver::class.java).apply {
                action = ACTION_SCHEDULE_END
                putExtra(EXTRA_RULE_ID, rule.id)
            }
            val endPending = PendingIntent.getBroadcast(
                context, (rule.id.hashCode() and 0x7fffffff) + dayOfWeek * 10 + 1,
                endIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMillis, endPending)
        }
    }

    fun isScheduleActive(rules: List<ScheduleRule>): Boolean {
        val now = Calendar.getInstance()
        val currentDay = (now.get(Calendar.DAY_OF_WEEK) + 6) % 7 + 1 // Convert Calendar day to our 1=Mon format
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        return rules.any { rule ->
            if (!rule.enabled || currentDay !in rule.daysOfWeek) return@any false
            val startMinutes = rule.startHour * 60 + rule.startMinute
            val endMinutes = rule.endHour * 60 + rule.endMinute
            if (endMinutes > startMinutes) {
                currentMinutes in startMinutes until endMinutes
            } else {
                // Overnight schedule (e.g., 22:00 - 08:00)
                currentMinutes >= startMinutes || currentMinutes < endMinutes
            }
        }
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == null) return

        Timber.tag("ScheduleReceiver").d("Received: $action")
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = GuardianRepository(context)
                val state = repo.guardianStateFlow.first()
                val manager = ScheduleManager(context)

                when (action) {
                    ScheduleManager.ACTION_SCHEDULE_START -> {
                        if (manager.isScheduleActive(state.scheduleRules) && !state.pornBlockerActive) {
                            val vpnIntent = Intent(context, GuardianVpnService::class.java)
                            context.startService(vpnIntent)
                            Timber.tag("ScheduleReceiver").d("Schedule: VPN started")
                        }
                    }
                    ScheduleManager.ACTION_SCHEDULE_END -> {
                        if (!manager.isScheduleActive(state.scheduleRules) && state.pornBlockerActive && !state.isShieldActive) {
                            val stopIntent = Intent(context, GuardianVpnService::class.java)
                            stopIntent.action = "STOP"
                            context.startService(stopIntent)
                            Timber.tag("ScheduleReceiver").d("Schedule: VPN stopped")
                        }
                    }
                }

                // Re-register for next week
                if (state.scheduleRules.isNotEmpty()) {
                    manager.registerAll(state.scheduleRules)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}