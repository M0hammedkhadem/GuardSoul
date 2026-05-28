package com.agon.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_rules")
data class ScheduleRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val daysOfWeek: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val enabled: Boolean = true
)
