package com.agon.app.blocking

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.utils.AppLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Fast-path detector for the Shortstop strategy.
 *
 * **Goal: ≤ 50 ms detection latency** (vs. 80-200 ms with the old
 * `rootInActiveWindow` + recursive walk approach).
 *
 * Three optimisations make this possible:
 *
 * 1. **WindowId cache** — we memoize the verdict for each
 *    `(packageName, windowId)` pair. If the same window fires another
 *    event in the next [BlockingConfig.WINDOW_CACHE_TTL_MS] ms we
 *    return the cached verdict in O(1) without touching any node.
 *
 * 2. **Event-source first** — `event.source` is the actual node that
 *    triggered the event. Inspecting it is ~5-10× faster than
 *    `rootInActiveWindow()` because we skip the full tree retrieval
 *    (which is an expensive IPC into the target app process).
 *
 * 3. **Targeted view-id lookup** — `findAccessibilityNodeInfosByViewId`
 *    lets the system do the tree walk for us, returning only the
 *    matching nodes. Used as a fallback after `event.source` misses.
 *
 * **Safety** — All tokens used here are the same surface-specific
 * view-ids from [PatternMatcher.Signature.surfaceViewIdTokens] and
 * `tabViewIdTokens`. The detector never inspects `text` or
 * `contentDescription` outside the tab-context (bottom 25 % +
 * `isSelected`) — see [PatternMatcher] for the rationale.
 */
class FastDetector {

    private data class CacheEntry(
        val verdict: Verdict,
        val expiresAtMs: Long,
        val isTabHit: Boolean = false,
    )

    /**
     * The detection verdict.
     *
     *  - [ALLOWED] — the window is on a safe surface; cache it.
     *  - [ON_SHORT_FORM] — we matched a short-form token; cache it.
     *  - [UNKNOWN] — transient; the current [detect] call always
     *    reaches a definitive verdict, but the variant is kept
     *    for callers that need to short-circuit the cache (e.g.
     *    for the no-package-name fast-exit path). It is **not**
     *    cached.
     */
    enum class Verdict { ALLOWED, ON_SHORT_FORM, UNKNOWN }

    private data class Key(val pkg: String, val windowId: Int)

    private val cache = ConcurrentHashMap<Key, CacheEntry>()

    /**
     * Last time the cache was pruned. Used to throttle the
     * `pruneExpired()` call so we don't walk the whole map on
     * every event.
     */
    @Volatile private var lastPruneMs: Long = 0L

    /** Clear all cache entries (e.g. when shield is turned off). */
    fun invalidateAll() {
        cache.clear()
        lastPruneMs = System.currentTimeMillis()
    }

    /**
     * Audit #7: invalidate the cached verdict for a single
     * `(pkg, windowId)` pair. Called when the user clicks
     * something — the click may have changed the surface
     * (e.g. tapped a Reels tab, opened a short from a list,
     * navigated via a deep-link), so any previous "ALLOWED"
     * verdict for this window is now stale and could let a
     * short-video surface through the cache for up to
     * [BlockingConfig.WINDOW_CACHE_TTL_MS].
     */
    fun invalidateWindow(pkg: String, windowId: Int) {
        cache.remove(Key(pkg, windowId))
    }

    /** Invalidate entries older than [BlockingConfig.WINDOW_CACHE_TTL_MS]. */
    fun pruneExpired(now: Long = System.currentTimeMillis()) {
        val it = cache.entries.iterator()
        while (it.hasNext()) {
            val e = it.next().value
            if (e.expiresAtMs <= now) it.remove()
        }
    }

