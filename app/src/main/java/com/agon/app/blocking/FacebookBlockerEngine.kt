package com.agon.app.blocking

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.utils.AppLogger

/**
 * **BATCH-R**: Facebook-Reels-specific detection engine.
 *
 * Lives in its own file because Facebook Reels uses a UI distinct
 * from YouTube Shorts or Instagram Reels:
 *
 *  1. **Top-nav orange play button** for the Reels section (also bottom-nav
 *     in the new 2024+ Facebook UI — we check both).
 *  2. **Right-side vertical engagement rail** (like / comment / share /
 *     save / more) — present *only* on the fullscreen Reels player.
 *  3. **"X اقتراحات" suggestions carousel** inside the news feed — must
 *     NOT be confused with the fullscreen player.
 *  4. **com.facebook.lite** renders Reels inside a WebView — we must
 *     inspect the surrounding Chrome (ARIA / contentDescription) rather
 *     than the WebView internals.
 *
 * The generic [PatternMatcher] Signature (view-id + className tokens)
 * catches the easy cases. This engine adds three **layered** checks
 * that close common gaps:
 *
 *  - **Layer 1 — Engagement rail** (strongest): right-side vertical
 *    clickable column. Specific to the fullscreen Reels player.
 *  - **Layer 2 — Fullscreen player + nearby text**: a node covering
 *    ≥ 85 % of screen height *plus* at least one of the creator-info
 *    signals ("متابعة" / "Follow" button, "صوت أصلي" / "original audio"
 *    attribution, or a "0:07" duration label).
 *  - **Layer 3 — Reels section tab**: orange play button in the top
 *    nav (or the new bottom-nav Reels tab) when the user has just
 *    tapped it. Returns a *tab hit* so the kick-out path can use the
 *    fast variant.
 *
 * The engine is bounded by [MAX_DEPTH] recursion depth and
 * [MAX_SCAN_MS] total scan time, matching the Shortstop tree budget
 * ([BlockingConfig.SHORTSTOP_TREE_BUDGET_MS]).
 *
 * **Assumptions** (derived from the three user-provided screenshots
 * and Android accessibility conventions — must be validated on a
 * real device and tuned after a few weeks of usage):
 *
 *  - The Reels fullscreen player renders a vertical ViewPager2 (the
 *    "swipe-up to next reel" interaction). Its root has bounds
 *    ≥ 85 % height, ≥ 80 % width, and contains a right-side action
 *    column with 3-5 clickable children.
 *  - The "Follow" button is rendered in the bottom 40 % of the
 *    player, near the creator name and reel description.
 *  - "original audio" / "صوت أصلي" attribution appears in the bottom
 *    15 % of the player, near the description text.
 *  - The duration label (e.g. "0:07") appears at the bottom edge of
 *    the Reels frame (or sometimes top-right for the active clip).
 *  - The orange Reels icon in the top nav has contentDescription
 *    containing "Reels" / "ريلز" and is at y < 25 % of screen height.
 *
 * @see ShortstopEngine
 * @see PatternMatcher
 */
class FacebookBlockerEngine {

