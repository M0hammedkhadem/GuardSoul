package com.agon.app.data.local.dao

import androidx.room.*
import com.agon.app.data.local.entity.BlockEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockEventDao {
    @Query("SELECT * FROM block_events ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<BlockEventEntity>>

    @Query("SELECT * FROM block_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<BlockEventEntity>>

    @Query("SELECT COUNT(*) FROM block_events")
    fun totalBlocksFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM block_events WHERE timestamp >= :since")
    fun blocksSinceFlow(since: Long): Flow<Int>

    @Query("SELECT packageName, appLabel, COUNT(*) as count FROM block_events GROUP BY packageName ORDER BY count DESC LIMIT 1")
    fun mostBlockedAppFlow(): Flow<MostBlockedApp?>

    @Query("SELECT packageName, appLabel, COUNT(*) as count FROM block_events WHERE timestamp >= :since GROUP BY packageName ORDER BY count DESC")
    fun blocksPerAppSince(since: Long): Flow<List<MostBlockedApp>>

    @Query("SELECT * FROM block_events WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun blocksSince(since: Long): List<BlockEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: BlockEventEntity)

    @Query("DELETE FROM block_events")
    suspend fun deleteAll()
}

data class MostBlockedApp(
    val packageName: String,
    val appLabel: String,
    val count: Int
)
