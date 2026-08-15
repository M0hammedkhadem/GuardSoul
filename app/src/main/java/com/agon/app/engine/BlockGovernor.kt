package com.agon.app.engine

/**
 * The block governor — decides WHEN a block may fire and HOW HARD.
 *
 * Goals:
 *  1. Never swallow a genuine repeat attempt: if the user re-opens the same
 *     blocked content, the block fires again — every time.
 *  2. Escalate on repetition: repeats of the same target inside the repeat
 *     window get a stronger response (kick to HOME, longer shield overlay,
 *     firmer message).
 *  3. Suppress only true duplicates: the same target re-detected while the
 *     shield overlay is still on screen (event storm) — this is noise, not
 *     a new attempt.
 */
class BlockGovernor(
    private val suppressMs: Long = 2_000,
    private val repeatWindowMs: Long = 60_000,
) {

    companion object {
        /**
         * Short suppress window used for REAL app-launch events
         * (TYPE_WINDOW_STATE_CHANGED). A genuine relaunch of a blocked app
         * must always be re-blocked — even faster than [suppressMs] — while
         * still absorbing the burst of state-change events a single launch
         * animation produces.
         */
        const val LAUNCH_SUPPRESS_MS = 600L
    }

    data class Grant(val repeatCount: Int) {
        val escalated: Boolean get() = repeatCount >= 1
    }

    private var lastTarget: String? = null
    private var lastGrantAt = 0L
    private var repeatCount = 0

    /**
     * Ask permission to block [target] at time [now].
     * Returns null only for duplicate detections inside the suppress window
     * (the overlay is still up). Otherwise always grants, with an escalation
     * counter when the same target repeats inside the repeat window.
     *
     * [bypassSuppress] = true for real launch events: shrinks the suppress
     * window to [LAUNCH_SUPPRESS_MS] so instant relaunches are never missed.
     */
    fun request(target: String, now: Long, bypassSuppress: Boolean = false): Grant? {
        val sameTarget = target == lastTarget
        val elapsed = now - lastGrantAt
        val effectiveSuppress = if (bypassSuppress) LAUNCH_SUPPRESS_MS else suppressMs

        if (sameTarget && elapsed < effectiveSuppress) return null // event storm

        repeatCount = if (sameTarget && elapsed < repeatWindowMs) repeatCount + 1 else 0
        lastTarget = target
        lastGrantAt = now
        return Grant(repeatCount)
    }

    fun reset() {
        lastTarget = null
        lastGrantAt = 0L
        repeatCount = 0
    }
}
