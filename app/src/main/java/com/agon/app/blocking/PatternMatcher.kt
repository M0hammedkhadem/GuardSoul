package com.agon.app.blocking

import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.utils.AppLogger

/**
 * Heuristic content detection engine for the Shortstop strategy.
 *
 * **Precision-first design (v2):**
 *
 * Unlike blunt keyword scanning, this engine treats each social app as a
 * target with its own **pattern signature** and computes a 0.0..1.0
 * **confidence score** per node. A score above
 * [BlockingConfig.SURGICAL_CONFIDENCE_THRESHOLD] classifies the surface
 * as "short-form video territory" and the accessibility service
 * injects the surgical overlay.
 *
 * **Critical safety rule** — *keywords are never matched on the main
 * detection path* because tokens like `"Shorts"`, `"Reels"`, `"ريلز"`,
 * `"شورت"` also appear in the home feed (Shorts shelf header, Reels
 * tab label, search suggestions, etc.). Matching them would trigger
 * random blocks on the main feed. We therefore restrict text matching
 * to the *tab-context* only (bottom 25% of the screen + `isSelected`),
 * which is the only place those words uniquely identify the user's
 * current destination.
 *
 * Detection now uses three safe layers:
 *
 * 1. **Surface view-ids** — `reel_watch_fragment_root`, `reels_video_container`,
 *    `clips_viewer_container`, `video_view` (TikTok), `spotlight_player`, etc.
 *    These IDs *only* exist on the actual short-form player surface, never
 *    on home feed cards.
 *
 * 2. **Class names** — `ReelsViewer`, `ReelViewer`, `ReelWatch`,
 *    `FeedVideoView`, `SpotlightFeed`, … — Java class names of the
 *    actual viewer component.
 *
 * 3. **Tab view-ids + isAtBottom + isSelected** — `pivot_bar_shorts`,
 *    `tab_clips`, etc. *Only* matched when the node is in the bottom
 *    25% of the screen and is currently selected. This is the only
 *    way the user can be **on** the Shorts/Reels tab from a sibling
 *    page.
 *
 * 4. **Size heuristic** — a clickable node that covers ≥85 % of the
 *    screen height is treated as a full-screen player surface.
 *
 * Pattern signatures are derived from a combination of:
 *   - view-id substrings (ReVanced patches, uBlock Origin DOM filter
 *     lists, public APK teardowns).
 *   - className substrings (Tab, Button, Title, RecyclerView, ...).
 *   - screen geometry (full-screen vertical video containers).
 *
 * Each signal contributes a small weight to the cumulative score; the
 * tree-walk returns the **maximum** score seen so we never downgrade
 * a clear hit to "uncertain" because of a deeper weak signal.
 */
