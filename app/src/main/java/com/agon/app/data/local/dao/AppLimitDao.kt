package com.agon.app.data.local.dao

import androidx.room.*
import com.agon.app.data.local.entity.AppLimitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLimitDao {
    @Query("SELECT * FROM app_limits")
    fun getAllFlow(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits")
    suspend fun getAll(): List<AppLimitEntity>

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName")
    suspend fun get(packageName: String): AppLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(limit: AppLimitEntity)

    @Delete
    suspend fun delete(limit: AppLimitEntity)
}
