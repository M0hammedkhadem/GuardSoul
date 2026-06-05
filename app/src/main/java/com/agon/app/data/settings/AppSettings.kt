package com.agon.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.agon.app.blocking.DayOfWeekUtil
import com.agon.app.billing.SubscriptionTier
import com.agon.app.data.local.dao.BlockEventDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

private val Context.settingsStore by preferencesDataStore(name = "app_settings")

class AppSettings(private val context: Context) {
    private val encryptedPrefs = EncryptedPrefs(context)

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val SHIELD_ACTIVE = booleanPreferencesKey("shield_active")
        val SHIELD_ACTIVATED_AT = longPreferencesKey("shield_activated_at")
        val TRIAL_MODE = booleanPreferencesKey("trial_mode")
        val DEACTIVATION_DELAY_DAYS = intPreferencesKey("deactivation_delay_days")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val STREAK_COUNT = intPreferencesKey("streak_count")
        val LONGEST_STREAK = intPreferencesKey("longest_streak")
        val LAST_ACTIVE_DATE = longPreferencesKey("last_active_date")

        val SOCIAL_INSTAGRAM = booleanPreferencesKey("social_instagram")
        val INSTAGRAM_MODE = stringPreferencesKey("instagram_mode")
        val SOCIAL_SNAPCHAT = booleanPreferencesKey("social_snapchat")
        val SOCIAL_TWITTER = booleanPreferencesKey("social_twitter")
        val SOCIAL_TIKTOK = booleanPreferencesKey("social_tiktok")
        val YOUTUBE_MODE = stringPreferencesKey("youtube_mode")
        val FACEBOOK_MODE = stringPreferencesKey("facebook_mode")
        val SHORTS_BLOCK_ACTION = stringPreferencesKey("shorts_block_action")

        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val STRICT_MODE_COOLDOWN_END_AT = longPreferencesKey("strict_mode_cooldown_end_at")

        // --- Bonus Time + Bedtime Grayscale (Family Link / Screen Time) --
        // Bonus time is a small buffer of extra minutes the user can grant
        // themselves after a schedule window kicks in. It's a "release
        // valve" that prevents the user from rage-quitting the app when
        // they need 5 more minutes to finish a task.
        val BONUS_TIME_REMAINING_MS = longPreferencesKey("bonus_time_remaining_ms")
        val BONUS_TIME_GRANTED_AT = longPreferencesKey("bonus_time_granted_at")
        val BONUS_TIME_CAP_MINUTES = intPreferencesKey("bonus_time_cap_minutes")

        // Bedtime grayscale: when `cachedBedtimeActive` is true and this
        // flag is on, we apply a `ColorMatrix` desaturate filter to the
        // whole window stack. Mirrors the iOS "Grayscale at Bedtime"
        // pattern (Screen Time + Family Link).
        val BEDTIME_GRAYSCALE = booleanPreferencesKey("bedtime_grayscale")

        // --- Study Room (Screen Stoic + Forest style) -----------------
        // When `studyRoomActiveUntil` is in the future, `AppBlockerService`
        // switches into "study mode": every non-education, non-productivity
        // app is blocked. The user picks the duration (default 60 min)
        // and the room auto-closes when the timer expires. Forest uses
        // the same pattern with a tree that grows while you focus.
        val STUDY_ROOM_ACTIVE_UNTIL = longPreferencesKey("study_room_active_until")
        val STUDY_ROOM_DURATION_MINUTES = intPreferencesKey("study_room_duration_minutes")
        val STUDY_ROOM_TOTAL_MINUTES_FOCUSED = intPreferencesKey("study_room_total_minutes_focused")

        // --- Accountability partner (Bulldog / Canopy style) -----------
        val ACCOUNTABILITY_ENABLED = booleanPreferencesKey("accountability_enabled")
        val ACCOUNTABILITY_EMAIL = stringPreferencesKey("accountability_email")
        val PENDING_UNLOCK_CODE = stringPreferencesKey("pending_unlock_code")
        val PENDING_UNLOCK_CODE_EXPIRES_AT = longPreferencesKey("pending_unlock_code_expires_at")

        val PORN_BLOCKER = booleanPreferencesKey("porn_blocker")
        val AI_SCANNER = booleanPreferencesKey("ai_scanner")
        val AI_SENSITIVITY = intPreferencesKey("ai_sensitivity")
        val UNINSTALL_PROTECTION = booleanPreferencesKey("uninstall_protection")
        val STRONG_PROTECTION = booleanPreferencesKey("strong_protection")
        val BLOCK_SAFE_MODE = booleanPreferencesKey("block_safe_mode")
        val NEXTDNS_PROFILE_ID = stringPreferencesKey("nextdns_profile_id")
        val AI_OVERLAY_MODE = booleanPreferencesKey("ai_overlay_mode")
        val SAFE_SEARCH_MODE = stringPreferencesKey("safe_search_mode")
        val BLOCK_DOH = booleanPreferencesKey("block_doh")
        val AI_THRESHOLD = floatPreferencesKey("ai_threshold")

        val XP_POINTS = intPreferencesKey("xp_points")
        val LEVEL = intPreferencesKey("level")
        val TOTAL_BLOCKS_LIFETIME = intPreferencesKey("total_blocks_lifetime")

