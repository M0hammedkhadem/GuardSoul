package com.agon.app.blocking

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.agon.app.GuardianApp
import com.agon.app.R
import com.agon.app.guardianApp
import com.agon.app.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Shortstop surgical short-video blocker engine.
 *
 * Extracted from the legacy [ShortstopAccessibilityService] so it can
 * run inside a single unified accessibility service
 * ([com.agon.app.services.GuardSoulAccessibilityService]) alongside
 * the [ContentFilterEngine], [UninstallGuardEngine], and [AiExplorerEngine].
 *
 * The engine is responsible for detecting Reels / Shorts / TikTok FYP
 * / Snapchat Spotlight / Twitter-X video surfaces and forcibly kicking
 * the user back to the launcher. It also runs the scroll-interception
 * and daily-quota layers, and the self-learning engine.
 *
 * The engine holds a reference to the host service so it can call
 * `performGlobalAction` and `rootInActiveWindow`. The host is owned
 * by the Android framework — never `null` while [onAccessibilityEvent]
 * is being invoked.
 */
class ShortstopEngine(private val host: AccessibilityService) {

    companion object {
        /** Packages that have a curated [PatternMatcher.Signature]. */
        val TARGET_PACKAGES: Set<String> = setOf(
            "com.facebook.katana",
            "com.facebook.lite",
            "com.google.android.youtube",
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.snapchat.android",
            "com.twitter.android",
            "com.x.android",
        )

        /** True when the current foreground package is a Shortstop target. */
        fun isTarget(pkg: String?): Boolean = pkg != null && pkg in TARGET_PACKAGES
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // --- Detection state --------------------------------------------------

    private val matcher = PatternMatcher()
    private val fastDetector = FastDetector()
    private val scrollInterception = ScrollInterception()
    /** **BATCH-R**: Facebook-Reels-specific layered detector. */
    private val facebookBlocker = FacebookBlockerEngine()
    private val overlay by lazy { ShortsMaskOverlay(host) }

    /**
     * Self-learning engine. Lazily created (in [start]) because the
     * [SignatureLearner] needs a [com.agon.app.data.settings.AppSettings]
     * instance from the [GuardianApp] container, which isn't safe to
     * read from a property initializer.
     */
    @Volatile private var learner: SignatureLearner? = null

    /** Per-package last block timestamp (anti-flicker / cooldown). */
    private val lastBlockAt = ConcurrentHashMap<String, Long>()

    /**
     * Per-package last kick-out timestamp. Prevents the back-press
     * loop when one event triggers multiple times in quick
     * succession.
     */
    private val lastKickAt = ConcurrentHashMap<String, Long>()

    /**
     * Per-package last TYPE_WINDOW_CONTENT_CHANGED timestamp. Used to
     * throttle content-change events that fire dozens of times per
     * second in apps like YouTube / Instagram.
     */
    private val lastContentChangeAt = ConcurrentHashMap<String, Long>()

    /** Per-package cached mode (per-app surgical / full / off). */
    @Volatile private var cachedShieldActive = false
    @Volatile private var cachedInstagramMode = "off"
    @Volatile private var cachedYoutubeMode = "off"
    @Volatile private var cachedFacebookMode = "off"
    @Volatile private var cachedTiktokBlocked = false
    @Volatile private var cachedSnapchatBlocked = false
    @Volatile private var cachedTwitterBlocked = false
    @Volatile private var cachedBlockedHourActive = false
    @Volatile private var cachedDailyQuotaExceeded = false
    @Volatile private var cachedBreakActive = false
    @Volatile private var cachedDailyQuotaMinutes = BlockingConfig.DEFAULT_DAILY_QUOTA_MIN
    @Volatile private var cachedBreakIntervalMinutes = 15
    @Volatile private var cachedMinutesSpentToday = 0

    /**
     * Per-package timestamp at which the user *entered* a
     * short-form surface. Used to compute minutes-spent on the
     * surface so the daily quota can be enforced. We persist the
     * accumulated minutes back to [AppSettings] on surface-left so
     * the count survives process death / reboot.
     */
    private val surfaceEnteredMs = ConcurrentHashMap<String, Long>()

    /** Public StateFlow the UI subscribes to. */
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** UI-facing status snapshot. */
    data class Status(
        val overlayShown: Boolean = false,
        val lastSurface: PatternMatcher.Surface = PatternMatcher.Surface.UNKNOWN,
        val lastConfidence: Float = 0f,
        val lastReason: String = "idle",
        val targetPackage: String? = null,
    )

    /** Subscribe to settings flows. Called from the host. */
    fun start() {
        serviceScope.launch {
            try {
                val app = host.applicationContext as GuardianApp
                val settings = app.repository.getAppSettings()
                // Initialise the self-learning engine. We honour the
                // `learnerEnabled` flag from DataStore so the user
                // can opt out of the auto-discovery in the debug
                // screen.
                val enabled = settings.learnerEnabledFlow.first()
                if (enabled) {
                    learner = SignatureLearner(settings).also { it.load() }
                }
                launch { settings.shieldActiveFlow.collect { cachedShieldActive = it } }
                launch { settings.instagramModeFlow.collect { cachedInstagramMode = it } }
                launch { settings.youtubeModeFlow.collect { cachedYoutubeMode = it } }
                launch { settings.facebookModeFlow.collect { cachedFacebookMode = it } }
                launch { settings.socialTiktokFlow.collect { cachedTiktokBlocked = it } }
                launch { settings.socialSnapchatFlow.collect { cachedSnapchatBlocked = it } }
                launch { settings.socialTwitterFlow.collect { cachedTwitterBlocked = it } }
                // Scheduling
                launch {
                    settings.shortstopBlockedHourActiveFlow.collect { cachedBlockedHourActive = it }
                }
                launch {
                    settings.shortstopDailyQuotaExceededFlow.collect { cachedDailyQuotaExceeded = it }
                }
                launch {
                    settings.shortstopBreakActiveFlow.collect { cachedBreakActive = it }
                }
                launch {
                    settings.shortstopDailyQuotaMinutesFlow.collect { cachedDailyQuotaMinutes = it }
                }
                launch {
                    settings.shortstopBreakIntervalMinutesFlow.collect { cachedBreakIntervalMinutes = it }
                }
                launch {
                    settings.shortstopMinutesSpentTodayFlow.collect { cachedMinutesSpentToday = it }
                }
                // Periodic janitor for the self-learning engine —
                // drops signatures the user hasn't opened in
                // 14 days. Runs every 6 h.
                if (enabled) {
                    launch {
                        while (true) {
                            kotlinx.coroutines.delay(6 * 60 * 60 * 1000L)
                            val stale = learner?.pruneStale().orEmpty()
                            if (stale.isNotEmpty()) {
                                AppLogger.d("Shortstop: learner pruned ${stale.size} stale sigs: $stale")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Shortstop: failed to subscribe to settings: ${e.message}")
            }
        }
        AppLogger.d("ShortstopEngine started")
    }

    /** Tear down. Flushes pending minutes-spent counters. */
    fun stop() {
        overlay.dismiss()
        // Cancel the per-engine scope so collectors, the learner
        // pruner, and any in-flight persistSurfaceLeft launch all
        // unwind cleanly. Without this, onAccessibilityEvent is
        // never called again but the collectors keep running
        // (and the pruner keeps deleting learned signatures).
        serviceScope.cancel()
        // Flush any in-flight short-form sessions so the daily
        // counter stays accurate across service restarts.
        for (pkg in surfaceEnteredMs.keys.toList()) {
            persistSurfaceLeft(pkg)
        }
        surfaceEnteredMs.clear()
        scrollInterception.resetAll()
        fastDetector.invalidateAll()
    }

    /** Called from the host when the framework interrupts us. */
    fun onInterrupt() {
        overlay.dismiss()
    }

    fun onAccessibilityEvent(
        event: AccessibilityEvent,
        preFetchedRoot: AccessibilityNodeInfo? = null,
    ) {
        val eventStart = System.currentTimeMillis()
        val pkg = event.packageName?.toString() ?: return
        if (!isTarget(pkg)) return
        if (!cachedShieldActive) return

        // 1) Mode filter: skip if the user has set this app to "off".
        if (!isAppBlockingEnabled(pkg)) return

        val now = System.currentTimeMillis()
        val eventType = event.eventType

        // 1.b) Audit #9 — Resurface guard. If the user has just
        //       been kicked out of this app (within the last
        //       [BlockingConfig.SHORTSTOP_RESURFACE_GUARD_MS] ms),
        //       re-kick immediately. This closes the "swipe back
        //       into the app" bypass where the user simply re-opens
        //       the app from Recents before the kickout cooldown
        //       expires. We re-run a full kick so the user is sent
        //       home again, then the guard window extends.
        val lastKick = lastKickAt[pkg] ?: 0L
        if (now - lastKick < BlockingConfig.SHORTSTOP_RESURFACE_GUARD_MS &&
            // Allow VIEW_CLICKED + WINDOW_STATE_CHANGED to retrigger
            // the guard; otherwise the guard fires on every
            // content-change event and we spam the user with
            // multiple GLOBAL_ACTION_HOMEs in a row.
            (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED)
        ) {
            kickOutFromShort(
                pkg = pkg,
                surface = matcher.surfaceFor(pkg),
                isTab = false,
                reason = "resurface-guard",
            )
            return
        }

        // 2) Scroll events — feed the interception layer.
        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val decision = scrollInterception.onScroll(event, now)
            ScrollInterception.log(decision)
            if (decision.isAddictive) {
                showFullScreenMask(
                    pkg,
                    "Time for a break",
                    "You've been scrolling fast for a while. Take a deep breath.",
                )
            }
            publishStatus(pkg, PatternMatcher.Surface.UNKNOWN, 0f, decision.reason)
            BenchmarkLogger.onEvent(
                pkg, eventType, fastMs = 0L, slowMs = 0L,
                verdict = "scroll:${decision.reason}"
            )
            return
        }

        // 2.b) Click events — bypass #1 in the audit. The user might
        //      have just tapped a Reels tab, a short-video thumbnail,
        //      or a deep-link entry point. The accessibility config
        //      requests `typeViewClicked` for exactly this case, but
        //      the previous implementation dropped these events on
        //      the floor. We now invalidate the FastDetector cache
        //      (because the click may have changed the surface) and
        //      fall through to the normal fast/slow detection path.
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            fastDetector.invalidateWindow(pkg, event.windowId)
        }

        // 3) Window events — run the fast detector, then fall back to
        //    the full pattern matcher if needed.
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_CLICKED
        ) return

        // Throttle TYPE_WINDOW_CONTENT_CHANGED to keep CPU under control.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val last = lastContentChangeAt[pkg] ?: 0L
            if (now - last < BlockingConfig.WINDOW_CONTENT_THROTTLE_MS) return
            lastContentChangeAt[pkg] = now
        }

        // 3.a) Rule-engine verdict (blocked hours / quota / break).
        val ruleCfg = RuleEngine.Config(
            blockedHourActive = cachedBlockedHourActive,
            dailyQuotaExceeded = cachedDailyQuotaExceeded,
            breakActive = cachedBreakActive,
            minutesSpentToday = cachedMinutesSpentToday,
            dailyQuotaMinutes = cachedDailyQuotaMinutes,
            breakIntervalMinutes = cachedBreakIntervalMinutes,
        )
        val ruleVerdict = RuleEngine(ruleCfg).evaluate(now)
        if (ruleVerdict.shouldBlock) {
            showFullScreenMask(pkg, ruleCopy(pkg, ruleVerdict.reason), ruleVerdict.message)
            publishStatus(pkg, matcher.surfaceFor(pkg), 1.0f, ruleVerdict.reason.name)
            val total = System.currentTimeMillis() - eventStart
            BenchmarkLogger.onEvent(
                pkg, eventType, fastMs = total, slowMs = 0L,
                verdict = "rule:${ruleVerdict.reason.name}"
            )
            return
        }

        // 3.b) FAST PATH — FastDetector (target: ≤ 10 ms).
        //      Uses event.source first, then windowId cache, then
        //      targeted view-id lookup. Avoids the expensive
        //      rootInActiveWindow() call whenever possible.
        //      **BATCH-P**: pass `preFetchedRoot` so the viewId
        //      lookup can search the *root* (not the event source)
        //      — closing the partial-blocking gap when the user
        //      taps a Reels tab and the source is the tab button.
        val sig = matcher.signatureFor(pkg)
        val fast = fastDetector.detect(event, sig, matcher.surfaceFor(pkg), preFetchedRoot)
        FastDetector.logSlowDetection(fast.elapsedMs, fast.triggeredBy)

        if (fast.verdict == FastDetector.Verdict.ON_SHORT_FORM) {
            scrollInterception.onSurfaceEntered(pkg, now)
            persistSurfaceEntered(pkg, now)
            // Kick the user out of the short video. We do NOT
            // block in place — the user is sent back to the
            // previous screen and a brief Toast explains why.
            kickOutFromShort(
                pkg = pkg,
                surface = matcher.surfaceFor(pkg),
                isTab = fast.isTabHit,
                reason = "fast:on:${fast.triggeredBy}",
            )
            publishStatus(pkg, matcher.surfaceFor(pkg), 0.90f, fast.triggeredBy)
            val total = System.currentTimeMillis() - eventStart
            BenchmarkLogger.onEvent(
                pkg, eventType, fastMs = fast.elapsedMs, slowMs = 0L,
                verdict = "fast:on"
            )
            return
        }

        // 3.c) SLOW PATH — full pattern matcher. Only reached when
        //      the fast path returned UNKNOWN (no event.source, no
        //      cache hit, no targeted view-id match).
        if (fast.verdict == FastDetector.Verdict.UNKNOWN) {
            // SE-001: prefer the pre-fetched root from the host. If
            // the host didn't supply one (e.g. unit tests, or the
            // short-circuit cache returned a verdict), call
            // `host.rootInActiveWindow` ourselves and own the node.
            val ownedRoot: AccessibilityNodeInfo?
            if (preFetchedRoot != null) {
                ownedRoot = null
            } else {
                ownedRoot = host.rootInActiveWindow
            }
            val root = preFetchedRoot ?: ownedRoot ?: return
            try {
                val (screenHeight, screenWidth) = screenSize(root)
                val activeLearner = learner

                // BATCH-R: Facebook-specific detection runs *first*
                // for Facebook packages. The FacebookBlockerEngine
                // looks for engagement-rail view-ids, the
                // fullscreen-player + follow/audio combination, and
                // the Reels section tab — signals that the generic
                // PatternMatcher misses for com.facebook.lite (which
                // renders Reels inside a WebView) and that even
                // for com.facebook.katana can resolve Reels hits
                // earlier / with higher confidence.
                var hit: PatternMatcher.Hit? = null
                if (FacebookBlockerEngine.isFacebookPackage(pkg)) {
                    val fbHit = facebookBlocker.detect(
                        root = root,
                        screenHeight = screenHeight,
                        screenWidth = screenWidth,
                    )
                    if (fbHit != null) {
                        FacebookBlockerEngine.logHit(fbHit)
                        if (fbHit.shouldBlock) {
                            // Wrap the FacebookBlocker hit as a
                            // PatternMatcher.Hit so the rest of the
                            // pipeline (kick-out, status, learner)
                            // is unchanged. The `node` field is
                            // never used downstream — we pass `root`
                            // because the Hit constructor requires
                            // it, and the host owns `root`.
                            hit = PatternMatcher.Hit(
                                packageName = pkg,
                                surface = PatternMatcher.Surface.FACEBOOK_REELS,
                                confidence = fbHit.confidence,
                                triggeredBy = "facebook:${fbHit.triggeredBy}",
                                node = root,
                                isTabHit = fbHit.isTabHit,
                            )
                        }
                    }
                }
                if (hit == null) {
                    hit = PatternMatcher.detect(pkg, root, screenHeight, screenWidth)
                }
                val verdictStr: String
                if (hit != null) {
                    scrollInterception.onSurfaceEntered(pkg, now)
                    persistSurfaceEntered(pkg, now)
                    if (hit.shouldBlock) {
                        kickOutFromShort(
                            pkg = pkg,
                            surface = hit.surface,
                            isTab = hit.isTabHit,
                            reason = "slow:on:${hit.triggeredBy}",
                        )
                        verdictStr = "slow:on"
                    } else {
                        verdictStr = "slow:low_conf"
                    }
                    publishStatus(pkg, hit.surface, hit.confidence, hit.triggeredBy)
                } else {
                    scrollInterception.onSurfaceLeft(pkg)
                    persistSurfaceLeft(pkg)
                    overlay.dismiss()
                    publishStatus(pkg, PatternMatcher.Surface.UNKNOWN, 0f, "ok")
                    verdictStr = "slow:miss"
                }
                // Self-learning: even when detection didn't fire,
                // check whether the tree contains a plausible
                // full-screen player node. If yes, feed it to the
                // learner so the next signature can be promoted.
                if (activeLearner != null) {
                    val candidate = PatternMatcher.findCandidateForLearner(
                        learner = activeLearner,
                        packageName = pkg,
                        root = root,
                        screenHeight = screenHeight,
                        screenWidth = screenWidth,
                    )
                    if (candidate != null) {
                        serviceScope.launch {
                            activeLearner.observe(
                                pkg = candidate.packageName,
                                viewId = candidate.viewId,
                                className = candidate.className,
                            )
                        }
                    }
                }
                val slowMs = System.currentTimeMillis() - eventStart - fast.elapsedMs
                BenchmarkLogger.onEvent(
                    pkg, eventType, fastMs = fast.elapsedMs, slowMs = slowMs,
                    verdict = verdictStr
                )
            } finally {
                // SE-001: only recycle the root if WE allocated it.
                // The host-owned preFetchedRoot is its responsibility.
                if (ownedRoot != null) {
                    try { ownedRoot.recycle() } catch (_: Exception) {}
                }
            }
        } else {
            // Allowed — no need to walk the tree.
            scrollInterception.onSurfaceLeft(pkg)
            persistSurfaceLeft(pkg)
            overlay.dismiss()
            publishStatus(pkg, PatternMatcher.Surface.UNKNOWN, 0f, fast.triggeredBy)
            BenchmarkLogger.onEvent(
                pkg, eventType, fastMs = fast.elapsedMs, slowMs = 0L,
                verdict = "fast:off"
            )
        }
    }

    // ----------------------------------------------------------------------
    // Internals
    // ----------------------------------------------------------------------

    /**
     * Per-package mode gate. Returns true if the user has *any*
     * blocking mode enabled for this package. The full-mode path is
     * still handled by the legacy `AppBlockerService` — Shortstop
     * only triggers for surgical and break/quota paths.
     */
    private fun isAppBlockingEnabled(pkg: String): Boolean = when {
        pkg.startsWith("com.facebook") -> cachedFacebookMode == "reels" || cachedFacebookMode == "full"
        pkg == "com.google.android.youtube" -> cachedYoutubeMode == "shorts" || cachedYoutubeMode == "full"
        pkg.startsWith("com.instagram") -> cachedInstagramMode == "reels" || cachedInstagramMode == "full"
        pkg.startsWith("com.zhiliaoapp") || pkg.startsWith("com.ss.android") -> cachedTiktokBlocked
        pkg.startsWith("com.snapchat") -> cachedSnapchatBlocked
        pkg.startsWith("com.twitter") || pkg.startsWith("com.x.android") -> cachedTwitterBlocked
        else -> false
    }

    /**
     * **Primary intervention** for short-form content detection.
     *
     * The user is **forcibly ejected** from the short clip and
     * returned to the device's home screen. There is no in-place
     * overlay, no "Close" button, no "Take a break" choice — the
     * entire point of Shortstop is to interrupt the addictive loop,
     * and giving the user an "OK" button defeats that. A brief
     * Toast explains what just happened and then the user is free
     * to launch any *other* app.
     *
     * The ejection is a two-step action:
     *  1. [GLOBAL_ACTION_BACK] pops the short-video player off the
     *     app's back stack (Reels viewer → Reels index → main feed).
     *  2. After a short delay (so the BACK animation completes),
     *     [GLOBAL_ACTION_HOME] returns the user to the launcher.
     *     The delayed HOME also means: if the user re-launches the
     *     app from Recents, they land on the app's main feed, not
     *     back in the short-video player.
     *
     * The call is debounced per-package via [lastKickAt] so the
     * user doesn't end up in a HOME-loop when a single event
     * triggers multiple times in quick succession.
     *
     * @param pkg     the offending package
     * @param surface which surface was matched
     * @param isTab   true if the user landed on the section tab,
     *                false if they were inside a specific short
     * @param reason  short diagnostic string for logs
     */
    private fun kickOutFromShort(
        pkg: String,
        surface: PatternMatcher.Surface,
        isTab: Boolean,
        reason: String,
    ) {
        val now = System.currentTimeMillis()
        val last = lastKickAt[pkg] ?: 0L
        if (now - last < BlockingConfig.SHORTSTOP_KICKOUT_COOLDOWN_MS) {
            return
        }
        lastKickAt[pkg] = now

        val surfaceLabel = surface.label

        // **BATCH-P (fast kick)**: when we detected a *tab* hit
        // (the user just tapped a Reels/Shorts section tab and
        // the player hasn't actually started yet), skip the BACK
        // step entirely. There's nothing on the back stack to pop,
        // and skipping BACK saves a ~5 ms framework round-trip
        // — bringing the tap→home latency under 10 ms.
        if (isTab && BlockingConfig.SHORTSTOP_FAST_KICK_NO_DELAY) {
            try {
                host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            } catch (e: Exception) {
                AppLogger.w("Shortstop: GLOBAL_ACTION_HOME (fast) failed: ${e.message}")
            }
        } else {
            // 1) Pop the short-video player off the back stack.
            //    We do this synchronously so the player is
            //    destroyed before we move to HOME — without it,
            //    the user could re-launch the app and find
            //    themselves back in the same Reels / Shorts clip.
            try {
                host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            } catch (e: Exception) {
                AppLogger.w("Shortstop: GLOBAL_ACTION_BACK failed: ${e.message}")
            }

            // 2) Forced return to home. Small delay (30 ms in
            //    BATCH-P, was 80 ms) so the BACK transition
            //    queues cleanly before HOME is sent. The user
            //    is not given a choice — this is the whole
            //    point of Shortstop.
            mainHandler.postDelayed({
                try {
                    host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                } catch (e: Exception) {
                    AppLogger.w("Shortstop: GLOBAL_ACTION_HOME failed: ${e.message}")
                }
            }, BlockingConfig.SHORTSTOP_FORCE_HOME_DELAY_MS)
        }

        // 3) Brief Toast message — explains why the user was sent
        //    to home. The user is now on the launcher, so the
        //    Toast is the only "feedback" they see.
        val message = if (isTab) {
            host.getString(R.string.shortstop_kickout_section, surfaceLabel)
        } else {
            host.getString(R.string.shortstop_kickout_video, surfaceLabel)
        }
        try {
            Toast.makeText(host, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            AppLogger.w("Shortstop: Toast failed: ${e.message}")
        }

        // 4) Persist the block event for analytics.
        try {
            val app = host.applicationContext as GuardianApp
            serviceScope.launch {
                app.repository.recordBlock(pkg, surfaceLabel, "shorts_reels_block")
            }
        } catch (e: Exception) {
            AppLogger.w("Shortstop: recordBlock failed: ${e.message}")
        }

        AppLogger.d("Shortstop: forced-home pkg=$pkg reason=$reason surface=$surfaceLabel tab=$isTab")
    }

    private fun showFullScreenMask(pkg: String, title: String, message: String) {
        val now = System.currentTimeMillis()
        if (!overlay.canShowNow(now)) return
        overlay.showFullScreen(
            title = title,
            subtitle = message,
            onClose = {
                try { host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) } catch (_: Exception) {}
            },
            onTakeBreak = {
                // The user accepted a break — start the 5-minute
                // countdown via AppSettings, then send the user to
                // home so they can pick a different activity.
                try {
                    val app = host.applicationContext as GuardianApp
                    val settings = app.repository.getAppSettings()
                    serviceScope.launch {
                        settings.setShortstopBreakEndsAt(System.currentTimeMillis() + 5 * 60 * 1000L)
                    }
                } catch (e: Exception) {
                    AppLogger.w("Shortstop: setBreakEndsAt failed: ${e.message}")
                }
                try { host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) } catch (_: Exception) {}
            },
        )
    }

