package com.agon.app.data.local.dao

import androidx.room.*
import com.agon.app.data.local.entity.KeywordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordDao {

    @Query("SELECT * FROM keywords WHERE isWhitelist = 0 ORDER BY createdAt DESC")
    fun getBlacklistFlow(): Flow<List<KeywordEntity>>

    @Query("SELECT * FROM keywords WHERE isWhitelist = 1 ORDER BY createdAt DESC")
    fun getWhitelistFlow(): Flow<List<KeywordEntity>>

    @Query("SELECT keyword FROM keywords WHERE isWhitelist = 0")
    fun getBlacklistKeywords(): Flow<List<String>>

    @Query("SELECT keyword FROM keywords WHERE isWhitelist = 1")
    fun getWhitelistKeywords(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(keyword: KeywordEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(keywords: List<KeywordEntity>)

    @Delete
    suspend fun delete(keyword: KeywordEntity)

    @Query("DELETE FROM keywords WHERE keyword = :keyword AND isWhitelist = :isWhitelist")
    suspend fun deleteByKeyword(keyword: String, isWhitelist: Boolean)

    @Query("DELETE FROM keywords")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM keywords WHERE isWhitelist = 0")
    suspend fun getBlacklistCount(): Int

    @Query("SELECT COUNT(*) FROM keywords WHERE isWhitelist = 1")
    suspend fun getWhitelistCount(): Int
}