package com.agon.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.agon.app.data.local.entity.TamperAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TamperAlertDao {
    @Query("SELECT * FROM tamper_alerts ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<TamperAlertEntity>>

    @Query("SELECT * FROM tamper_alerts ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<TamperAlertEntity>>

    @Insert
    suspend fun insert(alert: TamperAlertEntity)

    @Query("DELETE FROM tamper_alerts")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM tamper_alerts WHERE timestamp >= :since")
    fun countSince(since: Long): Flow<Int>
}
