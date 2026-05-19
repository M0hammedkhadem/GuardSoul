package com.agon.app.services

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import timber.log.Timber
import com.agon.app.data.GuardianRepository
import com.agon.app.engine.DnsResolver
import com.agon.app.engine.PacketForwarder
import com.agon.app.engine.safe.PornBlockerEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class GuardianVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var workerThread: Thread? = null
    private var packetForwarder: PacketForwarder? = null
    private var dnsResolver: DnsResolver? = null
    private val isRunning = AtomicBoolean(false)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var tunOutput: FileOutputStream? = null
    private val tunWriteLock = Any()

    companion object {
        private const val TAG = "GuardianVpnService"
        private const val VPN_MTU = 1500
        private val VPN_DNS_IP = InetAddress.getByName("10.0.1.1")
        private val VPN_CLIENT_IP = InetAddress.getByName("10.0.1.2")
        private val VPN_DNS_IP6 = InetAddress.getByName("fd00:1:2:3::1")
        private val VPN_CLIENT_IP6 = InetAddress.getByName("fd00:1:2:3::2")
        private const val DNS_PORT = 53
        private val IPV6_EXT_HDRS = setOf(0, 43, 44, 50, 51, 60)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun loadBlockedDomainsSync(): Set<String> {
        return try {
            val repo = GuardianRepository(this)
            val state = runBlocking(Dispatchers.IO) {
                withTimeout(5000) { repo.guardianStateFlow.first() }
            }
            val domains = PornBlockerEngine.ALL_PORN_DOMAINS + state.blacklistWebsites.toSet()
            Timber.tag(TAG).d("Loaded ${domains.size} blocked domains (${PornBlockerEngine.ALL_PORN_DOMAINS.size} porn + ${state.blacklistWebsites.size} custom)")
            domains
        } catch (e: Exception) {
            Timber.tag(TAG).e("Failed to load domains, using defaults", e)
            PornBlockerEngine.ALL_PORN_DOMAINS
        }
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        val blockedDomains = loadBlockedDomainsSync()
        val resolver = DnsResolver(blockedDomains)
        dnsResolver = resolver

        try {
            val builder = Builder()
            builder.setSession("Guardian DNS Filter")
            builder.setMtu(VPN_MTU)
            builder.addAddress(VPN_CLIENT_IP, 32)
            builder.addRoute("0.0.0.0", 0)
            builder.addDnsServer(VPN_DNS_IP)
            builder.addAddress(VPN_CLIENT_IP6, 126)
            builder.addRoute("::", 0)
            builder.addDnsServer(VPN_DNS_IP6)
            builder.setBlocking(true)

            vpnInterface = builder.establish()
            Timber.tag(TAG).d("VPN established: routing 0.0.0.0/0 through TUN")

            val tunInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val output = FileOutputStream(vpnInterface!!.fileDescriptor)
            tunOutput = output

            val forwarder = PacketForwarder(this) { packet ->
                synchronized(tunWriteLock) {
                    try {
                        output.write(packet)
                        output.flush()
                    } catch (e: Exception) {
                        if (isRunning.get()) Timber.tag(TAG).w("TUN write error: ${e.message}")
                    }
                }
            }
            packetForwarder = forwarder

            isRunning.set(true)
            startTunReader(tunInput, resolver, forwarder)
        } catch (e: Exception) {
            Timber.tag(TAG).e("Failed to start VPN", e)
        }
    }

    private fun startTunReader(
        tunInput: FileInputStream,
        resolver: DnsResolver,
        forwarder: PacketForwarder
    ) {
        workerThread = thread(name = "TUN-Reader", isDaemon = true) {
            val buffer = ByteArray(VPN_MTU)
            while (isRunning.get()) {
                try {
                    val length = tunInput.read(buffer)
                    if (length <= 0) continue
                    handlePacket(buffer, length, resolver, forwarder)
                } catch (e: Exception) {
                    if (isRunning.get()) Timber.tag(TAG).e("TUN read error", e)
                }
            }
        }
    }

    private fun handlePacket(
        packet: ByteArray,
        length: Int,
        resolver: DnsResolver,
        forwarder: PacketForwarder
    ) {
        if (length < 1) return
        val version = (packet[0].toInt() shr 4) and 0xF
        when (version) {
            4 -> handleIpv4(packet, length, resolver, forwarder)
            6 -> handleIpv6(packet, length, resolver, forwarder)
        }
    }

    private fun handleIpv4(
        packet: ByteArray,
        length: Int,
        resolver: DnsResolver,
        forwarder: PacketForwarder
    ) {
        if (length < 20) return
        val ihl = (packet[0].toInt() and 0xF) * 4
        if (ihl < 20 || ihl > length) return

        val totalLen = readShort(packet, 2)
        if (totalLen > length) return

        val protocol = packet[9].toInt() and 0xFF
        when (protocol) {
            6 -> handleTcp(packet, totalLen, forwarder, ihl)
            17 -> handleUdp(packet, totalLen, resolver, forwarder, ihl)
            else -> writeToTun(packet, length) // ICMP(1), GRE(47), etc. — passthrough
        }
    }

    private fun writeToTun(packet: ByteArray, length: Int) {
        synchronized(tunWriteLock) {
            try {
                tunOutput?.write(packet, 0, length)
                tunOutput?.flush()
            } catch (e: Exception) {
                if (isRunning.get()) Timber.tag(TAG).w("TUN write error: ${e.message}")
            }
        }
    }

    private fun handleIpv6(
        packet: ByteArray,
        length: Int,
        resolver: DnsResolver,
        forwarder: PacketForwarder
    ) {
        if (length < 40) return
        val payloadLen = readShort(packet, 4)
        if (payloadLen > length - 40) return

        var nextHeader = packet[6].toInt() and 0xFF
        var headerEnd = 40

        while (nextHeader in IPV6_EXT_HDRS) {
            if (headerEnd + 2 > length) return
            val extHdr = nextHeader
            nextHeader = packet[headerEnd].toInt() and 0xFF
            when (extHdr) {
                0, 60 -> { // Hop-by-Hop, Destination Options
                    val hdrLen = (packet[headerEnd + 1].toInt() and 0xFF) * 8 + 8
                    headerEnd += if (hdrLen > 0) hdrLen else 8
                }
                43 -> { // Routing
                    val hdrLen = (packet[headerEnd + 1].toInt() and 0xFF) * 8 + 8
                    headerEnd += if (hdrLen > 0) hdrLen else 8
                }
                44 -> headerEnd += 8 // Fragment (first fragment only)
                51 -> { // Authentication Header
                    val hdrLen = ((packet[headerEnd + 1].toInt() and 0xFF) + 2) * 4
                    headerEnd += hdrLen
                }
                50 -> return // ESP — can't parse encrypted
            }
        }

        when (nextHeader) {
            6 -> {
                val totalLen = headerEnd + (payloadLen - (headerEnd - 40))
                handleTcp6(packet, totalLen, forwarder, headerEnd)
            }
            17 -> handleUdp6(packet, payloadLen, resolver, forwarder, headerEnd)
            58 -> writeToTun(packet, length) // ICMPv6 passthrough — required for ND, SLAAC, RA
            else -> writeToTun(packet, length) // Forward unknown IPv6 protocols to TUN
        }
    }

    private fun handleTcp(
        packet: ByteArray,
        totalLen: Int,
        forwarder: PacketForwarder,
        ihl: Int
    ) {
        val flags = packet[ihl + 13].toInt() and 0xFF
        val isSyn = (flags and 0x02) != 0
        val isAck = (flags and 0x10) != 0

        if (isSyn && !isAck) {
            forwarder.handleTcpSyn(packet, totalLen)
        } else {
            forwarder.forwardTcpData(packet, totalLen)
        }
    }

    private fun handleTcp6(
        packet: ByteArray,
        totalLen: Int,
        forwarder: PacketForwarder,
        ipv6End: Int
    ) {
        val flags = packet[ipv6End + 13].toInt() and 0xFF
        val isSyn = (flags and 0x02) != 0
        val isAck = (flags and 0x10) != 0

        if (isSyn && !isAck) {
            forwarder.handleTcpSyn(packet, totalLen)
        } else {
            forwarder.forwardTcpData(packet, totalLen)
        }
    }

    private fun handleUdp(
        packet: ByteArray,
        totalLen: Int,
        resolver: DnsResolver,
        forwarder: PacketForwarder,
        ihl: Int
    ) {
        val destPort = readShort(packet, ihl + 2)

        if (destPort == DNS_PORT) {
            handleDnsPacket(packet, totalLen, resolver, ihl)
        } else {
            forwarder.forwardUdp(packet, totalLen)
        }
    }

    private fun handleUdp6(
        packet: ByteArray,
        payloadLen: Int,
        resolver: DnsResolver,
        forwarder: PacketForwarder,
        ipv6End: Int
    ) {
        val destPort = readShort(packet, ipv6End + 2)
        val udpLen = readShort(packet, ipv6End + 4)
        if (udpLen < 8 || ipv6End + udpLen > packet.size) return

        if (destPort == DNS_PORT) {
            handleDnsPacket6(packet, resolver, ipv6End, udpLen)
        } else {
            forwarder.forwardUdp6(packet, ipv6End, udpLen)
        }
    }

    private fun handleDnsPacket(packet: ByteArray, length: Int, resolver: DnsResolver, ihl: Int) {
        val dnsOffset = ihl + 8
        val dnsLength = length - dnsOffset
        if (dnsLength < 12) return

        val question = resolver.parseDnsQuestion(packet, dnsOffset, dnsLength) ?: return

        val srcIp = ipv4Str(packet, 12)
        val isBlocked = resolver.isDomainBlocked(question.domain)

        Timber.tag(TAG).d("DNS: ${question.domain} from $srcIp -> ${if (isBlocked) "BLOCKED" else "ALLOWED"}")

        val response = resolver.resolve(question, isBlocked)

        synchronized(tunWriteLock) {
            if (response != null) {
                writeDnsResponse4(packet, length, response, ihl)
            }
        }

        if (isBlocked) {
            scope.launch {
                try {
                    val repo = GuardianRepository(this@GuardianVpnService)
                    val state = repo.guardianStateFlow.first()
                    repo.updateBlocksCount(state.blocksCount + 1)
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleDnsPacket6(packet: ByteArray, resolver: DnsResolver, ipv6End: Int, udpLen: Int) {
        val dnsOffset = ipv6End + 8
        val dnsLength = udpLen - 8
        if (dnsLength < 12) return

        val question = resolver.parseDnsQuestion(packet, dnsOffset, dnsLength) ?: return

        val srcIp = ipv6Str(packet, 8)
        val isBlocked = resolver.isDomainBlocked(question.domain)

        Timber.tag(TAG).d("DNS6: ${question.domain} from $srcIp -> ${if (isBlocked) "BLOCKED" else "ALLOWED"}")

        val response = resolver.resolve(question, isBlocked)

        synchronized(tunWriteLock) {
            if (response != null) {
                writeDnsResponse6(packet, response, ipv6End, udpLen, dnsLength)
            }
        }

        if (isBlocked) {
            scope.launch {
                try {
                    val repo = GuardianRepository(this@GuardianVpnService)
                    val state = repo.guardianStateFlow.first()
                    repo.updateBlocksCount(state.blocksCount + 1)
                } catch (_: Exception) {}
            }
        }
    }

    private fun writeDnsResponse4(
        originalPacket: ByteArray,
        originalLength: Int,
        dnsResponse: ByteArray,
        ipHl: Int
    ) {
        val output = tunOutput ?: return
        try {
            val responseLen = ipHl + 8 + dnsResponse.size
            val responsePacket = ByteArray(responseLen)

            System.arraycopy(originalPacket, 0, responsePacket, 0, 12)
            System.arraycopy(originalPacket, 16, responsePacket, 12, 4)
            System.arraycopy(originalPacket, 12, responsePacket, 16, 4)

            responsePacket[8] = originalPacket[8]
            responsePacket[9] = 17

            writeShort(responsePacket, 2, responseLen)

            responsePacket[4] = (originalPacket[4].toInt() + 1 and 0xFF).toByte()
            responsePacket[5] = originalPacket[5]

            responsePacket[10] = 0
            responsePacket[11] = 0

            val udpLength = 8 + dnsResponse.size
            responsePacket[ipHl] = originalPacket[ipHl + 2]
            responsePacket[ipHl + 1] = originalPacket[ipHl + 3]
            responsePacket[ipHl + 2] = originalPacket[ipHl]
            responsePacket[ipHl + 3] = originalPacket[ipHl + 1]
            writeShort(responsePacket, ipHl + 4, udpLength)
            responsePacket[ipHl + 6] = 0
            responsePacket[ipHl + 7] = 0

            System.arraycopy(dnsResponse, 0, responsePacket, ipHl + 8, dnsResponse.size)

            val checksum = computeUdpChecksum4(responsePacket, ipHl, responseLen)
            if (checksum != 0) {
                writeShort(responsePacket, ipHl + 6, checksum)
            }

            writeIpChecksum(responsePacket, ipHl)

            output.write(responsePacket)
            output.flush()
        } catch (e: Exception) {
            Timber.tag(TAG).e("Failed to write IPv4 DNS response", e)
        }
    }

    private fun writeDnsResponse6(
        originalPacket: ByteArray,
        dnsResponse: ByteArray,
        ipv6End: Int,
        udpLen: Int,
        dnsLength: Int
    ) {
        val output = tunOutput ?: return
        try {
            val payloadLen = 8 + dnsResponse.size
            val totalLen = 40 + payloadLen
            val responsePacket = ByteArray(totalLen)

            // IPv6 fixed header (40 bytes)
            responsePacket[0] = (0x60).toByte() // Version=6, Traffic Class=0, Flow Label=0
            writeShort(responsePacket, 4, payloadLen)
            responsePacket[6] = 17.toByte() // Next Header = UDP
            responsePacket[7] = 64.toByte() // Hop Limit

            // Swap addresses: dst ← src (DNS server responds)
            System.arraycopy(originalPacket, 24, responsePacket, 8, 16) // src = original dst
            System.arraycopy(originalPacket, 8, responsePacket, 24, 16) // dst = original src

            // UDP header
            val udpSrcPort = readShort(originalPacket, ipv6End + 2) // dest port becomes src
            val udpDstPort = readShort(originalPacket, ipv6End)     // src port becomes dst
            writeShort(responsePacket, 40, udpSrcPort)
            writeShort(responsePacket, 42, udpDstPort)
            writeShort(responsePacket, 44, payloadLen)
            writeShort(responsePacket, 46, 0) // checksum placeholder

            System.arraycopy(dnsResponse, 0, responsePacket, 48, dnsResponse.size)

            // UDP checksum is MANDATORY for IPv6
            val checksum = computeUdpChecksum6(responsePacket)
            writeShort(responsePacket, 46, checksum)

            output.write(responsePacket)
            output.flush()
        } catch (e: Exception) {
            Timber.tag(TAG).e("Failed to write IPv6 DNS response", e)
        }
    }

    private fun computeUdpChecksum4(pkt: ByteArray, ipHl: Int, totalLen: Int): Int {
        val udpLen = totalLen - ipHl
        val pseudo = java.nio.ByteBuffer.allocate(12)
        pseudo.put(pkt, 12, 4)
        pseudo.put(pkt, 16, 4)
        pseudo.put(0.toByte())
        pseudo.put(17.toByte())
        pseudo.putShort(udpLen.toShort())

        var sum = 0L
        val ps = pseudo.array()
        for (i in ps.indices step 2) {
            sum += ((ps[i].toInt() shl 8) and 0xFFFF) or (if (i + 1 < ps.size) ps[i + 1].toInt() and 0xFF else 0)
        }
        for (i in ipHl until totalLen step 2) {
            sum += ((pkt[i].toInt() shl 8) and 0xFFFF) or (if (i + 1 < totalLen) pkt[i + 1].toInt() and 0xFF else 0)
        }
        sum = (sum and 0xFFFF) + (sum shr 16)
        sum = (sum and 0xFFFF) + (sum shr 16)
        return ((sum.toInt() xor 0xFFFF) and 0xFFFF)
    }

    private fun computeUdpChecksum6(pkt: ByteArray): Int {
        val udpOffset = 40
        val payloadLen = readShort(pkt, 4)
        val end = udpOffset + payloadLen

        val pseudo = java.nio.ByteBuffer.allocate(40)
        pseudo.put(pkt, 8, 16)  // source addr
        pseudo.put(pkt, 24, 16) // destination addr
        pseudo.putInt(payloadLen)
        pseudo.put(0.toByte())
        pseudo.put(0.toByte())
        pseudo.put(0.toByte())
        pseudo.put(17.toByte()) // next header = UDP

        var sum = 0L
        val ps = pseudo.array()
        for (i in ps.indices step 2) {
            sum += ((ps[i].toInt() shl 8) and 0xFFFF) or (if (i + 1 < ps.size) ps[i + 1].toInt() and 0xFF else 0)
        }
        for (i in udpOffset until end step 2) {
            sum += ((pkt[i].toInt() shl 8) and 0xFFFF) or (if (i + 1 < end) pkt[i + 1].toInt() and 0xFF else 0)
        }
        sum = (sum and 0xFFFF) + (sum shr 16)
        sum = (sum and 0xFFFF) + (sum shr 16)
        return ((sum.toInt() xor 0xFFFF) and 0xFFFF)
    }

    private fun writeIpChecksum(pkt: ByteArray, ipHl: Int) {
        pkt[10] = 0; pkt[11] = 0
        var sum = 0L
        for (i in 0 until ipHl step 2) {
            sum += ((pkt[i].toInt() shl 8) and 0xFFFF) or (pkt[i + 1].toInt() and 0xFF)
        }
        sum = (sum and 0xFFFF) + (sum shr 16)
        sum = (sum and 0xFFFF) + (sum shr 16)
        val c = (sum.toInt() xor 0xFFFF) and 0xFFFF
        pkt[10] = ((c shr 8) and 0xFF).toByte()
        pkt[11] = (c and 0xFF).toByte()
    }

    private fun readShort(pkt: ByteArray, off: Int): Int {
        return ((pkt[off].toInt() and 0xFF) shl 8) or (pkt[off + 1].toInt() and 0xFF)
    }

    private fun writeShort(pkt: ByteArray, off: Int, v: Int) {
        pkt[off] = ((v shr 8) and 0xFF).toByte()
        pkt[off + 1] = (v and 0xFF).toByte()
    }

    private fun ipv4Str(pkt: ByteArray, off: Int): String {
        return "${pkt[off].toInt() and 0xFF}.${pkt[off + 1].toInt() and 0xFF}.${pkt[off + 2].toInt() and 0xFF}.${pkt[off + 3].toInt() and 0xFF}"
    }

    private fun ipv6Str(pkt: ByteArray, off: Int): String {
        val parts = (0..7).map { i ->
            val hi = pkt[off + i * 2].toInt() and 0xFF
            val lo = pkt[off + i * 2 + 1].toInt() and 0xFF
            String.format("%x", (hi shl 8) or lo)
        }
        return parts.joinToString(":")
    }

    private fun stopVpn() {
        isRunning.set(false)
        workerThread?.interrupt()
        workerThread = null
        packetForwarder?.shutdown()
        packetForwarder = null
        dnsResolver?.clearCache()
        dnsResolver = null
        try {
            tunOutput?.close()
            tunOutput = null
        } catch (_: Exception) {}
        try {
            vpnInterface?.close()
            vpnInterface = null
            Timber.tag(TAG).d("VPN stopped")
        } catch (e: Exception) {
            Timber.tag(TAG).e("Failed to stop VPN", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        stopVpn()
    }

    override fun onBind(intent: Intent?) = null
}
