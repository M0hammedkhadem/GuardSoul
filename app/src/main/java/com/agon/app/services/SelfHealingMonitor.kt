package com.agon.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.agon.app.GuardianApp
import com.agon.app.R
import com.agon.app.utils.ServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * SelfHealingMonitor - نظام فحص صحي ذاتي الشفاء
 *
 * يفحص كل 30 ثانية:
 * 1. AccessibilityService - هل تعمل؟
 * 2. DeviceAdmin - هل مفعّل؟
 * 3. VPN Service - هل تعمل؟ (إذا كان Safe Search مفعّل)
 * 4. DNS Enforcement - هل DNS صحيح؟
 * 5. Temp Ban cache - تنظيف المنتهية
 *
 * عند اكتشاف خلل:
 * - إعادة تشغيل الخدمة المعطلة
 * - تسجيل التقرير
 * - إشعار المستخدم (إذا كان أمراً حرجاً)
 */
class SelfHealingMonitor : Service() {

    companion object {
        private const val CHECK_INTERVAL_MS = 30_000L // 30 seconds
        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "self_healing_channel"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SelfHealingMonitor::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SelfHealingMonitor::class.java))
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Timber.d("SelfHealingMonitor created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startForeground(NOTIFICATION_ID, buildNotification())
            startPeriodicChecks()
            Timber.i("SelfHealingMonitor started")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopPeriodicChecks()
        serviceScope.cancel()
        Timber.d("SelfHealingMonitor destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPeriodicChecks() {
        checkRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                performHealthCheck()
                mainHandler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
        mainHandler.post(checkRunnable!!)
    }

    private fun stopPeriodicChecks() {
        checkRunnable?.let { mainHandler.removeCallbacks(it) }
        checkRunnable = null
    }

    private fun performHealthCheck() {
        serviceScope.launch {
            try {
                val app = applicationContext as GuardianApp
                val settings = app.repository.getAppSettings()
                val shieldActive = settings.isShieldActive()
                if (!shieldActive) {
                    // Shield is off — nothing to heal, just cleanup
                    cleanup()
                    return@launch
                }

                // Check 1: AccessibilityService
                checkAccessibilityService()

                // Check 2: DeviceAdmin
                checkDeviceAdmin()

                // Check 3: VPN Service (if SafeSearch enabled)
                checkVpnService(settings)

                // Check 4: DNS enforcement
                checkDnsEnforcement(settings)

                // Check 5: Clean up expired temp bans
                cleanupTempBans()

                Timber.d("SelfHealingMonitor: health check completed")
            } catch (e: Exception) {
                Timber.w(e, "SelfHealingMonitor: health check failed")
            }
        }
    }

    private fun checkAccessibilityService() {
        val enabled = ServiceManager.isShieldActive(this)
        if (!enabled) {
            Timber.w("SelfHealingMonitor: AccessibilityService disabled! Attempting restart...")
            // Try to re-enable
            ServiceManager.setShieldActive(this, true)
            Timber.i("SelfHealingMonitor: AccessibilityService restart requested")
        }
    }

    private fun checkDeviceAdmin() {
        val active = ServiceManager.isDeviceAdminActive(this)
        if (!active) {
            Timber.w("SelfHealingMonitor: DeviceAdmin disabled!")
            // Cannot re-enable automatically — show persistent notification
        }
    }

    private suspend fun checkVpnService(settings: com.agon.app.data.settings.AppSettings) {
        val safeSearchEnabled = settings.safeSearchEnabledFlow.first()
        if (!safeSearchEnabled) return

        // Check if VPN permission is granted and VPN is prepared
        val vpnPrepared = ServiceManager.isVpnPrepared(this)
        if (!vpnPrepared) {
            Timber.w("SelfHealingMonitor: VPN not prepared! Restarting...")
            // Restart VPN
            try {
                startForegroundService(Intent(this, DnsVpnService::class.java))
            } catch (e: Exception) {
                Timber.e(e, "SelfHealingMonitor: VPN restart failed")
            }
        }
    }

    private fun checkDnsEnforcement(settings: com.agon.app.data.settings.AppSettings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val currentMode = Settings.Global.getString(contentResolver, "private_dns_mode")
                val currentSpecifier = Settings.Global.getString(contentResolver, "private_dns_specifier")
                // Expected: mode="hostname", specifier="family-filter-dns.cleanbrowsing.org"
                if (currentMode != "hostname" || currentSpecifier != "family-filter-dns.cleanbrowsing.org") {
                    Timber.w("SelfHealingMonitor: DNS not enforced correctly (mode=$currentMode, spec=$currentSpecifier)")
                    // Note: Cannot change Settings.Global without WRITE_SECURE_SETTINGS permission
                }
            } catch (e: Exception) {
                Timber.w(e, "SelfHealingMonitor: DNS check failed")
            }
        }
    }

    private fun cleanupTempBans() {
        try {
            com.agon.app.blocking.TempBanManager.getInstance(this).cleanupExpired()
        } catch (e: Exception) {
            Timber.w(e, "SelfHealingMonitor: TempBan cleanup failed")
        }
    }

    private fun cleanup() {
        cleanupTempBans()
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, com.agon.app.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_blocker_title))
            .setContentText("Self-healing monitor active")
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
                CHANNEL_ID,
                "Self-Healing Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors and heals GuardSoul services"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
