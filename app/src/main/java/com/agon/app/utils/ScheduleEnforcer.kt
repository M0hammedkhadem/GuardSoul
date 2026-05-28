package com.agon.app.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.agon.app.data.repository.AppRepository
import com.agon.app.receivers.ScheduleReceiver
import kotlinx.coroutines.flow.first
import java.util.*

/**
 * ScheduleEnforcer ensures that schedule transitions are triggered exactly on time, 
 * bypassing Doze Mode restrictions using the AlarmClock API.
 */
object ScheduleEnforcer {

    suspend fun rescheduleAll(context: Context, repository: AppRepository) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val transitions = calculateAllTransitions(repository) ?: return
        if (transitions.isEmpty()) return

        // Cancel all existing schedule alarms (request codes 1000-1999)
        for (oldCode in 1000 until 1100) {
            val oldIntent = Intent(context, ScheduleReceiver::class.java)
            val oldPending = PendingIntent.getBroadcast(
                context, oldCode, oldIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (oldPending != null) {
                alarmManager.cancel(oldPending)
                oldPending.cancel()
            }
        }

        // Schedule up to 10 upcoming transitions with unique IDs
        val maxAlarms = minOf(transitions.size, 10)
        for (i in 0 until maxAlarms) {
            val (timeMillis, transitionType) = transitions[i]
            val requestCode = 1000 + i
            val intent = Intent(context, ScheduleReceiver::class.java).apply {
                putExtra("TRANSITION_TIME", timeMillis)
                putExtra("TRANSITION_TYPE", transitionType)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmClockInfo = AlarmManager.AlarmClockInfo(timeMillis, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        }

        AppLogger.d("ScheduleEnforcer: ${maxAlarms} transitions scheduled, next at ${Date(transitions[0].timeMillis)}")
    }

    private data class Transition(val timeMillis: Long, val type: String)

    private suspend fun calculateAllTransitions(repository: AppRepository): List<Transition>? {
        return try {
            val rules = repository.getAllScheduleRules().first().filter { it.enabled }
            if (rules.isEmpty()) return null

            val now = Calendar.getInstance()
            val allTransitions = mutableListOf<Transition>()

            fun mapToAppDay(calDay: Int): Int = when(calDay) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }

            for (dayOffset in 0..7) {
                val checkDay = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
                val appDay = mapToAppDay(checkDay.get(Calendar.DAY_OF_WEEK))
                
                for (rule in rules) {
                    val ruleDays = rule.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
                    if (appDay !in ruleDays) continue

                    listOf(
                        rule.startHour * 60 + rule.startMinute to "start",
                        rule.endHour * 60 + rule.endMinute to "end"
                    ).forEach { (mins, type) ->
                        val transitionTime = (checkDay.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, mins / 60)
                            set(Calendar.MINUTE, mins % 60)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis

                        if (transitionTime > System.currentTimeMillis()) {
                            allTransitions.add(Transition(transitionTime, type))
                        }
                    }
                }
            }

            allTransitions.sortedBy { it.timeMillis }.distinctBy { it.timeMillis }.take(20)
        } catch (e: Exception) {
            AppLogger.e(e, "ScheduleEnforcer: Error calculating transitions")
            null
        }
    }
}
