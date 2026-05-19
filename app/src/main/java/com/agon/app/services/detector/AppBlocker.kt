package com.agon.app.services.detector

import android.content.Intent
import com.agon.app.data.GuardianRepository
import com.agon.app.data.GuardianState
import com.agon.app.services.AIExplorerService
import com.agon.app.services.GuardianAccessibilityService
import com.agon.app.ui.screens.BlockActivity
import com.agon.app.utils.TimeLimitManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class AppBlocker(
    private val service: GuardianAccessibilityService,
    private val repository: GuardianRepository,
    private val getState: () -> GuardianState
) {
    private val scope get() = service.scope
    private val currentState get() = getState()
    private val TAG = "GuardianService"

    private val fullBlockLastTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val FULL_BLOCK_COOLDOWN = 300L

    fun getFullBlockReason(packageName: String): String? {
        if (currentState.whitelistApps.contains(packageName)) return null
        if (currentState.blacklistApps.contains(packageName)) return "blacklist"

        if (currentState.dailyTimeLimits.any { it.packageName == packageName }) {
            val timeLimitManager = TimeLimitManager(service.applicationContext)
            if (timeLimitManager.hasExceededLimit(packageName, currentState.dailyTimeLimits)) {
                return "time_limit"
            }
        }

        return when {
            packageName in currentState.blockedPackageNames -> "social"
            AIExplorerService.isAppBanned(packageName) -> "ai_scan"
            else -> null
        }
    }

    fun executeFullBlock(packageName: String, blockReason: String?) {
        val now = System.currentTimeMillis()
        val last = fullBlockLastTimes[packageName] ?: 0L
        if (now - last < FULL_BLOCK_COOLDOWN) return
        fullBlockLastTimes[packageName] = now

        val intent = Intent(service, BlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("APP_NAME", getAppNameFromPackage(packageName))
            if (blockReason != null) {
                putExtra("BLOCK_REASON", blockReason)
            }
        }
        service.startActivity(intent)

        scope.launch {
            repository.updateBlocksCount(currentState.blocksCount + 1)
        }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val pm = service.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
                .replaceFirstChar { it.uppercase() }
                .ifEmpty { "App" }
        }
    }
}
