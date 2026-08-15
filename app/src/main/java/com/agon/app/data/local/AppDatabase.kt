package com.agon.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.agon.app.data.local.dao.JournalDao
import com.agon.app.data.local.entity.JournalEntryEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * List<String> <-> JSON string converter for the `triggers` column, using the
 * same kotlinx.serialization Json configuration as the rest of the project.
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }
    private val listSer = ListSerializer(String.serializer())

    @TypeConverter
    fun fromStringList(value: List<String>): String =
        json.encodeToString(listSer, value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        runCatching { json.decodeFromString(listSer, value) }.getOrDefault(emptyList())
}

@Database(entities = [JournalEntryEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao
}
