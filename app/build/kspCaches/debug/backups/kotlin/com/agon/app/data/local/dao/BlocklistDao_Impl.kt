package com.agon.app.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.agon.app.`data`.local.entity.BlocklistItemEntity
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
public class BlocklistDao_Impl(
  __db: RoomDatabase,
) : BlocklistDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfBlocklistItemEntity: EntityInsertAdapter<BlocklistItemEntity>

  private val __deleteAdapterOfBlocklistItemEntity: EntityDeleteOrUpdateAdapter<BlocklistItemEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfBlocklistItemEntity = object : EntityInsertAdapter<BlocklistItemEntity>()
        {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `blocklist_items` (`id`,`listType`,`category`,`value`) VALUES (nullif(?, 0),?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: BlocklistItemEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.listType)
        statement.bindText(3, entity.category)
        statement.bindText(4, entity.value)
      }
    }
    this.__deleteAdapterOfBlocklistItemEntity = object :
        EntityDeleteOrUpdateAdapter<BlocklistItemEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `blocklist_items` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: BlocklistItemEntity) {
        statement.bindLong(1, entity.id)
      }
    }
  }

  public override suspend fun insert(item: BlocklistItemEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfBlocklistItemEntity.insert(_connection, item)
  }

  public override suspend fun delete(item: BlocklistItemEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __deleteAdapterOfBlocklistItemEntity.handle(_connection, item)
  }

  public override fun getItemsFlow(listType: String, category: String):
      Flow<List<BlocklistItemEntity>> {
    val _sql: String = "SELECT * FROM blocklist_items WHERE listType = ? AND category = ?"
    return createFlow(__db, false, arrayOf("blocklist_items")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, listType)
        _argIndex = 2
        _stmt.bindText(_argIndex, category)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfListType: Int = getColumnIndexOrThrow(_stmt, "listType")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfValue: Int = getColumnIndexOrThrow(_stmt, "value")
        val _result: MutableList<BlocklistItemEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BlocklistItemEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpListType: String
          _tmpListType = _stmt.getText(_columnIndexOfListType)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpValue: String
          _tmpValue = _stmt.getText(_columnIndexOfValue)
          _item = BlocklistItemEntity(_tmpId,_tmpListType,_tmpCategory,_tmpValue)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getItems(listType: String, category: String):
      List<BlocklistItemEntity> {
    val _sql: String = "SELECT * FROM blocklist_items WHERE listType = ? AND category = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, listType)
        _argIndex = 2
        _stmt.bindText(_argIndex, category)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfListType: Int = getColumnIndexOrThrow(_stmt, "listType")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfValue: Int = getColumnIndexOrThrow(_stmt, "value")
        val _result: MutableList<BlocklistItemEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BlocklistItemEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpListType: String
          _tmpListType = _stmt.getText(_columnIndexOfListType)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpValue: String
          _tmpValue = _stmt.getText(_columnIndexOfValue)
          _item = BlocklistItemEntity(_tmpId,_tmpListType,_tmpCategory,_tmpValue)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteByValue(
    listType: String,
    category: String,
    `value`: String,
  ) {
    val _sql: String =
        "DELETE FROM blocklist_items WHERE listType = ? AND category = ? AND value = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, listType)
        _argIndex = 2
        _stmt.bindText(_argIndex, category)
        _argIndex = 3
        _stmt.bindText(_argIndex, value)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteCategory(listType: String, category: String) {
    val _sql: String = "DELETE FROM blocklist_items WHERE listType = ? AND category = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, listType)
        _argIndex = 2
        _stmt.bindText(_argIndex, category)
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
