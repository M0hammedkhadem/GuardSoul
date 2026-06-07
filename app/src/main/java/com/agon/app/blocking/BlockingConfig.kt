package com.agon.app.blocking

/**
 * Centralized tuning constants for the foreground-app blocking pipeline.
 *
 * Keep these in one place so:
 * - tests can override them,
 * - the rationale for each value is documented once,
 * - and adjacent subsystems (`ContentFilterEngine`, `UninstallGuardEngine`,
 *   `ShortstopEngine`, `AiExplorerEngine`, …) can share the same language.
 */
object BlockingConfig {
    /**
     * How often the foreground app is sampled while the shield is on.
     * Tuned down from 500 ms → 200 ms for snappier feedback.
     */
    const val POLL_INTERVAL_MS: Long = 200L

    /**
     * How often the foreground app is sampled while the shield is **off**.
     * Slower polling saves battery on the user's device.
     */
    const val SHIELD_OFF_INTERVAL_MS: Long = 5_000L

    /**
     * Per-package cooldown: we only record a new block event for the same
     * package after this delay. Prevents logging the same long block session
     * over and over in the database.
     */
    const val MIN_TIME_BETWEEN_BLOCKS_MS: Long = 2_000L

    /**
     * Hard cap on the size of the in-memory "last block time" map. When the
     * map grows past this we evict the oldest entry — keeps memory bounded
     * when the user installs many apps over time.
     */
    const val MAX_TRACKED_PACKAGES: Int = 200

    /**
     * Foreground app block notifications are deduplicated per package for
     * this long, to avoid spamming the user with overlapping toasts.
     * Tuned down from 800 ms → 150 ms for sub-frame response.
     */
    const val BLOCK_COOLDOWN_MS: Long = 150L

    // --- Short-video / Reels detection thresholds (ShortstopEngine) ---

    /** A video is considered "full screen" if it covers this fraction of the screen height. */
    const val FULL_SCREEN_HEIGHT_RATIO: Float = 0.85f

    /** …and this fraction of the screen width. */
    const val FULL_SCREEN_WIDTH_RATIO: Float = 0.80f

    /**
     * How often the time-of-day caches (school-time, bedtime) are refreshed
     * while the service is alive. 30 s is cheap and catches edge-of-window
     * transitions almost immediately.
     */
    const val TIME_OF_DAY_REFRESH_MS: Long = 30_000L

    /**
     * Cooldown for re-showing the block overlay for the same app. Prevents
     * the overlay from flickering on every poll cycle.
     * Tuned down from 2000 ms → 500 ms for snappier re-block.
     */
    const val OVERLAY_RELAUNCH_MS: Long = 500L

    /**
     * Cooldown between consecutive blocks for social apps (full mode).
     * Shorter than MIN_TIME_BETWEEN_BLOCKS_MS so re-opening a blocked social
     * app is re-blocked almost instantly.
     */
    const val SOCIAL_BLOCK_COOLDOWN_MS: Long = 500L

    /**
      * Top-edge threshold in **density-independent pixels (dp)**. Anything
      * rendered closer than this to the top of the screen is treated as
      * top-of-feed (and therefore a real short, not a comment/preview).
      * 150 dp ≈ 1.5 cm on a typical phone — works for both phones and tablets.
      */
     const val TOP_BOUNDARY_DP_THRESHOLD: Int = 150

    /** Height of the bottom navigation bar, as a ratio of screen height. */
    const val BOTTOM_TAB_HEIGHT_RATIO: Float = 0.75f

    /** Confidence tier thresholds (also height ratios). */
    const val HIGH_CONFIDENCE_HEIGHT_RATIO: Float = 0.85f
    const val MEDIUM_CONFIDENCE_HEIGHT_RATIO: Float = 0.5f
    const val LOW_CONFIDENCE_HEIGHT_RATIO: Float = 0.3f

    /**
     * Throttle window-content dumps to this many ms.
     *
     * Audit #2: at 50 ms the user can stay on the short-video
     * surface for up to one frame between events — enough to
     * watch the first ~50 ms of a clip. We disable the throttle
     * entirely (0 ms = "process every event") and rely on the
     * FastDetector cache + tree-budget to keep CPU bounded.
     */
    const val WINDOW_CONTENT_THROTTLE_MS: Long = 0L

    // --- Short-video hardened detection (YouTube Shorts focus) -------------

    /**
     * How many times to retry the YouTube-Shorts detection after the first
     * pass, to absorb the asynchronous UI rendering that happens when a
     * Shorts screen is just opening. Total worst-case wait = RETRY × RETRY_DELAY.
     */
    const val SHORTS_DETECT_RETRY_COUNT: Int = 3

