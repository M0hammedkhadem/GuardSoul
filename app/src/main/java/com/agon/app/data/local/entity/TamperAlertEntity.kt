package com.agon.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tamper_alerts",
    indices = [Index(value = ["timestamp"])]
)
data class TamperAlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val detail: String,
    val packageName: String = "",
    val userId: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