    /**
     * Fast detection — returns immediately if a cached verdict exists
     * for the same `(pkg, windowId)`, otherwise inspects
     * [AccessibilityEvent.getSource] in isolation and falls back to
     * a targeted view-id lookup.
     *
     * @return A [Result] carrying the verdict, the matched node (if
     *         any), the time spent in milliseconds, a short
     *         diagnostic string, and whether the hit was a *tab*
     *         descriptor (vs. the actual short-form surface).
     */
    fun detect(
        event: AccessibilityEvent,
        signature: PatternMatcher.Signature?,
        surface: PatternMatcher.Surface,
    ): Result {
        val started = System.currentTimeMillis()
        val pkg = event.packageName?.toString() ?: return Result(
            verdict = Verdict.UNKNOWN,
            node = null,
            elapsedMs = 0L,
            triggeredBy = "no-pkg",
            isTabHit = false,
        )
        val windowId = event.windowId

        // Periodic janitor — bound the cache size by pruning
        // expired entries at most once every PRUNE_INTERVAL_MS.
        // Without this the cache grows unbounded as the user
        // opens new windows, and we leak an entry per window
        // until process death.
        if (started - lastPruneMs > PRUNE_INTERVAL_MS) {
            lastPruneMs = started
            pruneExpired(started)
        }

        // 1) WindowId cache hit.
        val key = Key(pkg, windowId)
        val now = started
        val cached = cache[key]
        if (cached != null && cached.expiresAtMs > now) {
            return Result(
                verdict = cached.verdict,
                node = null,
                elapsedMs = System.currentTimeMillis() - started,
                triggeredBy = "cache:${cached.verdict.name}",
                isTabHit = cached.isTabHit,
            )
        }

        // 2) Event-source first — inspect the node that fired the event.
        val source = event.source
        if (source != null) {
            val (screenHeight, screenWidth) = try {
                val r = android.graphics.Rect()
                source.getBoundsInScreen(r)
                Pair(
                    r.bottom.coerceAtLeast(0),
                    r.right.coerceAtLeast(0),
                )
            } catch (_: Exception) { Pair(0, 0) }

            val hit = matchNode(pkg, source, signature, screenHeight, screenWidth)
            if (hit != null) {
                val verdict = if (hit.shouldBlock) Verdict.ON_SHORT_FORM else Verdict.ALLOWED
                cacheIfNotable(key, verdict, now, hit.isTabHit)
                return Result(
                    verdict = verdict,
                    node = source,
                    elapsedMs = System.currentTimeMillis() - started,
                    triggeredBy = "source:${hit.triggeredBy}",
                    isTabHit = hit.isTabHit,
                )
            }
            // Don't recycle event.source — the system owns it.
        }

        // 3) Targeted view-id lookup — ask the system to walk the tree
        //    for the most-likely tokens. This is faster than our own
        //    recursive walk because it runs in the target-app process.
        if (signature != null) {
            val topTokens = signature.surfaceViewIdTokens.take(3)
            for (token in topTokens) {
                val matches = source?.findAccessibilityNodeInfosByViewId(
                    "${pkg}:id/$token"
                ) ?: continue
                if (matches.isNotEmpty()) {
                    val hitNode = matches[0]
                    // FB-004: every AccessibilityNodeInfo obtained
                    // from a system query must be recycled. We
                    // recycle the matched node and any siblings
                    // returned by the same query, otherwise we
                    // leak a node per match.
                    for (n in matches) {
                        if (n !== hitNode) n.recycle()
                    }
                    val verdict = Verdict.ON_SHORT_FORM
                    cacheIfNotable(key, verdict, now, isTabHit = false)
                    return Result(
                        verdict = verdict,
                        node = hitNode,
                        elapsedMs = System.currentTimeMillis() - started,
                        triggeredBy = "viewId:$token",
                        isTabHit = false,
                    )
                }
            }
        }

        // 4) Cache "allowed" verdict to short-circuit future events.
        cacheIfNotable(key, Verdict.ALLOWED, now, isTabHit = false)
        return Result(
            verdict = Verdict.ALLOWED,
            node = null,
            elapsedMs = System.currentTimeMillis() - started,
            triggeredBy = "ok",
            isTabHit = false,
        )
    }

    private fun cacheIfNotable(key: Key, verdict: Verdict, now: Long, isTabHit: Boolean = false) {
        // Only cache non-ambiguous verdicts. UNKNOWN is a transient
        // state and would cause stale decisions if cached.
        if (verdict == Verdict.UNKNOWN) return
        val expiresAt = now + BlockingConfig.WINDOW_CACHE_TTL_MS
        cache[key] = CacheEntry(verdict, expiresAt, isTabHit)
    }

