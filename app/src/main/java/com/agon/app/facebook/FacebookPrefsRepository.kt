package com.agon.app.facebook

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.fbDataStore by preferencesDataStore(name = "facebook_blocker_prefs")

class FacebookPrefsRepository(private val context: Context) {
    private val BLOCKER_ENABLED = booleanPreferencesKey("fb_blocker_enabled")
    private val CONFIDENCE_THRESHOLD = intPreferencesKey("fb_confidence_threshold")
    private val SCHEDULE_ENABLED = booleanPreferencesKey("fb_schedule_enabled")
    private val SCHEDULE_START_HOUR = intPreferencesKey("fb_schedule_start_hour")
    private val SCHEDULE_END_HOUR = intPreferencesKey("fb_schedule_end_hour")
    private val FRIEND_PROTECTION = booleanPreferencesKey("fb_friend_protection")
    private val DAILY_BLOCKED_COUNT = intPreferencesKey("fb_daily_blocked_count")
    private val LAST_RESET_DATE = stringPreferencesKey("fb_last_reset_date")
    private val TIME_SAVED_MINUTES = intPreferencesKey("fb_time_saved_minutes")

    val settingsFlow: Flow<FacebookSettings> = context.fbDataStore.data.map { prefs ->
        FacebookSettings(
            blockerEnabled = prefs[BLOCKER_ENABLED] ?: true,
            confidenceThreshold = prefs[CONFIDENCE_THRESHOLD] ?: 85,
            scheduleEnabled = prefs[SCHEDULE_ENABLED] ?: false,
            scheduleStartHour = prefs[SCHEDULE_START_HOUR] ?: 9,
            scheduleEndHour = prefs[SCHEDULE_END_HOUR] ?: 17,
            friendProtection = prefs[FRIEND_PROTECTION] ?: true,
            dailyBlockedCount = prefs[DAILY_BLOCKED_COUNT] ?: 0,
            lastResetDate = prefs[LAST_RESET_DATE] ?: "",
            timeSavedMinutes = prefs[TIME_SAVED_MINUTES] ?: 0
        )
    }

    suspend fun updateBlockerEnabled(enabled: Boolean) {
        context.fbDataStore.edit { it[BLOCKER_ENABLED] = enabled }
    }

    suspend fun updateConfidenceThreshold(threshold: Int) {
        context.fbDataStore.edit { it[CONFIDENCE_THRESHOLD] = threshold.coerceIn(70, 95) }
    }

    suspend fun updateScheduleEnabled(enabled: Boolean) {
        context.fbDataStore.edit { it[SCHEDULE_ENABLED] = enabled }
    }

    suspend fun updateScheduleStartHour(hour: Int) {
        context.fbDataStore.edit { it[SCHEDULE_START_HOUR] = hour.coerceIn(0, 23) }
    }

    suspend fun updateScheduleEndHour(hour: Int) {
        context.fbDataStore.edit { it[SCHEDULE_END_HOUR] = hour.coerceIn(0, 23) }
    }

    suspend fun updateFriendProtection(enabled: Boolean) {
        context.fbDataStore.edit { it[FRIEND_PROTECTION] = enabled }
    }

    suspend fun updateDailyBlockedCount(count: Int) {
        context.fbDataStore.edit { it[DAILY_BLOCKED_COUNT] = count }
    }

    suspend fun updateTimeSavedMinutes(minutes: Int) {
        context.fbDataStore.edit { it[TIME_SAVED_MINUTES] = minutes }
    }

    suspend fun incrementBlockedCount() {
        val today = java.time.LocalDate.now().toString()
        context.fbDataStore.edit { prefs ->
            val saved = prefs[LAST_RESET_DATE] ?: ""
            val current = if (saved != today) 0 else (prefs[DAILY_BLOCKED_COUNT] ?: 0)
            prefs[DAILY_BLOCKED_COUNT] = current + 1
            prefs[LAST_RESET_DATE] = today
        }
    }

    suspend fun resetDailyStats() {
        context.fbDataStore.edit {
            it[DAILY_BLOCKED_COUNT] = 0
            it[TIME_SAVED_MINUTES] = 0
            it[LAST_RESET_DATE] = java.time.LocalDate.now().toString()
        }
    }
}

data class FacebookSettings(
    val blockerEnabled: Boolean = true,
    val confidenceThreshold: Int = 85,
    val scheduleEnabled: Boolean = false,
    val scheduleStartHour: Int = 9,
    val scheduleEndHour: Int = 17,
    val friendProtection: Boolean = true,
    val dailyBlockedCount: Int = 0,
    val lastResetDate: String = "",
    val timeSavedMinutes: Int = 0
)
