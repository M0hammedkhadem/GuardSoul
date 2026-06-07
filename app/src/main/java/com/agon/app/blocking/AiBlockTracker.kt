package com.agon.app.blocking

import com.agon.app.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * AI Explorer block tracker.
 *
 * Spec:
 *   - When the AI scanner detects sensitive content inside app `pkg`, it
 *     records an "AI block" event.
 *   - If **3** such events happen in **4 minutes** for the same `pkg` →
 *     that package is **temp-blocked for 15 minutes**.
 *   - During the temp-block window, the AppBlockerService must refuse to
 *     open the app and must show a "blocked until HH:MM" overlay.
 *
 * Storage:
 *   - Recent block timestamps per package are persisted in DataStore via
 *     `AppSettings` (so they survive process death / reboot).
 *   - The temp-block deadline is also persisted and re-evaluated on read.
 *
 * Concurrency:
 *   - All mutating methods are guarded by [mutex] so a foreground app
 *     detector and the AI scanner can call [recordAiBlock] concurrently
 *     without losing events.
 */
class AiBlockTracker(private val settings: AppSettings) {

    private val mutex = Mutex()

    private val _tempBlocks = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Map of packageName → block-until-timestamp (ms). */
    val tempBlocks: StateFlow<Map<String, Long>> = _tempBlocks.asStateFlow()

    /**
     * Returns the time (in ms) the temp block for [pkg] expires, or 0L if
     * the package is not currently temp-blocked.
     */
    fun tempBlockUntil(pkg: String): Long =
        _tempBlocks.value[pkg]?.takeIf { it > System.currentTimeMillis() } ?: 0L

    fun isTempBlocked(pkg: String): Boolean = tempBlockUntil(pkg) > 0L

    /**
     * Records a single AI block event. Returns the new temp-block
     * deadline (ms) if this event triggered a 15-minute block, else 0L.
     */
    suspend fun recordAiBlock(pkg: String, now: Long = System.currentTimeMillis()): Long =
        mutex.withLock {
            val timestamps = settings.getAiBlockTimestamps(pkg)
                .filter { it in (now - WINDOW_MS) until now }
                .toMutableList()
            timestamps.add(now)
            settings.setAiBlockTimestamps(pkg, timestamps)

            if (timestamps.size >= THRESHOLD) {
                val until = now + TEMP_BLOCK_MS
                settings.setAiTempBlockUntil(pkg, until)
                val current = _tempBlocks.value.toMutableMap()
                current[pkg] = until
                _tempBlocks.value = current
                // Once a temp block fires, clear the timestamp list so the
                // user has to "earn" another 3-strike window from scratch.
                settings.setAiBlockTimestamps(pkg, emptyList())
                until
            } else {
                0L
            }
        }

    /**
     * Manually clears the temp block for a package (used by the UI when
     * the user navigates to settings and wants to "unblock now").
     */
    suspend fun clearTempBlock(pkg: String) = mutex.withLock {
        settings.setAiTempBlockUntil(pkg, 0L)
        val current = _tempBlocks.value.toMutableMap()
        current.remove(pkg)
        _tempBlocks.value = current
    }

    /**
     * On app start (or whenever the block list is read) this should be
     * called to drop expired entries and populate [tempBlocks] with the
     * currently-active ones.
     */
    suspend fun refreshFromStorage() = mutex.withLock {
        val now = System.currentTimeMillis()
        val map = mutableMapOf<String, Long>()
        // We don't know which packages may be temp-blocked without
        // enumerating, so we expose them via AppSettings (next method).
        for (pkg in settings.getAllAiTempBlockedPackages()) {
            val until = settings.getAiTempBlockUntil(pkg)
            if (until > now) map[pkg] = until
        }
        _tempBlocks.value = map
    }

    /**
     * ABS-004: prune expired temp blocks on a periodic tick. The
     * previous implementation only called `refreshFromStorage()`
     * once at startup, so a temp block recorded at 09:00 would
     * still appear in the cached map at 10:00 (after its 15-minute
     * expiry) until the service restarted.
     *
     * The scan runs every 30 s — cheap (a single map walk) and
     * matches the existing FastDetector prune cadence. Cancellation
     * is cooperative: the caller must pass a [scope] that lives as
     * long as the tracker is needed.
     */
    fun startExpirationPruner(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val current = _tempBlocks.value
                if (current.isNotEmpty()) {
                    val pruned = current.filterValues { it > now }
                    if (pruned.size != current.size) {
                        _tempBlocks.value = pruned
                    }
                }
                delay(PRUNE_INTERVAL_MS)
            }
        }
    }

    companion object {
        /** Sliding window in milliseconds (4 minutes). */
        const val WINDOW_MS: Long = 4L * 60L * 1_000L

        /** Number of strikes needed to trigger a temp block. */
        const val THRESHOLD: Int = 3

        /** Length of the temp block in milliseconds (15 minutes). */
        const val TEMP_BLOCK_MS: Long = 15L * 60L * 1_000L

        /** How often the in-memory temp-block map is pruned. */
        const val PRUNE_INTERVAL_MS: Long = 30L * 1_000L
    }
}
