package com.agon.app

import java.util.UUID

data class ScheduleRule(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val daysOfWeek: Set<Int> = emptySet(),
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 8,
    val endMinute: Int = 0,
)

data class DailyTimeLimit(
    val packageName: String = "",
    val appLabel: String = "",
    val dailyMinutes: Int = 30,
)

data class BlockEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String = "",
    val blockType: String = "manual",
)


