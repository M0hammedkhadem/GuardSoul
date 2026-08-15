package com.agon.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.data.AppBlockState
import com.agon.app.data.JournalEntry
import com.agon.app.data.ListCategory
import com.agon.app.data.blockableApps
import com.agon.app.data.contentFilterList
import com.agon.app.data.defaultBlackSites
import com.agon.app.data.defaultBlackWords
import com.agon.app.data.defaultWhiteSites
import com.agon.app.data.delayOptions
import com.agon.app.data.quotes
import com.agon.app.data.repository.JournalRepository
import com.agon.app.data.repository.ProtectionRepository
import com.agon.app.data.searchEngineNames
import com.agon.app.domain.usecase.AddToListUseCase
import com.agon.app.domain.usecase.RemoveFromListUseCase
import com.agon.app.domain.usecase.ResetAllDataUseCase
import com.agon.app.domain.usecase.ToggleShieldUseCase
import com.agon.app.domain.usecase.UpdateAppFullBlockUseCase
import com.agon.app.domain.usecase.UpdateAppShortsBlockUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state holder (MVVM presentation layer).
 *
 * All persistence goes through [ProtectionRepository]/[JournalRepository];
 * all non-trivial business rules live in domain use cases. The public API
 * (fields + function signatures) is IDENTICAL to the pre-refactor version,
 * so no screen changes.
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
@HiltViewModel
class MainViewModel @Inject constructor(
    private val protectionRepo: ProtectionRepository,
    private val journalRepo: JournalRepository,
) : ViewModel() {

    private val toggleShieldUseCase = ToggleShieldUseCase()
    private val updateAppFullBlock = UpdateAppFullBlockUseCase()
    private val updateAppShortsBlock = UpdateAppShortsBlockUseCase()
    private val addToListUseCase = AddToListUseCase()
    private val removeFromListUseCase = RemoveFromListUseCase()
    private val resetAllDataUseCase = ResetAllDataUseCase()

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

    /** Epoch ms at which a scheduled shield stop completes (0 = none). */
    var pendingStopAt by mutableLongStateOf(0L)
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
    /** Show a "continue anyway" button on keyword shields (default off). */
    var keywordContinue by mutableStateOf(false)
        private set
    /** NSFW action: false = kick out (default), true = camouflage overlay. */
    var nsfwBlurMode by mutableStateOf(false)
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
            protectionRepo.blocksCountFlow.collect { blocksCount = it }
        }
        // Journal now lives in Room; the flow first runs the one-time legacy
        // DataStore -> Room migration, then keeps the UI list in sync.
        viewModelScope.launch {
            journalRepo.observeJournal().collect { entries ->
                journal.clear()
                journal.addAll(entries)
            }
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
        val s = protectionRepo.snapshot()
        val savedStart = s.streakStart
        if (savedStart != null) {
            streakStart = savedStart
        } else {
            protectionRepo.persistStreakStart(streakStart)
        }
        s.shieldActive?.let { shieldActive = it }
        s.shieldSince?.let { shieldSince = it }
        s.pendingStopAt?.let { pendingStopAt = it }
        s.controlSeconds?.let { accumulatedControlSeconds = it }
        // A stop scheduled before the app was killed may have become due.
        completePendingStopIfDue(System.currentTimeMillis())
        s.delayIndex?.let { delayIndex = it }
        s.relapses?.let { relapses = it }
        s.longestSeconds?.let { longestSeconds = it }
        s.urgesResisted?.let { urgesResisted = it }
        s.quoteIndex?.let { quoteIndex = it }
        s.dailyReminder?.let { dailyReminder = it }
        s.aiImageFilter?.let { aiImageFilter = it }
        s.uninstallGuard?.let { uninstallGuard = it }
        s.keywordContinue?.let { keywordContinue = it }
        s.nsfwBlur?.let { nsfwBlurMode = it }
        s.contentFilters?.let { m ->
            contentFilters.clear(); contentFilters.putAll(m)
        }
        s.searchEngines?.let { m ->
            searchEngines.clear(); searchEngines.putAll(m)
        }
        s.appBlocks?.let { m ->
            appBlocks.clear()
            // Only keep known apps; new catalog entries get defaults.
            blockableApps.forEach { app ->
                appBlocks[app.id] = m[app.id] ?: AppBlockState()
            }
        }
        loadList(s.blackWords, blackWords)
        loadList(s.blackSites, blackSites)
        loadList(s.blackApps, blackApps)
        loadList(s.whiteWords, whiteWords)
        loadList(s.whiteSites, whiteSites)
        loadList(s.whiteApps, whiteApps)
        loaded = true
    }

    private fun loadList(values: List<String>?, target: SnapshotStateList<String>) {
        values ?: return
        target.clear(); target.addAll(values)
    }

    // ---------- Shield ----------

    fun delayMillisFor(index: Int): Long = when (index) {
        1 -> 10L * 60_000L        // 10 دقائق
        2 -> 60L * 60_000L        // ساعة
        3 -> 24L * 60L * 60_000L  // 24 ساعة
        else -> 0L                // بدون تأخير
    }

    private fun shieldState() = ToggleShieldUseCase.ShieldState(
        active = shieldActive,
        since = shieldSince,
        pendingStopAt = pendingStopAt,
        controlSeconds = accumulatedControlSeconds,
    )

    private fun applyShieldState(state: ToggleShieldUseCase.ShieldState) {
        shieldActive = state.active
        shieldSince = state.since
        pendingStopAt = state.pendingStopAt
        accumulatedControlSeconds = state.controlSeconds
        persistShield()
    }

    /**
     * Starting is instant. Stopping honours the anti-impulse delay: with a
     * non-zero delay the stop is SCHEDULED and the shield stays fully active
     * until the timer elapses — the user can cancel (strengthening) anytime.
     */
    fun toggleShield() {
        val newState = toggleShieldUseCase(
            current = shieldState(),
            delayMillis = delayMillisFor(delayIndex),
            now = System.currentTimeMillis(),
        ) ?: return // stop already scheduled — legacy no-op without persist
        applyShieldState(newState)
    }

    /** Cancelling a scheduled stop strengthens protection — always allowed. */
    fun cancelPendingStop() {
        applyShieldState(toggleShieldUseCase.cancelPendingStop(shieldState()))
    }

    /** Called from the UI ticker; finalises a due scheduled stop. */
    fun completePendingStopIfDue(now: Long) {
        toggleShieldUseCase.completeIfDue(shieldState(), now)?.let { applyShieldState(it) }
    }

    private fun persistShield() {
        val active = shieldActive
        val since = shieldSince
        val pending = pendingStopAt
        val control = accumulatedControlSeconds
        viewModelScope.launch {
            protectionRepo.persistShield(active, since, pending, control)
        }
    }

    /**
     * While the shield is active the delay may only be INCREASED — the
     * wrap-around (24h -> none) is a weakening move and is rejected.
     */
    fun cycleDelay(): Boolean {
        val next = (delayIndex + 1) % delayOptions.size
        if (shieldActive && next < delayIndex) return false
        delayIndex = next
        val value = delayIndex
        viewModelScope.launch { protectionRepo.persistDelayIndex(value) }
        return true
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
        val start = streakStart
        val count = relapses
        val longest = longestSeconds
        viewModelScope.launch { protectionRepo.persistRelapse(start, count, longest) }
    }

    fun resistUrge() {
        urgesResisted += 1
        val value = urgesResisted
        viewModelScope.launch { protectionRepo.persistUrges(value) }
    }

    fun nextQuote() {
        quoteIndex = (quoteIndex + 1) % quotes.size
        val value = quoteIndex
        viewModelScope.launch { protectionRepo.persistQuoteIndex(value) }
    }

    fun updateDailyReminder(value: Boolean) {
        dailyReminder = value
        viewModelScope.launch { protectionRepo.persistDailyReminder(value) }
    }

    fun updateAiImageFilter(value: Boolean): Boolean {
        if (!value && shieldActive) return false
        aiImageFilter = value
        viewModelScope.launch { protectionRepo.persistAiImageFilter(value) }
        return true
    }

    fun updateUninstallGuard(value: Boolean): Boolean {
        if (!value && shieldActive) return false
        uninstallGuard = value
        viewModelScope.launch { protectionRepo.persistUninstallGuard(value) }
        return true
    }

    /** Enabling the continue button WEAKENS protection -> locked while shield on. */
    fun updateKeywordContinue(value: Boolean): Boolean {
        if (value && shieldActive) return false
        keywordContinue = value
        viewModelScope.launch { protectionRepo.persistKeywordContinue(value) }
        return true
    }

    /** Camouflage is weaker than kick-out -> switching to it is locked while shield on. */
    fun updateNsfwBlurMode(value: Boolean): Boolean {
        if (value && shieldActive) return false
        nsfwBlurMode = value
        viewModelScope.launch { protectionRepo.persistNsfwBlur(value) }
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
    fun updateAppFull(id: String, enabled: Boolean): Boolean =
        updateAppFullBlock(appBlocks, id, enabled, shieldActive) { persistProtection() }

    /**
     * Mutual exclusivity: enabling shorts-only auto-disables full block —
     * but that downgrade (full -> shorts) is forbidden while the shield is on.
     */
    fun updateAppShorts(id: String, enabled: Boolean): Boolean =
        updateAppShortsBlock(appBlocks, id, enabled, shieldActive) { persistProtection() }

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
        val engines = searchEngines.toMap()
        val apps = appBlocks.toMap()
        val filters = contentFilters.toMap()
        viewModelScope.launch {
            protectionRepo.persistProtection(engines, apps, filters)
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
    fun addToList(black: Boolean, category: ListCategory, value: String): Boolean =
        addToListUseCase(listFor(black, category), black, value, shieldActive) { persistLists() }

    /** Removing from a blacklist weakens; removing from a whitelist strengthens. */
    fun removeFromList(black: Boolean, category: ListCategory, value: String): Boolean =
        removeFromListUseCase(listFor(black, category), black, value, shieldActive) { persistLists() }

    private fun persistLists() {
        val bw = blackWords.toList()
        val bs = blackSites.toList()
        val ba = blackApps.toList()
        val ww = whiteWords.toList()
        val ws = whiteSites.toList()
        val wa = whiteApps.toList()
        viewModelScope.launch {
            protectionRepo.persistLists(bw, bs, ba, ww, ws, wa)
        }
    }

    // ---------- Journal ----------

    fun addJournalEntry(mood: Int, triggers: List<String>, text: String) {
        val now = System.currentTimeMillis()
        val entry = JournalEntry(id = now, timestamp = now, mood = mood, triggers = triggers, text = text.trim())
        // Optimistic update (same instant feedback as before); Room's flow
        // emission then confirms the same state.
        journal.add(0, entry)
        viewModelScope.launch { journalRepo.add(entry) }
    }

    fun deleteJournalEntry(id: Long) {
        journal.removeAll { it.id == id }
        viewModelScope.launch { journalRepo.delete(id) }
    }

    // ---------- Reset ----------

    fun resetAllData(): Boolean {
        val allowed = resetAllDataUseCase(shieldActive) {
            val start = System.currentTimeMillis()
            streakStart = start
            viewModelScope.launch { protectionRepo.clearAllData(start) }
        }
        if (!allowed) return false
        shieldActive = false
        shieldSince = 0L
        accumulatedControlSeconds = 0L
        delayIndex = 0
        relapses = 0
        longestSeconds = 0L
        urgesResisted = 0
        quoteIndex = 0
        dailyReminder = true
        aiImageFilter = false
        uninstallGuard = false
        keywordContinue = false
        nsfwBlurMode = false
        applyDefaults()
        journal.clear()
        viewModelScope.launch { journalRepo.clearAll() }
        return true
    }
}
