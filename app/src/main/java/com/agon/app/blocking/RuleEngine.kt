package com.agon.app.blocking

import java.util.Calendar

/**
 * Scheduling and quota rules for short-form feed blocking.
 *
 * Supports three modes (matching the Shortstop app):
 *  1. Blocked hours — scheduled blocking during focus hours.
 *  2. Daily quota — timer-based limit (minutes per day).
 *  3. Break reminders — forced breaks after N minutes of browsing.
 */
class RuleEngine(
    private val config: Config = Config(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Immutable snapshot of the user's current scheduling / quota
     * settings. Built by the accessibility service from the
     * DataStore-backed [com.agon.app.data.settings.AppSettings].
     */
    data class Config(
        /** True if the user has configured blocked hours and we're inside one. */
        val blockedHourActive: Boolean = false,
        /** True if the user has hit the daily quota. */
        val dailyQuotaExceeded: Boolean = false,
        /** True if the user is currently inside a forced break. */
        val breakActive: Boolean = false,
        /** Minutes already spent on Reels/Shorts in the current 24h window. */
        val minutesSpentToday: Int = 0,
        /** User-configured quota (minutes/day). 0 means "no quota". */
        val dailyQuotaMinutes: Int = BlockingConfig.DEFAULT_DAILY_QUOTA_MIN,
        /** User-configured break interval (minutes of use before a forced break). 0 disables. */
        val breakIntervalMinutes: Int = 15,
        /** Length of the forced break (minutes). 0 disables. */
        val breakLengthMinutes: Int = 5,
    )

    /** Result of [evaluate]. */
    data class Verdict(
        val shouldBlock: Boolean,
        val reason: Reason,
        val message: String,
    ) {
        enum class Reason {
            ALLOWED,
            BLOCKED_HOURS,
            DAILY_QUOTA_EXCEEDED,
            BREAK_ACTIVE,
        }
    }

    /**
     * Decide whether to apply short-form blocking **right now**.
     */
    fun evaluate(now: Long = clock()): Verdict {
        if (config.breakActive) {
            return Verdict(
                shouldBlock = true,
                reason = Verdict.Reason.BREAK_ACTIVE,
                message = "Forced break (${config.breakLengthMinutes} min).",
            )
        }
        if (config.dailyQuotaExceeded) {
            return Verdict(
                shouldBlock = true,
                reason = Verdict.Reason.DAILY_QUOTA_EXCEEDED,
                message = "Daily quota reached (${config.minutesSpentToday}/${config.dailyQuotaMinutes} min).",
            )
        }
        if (config.blockedHourActive) {
            return Verdict(
                shouldBlock = true,
                reason = Verdict.Reason.BLOCKED_HOURS,
                message = "Inside a blocked-hours window.",
            )
        }
        return Verdict(shouldBlock = false, reason = Verdict.Reason.ALLOWED, message = "ok")
    }

    /**
     * Helper for the accessibility service: did the user just cross
     * the break-interval threshold while on a short-form surface?
     * Returns true when [minutesSpentOnSurface] is a positive
     * multiple of [breakIntervalMinutes] (and that interval > 0).
     */
    fun shouldForceBreak(minutesSpentOnSurface: Int): Boolean {
        val interval = config.breakIntervalMinutes
        if (interval <= 0) return false
        if (minutesSpentOnSurface <= 0) return false
        return minutesSpentOnSurface % interval == 0
    }

    /**
     * Helper for the accessibility service: have we exceeded the
     * daily quota?
     */
    fun quotaExceeded(minutesSpentToday: Int): Boolean {
        val quota = config.dailyQuotaMinutes
        if (quota <= 0) return false
        return minutesSpentToday >= quota
    }

    companion object {
        /**
         * Decide whether [hour] (0-23) is inside any of the
         * blocked-hours ranges supplied as a list of `start..end`
         * pairs. Each pair is inclusive on both ends and is allowed
         * to wrap past midnight (start > end). Pure function — used
         * by the accessibility service to evaluate the active
         * blocked-hours windows from the persisted schedule.
         */
        fun isHourBlocked(hour: Int, ranges: List<IntRange>): Boolean {
            for (range in ranges) {
                if (range.first <= range.last) {
                    if (hour in range) return true
                } else {
                    if (hour >= range.first || hour <= range.last) return true
                }
            }
            return false
        }

        /** Convenience to derive the local hour for a given [nowMs]. */
        fun localHour(nowMs: Long = System.currentTimeMillis()): Int {
            val cal = Calendar.getInstance()
            cal.timeInMillis = nowMs
            return cal.get(Calendar.HOUR_OF_DAY)
        }
    }
}