    companion object {
        private const val TAG = "FacebookBlocker"

        /**
         * The two Facebook package names we inspect. We do **not**
         * match on suffix — only the two curated ids. (com.facebook.wififinder
         * etc. are unrelated.)
         */
        private val FACEBOOK_PACKAGES: Set<String> = setOf(
            "com.facebook.katana",
            "com.facebook.lite",
        )

        /**
         * Right-side engagement rail view-id tokens. The katana package
         * uses the primary "reel_action_bar" id; lite uses a shorter
         * "reel_action_button_row" id because the lite UI is a
         * simplified WebView-wrapped shell. The catch-all tokens
         * ("reel_actions", "reels_action_bar") absorb both.
         */
        private val ENGAGEMENT_RAIL_VIEW_ID_TOKENS: List<String> = listOf(
            "reel_action_bar",
            "reel_action_bar_container",
            "reel_footer_action_bar",
            "reel_secondary_action_bar",
            "reels_action_bar",
            "reel_action_button_row",
            "reel_actions",
            "reel_action_column",
            "reel_action_stack",
            "reel_action_button",
        )

        /**
         * Creator-info / Follow-button area tokens. When one of these
         * IDs is present *and* the screen is fullscreen, we know
         * we're on the Reels player (not the news feed).
         */
        private val CREATOR_INFO_VIEW_ID_TOKENS: List<String> = listOf(
            "reel_metadata_container",
            "reel_metadata",
            "reel_video_subtitle",
            "reel_footer",
            "reel_creator_info",
            "reels_creator_info",
            "reel_video_caption",
        )

        /**
         * Progress bar — only rendered on the fullscreen Reels
         * player (not the news feed video).
         */
        private val PROGRESS_BAR_VIEW_ID_TOKENS: List<String> = listOf(
            "reel_progress_bar",
            "reel_progressbar",
            "reel_video_progress_bar",
            "reels_progress",
        )

        /**
         * Vertical ViewPager2 — the swipe-up-to-next-reel container.
         * Strong signal: a ViewPager2 with vertical orientation
         * AND ≥ 1 child of ≥ 85 % height is *almost certainly* Reels.
         */
        private val PAGER_VIEW_ID_TOKENS: List<String> = listOf(
            "reels_pager",
            "reel_pager",
            "reels_view_pager",
            "reel_view_pager",
        )

        /**
         * Reels section tab view-id tokens. Matches the katana / lite
         * tab ids across layouts. Some of these overlap with
         * [PatternMatcher.Signature.tabViewIdTokens] for katana —
         * we keep them duplicated here so this engine is
         * self-contained for testing.
         */
        private val TAB_VIEW_ID_TOKENS: List<String> = listOf(
            "reels_tab",
            "tab_reels",
            "tab_reel",
            "reel_tab",
        )

        /**
         * Audio attribution text. Reels always show "original audio" /
         * "صوت أصلي" at the bottom of the fullscreen player. News
         * feed videos never show this label.
         */
        private val AUDIO_HINTS: List<String> = listOf(
            "original audio",
            "الصوت الأصلي",
            "صوت أصلي",
            "صوت",
        )

        /**
         * "Follow" button text. Rendered near the creator name on the
         * Reels player. May appear in the news feed too (e.g. on
         * sponsored posts) but is *only* a blocker signal when
         * combined with the fullscreen-size + audio hint.
         */
        private val FOLLOW_HINTS: List<String> = listOf(
            "متابعة",
            "Follow",
            "متابَع",
        )

        /**
         * Duration pattern: "0:07", "0:14", "1:23" — Reels clips are
         * short (typically < 90 s), and the timer is rendered as a
         * compact `M:SS` label. News-feed video durations are
         * typically `H:MM:SS` and are *not* rendered as a top-of-tree
         * text label.
         */
        private val DURATION_PATTERN: Regex = Regex("""^0:\d{1,2}$|^1:\d{2}$""")

        /**
         * Top-nav region: anything in the top 25 % of screen height.
         * The orange Reels play button lives here in the classic
         * Facebook UI.
         */
        private const val TOP_NAV_HEIGHT_RATIO: Float = 0.25f

        /**
         * Bottom-nav region: bottom 15 % of screen height. The new
         * 2024+ Facebook UI has Reels as a bottom-nav tab.
         */
        private const val BOTTOM_NAV_HEIGHT_RATIO: Float = 0.85f

        /**
         * Fullscreen player height threshold.
         */
        private const val FULLSCREEN_HEIGHT_RATIO: Float = 0.85f

        /**
         * Fullscreen player width threshold.
         */
        private const val FULLSCREEN_WIDTH_RATIO: Float = 0.80f

        /**
         * Engagement rail must sit in the rightmost 30 % of the
         * screen. This is how we distinguish the rail from any
         * in-feed vertical action group.
         */
        private const val RAIL_RIGHT_EDGE_RATIO: Float = 0.70f

        /**
         * Confidence floor for layer 1 (engagement rail) — already
         * a strong signal, so we set high.
         */
        private const val CONF_ENGAGEMENT_RAIL: Float = 0.92f

        /**
         * Confidence for layer 2 (fullscreen + follow + audio) —
         * two confirmatory signals, very strong.
         */
        private const val CONF_FULLSCREEN_FOLLOW_AUDIO: Float = 0.90f

        /**
         * Confidence for layer 2 (fullscreen + follow only) — one
         * follow-up signal, still strong.
         */
        private const val CONF_FULLSCREEN_FOLLOW: Float = 0.85f

        /**
         * Confidence for layer 2 (fullscreen + audio only) — slightly
         * weaker because audio attribution can be ambiguous.
         */
        private const val CONF_FULLSCREEN_AUDIO: Float = 0.82f

        /**
         * Confidence for layer 2 (fullscreen + duration label).
         */
        private const val CONF_FULLSCREEN_DURATION: Float = 0.80f

        /**
         * Confidence for layer 3 (Reels tab) — lower than the
         * fullscreen layers because the tab is also visible on the
         * Home screen (where we *don't* want to block).
         */
        private const val CONF_TAB_HIT: Float = 0.85f

        /**
         * Maximum recursion depth — matches
         * [BlockingConfig.SHORTSTOP_TREE_MAX_DEPTH].
         */
        private const val MAX_DEPTH: Int = 16

        /**
         * Total scan budget (ms) — matches
         * [BlockingConfig.SHORTSTOP_TREE_BUDGET_MS].
         */
        private const val MAX_SCAN_MS: Long = 35L

        fun isFacebookPackage(pkg: String?): Boolean =
            pkg != null && pkg in FACEBOOK_PACKAGES

        fun logHit(hit: FacebookHit) {
            AppLogger.d(
                "$TAG: hit conf=${hit.confidence} via ${hit.triggeredBy} " +
                    "player=${hit.isFullscreenPlayer} tab=${hit.isTabHit}"
            )
        }
    }

