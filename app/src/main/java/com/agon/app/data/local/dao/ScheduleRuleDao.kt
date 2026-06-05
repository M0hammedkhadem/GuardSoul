package com.agon.app.data.local.dao

import androidx.room.*
import com.agon.app.data.local.entity.ScheduleRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleRuleDao {
    @Query("SELECT * FROM schedule_rules ORDER BY id ASC")
    fun getAllFlow(): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules ORDER BY id ASC")
    suspend fun getAll(): List<ScheduleRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: ScheduleRuleEntity): Long

    @Update
    suspend fun update(rule: ScheduleRuleEntity)

    @Delete
    suspend fun delete(rule: ScheduleRuleEntity)

    @Query("UPDATE schedule_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM schedule_rules")
    suspend fun deleteAll()
}
