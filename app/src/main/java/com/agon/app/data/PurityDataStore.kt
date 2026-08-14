package com.agon.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single shared DataStore instance for the whole process — used by both the
 * UI (MainViewModel) and the protection engine (AccessibilityService), so
 * settings changes propagate to the brain instantly via the data Flow.
 */
val Context.purityDataStore by preferencesDataStore(name = "purity_prefs")

object PrefKeys {
    val SHIELD_ACTIVE = booleanPreferencesKey("shield_active")
    val SHIELD_SINCE = longPreferencesKey("shield_since")
    val CONTROL_SECONDS = longPreferencesKey("control_seconds")
    val DELAY_INDEX = intPreferencesKey("delay_index")
    val STREAK_START = longPreferencesKey("streak_start")
    val RELAPSES = intPreferencesKey("relapses")
    val LONGEST = longPreferencesKey("longest_seconds")
    val URGES = intPreferencesKey("urges")
    val QUOTE = intPreferencesKey("quote_index")
    val ENGINES = stringPreferencesKey("engines_json")
    val APPS = stringPreferencesKey("apps_json")
    val JOURNAL = stringPreferencesKey("journal_json")
    val DAILY_REMINDER = booleanPreferencesKey("daily_reminder")
    val FILTERS = stringPreferencesKey("filters_json")
    val AI_FILTER = booleanPreferencesKey("ai_filter")
    val UNINSTALL_GUARD = booleanPreferencesKey("uninstall_guard")
    val BLOCKS_COUNT = intPreferencesKey("blocks_count")

    // Legacy single lists (kept for migration).
    val BLACKLIST = stringPreferencesKey("blacklist_json")
    val WHITELIST = stringPreferencesKey("whitelist_json")

    // Categorised lists.
    val BLACK_WORDS = stringPreferencesKey("black_words_json")
    val BLACK_SITES = stringPreferencesKey("black_sites_json")
    val BLACK_APPS = stringPreferencesKey("black_apps_json")
    val WHITE_WORDS = stringPreferencesKey("white_words_json")
    val WHITE_SITES = stringPreferencesKey("white_sites_json")
    val WHITE_APPS = stringPreferencesKey("white_apps_json")
}
