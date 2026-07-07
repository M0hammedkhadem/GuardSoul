package com.agon.app.data.local.dao

import androidx.room.*
import com.agon.app.data.local.entity.BlockEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockEventDao {
    @Query("SELECT COUNT(*) FROM block_events")
    fun totalBlocksFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM block_events WHERE timestamp >= :since")
    fun blocksSinceFlow(since: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: BlockEventEntity)

    @Query("DELETE FROM block_events")
    suspend fun deleteAll()
}
