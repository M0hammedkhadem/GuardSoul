package com.agon.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.agon.app.data.repository.AppRepository
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
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

class DnsVpnService : VpnService() {

    companion object {
        private const val NOTIFICATION_ID = 4001
        private const val NEXTDNS_DNS_1 = "45.90.28.0"
        private const val NEXTDNS_DNS_2 = "45.90.29.0"
        private const val VPN_MTU = 1500
        private const val DNS_PORT = 53

        private val SAFESearch_HOSTS = mapOf(
            "forcesafesearch.google.com" to "185.228.168.168",
            "safesearch.xfinity.com" to "185.228.168.168",
            "safesearch.googleapis.com" to "185.228.168.168",
            "restrict.youtube.com" to "185.228.168.168",
            "restrictmoderate.youtube.com" to "185.228.168.168",
            "strict.youtube.com" to "185.228.168.168",
            "safe.duckduckgo.com" to "185.228.168.168",
            "safeearch.duckduckgo.com" to "185.228.168.168",
            "www.bing.com" to "185.228.168.168"
        )

        fun start(context: Context) {
            val intent = Intent(context, DnsVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DnsVpnService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var nextDnsProfileId: String = ""
    private val blockedWebsites = mutableSetOf<String>()
    private val whitelistedWebsites = mutableSetOf<String>()
    private var lastWebsitesLoadTime = 0L

    private val repo: AppRepository by lazy {
        (applicationContext as GuardianApp).repository
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("DnsVpnService created")
        serviceScope.launch {
            try {
                nextDnsProfileId = repo.getAppSettings().getNextDnsProfileId()
            } catch (_: Exception) {}
        }
        loadBlockedWebsites()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            val settings = repo.getAppSettings()
            val shieldActive = try { settings.isShieldActive() } catch (_: Exception) { false }
            val shouldRun = try { settings.isPornBlockerActive() } catch (_: Exception) { false }
            if (!shouldRun || !shieldActive) {
                Timber.d("DnsVpnService: porn blocker inactive or shield off, not starting")
                stopSelf()
                return@launch
            }
            startForeground(NOTIFICATION_ID, createNotification())
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
            try {
                repo.getAppSettings().setPornBlocker(false)
            } catch (_: Exception) {}
        }
        Timber.d("DnsVpnService revoked, porn blocker disabled")
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnJob?.cancel()
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        serviceScope.cancel()
        Timber.d("DnsVpnService destroyed")
    }

    private fun establishVpn() {
        val builder = Builder()
            .setSession("Guardian DNS Filter")
            .setMtu(VPN_MTU)
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(NEXTDNS_DNS_1)
            .addDnsServer(NEXTDNS_DNS_2)

        try {
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Timber.w("DnsVpnService: establish returned null")
                return
            }
            Timber.d("DnsVpnService: VPN established")
            startTrafficForwarding()
        } catch (e: Exception) {
            Timber.e(e, "DnsVpnService: failed to establish VPN")
        }
    }

    private fun startTrafficForwarding() {
        vpnJob?.cancel()
        vpnJob = serviceScope.launch {
            val input = FileInputStream(vpnInterface!!.fileDescriptor)
            val output = FileOutputStream(vpnInterface!!.fileDescriptor)
            val buffer = ByteBuffer.allocate(VPN_MTU)

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

                    if (isDnsQuery(packet)) {
                        checkReloadWebsites()
                        val dnsPayload = extractUdpPayload(packet)
                        val domain = dnsPayload?.let { parseDnsQueryDomain(it) }
                        
                        if (domain != null && isDomainBlocked(domain)) {
                            Timber.d("DnsVpnService: Blocked website: $domain")
                            serviceScope.launch {
                                try {
                                    repo.recordBlock(domain, domain, "dns_filter")
                                } catch (_: Exception) {}
                            }
                            val blockedDnsPayload = buildNxDomainResponse(dnsPayload)
                            val blockedPacket = rewriteUdpResponse(packet, blockedDnsPayload)
                            output.channel.write(ByteBuffer.wrap(blockedPacket))
                            continue
                        }

                        if (domain != null && isSafeSearchForced(domain)) {
                            val safeSearchIp = SAFESearch_HOSTS[domain] ?: SAFESearch_HOSTS["forcesafesearch.google.com"]!!
                            val aRecordPayload = buildARecordResponse(dnsPayload, safeSearchIp)
                            val responsePacket = rewriteUdpResponse(packet, aRecordPayload)
                            output.channel.write(ByteBuffer.wrap(responsePacket))
                            continue
                        }

                        val response = forwardDnsQuery(packet)
                        if (response != null) {
                            output.channel.write(ByteBuffer.wrap(response))
                            continue
                        }
                    }

                    output.channel.write(ByteBuffer.wrap(packet))
                }
            } catch (e: Exception) {
                if (isActive) {
                    Timber.w(e, "DnsVpnService: traffic forwarding stopped")
                }
            }
        }
    }

    private fun isDnsQuery(packet: ByteArray): Boolean {
        if (packet.size < 20) return false
        val version = (packet[0].toInt() shr 4) and 0x0f
        if (version != 4) return false
        val headerLength = (packet[0].toInt() and 0x0f) * 4
        if (packet.size < headerLength + 2) return false
        val protocol = packet[9].toInt() and 0xff
        if (protocol != 17) return false
        val srcPort = ((packet[headerLength].toInt() and 0xff) shl 8) or (packet[headerLength + 1].toInt() and 0xff)
        val dstPort = ((packet[headerLength + 2].toInt() and 0xff) shl 8) or (packet[headerLength + 3].toInt() and 0xff)
        return srcPort == DNS_PORT || dstPort == DNS_PORT
    }

    private fun parseDnsQueryDomain(dnsPayload: ByteArray): String? {
        if (dnsPayload.size < 12) return null
        val qdCount = ((dnsPayload[4].toInt() and 0xff) shl 8) or (dnsPayload[5].toInt() and 0xff)
        if (qdCount <= 0) return null
        
        val domain = java.lang.StringBuilder()
        var offset = 12
        while (offset < dnsPayload.size) {
            val labelLen = dnsPayload[offset].toInt() and 0xff
            if (labelLen == 0) {
                break
            }
            if (offset + 1 + labelLen > dnsPayload.size) {
                return null // Malformed
            }
            if (domain.isNotEmpty()) {
                domain.append(".")
            }
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
                if (cleanDomain.endsWith(".$blocked")) return true
            }
        }
        return false
    }

    private fun isSafeSearchForced(domain: String): Boolean {
        return SAFESearch_HOSTS.containsKey(domain.lowercase())
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
            response[2] = 0x81.toByte() // QR = 1, Opcode = 0, AA = 0, TC = 0, RD = 1
            response[3] = 0x83.toByte() // RA = 1, RCODE = 3 (NXDOMAIN)
        }
        return response
    }

    private fun forwardDnsQuery(packet: ByteArray): ByteArray? {
        return try {
            val dnsPayload = extractUdpPayload(packet) ?: return null
            val urlString = if (nextDnsProfileId.isNotBlank()) {
                "https://dns.nextdns.io/$nextDnsProfileId"
            } else {
                "https://family.cloudflare-dns.com/dns-query" // Fallback safe upstream
            }
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/dns-message")
            conn.setRequestProperty("Accept", "application/dns-message")
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val responseBytes = try {
                conn.outputStream.write(dnsPayload)
                conn.outputStream.flush()
                if (conn.responseCode == 200) conn.inputStream.readBytes() else null
            } finally {
                conn.disconnect()
            }
            if (responseBytes != null) rewriteUdpResponse(packet, responseBytes) else null
        } catch (e: Exception) {
            Timber.w(e, "DnsVpnService: DoH query failed")
            null
        }
    }

    private fun extractUdpPayload(packet: ByteArray): ByteArray? {
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

    private fun rewriteUdpResponse(originalPacket: ByteArray, dnsResponse: ByteArray): ByteArray {
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
        ipPacket[0] = (0x45).toByte()
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
                Timber.d("DnsVpnService: initialized with ${list.size} blocked, ${whitelist.size} whitelisted websites")
            } catch (e: Exception) {
                Timber.e(e, "DnsVpnService: failed to load blocked websites")
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
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (nextDnsProfileId.isNotBlank()) {
            "Filtering via NextDNS profile"
        } else {
            "Filtering via Secure Safe DNS"
        }

        return NotificationCompat.Builder(this, AppNotificationChannels.APP_BLOCKER)
            .setContentTitle("Guardian DNS Filter")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
