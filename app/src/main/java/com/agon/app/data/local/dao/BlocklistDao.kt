package com.agon.app.data.local.dao

import androidx.room.*
import com.agon.app.data.local.entity.BlocklistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocklistDao {
    @Query("SELECT * FROM blocklist_items WHERE listType = :listType AND category = :category ORDER BY createdAt DESC")
    fun getItemsFlow(listType: String, category: String): Flow<List<BlocklistItemEntity>>

    @Query("SELECT * FROM blocklist_items WHERE listType = :listType AND category = :category ORDER BY createdAt DESC")
    suspend fun getItems(listType: String, category: String): List<BlocklistItemEntity>

    @Query("SELECT * FROM blocklist_items WHERE id = :id")
    suspend fun getById(id: Long): BlocklistItemEntity?

    @Query("""
        SELECT * FROM blocklist_items 
        WHERE listType = :listType AND category = :category 
        AND (value LIKE '%' || :query || '%' OR label LIKE '%' || :query || '%')
        ORDER BY createdAt DESC
    """)
    fun search(listType: String, category: String, query: String): Flow<List<BlocklistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BlocklistItemEntity)

    @Delete
    suspend fun delete(item: BlocklistItemEntity)

    @Query("DELETE FROM blocklist_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM blocklist_items WHERE listType = :listType AND category = :category AND value = :value")
    suspend fun deleteByValue(listType: String, category: String, value: String)

    @Query("DELETE FROM blocklist_items WHERE listType = :listType AND category = :category")
    suspend fun deleteCategory(listType: String, category: String)
}
