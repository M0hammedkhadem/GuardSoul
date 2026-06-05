package com.agon.app.utils

/**
 * Discipline score calculator. Mirrors the way Screen Stoic and
 * Headspace combine multiple positive signals into a single
 * "engagement index":
 *
 *   score = streakDays * 1.5
 *         + (milestonesAchieved) * 25
 *         + (7 - weeklyPornAttempts).coerceAtLeast(0) * 5
 *         + (todayPledgeTaken ? 10 : 0)
 *
 * The numbers are intentionally lenient at the start (1.5 per day)
 * so a 30-day streak already lands the user on `Mind Master`. The
 * "porn attempts" penalty is what actually penalises relapse —
 * 7+ attempts in a week zeroes the bonus from that signal so the
 * user can't game the system by just opening the app.
 *
 * All inputs come from `HomeViewModel`'s flows — the calculator
 * itself is pure & testable.
 */
object DisciplineScore {

    /**
     * Compute the score for the given state snapshot. Negative values
     * are clamped to 0.
     */
    fun compute(
        streakDays: Int,
        milestonesAchieved: Int,
        weeklyPornAttempts: Int,
        todayPledgeTaken: Boolean
    ): Int {
        val streakComponent = (streakDays.coerceAtLeast(0) * 1.5).toInt()
        val milestonesComponent = milestonesAchieved.coerceAtLeast(0) * 25
        val pornPenaltyComponent =
            (7 - weeklyPornAttempts.coerceAtLeast(0)).coerceAtLeast(0) * 5
        val pledgeComponent = if (todayPledgeTaken) 10 else 0
        return (streakComponent + milestonesComponent + pornPenaltyComponent + pledgeComponent)
            .coerceAtLeast(0)
    }
}
