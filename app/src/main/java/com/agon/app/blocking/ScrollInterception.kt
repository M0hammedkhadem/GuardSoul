package com.agon.app.blocking

import android.view.accessibility.AccessibilityEvent
import com.agon.app.utils.AppLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Shortstop §3.2.b — Infinite-scroll interception.
 *
 * The accessibility service fires [AccessibilityEvent.TYPE_VIEW_SCROLLED]
 * for every scroll gesture on the active window. We track per-package:
 *
 *  - a sliding-window **scroll counter** (capped at
 *    [BlockingConfig.SCROLL_VELOCITY_THRESHOLD] inside
 *    [BlockingConfig.SCROLL_WINDOW_MS]),
 *  - a **Y-velocity** estimate derived from
 *    `event.scrollDeltaY / eventTimeDelta`, and
 *  - the **time-on-content** for the current short-form surface (reset
 *    when the user navigates to a different screen).
 *
 * When any signal exceeds its threshold the service injects the
 * break overlay. All tracking is in-memory only — nothing leaves
 * the device, consistent with the privacy stance in
 * [PROJECT_MAP.md].
 */
class ScrollInterception {

    private data class ScrollState(
        var count: Int = 0,
        var windowStartMs: Long = 0L,
        var lastScrollY: Int = 0,
        var lastScrollTimeMs: Long = 0L,
        var velocityPxPerSec: Float = 0f,
        var surfaceEnteredMs: Long = 0L,
    )

    /** Result of [onScroll]. */
    data class Decision(
        val isAddictive: Boolean,
        val reason: String,
        val scrollCount: Int,
        val velocityPxPerSec: Float,
        val timeOnContentMs: Long,
    )

    /** Per-package state — guarded by the ConcurrentHashMap. */
    private val states = ConcurrentHashMap<String, ScrollState>()

    /**
     * Reset the per-package state. Called when the user navigates
     * away from a short-form surface (or when the user closes the
     * app) so the next session starts from a clean slate.
     */
    fun reset(packageName: String) {
        states.remove(packageName)
    }

    /**
     * Reset everything. Called when the shield is turned off.
     */
    fun resetAll() {
        states.clear()
    }

    /**
     * Mark the moment the user *entered* a short-form surface for
     * [packageName]. The timer is the basis for the
     * `time-on-content` decision.
     *
     * **FB-006** — serialise the per-package state reset so it
     * can never interleave with a concurrent [onScroll] for the
     * same package.
     */
    fun onSurfaceEntered(packageName: String, now: Long = System.currentTimeMillis()) {
        val s = states.computeIfAbsent(packageName) { ScrollState() }
        synchronized(s) {
            s.surfaceEnteredMs = now
            s.count = 0
            s.windowStartMs = 0L
            s.lastScrollY = 0
            s.lastScrollTimeMs = 0L
            s.velocityPxPerSec = 0f
        }
    }

    /**
     * Mark the moment the user *left* a short-form surface. Used to
     * expose "time spent on Shorts/Reels today" stats and to stop
     * counting scrolls.
     */
    fun onSurfaceLeft(packageName: String) {
        val s = states[packageName] ?: return
        synchronized(s) {
            s.surfaceEnteredMs = 0L
            s.count = 0
            s.windowStartMs = 0L
            s.lastScrollY = 0
            s.lastScrollTimeMs = 0L
            s.velocityPxPerSec = 0f
        }
    }

