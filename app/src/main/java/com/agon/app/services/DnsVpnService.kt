package com.agon.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.IOException
import com.agon.app.GuardianApp
import com.agon.app.R
import com.agon.app.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class DnsVpnService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationId = 1001
    private val channelId = "vpn_service_channel"
    private var shieldCheckRunnable: Runnable? = null

    companion object {
        // FIX: Switched from Cloudflare Family (1.1.1.3) to CleanBrowsing Family
        // CleanBrowsing Family enforces SafeSearch on Google/YouTube and blocks adult content.
        private const val DNS_PRIMARY = "185.228.168.168"
        private const val DNS_SECONDARY = "185.228.169.168"
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_PREFIX_LENGTH = 32
        private const val VPN_ADDRESS_OWN_PACKAGE = "com.agon.app"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Timber.d("DnsVpnService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("DnsVpnService onStartCommand")
        if (!isRunning) {
            startVpn()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        serviceScope.cancel()
        Timber.d("DnsVpnService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startVpn() {
        serviceScope.launch {
            val app = applicationContext as GuardianApp
            val settings = app.repository.getAppSettings()

            // Both shield and safe search must be active
            val shieldActive = settings.isShieldActive()
            val safeSearchEnabled = settings.safeSearchEnabledFlow.first()
            
            if (!shieldActive || !safeSearchEnabled) {
                Timber.d("Shield inactive or safe search disabled, not starting VPN")
                updateDnsStatus("disabled")
                stopSelf()
                return@launch
            }

            try {
                establishVpn()
                isRunning = true
                startForeground(notificationId, buildNotification())
                updateDnsStatus("active")
                startShieldCheck()
                Timber.i("DnsVpnService started with CleanBrowsing Family DNS")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start VPN")
                updateDnsStatus("failed: ${e.message}")
                stopSelf()
            }
        }
    }

    private fun establishVpn() {
        val builder = Builder()
            .setSession("GuardSoul Safe Search")
            .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)
            .addDnsServer(DNS_PRIMARY)
            .addDnsServer(DNS_SECONDARY)
            // Route ALL traffic through the VPN interface so DNS is enforced
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)
            // Exclude our own app to prevent a routing loop
            .addDisallowedApplication(VPN_ADDRESS_OWN_PACKAGE)

        vpnInterface = builder.establish()

        if (vpnInterface == null) {
            throw IOException("VPN establishment returned null")
        }
    }

    private fun stopVpn() {
        isRunning = false
        stopShieldCheck()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing VPN interface")
        }
        vpnInterface = null
        stopForeground(true)
        updateDnsStatus("inactive")
        Timber.d("DnsVpnService stopped")
    }

    private fun startShieldCheck() {
        shieldCheckRunnable = object : Runnable {
            override fun run() {
                val thisRunnable = this
                serviceScope.launch {
                    val app = applicationContext as GuardianApp
                    val settings = app.repository.getAppSettings()
                    val shieldActive = settings.isShieldActive()
                    val safeSearchEnabled = settings.safeSearchEnabledFlow.first()
                    
                    if (!shieldActive || !safeSearchEnabled) {
                        Timber.d("Shield or safe search disabled, stopping VPN")
                        stopVpn()
                        stopSelf()
                        return@launch
                    }
                    
                    // Reschedule check every 5 seconds
                    mainHandler.postDelayed(thisRunnable, 5000)
                }
            }
        }
        mainHandler.post(shieldCheckRunnable!!)
    }

    private fun stopShieldCheck() {
        shieldCheckRunnable?.let { mainHandler.removeCallbacks(it) }
        shieldCheckRunnable = null
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, com.agon.app.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_blocker_title))
            .setContentText(getString(R.string.notification_blocker_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.channel_vpn_security_alert),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_vpn_security_alert_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateDnsStatus(status: String) {
        serviceScope.launch {
            try {
                val app = applicationContext as GuardianApp
                app.repository.getAppSettings().setSafeSearchDnsStatus(status)
            } catch (e: Exception) {
                Timber.w(e, "Failed to update DNS status")
            }
        }
    }

    fun restartIfNeeded() {
        serviceScope.launch {
            val app = applicationContext as GuardianApp
            val settings = app.repository.getAppSettings()
            val enabled = settings.safeSearchEnabledFlow.first()
            if (enabled && !isRunning) {
                Timber.d("Restarting VPN service")
                startVpn()
            } else if (!enabled && isRunning) {
                Timber.d("Safe search disabled, stopping VPN")
                stopVpn()
                stopSelf()
            }
        }
    }
}