class PatternMatcher(
    /**
     * Optional self-learning source. When present, [signatureFor]
     * will fall back to the learner for packages we have no curated
     * signature for, and [findCandidate] will feed new observations
     * to it.
     */
    private val learner: SignatureLearner? = null,
) {

    /**
     * Per-package pattern signature. Tokens are split into:
     *  - [surfaceViewIdTokens] — appear **only** on the actual short-form
     *    player surface (never on home feed cards). Matched at any depth.
     *  - [tabViewIdTokens] — appear in the bottom navigation bar.
     *    Matched **only** when the node is in the bottom 25 % of the
     *    screen **and** is currently selected.
     *  - [surfaceClassNameTokens] — Java class names of the actual
     *    viewer component. Matched at any depth.
     *  - [tabContentDescTokens] — text labels that uniquely identify
     *    the bottom-nav tab in the user's UI language (e.g. `"Shorts"`,
     *    `"Reels"`, `"ريلز"`, `"شورت"`). Matched **only** in tab-context
     *    (bottom 25 % + selected).
     */
    data class Signature(
        val surfaceViewIdTokens: List<String>,
        val tabViewIdTokens: List<String> = emptyList(),
        val surfaceClassNameTokens: List<String> = emptyList(),
        val tabContentDescTokens: List<String> = emptyList(),
    )

    /** A single weighted contribution to the cumulative confidence score. */
    internal data class Signal(val source: String, val weight: Float, val token: String)

    /** Result returned to [ShortstopAccessibilityService]. */
    data class Hit(
        val packageName: String,
        val surface: Surface,
        val confidence: Float,
        val triggeredBy: String,
        val node: AccessibilityNodeInfo,
        /**
         * `true` when the hit matched a *tab* descriptor (bottom-nav
         * element + isSelected). `false` when the hit matched the
         * actual short-form player surface.
         *
         * Used by the kick-out path to pick a more appropriate user
         * message ("Reels section blocked" vs "Reels video blocked").
         */
        val isTabHit: Boolean = false,
    ) {
        val shouldBlock: Boolean
            get() = confidence >= BlockingConfig.SURGICAL_CONFIDENCE_THRESHOLD
    }

    /** Which surface we matched. Used for analytics and overlay copy. */
    enum class Surface(val label: String) {
        FACEBOOK_REELS("Facebook Reels"),
        YOUTUBE_SHORTS("YouTube Shorts"),
        INSTAGRAM_REELS("Instagram Reels"),
        TIKTOK_FYP("TikTok For You"),
        SNAPCHAT_SPOTLIGHT("Snapchat Spotlight"),
        TWITTER_VIDEO("Twitter / X video"),
        UNKNOWN("Unknown"),
    }

    /**
     * Curated signatures for the apps we support. **No content-text
     * tokens appear in [Signature.surfaceViewIdTokens] or
     * [Signature.surfaceClassNameTokens]** — those fields are
     * restricted to view-ids and class names that exist **only** on
     * the short-form surface.
     */
    private val signatures: Map<String, Signature> = mapOf(
        "com.facebook.katana" to Signature(
            surfaceViewIdTokens = listOf(
                "reels_viewer_fragment_container",
                "reels_video_container",
                "reels_inner_video_container",
                "reel_viewer_container",
            ),
            tabViewIdTokens = listOf(
                "reels_tab",
                "tab_reels",
            ),
            surfaceClassNameTokens = listOf("ReelsViewer", "ReelsTabFragment", "ReelViewer"),
            tabContentDescTokens = listOf("reels", "reel", "ريلز"),
        ),
        "com.facebook.lite" to Signature(
            surfaceViewIdTokens = listOf(
                "reels_video_view",
                "reel_container",
            ),
            tabViewIdTokens = listOf(
                "reels_tab",
                "tab_reels",
            ),
            surfaceClassNameTokens = listOf("Reels"),
            tabContentDescTokens = listOf("reels", "reel", "ريلز"),
        ),
        "com.google.android.youtube" to Signature(
            surfaceViewIdTokens = listOf(
                "reel_watch_fragment_root",
                "reel_recycler",
                "reel_player_page_controller",
                "shorts_player",
                "reel_player",
                "shorts_video_player_view",
                "reels_player",
            ),
            tabViewIdTokens = listOf(
                "pivot_bar_shorts",
                "tab_shorts",
            ),
            surfaceClassNameTokens = listOf("ShortsPlayer", "ReelWatch"),
            tabContentDescTokens = listOf("Shorts", "Shorts player", "شورت", "شورتس"),
        ),
        "com.instagram.android" to Signature(
            surfaceViewIdTokens = listOf(
                "reels_video_container",
                "reel_viewer_container",
                "reels_clips_viewer_container",
                "clips_viewer_container",
            ),
            tabViewIdTokens = listOf(
                "tab_clips",
                "reels_tab",
                "clips_tab",
            ),
            surfaceClassNameTokens = listOf("ReelViewer", "ClipsViewer"),
            tabContentDescTokens = listOf("Reels", "Reel", "Clips", "ريلز"),
        ),
        "com.zhiliaoapp.musically" to Signature(
            surfaceViewIdTokens = listOf(
                "fyp_video_container",
                "for_you_video",
            ),
            surfaceClassNameTokens = listOf("FeedVideoView", "TikTokFeed"),
        ),
        "com.ss.android.ugc.trill" to Signature(
            surfaceViewIdTokens = listOf(
                "fyp_video_container",
            ),
            surfaceClassNameTokens = listOf("FeedVideoView"),
        ),
        "com.snapchat.android" to Signature(
            surfaceViewIdTokens = listOf(
                "spotlight_feed",
                "spotlight_player",
            ),
            surfaceClassNameTokens = listOf("SpotlightFeed"),
        ),
        // Twitter / X video surfaces. Twitter/X don't have a dedicated
        // "Reels" tab — videos play inline in the main timeline or in
        // the immersive full-screen player reached by tapping a tweet's
        // video. The view-id set below targets both. The tab list
        // intentionally stays empty: there is no stable bottom-bar tab
        // id for "Videos" across all builds/regions.
        "com.twitter.android" to Signature(
            surfaceViewIdTokens = listOf(
                "immersive_video_player_view",
                "video_player_view",
                "tweet_video_container",
                "video_tweet_player",
                "player_video_view",
            ),
            surfaceClassNameTokens = listOf(
                "ImmersiveVideoPlayerView",
                "VideoPlayerView",
                "VideoTweetView",
            ),
        ),
        // X is the re-branded Twitter app. The view-id naming is the
        // same — only the package changed.
        "com.x.android" to Signature(
            surfaceViewIdTokens = listOf(
                "immersive_video_player_view",
                "video_player_view",
                "tweet_video_container",
                "video_tweet_player",
                "player_video_view",
            ),
            surfaceClassNameTokens = listOf(
                "ImmersiveVideoPlayerView",
                "VideoPlayerView",
                "VideoTweetView",
            ),
        ),
    )

    /**
     * Look up the [Signature] for [packageName], or null if we don't
     * have a curated one and the learner hasn't promoted one yet.
     * The curated map takes priority; the learner is consulted only
     * as a fallback for unknown packages.
     */
    fun signatureFor(packageName: String): Signature? =
        signatures[packageName] ?: learner?.signatureFor(packageName)

    /** True iff we have a *hand-curated* signature for [packageName]. */
    fun hasCuratedSignature(packageName: String): Boolean = packageName in signatures

    /**
     * A *candidate* observation surfaced by the generic
     * size+clickable heuristic. Not strong enough to act on, but
     * strong enough to feed the [SignatureLearner] for later
     * promotion.
     */
    data class Candidate(
        val packageName: String,
        val viewId: String?,
        val className: String?,
        val heightRatio: Float,
    )

    fun surfaceFor(packageName: String): Surface = when {
        packageName.startsWith("com.facebook") -> Surface.FACEBOOK_REELS
        packageName.startsWith("com.google.android.youtube") -> Surface.YOUTUBE_SHORTS
        packageName.startsWith("com.instagram") -> Surface.INSTAGRAM_REELS
        packageName.startsWith("com.zhiliaoapp") ->
            Surface.TIKTOK_FYP
        packageName.startsWith("com.ss.android") -> Surface.TIKTOK_FYP
        packageName.startsWith("com.snapchat") -> Surface.SNAPCHAT_SPOTLIGHT
        packageName.startsWith("com.twitter") ||
            packageName.startsWith("com.x.android") -> Surface.TWITTER_VIDEO
        else -> Surface.UNKNOWN
    }

    /**
     * Walk [node] and return the strongest hit against [signature].
     * Returns null if no signal is found. The walk is bounded by
     * [BlockingConfig.SHORTSTOP_TREE_MAX_DEPTH] to keep the
     * accessibility callback inside a frame budget.
     *
     * **Audit #2 (FB-002 fix)** — the previous implementation
     * returned on the *first* node that had any signal, which
     * could mask a deeper, higher-confidence hit (a weak
     * ancestor signal stopping the recursion). We now walk
     * the whole tree and return the maximum-confidence hit
     * we observed.
     *
     * **Audit #3 (FB-003 fix)** — child nodes obtained via
     * [AccessibilityNodeInfo.getChild] must be recycled to
     * avoid leaking AccessibilityNodeInfo handles. The
     * recursion is wrapped in `try { … } finally { recycle }`
     * at every level.
     *
     * @param screenHeight  current screen height in pixels, used for
     *                      the "tab at bottom" check.
     * @param screenWidth   current screen width in pixels. Used for
     *                      the size heuristic (audit #5 — landscape
     *                      support) and split-screen.
     */
    fun findHit(
        packageName: String,
        node: AccessibilityNodeInfo?,
        signature: Signature?,
        screenHeight: Int,
        screenWidth: Int,
        deadlineElapsedMs: Long,
        depth: Int = 0,
    ): Hit? {
        if (node == null) return null
        if (depth > BlockingConfig.SHORTSTOP_TREE_MAX_DEPTH) return null
        if (System.currentTimeMillis() > deadlineElapsedMs) return null

        val signals = mutableListOf<Signal>()

        // ------------------------------------------------------------------
        // 1) Surface view-id substring match.
        //    These IDs exist *only* on the short-form player surface,
        //    so a match is unambiguous.
        // ------------------------------------------------------------------
        val viewId = node.viewIdResourceName
        if (!viewId.isNullOrEmpty()) {
            for (token in signature?.surfaceViewIdTokens ?: emptyList()) {
                if (viewId.contains(token, ignoreCase = true)) {
                    signals += Signal("surfaceViewId:$token", VIEW_ID_WEIGHT, token)
                    break
                }
            }
        }

        // ------------------------------------------------------------------
        // 2) Surface class-name match.
        //    Java class names of the actual viewer component.
        // ------------------------------------------------------------------
        val className = node.className?.toString().orEmpty()
        if (className.isNotEmpty()) {
            for (token in signature?.surfaceClassNameTokens ?: emptyList()) {
                if (className.contains(token, ignoreCase = true)) {
                    signals += Signal("surfaceClassName:$token", CLASS_NAME_WEIGHT, token)
                    break
                }
            }
        }

        // ------------------------------------------------------------------
        // 3) Tab-context view-id + (optional) contentDescription.
        //    Only valid if the node is in the bottom 25% of the screen
        //    AND is currently selected. This is the only safe way to
        //    use "Shorts"/"Reels" text — they ONLY uniquely identify
        //    the user's destination when they are on the bottom nav.
        // ------------------------------------------------------------------
        if (signature != null && screenHeight > 0 && node.isVisibleToUser) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val isAtBottom = bounds.top > (screenHeight * BlockingConfig.BOTTOM_TAB_HEIGHT_RATIO)
            val isSelected = node.isCurrentlySelected()
            if (isAtBottom && isSelected) {
                for (token in signature.tabViewIdTokens) {
                    if (!viewId.isNullOrEmpty() && viewId.contains(token, ignoreCase = true)) {
                        signals += Signal("tabViewId:$token", TAB_VIEW_ID_WEIGHT, token)
                        break
                    }
                }
                val desc = node.contentDescription?.toString().orEmpty()
                if (desc.isNotEmpty()) {
                    for (token in signature.tabContentDescTokens) {
                        if (desc.contains(token, ignoreCase = true)) {
                            signals += Signal("tabContentDesc:$token", TAB_DESC_WEIGHT, token)
                            break
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------------
        // 4) Size-based: a clickable node that covers ≥85 % of **either**
        //    the screen height OR the screen width is treated as a
        //    full-screen player.
        //
        //    Audit #5: the old check was `bounds.height() / screenHeight`
        //    only — that breaks in landscape orientation, where a
        //    short-video surface is now wide rather than tall.
        //    Audit #6: the ratio is computed against the *root window*
        //    bounds, not the physical display, so split-screen /
        //    multi-window mode produces sane numbers.
        // ------------------------------------------------------------------
        if (node.isVisibleToUser && screenHeight > 0) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.height() > 0 && bounds.width() > 0) {
                val widthRef = if (screenWidth > 0) screenWidth else screenHeight
                val heightRatio = bounds.height().toFloat() / screenHeight
                val widthRatio = bounds.width().toFloat() / widthRef
                val ratio = maxOf(heightRatio, widthRatio)
                if (ratio >= BlockingConfig.HIGH_CONFIDENCE_HEIGHT_RATIO &&
                    node.isClickable) {
                    signals += Signal("size:${ratio}", SIZE_WEIGHT, "")
                }
            }
        }

        val confidence = signals.sumOf { it.weight.toDouble() }.toFloat().coerceIn(0f, 1f)
        val isTab = signals.any {
            it.source.startsWith("tabViewId:") || it.source.startsWith("tabContentDesc:")
        }

        // Build the candidate for this node (if it has any signal).
        var bestHit: Hit? = null
        if (signals.isNotEmpty() && confidence > 0f) {
            val triggered = signals.maxByOrNull { it.weight }?.source ?: "unknown"
            bestHit = Hit(
                packageName = packageName,
                surface = surfaceFor(packageName),
                confidence = confidence,
                triggeredBy = triggered,
                node = node,
                isTabHit = isTab,
            )
        }

        // 5) Recurse into children. Audit #3: every child obtained
        //    via getChild() must be recycled after use, otherwise
        //    we leak an AccessibilityNodeInfo handle per recursion.
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val childHit = findHit(
                    packageName = packageName,
                    node = child,
                    signature = signature,
                    screenHeight = screenHeight,
                    screenWidth = screenWidth,
                    deadlineElapsedMs = deadlineElapsedMs,
                    depth = depth + 1,
                )
                if (childHit != null && (bestHit == null || childHit.confidence > bestHit.confidence)) {
                    bestHit = childHit
                }
            } finally {
                child.recycle()
            }
        }
        return bestHit
    }

    /**
     * Generic "is this app showing a full-screen player right now?"
     * probe. Used **only** to feed the [SignatureLearner] for
     * packages we don't have a curated signature for.
     *
     * Returns the deepest clickable full-screen node (or the
     * root) along with the [Candidate] tokens that the learner
     * will dedupe + count. Returns null if no plausible
     * full-screen surface exists.
     *
     * Deliberately does **not** consult [signatures] — it's
     * the observation path, not the enforcement path.
     */
    fun findCandidate(
        packageName: String,
        node: AccessibilityNodeInfo?,
        screenHeight: Int,
        screenWidth: Int,
        deadlineElapsedMs: Long,
        depth: Int = 0,
    ): Candidate? {
        if (node == null) return null
        if (depth > BlockingConfig.SHORTSTOP_TREE_MAX_DEPTH) return null
        if (System.currentTimeMillis() > deadlineElapsedMs) return null

        var bestCandidate: Candidate? = null

        // Plausibility: a clickable, visible, near-full-bounds node.
        if (node.isVisibleToUser && node.isClickable && screenHeight > 0) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.height() > 0 && bounds.width() > 0) {
                val widthRef = if (screenWidth > 0) screenWidth else screenHeight
                val ratio = maxOf(
                    bounds.height().toFloat() / screenHeight,
                    bounds.width().toFloat() / widthRef,
                )
                if (ratio >= BlockingConfig.HIGH_CONFIDENCE_HEIGHT_RATIO) {
                    val viewId = node.viewIdResourceName
                    val className = node.className?.toString()
                    bestCandidate = Candidate(
                        packageName = packageName,
                        viewId = viewId,
                        className = className,
                        heightRatio = ratio,
                    )
                }
            }
        }

        // 5) Recurse — prefer the deepest (most specific) node.
        //    Audit #3 (FB-005): recycle each child obtained via
        //    getChild() to avoid leaking AccessibilityNodeInfo
        //    handles on every accessibility event.
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val deeper = findCandidate(
                    packageName = packageName,
                    node = child,
                    screenHeight = screenHeight,
                    screenWidth = screenWidth,
                    deadlineElapsedMs = deadlineElapsedMs,
                    depth = depth + 1,
                )
                if (deeper != null) bestCandidate = deeper
            } finally {
                child.recycle()
            }
        }
        return bestCandidate
    }

    companion object {
        private const val VIEW_ID_WEIGHT: Float = 0.55f
        private const val CLASS_NAME_WEIGHT: Float = 0.45f
        private const val TAB_VIEW_ID_WEIGHT: Float = 0.50f
        private const val TAB_DESC_WEIGHT: Float = 0.30f
        private const val SIZE_WEIGHT: Float = 0.25f

        /**
         * One-stop helper for the accessibility service: build a
         * matcher, run [findHit], log a single debug line, and return.
         *
         * Synchronous by design — called from the accessibility
         * callback thread where blocking is forbidden. The learner
         * observation path is intentionally *not* triggered from
         * here; callers that have a [SignatureLearner] should also
         * call [findCandidateForLearner] and feed the result
         * asynchronously on a coroutine.
         */
        fun detect(
            packageName: String,
            root: AccessibilityNodeInfo,
            screenHeight: Int,
            screenWidth: Int,
        ): Hit? {
            val matcher = PatternMatcher()
            val sig = matcher.signatureFor(packageName)
            val started = System.currentTimeMillis()
            val deadline = started + BlockingConfig.SHORTSTOP_TREE_BUDGET_MS
            val hit = matcher.findHit(packageName, root, sig, screenHeight, screenWidth, deadline)
            if (hit != null) {
                AppLogger.d(
                    "Shortstop: ${hit.surface} hit ${hit.confidence} via ${hit.triggeredBy} " +
                        "in pkg=$packageName"
                )
            }
            return hit
        }

        /**
         * Walk the tree looking for a generic full-screen-player
         * candidate that [SignatureLearner] can record. Returns
         * null for packages with a curated signature (no need to
         * learn what we already know) or for trees that don't
         * contain a plausible player surface.
         *
         * Cheap enough to call on the accessibility thread; the
         * resulting observation is then fed to the learner on a
         * coroutine by the caller.
         */
        fun findCandidateForLearner(
            learner: SignatureLearner,
            packageName: String,
            root: AccessibilityNodeInfo,
            screenHeight: Int,
            screenWidth: Int,
        ): Candidate? {
            val matcher = PatternMatcher(learner)
            if (matcher.hasCuratedSignature(packageName)) return null
            val started = System.currentTimeMillis()
            val deadline = started + BlockingConfig.SHORTSTOP_TREE_BUDGET_MS
            return matcher.findCandidate(packageName, root, screenHeight, screenWidth, deadline)
        }
    }
}