    /**
     * Process a [AccessibilityEvent.TYPE_VIEW_SCROLLED] event.
     * Returns a [Decision] so the accessibility service can decide
     * whether to inject the overlay.
     *
     * **FB-006** — the previous implementation mutated
     * [ScrollState] non-atomically. Multiple scroll events for
     * the same package firing on different threads (or even
     * the same thread interleaved with [onSurfaceEntered]) could
     * race and lose updates. We now serialise per-package
     * mutations with a [synchronized] block on the state
     * instance itself.
     */
    fun onScroll(event: AccessibilityEvent, now: Long = System.currentTimeMillis()): Decision {
        val pkg = event.packageName?.toString() ?: return Decision(
            isAddictive = false,
            reason = "no-pkg",
            scrollCount = 0,
            velocityPxPerSec = 0f,
            timeOnContentMs = 0L,
        )
        val state = states.computeIfAbsent(pkg) { ScrollState() }

        val deltaY = event.scrollDeltaY
        val currentTimeMs = now

        // Serialise all per-package state mutations. The state
        // is owned by this map and never escapes, so locking on
        // it is safe.
        return synchronized(state) {
            val lastT = state.lastScrollTimeMs

            // Sliding-window counter
            if (state.windowStartMs == 0L ||
                currentTimeMs - state.windowStartMs > BlockingConfig.SCROLL_WINDOW_MS
            ) {
                state.windowStartMs = currentTimeMs
                state.count = 0
            }
            state.count += 1

            // Velocity estimate
            if (lastT > 0L && deltaY != 0) {
                val dtSec = (currentTimeMs - lastT).coerceAtLeast(1L) / 1000f
                state.velocityPxPerSec = Math.abs(deltaY) / dtSec
            }
            state.lastScrollY = deltaY
            state.lastScrollTimeMs = currentTimeMs

            // Time on content
            val timeOnContentMs = if (state.surfaceEnteredMs > 0L) {
                currentTimeMs - state.surfaceEnteredMs
            } else {
                0L
            }

            // Decide
            when {
                state.count >= BlockingConfig.SCROLL_VELOCITY_THRESHOLD -> Decision(
                    isAddictive = true,
                    reason = "scroll_count=${state.count}/${BlockingConfig.SCROLL_VELOCITY_THRESHOLD}",
                    scrollCount = state.count,
                    velocityPxPerSec = state.velocityPxPerSec,
                    timeOnContentMs = timeOnContentMs,
                )
                state.velocityPxPerSec >= BlockingConfig.SCROLL_VELOCITY_PX_PER_SEC -> Decision(
                    isAddictive = true,
                    reason = "velocity=${state.velocityPxPerSec.toInt()}px/s",
                    scrollCount = state.count,
                    velocityPxPerSec = state.velocityPxPerSec,
                    timeOnContentMs = timeOnContentMs,
                )
                timeOnContentMs >= BlockingConfig.TIME_ON_CONTENT_THRESHOLD_MS -> Decision(
                    isAddictive = true,
                    reason = "time_on_content=${timeOnContentMs / 1000}s",
                    scrollCount = state.count,
                    velocityPxPerSec = state.velocityPxPerSec,
                    timeOnContentMs = timeOnContentMs,
                )
                else -> Decision(
                    isAddictive = false,
                    reason = "ok",
                    scrollCount = state.count,
                    velocityPxPerSec = state.velocityPxPerSec,
                    timeOnContentMs = timeOnContentMs,
                )
            }
        }
    }

    /** Read-only snapshot of a package's current counters (for stats). */
    fun snapshot(packageName: String): Triple<Int, Float, Long> {
        val s = states[packageName] ?: return Triple(0, 0f, 0L)
        val toc = if (s.surfaceEnteredMs > 0L) {
            System.currentTimeMillis() - s.surfaceEnteredMs
        } else 0L
        return Triple(s.count, s.velocityPxPerSec, toc)
    }

    /** Diagnostic line for logs / the debug screen. */
    fun explain(packageName: String): String {
        val (c, v, t) = snapshot(packageName)
        return "pkg=$packageName scrolls=$c vel=${v.toInt()}px/s timeOnContent=${t / 1000}s"
    }

    companion object {
        /** Convenience that logs a one-liner for the accessibility service. */
        fun log(decision: Decision) {
            if (decision.isAddictive) {
                AppLogger.w("Shortstop: scroll-decision ${decision.reason}")
            }
        }
    }
}
