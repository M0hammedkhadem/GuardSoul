package com.agon.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocklist_items")
data class BlocklistItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val listType: String,
    val category: String,
    val value: String
)
