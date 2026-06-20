package com.agon.app.utils

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.agon.app.GuardianApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * VpnDoHDetector - كشف تطبيقات VPN و DNS-over-HTTPS (DoH)
 *
 * يكشف:
 * 1. تطبيقات VPN المعروفة (NordVPN, ExpressVPN, etc.)
 * 2. الاتصال النشط عبر VPN interface
 * 3. DoH providers (Cloudflare, Google, Quad9)
 *
 * عند اكتشاف VPN: يمكن حظر التطبيق أو إرسال تنبيه.
 */
object VpnDoHDetector {

    private val KNOWN_VPN_PACKAGES = setOf(
        "com.nordvpn.android",
        "com.tunnelbear.android",
        "com.expressvpn.vpn",
        "com.surfshark.vpn",
        "com.windscribe.vpn",
        "com.protonvpn.android",
        "com.privateinternetaccess.android",
        "com.hotspotshield.android.vpn",
        "com.cyberghost.vpn",
        "com.uvpn.android",
        "com.freevpn.intouch",
        "com.purevpn.freeswitch"
    )

    private val KNOWN_DOH_PROVIDERS = setOf(
        "cloudflare-dns.com",
        "dns.google",
        "dns.quad9.net",
        "dns.adguard.com",
        "doh.opendns.com"
    )

    /**
     * كشف تطبيقات VPN المثبتة على الجهاز.
     */
    fun detectInstalledVpnApps(context: Context): List<String> {
        val pm = context.packageManager
        return KNOWN_VPN_PACKAGES.mapNotNull { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                pkg
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }.also {
            if (it.isNotEmpty()) Timber.w("VpnDoHDetector: Installed VPN apps: $it")
        }
    }

    /**
     * كشف إذا كان هناك VPN connection نشط حالياً.
     */
    fun isVpnConnectionActive(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            val hasVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            Timber.d("VpnDoHDetector: VPN transport active=$hasVpn")
            hasVpn
        } catch (e: Exception) {
            Timber.w(e, "VpnDoHDetector: failed to check VPN connection")
            false
        }
    }

    /**
     * كشف DoH عبر مراقبة DNS settings.
     * Note: Requires READ_PRIVILEGED_PHONE_STATE or is limited.
     */
    fun detectDoH(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                val currentSpecifier = android.provider.Settings.Global.getString(
                    context.contentResolver,
                    "private_dns_specifier"
                ) ?: ""
                val isDoH = KNOWN_DOH_PROVIDERS.any { currentSpecifier.contains(it) }
                Timber.d("VpnDoHDetector: DoH detected=$isDoH (specifier=$currentSpecifier)")
                return isDoH
            } catch (e: Exception) {
                Timber.w(e, "VpnDoHDetector: failed to check DoH")
            }
        }
        return false
    }

    /**
     * فحص شامل - يعيد تقرير الحالة.
     */
    data class DetectionReport(
        val vpnAppsInstalled: List<String>,
        val vpnConnectionActive: Boolean,
        val doHDetected: Boolean
    )

    fun fullScan(context: Context): DetectionReport {
        return DetectionReport(
            vpnAppsInstalled = detectInstalledVpnApps(context),
            vpnConnectionActive = isVpnConnectionActive(context),
            doHDetected = detectDoH(context)
        ).also {
            Timber.i("VpnDoHDetector: Full scan: VPN apps=${it.vpnAppsInstalled.size}, VPN active=${it.vpnConnectionActive}, DoH=${it.doHDetected}")
        }
    }

    /**
     * إضافة تطبيقات VPN إلى قائمة الحظر (Blocked Apps).
     */
    fun blockVpnApps(context: Context) {
        val vpnApps = detectInstalledVpnApps(context)
        if (vpnApps.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as GuardianApp
                val settings = app.repository.getAppSettings()
                val currentBlocked = settings.blockedAppsFlow.first()
                settings.setBlockedApps(currentBlocked + vpnApps.toSet())
                Timber.w("VpnDoHDetector: Blocked ${vpnApps.size} VPN apps")
            } catch (e: Exception) {
                Timber.w(e, "VpnDoHDetector: failed to block VPN apps")
            }
        }
    }
}
