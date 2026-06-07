package com.agon.app.data.repository

import android.app.Application
import com.agon.app.data.local.dao.AppLimitDao
import com.agon.app.data.local.dao.BlockEventDao
import com.agon.app.data.local.dao.BlocklistDao
import com.agon.app.data.local.dao.MostBlockedApp
import com.agon.app.data.local.dao.ScheduleRuleDao
import com.agon.app.data.local.dao.TamperAlertDao
import com.agon.app.data.local.entity.AppLimitEntity
import com.agon.app.data.local.entity.BlockEventEntity
import com.agon.app.data.local.entity.BlocklistItemEntity
import com.agon.app.data.local.entity.ScheduleRuleEntity
import com.agon.app.data.local.entity.TamperAlertEntity
import com.agon.app.data.remote.FirebaseManager
import com.agon.app.data.settings.AppSettings
import com.agon.app.data.settings.EncryptedPrefs
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val application: Application,
    val blockEventDao: BlockEventDao,
    private val blocklistDao: BlocklistDao,
    val appLimitDao: AppLimitDao,
    private val scheduleRuleDao: ScheduleRuleDao,
    private val tamperAlertDao: TamperAlertDao,
    private val settings: AppSettings,
    private val encryptedPrefs: EncryptedPrefs
) {
    fun getAppSettings(): AppSettings = settings

    // Block Events
    fun getAllBlockEvents(): Flow<List<BlockEventEntity>> = blockEventDao.getAllFlow()
    fun getRecentBlockEvents(limit: Int = 50): Flow<List<BlockEventEntity>> = blockEventDao.getRecentFlow(limit)
    fun totalBlocksFlow(): Flow<Int> = blockEventDao.totalBlocksFlow()
    fun blocksTodayFlow(): Flow<Int> {
        val todayStart = getTodayStart()
        return blockEventDao.blocksSinceFlow(todayStart)
    }
    fun mostBlockedAppFlow(): Flow<MostBlockedApp?> = blockEventDao.mostBlockedAppFlow()
    fun blocksTodayPerApp(): Flow<List<MostBlockedApp>> {
        val todayStart = getTodayStart()
        return blockEventDao.blocksPerAppSince(todayStart)
    }
    suspend fun recordBlock(packageName: String, appLabel: String, blockType: String) {
        blockEventDao.insert(BlockEventEntity(packageName = packageName, appLabel = appLabel, blockType = blockType))
    }
    suspend fun clearAllEvents() = blockEventDao.deleteAll()
    fun getBlockEventsByDateRange(start: Long, end: Long): Flow<List<BlockEventEntity>> =
        blockEventDao.getByDateRange(start, end)
    suspend fun getTotalEventCount(): Int = blockEventDao.getCount()
    suspend fun clearOldEvents(days: Int) {
        val threshold = System.currentTimeMillis() - days * 86_400_000L
        blockEventDao.clearOld(threshold)
    }

    // Blocklist
    fun getBlocklistFlow(listType: String, category: String): Flow<List<BlocklistItemEntity>> =
        blocklistDao.getItemsFlow(listType, category)
    suspend fun getBlocklist(listType: String, category: String): List<BlocklistItemEntity> =
        blocklistDao.getItems(listType, category)
    suspend fun getBlocklistItemById(id: Long): BlocklistItemEntity? =
        blocklistDao.getById(id)
    suspend fun addBlocklistItem(listType: String, category: String, value: String) {
        blocklistDao.insert(BlocklistItemEntity(listType = listType, category = category, value = value))
    }
    suspend fun addBlocklistItem(entity: BlocklistItemEntity) {
        blocklistDao.insert(entity)
    }
    suspend fun removeBlocklistItem(listType: String, category: String, value: String) {
        blocklistDao.deleteByValue(listType, category, value)
    }
    suspend fun removeBlocklistItemById(id: Long) {
        blocklistDao.deleteById(id)
    }

    // App Limits
    fun getAllAppLimits(): Flow<List<AppLimitEntity>> = appLimitDao.getAllFlow()
    suspend fun getAppLimit(packageName: String): AppLimitEntity? = appLimitDao.get(packageName)
    suspend fun setAppLimit(packageName: String, appLabel: String, dailyMinutes: Int) {
        appLimitDao.insert(AppLimitEntity(packageName = packageName, appLabel = appLabel, dailyMinutes = dailyMinutes))
    }
    suspend fun removeAppLimit(limit: AppLimitEntity) = appLimitDao.delete(limit)

    // Tamper Alerts
    fun getTamperAlertsFlow(): Flow<List<TamperAlertEntity>> = tamperAlertDao.getAllFlow()

    suspend fun recordTamperAlert(type: String, detail: String, packageName: String = "", userId: Int = 0) {
        tamperAlertDao.insert(TamperAlertEntity(type = type, detail = detail, packageName = packageName, userId = userId))
        // Side-channel: queue a parent email intent if configured.
        // Done outside the DAO call so a slow mail-app launch can never
        // block the DB write.
        com.agon.app.utils.TamperEmailNotifier.maybeNotify(
            context = application,
            scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.Dispatchers.IO +
                    kotlinx.coroutines.SupervisorJob()
            ),
            settings = settings,
            tamperType = type,
            tamperDetail = detail
        )
    }

    suspend fun clearTamperAlerts() = tamperAlertDao.deleteAll()

    // Schedule Rules
    fun getAllScheduleRules(): Flow<List<ScheduleRuleEntity>> = scheduleRuleDao.getAllFlow()
    suspend fun addScheduleRule(rule: ScheduleRuleEntity): Long = scheduleRuleDao.insert(rule)
    suspend fun updateScheduleRule(rule: ScheduleRuleEntity) = scheduleRuleDao.update(rule)
    suspend fun deleteScheduleRule(rule: ScheduleRuleEntity) = scheduleRuleDao.delete(rule)
    suspend fun toggleScheduleRule(id: Long, enabled: Boolean) = scheduleRuleDao.setEnabled(id, enabled)

    // Streak calculation
    suspend fun calculateStreak(): Int {
        val todayStart = getTodayStart()
        var streak = 0
        var checkTime = todayStart
        while (true) {
            val dayEnd = checkTime + 86400000L
            val hasEvent = blockEventDao.blocksSince(checkTime).any { it.timestamp < dayEnd }
            if (!hasEvent) break
            streak++
            checkTime -= 86400000L
            if (streak > 3650) break 
        }
        return streak
    }

    suspend fun getDaysActive(): Int {
        val allEvents = blockEventDao.blocksSince(0L)
        return allEvents.map { it.timestamp / 86400000L }.distinct().count()
    }

    suspend fun resetAllSettings() {
        clearAllEvents()
        clearTamperAlerts()

        // Issue #241: Clear the Koin-injected EncryptedPrefs instance
        // (not a freshly-constructed one) so listeners registered via
        // pinHashFlow actually get notified. The previous
        // `EncryptedPrefs(application).clear()` call mutated a separate
        // instance, leaving the singleton's listeners stale.
        encryptedPrefs.clear()

        settings.setShieldActive(false)
        settings.setTrialMode(false)
        settings.setDeactivationDelay(0)
        settings.setProfileName("")
        settings.setPinHash("")
        settings.setStreakCount(0)
        settings.setLongestStreak(0)
        settings.setXpPoints(0)
        settings.setLevel(1)

        // Clear all schedules and limits
        appLimitDao.deleteAll()
        scheduleRuleDao.deleteAll()
    }

    // ── Firebase Remote Monitoring ─────────────────────────────

    suspend fun syncToFirebase() {
        if (!settings.isRemoteMonitoringEnabled()) return
        try {
            val firebase = FirebaseManager(application, blockEventDao, appLimitDao, settings)
            if (!firebase.initialize()) return
            firebase.syncDeviceInfo()
            firebase.syncAppLimits()
            firebase.syncBlockEvents()
            firebase.syncWeeklyReport()
        } catch (e: Exception) {
            timber.log.Timber.e(e, "AppRepository: syncToFirebase failed")
        }
    }

    suspend fun sendAlert(type: String, message: String) {
        if (!settings.isRemoteMonitoringEnabled()) return
        try {
            val firebase = FirebaseManager(application, blockEventDao, appLimitDao, settings)
            if (!firebase.initialize()) return
            firebase.sendAlert(type, message)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "AppRepository: sendAlert failed")
        }
    }

    suspend fun processRemoteCommands() {
        if (!settings.isRemoteMonitoringEnabled()) return
        try {
            val firebase = FirebaseManager(application, blockEventDao, appLimitDao, settings)
            if (!firebase.initialize()) return
            firebase.processPendingCommands { command, data ->
                when (command) {
                    "lock" -> settings.setShieldActive(true)
                    "unlock" -> settings.setShieldActive(false)
                    "enable_shield" -> settings.setShieldActive(true)
                    "disable_shield" -> settings.setShieldActive(false)
                }
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "AppRepository: processRemoteCommands failed")
        }
    }

    private fun getTodayStart(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

