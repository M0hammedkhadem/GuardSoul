package com.agon.app

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import com.agon.app.data.repository.AppRepository
import com.agon.app.utils.DetectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class DnsVpnService : VpnService() {

    companion object {
        private const val NOTIFICATION_ID = 4001
        private const val VPN_MTU = 1500
        private const val DNS_PORT = 53
        private const val DOT_PORT = 853
        private const val DOH_PORT = 443

        private const val ACTION_RELOAD_WEBSITES = "com.agon.app.action.RELOAD_WEBSITES"
        // Basic mode DNS (adult filter — blocks porn only)
        private const val BASIC_DNS_1 = "185.228.168.10"
        private const val BASIC_DNS_2 = "185.228.169.11"
        // Strict mode DNS (family filter — blocks porn + social + more)
        private const val STRICT_DNS_1 = "185.228.168.9"
        private const val STRICT_DNS_2 = "185.228.169.9"
        private const val CLEAN_DNS_IPV6_1 = "2a0d:2a00:1::"
        private const val CLEAN_DNS_IPV6_2 = "2a0d:2a00:2::"
        private const val BASIC_DOT_HOST = "adult-filter-dns.cleanbrowsing.org"
        private const val STRICT_DOT_HOST = "family-filter-dns.cleanbrowsing.org"
        private const val SAFESEARCH_IP = "185.228.168.168"

        // Redirection map for SafeSearch. 
        // We only redirect dedicated safesearch subdomains. 
        // Redirecting main domains (google.com) causes issues with other services (Gmail, etc.)
        private val SAFESEARCH_HOSTS = mapOf(
            "forcesafesearch.google.com" to SAFESEARCH_IP,
            "strict.bing.com" to SAFESEARCH_IP,
            "restrict.youtube.com" to SAFESEARCH_IP,
            "restrictmoderate.youtube.com" to SAFESEARCH_IP,
            "strict.youtube.com" to SAFESEARCH_IP
        )

        private val REELS_DOMAINS = setOf(
            "reels.facebook.com",
            "b-graph.facebook.com",
            "graph.facebook.com",
            "edge-chat.facebook.com",
            "video.fbcdn.net",
            "reels.cdninstagram.com",
            "scontent.cdninstagram.com",
            "scontent-ord5-1.cdninstagram.com",
            "scontent-ord5-2.cdninstagram.com",
            "instagram.fotp8-1.fna.fbcdn.net",
            "googlevideo.com",          // YouTube Shorts
            "googleapis.com",           // YouTube API
            "yt3.ggpht.com",            // YouTube thumbnails
            "rr1---sn-4g5ednsk.googlevideo.com",
            "rr2---sn-4g5ednsk.googlevideo.com",
            "rr3---sn-4g5ednsk.googlevideo.com"
        )

        private val BLOCKED_DOH_IPS = setOf(
            "8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9"
        )

        @Volatile
        private var intentionalStop = false

        fun start(context: Context) {
            intentionalStop = false
            ForegroundServiceHelper.startServiceAsForeground(context, DnsVpnService::class.java)
        }

        fun stop(context: Context) {
            intentionalStop = true
            context.stopService(Intent(context, DnsVpnService::class.java))
        }

        fun wasStoppedIntentionally(): Boolean = intentionalStop

        fun clearIntentionalStopFlag() {
            intentionalStop = false
        }

        fun reloadWebsites(context: Context) {
            val intent = Intent(context, DnsVpnService::class.java).apply {
                action = ACTION_RELOAD_WEBSITES
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var physicalNetwork: Network? = null
    private lateinit var connectivityManager: ConnectivityManager
    private val blockedWebsites = mutableSetOf<String>()
    private val whitelistedWebsites = mutableSetOf<String>()
    private var lastWebsitesLoadTime = 0L

    // Cached mode to avoid reading DataStore on every packet
    private var safeSearchMode: String = "basic"
    private var blockDohEnabled: Boolean = false

    private val repo: AppRepository by lazy { (applicationContext as GuardianApp).repository }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        loadBlockedWebsites()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ForegroundServiceHelper.startForegroundCompat(this, NOTIFICATION_ID, createNotification())
        if (intent?.action == ACTION_RELOAD_WEBSITES) {
            loadBlockedWebsites()
            return START_NOT_STICKY
        }
        serviceScope.launch {
            val settings = repo.getAppSettings()
            // Ensure both master shield AND porn blocker are ON
            if (!settings.isShieldActive() || !settings.isPornBlockerActive()) {
                stopSelf()
                return@launch
            }
            safeSearchMode = settings.getSafeSearchMode()
            blockDohEnabled = settings.isBlockDohEnabled()
            if (safeSearchMode == "strict") {
                trySetPrivateDns(true)
            }
            physicalNetwork = connectivityManager.activeNetwork
            establishVpn()
        }
        return START_STICKY
    }

    private fun trySetPrivateDns(enabled: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            if (enabled) {
                val dotHost = if (safeSearchMode == "strict") STRICT_DOT_HOST else BASIC_DOT_HOST
                Settings.Global.putString(contentResolver, "private_dns_mode", "hostname")
                Settings.Global.putString(contentResolver, "private_dns_specifier", dotHost)
            } else {
                Settings.Global.putString(contentResolver, "private_dns_mode", "off")
            }
            true
        } catch (_: Exception) { false }
    }

    private fun establishVpn() {
        val dns1 = if (safeSearchMode == "strict") STRICT_DNS_1 else BASIC_DNS_1
        val dns2 = if (safeSearchMode == "strict") STRICT_DNS_2 else BASIC_DNS_2
        val builder = Builder()
            .setSession("GuardSoul DNS Filter")
            .setMtu(VPN_MTU)
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(dns1)
            .addDnsServer(dns2)
            .addAddress("fd00::2", 128)
            .addRoute("::", 0)
            .addDnsServer(CLEAN_DNS_IPV6_1)
            .addDnsServer(CLEAN_DNS_IPV6_2)

        try {
            vpnInterface = builder.establish()
            startTrafficForwarding()
        } catch (e: Exception) {
            Timber.e(e, "failed to establish VPN")
        }
    }

    private fun startTrafficForwarding() {
        val iface = vpnInterface ?: return
        vpnJob?.cancel()
        vpnJob = serviceScope.launch {
            val input = FileInputStream(iface.fileDescriptor)
            val output = FileOutputStream(iface.fileDescriptor)
            val buffer = ByteBuffer.allocate(VPN_MTU)

            while (isActive) {
                buffer.clear()
                val readBytes = try { input.channel.read(buffer) } catch (_: Exception) { -1 }
                if (readBytes <= 0) { delay(20); continue }
                buffer.flip()
                val packet = ByteArray(readBytes)
                buffer.get(packet)

                if (packet.size >= 20) {
                    val protocol = if ((packet[0].toInt() shr 4) == 4) packet[9].toInt() and 0xff else packet[6].toInt() and 0xff
                    if (protocol == 17) { // UDP
                        handleUdpPacket(packet, output)
                        continue
                    } else if (protocol == 6 && isBlockedTcp(packet)) {
                        continue
                    }
                }
                try { output.channel.write(ByteBuffer.wrap(packet)) } catch (_: Exception) {}
            }
        }
    }

    private fun handleUdpPacket(packet: ByteArray, output: FileOutputStream) {
        val dnsPayload = extractUdpPayload(packet)
        val domain = dnsPayload?.let { parseDnsQueryDomain(it) }

        if (domain != null) {
            // Smart Reels Detection Logic
            if (isReelsRelatedDomain(domain)) {
                DetectionState.updateNetworkConfidence(1.0f)
                serviceScope.launch {
                    delay(5_000)
                    if (DetectionState.networkConfidence.value >= 0.8f) {
                        DetectionState.updateNetworkConfidence(0.2f)
                    }
                }
            }

            if (isDomainBlocked(domain)) {
                val nxResponse = rewriteUdpResponse(packet, buildNxDomainResponse(dnsPayload))
                output.channel.write(ByteBuffer.wrap(nxResponse))
                return
            }

            if (isSafeSearchDomain(domain)) {
                val ip = SAFESEARCH_HOSTS[domain.lowercase()] ?: SAFESEARCH_IP
                val safeResponse = rewriteUdpResponse(packet, buildARecordResponse(dnsPayload, ip))
                output.channel.write(ByteBuffer.wrap(safeResponse))
                return
            }
        }
        try { output.channel.write(ByteBuffer.wrap(packet)) } catch (_: Exception) {}
    }

    private fun isReelsRelatedDomain(domain: String): Boolean {
        val d = domain.lowercase()
        return REELS_DOMAINS.any { d == it || d.endsWith(".$it") } || 
               (d.contains("reels") && (d.contains("facebook") || d.contains("instagram")))
    }

    private fun isBlockedTcp(packet: ByteArray): Boolean {
        if (!blockDohEnabled) return false
        // Logic to block DoH/DoT endpoints via TCP RST
        return false // Simplified for now
    }

    private fun isDomainBlocked(domain: String): Boolean {
        val clean = domain.lowercase().trim()
        if (whitelistedWebsites.any { clean == it || clean.endsWith(".$it") }) return false
        return blockedWebsites.any { clean == it || clean.endsWith(".$it") }
    }

    private fun isSafeSearchDomain(domain: String): Boolean {
        return SAFESEARCH_HOSTS.containsKey(domain.lowercase())
    }

    private fun parseDnsQueryDomain(payload: ByteArray): String? {
        if (payload.size < 12) return null
        val domain = StringBuilder()
        var offset = 12
        while (offset < payload.size) {
            val len = payload[offset].toInt() and 0xff
            if (len == 0) break
            if (domain.isNotEmpty()) domain.append(".")
            if (offset + 1 + len > payload.size) return null
            domain.append(String(payload, offset + 1, len))
            offset += 1 + len
        }
        return domain.toString()
    }

    private fun extractUdpPayload(packet: ByteArray): ByteArray? {
        val headerLen = if ((packet[0].toInt() shr 4) == 4) (packet[0].toInt() and 0x0f) * 4 else 40
        val payloadOffset = headerLen + 8
        if (packet.size < payloadOffset) return null
        return packet.copyOfRange(payloadOffset, packet.size)
    }

    private fun buildNxDomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        if (response.size >= 4) { response[2] = 0x81.toByte(); response[3] = 0x83.toByte() }
        return response
    }

    private fun buildARecordResponse(query: ByteArray, ip: String): ByteArray {
        val response = query.copyOf()
        if (response.size >= 4) { response[2] = 0x81.toByte(); response[3] = 0x80.toByte() }
        val parts = ip.split(".").map { it.toInt().toByte() }
        val answer = byteArrayOf(0xC0.toByte(), 0x0C, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4) + parts.toByteArray()
        return response + answer
    }

    private fun rewriteUdpResponse(original: ByteArray, dns: ByteArray): ByteArray {
        // Implementation logic to flip ports and IPs for response
        return original // Placeholder
    }

    private fun loadBlockedWebsites() {
        serviceScope.launch {
            try {
                val list = repo.getBlocklist("blacklist", "websites").map { it.value }
                val white = repo.getBlocklist("whitelist", "websites").map { it.value }
                synchronized(blockedWebsites) { blockedWebsites.clear(); blockedWebsites.addAll(list) }
                synchronized(whitelistedWebsites) { whitelistedWebsites.clear(); whitelistedWebsites.addAll(white) }
            } catch (_: Exception) {}
        }
    }

    private fun createNotification(): Notification {
        return ForegroundServiceHelper.buildSilentNotification(this, "GuardSoul Filter", "Active Protection")
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnJob?.cancel()
        serviceScope.cancel()
        trySetPrivateDns(false) // Revert Private DNS setting
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Timber.e(e, "Error closing VPN interface")
        }
    }
}
