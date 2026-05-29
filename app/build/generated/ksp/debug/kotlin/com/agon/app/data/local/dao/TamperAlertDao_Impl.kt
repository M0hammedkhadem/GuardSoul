package com.agon.app.`data`.local.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.agon.app.`data`.local.entity.TamperAlertEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
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
public class TamperAlertDao_Impl(
  __db: RoomDatabase,
) : TamperAlertDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfTamperAlertEntity: EntityInsertAdapter<TamperAlertEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfTamperAlertEntity = object : EntityInsertAdapter<TamperAlertEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `tamper_alerts` (`id`,`type`,`detail`,`timestamp`,`packageName`,`userId`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: TamperAlertEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.type)
        statement.bindText(3, entity.detail)
        statement.bindLong(4, entity.timestamp)
        statement.bindText(5, entity.packageName)
        statement.bindLong(6, entity.userId.toLong())
      }
    }
  }

  public override suspend fun insert(alert: TamperAlertEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfTamperAlertEntity.insert(_connection, alert)
  }

  public override fun getAllFlow(): Flow<List<TamperAlertEntity>> {
    val _sql: String = "SELECT * FROM tamper_alerts ORDER BY timestamp DESC"
    return createFlow(__db, false, arrayOf("tamper_alerts")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfDetail: Int = getColumnIndexOrThrow(_stmt, "detail")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfPackageName: Int = getColumnIndexOrThrow(_stmt, "packageName")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _result: MutableList<TamperAlertEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: TamperAlertEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpDetail: String
          _tmpDetail = _stmt.getText(_columnIndexOfDetail)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpPackageName: String
          _tmpPackageName = _stmt.getText(_columnIndexOfPackageName)
          val _tmpUserId: Int
          _tmpUserId = _stmt.getLong(_columnIndexOfUserId).toInt()
          _item = TamperAlertEntity(_tmpId,_tmpType,_tmpDetail,_tmpTimestamp,_tmpPackageName,_tmpUserId)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getRecentFlow(limit: Int): Flow<List<TamperAlertEntity>> {
    val _sql: String = "SELECT * FROM tamper_alerts ORDER BY timestamp DESC LIMIT ?"
    return createFlow(__db, false, arrayOf("tamper_alerts")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfDetail: Int = getColumnIndexOrThrow(_stmt, "detail")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfPackageName: Int = getColumnIndexOrThrow(_stmt, "packageName")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _result: MutableList<TamperAlertEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: TamperAlertEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpDetail: String
          _tmpDetail = _stmt.getText(_columnIndexOfDetail)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpPackageName: String
          _tmpPackageName = _stmt.getText(_columnIndexOfPackageName)
          val _tmpUserId: Int
          _tmpUserId = _stmt.getLong(_columnIndexOfUserId).toInt()
          _item = TamperAlertEntity(_tmpId,_tmpType,_tmpDetail,_tmpTimestamp,_tmpPackageName,_tmpUserId)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun countSince(since: Long): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM tamper_alerts WHERE timestamp >= ?"
    return createFlow(__db, false, arrayOf("tamper_alerts")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, since)
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM tamper_alerts"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
