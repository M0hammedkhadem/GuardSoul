package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.data.GuardianRepository
import com.agon.app.data.GuardianState
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

    fun toggleShield() {
        val currentState = state.value
        viewModelScope.launch {
            if (!currentState.isShieldActive) {
                repository.updateShieldActive(true)
                repository.updateShieldActivatedAt(System.currentTimeMillis())
                repository.updateCountdownEndTime(null)
            } else {
                if (currentState.countdownEndTime == null) {
                    val delay = if (currentState.isTrialModeActive) 0L else currentState.deactivationDelayMinutes * 60 * 1000
                    repository.updateCountdownEndTime(System.currentTimeMillis() + delay)
                }
            }
        }
    }

    fun cancelCountdown() { viewModelScope.launch { repository.updateCountdownEndTime(null) } }

    fun finalizeDeactivation() {
        viewModelScope.launch {
            repository.updateShieldActive(false)
            repository.updateShieldActivatedAt(null)
            repository.updateCountdownEndTime(null)
        }
    }

    fun toggleTrialMode() { viewModelScope.launch { repository.updateTrialMode(!state.value.isTrialModeActive) } }
    fun setDeactivationDelay(minutes: Long) { viewModelScope.launch { repository.updateDeactivationDelay(minutes) } }

    fun updatePermission(key: String, granted: Boolean) {
        viewModelScope.launch { repository.updatePermission(key, granted) }
    }

    fun toggleInstagram() { viewModelScope.launch { repository.updateInstagramBlocked(!state.value.instagramBlocked) } }
    fun toggleSnapchat() { viewModelScope.launch { repository.updateSnapchatBlocked(!state.value.snapchatBlocked) } }
    fun toggleTwitter() { viewModelScope.launch { repository.updateTwitterBlocked(!state.value.twitterBlocked) } }
    fun toggleTiktok() { viewModelScope.launch { repository.updateTiktokBlocked(!state.value.tiktokBlocked) } }
    fun setYoutubeMode(mode: String) { viewModelScope.launch { repository.updateYoutubeMode(mode) } }
    fun setFacebookMode(mode: String) { viewModelScope.launch { repository.updateFacebookMode(mode) } }
    fun togglePornBlocker() { viewModelScope.launch { repository.updatePornBlocker(!state.value.pornBlockerActive) } }
    fun toggleAiExplorer() { viewModelScope.launch { repository.updateAiExplorer(!state.value.aiExplorerActive) } }
    fun toggleUninstallProtection() { viewModelScope.launch { repository.updateUninstallProtection(!state.value.uninstallProtectionActive) } }

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

    fun resetStatistics() {
        viewModelScope.launch {
            repository.updateBlocksCount(0)
            repository.updateShieldActivatedAt(null)
        }
    }

    fun resetAllSettings() {
        viewModelScope.launch {
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

            repository.updateList("blacklist", "keywords", listOf("porn", "xxx", "adult", "sex", "nude", "nsfw", "hentai", "erotic"))
            repository.updateList("blacklist", "websites", listOf("pornhub.com", "xvideos.com", "xnxx.com", "redtube.com", "youporn.com", "onlyfans.com"))
            repository.updateList("blacklist", "apps", emptyList())
            
            repository.updateList("whitelist", "keywords", emptyList())
            repository.updateList("whitelist", "websites", emptyList())
            repository.updateList("whitelist", "apps", emptyList())
        }
    }
}
