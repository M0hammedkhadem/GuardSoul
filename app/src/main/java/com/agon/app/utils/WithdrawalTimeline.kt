package com.agon.app.utils

import com.agon.app.R

/**
 * Withdrawal timeline (I Am Sober style). Maps the current day of
 * the streak (1-indexed) to the *typical* experience of someone
 * cutting back on dopamine-spiking content (porn, short-form video,
 * etc). The phases are medically-informed approximations, not
 * medical advice — the goal is just to give the user a realistic
 * "this is normal" check-in during the first few weeks.
 */
object WithdrawalTimeline {

    data class Phase(
        val dayStart: Int,        // inclusive
        val dayEnd: Int,          // inclusive; use Int.MAX_VALUE for "no upper bound"
        val titleRes: Int,
        val descRes: Int,
        val emoji: String
    )

    val phases: List<Phase> = listOf(
        Phase(1, 3, R.string.withdrawal_d1_title, R.string.withdrawal_d1_desc, "🌊"),
        Phase(4, 7, R.string.withdrawal_d4_title, R.string.withdrawal_d4_desc, "😴"),
        Phase(8, 14, R.string.withdrawal_d8_title, R.string.withdrawal_d8_desc, "🧠"),
        Phase(15, 30, R.string.withdrawal_d15_title, R.string.withdrawal_d15_desc, "💪"),
        Phase(31, 90, R.string.withdrawal_d31_title, R.string.withdrawal_d31_desc, "🌱"),
        Phase(91, Int.MAX_VALUE, R.string.withdrawal_d91_title, R.string.withdrawal_d91_desc, "🏆"),
    )

    /** Resolve the current phase given the streak day. Day 0 returns null (no streak yet). */
    fun phaseFor(streakDay: Int): Phase? {
        if (streakDay <= 0) return null
        return phases.firstOrNull { streakDay in it.dayStart..it.dayEnd }
    }

    /** Returns (currentPhase, nextPhase). Both null if streak is 0. */
    fun currentAndNext(streakDay: Int): Pair<Phase?, Phase?> {
        val current = phaseFor(streakDay) ?: return null to null
        val currentIdx = phases.indexOf(current)
        val next = phases.getOrNull(currentIdx + 1)
        return current to next
    }
}