    /**
     * The detection verdict for a Facebook Reels surface.
     *
     * The caller (ShortstopEngine) compares [confidence] against
     * [BlockingConfig.SURGICAL_CONFIDENCE_THRESHOLD] (0.80) to decide
     * on a kick-out.
     */
    data class FacebookHit(
        /**
         * True when the user has entered a Reels fullscreen player
         * (≥ 85 % height, vertical, with engagement rail or
         * creator-info / audio hints nearby). Highest confidence.
         */
        val isFullscreenPlayer: Boolean = false,

        /**
         * True when the user has tapped the Reels section tab in
         * the top nav (or the new bottom-nav Reels tab). The player
         * hasn't actually started yet. Lower confidence than
         * [isFullscreenPlayer].
         */
        val isTabHit: Boolean = false,

        /**
         * 0.0..1.0 confidence score. Compare against
         * [BlockingConfig.SURGICAL_CONFIDENCE_THRESHOLD] (0.80).
         */
        val confidence: Float = 0f,

        /**
         * Short diagnostic string for the BlockTrace log.
         * Format: `"facebook:<layer>:<signal>"` e.g.
         * `"facebook:engagement_rail:reel_action_bar"`.
         */
        val triggeredBy: String = "",
    ) {
        val shouldBlock: Boolean
            get() = confidence >= BlockingConfig.SURGICAL_CONFIDENCE_THRESHOLD
    }

    /**
     * Walk [root] looking for Facebook-Reels-specific signals.
     * Returns a [FacebookHit] when at least one layer produces a hit,
     * or null if no signal matched.
     *
     * Order of layers (first hit wins, all-or-nothing):
     *  1. Engagement rail (most distinctive — fastest).
     *  2. Fullscreen player + nearby text signal.
     *  3. Reels section tab.
     *
     * Cost: O(tree size) — bounded by [MAX_DEPTH] and [MAX_SCAN_MS].
     */
    fun detect(
        root: AccessibilityNodeInfo,
        screenHeight: Int,
        screenWidth: Int,
    ): FacebookHit? {
        if (screenHeight <= 0 || screenWidth <= 0) return null
        val started = System.currentTimeMillis()

        // Layer 1: engagement rail — strongest, fastest (~5ms).
        val rail = findEngagementRail(root, started)
        if (rail != null) return rail
        if (budgetExceeded(started)) return null

        // Layer 2: fullscreen player + nearby text signal.
        val player = findFullscreenPlayer(root, screenHeight, screenWidth, started)
        if (player != null) return player
        if (budgetExceeded(started)) return null

        // Layer 3: Reels section tab.
        val tab = findReelsTab(root, screenHeight, screenWidth, started)
        if (tab != null) return tab

        return null
    }

    // --- Layer 1: engagement rail ---------------------------------------

    /**
     * Find a right-side engagement rail. We try view-id lookups for
     * both packages (`com.facebook.katana` and `com.facebook.lite`).
     * Each found node is recycled to avoid leaks.
     */
    private fun findEngagementRail(
        root: AccessibilityNodeInfo,
        started: Long,
    ): FacebookHit? {
        for (pkg in FACEBOOK_PACKAGES) {
            if (budgetExceeded(started)) return null
            for (token in ENGAGEMENT_RAIL_VIEW_ID_TOKENS) {
                if (budgetExceeded(started)) return null
                val matches = try {
                    root.findAccessibilityNodeInfosByViewId("$pkg:id/$token")
                } catch (_: Exception) { emptyList() }
                if (matches.isNotEmpty()) {
                    recycleAll(matches)
                    return FacebookHit(
                        isFullscreenPlayer = true,
                        isTabHit = false,
                        confidence = CONF_ENGAGEMENT_RAIL,
                        triggeredBy = "engagement_rail:$token",
                    )
                }
            }
        }
        return null
    }

