package com.agon.app

import com.agon.app.data.local.entity.BlocklistItemEntity

/**
 * UI-facing representation of a single blocklist entry. Wraps the Room entity
 * so the entity's storage concerns (autoGenerate id, Room annotations) stay
 * inside the data layer while screens consume a stable model.
 */
data class BlocklistItem(
    val id: Long,
    val listType: String,
    val category: String,
    val value: String,
    val enabled: Boolean,
    val createdAt: Long,
    val label: String?,
    val regexEnabled: Boolean,
    val sensitivityLevel: String,
    val urlCategory: String?,
) {
    val displayLabel: String get() = label ?: value

    fun toEntity(): BlocklistItemEntity = BlocklistItemEntity(
        id = id,
        listType = listType,
        category = category,
        value = value,
        enabled = enabled,
        createdAt = createdAt,
        label = label,
        regexEnabled = regexEnabled,
        sensitivityLevel = sensitivityLevel,
        urlCategory = urlCategory,
    )

    companion object {
        fun fromEntity(e: BlocklistItemEntity): BlocklistItem = BlocklistItem(
            id = e.id,
            listType = e.listType,
            category = e.category,
            value = e.value,
            enabled = e.enabled,
            createdAt = e.createdAt,
            label = e.label,
            regexEnabled = e.regexEnabled,
            sensitivityLevel = e.sensitivityLevel,
            urlCategory = e.urlCategory,
        )

        fun create(
            listType: String,
            category: String,
            value: String,
            label: String? = null,
            regexEnabled: Boolean = false,
            sensitivityLevel: String = "medium",
            urlCategory: String? = null,
        ): BlocklistItem = BlocklistItem(
            id = 0,
            listType = listType,
            category = category,
            value = value,
            enabled = true,
            createdAt = System.currentTimeMillis(),
            label = label,
            regexEnabled = regexEnabled,
            sensitivityLevel = sensitivityLevel,
            urlCategory = urlCategory,
        )
    }
}
