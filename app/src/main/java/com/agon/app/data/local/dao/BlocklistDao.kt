package com.agon.app.data.local.dao

import androidx.room.*
import com.agon.app.data.local.entity.BlocklistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocklistDao {
    @Query("SELECT * FROM blocklist_items WHERE listType = :listType AND category = :category")
    fun getItemsFlow(listType: String, category: String): Flow<List<BlocklistItemEntity>>

    @Query("SELECT * FROM blocklist_items WHERE listType = :listType AND category = :category")
    suspend fun getItems(listType: String, category: String): List<BlocklistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BlocklistItemEntity)

    @Delete
    suspend fun delete(item: BlocklistItemEntity)

    @Query("DELETE FROM blocklist_items WHERE listType = :listType AND category = :category AND value = :value")
    suspend fun deleteByValue(listType: String, category: String, value: String)

    @Query("DELETE FROM blocklist_items WHERE listType = :listType AND category = :category")
    suspend fun deleteCategory(listType: String, category: String)
}
