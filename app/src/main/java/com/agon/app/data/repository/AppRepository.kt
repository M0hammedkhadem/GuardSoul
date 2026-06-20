package com.agon.app.data.repository

import com.agon.app.data.local.dao.BlockEventDao
import com.agon.app.data.local.dao.KeywordDao
import com.agon.app.data.local.dao.MostBlockedApp
import com.agon.app.data.local.entity.BlockEventEntity
import com.agon.app.data.local.entity.KeywordEntity
import com.agon.app.data.settings.AppSettings
import com.agon.app.data.settings.EncryptedPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AppRepository(
    val blockEventDao: BlockEventDao,
    val keywordDao: KeywordDao,
    private val settings: AppSettings,
    private val encryptedPrefs: EncryptedPrefs
) {
    fun getAppSettings(): AppSettings = settings

    fun getAllBlockEvents(): Flow<List<BlockEventEntity>> = blockEventDao.getAllFlow()
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
    suspend fun getTotalEventCount(): Int = blockEventDao.getCount()
    suspend fun clearOldEvents(days: Int) {
        val threshold = System.currentTimeMillis() - days * 86_400_000L
        blockEventDao.clearOld(threshold)
    }

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
        blockEventDao.deleteAll()
        keywordDao.clearAll()
        encryptedPrefs.clear()
        settings.setShieldActive(false)
        settings.setTrialMode(false)
        settings.setDeactivationDelay(0)
        settings.setProfileName("")
        settings.setPinHash("")
    }

    // ─── Keyword Management ────────────────────────────────────────────────

    fun getBlacklistKeywords(): Flow<List<String>> = keywordDao.getBlacklistKeywords()

    fun getWhitelistKeywords(): Flow<List<String>> = keywordDao.getWhitelistKeywords()

    suspend fun addKeyword(keyword: String, isWhitelist: Boolean = false) {
        keywordDao.insert(KeywordEntity(keyword = keyword, isWhitelist = isWhitelist))
    }

    suspend fun addKeywords(keywords: List<String>, isWhitelist: Boolean = false) {
        keywordDao.insertAll(keywords.map { KeywordEntity(keyword = it, isWhitelist = isWhitelist) })
    }

    suspend fun removeKeyword(keyword: String, isWhitelist: Boolean = false) {
        keywordDao.deleteByKeyword(keyword, isWhitelist)
    }

    suspend fun clearKeywords(isWhitelist: Boolean = false) {
        withContext(Dispatchers.IO) {
            val keywords = if (isWhitelist) {
                keywordDao.getWhitelistFlow().first()
            } else {
                keywordDao.getBlacklistFlow().first()
            }
            keywords.forEach { keywordDao.delete(it) }
        }
    }

    suspend fun getBlacklistCount(): Int = keywordDao.getBlacklistCount()

    suspend fun getWhitelistCount(): Int = keywordDao.getWhitelistCount()

    private fun getTodayStart(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
