package com.agon.app.viewmodel

import android.app.Application
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.AiScannerService
import com.agon.app.DnsVpnService
import com.agon.app.GuardianApp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ContentViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = (application as GuardianApp).repository.getAppSettings()

    val pornBlocker: StateFlow<Boolean> = settings.pornBlockerFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val nextDnsProfileId: StateFlow<String> = settings.nextDnsProfileIdFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val aiScanner: StateFlow<Boolean> = settings.aiScannerFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val uninstallProtection: StateFlow<Boolean> = settings.uninstallProtectionFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setPornBlocker(v: Boolean) = viewModelScope.launch {
        settings.setPornBlocker(v)
        if (v) DnsVpnService.start(getApplication())
        else DnsVpnService.stop(getApplication())
    }

    fun setNextDnsProfileId(v: String) = viewModelScope.launch {
        settings.setNextDnsProfileId(v)
        if (pornBlocker.value) {
            DnsVpnService.stop(getApplication())
            DnsVpnService.start(getApplication())
        }
    }

    fun setAiScanner(v: Boolean) = viewModelScope.launch {
        settings.setAiScanner(v)
        val context = getApplication<GuardianApp>()
        if (!v) {
            val stopIntent = Intent(context, AiScannerService::class.java).apply {
                action = AiScannerService.ACTION_STOP
            }
            context.startService(stopIntent)
        }
    }

    fun startAiScannerWithProjection(projectionIntent: Intent) = viewModelScope.launch {
        settings.setAiScanner(true)
        val context = getApplication<GuardianApp>()
        val serviceIntent = Intent(context, AiScannerService::class.java).apply {
            putExtra(AiScannerService.EXTRA_PROJECTION_INTENT, projectionIntent)
        }
        context.startForegroundService(serviceIntent)
    }

    fun getMediaProjectionIntent(): Intent? {
        val context = getApplication<GuardianApp>()
        val mpm = context.getSystemService(Application.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mpm.createScreenCaptureIntent()
    }

    fun setUninstallProtection(v: Boolean) = viewModelScope.launch { settings.setUninstallProtection(v) }
}