        // --- Daily pledge + milestones (I Am Sober style) ---------------
        val DAILY_PLEDGE_DATE = stringPreferencesKey("daily_pledge_date")
        val MILESTONES_ACHIEVED = stringSetPreferencesKey("milestones_achieved")
        val LAST_MILESTONE_CHECK = longPreferencesKey("last_milestone_check")
        val WITHDRAWAL_START_DAY = intPreferencesKey("withdrawal_start_day")

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

        val PARENT_EMAIL = stringPreferencesKey("parent_email")
        val CHILD_DEVICE_ID = stringPreferencesKey("child_device_id")
        val REMOTE_MONITORING_ENABLED = booleanPreferencesKey("remote_monitoring_enabled")

        val PERM_ACCESSIBILITY = booleanPreferencesKey("perm_accessibility")
        val PERM_VPN = booleanPreferencesKey("perm_vpn")
        val PERM_ADMIN = booleanPreferencesKey("perm_admin")
        val PERM_OVERLAY = booleanPreferencesKey("perm_overlay")
        val PERM_USAGE = booleanPreferencesKey("perm_usage")
        val PERM_NOTIFICATIONS = booleanPreferencesKey("perm_notifications")

        // --- SaaS / Billing / Account ---------------------------------
        val SUBSCRIPTION_TIER = stringPreferencesKey("subscription_tier")
        val SUBSCRIPTION_EXPIRES_AT = longPreferencesKey("subscription_expires_at")
        val AUTH_USER_ID = stringPreferencesKey("auth_user_id")
        val AUTH_PROVIDER = stringPreferencesKey("auth_provider")
        val AUTH_ANONYMOUS = booleanPreferencesKey("auth_anonymous")
        val CONSENT_GDPR_DECIDED = booleanPreferencesKey("consent_gdpr_decided")
        val CONSENT_ANALYTICS = booleanPreferencesKey("consent_analytics")
        val CONSENT_CRASH = booleanPreferencesKey("consent_crash")
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val CLOUD_LAST_SYNC_AT = longPreferencesKey("cloud_last_sync_at")
        val REVIEW_PROMPTED = booleanPreferencesKey("review_prompted")
        val REVIEW_USAGE_COUNT = intPreferencesKey("review_usage_count")
        val REVIEW_NEXT_ELIGIBLE_AT = longPreferencesKey("review_next_eligible_at")

        // --- Shortstop (surgical blocking) scheduling + quota ----------
        val SHORTSTOP_BLOCKED_HOUR_ACTIVE = booleanPreferencesKey("shortstop_blocked_hour_active")
        val SHORTSTOP_DAILY_QUOTA_EXCEEDED = booleanPreferencesKey("shortstop_daily_quota_exceeded")
        val SHORTSTOP_BREAK_ACTIVE = booleanPreferencesKey("shortstop_break_active")
        val SHORTSTOP_DAILY_QUOTA_MIN = intPreferencesKey("shortstop_daily_quota_min")
        val SHORTSTOP_BREAK_INTERVAL_MIN = intPreferencesKey("shortstop_break_interval_min")
        val SHORTSTOP_BREAK_LENGTH_MIN = intPreferencesKey("shortstop_break_length_min")
        val SHORTSTOP_MINUTES_SPENT_TODAY = intPreferencesKey("shortstop_minutes_spent_today")
        val SHORTSTOP_BREAK_ENDS_AT = longPreferencesKey("shortstop_break_ends_at")

