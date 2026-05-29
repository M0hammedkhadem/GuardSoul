package com.agon.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tamper_alerts")
data class TamperAlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String = "",
    val userId: Int = 0
)