    private fun ruleCopy(pkg: String, reason: RuleEngine.Verdict.Reason): String {
        val label = matcher.surfaceFor(pkg).label
        return when (reason) {
            RuleEngine.Verdict.Reason.BREAK_ACTIVE -> host.getString(R.string.shortstop_reason_break)
            RuleEngine.Verdict.Reason.DAILY_QUOTA_EXCEEDED -> host.getString(R.string.shortstop_reason_quota)
            RuleEngine.Verdict.Reason.BLOCKED_HOURS -> host.getString(R.string.shortstop_reason_hours, label)
            else -> label
        }
    }

    /**
     * Record that [pkg] entered a short-form surface at [now] (ms). The
     * entry is kept in-memory in [surfaceEnteredMs] and is read by
     * [persistSurfaceLeft] to compute minutes-spent.
     *
     * We don't write to DataStore here — entering is high-frequency
     * and the value is only needed when the user *leaves* the surface.
     */
    private fun persistSurfaceEntered(pkg: String, now: Long) {
        surfaceEnteredMs.putIfAbsent(pkg, now)
    }

    /**
     * Record that [pkg] left the short-form surface and flush the
     * accumulated minutes back to [AppSettings]. The minutes-spent
     * counter is additive across all target apps (we don't track
     * per-app — only the daily total matters for the quota).
     *
     * Idempotent: a second call without an intervening entered is a
     * no-op.
     */
    private fun persistSurfaceLeft(pkg: String) {
        val entered = surfaceEnteredMs.remove(pkg) ?: return
        val now = System.currentTimeMillis()
        val deltaMin = ((now - entered) / 60_000L).toInt()
        if (deltaMin <= 0) return
        val total = cachedMinutesSpentToday + deltaMin
        cachedMinutesSpentToday = total
        try {
            val app = host.applicationContext as GuardianApp
            serviceScope.launch {
                app.repository.getAppSettings().setShortstopMinutesSpentToday(total)
            }
        } catch (e: Exception) {
            AppLogger.w("Shortstop: persistSurfaceLeft failed: ${e.message}")
        }
    }

