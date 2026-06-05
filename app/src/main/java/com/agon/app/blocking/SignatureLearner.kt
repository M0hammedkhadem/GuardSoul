package com.agon.app.blocking

import com.agon.app.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Self-learning engine for the Shortstop strategy.
 *
 * **What this solves**
 * Hand-curated signatures in [PatternMatcher.signatures] cover the
 * major apps (YouTube, Instagram, Facebook, TikTok, Snapchat).
 * But apps update their view-ids every release, and brand-new
 * short-video apps (Lemon8, Threads-with-video, BeReal, …) ship
 * without us ever touching the code. Without learning we'd silently
 * let them through.
 *
 * **What "learning" means here**
 *  1. When [PatternMatcher] sees strong generic signals for a
 *     package we don't have a signature for — full-screen clickable
 *     node + tall aspect ratio + the user is on a short-form app —
 *     it asks this learner to record the observation.
 *  2. The learner stores the observed view-ids and class names,
 *     counts how many times the user has triggered the same
 *     pattern, and timestamps the first/last hit.
 *  3. Once a package has crossed the promotion threshold
 *     (see [PROMOTION_HIT_COUNT]) AND has held up across at least
 *     [PROMOTION_TIME_WINDOW_MS] of wall-clock time, the package is
 *     promoted and [signatureFor] starts returning the learned
 *     signature to [PatternMatcher].
 *
 * **What this deliberately does NOT do**
 *  - It does NOT trust the very first observation. False positives on
 *    one app launch (e.g. a notification panel that briefly looks
 *    like a full-screen player) should not promote a signature.
 *  - It does NOT learn from apps the user has whitelisted or
 *    blacklisted (whitelist = user explicitly approved; blacklist =
 *    already fully blocked, learning is redundant).
 *  - It does NOT run on the accessibility-callback thread. All
 *    state mutations are serialised by [mutex] and the persistence
 *    hop to DataStore happens off the main thread.
 *
 * **Storage format**
 * A single DataStore `stringPreferencesKey` ([AppSettings.Keys.LEARNED_SIGNATURES])
 * holds a JSON document:
 * ```json
 * {
 *   "com.example.app": {
 *     "viewIds":     ["reel_viewer", "player_root"],
 *     "classNames":  ["ReelPlayerView"],
 *     "hitCount":    7,
 *     "firstSeen":   1717500000000,
 *     "lastSeen":    1717500900000,
 *     "promoted":    true
 *   }
 * }
 * ```
 * Small enough to keep the whole document in memory; we never read
 * it on the hot accessibility-callback path.
 */
class SignatureLearner(private val settings: AppSettings) {

    /**
     * Minimum number of independent observations required before
     * the signature is promoted to "active". Below this, we keep
     * collecting but do not act on the signature.
     */
    private val promotionHitCount: Int = 5

    /**
     * Minimum wall-clock span (ms) between the first and the most
     * recent observation before the signature is eligible for
     * promotion. This is what stops a single launch session (e.g. a
     * notification that briefly looks like a player) from triggering
     * promotion. 24 h is enough to span a normal day of usage.
     */
    private val promotionTimeWindowMs: Long = TimeUnit.HOURS.toMillis(24)

    /**
     * Maximum age of the most recent observation (ms). If the user
     * hasn't opened the app for this long, we drop the signature
     * — the view-ids might have rotated since.
     */
    private val demotionAfterMs: Long = TimeUnit.DAYS.toMillis(14)

    private val mutex = Mutex()

    private val _signatures = MutableStateFlow<Map<String, LearnedSignature>>(emptyMap())

    /**
     * Hot view of all learned signatures, in observation-order
     * (most-recently-seen first). UI screens subscribe to this to
     * render the "Learned Signatures" debug screen.
     */
    val signatures: StateFlow<Map<String, LearnedSignature>> = _signatures.asStateFlow()

    /**
     * Hydrate the in-memory map from DataStore. Call once on service
     * start; from then on, every write goes through the same path so
     * the in-memory state is always authoritative.
     */
    suspend fun load() = mutex.withLock {
        val raw = settings.getLearnedSignaturesRaw()
        _signatures.value = if (raw.isBlank()) emptyMap() else parseJson(raw)
    }

    /**
     * Record an observation of a short-form surface in [pkg]. The
     * [viewId] and [className] are the *specific* identifiers the
     * app exposed; we count them, dedupe them, and use them as
     * candidate signature tokens.
     *
     * @return the new hit count, or -1 if the observation was
     *         rejected (whitelisted / blacklisted / GuardSoul
     *         itself).
     */
    suspend fun observe(
        pkg: String,
        viewId: String?,
        className: String?,
        nowMs: Long = System.currentTimeMillis(),
    ): Int = mutex.withLock {
        if (pkg.isBlank()) return@withLock -1
        if (pkg in EXCLUDED_PACKAGES) return@withLock -1

        val current = _signatures.value.toMutableMap()
        val existing = current[pkg]
        val newViewIds = mergeTokens(existing?.viewIds, viewId)
        val newClassNames = mergeTokens(existing?.classNames, className)
        val hitCount = (existing?.hitCount ?: 0) + 1
        val firstSeen = existing?.firstSeen ?: nowMs
        val promoted = existing?.promoted == true ||
            (hitCount >= promotionHitCount &&
                nowMs - firstSeen >= promotionTimeWindowMs)

        val updated = LearnedSignature(
            packageName = pkg,
            viewIds = newViewIds,
            classNames = newClassNames,
            hitCount = hitCount,
            firstSeen = firstSeen,
            lastSeen = nowMs,
            promoted = promoted,
        )
        current[pkg] = updated
        _signatures.value = current
        persist(current)
        if (promoted && existing?.promoted != true) {
            Timber.d("Learner: promoted $pkg after $hitCount hits " +
                "(viewIds=$newViewIds, classNames=$newClassNames)")
        }
        hitCount
    }

