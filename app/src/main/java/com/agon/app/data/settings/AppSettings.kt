package com.agon.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "app_settings")

class AppSettings(private val context: Context) {
    private val encryptedPrefs = EncryptedPrefs(context)

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val SHIELD_ACTIVE = booleanPreferencesKey("shield_active")
        val TRIAL_MODE = booleanPreferencesKey("trial_mode")
        val DEACTIVATION_DELAY_MINUTES = intPreferencesKey("deactivation_delay_minutes")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val STREAK_COUNT = intPreferencesKey("streak_count")
        val LONGEST_STREAK = intPreferencesKey("longest_streak")
        val LAST_ACTIVE_DATE = longPreferencesKey("last_active_date")

        val SOCIAL_INSTAGRAM = booleanPreferencesKey("social_instagram")
        val SOCIAL_SNAPCHAT = booleanPreferencesKey("social_snapchat")
        val SOCIAL_TWITTER = booleanPreferencesKey("social_twitter")
        val SOCIAL_TIKTOK = booleanPreferencesKey("social_tiktok")
        val YOUTUBE_MODE = stringPreferencesKey("youtube_mode")
        val FACEBOOK_MODE = stringPreferencesKey("facebook_mode")
        val SHORTS_BLOCK_ACTION = stringPreferencesKey("shorts_block_action")

        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val PORN_BLOCKER = booleanPreferencesKey("porn_blocker")
        val AI_SCANNER = booleanPreferencesKey("ai_scanner")
        val AI_SENSITIVITY = intPreferencesKey("ai_sensitivity")
        val UNINSTALL_PROTECTION = booleanPreferencesKey("uninstall_protection")
        val STRONG_PROTECTION = booleanPreferencesKey("strong_protection")
        val NEXTDNS_PROFILE_ID = stringPreferencesKey("nextdns_profile_id")
        val AI_OVERLAY_MODE = booleanPreferencesKey("ai_overlay_mode")

        // Gamification Keys
        val XP_POINTS = intPreferencesKey("xp_points")
        val LEVEL = intPreferencesKey("level")
        val TOTAL_BLOCKS_LIFETIME = intPreferencesKey("total_blocks_lifetime")

        // School Time & Bedtime Keys
        val SCHOOL_TIME_ENABLED = booleanPreferencesKey("school_time_enabled")
        val SCHOOL_TIME_START_HOUR = intPreferencesKey("school_time_start_hour")
        val SCHOOL_TIME_START_MINUTE = intPreferencesKey("school_time_start_minute")
        val SCHOOL_TIME_END_HOUR = intPreferencesKey("school_time_end_hour")
        val SCHOOL_TIME_END_MINUTE = intPreferencesKey("school_time_end_minute")
        val SCHOOL_TIME_DAYS = stringPreferencesKey("school_time_days")
        val BEDTIME_MODE_ENABLED = booleanPreferencesKey("bedtime_mode_enabled")
        val BEDTIME_START_HOUR = intPreferencesKey("bedtime_start_hour")
        val BEDTIME_START_MINUTE = intPreferencesKey("bedtime_start_minute")
        val BEDTIME_END_HOUR = intPreferencesKey("bedtime_end_hour")
        val BEDTIME_END_MINUTE = intPreferencesKey("bedtime_end_minute")
        val AUTO_LOCK_ON_LIMIT = booleanPreferencesKey("auto_lock_on_limit")

        // Remote Monitoring Keys
        val PARENT_EMAIL = stringPreferencesKey("parent_email")
        val CHILD_DEVICE_ID = stringPreferencesKey("child_device_id")
        val REMOTE_MONITORING_ENABLED = booleanPreferencesKey("remote_monitoring_enabled")

