package com.agon.app.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.data.AppBlockState
import com.agon.app.data.JournalEntry
import com.agon.app.data.ListCategory
import com.agon.app.data.PrefKeys as Keys
import com.agon.app.data.blockableApps
import com.agon.app.data.contentFilterList
import com.agon.app.data.defaultBlackSites
import com.agon.app.data.defaultBlackWords
import com.agon.app.data.defaultWhiteSites
import com.agon.app.data.delayOptions
import com.agon.app.data.purityDataStore
import com.agon.app.data.quotes
import com.agon.app.data.searchEngineNames
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * UI state holder.
 *
 * Shield lock ("smart" tamper protection): while the main shield is active,
 * every mutation that WEAKENS protection returns false and is rejected;
 * mutations that STRENGTHEN protection are always allowed.
 *  - Full block ON  -> cannot be downgraded to shorts-only or turned off.
 *  - Shorts ON      -> upgrading to full block is allowed (strengthening).
 *  - Engines/filters/AI/guard -> can be enabled, not disabled.
 *  - Blacklists     -> items can be added, not removed.
 *  - Whitelists     -> items can be removed, not added.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val ds = application.purityDataStore
    private val json = Json { ignoreUnknownKeys = true }

    private val enginesSer = MapSerializer(String.serializer(), Boolean.serializer())
    private val appsSer = MapSerializer(String.serializer(), AppBlockState.serializer())
    private val stringListSer = ListSerializer(String.serializer())
    private val journalSer = ListSerializer(JournalEntry.serializer())

    var loaded by mutableStateOf(false)
        private set

    // Shield / control
    var shieldActive by mutableStateOf(false)
        private set
    var shieldSince by mutableLongStateOf(0L)
        private set
    var accumulatedControlSeconds by mutableLongStateOf(0L)
        private set
    var delayIndex by mutableIntStateOf(0)
        private set

    // Streak
    var streakStart by mutableLongStateOf(System.currentTimeMillis())
        private set
    var relapses by mutableIntStateOf(0)
        private set
    var longestSeconds by mutableLongStateOf(0L)
        private set
    var urgesResisted by mutableIntStateOf(0)
        private set
    var quoteIndex by mutableIntStateOf(0)
        private set
    var dailyReminder by mutableStateOf(true)
        private set
    var aiImageFilter by mutableStateOf(false)
        private set
    var uninstallGuard by mutableStateOf(false)
        private set
    var blocksCount by mutableIntStateOf(0)
        private set

    val searchEngines = mutableStateMapOf<String, Boolean>()
    val contentFilters = mutableStateMapOf<String, Boolean>()
    val appBlocks = mutableStateMapOf<String, AppBlockState>()

    // Categorised lists
    val blackWords = mutableStateListOf<String>()
    val blackSites = mutableStateListOf<String>()
    val blackApps = mutableStateListOf<String>()
    val whiteWords = mutableStateListOf<String>()
    val whiteSites = mutableStateListOf<String>()
    val whiteApps = mutableStateListOf<String>()

    val journal = mutableStateListOf<JournalEntry>()

    init {
        applyDefaults()
        viewModelScope.launch { load() }
        // Live counter: the protection service increments this key on every block.
        viewModelScope.launch {
            ds.data.collect { p -> blocksCount = p[Keys.BLOCKS_COUNT] ?: 0 }
        }
    }

    private fun applyDefaults() {
        searchEngines.clear()
        searchEngineNames.forEach { searchEngines[it] = it == "Google" || it == "Bing" }
        contentFilters.clear()
        contentFilterList.forEach { contentFilters[it.key] = true }
        appBlocks.clear()
        blockableApps.forEach {
            appBlocks[it.id] = AppBlockState(fullBlock = it.id == "instagram" || it.id == "tiktok")
        }
        blackWords.clear(); blackWords.addAll(defaultBlackWords)
        blackSites.clear(); blackSites.addAll(defaultBlackSites)
        blackApps.clear()
        whiteWords.clear()
        whiteSites.clear(); whiteSites.addAll(defaultWhiteSites)
        whiteApps.clear()
    }

    private suspend fun load() {
        val p = ds.data.first()
        val savedStart = p[Keys.STREAK_START]
        if (savedStart != null) {
            streakStart = savedStart
        } else {
            persist { it[Keys.STREAK_START] = streakStart }
        }
        p[Keys.SHIELD_ACTIVE]?.let { shieldActive = it }
        p[Keys.SHIELD_SINCE]?.let { shieldSince = it }
        p[Keys.CONTROL_SECONDS]?.let { accumulatedControlSeconds = it }
        p[Keys.DELAY_INDEX]?.let { delayIndex = it }
        p[Keys.RELAPSES]?.let { relapses = it }
        p[Keys.LONGEST]?.let { longestSeconds = it }
        p[Keys.URGES]?.let { urgesResisted = it }
        p[Keys.QUOTE]?.let { quoteIndex = it }
        p[Keys.DAILY_REMINDER]?.let { dailyReminder = it }
        p[Keys.AI_FILTER]?.let { aiImageFilter = it }
        p[Keys.UNINSTALL_GUARD]?.let { uninstallGuard = it }
        p[Keys.FILTERS]?.let { s ->
            runCatching { json.decodeFromString(enginesSer, s) }.getOrNull()?.let { m ->
                contentFilters.clear(); contentFilters.putAll(m)
            }
        }
        p[Keys.ENGINES]?.let { s ->
            runCatching { json.decodeFromString(enginesSer, s) }.getOrNull()?.let { m ->
                searchEngines.clear(); searchEngines.putAll(m)
            }
        }
        p[Keys.APPS]?.let { s ->
            runCatching { json.decodeFromString(appsSer, s) }.getOrNull()?.let { m ->
                appBlocks.clear()
                // Only keep known apps; new catalog entries get defaults.
                blockableApps.forEach { app ->
                    appBlocks[app.id] = m[app.id] ?: AppBlockState()
                }
            }
        }
        loadList(p[Keys.BLACK_WORDS], blackWords)
        loadList(p[Keys.BLACK_SITES] ?: p[Keys.BLACKLIST], blackSites)
        loadList(p[Keys.BLACK_APPS], blackApps)
        loadList(p[Keys.WHITE_WORDS], whiteWords)
        loadList(p[Keys.WHITE_SITES] ?: p[Keys.WHITELIST], whiteSites)
        loadList(p[Keys.WHITE_APPS], whiteApps)
        p[Keys.JOURNAL]?.let { s ->
            runCatching { json.decodeFromString(journalSer, s) }.getOrNull()?.let { l ->
                journal.clear(); journal.addAll(l)
            }
        }
        loaded = true
    }

    private fun loadList(raw: String?, target: SnapshotStateList<String>) {
        raw ?: return
        runCatching { json.decodeFromString(stringListSer, raw) }.getOrNull()?.let { l ->
            target.clear(); target.addAll(l)
        }
    }

    private fun persist(block: (MutablePreferences) -> Unit) {
        viewModelScope.launch { ds.edit { block(it) } }
    }

    // ---------- Shield ----------

    fun toggleShield() {
        val now = System.currentTimeMillis()
        if (shieldActive) {
            accumulatedControlSeconds += (now - shieldSince) / 1000
            shieldActive = false
        } else {
            shieldSince = now
            shieldActive = true
        }
        persist {
            it[Keys.SHIELD_ACTIVE] = shieldActive
            it[Keys.SHIELD_SINCE] = shieldSince
            it[Keys.CONTROL_SECONDS] = accumulatedControlSeconds
        }
    }

    fun cycleDelay() {
        delayIndex = (delayIndex + 1) % delayOptions.size
        persist { it[Keys.DELAY_INDEX] = delayIndex }
    }

    fun controlSeconds(now: Long): Long =
        accumulatedControlSeconds + if (shieldActive) ((now - shieldSince) / 1000).coerceAtLeast(0) else 0L

    // ---------- Streak ----------

    fun registerRelapse() {
        val now = System.currentTimeMillis()
        val current = ((now - streakStart) / 1000).coerceAtLeast(0)
        if (current > longestSeconds) longestSeconds = current
        relapses += 1
        streakStart = now
        persist {
            it[Keys.STREAK_START] = streakStart
            it[Keys.RELAPSES] = relapses
            it[Keys.LONGEST] = longestSeconds
        }
    }

    fun resistUrge() {
        urgesResisted += 1
        persist { it[Keys.URGES] = urgesResisted }
    }

    fun nextQuote() {
        quoteIndex = (quoteIndex + 1) % quotes.size
        persist { it[Keys.QUOTE] = quoteIndex }
    }

    fun updateDailyReminder(value: Boolean) {
        dailyReminder = value
        persist { it[Keys.DAILY_REMINDER] = value }
    }

    fun updateAiImageFilter(value: Boolean): Boolean {
        if (!value && shieldActive) return false
        aiImageFilter = value
        persist { it[Keys.AI_FILTER] = value }
        return true
    }

    fun updateUninstallGuard(value: Boolean): Boolean {
        if (!value && shieldActive) return false
        uninstallGuard = value
        persist { it[Keys.UNINSTALL_GUARD] = value }
        return true
    }

    // ---------- Protection ----------

    fun updateEngine(name: String, enabled: Boolean): Boolean {
        if (!enabled && shieldActive) return false
        searchEngines[name] = enabled
        persistProtection()
        return true
    }

    fun updateContentFilter(key: String, enabled: Boolean): Boolean {
        if (!enabled && shieldActive) return false
        contentFilters[key] = enabled
        persistProtection()
        return true
    }

    fun enableFullSafeSearch() {
        searchEngines.keys.toList().forEach { searchEngines[it] = true }
        contentFilters.keys.toList().forEach { contentFilters[it] = true }
        persistProtection()
    }

    fun safeSearchCount(): Int =
        searchEngines.count { it.value } + contentFilters.count { it.value }

    /**
     * Mutual exclusivity: enabling full block auto-disables shorts-only.
     * Shield lock: full block can never be weakened while the shield is on.
     */
    fun updateAppFull(id: String, enabled: Boolean): Boolean {
        val cur = appBlocks[id] ?: AppBlockState()
        if (enabled) {
            // Strengthening: always allowed. Auto-switch off shorts (exclusive).
            appBlocks[id] = AppBlockState(fullBlock = true, shortsBlock = false)
        } else {
            if (shieldActive) return false // weakening
            appBlocks[id] = cur.copy(fullBlock = false)
        }
        persistProtection()
        return true
    }

    /**
     * Mutual exclusivity: enabling shorts-only auto-disables full block —
     * but that downgrade (full -> shorts) is forbidden while the shield is on.
     */
    fun updateAppShorts(id: String, enabled: Boolean): Boolean {
        val cur = appBlocks[id] ?: AppBlockState()
        if (enabled) {
            if (cur.fullBlock && shieldActive) return false // downgrade
            appBlocks[id] = AppBlockState(fullBlock = false, shortsBlock = true)
        } else {
            if (shieldActive) return false // weakening
            appBlocks[id] = cur.copy(shortsBlock = false)
        }
        persistProtection()
        return true
    }

    fun blockAll() {
        // Strengthening: always allowed. Exclusivity keeps one mode per app.
        blockableApps.forEach { appBlocks[it.id] = AppBlockState(fullBlock = true, shortsBlock = false) }
        searchEngineNames.forEach { searchEngines[it] = true }
        persistProtection()
    }

    fun blockShortsOnly(): Boolean {
        if (shieldActive) return false // downgrades any full blocks
        blockableApps.forEach {
            appBlocks[it.id] = AppBlockState(fullBlock = false, shortsBlock = it.hasShorts)
        }
        persistProtection()
        return true
    }

    fun unblockAll(): Boolean {
        if (shieldActive) return false
        blockableApps.forEach { appBlocks[it.id] = AppBlockState(fullBlock = false, shortsBlock = false) }
        searchEngineNames.forEach { searchEngines[it] = false }
        persistProtection()
        return true
    }

    fun protectionCount(): Int =
        searchEngines.count { it.value } +
            appBlocks.values.count { it.fullBlock || it.shortsBlock }

    private fun persistProtection() {
        val engines = json.encodeToString(enginesSer, searchEngines.toMap())
        val apps = json.encodeToString(appsSer, appBlocks.toMap())
        val filters = json.encodeToString(enginesSer, contentFilters.toMap())
        persist {
            it[Keys.ENGINES] = engines
            it[Keys.APPS] = apps
            it[Keys.FILTERS] = filters
        }
    }

    // ---------- Lists ----------

    fun listFor(black: Boolean, category: ListCategory): SnapshotStateList<String> = when {
        black && category == ListCategory.WORDS -> blackWords
        black && category == ListCategory.SITES -> blackSites
        black -> blackApps
        category == ListCategory.WORDS -> whiteWords
        category == ListCategory.SITES -> whiteSites
        else -> whiteApps
    }

    /** Adding to a blacklist strengthens; adding to a whitelist weakens. */
    fun addToList(black: Boolean, category: ListCategory, value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty()) return true
        if (!black && shieldActive) return false
        val list = listFor(black, category)
        if (!list.contains(v)) list.add(0, v)
        persistLists()
        return true
    }

    /** Removing from a blacklist weakens; removing from a whitelist strengthens. */
    fun removeFromList(black: Boolean, category: ListCategory, value: String): Boolean {
        if (black && shieldActive) return false
        listFor(black, category).remove(value)
        persistLists()
        return true
    }

    private fun persistLists() {
        val bw = json.encodeToString(stringListSer, blackWords.toList())
        val bs = json.encodeToString(stringListSer, blackSites.toList())
        val ba = json.encodeToString(stringListSer, blackApps.toList())
        val ww = json.encodeToString(stringListSer, whiteWords.toList())
        val ws = json.encodeToString(stringListSer, whiteSites.toList())
        val wa = json.encodeToString(stringListSer, whiteApps.toList())
        persist {
            it[Keys.BLACK_WORDS] = bw
            it[Keys.BLACK_SITES] = bs
            it[Keys.BLACK_APPS] = ba
            it[Keys.WHITE_WORDS] = ww
            it[Keys.WHITE_SITES] = ws
            it[Keys.WHITE_APPS] = wa
        }
    }

    // ---------- Journal ----------

    fun addJournalEntry(mood: Int, triggers: List<String>, text: String) {
        val now = System.currentTimeMillis()
        journal.add(0, JournalEntry(id = now, timestamp = now, mood = mood, triggers = triggers, text = text.trim()))
        persistJournal()
    }

    fun deleteJournalEntry(id: Long) {
        journal.removeAll { it.id == id }
        persistJournal()
    }

    private fun persistJournal() {
        val j = json.encodeToString(journalSer, journal.toList())
        persist { it[Keys.JOURNAL] = j }
    }

    // ---------- Reset ----------

    fun resetAllData(): Boolean {
        if (shieldActive) return false
        shieldActive = false
        shieldSince = 0L
        accumulatedControlSeconds = 0L
        delayIndex = 0
        streakStart = System.currentTimeMillis()
        relapses = 0
        longestSeconds = 0L
        urgesResisted = 0
        quoteIndex = 0
        dailyReminder = true
        aiImageFilter = false
        uninstallGuard = false
        applyDefaults()
        journal.clear()
        viewModelScope.launch {
            ds.edit {
                it.clear()
                it[Keys.STREAK_START] = streakStart
            }
        }
        return true
    }
}
