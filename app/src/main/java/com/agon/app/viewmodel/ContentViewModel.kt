package com.agon.app.viewmodel

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.AiScannerService
import com.agon.app.AppBlockerService
import com.agon.app.DnsVpnService
import com.agon.app.GuardianDeviceAdminReceiver
import com.agon.app.GuardianApp
import com.agon.app.nn.NsfwModelDownloader
import com.agon.app.services.DeviceOwnerService
import com.agon.app.utils.KnoxManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ContentViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = (application as GuardianApp).repository.getAppSettings()
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
    val aiThreshold: StateFlow<Float> = settings.aiSensitivityFlow
        .map { (it.coerceIn(50, 90) / 100f) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.7f)
    val aiOverlayMode: StateFlow<Boolean> = settings.aiOverlayModeFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val safeSearchMode: StateFlow<String> = settings.safeSearchModeFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "basic")
    val blockDoh: StateFlow<Boolean> = settings.blockDohFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val modelDownloader = NsfwModelDownloader(context)

    val modelDownloadProgress: StateFlow<Int> = modelDownloader.downloadProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val isModelDownloading: StateFlow<Boolean> = modelDownloader.isDownloading
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isKnoxDevice: Boolean = KnoxManager.isKnoxDevice()

    fun setPornBlocker(v: Boolean) = viewModelScope.launch {
        settings.setPornBlocker(v)
        if (v && settings.isShieldActive()) {
            DnsVpnService.start(getApplication())
        } else if (!v) {
            DnsVpnService.stop(getApplication())
        }
    }

    fun setNextDnsProfileId(v: String) = viewModelScope.launch {
        settings.setNextDnsProfileId(v)
        if (pornBlocker.value && settings.isShieldActive()) {
            DnsVpnService.stop(getApplication())
            DnsVpnService.start(getApplication())
        }
    }

    fun setAiScanner(v: Boolean) = viewModelScope.launch {
        settings.setAiScanner(v)
        val ctx = getApplication<GuardianApp>()
        if (!v) {
            val stopIntent = Intent(ctx, AiScannerService::class.java).apply {
                action = AiScannerService.ACTION_STOP
            }
            ctx.startService(stopIntent)
        }
    }

    fun setSafeSearchMode(mode: String) = viewModelScope.launch {
        settings.setSafeSearchMode(mode)
        if (pornBlocker.value && settings.isShieldActive()) {
            DnsVpnService.stop(getApplication())
            DnsVpnService.start(getApplication())
        }
    }

    fun setBlockDoh(v: Boolean) = viewModelScope.launch {
        settings.setBlockDoh(v)
        if (pornBlocker.value && settings.isShieldActive()) {
            DnsVpnService.stop(getApplication())
            DnsVpnService.start(getApplication())
        }
    }

    fun startAiScannerWithProjection(projectionIntent: Intent) = viewModelScope.launch {
        settings.setAiScanner(true)
        if (!settings.isShieldActive()) return@launch
        val ctx = getApplication<GuardianApp>()
        if (!modelDownloader.isModelDownloaded()) {
            modelDownloader.downloadModel()
        }
        val serviceIntent = Intent(ctx, AiScannerService::class.java).apply {
            putExtra(AiScannerService.EXTRA_PROJECTION_INTENT, projectionIntent)
        }
        ctx.startForegroundService(serviceIntent)
    }

    fun getMediaProjectionIntent(): Intent? {
        val ctx = getApplication<GuardianApp>()
        val mpm = ctx.getSystemService(Application.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mpm.createScreenCaptureIntent()
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

    fun setAiThreshold(v: Float) = viewModelScope.launch {
        settings.setAiSensitivity((v * 100).toInt().coerceIn(50, 90))
    }

    fun setAiOverlayMode(v: Boolean) = viewModelScope.launch {
        settings.setAiOverlayMode(v)
    }
}
