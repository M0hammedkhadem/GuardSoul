package com.agon.app.blocking

import com.agon.app.utils.AppLogger

/**
 * Lightweight latency logger for the Shortstop detection pipeline.
 *
 * Goal: keep detection at ≤ 50 ms (target) with a hard ceiling of
 * 100 ms (anything above is logged as a warning so the team can
 * investigate in `adb logcat -s ShortstopBench`).
 *
 * **What we measure**
 *  - **Total event time** — wall-clock from `onAccessibilityEvent`
 *    start to return.
 *  - **Fast path** — sub-budget of the [FastDetector] alone.
 *  - **Slow path** — sub-budget of the full [PatternMatcher] walk.
 *  - **Outcome** — what verdict was returned.
 *
 * **How to read the log**
 *  - `D` lines: every event, compact.
 *  - `W` lines: events that exceeded the per-event budget.
 *  - `I` lines: every 100 events, a rolling average summary.
 *
 * The logger is opt-in via [enabled] — the default is `false` to
 * avoid log spam in production. Toggle it from the diagnostic
 * screen in the app.
 */
object BenchmarkLogger {

    @Volatile var enabled: Boolean = false

    private const val TAG = "ShortstopBench"

    private const val TARGET_MS: Long = 50L
    private const val HARD_CEILING_MS: Long = 100L

    private var totalEvents: Int = 0
    private var totalFastMs: Long = 0L
    private var totalSlowMs: Long = 0L
    private var lastSummaryAt: Long = 0L
    private const val SUMMARY_INTERVAL_MS: Long = 60_000L

    /** Called from [ShortstopEngine.onAccessibilityEvent]. */
    fun onEvent(
        pkg: String,
        eventType: Int,
        fastMs: Long,
        slowMs: Long,
        verdict: String,
    ) {
        if (!enabled) return
        val total = fastMs + slowMs

        totalEvents += 1
        totalFastMs += fastMs
        totalSlowMs += slowMs

        if (total > HARD_CEILING_MS) {
            AppLogger.w(
                "$TAG: over ceiling pkg=$pkg type=$eventType total=${total}ms " +
                    "(fast=${fastMs}ms slow=${slowMs}ms verdict=$verdict)"
            )
        } else if (total > TARGET_MS) {
            AppLogger.w(
                "$TAG: over target pkg=$pkg type=$eventType total=${total}ms verdict=$verdict"
            )
        } else {
            AppLogger.d(
                "$TAG: pkg=$pkg type=$eventType total=${total}ms verdict=$verdict"
            )
        }

        val now = System.currentTimeMillis()
        if (lastSummaryAt == 0L || now - lastSummaryAt >= SUMMARY_INTERVAL_MS) {
            lastSummaryAt = now
            if (totalEvents > 0) {
                val avgFast = totalFastMs / totalEvents
                val avgSlow = totalSlowMs / totalEvents
                val avgTotal = avgFast + avgSlow
                AppLogger.i(
                    "$TAG: summary events=$totalEvents " +
                        "avgFast=${avgFast}ms avgSlow=${avgSlow}ms avgTotal=${avgTotal}ms"
                )
            }
        }
    }

    /** Reset rolling counters (e.g. between debug sessions). */
    fun reset() {
        totalEvents = 0
        totalFastMs = 0L
        totalSlowMs = 0L
        lastSummaryAt = 0L
    }
}
