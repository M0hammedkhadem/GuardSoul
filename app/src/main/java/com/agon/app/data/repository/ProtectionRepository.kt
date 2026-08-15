package com.agon.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.agon.app.data.AppBlockState
import com.agon.app.data.PrefKeys
import com.agon.app.engine.EngineSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Single source of truth for every protection-related key in [PrefKeys].
 *
 * Wraps ALL DataStore reads/writes for: shield state (SHIELD_ACTIVE,
 * SHIELD_SINCE, PENDING_STOP_AT, CONTROL_SECONDS, DELAY_INDEX), ENGINES,
 * APPS, FILTERS, the six categorised lists (black/white × words/sites/apps)
 * plus the streak/stats/preference scalars.
 *
 * JSON wire format is IDENTICAL to the legacy MainViewModel implementation
 * (same MapSerializer/ListSerializer instances), so data stored by previous
 * versions keeps loading unchanged.
 */
class ProtectionRepository(private val ds: DataStore<Preferences>) {

    private val json = Json { ignoreUnknownKeys = true }
    private val enginesSer = MapSerializer(String.serializer(), Boolean.serializer())
    private val appsSer = MapSerializer(String.serializer(), AppBlockState.serializer())
    private val stringListSer = ListSerializer(String.serializer())

    /**
     * Typed view of everything stored. Fields are nullable: `null` means
     * "key absent" so callers can keep their in-memory defaults, matching
     * the legacy `p[key]?.let { ... }` semantics exactly.
     */
    data class Snapshot(
        val shieldActive: Boolean?,
        val shieldSince: Long?,
        val pendingStopAt: Long?,
        val controlSeconds: Long?,
        val delayIndex: Int?,
        val streakStart: Long?,
        val relapses: Int?,
        val longestSeconds: Long?,
        val urgesResisted: Int?,
        val quoteIndex: Int?,
        val dailyReminder: Boolean?,
        val aiImageFilter: Boolean?,
        val uninstallGuard: Boolean?,
        val keywordContinue: Boolean?,
        val nsfwBlur: Boolean?,
        val searchEngines: Map<String, Boolean>?,
        val appBlocks: Map<String, AppBlockState>?,
        val contentFilters: Map<String, Boolean>?,
        val blackWords: List<String>?,
        val blackSites: List<String>?,
        val blackApps: List<String>?,
        val whiteWords: List<String>?,
        val whiteSites: List<String>?,
        val whiteApps: List<String>?,
    )

    // ---------- Reads ----------

    /**
     * Unified live stream of every protection setting in one emission —
     * intended for both the UI layer and (in a later step) the
     * AccessibilityService, replacing its hand-rolled decoding.
     */
    val settingsFlow: Flow<Snapshot> = ds.data.map { it.toSnapshot() }

    /** Live count of blocks executed by the protection service. */
    val blocksCountFlow: Flow<Int> = ds.data.map { it[PrefKeys.BLOCKS_COUNT] ?: 0 }

    /**
     * Ready-to-consume settings stream for the protection engine
     * (AccessibilityService). Produces values IDENTICAL to the legacy
     * hand-rolled decode block in ProtectionAccessibilityService: the same
     * defaults apply when a key is absent (?: false / emptyMap / emptyList),
     * including the legacy single-list fallbacks handled by [Snapshot].
     */
    val engineSettingsFlow: Flow<EngineSettings> = settingsFlow.map { s ->
        EngineSettings(
            shieldActive = s.shieldActive ?: false,
            aiImageFilter = s.aiImageFilter ?: false,
            uninstallGuard = s.uninstallGuard ?: false,
            appBlocks = s.appBlocks ?: emptyMap(),
            searchEngines = s.searchEngines ?: emptyMap(),
            contentFilters = s.contentFilters ?: emptyMap(),
            blackWords = s.blackWords ?: emptyList(),
            blackSites = s.blackSites ?: emptyList(),
            blackApps = s.blackApps ?: emptyList(),
            whiteWords = s.whiteWords ?: emptyList(),
            whiteSites = s.whiteSites ?: emptyList(),
            whiteApps = s.whiteApps ?: emptyList(),
            keywordContinueOption = s.keywordContinue ?: false,
            nsfwBlurMode = s.nsfwBlur ?: false,
        )
    }

    /** Atomic increment used by the service after every executed block. */
    suspend fun incrementBlocksCount() {
        ds.edit { it[PrefKeys.BLOCKS_COUNT] = (it[PrefKeys.BLOCKS_COUNT] ?: 0) + 1 }
    }

    /** One-shot read used for the initial load. */
    suspend fun snapshot(): Snapshot = ds.data.first().toSnapshot()

