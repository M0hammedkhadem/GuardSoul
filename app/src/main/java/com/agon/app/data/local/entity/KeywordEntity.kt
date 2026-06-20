package com.agon.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "keywords",
    indices = [Index(value = ["keyword"], unique = true)]
)
data class KeywordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keyword: String,
    val isWhitelist: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)