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
import kotlin.Boolean
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
    this.__insertAdapterOfBlocklistItemEntity = object : EntityInsertAdapter<BlocklistItemEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `blocklist_items` (`id`,`listType`,`category`,`value`,`enabled`,`createdAt`,`label`,`regexEnabled`,`sensitivityLevel`,`urlCategory`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: BlocklistItemEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.listType)
        statement.bindText(3, entity.category)
        statement.bindText(4, entity.value)
        val _tmp: Int = if (entity.enabled) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        statement.bindLong(6, entity.createdAt)
        val _tmpLabel: String? = entity.label
        if (_tmpLabel == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpLabel)
        }
        val _tmp_1: Int = if (entity.regexEnabled) 1 else 0
        statement.bindLong(8, _tmp_1.toLong())
        statement.bindText(9, entity.sensitivityLevel)
        val _tmpUrlCategory: String? = entity.urlCategory
        if (_tmpUrlCategory == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpUrlCategory)
        }
      }
    }
    this.__deleteAdapterOfBlocklistItemEntity = object : EntityDeleteOrUpdateAdapter<BlocklistItemEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `blocklist_items` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: BlocklistItemEntity) {
        statement.bindLong(1, entity.id)
      }
    }
  }

  public override suspend fun insert(item: BlocklistItemEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfBlocklistItemEntity.insert(_connection, item)
  }

  public override suspend fun delete(item: BlocklistItemEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfBlocklistItemEntity.handle(_connection, item)
  }

  public override fun getItemsFlow(listType: String, category: String): Flow<List<BlocklistItemEntity>> {
    val _sql: String = "SELECT * FROM blocklist_items WHERE listType = ? AND category = ? ORDER BY createdAt DESC"
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
        val _columnIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfLabel: Int = getColumnIndexOrThrow(_stmt, "label")
        val _columnIndexOfRegexEnabled: Int = getColumnIndexOrThrow(_stmt, "regexEnabled")
        val _columnIndexOfSensitivityLevel: Int = getColumnIndexOrThrow(_stmt, "sensitivityLevel")
        val _columnIndexOfUrlCategory: Int = getColumnIndexOrThrow(_stmt, "urlCategory")
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
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpLabel: String?
          if (_stmt.isNull(_columnIndexOfLabel)) {
            _tmpLabel = null
          } else {
            _tmpLabel = _stmt.getText(_columnIndexOfLabel)
          }
          val _tmpRegexEnabled: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfRegexEnabled).toInt()
          _tmpRegexEnabled = _tmp_1 != 0
          val _tmpSensitivityLevel: String
          _tmpSensitivityLevel = _stmt.getText(_columnIndexOfSensitivityLevel)
          val _tmpUrlCategory: String?
          if (_stmt.isNull(_columnIndexOfUrlCategory)) {
            _tmpUrlCategory = null
          } else {
            _tmpUrlCategory = _stmt.getText(_columnIndexOfUrlCategory)
          }
          _item = BlocklistItemEntity(_tmpId,_tmpListType,_tmpCategory,_tmpValue,_tmpEnabled,_tmpCreatedAt,_tmpLabel,_tmpRegexEnabled,_tmpSensitivityLevel,_tmpUrlCategory)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getItems(listType: String, category: String): List<BlocklistItemEntity> {
    val _sql: String = "SELECT * FROM blocklist_items WHERE listType = ? AND category = ? ORDER BY createdAt DESC"
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
        val _columnIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfLabel: Int = getColumnIndexOrThrow(_stmt, "label")
        val _columnIndexOfRegexEnabled: Int = getColumnIndexOrThrow(_stmt, "regexEnabled")
        val _columnIndexOfSensitivityLevel: Int = getColumnIndexOrThrow(_stmt, "sensitivityLevel")
        val _columnIndexOfUrlCategory: Int = getColumnIndexOrThrow(_stmt, "urlCategory")
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
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpLabel: String?
          if (_stmt.isNull(_columnIndexOfLabel)) {
            _tmpLabel = null
          } else {
            _tmpLabel = _stmt.getText(_columnIndexOfLabel)
          }
          val _tmpRegexEnabled: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfRegexEnabled).toInt()
          _tmpRegexEnabled = _tmp_1 != 0
          val _tmpSensitivityLevel: String
          _tmpSensitivityLevel = _stmt.getText(_columnIndexOfSensitivityLevel)
          val _tmpUrlCategory: String?
          if (_stmt.isNull(_columnIndexOfUrlCategory)) {
            _tmpUrlCategory = null
          } else {
            _tmpUrlCategory = _stmt.getText(_columnIndexOfUrlCategory)
          }
          _item = BlocklistItemEntity(_tmpId,_tmpListType,_tmpCategory,_tmpValue,_tmpEnabled,_tmpCreatedAt,_tmpLabel,_tmpRegexEnabled,_tmpSensitivityLevel,_tmpUrlCategory)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: Long): BlocklistItemEntity? {
    val _sql: String = "SELECT * FROM blocklist_items WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfListType: Int = getColumnIndexOrThrow(_stmt, "listType")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfValue: Int = getColumnIndexOrThrow(_stmt, "value")
        val _columnIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfLabel: Int = getColumnIndexOrThrow(_stmt, "label")
        val _columnIndexOfRegexEnabled: Int = getColumnIndexOrThrow(_stmt, "regexEnabled")
        val _columnIndexOfSensitivityLevel: Int = getColumnIndexOrThrow(_stmt, "sensitivityLevel")
        val _columnIndexOfUrlCategory: Int = getColumnIndexOrThrow(_stmt, "urlCategory")
        val _result: BlocklistItemEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpListType: String
          _tmpListType = _stmt.getText(_columnIndexOfListType)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpValue: String
          _tmpValue = _stmt.getText(_columnIndexOfValue)
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpLabel: String?
          if (_stmt.isNull(_columnIndexOfLabel)) {
            _tmpLabel = null
          } else {
            _tmpLabel = _stmt.getText(_columnIndexOfLabel)
          }
          val _tmpRegexEnabled: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfRegexEnabled).toInt()
          _tmpRegexEnabled = _tmp_1 != 0
          val _tmpSensitivityLevel: String
          _tmpSensitivityLevel = _stmt.getText(_columnIndexOfSensitivityLevel)
          val _tmpUrlCategory: String?
          if (_stmt.isNull(_columnIndexOfUrlCategory)) {
            _tmpUrlCategory = null
          } else {
            _tmpUrlCategory = _stmt.getText(_columnIndexOfUrlCategory)
          }
          _result = BlocklistItemEntity(_tmpId,_tmpListType,_tmpCategory,_tmpValue,_tmpEnabled,_tmpCreatedAt,_tmpLabel,_tmpRegexEnabled,_tmpSensitivityLevel,_tmpUrlCategory)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun search(
    listType: String,
    category: String,
    query: String,
  ): Flow<List<BlocklistItemEntity>> {
    val _sql: String = """
        |
        |        SELECT * FROM blocklist_items 
        |        WHERE listType = ? AND category = ? 
        |        AND (value LIKE '%' || ? || '%' OR label LIKE '%' || ? || '%')
        |        ORDER BY createdAt DESC
        |    
        """.trimMargin()
    return createFlow(__db, false, arrayOf("blocklist_items")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, listType)
        _argIndex = 2
        _stmt.bindText(_argIndex, category)
        _argIndex = 3
        _stmt.bindText(_argIndex, query)
        _argIndex = 4
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfListType: Int = getColumnIndexOrThrow(_stmt, "listType")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfValue: Int = getColumnIndexOrThrow(_stmt, "value")
        val _columnIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfLabel: Int = getColumnIndexOrThrow(_stmt, "label")
        val _columnIndexOfRegexEnabled: Int = getColumnIndexOrThrow(_stmt, "regexEnabled")
        val _columnIndexOfSensitivityLevel: Int = getColumnIndexOrThrow(_stmt, "sensitivityLevel")
        val _columnIndexOfUrlCategory: Int = getColumnIndexOrThrow(_stmt, "urlCategory")
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
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpLabel: String?
          if (_stmt.isNull(_columnIndexOfLabel)) {
            _tmpLabel = null
          } else {
            _tmpLabel = _stmt.getText(_columnIndexOfLabel)
          }
          val _tmpRegexEnabled: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfRegexEnabled).toInt()
          _tmpRegexEnabled = _tmp_1 != 0
          val _tmpSensitivityLevel: String
          _tmpSensitivityLevel = _stmt.getText(_columnIndexOfSensitivityLevel)
          val _tmpUrlCategory: String?
          if (_stmt.isNull(_columnIndexOfUrlCategory)) {
            _tmpUrlCategory = null
          } else {
            _tmpUrlCategory = _stmt.getText(_columnIndexOfUrlCategory)
          }
          _item = BlocklistItemEntity(_tmpId,_tmpListType,_tmpCategory,_tmpValue,_tmpEnabled,_tmpCreatedAt,_tmpLabel,_tmpRegexEnabled,_tmpSensitivityLevel,_tmpUrlCategory)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: Long) {
    val _sql: String = "DELETE FROM blocklist_items WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
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
    val _sql: String = "DELETE FROM blocklist_items WHERE listType = ? AND category = ? AND value = ?"
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