    /** Delay between retries (ms). Keeps the UX snappy (≤ 600 ms total). */
    const val SHORTS_DETECT_RETRY_DELAY_MS: Long = 200L

    /**
     * Hard upper bound (ms) on the time we spend scanning the accessibility
     * tree. The YouTube app's hierarchy can be large; aborting protects CPU.
     */
    const val SHORTS_SCAN_TIMEOUT_MS: Long = 100L

    /**
     * Maximum tree depth for the recursive Shorts scan. YouTube's tree can
     * be deeply nested; we cap to keep detection under 16 ms.
     */
    const val SHORTS_SCAN_MAX_DEPTH: Int = 30

    // --- Porn / VPN DNS constants ---

    /** CleanBrowsing Family Filter host (Device-Owner Private DNS). */
    const val CLEANBROWSING_FAMILY_HOST: String = "family-filter-dns.cleanbrowsing.org"

    // -- BATCH-Q (Family DNS addition) ---------------------------------
    // The VPN fallback used to hard-code the **Adult** filter, which
    // is more aggressive than what most "clean / safe search" users
    // want. We now expose three well-known family DNS providers and
    // tier them so the user gets the *cleanest possible* upstream
    // by default, with progressively stronger filters as fallback.

    /** OpenDNS FamilyShield (Cisco) — adult-only, very stable. */
    const val OPENDNS_FAMILY_DNS_1: String = "208.67.222.123"
    const val OPENDNS_FAMILY_DNS_2: String = "208.67.220.123"
    const val OPENDNS_FAMILY_DNS_V6_1: String = "2620:119:35::35"
    const val OPENDNS_FAMILY_DNS_V6_2: String = "2620:119:53::53"

    /** Cloudflare for Families — adult + malware (1.1.1.3). */
    const val CLOUDFLARE_FAMILY_DNS_1: String = "1.1.1.3"
    const val CLOUDFLARE_FAMILY_DNS_2: String = "1.0.0.3"
    const val CLOUDFLARE_FAMILY_DNS_V6_1: String = "2606:4700:4700::1113"
    const val CLOUDFLARE_FAMILY_DNS_V6_2: String = "2606:4700:4700::1003"

    /** CleanBrowsing Family IPs (in addition to the hostname above). */
    const val CLEANBROWSING_FAMILY_DNS_1: String = "185.228.168.168"
    const val CLEANBROWSING_FAMILY_DNS_2: String = "185.228.169.168"
    const val CLEANBROWSING_FAMILY_DNS_V6_1: String = "2a0d:2a00:1::2"
    const val CLEANBROWSING_FAMILY_DNS_V6_2: String = "2a0d:2a00:2::2"

    /** CleanBrowsing Adult Filter DNS v4 primary (most aggressive — final fallback). */
    const val CLEANBROWSING_ADULT_DNS_1: String = "185.228.168.10"
    /** CleanBrowsing Adult Filter DNS v4 secondary. */
    const val CLEANBROWSING_ADULT_DNS_2: String = "185.228.169.11"
    /** CleanBrowsing Adult Filter DNS v6 primary. */
    const val CLEANBROWSING_ADULT_DNS_V6_1: String = "2a0d:2a00:1::"
    /** CleanBrowsing Adult Filter DNS v6 secondary. */
    const val CLEANBROWSING_ADULT_DNS_V6_2: String = "2a0d:2a00:2::"

    /** MTU used by the local VPN tunnel. */
    const val VPN_MTU: Int = 1500

    /** VPN tunnel /32 address for the client. */
    const val VPN_CLIENT_ADDRESS: String = "10.0.0.2"
    /** VPN tunnel prefix length. */
    const val VPN_CLIENT_PREFIX: Int = 32
    /** VPN tunnel v6 /128 address for the client. */
    const val VPN_CLIENT_V6_ADDRESS: String = "fd00::2"
    /** VPN tunnel v6 prefix length. */
    const val VPN_CLIENT_V6_PREFIX: Int = 128

    // =====================================================================
    // Shortstop (Surgical Blocking) constants
    // See PROJECT_MAP.md § Shortstop Strategy.
    // ---------------------------------------------------------------------

    /**
     * Confidence threshold (0.0..1.0) above which a tree-walk hit is
     * treated as a confirmed "short-form surface" and an intervention is
     * triggered. The Shortstop strategy (§3.2) prescribes 80 %.
     */
    const val SURGICAL_CONFIDENCE_THRESHOLD: Float = 0.80f

    /**
     * How many scroll events on the same package within
     * [SCROLL_WINDOW_MS] we accept before injecting the break overlay.
     * Tuned to match the Shortstop strategy (10 scrolls in 60 s).
     */
    const val SCROLL_VELOCITY_THRESHOLD: Int = 10
    const val SCROLL_WINDOW_MS: Long = 60_000L