    /**
     * Returns the active learned signature for [pkg], or null if no
     * signature has been promoted yet for this package. [PatternMatcher]
     * falls back to this lookup after its hand-curated map.
     */
    fun signatureFor(pkg: String): PatternMatcher.Signature? {
        val sig = _signatures.value[pkg] ?: return null
        if (!sig.promoted) return null
        return PatternMatcher.Signature(
            surfaceViewIdTokens = sig.viewIds,
            surfaceClassNameTokens = sig.classNames,
        )
    }

    /**
     * Demote a learned signature. Called by the UI when the user
     * disagrees with the auto-detected signature, or by the
     * periodic janitor when the signature has gone stale.
     */
    suspend fun demote(pkg: String) = mutex.withLock {
        val current = _signatures.value.toMutableMap()
        current.remove(pkg)
        _signatures.value = current
        persist(current)
        Timber.d("Learner: demoted $pkg")
    }

    /**
     * Periodic janitor — drop signatures that haven't been seen
     * for [demotionAfterMs]. Returns the list of demoted packages
     * for logging.
     */
    suspend fun pruneStale(nowMs: Long = System.currentTimeMillis()): List<String> = mutex.withLock {
        val current = _signatures.value
        val stale = current.entries.filter { nowMs - it.value.lastSeen > demotionAfterMs }
        if (stale.isEmpty()) return@withLock emptyList()
        val updated = current.toMutableMap()
        for ((pkg, _) in stale) updated.remove(pkg)
        _signatures.value = updated
        persist(updated)
        stale.map { it.key }
    }

    /**
     * Wipe all learned data. Bound to a "Reset" button on the debug
     * screen.
     */
    suspend fun clearAll() = mutex.withLock {
        _signatures.value = emptyMap()
        persist(emptyMap())
    }

    private fun mergeTokens(existing: List<String>?, candidate: String?): List<String> {
        val merged = LinkedHashSet<String>()
        existing?.forEach { merged.add(it) }
        if (!candidate.isNullOrBlank()) merged.add(candidate)
        // Cap at MAX_TOKENS to keep the JSON document small and the
        // hot-path substring scan fast.
        return merged.take(MAX_TOKENS)
    }

    private suspend fun persist(snapshot: Map<String, LearnedSignature>) {
        runCatching { settings.setLearnedSignaturesRaw(serializeJson(snapshot)) }
            .onFailure { Timber.w(it, "Learner: failed to persist snapshot") }
    }

    private fun serializeJson(map: Map<String, LearnedSignature>): String {
        val root = JSONObject()
        for ((pkg, sig) in map) {
            val obj = JSONObject()
            obj.put("viewIds", JSONArray(sig.viewIds))
            obj.put("classNames", JSONArray(sig.classNames))
            obj.put("hitCount", sig.hitCount)
            obj.put("firstSeen", sig.firstSeen)
            obj.put("lastSeen", sig.lastSeen)
            obj.put("promoted", sig.promoted)
            root.put(pkg, obj)
        }
        return root.toString()
    }

    private fun parseJson(raw: String): Map<String, LearnedSignature> {
        return runCatching {
            val root = JSONObject(raw)
            val out = LinkedHashMap<String, LearnedSignature>()
            for (key in root.keys()) {
                val obj = root.getJSONObject(key)
                out[key] = LearnedSignature(
                    packageName = key,
                    viewIds = obj.optJSONArray("viewIds").toStringList(),
                    classNames = obj.optJSONArray("classNames").toStringList(),
                    hitCount = obj.optInt("hitCount", 0),
                    firstSeen = obj.optLong("firstSeen", 0L),
                    lastSeen = obj.optLong("lastSeen", 0L),
                    promoted = obj.optBoolean("promoted", false),
                )
            }
            out
        }.getOrElse {
            Timber.w(it, "Learner: failed to parse persisted signatures; starting empty")
            emptyMap()
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>(length())
        for (i in 0 until length()) out.add(optString(i, ""))
        return out.filter { it.isNotBlank() }
    }

    /**
     * Snapshot of a single learned signature. Mirrors the on-disk
     * JSON shape so we never re-parse on read.
     */
    data class LearnedSignature(
        val packageName: String,
        val viewIds: List<String>,
        val classNames: List<String>,
        val hitCount: Int,
        val firstSeen: Long,
        val lastSeen: Long,
        val promoted: Boolean,
    )

    companion object {
        /** Cap on stored tokens per package to bound the JSON size. */
        private const val MAX_TOKENS: Int = 16

        /**
         * Packages we never learn from. GuardSoul itself is the
         * obvious one; the system packages are skipped to avoid
         * polluting the learner with useless observations.
         */
        private val EXCLUDED_PACKAGES = setOf(
            "com.agon.app",
            "android",
            "com.android.systemui",
            "com.android.settings",
        )
    }
}
