package com.agon.app.viewmodel

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.AppBlockerService
import com.agon.app.DnsVpnService
import com.agon.app.PornBlockerService
import com.agon.app.blocking.PornBlockerController
import com.agon.app.guardianApp
import com.agon.app.GuardianDeviceAdminReceiver
import com.agon.app.GuardianApp
import com.agon.app.services.DeviceOwnerService
import com.agon.app.utils.KnoxManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ContentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as GuardianApp).repository
    private val settings = repository.getAppSettings()
    private val context = application

    val pornBlocker: StateFlow<Boolean> = settings.pornBlockerFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val nextDnsProfileId: StateFlow<String> = settings.nextDnsProfileIdFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val aiScanner: StateFlow<Boolean> = settings.aiScannerFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val uninstallProtection: StateFlow<Boolean> = settings.uninstallProtectionFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val strongProtection: StateFlow<Boolean> = settings.strongProtectionFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val safeSearchMode: StateFlow<String> = settings.safeSearchModeFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "basic")
    /** BATCH-Q: which family DNS the user picked for the VPN. */
    val familyDnsProvider: StateFlow<String> = settings.familyDnsProviderFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "opendns")

    val isKnoxDevice: Boolean = KnoxManager.isKnoxDevice()

    val isDeviceOwner: StateFlow<Boolean> = flow { emit(DeviceOwnerService.isDeviceOwner(context)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setPornBlocker(v: Boolean) = viewModelScope.launch {
        settings.setPornBlocker(v)
        PornBlockerController.sync(getApplication())
    }

    fun setNextDnsProfileId(v: String) = viewModelScope.launch {
        settings.setNextDnsProfileId(v)
    }

    fun setAiScanner(v: Boolean) = viewModelScope.launch {
        // The actual classification runs inside
        // [com.agon.app.blocking.AiExplorerEngine], driven by the
        // accessibility service. We just flip the persisted flag;
        // the engine's settings subscription picks it up.
        settings.setAiScanner(v)
    }

    fun setSafeSearchMode(mode: String) = viewModelScope.launch {
        settings.setSafeSearchMode(mode)
    }

    /**
     * BATCH-Q: pick the family DNS provider for the VPN fallback.
     * The change is observed by [DnsVpnService] via
     * [AppSettings.familyDnsProviderFlow] which re-establishes the
     * VPN with the new resolver set.
     */
    fun setFamilyDnsProvider(provider: String) = viewModelScope.launch {
        settings.setFamilyDnsProvider(provider)
        // Re-apply the porn-blocker controller so the VPN is torn
        // down + re-established with the new family DNS at the
        // top of the addDnsServer list.
        PornBlockerController.sync(getApplication())
    }

    fun setUninstallProtection(v: Boolean) = viewModelScope.launch {
        settings.setUninstallProtection(v)
        if (v) {
            GuardianDeviceAdminReceiver.setProtectionEnabled(context, true)
        } else {
            val dpm = context.getSystemService(Application.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(component)) {
                GuardianDeviceAdminReceiver.setProtectionEnabled(context, false)
            }
        }
    }

    fun setStrongProtection(v: Boolean) = viewModelScope.launch {
        settings.setStrongProtection(v)
        if (v) {
            GuardianDeviceAdminReceiver.setProtectionEnabled(context, true)
        }
    }

    fun isDeviceAdminGranted(): Boolean {
        val dpm = context.getSystemService(Application.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(component)
    }

    fun getAdminComponent(): ComponentName {
        return ComponentName(context, GuardianDeviceAdminReceiver::class.java)
    }

    fun isDeviceOwner(): Boolean = DeviceOwnerService.isDeviceOwner(context)

    /**
     * Snapshot of every component the porn-blocker path depends on,
     * for the "Test filter" diagnostics dialog on the Content
     * screen. Each line is computed on demand so the dialog never
     * shows a stale reading.
     */
    suspend fun runDiagnostics(): PornBlockerDiagnostics {
        val keywordList = try {
            repository.getBlocklist("blacklist", "keywords")
        } catch (_: Exception) { emptyList() }
        val domainList = try {
            repository.getBlocklist("blacklist", "websites")
        } catch (_: Exception) { emptyList() }
        val a11yBound = try {
            com.agon.app.utils.AccessibilityUtils.isServiceEnabled(
                context, com.agon.app.services.GuardSoulAccessibilityService::class.java
            )
        } catch (_: Exception) { false }
        return PornBlockerDiagnostics(
            shieldActive = settings.isShieldActive(),
            pornBlockerActive = settings.isPornBlockerActive(),
            isDeviceOwner = DeviceOwnerService.isDeviceOwner(context),
            privateDnsConfigured = PornBlockerService.isDnsConfigured,
            vpnEstablished = DnsVpnService.isVpnTunEstablished,
            // BATCH-Q: report the active family DNS provider so
            // the diagnostics dialog can confirm which tier the
            // VPN is currently using.
            activeFamilyProvider = DnsVpnService.cachedActiveFamilyProvider,
            a11yServiceBound = a11yBound,
            keywordCount = keywordList.size,
            domainCount = domainList.size,
        )
    }
}

/**
 * Immutable snapshot of the filter stack's runtime state. Returned
 * by [ContentViewModel.runDiagnostics] and rendered by the
 * "Test filter" dialog on the Content screen.
 */
data class PornBlockerDiagnostics(
    val shieldActive: Boolean,
    val pornBlockerActive: Boolean,
    val isDeviceOwner: Boolean,
    val privateDnsConfigured: Boolean,
    val vpnEstablished: Boolean,
    /** BATCH-Q: which family DNS provider the VPN is bound to. */
    val activeFamilyProvider: DnsVpnService.FamilyDnsProvider,
    val a11yServiceBound: Boolean,
    val keywordCount: Int,
    val domainCount: Int,
)
