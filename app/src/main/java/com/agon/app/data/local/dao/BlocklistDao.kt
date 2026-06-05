package com.agon.app.data.local.dao

import androidx.room.*
import com.agon.app.data.local.entity.BlocklistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocklistDao {
    @Query("SELECT * FROM blocklist_items WHERE listType = :listType AND category = :category ORDER BY id DESC")
    fun getItemsFlow(listType: String, category: String): Flow<List<BlocklistItemEntity>>

    @Query("SELECT * FROM blocklist_items WHERE listType = :listType AND category = :category")
    suspend fun getItems(listType: String, category: String): List<BlocklistItemEntity>

    @Query("SELECT * FROM blocklist_items ORDER BY id ASC")
    suspend fun getAll(): List<BlocklistItemEntity>

    @Query("SELECT * FROM blocklist_items WHERE id = :id")
    suspend fun getById(id: Long): BlocklistItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BlocklistItemEntity): Long

    @Query("DELETE FROM blocklist_items WHERE listType = :listType AND category = :category AND value = :value")
    suspend fun deleteByValue(listType: String, category: String, value: String)

    @Query("DELETE FROM blocklist_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM blocklist_items")
    suspend fun deleteAll()

    @Update
    suspend fun update(item: BlocklistItemEntity)
}
