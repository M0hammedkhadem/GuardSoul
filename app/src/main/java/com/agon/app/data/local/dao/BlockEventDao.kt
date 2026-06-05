package com.agon.app.data.local.dao

import androidx.room.*
import com.agon.app.data.local.entity.BlockEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockEventDao {
    @Query("SELECT * FROM block_events ORDER BY timestamp DESC LIMIT 1000")
    fun getAllFlow(): Flow<List<BlockEventEntity>>

    @Query("SELECT * FROM block_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<BlockEventEntity>>

    @Query("SELECT COUNT(*) FROM block_events")
    fun totalBlocksFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM block_events WHERE timestamp >= :since")
    fun blocksSinceFlow(since: Long): Flow<Int>

    // Issue #179 & #228: Explicit aggregation and column mapping
    @Query("SELECT packageName, MAX(appLabel) as appLabel, COUNT(*) as count FROM block_events GROUP BY packageName ORDER BY count DESC LIMIT 1")
    fun mostBlockedAppFlow(): Flow<MostBlockedApp?>

    @Query("SELECT packageName, MAX(appLabel) as appLabel, COUNT(*) as count FROM block_events WHERE timestamp >= :since GROUP BY packageName ORDER BY count DESC LIMIT 100")
    fun blocksPerAppSince(since: Long): Flow<List<MostBlockedApp>>

    // Issue #230: Added safety limit to prevent OOM
    @Query("SELECT * FROM block_events WHERE timestamp >= :since ORDER BY timestamp DESC LIMIT 500")
    fun blocksSince(since: Long): List<BlockEventEntity>

    @Query("SELECT * FROM block_events WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun getByDateRange(start: Long, end: Long): Flow<List<BlockEventEntity>>

    // Helper for Firebase sync to avoid Flows in workers
    @Query("SELECT packageName, MAX(appLabel) as appLabel, COUNT(*) as count FROM block_events GROUP BY packageName ORDER BY count DESC LIMIT 1")
    suspend fun getMostBlockedApp(): MostBlockedApp?

    @Query("SELECT COUNT(*) FROM block_events")
    suspend fun getCount(): Int

    @Query("DELETE FROM block_events WHERE timestamp < :threshold")
    suspend fun clearOld(threshold: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: BlockEventEntity)

    @Query("DELETE FROM block_events")
    suspend fun deleteAll()
}

data class MostBlockedApp(
    @ColumnInfo(name = "packageName") val packageName: String,
    @ColumnInfo(name = "appLabel") val appLabel: String,
    @ColumnInfo(name = "count") val count: Int
)