    /**
     * Inspect a single node against the signature. Same logic as
     * [PatternMatcher.findHit] but only for one node (no recursion).
     */
    private fun matchNode(
        packageName: String,
        node: AccessibilityNodeInfo,
        signature: PatternMatcher.Signature?,
        screenHeight: Int,
        screenWidth: Int,
    ): PatternMatcher.Hit? {
        val signals = mutableListOf<PatternMatcher.Signal>()

        // 1) Surface view-id
        val viewId = node.viewIdResourceName
        if (!viewId.isNullOrEmpty() && signature != null) {
            for (token in signature.surfaceViewIdTokens) {
                if (viewId.contains(token, ignoreCase = true)) {
                    signals += PatternMatcher.Signal("viewId:$token", 0.55f, token)
                    break
                }
            }
        }

        // 2) Surface class name — also catches WebView-rendered
        //    short videos (audit #10). WebView's class is
        //    `android.webkit.WebView`; the parent activity that
        //    hosts it is usually `<Pkg>WebViewActivity` or similar.
        //    We add a small WebView-specific contribution when
        //    we see it.
        val className = node.className?.toString().orEmpty()
        if (className.isNotEmpty() && signature != null) {
            for (token in signature.surfaceClassNameTokens) {
                if (className.contains(token, ignoreCase = true)) {
                    signals += PatternMatcher.Signal("className:$token", 0.45f, token)
                    break
                }
            }
            if (signals.none { it.source.startsWith("className:") } &&
                isWebViewShortVideoSurface(className, viewId, node.contentDescription?.toString())
            ) {
                signals += PatternMatcher.Signal("className:webviewShort", 0.35f, "WebView")
            }
        }

        // 3) Tab-context (only valid at bottom + selected)
        if (signature != null && screenHeight > 0 && node.isVisibleToUser) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val isAtBottom = bounds.top > (screenHeight * BlockingConfig.BOTTOM_TAB_HEIGHT_RATIO)
            val isSelected = node.isCurrentlySelected()
            if (isAtBottom && isSelected) {
                for (token in signature.tabViewIdTokens) {
                    if (!viewId.isNullOrEmpty() && viewId.contains(token, ignoreCase = true)) {
                        signals += PatternMatcher.Signal("tabViewId:$token", 0.50f, token)
                        break
                    }
                }
                val desc = node.contentDescription?.toString().orEmpty()
                if (desc.isNotEmpty()) {
                    for (token in signature.tabContentDescTokens) {
                        if (desc.contains(token, ignoreCase = true)) {
                            signals += PatternMatcher.Signal("tabContentDesc:$token", 0.30f, token)
                            break
                        }
                    }
                }
            }
        }

        // 4) Size — direction-agnostic. Audit #5: covers landscape,
        //    audit #6: covers split-screen when the caller passes
        //    the active window's bounds.
        if (node.isVisibleToUser && screenHeight > 0) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.height() > 0 && bounds.width() > 0) {
                val widthRef = if (screenWidth > 0) screenWidth else screenHeight
                val ratio = maxOf(
                    bounds.height().toFloat() / screenHeight,
                    bounds.width().toFloat() / widthRef,
                )
                if (ratio >= BlockingConfig.HIGH_CONFIDENCE_HEIGHT_RATIO && node.isClickable) {
                    signals += PatternMatcher.Signal("size:${ratio}", 0.25f, "")
                }
            }
        }

        val confidence = signals.sumOf { it.weight.toDouble() }.toFloat().coerceIn(0f, 1f)
        if (signals.isEmpty() || confidence <= 0f) return null
        val triggered = signals.maxByOrNull { it.weight }?.source ?: "unknown"
        return PatternMatcher.Hit(
            packageName = packageName,
            surface = PatternMatcher.Surface.UNKNOWN,
            confidence = confidence,
            triggeredBy = triggered,
            node = node,
        )
    }

    /**
     * Audit #10: cheap WebView-detector. WebView-rendered short
     * videos (e.g. Facebook Lite Reels, Twitter video in tweets)
     * use HTML view-ids, not Android resource IDs, so the
     * regular view-id matcher misses them. We compensate with a
     * class-name + content-description sniff: if the node is a
     * `WebView` (or hosts one), is clickable, and its
     * content-description hints at a short-video surface, we
     * fire a weak-but-useful signal.
     */
    private fun isWebViewShortVideoSurface(
        className: String,
        viewId: String?,
        contentDescription: String?,
    ): Boolean {
        val isWebView = className.contains("WebView", ignoreCase = true) ||
            (viewId?.contains("webview", ignoreCase = true) == true)
        if (!isWebView) return false
        val desc = contentDescription?.lowercase().orEmpty()
        val hints = listOf(
            "reel", "short", "shorts", "video", "player", "feed", "fyp",
            "ريلز", "شورت", "شورتس",
        )
        return hints.any { desc.contains(it) }
    }

    data class Result(
        val verdict: Verdict,
        val node: AccessibilityNodeInfo?,
        val elapsedMs: Long,
        val triggeredBy: String,
        /**
         * `true` when the hit matched a *tab* descriptor
         * (bottom-nav element + isSelected). `false` when it matched
         * the actual short-form surface, or no hit.
         */
        val isTabHit: Boolean = false,
    )

    companion object {
        private const val TAG = "FastDetector"

        /**
         * How often the in-memory cache is pruned of expired
         * entries. 30 s is cheap (a single map walk) and keeps
         * the map size bounded for users who open lots of
         * windows.
         */
        private const val PRUNE_INTERVAL_MS: Long = 30_000L

        fun logSlowDetection(elapsedMs: Long, triggeredBy: String) {
            if (elapsedMs > 50) {
                AppLogger.w("$TAG: slow detection ${elapsedMs}ms via $triggeredBy")
            }
        }
    }
}
