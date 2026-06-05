package com.agon.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.agon.app.data.local.dao.AppLimitDao
import com.agon.app.data.local.dao.BlockEventDao
import com.agon.app.data.local.dao.BlocklistDao
import com.agon.app.data.local.dao.ScheduleRuleDao
import com.agon.app.data.local.dao.TamperAlertDao
import com.agon.app.data.local.entity.AppLimitEntity
import com.agon.app.data.local.entity.BlockEventEntity
import com.agon.app.data.local.entity.BlocklistItemEntity
import com.agon.app.data.local.entity.ScheduleRuleEntity
import com.agon.app.data.local.entity.TamperAlertEntity

@Database(
    entities = [
        BlockEventEntity::class,
        BlocklistItemEntity::class,
        AppLimitEntity::class,
        ScheduleRuleEntity::class,
        TamperAlertEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockEventDao(): BlockEventDao
    abstract fun blocklistDao(): BlocklistDao
    abstract fun appLimitDao(): AppLimitDao
    abstract fun scheduleRuleDao(): ScheduleRuleDao
    abstract fun tamperAlertDao(): TamperAlertDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "guardsoul.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
