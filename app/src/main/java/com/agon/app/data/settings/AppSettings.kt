package com.agon.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.settingsStore by preferencesDataStore(name = "app_settings")

class AppSettings(private val context: Context) {
    val encryptedPrefs: EncryptedPrefs = EncryptedPrefs(context)

    @Volatile private var shieldActiveCache: Boolean = false
    @Volatile private var trialModeCache: Boolean = false

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val SHIELD_ACTIVE = booleanPreferencesKey("shield_active")
        val SHIELD_ACTIVATED_AT = longPreferencesKey("shield_activated_at")
        val TRIAL_MODE = booleanPreferencesKey("trial_mode")
        val TEST_MODE = booleanPreferencesKey("test_mode")
        val DEACTIVATION_DELAY_DAYS = intPreferencesKey("deactivation_delay_days")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val STREAK_COUNT = intPreferencesKey("streak_count")
        val LONGEST_STREAK = intPreferencesKey("longest_streak")
        val LAST_ACTIVE_DATE = longPreferencesKey("last_active_date")
        val LAST_SHIELD_ENABLED_DAY = longPreferencesKey("last_shield_enabled_day")

        val YOUTUBE_MODE = stringPreferencesKey("youtube_mode")
        val SHORTS_BLOCK_ACTION = stringPreferencesKey("shorts_block_action")

        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val STRICT_MODE_COOLDOWN_END_AT = longPreferencesKey("strict_mode_cooldown_end_at")

        val PARTNER_CONTACT = stringPreferencesKey("partner_contact")
        val PARTNER_CONTACT_METHOD = stringPreferencesKey("partner_contact_method")
        val PERM_ACCESSIBILITY = booleanPreferencesKey("perm_accessibility")
        val DEFAULTS_SEEDED = booleanPreferencesKey("defaults_seeded")

        val BLOCKED_APPS = stringPreferencesKey("blocked_apps")
        val BLOCKED_WEBSITES = stringPreferencesKey("blocked_websites")
        val BLOCKED_KEYWORDS = stringPreferencesKey("blocked_keywords")

        val WHITELIST_APPS = stringPreferencesKey("whitelist_apps")
        val WHITELIST_WEBSITES = stringPreferencesKey("whitelist_websites")

        val PORN_BLOCKER_ENABLED = booleanPreferencesKey("porn_blocker_enabled")
        val AI_EXPLORER_ENABLED = booleanPreferencesKey("ai_explorer_enabled")
        val UNINSTALL_PROTECTION_ENABLED = booleanPreferencesKey("uninstall_protection_enabled")

