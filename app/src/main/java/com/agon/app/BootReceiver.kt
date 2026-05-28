package com.agon.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        Timber.d("BootReceiver: device booted, checking shield state")

        val shouldStart = try {
            runBlocking {
                val app = context.applicationContext as GuardianApp
                app.repository.getAppSettings().isShieldActive()
            }
        } catch (e: Exception) {
            Timber.w(e, "BootReceiver: failed to read shield state, starting anyway")
            true
        }

        if (!shouldStart) {
            Timber.d("BootReceiver: shield was not active, skipping service restart")
            return
        }

        Timber.d("BootReceiver: shield was active, restarting services")

        startServiceSafe(context, Intent(context, AppBlockerService::class.java))

        val pornBlockerActive = try {
            runBlocking {
                val app = context.applicationContext as GuardianApp
                app.repository.getAppSettings().isPornBlockerActive()
            }
        } catch (e: Exception) {
            false
        }
        if (pornBlockerActive) {
            startServiceSafe(context, Intent(context, DnsVpnService::class.java))
        }
    }

    private fun startServiceSafe(context: Context, intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Timber.w(e, "BootReceiver: failed to start ${intent.component}")
        }
    }
}
