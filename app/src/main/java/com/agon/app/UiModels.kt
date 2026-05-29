package com.agon.app

import com.agon.app.data.local.entity.BlocklistItemEntity
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

data class BlocklistItem(
    val id: Long = 0,
    val listType: String,
    val category: String,
    val value: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val label: String? = null,
    val regexEnabled: Boolean = false,
    val sensitivityLevel: String = "medium",
    val urlCategory: String? = null
) {
    val displayLabel: String get() = label ?: value
}

fun BlocklistItemEntity.toUiModel() = BlocklistItem(
    id = id,
    listType = listType,
    category = category,
    value = value,
    enabled = enabled,
    createdAt = createdAt,
    label = label,
    regexEnabled = regexEnabled,
    sensitivityLevel = sensitivityLevel,
    urlCategory = urlCategory
)

fun BlocklistItem.toEntity() = BlocklistItemEntity(
    id = id,
    listType = listType,
    category = category,
    value = value,
    enabled = enabled,
    createdAt = createdAt,
    label = label,
    regexEnabled = regexEnabled,
    sensitivityLevel = sensitivityLevel,
    urlCategory = urlCategory
)