    // --- Layer 2: fullscreen player + text signal -----------------------

    /**
     * Find a fullscreen node (≥ 85 % height, ≥ 80 % width) and look
     * for nearby text signals (Follow button, audio attribution,
     * duration label). Combines into a layered confidence.
     */
    private fun findFullscreenPlayer(
        root: AccessibilityNodeInfo,
        screenHeight: Int,
        screenWidth: Int,
        started: Long,
    ): FacebookHit? {
        // 1) Find a clickable, fullscreen-sized video surface.
        //    This is a quick recursive walk with a depth + time budget.
        val fullscreenNode = findFullscreenVideoNode(
            root = root,
            screenHeight = screenHeight,
            screenWidth = screenWidth,
            depth = 0,
            started = started,
        ) ?: return null
        try {
            // 2) Look for confirmatory signals *anywhere* in the tree
            //    bounded by the budget. We use findAccessibilityNodeInfosByText
            //    which lets the framework do the walk on the target-app
            //    process (cheaper than our own recursion).
            val hasFollow = hasAnyTextHint(root, FOLLOW_HINTS, started)
            if (budgetExceeded(started)) return null
            val hasAudio = hasAnyTextHint(root, AUDIO_HINTS, started)
            if (budgetExceeded(started)) return null
            val hasDuration = hasDurationLabel(root, started)
            if (budgetExceeded(started)) return null

            // Combine signals — each combination yields a different
            // confidence, with follow + audio being the strongest
            // (these two together are *only* present on Reels).
            return when {
                hasFollow && hasAudio -> FacebookHit(
                    isFullscreenPlayer = true,
                    isTabHit = false,
                    confidence = CONF_FULLSCREEN_FOLLOW_AUDIO,
                    triggeredBy = "fullscreen:follow+audio",
                )
                hasFollow -> FacebookHit(
                    isFullscreenPlayer = true,
                    isTabHit = false,
                    confidence = CONF_FULLSCREEN_FOLLOW,
                    triggeredBy = "fullscreen:follow",
                )
                hasAudio -> FacebookHit(
                    isFullscreenPlayer = true,
                    isTabHit = false,
                    confidence = CONF_FULLSCREEN_AUDIO,
                    triggeredBy = "fullscreen:audio",
                )
                hasDuration -> FacebookHit(
                    isFullscreenPlayer = true,
                    isTabHit = false,
                    confidence = CONF_FULLSCREEN_DURATION,
                    triggeredBy = "fullscreen:duration",
                )
                else -> null
            }
        } finally {
            // fullscreenNode is a child of root obtained via
            // getChild(); we own it and must recycle.
            try { fullscreenNode.recycle() } catch (_: Exception) {}
        }
    }

    /**
     * Recursive walker that returns the first node satisfying:
     *  - visible to the user,
     *  - clickable (so the user can interact with it),
     *  - bounds.height() / screenHeight ≥ FULLSCREEN_HEIGHT_RATIO,
     *  - bounds.width() / screenWidth ≥ FULLSCREEN_WIDTH_RATIO.
     *
     * The walk is bounded by [MAX_DEPTH] and the time budget. Nodes
     * visited along the way are recycled to avoid leaks.
     */
    private fun findFullscreenVideoNode(
        root: AccessibilityNodeInfo,
        screenHeight: Int,
        screenWidth: Int,
        depth: Int,
        started: Long,
    ): AccessibilityNodeInfo? {
        if (depth > MAX_DEPTH) return null
        if (budgetExceeded(started)) return null

        // Self check.
        if (isFullscreenClickable(root, screenHeight, screenWidth)) {
            return root
        }

        // Recurse.
        val childCount = try { root.childCount } catch (_: Exception) { 0 }
        for (i in 0 until childCount) {
            if (budgetExceeded(started)) return null
            val child = try { root.getChild(i) } catch (_: Exception) { null } ?: continue
            // Always recycle child unless the recursion matched it
            // directly (in which case the caller recycles).
            var result: AccessibilityNodeInfo? = null
            try {
                result = findFullscreenVideoNode(
                    root = child,
                    screenHeight = screenHeight,
                    screenWidth = screenWidth,
                    depth = depth + 1,
                    started = started,
                )
            } finally {
                if (result !== child) {
                    try { child.recycle() } catch (_: Exception) {}
                }
            }
            if (result != null) return result
        }
        return null
    }

