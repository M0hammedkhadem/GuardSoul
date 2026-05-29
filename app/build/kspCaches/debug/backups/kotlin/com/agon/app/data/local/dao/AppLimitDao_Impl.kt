package com.agon.app.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.agon.app.`data`.local.entity.AppLimitEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppLimitDao_Impl(
  __db: RoomDatabase,
) : AppLimitDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfAppLimitEntity: EntityInsertAdapter<AppLimitEntity>

  private val __deleteAdapterOfAppLimitEntity: EntityDeleteOrUpdateAdapter<AppLimitEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfAppLimitEntity = object : EntityInsertAdapter<AppLimitEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `app_limits` (`packageName`,`appLabel`,`dailyMinutes`) VALUES (?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: AppLimitEntity) {
        statement.bindText(1, entity.packageName)
        statement.bindText(2, entity.appLabel)
        statement.bindLong(3, entity.dailyMinutes.toLong())
      }
    }
    this.__deleteAdapterOfAppLimitEntity = object : EntityDeleteOrUpdateAdapter<AppLimitEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `app_limits` WHERE `packageName` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: AppLimitEntity) {
        statement.bindText(1, entity.packageName)
      }
    }
  }

  public override suspend fun insert(limit: AppLimitEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfAppLimitEntity.insert(_connection, limit)
  }

  public override suspend fun delete(limit: AppLimitEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfAppLimitEntity.handle(_connection, limit)
  }

  public override fun getAllFlow(): Flow<List<AppLimitEntity>> {
    val _sql: String = "SELECT * FROM app_limits"
    return createFlow(__db, false, arrayOf("app_limits")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfPackageName: Int = getColumnIndexOrThrow(_stmt, "packageName")
        val _columnIndexOfAppLabel: Int = getColumnIndexOrThrow(_stmt, "appLabel")
        val _columnIndexOfDailyMinutes: Int = getColumnIndexOrThrow(_stmt, "dailyMinutes")
        val _result: MutableList<AppLimitEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: AppLimitEntity
          val _tmpPackageName: String
          _tmpPackageName = _stmt.getText(_columnIndexOfPackageName)
          val _tmpAppLabel: String
          _tmpAppLabel = _stmt.getText(_columnIndexOfAppLabel)
          val _tmpDailyMinutes: Int
          _tmpDailyMinutes = _stmt.getLong(_columnIndexOfDailyMinutes).toInt()
          _item = AppLimitEntity(_tmpPackageName,_tmpAppLabel,_tmpDailyMinutes)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAll(): List<AppLimitEntity> {
    val _sql: String = "SELECT * FROM app_limits"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfPackageName: Int = getColumnIndexOrThrow(_stmt, "packageName")
        val _columnIndexOfAppLabel: Int = getColumnIndexOrThrow(_stmt, "appLabel")
        val _columnIndexOfDailyMinutes: Int = getColumnIndexOrThrow(_stmt, "dailyMinutes")
        val _result: MutableList<AppLimitEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: AppLimitEntity
          val _tmpPackageName: String
          _tmpPackageName = _stmt.getText(_columnIndexOfPackageName)
          val _tmpAppLabel: String
          _tmpAppLabel = _stmt.getText(_columnIndexOfAppLabel)
          val _tmpDailyMinutes: Int
          _tmpDailyMinutes = _stmt.getLong(_columnIndexOfDailyMinutes).toInt()
          _item = AppLimitEntity(_tmpPackageName,_tmpAppLabel,_tmpDailyMinutes)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun `get`(packageName: String): AppLimitEntity? {
    val _sql: String = "SELECT * FROM app_limits WHERE packageName = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, packageName)
        val _columnIndexOfPackageName: Int = getColumnIndexOrThrow(_stmt, "packageName")
        val _columnIndexOfAppLabel: Int = getColumnIndexOrThrow(_stmt, "appLabel")
        val _columnIndexOfDailyMinutes: Int = getColumnIndexOrThrow(_stmt, "dailyMinutes")
        val _result: AppLimitEntity?
        if (_stmt.step()) {
          val _tmpPackageName: String
          _tmpPackageName = _stmt.getText(_columnIndexOfPackageName)
          val _tmpAppLabel: String
          _tmpAppLabel = _stmt.getText(_columnIndexOfAppLabel)
          val _tmpDailyMinutes: Int
          _tmpDailyMinutes = _stmt.getLong(_columnIndexOfDailyMinutes).toInt()
          _result = AppLimitEntity(_tmpPackageName,_tmpAppLabel,_tmpDailyMinutes)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