    /**
     * SE-001: was `screenSize()` with no args, which performed a
     * second `host.rootInActiveWindow` round-trip. Now accepts the
     * pre-fetched root from the slow path; the caller still owns
     * the node (no recycle inside).
     */
    private fun screenSize(preFetchedRoot: AccessibilityNodeInfo?): Pair<Int, Int> = try {
        val m = host.resources.displayMetrics
        // Audit #6: in split-screen / multi-window, use the
        // active-window's bounds so the size heuristic produces
        // sane ratios. We approximate that by reading the most
        // recent root-node bounds when available; otherwise we
        // fall back to the display dimensions.
        if (preFetchedRoot != null) {
            val b = Rect()
            preFetchedRoot.getBoundsInScreen(b)
            Pair(b.height().coerceAtLeast(0), b.width().coerceAtLeast(0))
        } else {
            Pair(m.heightPixels, m.widthPixels)
        }
    } catch (_: Exception) { Pair(0, 0) }

    private fun publishStatus(
        pkg: String,
        surface: PatternMatcher.Surface,
        confidence: Float,
        reason: String,
    ) {
        _status.value = Status(
            overlayShown = overlay.isShowing,
            lastSurface = surface,
            lastConfidence = confidence,
            lastReason = reason,
            targetPackage = pkg,
        )
    }
}
