package com.agon.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "guardian_prefs")

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

            blacklistKeywords = prefs[BLACKLIST_KEYWORDS]?.split(",")?.filter { it.isNotEmpty() }
                ?: listOf("porn","xxx","sex","nude","naked","hentai","adult","onlyfans",
                    "escort","cam","masturbat","erotic","lewd","nsfw","rule34",
                    "milf","anal","blowjob","hardcore","softcore",
                    "اباحية","جنس","عري","سكس","افلام ساخنة","اثارة جنسية"),
            blacklistWebsites = prefs[BLACKLIST_WEBSITES]?.split(",")?.filter { it.isNotEmpty() }
                ?: listOf("pornhub.com","xvideos.com","xnxx.com","redtube.com","youporn.com",
                    "xhamster.com","tube8.com","spankbang.com","eporner.com","tnaflix.com",
                    "drtuber.com","slutload.com","beeg.com","hclips.com",
                    "nhentai.net","hanime.tv","hentaihaven.xxx","gelbooru.com","rule34.xxx",
                    "onlyfans.com","chaturbate.com","livejasmin.com","cam4.com",
                    "myfreecams.com","bongacams.com"),
            blacklistApps = prefs[BLACKLIST_APPS]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
            
            whitelistKeywords = prefs[WHITELIST_KEYWORDS]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
            whitelistWebsites = prefs[WHITELIST_WEBSITES]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
            whitelistApps = prefs[WHITELIST_APPS]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
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
            val str = list.joinToString(",")
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
}