        // --- Self-learning learner (auto-discovered short-form signatures) ---
        val LEARNED_SIGNATURES = stringPreferencesKey("learned_signatures_v1")
        val LEARNER_ENABLED = booleanPreferencesKey("learner_enabled")
    }

    // --- AI Explorer temp block storage ---------------------------------
    // We avoid adding a new key object instance per call; the helpers below
    // build dynamic keys based on the package name.

    val onboardingCompleteFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }
    val shieldActiveFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SHIELD_ACTIVE] ?: false }
    val shieldActivatedAtFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.SHIELD_ACTIVATED_AT] ?: 0L }
    val trialModeFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.TRIAL_MODE] ?: false }
    val deactivationDelayFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.DEACTIVATION_DELAY_DAYS] ?: 0 }
    val profileNameFlow: Flow<String> = context.settingsStore.data.map { it[Keys.PROFILE_NAME] ?: "" }
    val pinHashFlow: Flow<String> = encryptedPrefs.pinHashFlow
    val streakCountFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.STREAK_COUNT] ?: 0 }
    val longestStreakFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.LONGEST_STREAK] ?: 0 }
    val lastActiveDateFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.LAST_ACTIVE_DATE] ?: 0L }

    val socialInstagramFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SOCIAL_INSTAGRAM] ?: false }
    val instagramModeFlow: Flow<String> = context.settingsStore.data.map { it[Keys.INSTAGRAM_MODE] ?: "off" }
    val socialSnapchatFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SOCIAL_SNAPCHAT] ?: false }
    val socialTwitterFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SOCIAL_TWITTER] ?: false }
    val socialTiktokFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SOCIAL_TIKTOK] ?: false }
    val youtubeModeFlow: Flow<String> = context.settingsStore.data.map { it[Keys.YOUTUBE_MODE] ?: "off" }
    val facebookModeFlow: Flow<String> = context.settingsStore.data.map { it[Keys.FACEBOOK_MODE] ?: "off" }
    val shortsBlockActionFlow: Flow<String> = context.settingsStore.data.map { it[Keys.SHORTS_BLOCK_ACTION] ?: "redirect" }

    val strictModeFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.STRICT_MODE] ?: false }
    val strictModeCooldownEndAtFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.STRICT_MODE_COOLDOWN_END_AT] ?: 0L }

    val bonusTimeRemainingMsFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.BONUS_TIME_REMAINING_MS] ?: 0L }
    val bonusTimeGrantedAtFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.BONUS_TIME_GRANTED_AT] ?: 0L }
    val bonusTimeCapMinutesFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.BONUS_TIME_CAP_MINUTES] ?: 30 }
    val bedtimeGrayscaleFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.BEDTIME_GRAYSCALE] ?: true }

    val studyRoomActiveUntilFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.STUDY_ROOM_ACTIVE_UNTIL] ?: 0L }
    val studyRoomDurationMinutesFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.STUDY_ROOM_DURATION_MINUTES] ?: 60 }
    val studyRoomTotalMinutesFocusedFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.STUDY_ROOM_TOTAL_MINUTES_FOCUSED] ?: 0 }

    // --- Accountability partner flows -------------------------------
    val accountabilityEnabledFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.ACCOUNTABILITY_ENABLED] ?: false }
    val accountabilityEmailFlow: Flow<String> = context.settingsStore.data.map { it[Keys.ACCOUNTABILITY_EMAIL] ?: "" }
    val pendingUnlockCodeFlow: Flow<String> = context.settingsStore.data.map { it[Keys.PENDING_UNLOCK_CODE] ?: "" }
    val pendingUnlockCodeExpiresAtFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.PENDING_UNLOCK_CODE_EXPIRES_AT] ?: 0L }
    val pornBlockerFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PORN_BLOCKER] ?: false }
    val aiScannerFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.AI_SCANNER] ?: false }
    val aiSensitivityFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.AI_SENSITIVITY] ?: 75 }
    val uninstallProtectionFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.UNINSTALL_PROTECTION] ?: false }
    val strongProtectionFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.STRONG_PROTECTION] ?: false }
    val blockSafeModeFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.BLOCK_SAFE_MODE] ?: false }
    val nextDnsProfileIdFlow: Flow<String> = context.settingsStore.data.map { it[Keys.NEXTDNS_PROFILE_ID] ?: "" }
    val aiOverlayModeFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.AI_OVERLAY_MODE] ?: false }
    val safeSearchModeFlow: Flow<String> = context.settingsStore.data.map { it[Keys.SAFE_SEARCH_MODE] ?: "basic" }
    val blockDohFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.BLOCK_DOH] ?: false }
    val aiThresholdFlow: Flow<Float> = context.settingsStore.data.map { it[Keys.AI_THRESHOLD] ?: 0.7f }

    val xpPointsFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.XP_POINTS] ?: 0 }
    val levelFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.LEVEL] ?: 1 }
    val totalBlocksLifetimeFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.TOTAL_BLOCKS_LIFETIME] ?: 0 }

    // --- Daily pledge + milestones ------------------------------------
    val dailyPledgeDateFlow: Flow<String> = context.settingsStore.data.map { it[Keys.DAILY_PLEDGE_DATE] ?: "" }
    val milestonesAchievedFlow: Flow<Set<String>> = context.settingsStore.data.map { it[Keys.MILESTONES_ACHIEVED] ?: emptySet() }
    val lastMilestoneCheckFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.LAST_MILESTONE_CHECK] ?: 0L }
    val withdrawalStartDayFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.WITHDRAWAL_START_DAY] ?: -1 }

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
    
    val remoteMonitoringEnabledFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.REMOTE_MONITORING_ENABLED] ?: false }
    val parentEmailFlow: Flow<String> = context.settingsStore.data.map { it[Keys.PARENT_EMAIL] ?: "" }

    // --- Shortstop flows ----------------------------------------------
    val shortstopBlockedHourActiveFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SHORTSTOP_BLOCKED_HOUR_ACTIVE] ?: false }
    val shortstopDailyQuotaExceededFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SHORTSTOP_DAILY_QUOTA_EXCEEDED] ?: false }
    val shortstopBreakActiveFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SHORTSTOP_BREAK_ACTIVE] ?: false }
    val shortstopDailyQuotaMinutesFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.SHORTSTOP_DAILY_QUOTA_MIN] ?: 10 }
    val shortstopBreakIntervalMinutesFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.SHORTSTOP_BREAK_INTERVAL_MIN] ?: 15 }
    val shortstopBreakLengthMinutesFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.SHORTSTOP_BREAK_LENGTH_MIN] ?: 5 }
    val shortstopMinutesSpentTodayFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.SHORTSTOP_MINUTES_SPENT_TODAY] ?: 0 }
    val shortstopBreakEndsAtFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.SHORTSTOP_BREAK_ENDS_AT] ?: 0L }

    val permAccessibilityFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PERM_ACCESSIBILITY] ?: false }
    val permVpnFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PERM_VPN] ?: false }
    val permAdminFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PERM_ADMIN] ?: false }
    val permOverlayFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PERM_OVERLAY] ?: false }
    val permUsageFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PERM_USAGE] ?: false }
    val permNotificationsFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PERM_NOTIFICATIONS] ?: false }

    suspend fun setOnboardingComplete() { context.settingsStore.edit { it[Keys.ONBOARDING_COMPLETE] = true } }
    suspend fun setShieldActive(v: Boolean) { context.settingsStore.edit { it[Keys.SHIELD_ACTIVE] = v } }
    suspend fun setShieldActivatedAt(v: Long) { context.settingsStore.edit { it[Keys.SHIELD_ACTIVATED_AT] = v } }
    suspend fun setTrialMode(v: Boolean) { context.settingsStore.edit { it[Keys.TRIAL_MODE] = v } }
    suspend fun setDeactivationDelay(v: Int) { context.settingsStore.edit { it[Keys.DEACTIVATION_DELAY_DAYS] = v } }
    suspend fun setProfileName(v: String) { context.settingsStore.edit { it[Keys.PROFILE_NAME] = v } }
    suspend fun setPinHash(v: String) { encryptedPrefs.savePinHash(v) }
    suspend fun setStreakCount(v: Int) { context.settingsStore.edit { it[Keys.STREAK_COUNT] = v } }
    suspend fun setLongestStreak(v: Int) { context.settingsStore.edit { it[Keys.LONGEST_STREAK] = v } }
    suspend fun setLastActiveDate(v: Long) { context.settingsStore.edit { it[Keys.LAST_ACTIVE_DATE] = v } }

    suspend fun setInstagramMode(v: String) {
        require(v in listOf("off", "full", "reels")) { "Invalid Instagram mode: $v" }
        context.settingsStore.edit { it[Keys.INSTAGRAM_MODE] = v }
    }
    suspend fun setSocialInstagram(v: Boolean) { context.settingsStore.edit { it[Keys.SOCIAL_INSTAGRAM] = v } }
    suspend fun setSocialSnapchat(v: Boolean) { context.settingsStore.edit { it[Keys.SOCIAL_SNAPCHAT] = v } }
    suspend fun setSocialTwitter(v: Boolean) { context.settingsStore.edit { it[Keys.SOCIAL_TWITTER] = v } }
    suspend fun setSocialTiktok(v: Boolean) { context.settingsStore.edit { it[Keys.SOCIAL_TIKTOK] = v } }
    suspend fun setYoutubeMode(v: String) {
        require(v in listOf("off", "full", "shorts")) { "Invalid YouTube mode: $v" }
        context.settingsStore.edit { it[Keys.YOUTUBE_MODE] = v }
    }
    suspend fun setFacebookMode(v: String) {
        require(v in listOf("off", "full", "reels")) { "Invalid Facebook mode: $v" }
        context.settingsStore.edit { it[Keys.FACEBOOK_MODE] = v }
    }
    suspend fun setShortsBlockAction(v: String) { context.settingsStore.edit { it[Keys.SHORTS_BLOCK_ACTION] = v } }
    suspend fun getShortsBlockAction(): String = context.settingsStore.data.first()[Keys.SHORTS_BLOCK_ACTION] ?: "redirect"

    // --- Shortstop setters --------------------------------------------
    suspend fun setShortstopBlockedHourActive(v: Boolean) { context.settingsStore.edit { it[Keys.SHORTSTOP_BLOCKED_HOUR_ACTIVE] = v } }
    suspend fun setShortstopDailyQuotaExceeded(v: Boolean) { context.settingsStore.edit { it[Keys.SHORTSTOP_DAILY_QUOTA_EXCEEDED] = v } }
    suspend fun setShortstopBreakActive(v: Boolean) { context.settingsStore.edit { it[Keys.SHORTSTOP_BREAK_ACTIVE] = v } }
    suspend fun setShortstopDailyQuotaMinutes(v: Int) { context.settingsStore.edit { it[Keys.SHORTSTOP_DAILY_QUOTA_MIN] = v } }
    suspend fun setShortstopBreakIntervalMinutes(v: Int) { context.settingsStore.edit { it[Keys.SHORTSTOP_BREAK_INTERVAL_MIN] = v } }
    suspend fun setShortstopBreakLengthMinutes(v: Int) { context.settingsStore.edit { it[Keys.SHORTSTOP_BREAK_LENGTH_MIN] = v } }
    suspend fun setShortstopMinutesSpentToday(v: Int) { context.settingsStore.edit { it[Keys.SHORTSTOP_MINUTES_SPENT_TODAY] = v } }
    suspend fun setShortstopBreakEndsAt(v: Long) { context.settingsStore.edit { it[Keys.SHORTSTOP_BREAK_ENDS_AT] = v } }

    // --- Self-learning learner (auto-discovered short-form signatures) ---
    val learnerEnabledFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.LEARNER_ENABLED] ?: true }
    suspend fun setLearnerEnabled(v: Boolean) { context.settingsStore.edit { it[Keys.LEARNER_ENABLED] = v } }
    suspend fun getLearnedSignaturesRaw(): String = context.settingsStore.data.first()[Keys.LEARNED_SIGNATURES] ?: ""
    suspend fun setLearnedSignaturesRaw(v: String) { context.settingsStore.edit { it[Keys.LEARNED_SIGNATURES] = v } }

    suspend fun setStrictMode(v: Boolean) { context.settingsStore.edit { it[Keys.STRICT_MODE] = v } }
    suspend fun setStrictModeCooldownEndAt(v: Long) { context.settingsStore.edit { it[Keys.STRICT_MODE_COOLDOWN_END_AT] = v } }

    suspend fun setBonusTimeRemainingMs(v: Long) { context.settingsStore.edit { it[Keys.BONUS_TIME_REMAINING_MS] = v } }
    suspend fun setBonusTimeGrantedAt(v: Long) { context.settingsStore.edit { it[Keys.BONUS_TIME_GRANTED_AT] = v } }
    suspend fun setBonusTimeCapMinutes(v: Int) { context.settingsStore.edit { it[Keys.BONUS_TIME_CAP_MINUTES] = v } }
    suspend fun setBedtimeGrayscale(v: Boolean) { context.settingsStore.edit { it[Keys.BEDTIME_GRAYSCALE] = v } }

    suspend fun setStudyRoomActiveUntil(v: Long) { context.settingsStore.edit { it[Keys.STUDY_ROOM_ACTIVE_UNTIL] = v } }
    suspend fun setStudyRoomDurationMinutes(v: Int) { context.settingsStore.edit { it[Keys.STUDY_ROOM_DURATION_MINUTES] = v } }
    suspend fun setStudyRoomTotalMinutesFocused(v: Int) { context.settingsStore.edit { it[Keys.STUDY_ROOM_TOTAL_MINUTES_FOCUSED] = v } }

    suspend fun setAccountabilityEnabled(v: Boolean) { context.settingsStore.edit { it[Keys.ACCOUNTABILITY_ENABLED] = v } }
    suspend fun setAccountabilityEmail(v: String) { context.settingsStore.edit { it[Keys.ACCOUNTABILITY_EMAIL] = v } }
    suspend fun setPendingUnlockCode(code: String, expiresAt: Long) {
        context.settingsStore.edit {
            it[Keys.PENDING_UNLOCK_CODE] = code
            it[Keys.PENDING_UNLOCK_CODE_EXPIRES_AT] = expiresAt
        }
    }
    suspend fun clearPendingUnlockCode() {
        context.settingsStore.edit {
            it[Keys.PENDING_UNLOCK_CODE] = ""
            it[Keys.PENDING_UNLOCK_CODE_EXPIRES_AT] = 0L
        }
    }
    suspend fun isStrictModeCooldownActive(): Boolean {
        val end = context.settingsStore.data.first()[Keys.STRICT_MODE_COOLDOWN_END_AT] ?: 0L
        return end > System.currentTimeMillis()
    }
    suspend fun setPornBlocker(v: Boolean) { context.settingsStore.edit { it[Keys.PORN_BLOCKER] = v } }
    suspend fun setAiScanner(v: Boolean) { context.settingsStore.edit { it[Keys.AI_SCANNER] = v } }
    suspend fun setAiSensitivity(v: Int) { context.settingsStore.edit { it[Keys.AI_SENSITIVITY] = v } }
    suspend fun setUninstallProtection(v: Boolean) { context.settingsStore.edit { it[Keys.UNINSTALL_PROTECTION] = v } }
    suspend fun setStrongProtection(v: Boolean) { context.settingsStore.edit { it[Keys.STRONG_PROTECTION] = v } }
    suspend fun setBlockSafeMode(v: Boolean) { context.settingsStore.edit { it[Keys.BLOCK_SAFE_MODE] = v } }
    suspend fun setNextDnsProfileId(v: String) { context.settingsStore.edit { it[Keys.NEXTDNS_PROFILE_ID] = v } }
    suspend fun getNextDnsProfileId(): String = context.settingsStore.data.first()[Keys.NEXTDNS_PROFILE_ID] ?: ""
    suspend fun setAiOverlayMode(v: Boolean) { context.settingsStore.edit { it[Keys.AI_OVERLAY_MODE] = v } }
    suspend fun setSafeSearchMode(v: String) { context.settingsStore.edit { it[Keys.SAFE_SEARCH_MODE] = v } }
    suspend fun setBlockDoh(v: Boolean) { context.settingsStore.edit { it[Keys.BLOCK_DOH] = v } }
    suspend fun setAiThreshold(v: Float) { context.settingsStore.edit { it[Keys.AI_THRESHOLD] = v } }

    suspend fun setXpPoints(v: Int) { context.settingsStore.edit { it[Keys.XP_POINTS] = v } }
    suspend fun setLevel(v: Int) { context.settingsStore.edit { it[Keys.LEVEL] = v } }
    suspend fun setTotalBlocksLifetime(v: Int) { context.settingsStore.edit { it[Keys.TOTAL_BLOCKS_LIFETIME] = v } }

    suspend fun setDailyPledgeDate(v: String) { context.settingsStore.edit { it[Keys.DAILY_PLEDGE_DATE] = v } }
    suspend fun setMilestonesAchieved(v: Set<String>) { context.settingsStore.edit { it[Keys.MILESTONES_ACHIEVED] = v } }
    suspend fun setLastMilestoneCheck(v: Long) { context.settingsStore.edit { it[Keys.LAST_MILESTONE_CHECK] = v } }
    suspend fun setWithdrawalStartDay(v: Int) { context.settingsStore.edit { it[Keys.WITHDRAWAL_START_DAY] = v } }

    suspend fun setSchoolTimeEnabled(v: Boolean) { context.settingsStore.edit { it[Keys.SCHOOL_TIME_ENABLED] = v } }
    suspend fun setSchoolTimeStart(hour: Int, minute: Int) { context.settingsStore.edit { it[Keys.SCHOOL_TIME_START_HOUR] = hour; it[Keys.SCHOOL_TIME_START_MINUTE] = minute } }
    suspend fun setSchoolTimeEnd(hour: Int, minute: Int) { context.settingsStore.edit { it[Keys.SCHOOL_TIME_END_HOUR] = hour; it[Keys.SCHOOL_TIME_END_MINUTE] = minute } }
    suspend fun setSchoolTimeDays(days: String) { context.settingsStore.edit { it[Keys.SCHOOL_TIME_DAYS] = days } }
    suspend fun setBedtimeModeEnabled(v: Boolean) { context.settingsStore.edit { it[Keys.BEDTIME_MODE_ENABLED] = v } }
    suspend fun setBedtimeStart(hour: Int, minute: Int) { context.settingsStore.edit { it[Keys.BEDTIME_START_HOUR] = hour; it[Keys.BEDTIME_START_MINUTE] = minute } }
    suspend fun setBedtimeEnd(hour: Int, minute: Int) { context.settingsStore.edit { it[Keys.BEDTIME_END_HOUR] = hour; it[Keys.BEDTIME_END_MINUTE] = minute } }
    suspend fun setAutoLockOnLimit(v: Boolean) { context.settingsStore.edit { it[Keys.AUTO_LOCK_ON_LIMIT] = v } }

    suspend fun setParentEmail(v: String) { context.settingsStore.edit { it[Keys.PARENT_EMAIL] = v } }
    suspend fun setChildDeviceId(v: String) { context.settingsStore.edit { it[Keys.CHILD_DEVICE_ID] = v } }
    suspend fun setRemoteMonitoringEnabled(v: Boolean) { context.settingsStore.edit { it[Keys.REMOTE_MONITORING_ENABLED] = v } }

    suspend fun getParentEmail(): String = context.settingsStore.data.first()[Keys.PARENT_EMAIL] ?: ""
    suspend fun getChildDeviceId(): String = context.settingsStore.data.first()[Keys.CHILD_DEVICE_ID] ?: ""
    suspend fun isRemoteMonitoringEnabled(): Boolean = context.settingsStore.data.first()[Keys.REMOTE_MONITORING_ENABLED] ?: false

    // helper suspend methods for features
    suspend fun isOnboardingComplete(): Boolean = context.settingsStore.data.first()[Keys.ONBOARDING_COMPLETE] ?: false
    suspend fun isShieldActive(): Boolean = context.settingsStore.data.first()[Keys.SHIELD_ACTIVE] ?: false
    suspend fun getShieldActivatedAt(): Long = context.settingsStore.data.first()[Keys.SHIELD_ACTIVATED_AT] ?: 0L
    suspend fun isPornBlockerActive(): Boolean = context.settingsStore.data.first()[Keys.PORN_BLOCKER] ?: false
    suspend fun isAiScannerActive(): Boolean = context.settingsStore.data.first()[Keys.AI_SCANNER] ?: false
    suspend fun hasPin(): Boolean = encryptedPrefs.hasPin()
    suspend fun getDeactivationDelay(): Int = context.settingsStore.data.first()[Keys.DEACTIVATION_DELAY_DAYS] ?: 0
    suspend fun getProfileName(): String = context.settingsStore.data.first()[Keys.PROFILE_NAME] ?: ""
    suspend fun getPinHash(): String = encryptedPrefs.getPinHash()
    suspend fun getInstagramMode(): String = context.settingsStore.data.first()[Keys.INSTAGRAM_MODE] ?: "off"
    suspend fun getYoutubeMode(): String = context.settingsStore.data.first()[Keys.YOUTUBE_MODE] ?: "off"
    suspend fun getFacebookMode(): String = context.settingsStore.data.first()[Keys.FACEBOOK_MODE] ?: "off"
    suspend fun isStrictMode(): Boolean = context.settingsStore.data.first()[Keys.STRICT_MODE] ?: false
    suspend fun isYoutubeShortsMode(): Boolean = getYoutubeMode() == "shorts"
    suspend fun isFacebookReelsMode(): Boolean = getFacebookMode() == "reels"
    suspend fun isInstagramReelsMode(): Boolean = getInstagramMode() == "reels"
    suspend fun isInstagramBlocked(): Boolean = context.settingsStore.data.first()[Keys.SOCIAL_INSTAGRAM] ?: false
    suspend fun isSnapchatBlocked(): Boolean = context.settingsStore.data.first()[Keys.SOCIAL_SNAPCHAT] ?: false
    suspend fun isTwitterBlocked(): Boolean = context.settingsStore.data.first()[Keys.SOCIAL_TWITTER] ?: false
    suspend fun isTiktokBlocked(): Boolean = context.settingsStore.data.first()[Keys.SOCIAL_TIKTOK] ?: false
    suspend fun isSafeModeBlockEnabled(): Boolean = context.settingsStore.data.first()[Keys.BLOCK_SAFE_MODE] ?: false
    suspend fun getSafeSearchMode(): String = context.settingsStore.data.first()[Keys.SAFE_SEARCH_MODE] ?: "basic"
    suspend fun isBlockDohEnabled(): Boolean = context.settingsStore.data.first()[Keys.BLOCK_DOH] ?: false

    // Issue #192 & #193: Unified logic for time-based features
    fun getTodayStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    suspend fun calculateStreak(blockEventDao: BlockEventDao): Int {
        val todayStart = getTodayStart()
        var streak = 0
        var checkTime = todayStart
        while (true) {
            val dayEnd = checkTime + 86400000L
            val hasEvent = blockEventDao.blocksSince(checkTime).any { it.timestamp < dayEnd }
            if (!hasEvent) break
            streak++
            checkTime -= 86400000L
            if (streak > 3650) break 
        }
        return streak
    }

    suspend fun isSchoolTimeActive(): Boolean {
        if (!schoolTimeEnabledFlow.first()) return false
        val now = Calendar.getInstance()
        val mappedDay = DayOfWeekUtil.calendarDayToMondayFirstIndex(now.get(Calendar.DAY_OF_WEEK))
        val data = context.settingsStore.data.first()
        val days = DayOfWeekUtil.decode(data[Keys.SCHOOL_TIME_DAYS] ?: "1,2,3,4,5")
        if (mappedDay !in days) return false
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = (data[Keys.SCHOOL_TIME_START_HOUR] ?: 8) * 60 + (data[Keys.SCHOOL_TIME_START_MINUTE] ?: 0)
        val endMinutes = (data[Keys.SCHOOL_TIME_END_HOUR] ?: 15) * 60 + (data[Keys.SCHOOL_TIME_END_MINUTE] ?: 0)
        return currentMinutes in startMinutes..endMinutes
    }

    suspend fun isBedtimeActive(): Boolean {
        if (!bedtimeModeEnabledFlow.first()) return false
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val data = context.settingsStore.data.first()
        val startMinutes = (data[Keys.BEDTIME_START_HOUR] ?: 22) * 60 + (data[Keys.BEDTIME_START_MINUTE] ?: 0)
        val endMinutes = (data[Keys.BEDTIME_END_HOUR] ?: 7) * 60 + (data[Keys.BEDTIME_END_MINUTE] ?: 0)
        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    suspend fun setPermAccessibility(v: Boolean) { context.settingsStore.edit { it[Keys.PERM_ACCESSIBILITY] = v } }
    suspend fun setPermVpn(v: Boolean) { context.settingsStore.edit { it[Keys.PERM_VPN] = v } }
    suspend fun setPermAdmin(v: Boolean) { context.settingsStore.edit { it[Keys.PERM_ADMIN] = v } }
    suspend fun setPermOverlay(v: Boolean) { context.settingsStore.edit { it[Keys.PERM_OVERLAY] = v } }
    suspend fun setPermUsage(v: Boolean) { context.settingsStore.edit { it[Keys.PERM_USAGE] = v } }
    suspend fun setPermNotifications(v: Boolean) { context.settingsStore.edit { it[Keys.PERM_NOTIFICATIONS] = v } }

    // --- SaaS / Billing / Account flows -------------------------------
    val subscriptionTierFlow: Flow<String> = context.settingsStore.data.map { it[Keys.SUBSCRIPTION_TIER] ?: "free" }
    val subscriptionExpiresAtFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.SUBSCRIPTION_EXPIRES_AT] ?: 0L }
    val authUserIdFlow: Flow<String> = context.settingsStore.data.map { it[Keys.AUTH_USER_ID] ?: "" }
    val authProviderFlow: Flow<String> = context.settingsStore.data.map { it[Keys.AUTH_PROVIDER] ?: "anonymous" }
    val authAnonymousFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.AUTH_ANONYMOUS] ?: true }
    val consentGdprDecidedFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.CONSENT_GDPR_DECIDED] ?: false }
    val consentAnalyticsFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.CONSENT_ANALYTICS] ?: false }
    val consentCrashFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.CONSENT_CRASH] ?: false }
    val cloudSyncEnabledFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.CLOUD_SYNC_ENABLED] ?: false }
    val cloudLastSyncAtFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.CLOUD_LAST_SYNC_AT] ?: 0L }
    val reviewPromptedFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.REVIEW_PROMPTED] ?: false }
    val reviewUsageCountFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.REVIEW_USAGE_COUNT] ?: 0 }
    val reviewNextEligibleAtFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.REVIEW_NEXT_ELIGIBLE_AT] ?: 0L }

    suspend fun setSubscriptionTier(v: SubscriptionTier) {
        context.settingsStore.edit { it[Keys.SUBSCRIPTION_TIER] = v.name }
    }
    suspend fun setSubscriptionExpiresAt(v: Long) { context.settingsStore.edit { it[Keys.SUBSCRIPTION_EXPIRES_AT] = v } }
    suspend fun getSubscriptionTierCached(): SubscriptionTier {
        val raw = context.settingsStore.data.first()[Keys.SUBSCRIPTION_TIER] ?: "free"
        return runCatching { SubscriptionTier.valueOf(raw) }.getOrDefault(SubscriptionTier.FREE)
    }
    suspend fun setAuthUserId(v: String) { context.settingsStore.edit { it[Keys.AUTH_USER_ID] = v } }
    suspend fun setAuthProvider(v: String) { context.settingsStore.edit { it[Keys.AUTH_PROVIDER] = v } }
    suspend fun setAuthAnonymous(v: Boolean) { context.settingsStore.edit { it[Keys.AUTH_ANONYMOUS] = v } }
    suspend fun setConsentGdprDecided(v: Boolean) { context.settingsStore.edit { it[Keys.CONSENT_GDPR_DECIDED] = v } }
    suspend fun setConsentAnalytics(v: Boolean) { context.settingsStore.edit { it[Keys.CONSENT_ANALYTICS] = v } }
    suspend fun setConsentCrash(v: Boolean) { context.settingsStore.edit { it[Keys.CONSENT_CRASH] = v } }
    suspend fun setCloudSyncEnabled(v: Boolean) { context.settingsStore.edit { it[Keys.CLOUD_SYNC_ENABLED] = v } }
    suspend fun setCloudLastSyncAt(v: Long) { context.settingsStore.edit { it[Keys.CLOUD_LAST_SYNC_AT] = v } }
    suspend fun setReviewPrompted(v: Boolean) { context.settingsStore.edit { it[Keys.REVIEW_PROMPTED] = v } }
    suspend fun setReviewUsageCount(v: Int) { context.settingsStore.edit { it[Keys.REVIEW_USAGE_COUNT] = v } }
    suspend fun setReviewNextEligibleAt(v: Long) { context.settingsStore.edit { it[Keys.REVIEW_NEXT_ELIGIBLE_AT] = v } }
    suspend fun incrementReviewUsageCount() {
        context.settingsStore.edit { prefs ->
            val cur = prefs[Keys.REVIEW_USAGE_COUNT] ?: 0
            prefs[Keys.REVIEW_USAGE_COUNT] = cur + 1
        }
    }

    // --- AI Explorer temp block helpers ----------------------------------

    private fun aiTempBlockKey(pkg: String) =
        androidx.datastore.preferences.core.longPreferencesKey("ai_temp_block_$pkg")

    private fun aiBlockTimestampsKey(pkg: String) =
        androidx.datastore.preferences.core.stringPreferencesKey("ai_block_ts_$pkg")

    suspend fun getAiTempBlockUntil(pkg: String): Long =
        context.settingsStore.data.first()[aiTempBlockKey(pkg)] ?: 0L

    suspend fun setAiTempBlockUntil(pkg: String, until: Long) {
        context.settingsStore.edit { it[aiTempBlockKey(pkg)] = until }
    }

    /**
     * Returns the list of recent AI-block timestamps for [pkg]. Stored as
     * a comma-separated long string in DataStore (which doesn't support
     * list-typed values out of the box).
     */
    suspend fun getAiBlockTimestamps(pkg: String): List<Long> {
        val raw = context.settingsStore.data.first()[aiBlockTimestampsKey(pkg)] ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.toLongOrNull() }
    }

    suspend fun setAiBlockTimestamps(pkg: String, timestamps: List<Long>) {
        val encoded = timestamps.joinToString(",")
        context.settingsStore.edit { it[aiBlockTimestampsKey(pkg)] = encoded }
    }

    /**
     * Enumerates the package names that have a non-zero temp-block key in
     * DataStore. Used by [AiBlockTracker] on startup to rebuild the
     * in-memory map.
     */
    suspend fun getAllAiTempBlockedPackages(): List<String> {
        val prefs = context.settingsStore.data.first()
        return prefs.asMap().entries.mapNotNull { (key, value) ->
            val name = key.name
            if (name.startsWith("ai_temp_block_") && (value as? Long ?: 0L) > 0L) {
                name.removePrefix("ai_temp_block_")
            } else null
        }
    }

    companion object {
        fun calculateXp(blockCount: Int): Int = blockCount * 10
        fun checkLevelUp(xp: Int): Int {
            var level = 1; var xpNeeded = 100; var totalXp = 0
            while (totalXp + xpNeeded <= xp) { totalXp += xpNeeded; level++; xpNeeded = (xpNeeded * 1.5).toInt() }
            return level
        }

        /**
         * Deactivation delay options (in days). 0 = no delay (instant off).
         * 30 is used as the "1 month" approximation.
         */
        val DEACTIVATION_DELAY_OPTIONS_DAYS = listOf(0, 2, 7, 15, 30)

        /**
         * Returns the number of full days the shield has been continuously active.
         * Returns 0 if the shield is off or `activatedAt` is zero.
         */
        fun calculateDaysActive(activatedAt: Long, now: Long = System.currentTimeMillis()): Int {
            if (activatedAt <= 0L) return 0
            val activatedCal = Calendar.getInstance().apply { timeInMillis = activatedAt }
            val nowCal = Calendar.getInstance().apply { timeInMillis = now }
            val activatedDay = stripTime(activatedCal)
            val nowDay = stripTime(nowCal)
            val diffMs = nowDay - activatedDay
            return (diffMs / 86_400_000L).toInt().coerceAtLeast(0)
        }

        private fun stripTime(cal: Calendar): Long {
            val c = cal.clone() as Calendar
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
    }
}