    private fun Preferences.toSnapshot(): Snapshot = Snapshot(
        shieldActive = this[PrefKeys.SHIELD_ACTIVE],
        shieldSince = this[PrefKeys.SHIELD_SINCE],
        pendingStopAt = this[PrefKeys.PENDING_STOP_AT],
        controlSeconds = this[PrefKeys.CONTROL_SECONDS],
        delayIndex = this[PrefKeys.DELAY_INDEX],
        streakStart = this[PrefKeys.STREAK_START],
        relapses = this[PrefKeys.RELAPSES],
        longestSeconds = this[PrefKeys.LONGEST],
        urgesResisted = this[PrefKeys.URGES],
        quoteIndex = this[PrefKeys.QUOTE],
        dailyReminder = this[PrefKeys.DAILY_REMINDER],
        aiImageFilter = this[PrefKeys.AI_FILTER],
        uninstallGuard = this[PrefKeys.UNINSTALL_GUARD],
        keywordContinue = this[PrefKeys.KEYWORD_CONTINUE],
        nsfwBlur = this[PrefKeys.NSFW_BLUR],
        searchEngines = decodeEngines(this[PrefKeys.ENGINES]),
        appBlocks = decodeApps(this[PrefKeys.APPS]),
        contentFilters = decodeEngines(this[PrefKeys.FILTERS]),
        blackWords = decodeList(this[PrefKeys.BLACK_WORDS]),
        // Legacy single-list migration: fall back to the old keys.
        blackSites = decodeList(this[PrefKeys.BLACK_SITES] ?: this[PrefKeys.BLACKLIST]),
        blackApps = decodeList(this[PrefKeys.BLACK_APPS]),
        whiteWords = decodeList(this[PrefKeys.WHITE_WORDS]),
        whiteSites = decodeList(this[PrefKeys.WHITE_SITES] ?: this[PrefKeys.WHITELIST]),
        whiteApps = decodeList(this[PrefKeys.WHITE_APPS]),
    )

    private fun decodeEngines(raw: String?): Map<String, Boolean>? =
        raw?.let { s -> runCatching { json.decodeFromString(enginesSer, s) }.getOrNull() }

    private fun decodeApps(raw: String?): Map<String, AppBlockState>? =
        raw?.let { s -> runCatching { json.decodeFromString(appsSer, s) }.getOrNull() }

    private fun decodeList(raw: String?): List<String>? =
        raw?.let { s -> runCatching { json.decodeFromString(stringListSer, s) }.getOrNull() }

    // ---------- Writes (one edit per legacy persist block) ----------

    suspend fun persistShield(
        active: Boolean,
        since: Long,
        pendingStopAt: Long,
        controlSeconds: Long,
    ) {
        ds.edit {
            it[PrefKeys.SHIELD_ACTIVE] = active
            it[PrefKeys.SHIELD_SINCE] = since
            it[PrefKeys.PENDING_STOP_AT] = pendingStopAt
            it[PrefKeys.CONTROL_SECONDS] = controlSeconds
        }
    }

    suspend fun persistDelayIndex(index: Int) {
        ds.edit { it[PrefKeys.DELAY_INDEX] = index }
    }

    suspend fun persistStreakStart(value: Long) {
        ds.edit { it[PrefKeys.STREAK_START] = value }
    }

    suspend fun persistRelapse(streakStart: Long, relapses: Int, longestSeconds: Long) {
        ds.edit {
            it[PrefKeys.STREAK_START] = streakStart
            it[PrefKeys.RELAPSES] = relapses
            it[PrefKeys.LONGEST] = longestSeconds
        }
    }

    suspend fun persistUrges(value: Int) {
        ds.edit { it[PrefKeys.URGES] = value }
    }

    suspend fun persistQuoteIndex(value: Int) {
        ds.edit { it[PrefKeys.QUOTE] = value }
    }

    suspend fun persistDailyReminder(value: Boolean) {
        ds.edit { it[PrefKeys.DAILY_REMINDER] = value }
    }

    suspend fun persistAiImageFilter(value: Boolean) {
        ds.edit { it[PrefKeys.AI_FILTER] = value }
    }

    suspend fun persistUninstallGuard(value: Boolean) {
        ds.edit { it[PrefKeys.UNINSTALL_GUARD] = value }
    }

    suspend fun persistKeywordContinue(value: Boolean) {
        ds.edit { it[PrefKeys.KEYWORD_CONTINUE] = value }
    }

    suspend fun persistNsfwBlur(value: Boolean) {
        ds.edit { it[PrefKeys.NSFW_BLUR] = value }
    }

    /** Same three-key edit and same JSON encoding as the legacy code. */
    suspend fun persistProtection(
        engines: Map<String, Boolean>,
        apps: Map<String, AppBlockState>,
        filters: Map<String, Boolean>,
    ) {
        val e = json.encodeToString(enginesSer, engines)
        val a = json.encodeToString(appsSer, apps)
        val f = json.encodeToString(enginesSer, filters)
        ds.edit {
            it[PrefKeys.ENGINES] = e
            it[PrefKeys.APPS] = a
            it[PrefKeys.FILTERS] = f
        }
    }

    /** Same six-key edit and same JSON encoding as the legacy code. */
    suspend fun persistLists(
        blackWords: List<String>,
        blackSites: List<String>,
        blackApps: List<String>,
        whiteWords: List<String>,
        whiteSites: List<String>,
        whiteApps: List<String>,
    ) {
        val bw = json.encodeToString(stringListSer, blackWords)
        val bs = json.encodeToString(stringListSer, blackSites)
        val ba = json.encodeToString(stringListSer, blackApps)
        val ww = json.encodeToString(stringListSer, whiteWords)
        val ws = json.encodeToString(stringListSer, whiteSites)
        val wa = json.encodeToString(stringListSer, whiteApps)
        ds.edit {
            it[PrefKeys.BLACK_WORDS] = bw
            it[PrefKeys.BLACK_SITES] = bs
            it[PrefKeys.BLACK_APPS] = ba
            it[PrefKeys.WHITE_WORDS] = ww
            it[PrefKeys.WHITE_SITES] = ws
            it[PrefKeys.WHITE_APPS] = wa
        }
    }

    /** Full wipe keeping only the new streak start (legacy reset semantics). */
    suspend fun clearAllData(streakStart: Long) {
        ds.edit {
            it.clear()
            it[PrefKeys.STREAK_START] = streakStart
        }
    }
}
