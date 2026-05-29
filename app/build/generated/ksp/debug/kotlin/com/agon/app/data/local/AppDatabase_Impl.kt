package com.agon.app.`data`.local

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.agon.app.`data`.local.dao.AppLimitDao
import com.agon.app.`data`.local.dao.AppLimitDao_Impl
import com.agon.app.`data`.local.dao.BlockEventDao
import com.agon.app.`data`.local.dao.BlockEventDao_Impl
import com.agon.app.`data`.local.dao.BlocklistDao
import com.agon.app.`data`.local.dao.BlocklistDao_Impl
import com.agon.app.`data`.local.dao.ScheduleRuleDao
import com.agon.app.`data`.local.dao.ScheduleRuleDao_Impl
import com.agon.app.`data`.local.dao.TamperAlertDao
import com.agon.app.`data`.local.dao.TamperAlertDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _blockEventDao: Lazy<BlockEventDao> = lazy {
    BlockEventDao_Impl(this)
  }

  private val _blocklistDao: Lazy<BlocklistDao> = lazy {
    BlocklistDao_Impl(this)
  }

  private val _appLimitDao: Lazy<AppLimitDao> = lazy {
    AppLimitDao_Impl(this)
  }

  private val _scheduleRuleDao: Lazy<ScheduleRuleDao> = lazy {
    ScheduleRuleDao_Impl(this)
  }

  private val _tamperAlertDao: Lazy<TamperAlertDao> = lazy {
    TamperAlertDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(3, "6eaf0c71925684f65ff537facc4075d0", "f5ff3be63e51dc367b3a3f0ac8c12c6f") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `block_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packageName` TEXT NOT NULL, `appLabel` TEXT NOT NULL, `blockType` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `blocklist_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `listType` TEXT NOT NULL, `category` TEXT NOT NULL, `value` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `label` TEXT, `regexEnabled` INTEGER NOT NULL, `sensitivityLevel` TEXT NOT NULL, `urlCategory` TEXT)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `app_limits` (`packageName` TEXT NOT NULL, `appLabel` TEXT NOT NULL, `dailyMinutes` INTEGER NOT NULL, PRIMARY KEY(`packageName`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `schedule_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `daysOfWeek` TEXT NOT NULL, `startHour` INTEGER NOT NULL, `startMinute` INTEGER NOT NULL, `endHour` INTEGER NOT NULL, `endMinute` INTEGER NOT NULL, `enabled` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `tamper_alerts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `detail` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `packageName` TEXT NOT NULL, `userId` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6eaf0c71925684f65ff537facc4075d0')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `block_events`")
        connection.execSQL("DROP TABLE IF EXISTS `blocklist_items`")
        connection.execSQL("DROP TABLE IF EXISTS `app_limits`")
        connection.execSQL("DROP TABLE IF EXISTS `schedule_rules`")
        connection.execSQL("DROP TABLE IF EXISTS `tamper_alerts`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsBlockEvents: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsBlockEvents.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBlockEvents.put("packageName", TableInfo.Column("packageName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBlockEvents.put("appLabel", TableInfo.Column("appLabel", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBlockEvents.put("blockType", TableInfo.Column("blockType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBlockEvents.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysBlockEvents: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesBlockEvents: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoBlockEvents: TableInfo = TableInfo("block_events", _columnsBlockEvents, _foreignKeysBlockEvents, _indicesBlockEvents)
        val _existingBlockEvents: TableInfo = read(connection, "block_events")
        if (!_infoBlockEvents.equals(_existingBlockEvents)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |block_events(com.agon.app.data.local.entity.BlockEventEntity).
              | Expected:
              |""".trimMargin() + _infoBlockEvents + """
              |
              | Found:
              |""".trimMargin() + _existingBlockEvents)
        }
        val _columnsBlocklistItems: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsBlocklistItems.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBlocklistItems.put("listType", TableInfo.Column("listType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBlocklistItems.put("category", TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBlocklistItems.put("value", TableInfo.Column("value", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBlocklistItems.put("enabled", TableInfo.Column("enabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBlocklistItems.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBlocklistItems.put("label", TableInfo.Column("label", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBlocklistItems.put("regexEnabled", TableInfo.Column("regexEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBlocklistItems.put("sensitivityLevel", TableInfo.Column("sensitivityLevel", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBlocklistItems.put("urlCategory", TableInfo.Column("urlCategory", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysBlocklistItems: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesBlocklistItems: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoBlocklistItems: TableInfo = TableInfo("blocklist_items", _columnsBlocklistItems, _foreignKeysBlocklistItems, _indicesBlocklistItems)
        val _existingBlocklistItems: TableInfo = read(connection, "blocklist_items")
        if (!_infoBlocklistItems.equals(_existingBlocklistItems)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |blocklist_items(com.agon.app.data.local.entity.BlocklistItemEntity).
              | Expected:
              |""".trimMargin() + _infoBlocklistItems + """
              |
              | Found:
              |""".trimMargin() + _existingBlocklistItems)
        }
        val _columnsAppLimits: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsAppLimits.put("packageName", TableInfo.Column("packageName", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAppLimits.put("appLabel", TableInfo.Column("appLabel", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAppLimits.put("dailyMinutes", TableInfo.Column("dailyMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysAppLimits: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesAppLimits: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoAppLimits: TableInfo = TableInfo("app_limits", _columnsAppLimits, _foreignKeysAppLimits, _indicesAppLimits)
        val _existingAppLimits: TableInfo = read(connection, "app_limits")
        if (!_infoAppLimits.equals(_existingAppLimits)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |app_limits(com.agon.app.data.local.entity.AppLimitEntity).
              | Expected:
              |""".trimMargin() + _infoAppLimits + """
              |
              | Found:
              |""".trimMargin() + _existingAppLimits)
        }
        val _columnsScheduleRules: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsScheduleRules.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsScheduleRules.put("daysOfWeek", TableInfo.Column("daysOfWeek", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsScheduleRules.put("startHour", TableInfo.Column("startHour", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsScheduleRules.put("startMinute", TableInfo.Column("startMinute", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsScheduleRules.put("endHour", TableInfo.Column("endHour", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsScheduleRules.put("endMinute", TableInfo.Column("endMinute", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsScheduleRules.put("enabled", TableInfo.Column("enabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysScheduleRules: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesScheduleRules: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoScheduleRules: TableInfo = TableInfo("schedule_rules", _columnsScheduleRules, _foreignKeysScheduleRules, _indicesScheduleRules)
        val _existingScheduleRules: TableInfo = read(connection, "schedule_rules")
        if (!_infoScheduleRules.equals(_existingScheduleRules)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |schedule_rules(com.agon.app.data.local.entity.ScheduleRuleEntity).
              | Expected:
              |""".trimMargin() + _infoScheduleRules + """
              |
              | Found:
              |""".trimMargin() + _existingScheduleRules)
        }
        val _columnsTamperAlerts: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTamperAlerts.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTamperAlerts.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTamperAlerts.put("detail", TableInfo.Column("detail", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTamperAlerts.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTamperAlerts.put("packageName", TableInfo.Column("packageName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTamperAlerts.put("userId", TableInfo.Column("userId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTamperAlerts: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesTamperAlerts: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoTamperAlerts: TableInfo = TableInfo("tamper_alerts", _columnsTamperAlerts, _foreignKeysTamperAlerts, _indicesTamperAlerts)
        val _existingTamperAlerts: TableInfo = read(connection, "tamper_alerts")
        if (!_infoTamperAlerts.equals(_existingTamperAlerts)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |tamper_alerts(com.agon.app.data.local.entity.TamperAlertEntity).
              | Expected:
              |""".trimMargin() + _infoTamperAlerts + """
              |
              | Found:
              |""".trimMargin() + _existingTamperAlerts)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "block_events", "blocklist_items", "app_limits", "schedule_rules", "tamper_alerts")
  }

  public override fun clearAllTables() {
    super.performClear(false, "block_events", "blocklist_items", "app_limits", "schedule_rules", "tamper_alerts")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(BlockEventDao::class, BlockEventDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(BlocklistDao::class, BlocklistDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(AppLimitDao::class, AppLimitDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ScheduleRuleDao::class, ScheduleRuleDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(TamperAlertDao::class, TamperAlertDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun blockEventDao(): BlockEventDao = _blockEventDao.value

  public override fun blocklistDao(): BlocklistDao = _blocklistDao.value

  public override fun appLimitDao(): AppLimitDao = _appLimitDao.value

  public override fun scheduleRuleDao(): ScheduleRuleDao = _scheduleRuleDao.value

  public override fun tamperAlertDao(): TamperAlertDao = _tamperAlertDao.value
}