    /**
     * Y-axis velocity (pixels/second) that qualifies as "addictive
     * scrolling". Anything below this is treated as deliberate reading.
     */
    const val SCROLL_VELOCITY_PX_PER_SEC: Float = 3_000f

    /**
     * If the user stays on the same short-form surface (Reels / Shorts)
     * for this long, the overlay is injected. 5 minutes is the
     * Shortstop default.
     */
    const val TIME_ON_CONTENT_THRESHOLD_MS: Long = 5L * 60L * 1_000L

    /**
     * WindowId-cache TTL. We cache the (pkg, windowId) → last verdict
     * for this long so the next event for the same window is O(1).
     */
    const val WINDOW_CACHE_TTL_MS: Long = 1_500L

    /**
     * Inactivity period after which a per-package scroll counter is
     * reset. Stops the user from being punished for slow sessions
     * spread over a long time.
     */
    const val SCROLL_INACTIVITY_RESET_MS: Long = 60_000L

    /**
     * Minimum confidence margin between a "Shorts/Reels hit" and a
     * "safe surface" before we treat them as distinct. Stops us from
     * toggling the overlay on for sub-second false positives.
     */
    const val SHORTSTOP_DEBOUNCE_MS: Long = 750L

    /**
     * Hard upper bound (ms) on a single tree scan. Bounded so the
     * accessibility callback returns inside a frame budget even on
     * pathological trees. Tuned down from 80 ms → 35 ms (sub-frame).
     */
    const val SHORTSTOP_TREE_BUDGET_MS: Long = 35L

    /** Maximum recursive depth for the Shortstop tree scanner. */
    const val SHORTSTOP_TREE_MAX_DEPTH: Int = 16

    /**
     * Per-package cooldown for re-applying the surgical overlay.
     * Prevents flicker when the user is bouncing between comments
     * and the player. Tuned down from 1500 ms → 400 ms.
     */
    const val SHORTSTOP_OVERLAY_COOLDOWN_MS: Long = 400L

    /**
     * Per-package cooldown for [GLOBAL_ACTION_BACK] kick-outs.
     *
     * Audit #3: 800 ms gave the user a long-enough window to
     * swipe back into the app and resume watching before the
     * next detection event fired. We tighten this to 200 ms —
     * the shortest interval the system can reliably distinguish
     * a real new entry into the surface from event-storm noise.
     */
    const val SHORTSTOP_KICKOUT_COOLDOWN_MS: Long = 200L

    /**
     * Suppression window *after* a kick-out fires. If the user
     * re-enters the same short-form app inside this window, we
     * kick them out again immediately (no detection work needed).
     *
     * Audit #9: without this, the user can simply press the app's
     * icon in the Recents screen and be back on Reels in < 1 s.
     * The 5 s window mirrors the "Take 5-min break" affordance
     * — long enough that the user is unlikely to keep trying, but
     * not so long that it feels punitive.
     */
    const val SHORTSTOP_RESURFACE_GUARD_MS: Long = 5_000L

    /**
     * Delay between the [GLOBAL_ACTION_BACK] and the forced
     * [GLOBAL_ACTION_HOME] in [ShortstopEngine.kickOutFromShort].
     *
     * **BATCH-P**: tightened from 80 ms → 30 ms to meet the
     * sub-frame (≤ 50 ms) detection target. The BACK transition
     * still queues its animation, but HOME is dispatched before
     * the BACK animation finishes — Android merges the two
     * transitions, so the user perceives a single "swoop" off the
     * short-video surface. A long delay (e.g. 80 ms) meant the
     * user watched 1-2 frames of the short before HOME landed.
     */
    const val SHORTSTOP_FORCE_HOME_DELAY_MS: Long = 30L

    /**
     * When the FastDetector returns a "tab hit" (a high-confidence
     * detection that the user just tapped a Reels/Shorts tab), we
     * skip the BACK step entirely and dispatch HOME immediately.
     * Rationale: the player hasn't actually started yet, so there
     * is nothing on the back stack to pop. This brings the
     * "tap-Reels-tab → home" end-to-end latency under 10 ms
     * (HOME itself takes ~5 ms to dispatch through the framework).
     */
    const val SHORTSTOP_FAST_KICK_NO_DELAY: Boolean = true

    /**
     * Daily quota (minutes) for short-form content. After the user
     * has spent this much time on Reels/Shorts in a 24-hour window
     * the overlay stays up until the next day. 0 disables the quota.
     */
    const val DEFAULT_DAILY_QUOTA_MIN: Int = 10
}
