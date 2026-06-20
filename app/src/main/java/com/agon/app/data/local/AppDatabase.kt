package com.agon.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.agon.app.data.local.dao.BlockEventDao
import com.agon.app.data.local.dao.KeywordDao
import com.agon.app.data.local.entity.BlockEventEntity
import com.agon.app.data.local.entity.KeywordEntity

@Database(
    entities = [BlockEventEntity::class, KeywordEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockEventDao(): BlockEventDao
    abstract fun keywordDao(): KeywordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "guardsoul.db"
                ).fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
