package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.blocking.PornBlockerController
import com.agon.app.guardianApp
import com.agon.app.data.settings.AppSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val shieldActive: Boolean = false,
    val pornBlockerActive: Boolean = false,
    val aiScannerActive: Boolean = false,
    val uninstallProtection: Boolean = false,
    val strongProtection: Boolean = false,
    val blockSafeMode: Boolean = false,
    val strictMode: Boolean = false,
    val profileName: String = "",
    val facebookMode: String = "off",
    val youtubeMode: String = "off",
    val instagramMode: String = "off",
    val remoteMonitoring: Boolean = false,
    val isLoading: Boolean = true
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.guardianApp()!!.repository
    private val settings = repo.getAppSettings()

    // Issue #198: Combine all flows into a single StateFlow to prevent excessive recompositions.
    //
    // The previous implementation used the variadic
    // `combine(*flows) { args: Array<Any?> -> ... }` overload to
    // combine 12 flows at once, then `args[i] as Boolean` /
    // `as String` per slot. The casts are not type-safe — if a
    // future change to AppSettings flips a flag's type
    // (e.g. Boolean -> Int for tri-state), the cast fails at
    // runtime with a ClassCastException the first time the
    // user opens the screen, not at compile time.
    //
    // The fix is to split the 12 flows into 3 typed groups of 4
    // (the largest combine overload that keeps a typed
    // signature), then combine the 3 group flows. Each combine
    // is now statically type-safe; a future refactor that
    // changes a flag's type fails at compile time in the
    // specific group where the flag lives.
    private data class GroupA(
        val shieldActive: Boolean,
        val pornBlocker: Boolean,
        val aiScanner: Boolean,
        val uninstallProtection: Boolean
    )
    private data class GroupB(
        val strongProtection: Boolean,
        val blockSafeMode: Boolean,
        val strictMode: Boolean,
        val profileName: String
    )
    private data class GroupC(
        val facebookMode: String,
        val youtubeMode: String,
        val instagramMode: String,
        val remoteMonitoring: Boolean
    )

    private val groupA: Flow<GroupA> = combine(
        settings.shieldActiveFlow,
        settings.pornBlockerFlow,
        settings.aiScannerFlow,
        settings.uninstallProtectionFlow
    ) { shield, porn, ai, uninstall -> GroupA(shield, porn, ai, uninstall) }

    private val groupB: Flow<GroupB> = combine(
        settings.strongProtectionFlow,
        settings.blockSafeModeFlow,
        settings.strictModeFlow,
        settings.profileNameFlow
    ) { strong, blockSafe, strict, name -> GroupB(strong, blockSafe, strict, name) }

    private val groupC: Flow<GroupC> = combine(
        settings.facebookModeFlow,
        settings.youtubeModeFlow,
        settings.instagramModeFlow,
        settings.remoteMonitoringEnabledFlow
    ) { fb, yt, ig, remote -> GroupC(fb, yt, ig, remote) }

    val uiState: StateFlow<SettingsUiState> = combine(groupA, groupB, groupC) { a, b, c ->
        SettingsUiState(
            shieldActive = a.shieldActive,
            pornBlockerActive = a.pornBlocker,
            aiScannerActive = a.aiScanner,
            uninstallProtection = a.uninstallProtection,
            strongProtection = b.strongProtection,
            blockSafeMode = b.blockSafeMode,
            strictMode = b.strictMode,
            profileName = b.profileName,
            facebookMode = c.facebookMode,
            youtubeMode = c.youtubeMode,
            instagramMode = c.instagramMode,
            remoteMonitoring = c.remoteMonitoring,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState() // Issue #200: Default values match initial expected state
    )

    fun setShieldActive(v: Boolean) = viewModelScope.launch {
        settings.setShieldActive(v)
        // STALE-SHIELD-CHECK: when the user turns the shield
        // ON, stamp the current day. StatisticsViewModel
        // .calculateCleanStreak will only credit clean days
        // from this point on, so a user who disabled the
        // shield for a week doesn't get a fake "7 clean days"
        // streak when they re-enable it.
        if (v) {
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            settings.setLastShieldEnabledDay(todayStart)
        }
    }
    fun setPornBlocker(v: Boolean) = viewModelScope.launch {
        settings.setPornBlocker(v)
        PornBlockerController.sync(getApplication())
    }
    fun setAiScanner(v: Boolean) = viewModelScope.launch {
        // AI Explorer runs inside
        // [com.agon.app.blocking.AiExplorerEngine] (accessibility
        // service). The toggle just flips the persisted flag;
        // the engine's settings subscription picks it up.
        settings.setAiScanner(v)
    }
    fun setUninstallProtection(v: Boolean) = viewModelScope.launch { settings.setUninstallProtection(v) }
    fun setStrongProtection(v: Boolean) = viewModelScope.launch { settings.setStrongProtection(v) }
    fun setBlockSafeMode(v: Boolean) = viewModelScope.launch { settings.setBlockSafeMode(v) }
    fun setStrictMode(v: Boolean) = viewModelScope.launch { settings.setStrictMode(v) }
    fun setRemoteMonitoring(v: Boolean) = viewModelScope.launch { settings.setRemoteMonitoringEnabled(v) }
    
    fun resetAllSettings() = viewModelScope.launch {
        repo.resetAllSettings()
    }

    fun resetStatistics() = viewModelScope.launch {
        repo.clearAllEvents()
    }
}
