package com.agon.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.agon.app.data.GuardianRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repository = GuardianRepository(context)
            CoroutineScope(Dispatchers.IO).launch {
                val state = repository.guardianStateFlow.first()
                if (state.isShieldActive) {
                    // Restart VPN and other services if needed
                }
            }
        }
    }
}
