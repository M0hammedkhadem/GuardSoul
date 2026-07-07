package com.agon.app.data.repository

import com.agon.app.data.local.dao.BlockEventDao
import com.agon.app.data.local.entity.BlockEventEntity
import com.agon.app.data.settings.AppSettings
import com.agon.app.data.settings.EncryptedPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AppRepository(
    val blockEventDao: BlockEventDao,
    private val settings: AppSettings,
    private val encryptedPrefs: EncryptedPrefs
) {
    fun getBlacklistKeywords(): Flow<List<String>> = settings.blockedKeywordsFlow.map { it.toList() }
    fun getWhitelistKeywords(): Flow<List<String>> = settings.whitelistAppsFlow.map { it.toList() }

    suspend fun addKeyword(keyword: String, isWhitelist: Boolean = false) {
        if (isWhitelist) {
            val current = settings.whitelistAppsFlow.first()
            settings.setWhitelistApps(current + keyword)
        } else {
            val current = settings.blockedKeywordsFlow.first()
            settings.setBlockedKeywords(current + keyword)
        }
    }

    suspend fun removeKeyword(keyword: String, isWhitelist: Boolean = false) {
        if (isWhitelist) {
            val current = settings.whitelistAppsFlow.first()
            settings.setWhitelistApps(current - keyword)
        } else {
            val current = settings.blockedKeywordsFlow.first()
            settings.setBlockedKeywords(current - keyword)
        }
    }

    suspend fun addKeywords(keywords: List<String>, isWhitelist: Boolean = false) {
        if (isWhitelist) {
            val current = settings.whitelistAppsFlow.first()
            settings.setWhitelistApps(current + keywords.toSet())
        } else {
            val current = settings.blockedKeywordsFlow.first()
            settings.setBlockedKeywords(current + keywords.toSet())
        }
    }
    fun getAppSettings(): AppSettings = settings

    fun totalBlocksFlow(): Flow<Int> = blockEventDao.totalBlocksFlow()
    fun blocksTodayFlow(): Flow<Int> {
        val todayStart = getTodayStart()
        return blockEventDao.blocksSinceFlow(todayStart)
    }

    suspend fun recordBlock(packageName: String, appLabel: String, blockType: String) {
        blockEventDao.insert(BlockEventEntity(packageName = packageName, appLabel = appLabel, blockType = blockType))
    }

    suspend fun resetAllSettings() {
        blockEventDao.deleteAll()
        encryptedPrefs.clear()
        settings.setShieldActive(false)
        settings.setTrialMode(false)
        settings.setDeactivationDelay(0)
        settings.setProfileName("")
        settings.setPinHash("")
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
