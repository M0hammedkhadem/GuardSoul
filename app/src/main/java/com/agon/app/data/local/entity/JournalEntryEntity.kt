package com.agon.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val id: Long,
    val timestamp: Long,
    val mood: Int,
    val triggers: List<String>, // persisted as JSON via Converters (kotlinx Json)
    val text: String,
)
