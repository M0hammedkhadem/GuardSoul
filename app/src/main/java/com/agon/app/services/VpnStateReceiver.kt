package com.agon.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import com.agon.app.GuardianApp
import com.agon.app.data.repository.AppRepository
import com.agon.app.utils.SafeModeDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class VpnStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Timber.d("VpnStateReceiver: $action")

        when (action) {
            ConnectivityManager.CONNECTIVITY_ACTION -> {
                checkAndRestartVpn(context)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                SafeModeDetector.checkAtBoot(context)
                checkAndRestartVpn(context)
            }
        }
    }

    private fun checkAndRestartVpn(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as GuardianApp
                val settings = app.repository.getAppSettings()
                val enabled = settings.safeSearchEnabledFlow.first()
                if (enabled) {
                    Timber.d("Connectivity/boot changed, ensuring VPN is running")
                    context.startForegroundService(Intent(context, DnsVpnService::class.java))
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to check/restart VPN")
            }
        }
    }
}