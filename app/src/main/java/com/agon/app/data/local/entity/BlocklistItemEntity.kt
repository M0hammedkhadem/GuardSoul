package com.agon.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocklist_items")
data class BlocklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listType: String,
    val category: String,
    val value: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val label: String? = null,
    val regexEnabled: Boolean = false,
    val sensitivityLevel: String = "medium",
    val urlCategory: String? = null
)
