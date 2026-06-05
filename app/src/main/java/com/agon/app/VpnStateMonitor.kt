package com.agon.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Was a [android.content.BroadcastReceiver]; we now expose the same work
 * scheduler and connectivity helpers as a plain object. The receiver was
 * never registered in the manifest (dead code) and custom broadcasts are not
 * the right mechanism for the only operation we needed — re-scheduling
 * [VpnRevocationWorker] from [DnsVpnService.onRevoke].
 */
object VpnStateMonitor {
    private const val TAG = "VpnStateMonitor"
    private const val WORK_NAME = "vpn_revocation_alert"
    private const val WORK_TAG = "vpn_revocation"

    fun scheduleRevocationWork(context: Context, attempts: Int = 0) {
        val inputData = Data.Builder()
            .putInt("restart_attempts", attempts)
            .build()

        val work = OneTimeWorkRequestBuilder<VpnRevocationWorker>()
            .setInitialDelay(1, TimeUnit.SECONDS)
            .setInputData(inputData)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            work,
        )
        Timber.d(TAG, "Revocation work scheduled (attempts: $attempts)")
    }

    fun cancelRevocationWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = cm.allNetworks
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                val info = cm.getNetworkInfo(network)
                if (info?.extraInfo?.contains(context.packageName) == true) {
                    return true
                }
            }
        }
        return false
    }
}
