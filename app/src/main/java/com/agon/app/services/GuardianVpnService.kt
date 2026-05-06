package com.agon.app.services

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class GuardianVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.3") // Cloudflare for Families (blocks adult content)
                .addDnsServer("8.8.8.8") // Fallback
                .setSession("GuardianVPN")
                .setBlocking(true)

            vpnInterface = builder.establish()
            Log.d("GuardianVpnService", "VPN Started")
        } catch (e: Exception) {
            Log.e("GuardianVpnService", "Failed to start VPN", e)
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            Log.d("GuardianVpnService", "VPN Stopped")
        } catch (e: Exception) {
            Log.e("GuardianVpnService", "Failed to stop VPN", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
