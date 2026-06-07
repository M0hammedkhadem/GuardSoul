package com.agon.app

import android.app.Notification
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import com.agon.app.blocking.BlockingConfig
import com.agon.app.blocking.PornBlockerController
import com.agon.app.data.repository.AppRepository
import com.agon.app.services.DeviceOwnerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import org.koin.android.ext.android.inject
import timber.log.Timber

class PornBlockerService : android.app.Service() {

    companion object {
        private const val NOTIFICATION_ID = 4001

        @Volatile
        private var intentionalStop = false

        /**
         * True when the system Private DNS has been successfully
         * set to CleanBrowsing Family by [configurePrivateDns].
         * Exposed to the home-screen status badge so the user can
         * see at a glance whether the filter is actually applied
         * (not just the toggle being on).
         */
        @Volatile
        var isDnsConfigured: Boolean = false
            private set

        fun start(context: Context) {
            intentionalStop = false
            ForegroundServiceHelper.startServiceAsForeground(context, PornBlockerService::class.java)
        }

        fun stop(context: Context) {
            intentionalStop = true
            context.stopService(Intent(context, PornBlockerService::class.java))
        }

        fun wasStoppedIntentionally(): Boolean = intentionalStop
    }

    private val repo: AppRepository by inject()
    // PB-003: was Dispatchers.Main. `dpm.setGlobalSetting` is a
    // binder call into system_server and can block on the main
    // thread; on slow devices this can cause UI jank or ANRs. The
    // service has no UI surface, so Dispatchers.IO is the right
    // dispatcher for all the binder-bound work in this class.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // @Volatile: written by [onStartCommand] on the main thread but
    // read by the ContentObserver callback (registered on
    // `Settings.Global`) which fires on the binder thread. Without
    // @Volatile, the observer can see a stale `null` even after
    // the service has captured the original DNS values, and would
    // then re-overwrite the value on cleanup.
    @Volatile private var originalMode: String? = null
    @Volatile private var originalSpecifier: String? = null
    private var observationJob: Job? = null
    private var dnsObserver: ContentObserver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startObservation()
        registerDnsObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ForegroundServiceHelper.startForegroundCompat(
            this, NOTIFICATION_ID, createNotification(),
            // FG-001: manifest declares specialUse.
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        return START_STICKY
    }

    private fun startObservation() {
        observationJob?.cancel()
        observationJob = serviceScope.launch {
            val settings = repo.getAppSettings()
            combine(settings.shieldActiveFlow, settings.pornBlockerFlow) { shield, porn ->
                shield && porn
            }.collect { shouldBeActive ->
                if (shouldBeActive) {
                    configurePrivateDns()
                } else {
                    revertPrivateDns()
                    // If the feature or shield is disabled, we should stop the service
                    if (!intentionalStop) {
                        stopSelf()
                    }
                }
            }
        }
    }

