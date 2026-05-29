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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
        private const val CLEAN_DNS_1 = "185.228.168.9"
        private const val CLEAN_DNS_2 = "185.228.169.9"
        private const val CLEAN_DNS_IPV6_1 = "2a0d:2a00:1::"
        private const val CLEAN_DNS_IPV6_2 = "2a0d:2a00:2::"
        private const val CLEAN_DOT_HOST = "family-filter-dns.cleanbrowsing.org"
        private const val SAFESEARCH_IP = "185.228.168.168"

        private val SAFESEARCH_HOSTS = mapOf(
            "google.com" to SAFESEARCH_IP,
            "www.google.com" to SAFESEARCH_IP,
            "bing.com" to SAFESEARCH_IP,
            "www.bing.com" to SAFESEARCH_IP,
            "youtube.com" to SAFESEARCH_IP,
            "www.youtube.com" to SAFESEARCH_IP,
            "forcesafesearch.google.com" to SAFESEARCH_IP,
            "strict.bing.com" to SAFESEARCH_IP,
            "restrict.youtube.com" to SAFESEARCH_IP,
            "restrictmoderate.youtube.com" to SAFESEARCH_IP,
            "strict.youtube.com" to SAFESEARCH_IP
        )

        private val BLOCKED_DOH_DOMAINS = setOf(
            "dns.google", "dns.google.com",
            "dns.google:443",
            "cloudflare-dns.com", "mozilla.cloudflare-dns.com",
            "one.one.one.one",
            "dns.quad9.net", "dns9.quad9.net",
            "dns.opendns.com",
            "dns.comodo.com",
            "dns.nextdns.io",
            "208.67.222.222", "208.67.220.123"
        )

        private val BLOCKED_DOH_IPS = setOf(
            "8.8.8.8", "8.8.4.4",
            "1.1.1.1", "1.0.0.1",
            "9.9.9.9", "149.112.112.112",
            "208.67.222.222", "208.67.220.220",
            "208.67.222.123", "208.67.220.123"
        )

        private val BLOCKED_DOH_IPS_V6 = setOf(
            "2001:4860:4860::8888", "2001:4860:4860::8844",
            "2606:4700:4700::1111", "2606:4700:4700::1001",
            "2620:fe::fe", "2620:fe::9"
        )

        private val BLOCKED_DOT_IPS = setOf(
            "8.8.8.8", "8.8.4.4",
            "1.1.1.1", "1.0.0.1",
            "9.9.9.9", "149.112.112.112"
        )

        private val BLOCKED_DOT_IPS_V6 = setOf(
            "2001:4860:4860::8888", "2001:4860:4860::8844",
            "2606:4700:4700::1111", "2606:4700:4700::1001",
            "2620:fe::fe", "2620:fe::9"
        )

        @Volatile
        private var intentionalStop = false

        fun wasStoppedIntentionally(): Boolean = intentionalStop

        fun clearIntentionalStopFlag() {
            intentionalStop = false
        }

        fun start(context: Context) {
            intentionalStop = false
            ForegroundServiceHelper.startServiceAsForeground(
                context, DnsVpnService::class.java
            )
        }

        fun stop(context: Context) {
            intentionalStop = true
            context.stopService(Intent(context, DnsVpnService::class.java))
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

    private val repo: AppRepository by lazy {
        (applicationContext as GuardianApp).repository
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        Timber.d("DnsVpnService created")
        loadBlockedWebsites()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RELOAD_WEBSITES) {
            loadBlockedWebsites()
            return START_NOT_STICKY
        }
        serviceScope.launch {
            val settings = repo.getAppSettings()
            val shouldRun = try { settings.isPornBlockerActive() } catch (_: Exception) { false }
            if (!shouldRun) {
                Timber.d("DnsVpnService: porn blocker inactive")
                stopSelf()
                return@launch
            }
            ForegroundServiceHelper.startForegroundCompat(
                this@DnsVpnService, NOTIFICATION_ID, createNotification()
            )

            trySetPrivateDns()
            physicalNetwork = connectivityManager.activeNetwork
            establishVpn()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        super.onRevoke()
        vpnJob?.cancel()
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        serviceScope.launch {
            try { repo.getAppSettings().setPornBlocker(false) } catch (_: Exception) {}
        }
        Timber.d("DnsVpnService revoked")
        if (!intentionalStop) {
            sendRevocationBroadcast()
        } else {
            clearIntentionalStopFlag()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnJob?.cancel()
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        serviceScope.cancel()
        Timber.d("DnsVpnService destroyed")
        if (!intentionalStop) {
            sendRevocationBroadcast()
        } else {
            clearIntentionalStopFlag()
        }
    }

    private fun sendRevocationBroadcast() {
        Timber.w("VPN stopped unintentionally — scheduling security alert")
        val intent = Intent(VpnStateMonitor.ACTION_VPN_REVOKED).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
        VpnStateMonitor.scheduleRevocationWork(this)
    }

    private fun trySetPrivateDns(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            Settings.Global.putString(contentResolver, "private_dns_mode", "hostname")
            Settings.Global.putString(contentResolver, "private_dns_specifier", CLEAN_DOT_HOST)
            Timber.d("Private DNS set to $CLEAN_DOT_HOST")
            true
        } catch (e: Exception) {
            Timber.w(e, "Cannot set Private DNS, using VPN fallback")
            false
        }
    }

    private fun establishVpn() {
        val builder = Builder()
            .setSession("Guardian DNS Filter")
            .setMtu(VPN_MTU)
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(CLEAN_DNS_1)
            .addDnsServer(CLEAN_DNS_2)
            .addAddress("fd00::2", 128)
            .addRoute("::", 0)
            .addDnsServer(CLEAN_DNS_IPV6_1)
            .addDnsServer(CLEAN_DNS_IPV6_2)

        try {
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Timber.w("establish returned null")
                return
            }
            Timber.d("VPN established with CleanBrowsing DNS")
            startTrafficForwarding()
        } catch (e: Exception) {
            Timber.e(e, "failed to establish VPN")
        }
    }

    private fun startTrafficForwarding() {
        val iface = vpnInterface ?: run {
            Timber.w("startTrafficForwarding: vpnInterface is null, aborting")
            return
        }
        vpnJob?.cancel()
        vpnJob = serviceScope.launch {
            val input = FileInputStream(iface.fileDescriptor)
            val output = FileOutputStream(iface.fileDescriptor)
            val buffer = ByteBuffer.allocate(VPN_MTU)
            val outBuffer = ByteBuffer.allocate(VPN_MTU)

            try {
                while (isActive) {
                    buffer.clear()
                    val readBytes = input.channel.read(buffer)
                    if (readBytes <= 0) {
                        if (isActive) delay(50)
                        continue
                    }
                    buffer.flip()

                    val packet = ByteArray(readBytes)
                    buffer.get(packet)

                    if (packet.size < 20) {
                        output.channel.write(ByteBuffer.wrap(packet))
                        continue
                    }

                    val version = (packet[0].toInt() shr 4) and 0x0f
                    val protocol = when (version) {
                        4 -> packet[9].toInt() and 0xff
                        6 -> packet[6].toInt() and 0xff
                        else -> {
                            output.channel.write(ByteBuffer.wrap(packet))
                            continue
                        }
                    }

                    when (protocol) {
                        6 -> { // TCP
                            if (isBlockedTcpConnection(packet)) continue
                            output.channel.write(ByteBuffer.wrap(packet))
                        }
                        17 -> { // UDP
                            if (isDnsQuery(packet)) {
                                checkReloadWebsites()
                                val dnsPayload = extractUdpPayload(packet)
                                val domain = dnsPayload?.let { parseDnsQueryDomain(it) }

                                if (domain != null && isDohDomain(domain)) {
                                    Timber.d("Blocked DoH domain: $domain")
                                    val blocked = buildNxDomainResponse(dnsPayload)
                                    val response = rewriteUdpResponse(packet, blocked)
                                    output.channel.write(ByteBuffer.wrap(response))
                                    continue
                                }

                                if (domain != null && isDomainBlocked(domain)) {
                                    Timber.d("Blocked website: $domain")
                                    serviceScope.launch {
                                        try { repo.recordBlock(domain, domain, "dns_filter") } catch (_: Exception) {}
                                    }
                                    val blocked = buildNxDomainResponse(dnsPayload)
                                    val response = rewriteUdpResponse(packet, blocked)
                                    output.channel.write(ByteBuffer.wrap(response))
                                    continue
                                }

                                if (domain != null && isSafeSearchDomain(domain)) {
                                    val ip = SAFESEARCH_HOSTS[domain] ?: SAFESEARCH_IP
                                    val aRecord = buildARecordResponse(dnsPayload, ip)
                                    val response = rewriteUdpResponse(packet, aRecord)
                                    output.channel.write(ByteBuffer.wrap(response))
                                    continue
                                }

                                val forwarded = forwardDnsQuery(dnsPayload)
                                if (forwarded != null) {
                                    val response = rewriteUdpResponse(packet, forwarded)
                                    output.channel.write(ByteBuffer.wrap(response))
                                    continue
                                }
                            }
                            output.channel.write(ByteBuffer.wrap(packet))
                        }
                        else -> {
                            output.channel.write(ByteBuffer.wrap(packet))
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) Timber.w(e, "traffic forwarding stopped")
            }
        }
    }

    private fun isDnsQuery(packet: ByteArray): Boolean {
        if (packet.size < 20) return false
        val version = (packet[0].toInt() shr 4) and 0x0f
        return when (version) {
            4 -> isDnsQueryIPv4(packet)
            6 -> isDnsQueryIPv6(packet)
            else -> false
        }
    }

    private fun isDnsQueryIPv4(packet: ByteArray): Boolean {
        val headerLength = (packet[0].toInt() and 0x0f) * 4
        if (packet.size < headerLength + 4) return false
        val srcPort = ((packet[headerLength].toInt() and 0xff) shl 8) or (packet[headerLength + 1].toInt() and 0xff)
        val dstPort = ((packet[headerLength + 2].toInt() and 0xff) shl 8) or (packet[headerLength + 3].toInt() and 0xff)
        return srcPort == DNS_PORT || dstPort == DNS_PORT
    }

    private fun isDnsQueryIPv6(packet: ByteArray): Boolean {
        if (packet.size < 48) return false
        val nextHeader = packet[6].toInt() and 0xff
        if (nextHeader != 17) return false
        val dstPort = ((packet[42].toInt() and 0xff) shl 8) or (packet[43].toInt() and 0xff)
        return dstPort == DNS_PORT
    }

    private fun isBlockedTcpConnection(packet: ByteArray): Boolean {
        val version = (packet[0].toInt() shr 4) and 0x0f
        return when (version) {
            4 -> isBlockedTcpConnectionIPv4(packet)
            6 -> isBlockedTcpConnectionIPv6(packet)
            else -> false
        }
    }

    private fun isBlockedTcpConnectionIPv4(packet: ByteArray): Boolean {
        val headerLength = (packet[0].toInt() and 0x0f) * 4
        if (packet.size < headerLength + 14) return false

        val dstIp = buildIpv4String(packet, 16)
        val dstPort = ((packet[headerLength + 2].toInt() and 0xff) shl 8) or (packet[headerLength + 3].toInt() and 0xff)

        if (dstPort == DOH_PORT && dstIp in BLOCKED_DOH_IPS) {
            Timber.d("Blocked DoH TCP connection to $dstIp:$dstPort")
            injectTcpRst(packet)
            return true
        }

        if (dstPort == DOT_PORT && dstIp in BLOCKED_DOT_IPS) {
            Timber.d("Blocked DoT TCP connection to $dstIp:$dstPort")
            injectTcpRst(packet)
            return true
        }

        return false
    }

    private fun isBlockedTcpConnectionIPv6(packet: ByteArray): Boolean {
        if (packet.size < 60) return false
        val nextHeader = packet[6].toInt() and 0xff
        if (nextHeader != 6) return false

        val dstIp = buildIpv6String(packet, 24)
        val dstPort = ((packet[42].toInt() and 0xff) shl 8) or (packet[43].toInt() and 0xff)

        if (dstPort == DOH_PORT && dstIp in BLOCKED_DOH_IPS_V6) {
            Timber.d("Blocked DoH TCP connection to $dstIp:$dstPort")
            injectTcpRst(packet)
            return true
        }

        if (dstPort == DOT_PORT && dstIp in BLOCKED_DOT_IPS_V6) {
            Timber.d("Blocked DoT TCP connection to $dstIp:$dstPort")
            injectTcpRst(packet)
            return true
        }

        return false
    }

    private fun injectTcpRst(packet: ByteArray) {
        val iface = vpnInterface ?: return
        try {
            val output = FileOutputStream(iface.fileDescriptor)
            val resetPacket = buildTcpRst(packet)
            output.channel.write(ByteBuffer.wrap(resetPacket))
        } catch (_: Exception) {}
    }

    private fun buildTcpRst(packet: ByteArray): ByteArray {
        val version = (packet[0].toInt() shr 4) and 0x0f
        return when (version) {
            4 -> buildTcpRstIPv4(packet)
            6 -> buildTcpRstIPv6(packet)
            else -> packet
        }
    }

    private fun buildTcpRstIPv4(packet: ByteArray): ByteArray {
        val headerLength = (packet[0].toInt() and 0x0f) * 4
        val tcpHeaderOffset = headerLength
        val tcpHeaderLen = ((packet[tcpHeaderOffset + 12].toInt() and 0xf0) shr 2)

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val srcPort = ((packet[tcpHeaderOffset].toInt() and 0xff) shl 8) or (packet[tcpHeaderOffset + 1].toInt() and 0xff)
        val dstPort = ((packet[tcpHeaderOffset + 2].toInt() and 0xff) shl 8) or (packet[tcpHeaderOffset + 3].toInt() and 0xff)

        val seqNum = ((packet[tcpHeaderOffset + 4].toInt() and 0xff) shl 24) or
                ((packet[tcpHeaderOffset + 5].toInt() and 0xff) shl 16) or
                ((packet[tcpHeaderOffset + 6].toInt() and 0xff) shl 8) or
                (packet[tcpHeaderOffset + 7].toInt() and 0xff)

        val ackNum = ((packet[tcpHeaderOffset + 8].toInt() and 0xff) shl 24) or
                ((packet[tcpHeaderOffset + 9].toInt() and 0xff) shl 16) or
                ((packet[tcpHeaderOffset + 10].toInt() and 0xff) shl 8) or
                (packet[tcpHeaderOffset + 11].toInt() and 0xff)

        val dataOffset = tcpHeaderOffset + tcpHeaderLen
        val payloadLen = if (dataOffset < packet.size) packet.size - dataOffset else 0

        val tcpRstLen = 20
        val ipTotalLen = 20 + tcpRstLen

        val buf = ByteBuffer.allocate(ipTotalLen)

        buf.put(0x45.toByte())
        buf.put(packet[1])
        buf.putShort(ipTotalLen.toShort())
        buf.putShort((((packet[4].toInt() and 0xff) shl 8) or (packet[5].toInt() and 0xff)).toShort())
        buf.putShort(0.toShort()) // flags + fragment offset
        buf.put(64.toByte()) // TTL
        buf.put(6.toByte()) // TCP
        buf.putShort(0.toShort()) // checksum placeholder
        buf.put(dstIp)
        buf.put(srcIp)
        buf.putShort(dstPort.toShort())
        buf.putShort(srcPort.toShort())
        buf.putInt(((ackNum).toInt()))
        buf.putInt(((seqNum + payloadLen).toInt()))
        buf.putShort(0x5014.toShort()) // data offset=5, reserved=0, flags=RST+ACK
        buf.putShort(0.toShort()) // window
        buf.putShort(0.toShort()) // checksum placeholder
        buf.putShort(0.toShort()) // urgent pointer

        val ipPacket = buf.array()

        val savedIpChecksumOffset = 10
        ipPacket[savedIpChecksumOffset] = 0
        ipPacket[savedIpChecksumOffset + 1] = 0
        var ipChecksum = 0L
        for (i in 0 until 20 step 2) {
            val w = ((ipPacket[i].toInt() and 0xff) shl 8) or (ipPacket[i + 1].toInt() and 0xff)
            ipChecksum += w
        }
        while (ipChecksum > 0xffff) ipChecksum = (ipChecksum and 0xffff) + (ipChecksum shr 16)
        val ipCsum = ((ipChecksum.toInt() xor 0xffff) and 0xffff)
        ipPacket[savedIpChecksumOffset] = (ipCsum shr 8).toByte()
        ipPacket[savedIpChecksumOffset + 1] = (ipCsum and 0xff).toByte()

        val tcpChecksumOffset = 20 + 16
        ipPacket[tcpChecksumOffset] = 0
        ipPacket[tcpChecksumOffset + 1] = 0

        val pseudoLen = 12 + tcpRstLen
        val pseudoBuf = ByteBuffer.allocate(pseudoLen)
        pseudoBuf.put(dstIp)
        pseudoBuf.put(srcIp)
        pseudoBuf.put(0.toByte())
        pseudoBuf.put(6.toByte())
        pseudoBuf.putShort(tcpRstLen.toShort())
        pseudoBuf.put(ipPacket, 20, tcpRstLen)
        val pseudoData = pseudoBuf.array()

        var tcpChecksum = 0L
        val wordCount = pseudoLen + (pseudoLen % 2)
        for (i in 0 until wordCount step 2) {
            val b1 = if (i < pseudoLen) pseudoData[i].toInt() and 0xff else 0
            val b2 = if (i + 1 < pseudoLen) pseudoData[i + 1].toInt() and 0xff else 0
            tcpChecksum += ((b1 shl 8) or b2)
        }
        while (tcpChecksum > 0xffff) tcpChecksum = (tcpChecksum and 0xffff) + (tcpChecksum shr 16)
        val tcpCsum = ((tcpChecksum.toInt() xor 0xffff) and 0xffff)
        ipPacket[tcpChecksumOffset] = (tcpCsum shr 8).toByte()
        ipPacket[tcpChecksumOffset + 1] = (tcpCsum and 0xff).toByte()

        return ipPacket
    }

    private fun buildTcpRstIPv6(packet: ByteArray): ByteArray {
        if (packet.size < 60) return packet
        val tcpHeaderOffset = 40
        val tcpHeaderLen = ((packet[tcpHeaderOffset + 12].toInt() and 0xf0) shr 2)

        val srcIp = packet.copyOfRange(8, 24)
        val dstIp = packet.copyOfRange(24, 40)
        val srcPort = ((packet[tcpHeaderOffset].toInt() and 0xff) shl 8) or (packet[tcpHeaderOffset + 1].toInt() and 0xff)
        val dstPort = ((packet[tcpHeaderOffset + 2].toInt() and 0xff) shl 8) or (packet[tcpHeaderOffset + 3].toInt() and 0xff)

        val seqNum = ((packet[tcpHeaderOffset + 4].toInt() and 0xff) shl 24) or
                ((packet[tcpHeaderOffset + 5].toInt() and 0xff) shl 16) or
                ((packet[tcpHeaderOffset + 6].toInt() and 0xff) shl 8) or
                (packet[tcpHeaderOffset + 7].toInt() and 0xff)

        val ackNum = ((packet[tcpHeaderOffset + 8].toInt() and 0xff) shl 24) or
                ((packet[tcpHeaderOffset + 9].toInt() and 0xff) shl 16) or
                ((packet[tcpHeaderOffset + 10].toInt() and 0xff) shl 8) or
                (packet[tcpHeaderOffset + 11].toInt() and 0xff)

        val dataOffset = tcpHeaderOffset + tcpHeaderLen
        val payloadLen = if (dataOffset < packet.size) packet.size - dataOffset else 0

        val tcpRstLen = 20
        val ipv6PayloadLen = tcpRstLen

        val buf = ByteBuffer.allocate(40 + tcpRstLen)
        buf.put(0x60.toByte())
        buf.put(0x00)
        buf.putShort(ipv6PayloadLen.toShort())
        buf.put(6.toByte()) // TCP
        buf.put(0x00)
        buf.put(dstIp)
        buf.put(srcIp)
        buf.putShort(dstPort.toShort())
        buf.putShort(srcPort.toShort())
        buf.putInt(ackNum)
        buf.putInt(seqNum + payloadLen)
        buf.putShort(0x5014.toShort()) // data offset=5, reserved=0, flags=RST+ACK
        buf.putShort(0.toShort()) // window
        buf.putShort(0.toShort()) // checksum placeholder
        buf.putShort(0.toShort()) // urgent pointer

        val rstPacket = buf.array()

        val tcpChecksumOffset = 40 + 16
        rstPacket[tcpChecksumOffset] = 0
        rstPacket[tcpChecksumOffset + 1] = 0

        val pseudoLen = 40 + 12 + tcpRstLen
        val pseudoBuf = ByteBuffer.allocate(pseudoLen)
        pseudoBuf.put(dstIp)
        pseudoBuf.put(srcIp)
        pseudoBuf.putInt(ipv6PayloadLen)
        pseudoBuf.put(0x00)
        pseudoBuf.put(0x00)
        pseudoBuf.put(0x00)
        pseudoBuf.put(6.toByte())
        pseudoBuf.putShort(tcpRstLen.toShort())
        pseudoBuf.put(rstPacket, 40, tcpRstLen)
        val pseudoData = pseudoBuf.array()

        var tcpChecksum = 0L
        val wordCount = pseudoLen + (pseudoLen % 2)
        for (i in 0 until wordCount step 2) {
            val b1 = if (i < pseudoLen) pseudoData[i].toInt() and 0xff else 0
            val b2 = if (i + 1 < pseudoLen) pseudoData[i + 1].toInt() and 0xff else 0
            tcpChecksum += ((b1 shl 8) or b2)
        }
        while (tcpChecksum > 0xffff) tcpChecksum = (tcpChecksum and 0xffff) + (tcpChecksum shr 16)
        val tcpCsum = ((tcpChecksum.toInt() xor 0xffff) and 0xffff)
        rstPacket[tcpChecksumOffset] = (tcpCsum shr 8).toByte()
        rstPacket[tcpChecksumOffset + 1] = (tcpCsum and 0xff).toByte()

        return rstPacket
    }

    private fun parseDnsQueryDomain(dnsPayload: ByteArray): String? {
        if (dnsPayload.size < 12) return null
        val qdCount = ((dnsPayload[4].toInt() and 0xff) shl 8) or (dnsPayload[5].toInt() and 0xff)
        if (qdCount <= 0) return null

        val domain = StringBuilder()
        var offset = 12
        while (offset < dnsPayload.size) {
            val labelLen = dnsPayload[offset].toInt() and 0xff
            if (labelLen == 0) break
            if (labelLen and 0xc0 == 0xc0) {
                offset += 2
                break
            }
            if (offset + 1 + labelLen > dnsPayload.size) return null
            if (domain.isNotEmpty()) domain.append(".")
            domain.append(String(dnsPayload, offset + 1, labelLen, Charsets.US_ASCII))
            offset += 1 + labelLen
        }
        return domain.toString()
    }

    private fun isDomainBlocked(domain: String): Boolean {
        val cleanDomain = domain.lowercase().trim()
        synchronized(whitelistedWebsites) {
            if (whitelistedWebsites.any { cleanDomain == it || cleanDomain.endsWith(".$it") }) return false
        }
        synchronized(blockedWebsites) {
            if (blockedWebsites.contains(cleanDomain)) return true
            for (blocked in blockedWebsites) {
                if (cleanDomain == blocked || cleanDomain.endsWith(".$blocked")) return true
            }
        }
        return false
    }

    private fun isSafeSearchDomain(domain: String): Boolean {
        return SAFESEARCH_HOSTS.containsKey(domain.lowercase().trim())
    }

    private fun isDohDomain(domain: String): Boolean {
        return BLOCKED_DOH_DOMAINS.contains(domain.lowercase().trim())
    }

    private fun buildIpv4String(packet: ByteArray, offset: Int): String {
        return "${packet[offset].toInt() and 0xff}.${packet[offset + 1].toInt() and 0xff}.${packet[offset + 2].toInt() and 0xff}.${packet[offset + 3].toInt() and 0xff}"
    }

    private fun buildIpv6String(packet: ByteArray, offset: Int): String {
        val sb = StringBuilder()
        for (i in 0 until 16 step 2) {
            if (i > 0) sb.append(":")
            sb.append(String.format("%04x", ((packet[offset + i].toInt() and 0xff) shl 8) or (packet[offset + i + 1].toInt() and 0xff)))
        }
        return sb.toString()
    }

    private fun forwardDnsQuery(dnsQuery: ByteArray?): ByteArray? {
        if (dnsQuery == null) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val dotResult = forwardViaDoT(dnsQuery)
            if (dotResult != null) return dotResult
        }

        val udpResult = forwardViaUdp(dnsQuery, CLEAN_DNS_1)
        if (udpResult != null) return udpResult

        return forwardViaUdp(dnsQuery, CLEAN_DNS_2)
    }

    private fun forwardViaDoT(dnsQuery: ByteArray): ByteArray? {
        val network = physicalNetwork ?: return null
        return try {
            val socket = SSLSocketFactory.getDefault().createSocket() as SSLSocket
            network.bindSocket(socket)
            socket.connect(InetSocketAddress(InetAddress.getByName(CLEAN_DOT_HOST), DOT_PORT), 5000)
            socket.soTimeout = 5000
            socket.startHandshake()

            val hv = HttpsURLConnection.getDefaultHostnameVerifier()
            if (!hv.verify(CLEAN_DOT_HOST, socket.session)) {
                socket.close()
                return null
            }

            val lengthPrefixed = ByteBuffer.allocate(2 + dnsQuery.size)
                .putShort(dnsQuery.size.toShort())
                .put(dnsQuery)
                .array()
            socket.outputStream.write(lengthPrefixed)
            socket.outputStream.flush()

            val lenBytes = ByteArray(2)
            readFully(socket.inputStream, lenBytes)
            val responseLen = ((lenBytes[0].toInt() and 0xff) shl 8) or (lenBytes[1].toInt() and 0xff)
            if (responseLen <= 0 || responseLen > 4096) {
                socket.close()
                return null
            }

            val response = ByteArray(responseLen)
            readFully(socket.inputStream, response)
            socket.close()
            response
        } catch (e: Exception) {
            Timber.w(e, "DoT query failed")
            null
        }
    }

    private fun forwardViaUdp(dnsQuery: ByteArray, dnsServer: String): ByteArray? {
        return try {
            val socket = DatagramSocket()
            val network = physicalNetwork
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && network != null) {
                network.bindSocket(socket)
            }
            socket.soTimeout = 5000
            val packet = DatagramPacket(dnsQuery, dnsQuery.size, InetAddress.getByName(dnsServer), DNS_PORT)
            socket.send(packet)
            val responseBuf = ByteArray(1500)
            val responsePacket = DatagramPacket(responseBuf, responseBuf.size)
            socket.receive(responsePacket)
            socket.close()
            responsePacket.data.copyOfRange(0, responsePacket.length)
        } catch (e: Exception) {
            Timber.w(e, "UDP DNS query to $dnsServer failed")
            null
        }
    }

    private fun readFully(inputStream: java.io.InputStream, buffer: ByteArray) {
        var offset = 0
        val len = buffer.size
        while (offset < len) {
            val read = inputStream.read(buffer, offset, len - offset)
            if (read < 0) throw java.io.EOFException()
            offset += read
        }
    }

    private fun buildARecordResponse(queryPayload: ByteArray, ip: String): ByteArray {
        val response = queryPayload.copyOf()
        if (response.size >= 4) {
            response[2] = 0x81.toByte()
            response[3] = 0x80.toByte()
            response[6] = 0x00
            response[7] = 0x01
        }
        val ipParts = ip.split(".")
        if (ipParts.size == 4) {
            val answer = ByteArray(16)
            answer[0] = 0xC0.toByte()
            answer[1] = 0x0C
            answer[2] = 0x00
            answer[3] = 0x01
            answer[4] = 0x00
            answer[5] = 0x01
            answer[6] = 0x00
            answer[7] = 0x00
            answer[8] = 0x01
            answer[9] = 0x2C
            answer[10] = 0x00
            answer[11] = 0x04
            answer[12] = ipParts[0].toByte()
            answer[13] = ipParts[1].toByte()
            answer[14] = ipParts[2].toByte()
            answer[15] = ipParts[3].toByte()
            return response + answer
        }
        return response
    }

    private fun buildNxDomainResponse(queryPayload: ByteArray): ByteArray {
        val response = queryPayload.copyOf()
        if (response.size >= 4) {
            response[2] = 0x81.toByte()
            response[3] = 0x83.toByte()
        }
        return response
    }

    private fun extractUdpPayload(packet: ByteArray): ByteArray? {
        val version = (packet[0].toInt() shr 4) and 0x0f
        return when (version) {
            4 -> extractUdpPayloadIPv4(packet)
            6 -> extractUdpPayloadIPv6(packet)
            else -> null
        }
    }

    private fun extractUdpPayloadIPv4(packet: ByteArray): ByteArray? {
        if (packet.size < 20) return null
        val headerLength = (packet[0].toInt() and 0x0f) * 4
        val udpHeaderOffset = headerLength
        if (packet.size < udpHeaderOffset + 8) return null
        val udpLength = ((packet[udpHeaderOffset + 4].toInt() and 0xff) shl 8) or (packet[udpHeaderOffset + 5].toInt() and 0xff)
        val payloadOffset = udpHeaderOffset + 8
        val payloadLength = minOf(udpLength - 8, packet.size - payloadOffset)
        if (payloadLength <= 0) return null
        return packet.copyOfRange(payloadOffset, payloadOffset + payloadLength)
    }

    private fun extractUdpPayloadIPv6(packet: ByteArray): ByteArray? {
        if (packet.size < 48) return null
        val nextHeader = packet[6].toInt() and 0xff
        if (nextHeader != 17) return null
        val udpHeaderOffset = 40
        val udpLength = ((packet[udpHeaderOffset + 4].toInt() and 0xff) shl 8) or (packet[udpHeaderOffset + 5].toInt() and 0xff)
        val payloadOffset = udpHeaderOffset + 8
        val payloadLength = minOf(udpLength - 8, packet.size - payloadOffset)
        if (payloadLength <= 0) return null
        return packet.copyOfRange(payloadOffset, payloadOffset + payloadLength)
    }

    private fun rewriteUdpResponse(originalPacket: ByteArray, dnsResponse: ByteArray): ByteArray {
        val version = (originalPacket[0].toInt() shr 4) and 0x0f
        return when (version) {
            4 -> rewriteUdpResponseIPv4(originalPacket, dnsResponse)
            6 -> rewriteUdpResponseIPv6(originalPacket, dnsResponse)
            else -> originalPacket
        }
    }

    private fun rewriteUdpResponseIPv4(originalPacket: ByteArray, dnsResponse: ByteArray): ByteArray {
        val headerLength = (originalPacket[0].toInt() and 0x0f) * 4
        val udpOffset = headerLength
        val srcIp = originalPacket.copyOfRange(12, 16)
        val dstIp = originalPacket.copyOfRange(16, 20)
        val srcPort = ((originalPacket[udpOffset].toInt() and 0xff) shl 8) or (originalPacket[udpOffset + 1].toInt() and 0xff)
        val dstPort = ((originalPacket[udpOffset + 2].toInt() and 0xff) shl 8) or (originalPacket[udpOffset + 3].toInt() and 0xff)

        val udpLen = 8 + dnsResponse.size
        val totalLen = headerLength + udpLen

        val buf = ByteBuffer.allocate(totalLen)
        buf.put(originalPacket, 0, headerLength)
        buf.put(dstIp)
        buf.put(srcIp)
        buf.putShort(dstPort.toShort())
        buf.putShort(srcPort.toShort())
        buf.putShort(udpLen.toShort())
        buf.putShort(0)
        buf.put(dnsResponse)

        val ipPacket = buf.array()
        ipPacket[2] = ((totalLen shr 8) and 0xff).toByte()
        ipPacket[3] = (totalLen and 0xff).toByte()
        ipPacket[8] = 0
        ipPacket[9] = 17

        var checksum = 0L
        for (i in 0 until headerLength step 2) {
            val w = ((ipPacket[i].toInt() and 0xff) shl 8) or (ipPacket[i + 1].toInt() and 0xff)
            checksum += w
        }
        while (checksum > 0xffff) checksum = (checksum and 0xffff) + (checksum shr 16)
        val csum = ((checksum.toInt() xor 0xffff) and 0xffff)
        ipPacket[10] = (csum shr 8).toByte()
        ipPacket[11] = (csum and 0xff).toByte()

        val udpCsumOffset = udpOffset + 6
        ipPacket[udpCsumOffset] = 0
        ipPacket[udpCsumOffset + 1] = 0

        return ipPacket
    }

    private fun rewriteUdpResponseIPv6(originalPacket: ByteArray, dnsResponse: ByteArray): ByteArray {
        if (originalPacket.size < 40) return originalPacket

        val srcIp = originalPacket.copyOfRange(8, 24)
        val dstIp = originalPacket.copyOfRange(24, 40)
        val srcPort = ((originalPacket[40].toInt() and 0xff) shl 8) or (originalPacket[41].toInt() and 0xff)
        val dstPort = ((originalPacket[42].toInt() and 0xff) shl 8) or (originalPacket[43].toInt() and 0xff)

        val udpLen = 8 + dnsResponse.size
        val ipv6PayloadLen = udpLen

        val buf = ByteBuffer.allocate(40 + udpLen)
        buf.put(0x60.toByte())
        buf.put(0x00)
        buf.putShort(ipv6PayloadLen.toShort())
        buf.put(17.toByte())
        buf.put(0x00)
        buf.put(dstIp)
        buf.put(srcIp)
        buf.putShort(dstPort.toShort())
        buf.putShort(srcPort.toShort())
        buf.putShort(udpLen.toShort())
        buf.putShort(0)
        buf.put(dnsResponse)

        return buf.array()
    }

    private fun loadBlockedWebsites() {
        serviceScope.launch {
            try {
                val list = repo.getBlocklist("blacklist", "websites").map { it.value.lowercase().trim() }
                val whitelist = repo.getBlocklist("whitelist", "websites").map { it.value.lowercase().trim() }
                synchronized(blockedWebsites) {
                    blockedWebsites.clear()
                    blockedWebsites.addAll(list)
                }
                synchronized(whitelistedWebsites) {
                    whitelistedWebsites.clear()
                    whitelistedWebsites.addAll(whitelist)
                }
                Timber.d("loaded ${list.size} blocked, ${whitelist.size} whitelisted websites")
            } catch (e: Exception) {
                Timber.e(e, "failed to load blocked websites")
            }
        }
    }

    private fun checkReloadWebsites() {
        val now = System.currentTimeMillis()
        if (now - lastWebsitesLoadTime > 15_000L) {
            lastWebsitesLoadTime = now
            loadBlockedWebsites()
        }
    }

    private fun createNotification(): Notification {
        return ForegroundServiceHelper.buildSilentNotification(
            context = this,
            title = "Guardian DNS Filter",
            text = "Active — filtering via CleanBrowsing Family Filter"
        )
    }
}
