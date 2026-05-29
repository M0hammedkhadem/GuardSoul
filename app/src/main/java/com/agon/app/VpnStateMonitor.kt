package com.agon.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class VpnStateMonitor : BroadcastReceiver() {

    companion object {
        const val ACTION_VPN_REVOKED = "com.agon.app.action.VPN_REVOKED"
        const val ACTION_VPN_STOPPED = "com.agon.app.action.VPN_STOPPED"
        const val ACTION_VPN_RESTART = "com.agon.app.action.VPN_RESTART"
        private const val TAG = "VpnStateMonitor"

        fun scheduleRevocationWork(context: Context) {
            val work = OneTimeWorkRequestBuilder<VpnRevocationWorker>()
                .setInitialDelay(1, TimeUnit.SECONDS)
                .addTag("vpn_revocation")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "vpn_revocation_alert",
                androidx.work.ExistingWorkPolicy.REPLACE,
                work
            )
            Timber.d(TAG, "Revocation work scheduled")
        }

        fun cancelRevocationWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("vpn_revocation_alert")
        }

        fun registerNetworkCallback(context: ConnectivityManager, callback: NetworkCallback) {
            val request = NetworkRequest.Builder()
                .addCapability(4)
                .build()
            try {
                context.registerNetworkCallback(request, callback)
                Timber.d(TAG, "VPN network callback registered")
            } catch (e: Exception) {
                Timber.w(e, TAG, "Failed to register VPN network callback")
            }
        }

        fun isVpnActive(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networks = cm.allNetworks
            for (network in networks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    val pkg = cm.getNetworkInfo(network)?.extraInfo
                    if (pkg != null && pkg.contains(context.packageName)) {
                        return true
                    }
                }
            }
            return false
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d(TAG, "Received action: ${intent.action}")
        when (intent.action) {
            ACTION_VPN_REVOKED, ACTION_VPN_STOPPED -> {
                if (!DnsVpnService.wasStoppedIntentionally()) {
                    Timber.w(TAG, "VPN stopped unintentionally! Scheduling alert...")
                    scheduleRevocationWork(context)
                } else {
                    Timber.d(TAG, "VPN stopped intentionally, no alert needed")
                    DnsVpnService.clearIntentionalStopFlag()
                }
            }
            ACTION_VPN_RESTART -> {
                if (!DnsVpnService.wasStoppedIntentionally()) {
                    Timber.d(TAG, "Auto-restart requested")
                    DnsVpnService.start(context)
                }
            }
        }
    }

    abstract class NetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            super.onLost(network)
            Timber.d(TAG, "VPN network lost: $network")
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, caps)
            Timber.d(TAG, "VPN capabilities changed: ${caps.transportInfo}")
        }
    }
}