        // Permission Cache Keys
        val PERM_ACCESSIBILITY = booleanPreferencesKey("perm_accessibility")
        val PERM_VPN = booleanPreferencesKey("perm_vpn")
        val PERM_ADMIN = booleanPreferencesKey("perm_admin")
        val PERM_OVERLAY = booleanPreferencesKey("perm_overlay")
        val PERM_USAGE = booleanPreferencesKey("perm_usage")
        val PERM_NOTIFICATIONS = booleanPreferencesKey("perm_notifications")
    }

    val onboardingCompleteFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }
    val shieldActiveFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SHIELD_ACTIVE] ?: false }
    val trialModeFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.TRIAL_MODE] ?: false }
    val deactivationDelayFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.DEACTIVATION_DELAY_MINUTES] ?: 0 }
    val profileNameFlow: Flow<String> = context.settingsStore.data.map { it[Keys.PROFILE_NAME] ?: "" }
    val pinHashFlow: Flow<String> = encryptedPrefs.pinHashFlow
    val streakCountFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.STREAK_COUNT] ?: 0 }
    val longestStreakFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.LONGEST_STREAK] ?: 0 }
    val lastActiveDateFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.LAST_ACTIVE_DATE] ?: 0L }

    val socialInstagramFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SOCIAL_INSTAGRAM] ?: false }
    val socialSnapchatFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SOCIAL_SNAPCHAT] ?: false }
    val socialTwitterFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SOCIAL_TWITTER] ?: false }
    val socialTiktokFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SOCIAL_TIKTOK] ?: false }
    val youtubeModeFlow: Flow<String> = context.settingsStore.data.map { it[Keys.YOUTUBE_MODE] ?: "off" }
    val facebookModeFlow: Flow<String> = context.settingsStore.data.map { it[Keys.FACEBOOK_MODE] ?: "off" }
    val shortsBlockActionFlow: Flow<String> = context.settingsStore.data.map { it[Keys.SHORTS_BLOCK_ACTION] ?: "redirect" }

    val strictModeFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.STRICT_MODE] ?: false }
    val pornBlockerFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PORN_BLOCKER] ?: false }
    val aiScannerFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.AI_SCANNER] ?: false }
    val aiSensitivityFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.AI_SENSITIVITY] ?: 75 }
    val uninstallProtectionFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.UNINSTALL_PROTECTION] ?: false }
    val strongProtectionFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.STRONG_PROTECTION] ?: false }
    val nextDnsProfileIdFlow: Flow<String> = context.settingsStore.data.map { it[Keys.NEXTDNS_PROFILE_ID] ?: "" }
    val aiOverlayModeFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.AI_OVERLAY_MODE] ?: false }

    val xpPointsFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.XP_POINTS] ?: 0 }
    val levelFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.LEVEL] ?: 1 }
    val totalBlocksLifetimeFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.TOTAL_BLOCKS_LIFETIME] ?: 0 }

    val schoolTimeEnabledFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SCHOOL_TIME_ENABLED] ?: false }
    val schoolTimeStartHourFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.SCHOOL_TIME_START_HOUR] ?: 8 }
    val schoolTimeStartMinuteFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.SCHOOL_TIME_START_MINUTE] ?: 0 }
    val schoolTimeEndHourFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.SCHOOL_TIME_END_HOUR] ?: 15 }
    val schoolTimeEndMinuteFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.SCHOOL_TIME_END_MINUTE] ?: 0 }
    val schoolTimeDaysFlow: Flow<String> = context.settingsStore.data.map { it[Keys.SCHOOL_TIME_DAYS] ?: "1,2,3,4,5" }
    val bedtimeModeEnabledFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.BEDTIME_MODE_ENABLED] ?: false }
    val bedtimeStartHourFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.BEDTIME_START_HOUR] ?: 22 }
    val bedtimeStartMinuteFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.BEDTIME_START_MINUTE] ?: 0 }
    val bedtimeEndHourFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.BEDTIME_END_HOUR] ?: 7 }
    val bedtimeEndMinuteFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.BEDTIME_END_MINUTE] ?: 0 }
    val autoLockOnLimitFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.AUTO_LOCK_ON_LIMIT] ?: false }

    // Remote Monitoring Flows
    val parentEmailFlow: Flow<String> = context.settingsStore.data.map { it[Keys.PARENT_EMAIL] ?: "" }
    val childDeviceIdFlow: Flow<String> = context.settingsStore.data.map { it[Keys.CHILD_DEVICE_ID] ?: "" }
    val remoteMonitoringEnabledFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.REMOTE_MONITORING_ENABLED] ?: false }

    // Permission Flows
    val permAccessibilityFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PERM_ACCESSIBILITY] ?: false }
    val permVpnFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PERM_VPN] ?: false }
    val permAdminFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PERM_ADMIN] ?: false }
    val permOverlayFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PERM_OVERLAY] ?: false }
    val permUsageFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PERM_USAGE] ?: false }
    val permNotificationsFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PERM_NOTIFICATIONS] ?: false }

    suspend fun setOnboardingComplete() { context.settingsStore.edit { it[Keys.ONBOARDING_COMPLETE] = true } }
    suspend fun setShieldActive(v: Boolean) { context.settingsStore.edit { it[Keys.SHIELD_ACTIVE] = v } }
    suspend fun setTrialMode(v: Boolean) { context.settingsStore.edit { it[Keys.TRIAL_MODE] = v } }
    suspend fun setDeactivationDelay(v: Int) { context.settingsStore.edit { it[Keys.DEACTIVATION_DELAY_MINUTES] = v } }
    suspend fun setProfileName(v: String) { context.settingsStore.edit { it[Keys.PROFILE_NAME] = v } }
    suspend fun setPinHash(v: String) { encryptedPrefs.savePinHash(v) }
    suspend fun setStreakCount(v: Int) { context.settingsStore.edit { it[Keys.STREAK_COUNT] = v } }
    suspend fun setLongestStreak(v: Int) { context.settingsStore.edit { it[Keys.LONGEST_STREAK] = v } }
    suspend fun setLastActiveDate(v: Long) { context.settingsStore.edit { it[Keys.LAST_ACTIVE_DATE] = v } }

    suspend fun setSocialInstagram(v: Boolean) { context.settingsStore.edit { it[Keys.SOCIAL_INSTAGRAM] = v } }
    suspend fun setSocialSnapchat(v: Boolean) { context.settingsStore.edit { it[Keys.SOCIAL_SNAPCHAT] = v } }
    suspend fun setSocialTwitter(v: Boolean) { context.settingsStore.edit { it[Keys.SOCIAL_TWITTER] = v } }
    suspend fun setSocialTiktok(v: Boolean) { context.settingsStore.edit { it[Keys.SOCIAL_TIKTOK] = v } }
    suspend fun setYoutubeMode(v: String) { context.settingsStore.edit { it[Keys.YOUTUBE_MODE] = v } }
    suspend fun setFacebookMode(v: String) { context.settingsStore.edit { it[Keys.FACEBOOK_MODE] = v } }
    suspend fun setShortsBlockAction(v: String) { context.settingsStore.edit { it[Keys.SHORTS_BLOCK_ACTION] = v } }
    suspend fun getShortsBlockAction(): String = context.settingsStore.data.first()[Keys.SHORTS_BLOCK_ACTION] ?: "redirect"

    suspend fun setStrictMode(v: Boolean) { context.settingsStore.edit { it[Keys.STRICT_MODE] = v } }
    suspend fun setPornBlocker(v: Boolean) { context.settingsStore.edit { it[Keys.PORN_BLOCKER] = v } }
    suspend fun setAiScanner(v: Boolean) { context.settingsStore.edit { it[Keys.AI_SCANNER] = v } }
    suspend fun setAiSensitivity(v: Int) { context.settingsStore.edit { it[Keys.AI_SENSITIVITY] = v } }
    suspend fun setUninstallProtection(v: Boolean) { context.settingsStore.edit { it[Keys.UNINSTALL_PROTECTION] = v } }
    suspend fun setStrongProtection(v: Boolean) { context.settingsStore.edit { it[Keys.STRONG_PROTECTION] = v } }
    suspend fun setNextDnsProfileId(v: String) { context.settingsStore.edit { it[Keys.NEXTDNS_PROFILE_ID] = v } }
    suspend fun getNextDnsProfileId(): String = context.settingsStore.data.first()[Keys.NEXTDNS_PROFILE_ID] ?: ""
    suspend fun setAiOverlayMode(v: Boolean) { context.settingsStore.edit { it[Keys.AI_OVERLAY_MODE] = v } }

    suspend fun setXpPoints(v: Int) { context.settingsStore.edit { it[Keys.XP_POINTS] = v } }
    suspend fun setLevel(v: Int) { context.settingsStore.edit { it[Keys.LEVEL] = v } }
    suspend fun setTotalBlocksLifetime(v: Int) { context.settingsStore.edit { it[Keys.TOTAL_BLOCKS_LIFETIME] = v } }

    suspend fun setSchoolTimeEnabled(v: Boolean) { context.settingsStore.edit { it[Keys.SCHOOL_TIME_ENABLED] = v } }
    suspend fun setSchoolTimeStart(hour: Int, minute: Int) { context.settingsStore.edit { it[Keys.SCHOOL_TIME_START_HOUR] = hour; it[Keys.SCHOOL_TIME_START_MINUTE] = minute } }
    suspend fun setSchoolTimeEnd(hour: Int, minute: Int) { context.settingsStore.edit { it[Keys.SCHOOL_TIME_END_HOUR] = hour; it[Keys.SCHOOL_TIME_END_MINUTE] = minute } }
    suspend fun setSchoolTimeDays(days: String) { context.settingsStore.edit { it[Keys.SCHOOL_TIME_DAYS] = days } }
    suspend fun setBedtimeModeEnabled(v: Boolean) { context.settingsStore.edit { it[Keys.BEDTIME_MODE_ENABLED] = v } }
    suspend fun setBedtimeStart(hour: Int, minute: Int) { context.settingsStore.edit { it[Keys.BEDTIME_START_HOUR] = hour; it[Keys.BEDTIME_START_MINUTE] = minute } }
    suspend fun setBedtimeEnd(hour: Int, minute: Int) { context.settingsStore.edit { it[Keys.BEDTIME_END_HOUR] = hour; it[Keys.BEDTIME_END_MINUTE] = minute } }
    suspend fun setAutoLockOnLimit(v: Boolean) { context.settingsStore.edit { it[Keys.AUTO_LOCK_ON_LIMIT] = v } }

    // Remote Monitoring Setters
    suspend fun setParentEmail(v: String) { context.settingsStore.edit { it[Keys.PARENT_EMAIL] = v } }
    suspend fun setChildDeviceId(v: String) { context.settingsStore.edit { it[Keys.CHILD_DEVICE_ID] = v } }
    suspend fun setRemoteMonitoringEnabled(v: Boolean) { context.settingsStore.edit { it[Keys.REMOTE_MONITORING_ENABLED] = v } }

    // Getters
    suspend fun getParentEmail(): String = context.settingsStore.data.first()[Keys.PARENT_EMAIL] ?: ""
    suspend fun getChildDeviceId(): String = context.settingsStore.data.first()[Keys.CHILD_DEVICE_ID] ?: ""
    suspend fun isRemoteMonitoringEnabled(): Boolean = context.settingsStore.data.first()[Keys.REMOTE_MONITORING_ENABLED] ?: false

    suspend fun isSchoolTimeActive(): Boolean {
        if (!schoolTimeEnabledFlow.first()) return false
        val now = java.util.Calendar.getInstance()
        val dayOfWeek = now.get(java.util.Calendar.DAY_OF_WEEK)
        val mappedDay = when (dayOfWeek) {
            java.util.Calendar.MONDAY -> 1; java.util.Calendar.TUESDAY -> 2
            java.util.Calendar.WEDNESDAY -> 3; java.util.Calendar.THURSDAY -> 4
            java.util.Calendar.FRIDAY -> 5; java.util.Calendar.SATURDAY -> 6
            java.util.Calendar.SUNDAY -> 7; else -> 1
        }
        val days = schoolTimeDaysFlow.first().split(",").mapNotNull { it.trim().toIntOrNull() }
        if (mappedDay !in days) return false
        val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val startMinutes = schoolTimeStartHourFlow.first() * 60 + schoolTimeStartMinuteFlow.first()
        val endMinutes = schoolTimeEndHourFlow.first() * 60 + schoolTimeEndMinuteFlow.first()
        return currentMinutes in startMinutes..endMinutes
    }

    suspend fun isBedtimeActive(): Boolean {
        if (!bedtimeModeEnabledFlow.first()) return false
        val now = java.util.Calendar.getInstance()
        val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val startMinutes = bedtimeStartHourFlow.first() * 60 + bedtimeStartMinuteFlow.first()
        val endMinutes = bedtimeEndHourFlow.first() * 60 + bedtimeEndMinuteFlow.first()
        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    // Permission Setters
    suspend fun setPermAccessibility(v: Boolean) { context.settingsStore.edit { it[Keys.PERM_ACCESSIBILITY] = v } }
    suspend fun setPermVpn(v: Boolean) { context.settingsStore.edit { it[Keys.PERM_VPN] = v } }
    suspend fun setPermAdmin(v: Boolean) { context.settingsStore.edit { it[Keys.PERM_ADMIN] = v } }
    suspend fun setPermOverlay(v: Boolean) { context.settingsStore.edit { it[Keys.PERM_OVERLAY] = v } }
    suspend fun setPermUsage(v: Boolean) { context.settingsStore.edit { it[Keys.PERM_USAGE] = v } }
    suspend fun setPermNotifications(v: Boolean) { context.settingsStore.edit { it[Keys.PERM_NOTIFICATIONS] = v } }

    suspend fun isOnboardingComplete(): Boolean = context.settingsStore.data.first()[Keys.ONBOARDING_COMPLETE] ?: false
    suspend fun isShieldActive(): Boolean = context.settingsStore.data.first()[Keys.SHIELD_ACTIVE] ?: false
    suspend fun isPornBlockerActive(): Boolean = context.settingsStore.data.first()[Keys.PORN_BLOCKER] ?: false
    suspend fun hasPin(): Boolean = encryptedPrefs.hasPin()
    suspend fun getDeactivationDelay(): Int = context.settingsStore.data.first()[Keys.DEACTIVATION_DELAY_MINUTES] ?: 0
    suspend fun getProfileName(): String = context.settingsStore.data.first()[Keys.PROFILE_NAME] ?: ""
    suspend fun getPinHash(): String = encryptedPrefs.getPinHash()
    suspend fun getYoutubeMode(): String = context.settingsStore.data.first()[Keys.YOUTUBE_MODE] ?: "off"
    suspend fun getFacebookMode(): String = context.settingsStore.data.first()[Keys.FACEBOOK_MODE] ?: "off"
    suspend fun isStrictMode(): Boolean = context.settingsStore.data.first()[Keys.STRICT_MODE] ?: false
    suspend fun isYoutubeShortsMode(): Boolean = getYoutubeMode() == "shorts"
    suspend fun isFacebookReelsMode(): Boolean = getFacebookMode() == "reels"

    companion object {
        fun calculateXp(blockCount: Int): Int = blockCount * 10

        fun checkLevelUp(xp: Int): Int {
            var level = 1
            var xpNeeded = 100
            var totalXp = 0
            while (totalXp + xpNeeded <= xp) {
                totalXp += xpNeeded
                level++
                xpNeeded = (xpNeeded * 1.5).toInt()
            }
            return level
        }
    }
}
