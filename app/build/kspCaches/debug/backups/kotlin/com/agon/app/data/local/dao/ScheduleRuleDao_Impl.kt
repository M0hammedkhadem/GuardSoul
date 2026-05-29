package com.agon.app.`data`.local.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.agon.app.`data`.local.entity.ScheduleRuleEntity
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
public class ScheduleRuleDao_Impl(
  __db: RoomDatabase,
) : ScheduleRuleDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfScheduleRuleEntity: EntityInsertAdapter<ScheduleRuleEntity>

  private val __deleteAdapterOfScheduleRuleEntity: EntityDeleteOrUpdateAdapter<ScheduleRuleEntity>

  private val __updateAdapterOfScheduleRuleEntity: EntityDeleteOrUpdateAdapter<ScheduleRuleEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfScheduleRuleEntity = object : EntityInsertAdapter<ScheduleRuleEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `schedule_rules` (`id`,`daysOfWeek`,`startHour`,`startMinute`,`endHour`,`endMinute`,`enabled`) VALUES (nullif(?, 0),?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ScheduleRuleEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.daysOfWeek)
        statement.bindLong(3, entity.startHour.toLong())
        statement.bindLong(4, entity.startMinute.toLong())
        statement.bindLong(5, entity.endHour.toLong())
        statement.bindLong(6, entity.endMinute.toLong())
        val _tmp: Int = if (entity.enabled) 1 else 0
        statement.bindLong(7, _tmp.toLong())
      }
    }
    this.__deleteAdapterOfScheduleRuleEntity = object :
        EntityDeleteOrUpdateAdapter<ScheduleRuleEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `schedule_rules` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: ScheduleRuleEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfScheduleRuleEntity = object :
        EntityDeleteOrUpdateAdapter<ScheduleRuleEntity>() {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `schedule_rules` SET `id` = ?,`daysOfWeek` = ?,`startHour` = ?,`startMinute` = ?,`endHour` = ?,`endMinute` = ?,`enabled` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: ScheduleRuleEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.daysOfWeek)
        statement.bindLong(3, entity.startHour.toLong())
        statement.bindLong(4, entity.startMinute.toLong())
        statement.bindLong(5, entity.endHour.toLong())
        statement.bindLong(6, entity.endMinute.toLong())
        val _tmp: Int = if (entity.enabled) 1 else 0
        statement.bindLong(7, _tmp.toLong())
        statement.bindLong(8, entity.id)
      }
    }
  }

  public override suspend fun insert(rule: ScheduleRuleEntity): Long = performSuspending(__db,
      false, true) { _connection ->
    val _result: Long = __insertAdapterOfScheduleRuleEntity.insertAndReturnId(_connection, rule)
    _result
  }

  public override suspend fun delete(rule: ScheduleRuleEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __deleteAdapterOfScheduleRuleEntity.handle(_connection, rule)
  }

  public override suspend fun update(rule: ScheduleRuleEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __updateAdapterOfScheduleRuleEntity.handle(_connection, rule)
  }

  public override fun getAllFlow(): Flow<List<ScheduleRuleEntity>> {
    val _sql: String = "SELECT * FROM schedule_rules ORDER BY id ASC"
    return createFlow(__db, false, arrayOf("schedule_rules")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDaysOfWeek: Int = getColumnIndexOrThrow(_stmt, "daysOfWeek")
        val _columnIndexOfStartHour: Int = getColumnIndexOrThrow(_stmt, "startHour")
        val _columnIndexOfStartMinute: Int = getColumnIndexOrThrow(_stmt, "startMinute")
        val _columnIndexOfEndHour: Int = getColumnIndexOrThrow(_stmt, "endHour")
        val _columnIndexOfEndMinute: Int = getColumnIndexOrThrow(_stmt, "endMinute")
        val _columnIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _result: MutableList<ScheduleRuleEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ScheduleRuleEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpDaysOfWeek: String
          _tmpDaysOfWeek = _stmt.getText(_columnIndexOfDaysOfWeek)
          val _tmpStartHour: Int
          _tmpStartHour = _stmt.getLong(_columnIndexOfStartHour).toInt()
          val _tmpStartMinute: Int
          _tmpStartMinute = _stmt.getLong(_columnIndexOfStartMinute).toInt()
          val _tmpEndHour: Int
          _tmpEndHour = _stmt.getLong(_columnIndexOfEndHour).toInt()
          val _tmpEndMinute: Int
          _tmpEndMinute = _stmt.getLong(_columnIndexOfEndMinute).toInt()
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          _item =
              ScheduleRuleEntity(_tmpId,_tmpDaysOfWeek,_tmpStartHour,_tmpStartMinute,_tmpEndHour,_tmpEndMinute,_tmpEnabled)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAll(): List<ScheduleRuleEntity> {
    val _sql: String = "SELECT * FROM schedule_rules ORDER BY id ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDaysOfWeek: Int = getColumnIndexOrThrow(_stmt, "daysOfWeek")
        val _columnIndexOfStartHour: Int = getColumnIndexOrThrow(_stmt, "startHour")
        val _columnIndexOfStartMinute: Int = getColumnIndexOrThrow(_stmt, "startMinute")
        val _columnIndexOfEndHour: Int = getColumnIndexOrThrow(_stmt, "endHour")
        val _columnIndexOfEndMinute: Int = getColumnIndexOrThrow(_stmt, "endMinute")
        val _columnIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _result: MutableList<ScheduleRuleEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ScheduleRuleEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpDaysOfWeek: String
          _tmpDaysOfWeek = _stmt.getText(_columnIndexOfDaysOfWeek)
          val _tmpStartHour: Int
          _tmpStartHour = _stmt.getLong(_columnIndexOfStartHour).toInt()
          val _tmpStartMinute: Int
          _tmpStartMinute = _stmt.getLong(_columnIndexOfStartMinute).toInt()
          val _tmpEndHour: Int
          _tmpEndHour = _stmt.getLong(_columnIndexOfEndHour).toInt()
          val _tmpEndMinute: Int
          _tmpEndMinute = _stmt.getLong(_columnIndexOfEndMinute).toInt()
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          _item =
              ScheduleRuleEntity(_tmpId,_tmpDaysOfWeek,_tmpStartHour,_tmpStartMinute,_tmpEndHour,_tmpEndMinute,_tmpEnabled)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun setEnabled(id: Long, enabled: Boolean) {
    val _sql: String = "UPDATE schedule_rules SET enabled = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (enabled) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, id)
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
