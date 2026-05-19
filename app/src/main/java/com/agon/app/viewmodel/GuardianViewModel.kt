package com.agon.app.viewmodel

import android.app.ActivityManager
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.data.*
import com.agon.app.receivers.GuardianDeviceAdminReceiver
import com.agon.app.services.AIExplorerService
import com.agon.app.services.GuardianVpnService
import com.agon.app.utils.ScheduleManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GuardianViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GuardianRepository(application)

    val state: StateFlow<GuardianState> = repository.guardianStateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GuardianState()
    )

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (service.service.className == serviceClass.name) return true
        }
        return false
    }

    private suspend fun stopServiceAndConfirm(intent: Intent, serviceClass: Class<*>, maxWaitMs: Long = 3000) {
        getApplication<Application>().stopService(intent)
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            if (!isServiceRunning(serviceClass)) return
            delay(200)
        }
    }

    fun finalizeDeactivation() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            stopServiceAndConfirm(Intent(context, AIExplorerService::class.java), AIExplorerService::class.java)
            stopServiceAndConfirm(Intent(context, GuardianVpnService::class.java), GuardianVpnService::class.java)
            repository.updateShieldActive(false)
            repository.updateShieldActivatedAt(null)
            repository.updateCountdownEndTime(null)
        }
    }

    fun toggleShield() {
        val currentState = state.value
        viewModelScope.launch {
            if (!currentState.isShieldActive) {
                repository.updateShieldActive(true)
                repository.updateShieldActivatedAt(System.currentTimeMillis())
                repository.updateCountdownEndTime(null)
                // Register schedules on shield activation
                val scheduleManager = ScheduleManager(getApplication())
                if (currentState.scheduleRules.isNotEmpty()) {
                    scheduleManager.registerAll(currentState.scheduleRules)
                }
            } else {
                if (currentState.countdownEndTime == null) {
                    val delay = if (currentState.isTrialModeActive) 0L else currentState.deactivationDelayMinutes * 60 * 1000
                    repository.updateCountdownEndTime(System.currentTimeMillis() + delay)
                }
                // Unregister schedules on shield deactivation
                val scheduleManager = ScheduleManager(getApplication())
                scheduleManager.unregisterAll()
            }
        }
    }

    fun cancelCountdown() { viewModelScope.launch { repository.updateCountdownEndTime(null) } }

    fun toggleTrialMode() { viewModelScope.launch { repository.updateTrialMode(!state.value.isTrialModeActive) } }
    fun setDeactivationDelay(minutes: Long) { viewModelScope.launch { repository.updateDeactivationDelay(minutes) } }

    fun updatePermission(key: String, granted: Boolean) {
        viewModelScope.launch { repository.updatePermission(key, granted) }
    }

    // Sync DataStore with real device admin state (safe to call at any time)
    fun syncDeviceAdminStatus() {
        val context = getApplication<Application>()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        val reallyActive = dpm.isAdminActive(componentName)
        viewModelScope.launch {
            if (reallyActive != state.value.deviceAdminGranted) {
                repository.updatePermission("device_admin", reallyActive)
            }
            if (reallyActive != state.value.uninstallProtectionActive) {
                repository.updateUninstallProtection(reallyActive)
            }
        }
    }

    fun toggleInstagram() { viewModelScope.launch { repository.updateInstagramBlocked(!state.value.instagramBlocked) } }
    fun toggleSnapchat() { viewModelScope.launch { repository.updateSnapchatBlocked(!state.value.snapchatBlocked) } }
    fun toggleTwitter() { viewModelScope.launch { repository.updateTwitterBlocked(!state.value.twitterBlocked) } }
    fun toggleTiktok() { viewModelScope.launch { repository.updateTiktokBlocked(!state.value.tiktokBlocked) } }
    fun setYoutubeMode(mode: String) { viewModelScope.launch { repository.updateYoutubeMode(mode) } }
    fun setFacebookMode(mode: String) { viewModelScope.launch { repository.updateFacebookMode(mode) } }
    fun togglePornBlocker() { viewModelScope.launch { repository.updatePornBlocker(!state.value.pornBlockerActive) } }
    fun toggleAiExplorer() { viewModelScope.launch { repository.updateAiExplorer(!state.value.aiExplorerActive) } }
    fun toggleUninstallProtection() {
        val context = getApplication<Application>()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(context, GuardianDeviceAdminReceiver::class.java)

        viewModelScope.launch {
            if (!state.value.uninstallProtectionActive) {
                if (!dpm.isAdminActive(componentName)) {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Guardian needs Device Admin to prevent uninstallation and keep your digital wellness protections active.")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } else {
                try {
                    if (dpm.isAdminActive(componentName)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            dpm.removeActiveAdmin(componentName)
                        } else {
                            val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                } catch (_: SecurityException) {
                    val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                repository.updateUninstallProtection(false)
                repository.updatePermission("device_admin", false)
            }
        }
    }

    // F4: PIN
    fun setPinCode(pin: String) { viewModelScope.launch { repository.updatePinCode(pin) } }
    fun clearPinCode() { viewModelScope.launch { repository.updatePinCode(null) } }
    fun unlockApp() { viewModelScope.launch { repository.updateAppUnlocked(true) } }
    fun lockApp() { viewModelScope.launch { repository.updateAppUnlocked(false) } }
    fun verifyPin(input: String, onResult: (Boolean) -> Unit) {
        val hashed = input.let {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.digest(it.toByteArray()).joinToString("") { "%02x".format(it) }
        }
        val stored = state.value.pinCode
        onResult(stored != null && stored == hashed)
    }

    // F1: Onboarding + Profile
    fun completeOnboarding(name: String) {
        viewModelScope.launch {
            repository.updateProfileName(name.ifBlank { "User" })
            repository.updateOnboardingCompleted(true)
            if (repository.guardianStateFlow.stateIn(viewModelScope).value.installTimestamp == null) {
                repository.updateInstallTimestamp(System.currentTimeMillis())
            }
        }
    }
    fun updateProfileName(name: String) { viewModelScope.launch { repository.updateProfileName(name) } }

    // F2: Schedule
    fun addScheduleRule(rule: ScheduleRule) {
        viewModelScope.launch {
            val current = state.value.scheduleRules
            repository.updateScheduleRules(current + rule)
            val scheduleManager = ScheduleManager(getApplication())
            scheduleManager.registerAll(current + rule)
        }
    }
    fun updateScheduleRule(rule: ScheduleRule) {
        viewModelScope.launch {
            val updated = state.value.scheduleRules.map { if (it.id == rule.id) rule else it }
            repository.updateScheduleRules(updated)
            val scheduleManager = ScheduleManager(getApplication())
            scheduleManager.unregisterAll()
            scheduleManager.registerAll(updated)
        }
    }
    fun deleteScheduleRule(id: String) {
        viewModelScope.launch {
            val updated = state.value.scheduleRules.filter { it.id != id }
            repository.updateScheduleRules(updated)
            val scheduleManager = ScheduleManager(getApplication())
            scheduleManager.unregisterAll()
            scheduleManager.registerAll(updated)
        }
    }

    // F3: Time limits
    fun addTimeLimit(limit: DailyTimeLimit) {
        viewModelScope.launch {
            val current = state.value.dailyTimeLimits.filter { it.packageName != limit.packageName }
            repository.updateDailyTimeLimits(current + limit)
        }
    }
    fun removeTimeLimit(packageName: String) {
        viewModelScope.launch {
            val updated = state.value.dailyTimeLimits.filter { it.packageName != packageName }
            repository.updateDailyTimeLimits(updated)
        }
    }

    // F5: Block events
    fun logBlockEvent(packageName: String, blockType: String) {
        viewModelScope.launch {
            repository.addBlockEvent(BlockEvent(packageName = packageName, blockType = blockType))
        }
    }
    fun resetStatistics() {
        viewModelScope.launch {
            repository.updateBlocksCount(0)
            repository.updateShieldActivatedAt(null)
            repository.clearBlockEvents()
        }
    }

    // F6: Trial expiry check
    fun isTrialExpired(): Boolean = state.value.isTrialExpired

    // F7: Import
    fun importBlocklist(websites: List<String>, keywords: List<String>, apps: List<String>) {
        viewModelScope.launch {
            val s = state.value
            val mergedWebsites = (s.blacklistWebsites + websites).distinct()
            val mergedKeywords = (s.blacklistKeywords + keywords).distinct()
            val mergedApps = (s.blacklistApps + apps).distinct()
            repository.updateList("blacklist", "websites", mergedWebsites)
            repository.updateList("blacklist", "keywords", mergedKeywords)
            repository.updateList("blacklist", "apps", mergedApps)
        }
    }

    fun addToList(listType: String, category: String, item: String) {
        val currentList = getList(listType, category).toMutableList()
        if (!currentList.contains(item) && item.isNotBlank()) {
            currentList.add(item.trim())
            viewModelScope.launch { repository.updateList(listType, category, currentList) }
        }
    }

    fun removeFromList(listType: String, category: String, item: String) {
        val currentList = getList(listType, category).toMutableList()
        if (currentList.remove(item)) {
            viewModelScope.launch { repository.updateList(listType, category, currentList) }
        }
    }

    private fun getList(listType: String, category: String): List<String> {
        val s = state.value
        return when ("${listType}_$category") {
            "blacklist_keywords" -> s.blacklistKeywords
            "blacklist_websites" -> s.blacklistWebsites
            "blacklist_apps" -> s.blacklistApps
            "whitelist_keywords" -> s.whitelistKeywords
            "whitelist_websites" -> s.whitelistWebsites
            "whitelist_apps" -> s.whitelistApps
            else -> emptyList()
        }
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            stopServiceAndConfirm(Intent(context, AIExplorerService::class.java), AIExplorerService::class.java)
            stopServiceAndConfirm(Intent(context, GuardianVpnService::class.java), GuardianVpnService::class.java)
            repository.updateShieldActive(false)
            repository.updateTrialMode(false)
            repository.updateDeactivationDelay(7 * 24 * 60L)
            repository.updateCountdownEndTime(null)
            repository.updateBlocksCount(0)
            repository.updateShieldActivatedAt(null)
            repository.updateInstagramBlocked(false)
            repository.updateSnapchatBlocked(false)
            repository.updateTwitterBlocked(false)
            repository.updateTiktokBlocked(false)
            repository.updateYoutubeMode("off")
            repository.updateFacebookMode("off")
            repository.updatePornBlocker(false)
            repository.updateAiExplorer(false)
            repository.updateUninstallProtection(false)
            repository.updateList("blacklist", "keywords", defaultBlacklistKeywords)
            repository.updateList("blacklist", "websites", defaultBlacklistWebsites)
            repository.updateList("blacklist", "apps", emptyList())
            repository.updateList("whitelist", "keywords", emptyList())
            repository.updateList("whitelist", "websites", emptyList())
            repository.updateList("whitelist", "apps", emptyList())
            repository.clearBlockEvents()
            repository.updateScheduleRules(emptyList())
            repository.updateDailyTimeLimits(emptyList())
        }
    }
}