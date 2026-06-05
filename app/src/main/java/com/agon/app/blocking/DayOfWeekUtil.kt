package com.agon.app.blocking

import java.util.Calendar

/**
 * Single source of truth for converting between:
 * - [java.util.Calendar.DAY_OF_WEEK] (1 = Sunday … 7 = Saturday)
 * - the in-app "week starts on Monday" day index (1 = Mon … 7 = Sun)
 * - the CSV-encoded [String] stored in [com.agon.app.data.local.entity.ScheduleRuleEntity.daysOfWeek]
 *
 * The CSV format is 1-based with Monday=1 to match what [com.agon.app.ui.screens.ScheduleScreen]
 * already writes — switching to 0-based would silently break existing user schedules.
 */
object DayOfWeekUtil {
    /** Returns 1 = Mon … 7 = Sun. Calendar.SUNDAY=1 → 7, Calendar.SATURDAY=7 → 6. */
    fun calendarDayToMondayFirstIndex(calendarDay: Int): Int = when (calendarDay) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> -1
    }

    /** Inverse of [calendarDayToMondayFirstIndex]. 1 = Mon … 7 = Sun. */
    fun mondayFirstIndexToCalendarDay(index: Int): Int = when (index) {
        1 -> Calendar.MONDAY
        2 -> Calendar.TUESDAY
        3 -> Calendar.WEDNESDAY
        4 -> Calendar.THURSDAY
        5 -> Calendar.FRIDAY
        6 -> Calendar.SATURDAY
        7 -> Calendar.SUNDAY
        else -> -1
    }

    /** Encode a set of 1-7 indices to the CSV string used by the DB. */
    fun encode(days: Set<Int>): String =
        days.filter { it in 1..7 }.sorted().joinToString(",")

    /** Decode the CSV string used by the DB back to a set of 1-7 indices. */
    fun decode(csv: String): Set<Int> =
        csv.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()

    /** Convenience: current "Monday-first" day index for the current locale. */
    fun todayIndex(): Int = calendarDayToMondayFirstIndex(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
}
