package com.agon.app.`data`.local.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performBlocking
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.agon.app.`data`.local.entity.BlockEventEntity
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
public class BlockEventDao_Impl(
  __db: RoomDatabase,
) : BlockEventDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfBlockEventEntity: EntityInsertAdapter<BlockEventEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfBlockEventEntity = object : EntityInsertAdapter<BlockEventEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `block_events` (`id`,`packageName`,`appLabel`,`blockType`,`timestamp`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: BlockEventEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.packageName)
        statement.bindText(3, entity.appLabel)
        statement.bindText(4, entity.blockType)
        statement.bindLong(5, entity.timestamp)
      }
    }
  }

  public override suspend fun insert(event: BlockEventEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfBlockEventEntity.insert(_connection, event)
  }

  public override fun getAllFlow(): Flow<List<BlockEventEntity>> {
    val _sql: String = "SELECT * FROM block_events ORDER BY timestamp DESC"
    return createFlow(__db, false, arrayOf("block_events")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPackageName: Int = getColumnIndexOrThrow(_stmt, "packageName")
        val _columnIndexOfAppLabel: Int = getColumnIndexOrThrow(_stmt, "appLabel")
        val _columnIndexOfBlockType: Int = getColumnIndexOrThrow(_stmt, "blockType")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _result: MutableList<BlockEventEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BlockEventEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpPackageName: String
          _tmpPackageName = _stmt.getText(_columnIndexOfPackageName)
          val _tmpAppLabel: String
          _tmpAppLabel = _stmt.getText(_columnIndexOfAppLabel)
          val _tmpBlockType: String
          _tmpBlockType = _stmt.getText(_columnIndexOfBlockType)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          _item = BlockEventEntity(_tmpId,_tmpPackageName,_tmpAppLabel,_tmpBlockType,_tmpTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getRecentFlow(limit: Int): Flow<List<BlockEventEntity>> {
    val _sql: String = "SELECT * FROM block_events ORDER BY timestamp DESC LIMIT ?"
    return createFlow(__db, false, arrayOf("block_events")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPackageName: Int = getColumnIndexOrThrow(_stmt, "packageName")
        val _columnIndexOfAppLabel: Int = getColumnIndexOrThrow(_stmt, "appLabel")
        val _columnIndexOfBlockType: Int = getColumnIndexOrThrow(_stmt, "blockType")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _result: MutableList<BlockEventEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BlockEventEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpPackageName: String
          _tmpPackageName = _stmt.getText(_columnIndexOfPackageName)
          val _tmpAppLabel: String
          _tmpAppLabel = _stmt.getText(_columnIndexOfAppLabel)
          val _tmpBlockType: String
          _tmpBlockType = _stmt.getText(_columnIndexOfBlockType)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          _item = BlockEventEntity(_tmpId,_tmpPackageName,_tmpAppLabel,_tmpBlockType,_tmpTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun totalBlocksFlow(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM block_events"
    return createFlow(__db, false, arrayOf("block_events")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
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

  public override fun blocksSinceFlow(since: Long): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM block_events WHERE timestamp >= ?"
    return createFlow(__db, false, arrayOf("block_events")) { _connection ->
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

  public override fun mostBlockedAppFlow(): Flow<MostBlockedApp?> {
    val _sql: String = "SELECT packageName, appLabel, COUNT(*) as count FROM block_events GROUP BY packageName ORDER BY count DESC LIMIT 1"
    return createFlow(__db, false, arrayOf("block_events")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfPackageName: Int = 0
        val _columnIndexOfAppLabel: Int = 1
        val _columnIndexOfCount: Int = 2
        val _result: MostBlockedApp?
        if (_stmt.step()) {
          val _tmpPackageName: String
          _tmpPackageName = _stmt.getText(_columnIndexOfPackageName)
          val _tmpAppLabel: String
          _tmpAppLabel = _stmt.getText(_columnIndexOfAppLabel)
          val _tmpCount: Int
          _tmpCount = _stmt.getLong(_columnIndexOfCount).toInt()
          _result = MostBlockedApp(_tmpPackageName,_tmpAppLabel,_tmpCount)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun blocksPerAppSince(since: Long): Flow<List<MostBlockedApp>> {
    val _sql: String = "SELECT packageName, appLabel, COUNT(*) as count FROM block_events WHERE timestamp >= ? GROUP BY packageName ORDER BY count DESC"
    return createFlow(__db, false, arrayOf("block_events")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, since)
        val _columnIndexOfPackageName: Int = 0
        val _columnIndexOfAppLabel: Int = 1
        val _columnIndexOfCount: Int = 2
        val _result: MutableList<MostBlockedApp> = mutableListOf()
        while (_stmt.step()) {
          val _item: MostBlockedApp
          val _tmpPackageName: String
          _tmpPackageName = _stmt.getText(_columnIndexOfPackageName)
          val _tmpAppLabel: String
          _tmpAppLabel = _stmt.getText(_columnIndexOfAppLabel)
          val _tmpCount: Int
          _tmpCount = _stmt.getLong(_columnIndexOfCount).toInt()
          _item = MostBlockedApp(_tmpPackageName,_tmpAppLabel,_tmpCount)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun blocksSince(since: Long): List<BlockEventEntity> {
    val _sql: String = "SELECT * FROM block_events WHERE timestamp >= ? ORDER BY timestamp DESC"
    return performBlocking(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, since)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPackageName: Int = getColumnIndexOrThrow(_stmt, "packageName")
        val _columnIndexOfAppLabel: Int = getColumnIndexOrThrow(_stmt, "appLabel")
        val _columnIndexOfBlockType: Int = getColumnIndexOrThrow(_stmt, "blockType")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _result: MutableList<BlockEventEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BlockEventEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpPackageName: String
          _tmpPackageName = _stmt.getText(_columnIndexOfPackageName)
          val _tmpAppLabel: String
          _tmpAppLabel = _stmt.getText(_columnIndexOfAppLabel)
          val _tmpBlockType: String
          _tmpBlockType = _stmt.getText(_columnIndexOfBlockType)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          _item = BlockEventEntity(_tmpId,_tmpPackageName,_tmpAppLabel,_tmpBlockType,_tmpTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getByDateRange(start: Long, end: Long): Flow<List<BlockEventEntity>> {
    val _sql: String = "SELECT * FROM block_events WHERE timestamp >= ? AND timestamp <= ? ORDER BY timestamp DESC"
    return createFlow(__db, false, arrayOf("block_events")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, start)
        _argIndex = 2
        _stmt.bindLong(_argIndex, end)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPackageName: Int = getColumnIndexOrThrow(_stmt, "packageName")
        val _columnIndexOfAppLabel: Int = getColumnIndexOrThrow(_stmt, "appLabel")
        val _columnIndexOfBlockType: Int = getColumnIndexOrThrow(_stmt, "blockType")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _result: MutableList<BlockEventEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BlockEventEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpPackageName: String
          _tmpPackageName = _stmt.getText(_columnIndexOfPackageName)
          val _tmpAppLabel: String
          _tmpAppLabel = _stmt.getText(_columnIndexOfAppLabel)
          val _tmpBlockType: String
          _tmpBlockType = _stmt.getText(_columnIndexOfBlockType)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          _item = BlockEventEntity(_tmpId,_tmpPackageName,_tmpAppLabel,_tmpBlockType,_tmpTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getCount(): Int {
    val _sql: String = "SELECT COUNT(*) FROM block_events"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
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

  public override suspend fun clearOld(threshold: Long) {
    val _sql: String = "DELETE FROM block_events WHERE timestamp < ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, threshold)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM block_events"
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