    /**
     * FEATURES_SPEC §5: Watch the system Private DNS settings and re-apply
     * CleanBrowsing Family the moment the user (or another app) flips them.
     * Without this an attacker can simply toggle Private DNS off in Settings
     * and bypass the filter between the periodic configure() calls.
     */
    private fun registerDnsObserver() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val handler = Handler(Looper.getMainLooper())
        // PB-002: collapse bursts of onChange callbacks into a single
        // re-application. The settings flow itself writes the URI,
        // which fires a synchronous onChange; if we react
        // immediately we re-apply, which fires another onChange, and
        // a rapid UI toggle (e.g. a parent toggling shield off and
        // back on in Settings) used to spawn 8-10 coroutines
        // before the burst settled. The flag is `compareAndSet`-d
        // so concurrent emissions coalesce cleanly.
        var inFlight = java.util.concurrent.atomic.AtomicBoolean(false)
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (!inFlight.compareAndSet(false, true)) return
                serviceScope.launch {
                    try {
                        kotlinx.coroutines.delay(200)
                        val settings = repo.getAppSettings()
                        if (settings.isShieldActive() && settings.isPornBlockerActive()) {
                            val cr = contentResolver
                            val mode = Settings.Global.getString(cr, "private_dns_mode")
                            val specifier = Settings.Global.getString(cr, "private_dns_specifier")
                            val looksOk = mode == "hostname" &&
                                specifier == BlockingConfig.CLEANBROWSING_FAMILY_HOST
                            if (!looksOk) {
                                Timber.w("DNS: tamper detected (mode=$mode, specifier=$specifier), re-applying filter")
                                try {
                                    repo.recordTamperAlert(
                                        "dns_changed",
                                        "Private DNS was changed away from CleanBrowsing Family. " +
                                            "Current mode=$mode, specifier=$specifier."
                                    )
                                } catch (_: Exception) {}
                                configurePrivateDns()
                            }
                        }
                    } finally {
                        inFlight.set(false)
                    }
                }
            }
        }
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor("private_dns_mode"), false, observer
        )
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor("private_dns_specifier"), false, observer
        )
        dnsObserver = observer
    }

    override fun onDestroy() {
        // PB-001: revertPrivateDns() does binder IPC into
        // system_server. On a fast system kill the service can be
        // torn down before the call returns, leaving the user's
        // Private DNS stuck on CleanBrowsing. Dispatch to IO and
        // join with a tight timeout — if it doesn't return, the OS
        // is killing us anyway and the DNS revert is best-effort.
        observationJob?.cancel()
        dnsObserver?.let { contentResolver.unregisterContentObserver(it) }
        dnsObserver = null
        // PB-001: only revert when the user (or a settings flow)
        // stopped us intentionally. A system kill should NOT
        // trigger the revert — otherwise a boot loop or a memory
        // pressure kill would silently toggle the user's Private
        // DNS setting on each cycle.
        val wasIntentional = wasStoppedIntentionally()
        val revertJob = serviceScope.launch {
            if (wasIntentional) {
                try { revertPrivateDns() } catch (e: Exception) {
                    Timber.w(e, "PornBlockerService: revert failed")
                }
            }
        }
        try {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(500) { revertJob.join() }
            }
        } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun configurePrivateDns() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

        try {
            val cr = contentResolver
            val currentMode = Settings.Global.getString(cr, "private_dns_mode")
            val currentSpecifier = Settings.Global.getString(cr, "private_dns_specifier")

            if (currentMode == "hostname" && currentSpecifier == BlockingConfig.CLEANBROWSING_FAMILY_HOST) {
                Timber.d("DNS: Already configured correctly")
                isDnsConfigured = true
                return
            }

            // Store original values before changing, if not already stored
            if (originalMode == null) {
                originalMode = currentMode ?: "off"
                originalSpecifier = currentSpecifier
            }

            if (DeviceOwnerService.isDeviceOwner(this)) {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val component = ComponentName(this, GuardianDeviceAdminReceiver::class.java)
                dpm.setGlobalSetting(component, "private_dns_mode", "hostname")
                dpm.setGlobalSetting(component, "private_dns_specifier", BlockingConfig.CLEANBROWSING_FAMILY_HOST)
                Timber.d("DNS: Configured via Device Owner")
                isDnsConfigured = true
            } else {
                // Issue #143: automatic Private DNS manipulation requires
                // Device Owner. Without it we cannot enforce the filter,
                // so we hand off to PornBlockerController which will
                // start the VPN fallback (DnsVpnService). The VPN
                // works without any privileged setup — only the user
                // VPN-permission consent — and provides equivalent
                // adult-domain coverage.
                Timber.w("DNS: not Device Owner — handing off to VPN fallback via PornBlockerController")
                PornBlockerController.sync(this)
            }
        } catch (e: Exception) {
            Timber.e(e, "DNS: Configuration failed")
        }
    }

    private fun revertPrivateDns() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || originalMode == null) return

        try {
            if (DeviceOwnerService.isDeviceOwner(this)) {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val component = ComponentName(this, GuardianDeviceAdminReceiver::class.java)

                // Only revert if it's currently set to our filter
                val cr = contentResolver
                val currentSpecifier = Settings.Global.getString(cr, "private_dns_specifier")
                if (currentSpecifier == BlockingConfig.CLEANBROWSING_FAMILY_HOST) {
                    dpm.setGlobalSetting(component, "private_dns_mode", originalMode)
                    originalSpecifier?.let {
                        dpm.setGlobalSetting(component, "private_dns_specifier", it)
                    }
                    Timber.d("DNS: Reverted to $originalMode")
                }
            }
            originalMode = null
            originalSpecifier = null
            isDnsConfigured = false
        } catch (e: Exception) {
            Timber.e(e, "DNS: Revert failed")
        }
    }

    private fun createNotification(): Notification {
        return ForegroundServiceHelper.buildSilentNotification(
            this, 
            getString(R.string.app_name), 
            getString(R.string.notification_blocker_text)
        )
    }
}
