package com.agon.app.data.repository

import android.content.Context
import com.agon.app.data.local.AppDatabase
import com.agon.app.data.local.dao.MostBlockedApp
import com.agon.app.data.local.entity.AppLimitEntity
import com.agon.app.data.local.entity.BlockEventEntity
import com.agon.app.data.local.entity.BlocklistItemEntity
import com.agon.app.data.local.entity.ScheduleRuleEntity
import com.agon.app.data.remote.FirebaseManager
import com.agon.app.data.settings.AppSettings
import kotlinx.coroutines.flow.Flow

class AppRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val settings = AppSettings(context)

    val blockEventDao = db.blockEventDao()
    val blocklistDao = db.blocklistDao()
    val appLimitDao = db.appLimitDao()
    val scheduleRuleDao = db.scheduleRuleDao()

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

    // Blocklist
    fun getBlocklistFlow(listType: String, category: String): Flow<List<BlocklistItemEntity>> =
        blocklistDao.getItemsFlow(listType, category)
    suspend fun getBlocklist(listType: String, category: String): List<BlocklistItemEntity> =
        blocklistDao.getItems(listType, category)
    suspend fun addBlocklistItem(listType: String, category: String, value: String) {
        blocklistDao.insert(BlocklistItemEntity(listType = listType, category = category, value = value))
    }
    suspend fun removeBlocklistItem(listType: String, category: String, value: String) {
        blocklistDao.deleteByValue(listType, category, value)
    }
    suspend fun getFullBlocklist(listType: String) = blocklistDao.getItems(listType, "apps")

    // App Limits
    fun getAllAppLimits(): Flow<List<AppLimitEntity>> = appLimitDao.getAllFlow()
    suspend fun getAppLimit(packageName: String): AppLimitEntity? = appLimitDao.get(packageName)
    suspend fun setAppLimit(packageName: String, appLabel: String, dailyMinutes: Int) {
        appLimitDao.insert(AppLimitEntity(packageName = packageName, appLabel = appLabel, dailyMinutes = dailyMinutes))
    }
    suspend fun removeAppLimit(limit: AppLimitEntity) = appLimitDao.delete(limit)

    // Schedule Rules
    fun getAllScheduleRules(): Flow<List<ScheduleRuleEntity>> = scheduleRuleDao.getAllFlow()
    suspend fun addScheduleRule(rule: ScheduleRuleEntity): Long = scheduleRuleDao.insert(rule)
    suspend fun updateScheduleRule(rule: ScheduleRuleEntity) = scheduleRuleDao.update(rule)
    suspend fun deleteScheduleRule(rule: ScheduleRuleEntity) = scheduleRuleDao.delete(rule)
    suspend fun toggleScheduleRule(id: Long, enabled: Boolean) = scheduleRuleDao.setEnabled(id, enabled)

    // Streak calculation — counts consecutive days with ≥1 block event
    suspend fun calculateStreak(): Int {
        val allEvents = blockEventDao.blocksSince(0L)
        val todayStart = getTodayStart()
        var streak = 0
        var checkTime = todayStart

        while (true) {
            val dayEnd = checkTime + 86400000L
            val hasEvent = allEvents.any { it.timestamp in checkTime until dayEnd }
            if (!hasEvent) break
            streak++
            checkTime -= 86400000L
            val oldest = allEvents.minOfOrNull { it.timestamp } ?: break
            if (checkTime < oldest - 86400000L) break
        }
        return streak
    }

    suspend fun getDaysActive(): Int {
        val allEvents = blockEventDao.blocksSince(0L)
        return allEvents.map { it.timestamp / 86400000L }.distinct().count()
    }

    suspend fun resetAllSettings() {
        clearAllEvents()
        settings.setOnboardingComplete()
        settings.setShieldActive(false)
        settings.setTrialMode(false)
        settings.setDeactivationDelay(0)
        settings.setProfileName("")
        settings.setPinHash("")
        settings.setStreakCount(0)
    }

    // ── Firebase Remote Monitoring ─────────────────────────────

    suspend fun syncToFirebase() {
        if (!settings.isRemoteMonitoringEnabled()) return
        try {
            val firebase = FirebaseManager(context)
            if (!firebase.initialize()) return
            firebase.syncDeviceInfo()
            firebase.syncAppLimits()
            firebase.syncBlockEvents()
            firebase.syncWeeklyReport()
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "syncToFirebase failed", e)
        }
    }

    suspend fun sendAlert(type: String, message: String) {
        if (!settings.isRemoteMonitoringEnabled()) return
        try {
            val firebase = FirebaseManager(context)
            if (!firebase.initialize()) return
            firebase.sendAlert(type, message)
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "sendAlert failed", e)
        }
    }

    suspend fun processRemoteCommands() {
        if (!settings.isRemoteMonitoringEnabled()) return
        try {
            val firebase = FirebaseManager(context)
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
            android.util.Log.e("AppRepository", "processRemoteCommands failed", e)
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
