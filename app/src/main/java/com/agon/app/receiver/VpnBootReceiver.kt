package com.agon.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.agon.app.data.settings.AppSettings
import com.agon.app.services.PornBlockerVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VpnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val settings = AppSettings(context)
                    if (settings.pornBlockerEnabledFlow.first()) {
                        val serviceIntent = Intent(context, PornBlockerVpnService::class.java).apply {
                            action = PornBlockerVpnService.ACTION_START
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
