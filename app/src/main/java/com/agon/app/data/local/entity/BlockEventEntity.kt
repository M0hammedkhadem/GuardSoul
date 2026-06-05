package com.agon.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "block_events",
    indices = [Index(value = ["timestamp"])]
)
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val blockType: String,
    val timestamp: Long = System.currentTimeMillis()
)