        val INSTAGRAM_BLOCKED = booleanPreferencesKey("instagram_blocked")
        val SNAPCHAT_BLOCKED = booleanPreferencesKey("snapchat_blocked")
        val TWITTER_BLOCKED = booleanPreferencesKey("twitter_blocked")
        val TIKTOK_BLOCKED = booleanPreferencesKey("tiktok_blocked")
        val FACEBOOK_MODE = stringPreferencesKey("facebook_mode")
    }

    val onboardingCompleteFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }
    val shieldActiveFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SHIELD_ACTIVE] ?: false }
    val shieldActivatedAtFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.SHIELD_ACTIVATED_AT] ?: 0L }
    val trialModeFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.TRIAL_MODE] ?: false }
    val testModeFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.TEST_MODE] ?: false }
    val deactivationDelayFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.DEACTIVATION_DELAY_DAYS] ?: 0 }
    val profileNameFlow: Flow<String> = context.settingsStore.data.map { it[Keys.PROFILE_NAME] ?: "" }
    val pinHashFlow: Flow<String> = encryptedPrefs.pinHashFlow
    val streakCountFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.STREAK_COUNT] ?: 0 }
    val longestStreakFlow: Flow<Int> = context.settingsStore.data.map { it[Keys.LONGEST_STREAK] ?: 0 }
    val lastActiveDateFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.LAST_ACTIVE_DATE] ?: 0L }
    val lastShieldEnabledDayFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.LAST_SHIELD_ENABLED_DAY] ?: 0L }

    val youtubeModeFlow: Flow<String> = context.settingsStore.data.map { it[Keys.YOUTUBE_MODE] ?: "off" }
    val shortsBlockActionFlow: Flow<String> = context.settingsStore.data.map { it[Keys.SHORTS_BLOCK_ACTION] ?: "redirect" }

    val strictModeFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.STRICT_MODE] ?: false }
    val strictModeCooldownEndAtFlow: Flow<Long> = context.settingsStore.data.map { it[Keys.STRICT_MODE_COOLDOWN_END_AT] ?: 0L }

    val permAccessibilityFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PERM_ACCESSIBILITY] ?: false }

    val blockedAppsFlow: Flow<Set<String>> = context.settingsStore.data.map { prefs ->
        val raw = prefs[Keys.BLOCKED_APPS] ?: "[]"
        try { Json.decodeFromString<Set<String>>(raw) } catch (_: Exception) { emptySet() }
    }
    val blockedWebsitesFlow: Flow<Set<String>> = context.settingsStore.data.map { prefs ->
        val raw = prefs[Keys.BLOCKED_WEBSITES] ?: "[]"
        try { Json.decodeFromString<Set<String>>(raw) } catch (_: Exception) { emptySet() }
    }
    val blockedKeywordsFlow: Flow<Set<String>> = context.settingsStore.data.map { prefs ->
        val raw = prefs[Keys.BLOCKED_KEYWORDS] ?: "[]"
        try { Json.decodeFromString<Set<String>>(raw) } catch (_: Exception) { emptySet() }
    }

    val whitelistAppsFlow: Flow<Set<String>> = context.settingsStore.data.map { prefs ->
        val raw = prefs[Keys.WHITELIST_APPS] ?: "[]"
        try { Json.decodeFromString<Set<String>>(raw) } catch (_: Exception) { emptySet() }
    }
    val whitelistWebsitesFlow: Flow<Set<String>> = context.settingsStore.data.map { prefs ->
        val raw = prefs[Keys.WHITELIST_WEBSITES] ?: "[]"
        try { Json.decodeFromString<Set<String>>(raw) } catch (_: Exception) { emptySet() }
    }

    val pornBlockerEnabledFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.PORN_BLOCKER_ENABLED] ?: false }
    val aiExplorerEnabledFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.AI_EXPLORER_ENABLED] ?: false }
    val uninstallProtectionEnabledFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.UNINSTALL_PROTECTION_ENABLED] ?: false }

    val instagramBlockedFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.INSTAGRAM_BLOCKED] ?: false }
    val snapchatBlockedFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.SNAPCHAT_BLOCKED] ?: false }
    val twitterBlockedFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.TWITTER_BLOCKED] ?: false }
    val tiktokBlockedFlow: Flow<Boolean> = context.settingsStore.data.map { it[Keys.TIKTOK_BLOCKED] ?: false }
    val facebookModeFlow: Flow<String> = context.settingsStore.data.map { it[Keys.FACEBOOK_MODE] ?: "off" }

    suspend fun setBlockedApps(apps: Set<String>) {
        context.settingsStore.edit { it[Keys.BLOCKED_APPS] = Json.encodeToString(apps) }
    }
    suspend fun setBlockedWebsites(websites: Set<String>) {
        context.settingsStore.edit { it[Keys.BLOCKED_WEBSITES] = Json.encodeToString(websites) }
    }
    suspend fun setBlockedKeywords(keywords: Set<String>) {
        context.settingsStore.edit { it[Keys.BLOCKED_KEYWORDS] = Json.encodeToString(keywords) }
    }

    suspend fun setWhitelistApps(apps: Set<String>) {
        context.settingsStore.edit { it[Keys.WHITELIST_APPS] = Json.encodeToString(apps) }
    }
    suspend fun setWhitelistWebsites(websites: Set<String>) {
        context.settingsStore.edit { it[Keys.WHITELIST_WEBSITES] = Json.encodeToString(websites) }
    }

    suspend fun setPornBlockerEnabled(v: Boolean) { context.settingsStore.edit { it[Keys.PORN_BLOCKER_ENABLED] = v } }
    suspend fun setAiExplorerEnabled(v: Boolean) { context.settingsStore.edit { it[Keys.AI_EXPLORER_ENABLED] = v } }
    suspend fun setUninstallProtectionEnabled(v: Boolean) { context.settingsStore.edit { it[Keys.UNINSTALL_PROTECTION_ENABLED] = v } }

    suspend fun setInstagramBlocked(v: Boolean) { context.settingsStore.edit { it[Keys.INSTAGRAM_BLOCKED] = v } }
    suspend fun setSnapchatBlocked(v: Boolean) { context.settingsStore.edit { it[Keys.SNAPCHAT_BLOCKED] = v } }
    suspend fun setTwitterBlocked(v: Boolean) { context.settingsStore.edit { it[Keys.TWITTER_BLOCKED] = v } }
    suspend fun setTiktokBlocked(v: Boolean) { context.settingsStore.edit { it[Keys.TIKTOK_BLOCKED] = v } }
    suspend fun setFacebookMode(v: String) { context.settingsStore.edit { it[Keys.FACEBOOK_MODE] = v } }

    suspend fun isAppBlocked(pkg: String): Boolean {
        val raw = context.settingsStore.data.first()[Keys.BLOCKED_APPS] ?: "[]"
        return try { Json.decodeFromString<Set<String>>(raw).contains(pkg) } catch (_: Exception) { false }
    }

    suspend fun setOnboardingComplete() { context.settingsStore.edit { it[Keys.ONBOARDING_COMPLETE] = true } }
    suspend fun setShieldActive(v: Boolean) {
        shieldActiveCache = v
        context.settingsStore.edit { it[Keys.SHIELD_ACTIVE] = v }
    }
    suspend fun setShieldActivatedAt(v: Long) { context.settingsStore.edit { it[Keys.SHIELD_ACTIVATED_AT] = v } }
    suspend fun setTrialMode(v: Boolean) {
        trialModeCache = v
        context.settingsStore.edit { it[Keys.TRIAL_MODE] = v }
    }
    suspend fun setTestMode(v: Boolean) { context.settingsStore.edit { it[Keys.TEST_MODE] = v } }
    suspend fun setDeactivationDelay(v: Int) { context.settingsStore.edit { it[Keys.DEACTIVATION_DELAY_DAYS] = v } }
    suspend fun setProfileName(v: String) { context.settingsStore.edit { it[Keys.PROFILE_NAME] = v } }
    suspend fun setPinHash(v: String) { encryptedPrefs.savePinHash(v) }
    suspend fun setStreakCount(v: Int) { context.settingsStore.edit { it[Keys.STREAK_COUNT] = v } }
    suspend fun setLongestStreak(v: Int) { context.settingsStore.edit { it[Keys.LONGEST_STREAK] = v } }
    suspend fun setLastActiveDate(v: Long) { context.settingsStore.edit { it[Keys.LAST_ACTIVE_DATE] = v } }
    suspend fun setLastShieldEnabledDay(v: Long) { context.settingsStore.edit { it[Keys.LAST_SHIELD_ENABLED_DAY] = v } }

    suspend fun setYoutubeMode(v: String) { context.settingsStore.edit { it[Keys.YOUTUBE_MODE] = v } }
    suspend fun setShortsBlockAction(v: String) { context.settingsStore.edit { it[Keys.SHORTS_BLOCK_ACTION] = v } }

    suspend fun setStrictMode(v: Boolean) { context.settingsStore.edit { it[Keys.STRICT_MODE] = v } }
    suspend fun setStrictModeCooldownEndAt(v: Long) { context.settingsStore.edit { it[Keys.STRICT_MODE_COOLDOWN_END_AT] = v } }

    suspend fun setPermAccessibility(v: Boolean) { context.settingsStore.edit { it[Keys.PERM_ACCESSIBILITY] = v } }

    suspend fun isDefaultsSeeded(): Boolean = context.settingsStore.data.first()[Keys.DEFAULTS_SEEDED] ?: false
    suspend fun setDefaultsSeeded(v: Boolean) { context.settingsStore.edit { it[Keys.DEFAULTS_SEEDED] = v } }

    suspend fun getPartnerContact(): String? = context.settingsStore.data.first()[Keys.PARTNER_CONTACT]
    suspend fun setPartnerContact(v: String) { context.settingsStore.edit { it[Keys.PARTNER_CONTACT] = v } }
    suspend fun getPartnerContactMethod(): String? = context.settingsStore.data.first()[Keys.PARTNER_CONTACT_METHOD]
    suspend fun setPartnerContactMethod(v: String) { context.settingsStore.edit { it[Keys.PARTNER_CONTACT_METHOD] = v } }

    suspend fun isOnboardingComplete(): Boolean = context.settingsStore.data.first()[Keys.ONBOARDING_COMPLETE] ?: false
    suspend fun isShieldActive(): Boolean = context.settingsStore.data.first()[Keys.SHIELD_ACTIVE] ?: false
    fun isShieldActiveSync(): Boolean = shieldActiveCache
    fun isTrialModeSync(): Boolean = trialModeCache

    suspend fun warmShieldCache() {
        val prefs = context.settingsStore.data.first()
        shieldActiveCache = prefs[Keys.SHIELD_ACTIVE] ?: false
        trialModeCache = prefs[Keys.TRIAL_MODE] ?: false
    }
    suspend fun getShieldActivatedAt(): Long = context.settingsStore.data.first()[Keys.SHIELD_ACTIVATED_AT] ?: 0L
    suspend fun hasPin(): Boolean = encryptedPrefs.hasPin()
    suspend fun getDeactivationDelay(): Int = context.settingsStore.data.first()[Keys.DEACTIVATION_DELAY_DAYS] ?: 0
    suspend fun getProfileName(): String = context.settingsStore.data.first()[Keys.PROFILE_NAME] ?: ""
    suspend fun getPinHash(): String = encryptedPrefs.getPinHash()
    suspend fun getYoutubeMode(): String = context.settingsStore.data.first()[Keys.YOUTUBE_MODE] ?: "off"
    suspend fun isStrictMode(): Boolean = context.settingsStore.data.first()[Keys.STRICT_MODE] ?: false
    suspend fun isStrictModeCooldownActive(): Boolean {
        val end = context.settingsStore.data.first()[Keys.STRICT_MODE_COOLDOWN_END_AT] ?: 0L
        return end > System.currentTimeMillis()
    }

    companion object {
        val DEACTIVATION_DELAY_OPTIONS_DAYS = listOf(0, 2, 7, 15, 30)

        fun calculateDaysActive(activatedAt: Long, now: Long = System.currentTimeMillis()): Int {
            if (activatedAt <= 0L) return 0
            val activatedCal = java.util.Calendar.getInstance().apply { timeInMillis = activatedAt }
            val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = now }
            val activatedDay = stripTime(activatedCal)
            val nowDay = stripTime(nowCal)
            val diffMs = nowDay - activatedDay
            return (diffMs / 86_400_000L).toInt().coerceAtLeast(0)
        }

        private fun stripTime(cal: java.util.Calendar): Long {
            val c = cal.clone() as java.util.Calendar
            c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
            c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
    }
}