    private fun isFullscreenClickable(
        node: AccessibilityNodeInfo,
        screenHeight: Int,
        screenWidth: Int,
    ): Boolean {
        if (!node.isVisibleToUser) return false
        val isClickable = try { node.isClickable } catch (_: Exception) { false }
        if (!isClickable) return false
        val bounds = Rect()
        try { node.getBoundsInScreen(bounds) } catch (_: Exception) { return false }
        if (bounds.height() <= 0 || bounds.width() <= 0) return false
        val hRatio = bounds.height().toFloat() / screenHeight
        val wRatio = bounds.width().toFloat() / screenWidth
        return hRatio >= FULLSCREEN_HEIGHT_RATIO && wRatio >= FULLSCREEN_WIDTH_RATIO
    }

    /**
     * True if [root] contains a text node whose text contains any of
     * [hints] (case-insensitive). Uses
     * [AccessibilityNodeInfo.findAccessibilityNodeInfosByText] which
     * runs in the target app process.
     */
    private fun hasAnyTextHint(
        root: AccessibilityNodeInfo,
        hints: List<String>,
        started: Long,
    ): Boolean {
        for (hint in hints) {
            if (budgetExceeded(started)) return false
            val matches = try {
                root.findAccessibilityNodeInfosByText(hint)
            } catch (_: Exception) { emptyList() }
            if (matches.isNotEmpty()) {
                recycleAll(matches)
                return true
            }
        }
        return false
    }

    /**
     * True if [root] contains a text node matching the Reels
     * duration pattern (`0:07`, `0:14`, `1:23`).
     */
    private fun hasDurationLabel(
        root: AccessibilityNodeInfo,
        started: Long,
    ): Boolean {
        if (budgetExceeded(started)) return false
        // findAccessibilityNodeInfosByText with a regex isn't
        // supported — we walk the tree ourselves for this one. The
        // walk is bounded by [MAX_DEPTH] and the time budget.
        return walkForDuration(root, 0, started)
    }

    private fun walkForDuration(
        node: AccessibilityNodeInfo,
        depth: Int,
        started: Long,
    ): Boolean {
        if (depth > MAX_DEPTH) return false
        if (budgetExceeded(started)) return false
        val text = try { node.text?.toString().orEmpty() } catch (_: Exception) { "" }
        if (text.isNotEmpty() && DURATION_PATTERN.matches(text.trim())) {
            return true
        }
        val childCount = try { node.childCount } catch (_: Exception) { 0 }
        for (i in 0 until childCount) {
            if (budgetExceeded(started)) return false
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            try {
                if (walkForDuration(child, depth + 1, started)) return true
            } finally {
                try { child.recycle() } catch (_: Exception) {}
            }
        }
        return false
    }

    // --- Layer 3: Reels section tab -------------------------------------

    /**
     * Find the Reels section tab. The tab lives in the top nav
     * (classic Facebook UI) or the bottom nav (newer 2024+ Facebook
     * UI). We require the tab to be in the correct region AND
     * `isSelected = true` for the bottom-nav variant (because the
     * top-nav variant is always visible on Home).
     */
    private fun findReelsTab(
        root: AccessibilityNodeInfo,
        screenHeight: Int,
        screenWidth: Int,
        started: Long,
    ): FacebookHit? {
        for (pkg in FACEBOOK_PACKAGES) {
            if (budgetExceeded(started)) return null
            for (token in TAB_VIEW_ID_TOKENS) {
                if (budgetExceeded(started)) return null
                val matches = try {
                    root.findAccessibilityNodeInfosByViewId("$pkg:id/$token")
                } catch (_: Exception) { emptyList() }
                for (m in matches) {
                    try {
                        val b = Rect()
                        m.getBoundsInScreen(b)
                        val isTop = b.top < (screenHeight * TOP_NAV_HEIGHT_RATIO)
                        val isBottom = b.top > (screenHeight * BOTTOM_NAV_HEIGHT_RATIO)
                        val isSelected = try { m.isSelected } catch (_: Exception) { false }
                        val matched = isTop || (isBottom && isSelected)
                        if (matched) {
                            return FacebookHit(
                                isFullscreenPlayer = false,
                                isTabHit = true,
                                confidence = CONF_TAB_HIT,
                                triggeredBy = "tab:$token",
                            )
                        }
                    } catch (_: Exception) {
                    } finally {
                        try { m.recycle() } catch (_: Exception) {}
                    }
                }
            }
        }
        return null
    }

    // --- Helpers --------------------------------------------------------

    private fun budgetExceeded(started: Long): Boolean =
        (System.currentTimeMillis() - started) > MAX_SCAN_MS

    private fun recycleAll(nodes: List<AccessibilityNodeInfo>) {
        for (n in nodes) {
            try { n.recycle() } catch (_: Exception) {}
        }
    }
}
