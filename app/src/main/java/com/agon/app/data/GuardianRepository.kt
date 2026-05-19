package com.agon.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray

val Context.dataStore by preferencesDataStore(name = "guardian_prefs")
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class GuardianRepository(private val context: Context) {
    private val IS_SHIELD_ACTIVE = booleanPreferencesKey("is_shield_active")
    private val IS_TRIAL_MODE_ACTIVE = booleanPreferencesKey("is_trial_mode_active")
    private val DEACTIVATION_DELAY = longPreferencesKey("deactivation_delay")
    private val COUNTDOWN_END_TIME = longPreferencesKey("countdown_end_time")
    private val SHIELD_ACTIVATED_AT = longPreferencesKey("shield_activated_at")
    private val BLOCKS_COUNT = intPreferencesKey("blocks_count")

    private val ACCESSIBILITY_GRANTED = booleanPreferencesKey("accessibility_granted")
    private val VPN_GRANTED = booleanPreferencesKey("vpn_granted")
    private val DEVICE_ADMIN_GRANTED = booleanPreferencesKey("device_admin_granted")
    private val OVERLAY_GRANTED = booleanPreferencesKey("overlay_granted")
    private val USAGE_ACCESS_GRANTED = booleanPreferencesKey("usage_access_granted")

    private val INSTAGRAM_BLOCKED = booleanPreferencesKey("instagram_blocked")
    private val SNAPCHAT_BLOCKED = booleanPreferencesKey("snapchat_blocked")
    private val TWITTER_BLOCKED = booleanPreferencesKey("twitter_blocked")
    private val TIKTOK_BLOCKED = booleanPreferencesKey("tiktok_blocked")
    private val YOUTUBE_MODE = stringPreferencesKey("youtube_mode")
    private val FACEBOOK_MODE = stringPreferencesKey("facebook_mode")
    private val PORN_BLOCKER_ACTIVE = booleanPreferencesKey("porn_blocker_active")
    private val AI_EXPLORER_ACTIVE = booleanPreferencesKey("ai_explorer_active")
    private val UNINSTALL_PROTECTION = booleanPreferencesKey("uninstall_protection")

    private val BLACKLIST_KEYWORDS = stringPreferencesKey("blacklist_keywords")
    private val BLACKLIST_WEBSITES = stringPreferencesKey("blacklist_websites")
    private val BLACKLIST_APPS = stringPreferencesKey("blacklist_apps")

    private val WHITELIST_KEYWORDS = stringPreferencesKey("whitelist_keywords")
    private val WHITELIST_WEBSITES = stringPreferencesKey("whitelist_websites")
    private val WHITELIST_APPS = stringPreferencesKey("whitelist_apps")

    private val PIN_CODE = stringPreferencesKey("pin_code")
    private val APP_UNLOCKED = booleanPreferencesKey("app_unlocked")
    private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    private val PROFILE_NAME = stringPreferencesKey("profile_name")
    private val INSTALL_TIMESTAMP = longPreferencesKey("install_timestamp")
    private val SCHEDULE_RULES = stringPreferencesKey("schedule_rules")
    private val DAILY_TIME_LIMITS = stringPreferencesKey("daily_time_limits")
    private val BLOCK_EVENTS = stringPreferencesKey("block_events")
    private val LAST_DAILY_RESET = stringPreferencesKey("last_daily_reset")

    val guardianStateFlow: Flow<GuardianState> = context.dataStore.data.map { prefs ->
        GuardianState(
            isShieldActive = prefs[IS_SHIELD_ACTIVE] ?: false,
            isTrialModeActive = prefs[IS_TRIAL_MODE_ACTIVE] ?: false,
            deactivationDelayMinutes = prefs[DEACTIVATION_DELAY] ?: (7 * 24 * 60L),
            countdownEndTime = prefs[COUNTDOWN_END_TIME],
            shieldActivatedAt = prefs[SHIELD_ACTIVATED_AT],
            blocksCount = prefs[BLOCKS_COUNT] ?: 0,

            accessibilityGranted = prefs[ACCESSIBILITY_GRANTED] ?: false,
            vpnGranted = prefs[VPN_GRANTED] ?: false,
            deviceAdminGranted = prefs[DEVICE_ADMIN_GRANTED] ?: false,
            overlayGranted = prefs[OVERLAY_GRANTED] ?: false,
            usageAccessGranted = prefs[USAGE_ACCESS_GRANTED] ?: false,

            instagramBlocked = prefs[INSTAGRAM_BLOCKED] ?: false,
            snapchatBlocked = prefs[SNAPCHAT_BLOCKED] ?: false,
            twitterBlocked = prefs[TWITTER_BLOCKED] ?: false,
            tiktokBlocked = prefs[TIKTOK_BLOCKED] ?: false,
            youtubeMode = prefs[YOUTUBE_MODE] ?: "off",
            facebookMode = prefs[FACEBOOK_MODE] ?: "off",
            pornBlockerActive = prefs[PORN_BLOCKER_ACTIVE] ?: false,
            aiExplorerActive = prefs[AI_EXPLORER_ACTIVE] ?: false,
            uninstallProtectionActive = prefs[UNINSTALL_PROTECTION] ?: false,

            blacklistKeywords = prefs[BLACKLIST_KEYWORDS]?.let { parseJsonList(it) }
                ?: defaultBlacklistKeywords,
            blacklistWebsites = prefs[BLACKLIST_WEBSITES]?.let { parseJsonList(it) }
                ?: defaultBlacklistWebsites,
            blacklistApps = prefs[BLACKLIST_APPS]?.let { parseJsonList(it) } ?: emptyList(),

            whitelistKeywords = prefs[WHITELIST_KEYWORDS]?.let { parseJsonList(it) } ?: emptyList(),
            whitelistWebsites = prefs[WHITELIST_WEBSITES]?.let { parseJsonList(it) } ?: emptyList(),
            whitelistApps = prefs[WHITELIST_APPS]?.let { parseJsonList(it) } ?: emptyList(),

            pinCode = prefs[PIN_CODE],
            appUnlocked = prefs[APP_UNLOCKED] ?: false,
            onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false,
            profileName = prefs[PROFILE_NAME] ?: "",
            installTimestamp = prefs[INSTALL_TIMESTAMP],

            scheduleRules = prefs[SCHEDULE_RULES]?.let {
                try { json.decodeFromString<List<ScheduleRule>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList(),

            dailyTimeLimits = prefs[DAILY_TIME_LIMITS]?.let {
                try { json.decodeFromString<List<DailyTimeLimit>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList(),

            blockEvents = prefs[BLOCK_EVENTS]?.let {
                try { json.decodeFromString<List<BlockEvent>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
        )
    }

    suspend fun updateShieldActive(active: Boolean) { context.dataStore.edit { it[IS_SHIELD_ACTIVE] = active } }
    suspend fun updateTrialMode(active: Boolean) { context.dataStore.edit { it[IS_TRIAL_MODE_ACTIVE] = active } }
    suspend fun updateDeactivationDelay(minutes: Long) { context.dataStore.edit { it[DEACTIVATION_DELAY] = minutes } }
    suspend fun updateCountdownEndTime(time: Long?) { context.dataStore.edit { if (time == null) it.remove(COUNTDOWN_END_TIME) else it[COUNTDOWN_END_TIME] = time } }
    suspend fun updateShieldActivatedAt(time: Long?) { context.dataStore.edit { if (time == null) it.remove(SHIELD_ACTIVATED_AT) else it[SHIELD_ACTIVATED_AT] = time } }
    suspend fun updateBlocksCount(count: Int) { context.dataStore.edit { it[BLOCKS_COUNT] = count } }

    suspend fun updatePermission(key: String, granted: Boolean) {
        context.dataStore.edit {
            when(key) {
                "accessibility" -> it[ACCESSIBILITY_GRANTED] = granted
                "vpn" -> it[VPN_GRANTED] = granted
                "device_admin" -> it[DEVICE_ADMIN_GRANTED] = granted
                "overlay" -> it[OVERLAY_GRANTED] = granted
                "usage" -> it[USAGE_ACCESS_GRANTED] = granted
            }
        }
    }

    suspend fun updateInstagramBlocked(blocked: Boolean) { context.dataStore.edit { it[INSTAGRAM_BLOCKED] = blocked } }
    suspend fun updateSnapchatBlocked(blocked: Boolean) { context.dataStore.edit { it[SNAPCHAT_BLOCKED] = blocked } }
    suspend fun updateTwitterBlocked(blocked: Boolean) { context.dataStore.edit { it[TWITTER_BLOCKED] = blocked } }
    suspend fun updateTiktokBlocked(blocked: Boolean) { context.dataStore.edit { it[TIKTOK_BLOCKED] = blocked } }
    suspend fun updateYoutubeMode(mode: String) { context.dataStore.edit { it[YOUTUBE_MODE] = mode } }
    suspend fun updateFacebookMode(mode: String) { context.dataStore.edit { it[FACEBOOK_MODE] = mode } }
    suspend fun updatePornBlocker(active: Boolean) { context.dataStore.edit { it[PORN_BLOCKER_ACTIVE] = active } }
    suspend fun updateAiExplorer(active: Boolean) { context.dataStore.edit { it[AI_EXPLORER_ACTIVE] = active } }
    suspend fun updateUninstallProtection(active: Boolean) { context.dataStore.edit { it[UNINSTALL_PROTECTION] = active } }

    suspend fun updateList(listType: String, category: String, list: List<String>) {
        context.dataStore.edit { prefs ->
            val str = JSONArray(list).toString()
            when ("${listType}_$category") {
                "blacklist_keywords" -> prefs[BLACKLIST_KEYWORDS] = str
                "blacklist_websites" -> prefs[BLACKLIST_WEBSITES] = str
                "blacklist_apps" -> prefs[BLACKLIST_APPS] = str
                "whitelist_keywords" -> prefs[WHITELIST_KEYWORDS] = str
                "whitelist_websites" -> prefs[WHITELIST_WEBSITES] = str
                "whitelist_apps" -> prefs[WHITELIST_APPS] = str
            }
        }
    }

    // F4: PIN
    suspend fun updatePinCode(pin: String?) { context.dataStore.edit { if (pin == null) it.remove(PIN_CODE) else it[PIN_CODE] = pin } }
    suspend fun updateAppUnlocked(unlocked: Boolean) { context.dataStore.edit { it[APP_UNLOCKED] = unlocked } }

    // F1: Onboarding + Profile
    suspend fun updateOnboardingCompleted(completed: Boolean) { context.dataStore.edit { it[ONBOARDING_COMPLETED] = completed } }
    suspend fun updateProfileName(name: String) { context.dataStore.edit { it[PROFILE_NAME] = name } }

    // F6: Install timestamp
    suspend fun updateInstallTimestamp(time: Long) { context.dataStore.edit { it[INSTALL_TIMESTAMP] = time } }

    // F2: Schedule rules
    suspend fun updateScheduleRules(rules: List<ScheduleRule>) {
        context.dataStore.edit { it[SCHEDULE_RULES] = json.encodeToString(rules) }
    }

    // F3: Daily time limits
    suspend fun updateDailyTimeLimits(limits: List<DailyTimeLimit>) {
        context.dataStore.edit { it[DAILY_TIME_LIMITS] = json.encodeToString(limits) }
    }

    // F5: Block events
    suspend fun addBlockEvent(event: BlockEvent) {
        context.dataStore.edit { prefs ->
            val existing = prefs[BLOCK_EVENTS]?.let {
                try { json.decodeFromString<List<BlockEvent>>(it) } catch (_: Exception) { emptyList<BlockEvent>() }
            } ?: emptyList()
            val updated = (existing + event).takeLast(1000)
            prefs[BLOCK_EVENTS] = json.encodeToString(updated)
        }
    }

    suspend fun clearBlockEvents() { context.dataStore.edit { it.remove(BLOCK_EVENTS) } }

    suspend fun getBlockEvents(): List<BlockEvent> {
        return context.dataStore.data.map { prefs ->
            prefs[BLOCK_EVENTS]?.let {
                try { json.decodeFromString<List<BlockEvent>>(it) } catch (_: Exception) { emptyList() }
            } ?: emptyList()
        }.let { flow ->
            var result = emptyList<BlockEvent>()
            flow.collect { result = it }
            result
        }
    }

    suspend fun getLastDailyResetDate(): String? {
        return context.dataStore.data.map { it[LAST_DAILY_RESET] }.let { flow ->
            var result: String? = null
            flow.collect { result = it }
            result
        }
    }

    suspend fun updateLastDailyResetDate(date: String) {
        context.dataStore.edit { it[LAST_DAILY_RESET] = date }
    }

    private fun parseJsonList(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            json.split(",").filter { it.isNotEmpty() }
        }
    }
}