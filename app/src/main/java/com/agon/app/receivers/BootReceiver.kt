package com.agon.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.agon.app.data.GuardianRepository
import com.agon.app.services.AIExplorerService
import com.agon.app.services.GuardianVpnService
import com.agon.app.utils.ScheduleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = GuardianRepository(context)
                val state = repository.guardianStateFlow.first()

                if (!state.isShieldActive) return@launch

                if (state.pornBlockerActive) {
                    val vpnIntent = Intent(context, GuardianVpnService::class.java)
                    context.startService(vpnIntent)
                }

                if (state.aiExplorerActive) {
                    val aiIntent = Intent(context, AIExplorerService::class.java).apply {
                        action = "BOOT_RESTART"
                    }
                    context.startService(aiIntent)
                }

                if (state.scheduleRules.isNotEmpty()) {
                    val scheduleManager = ScheduleManager(context)
                    scheduleManager.registerAll(state.scheduleRules)
                }
            } catch (_: Exception) {
                // BootReceiver should never crash — silently handle errors
            }
        }
    }
}
