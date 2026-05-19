package com.agon.app.engine

import android.net.VpnService
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class PacketForwarder(private val vpnService: VpnService, private val tunWriter: (ByteArray) -> Unit) {
    companion object {
        private const val TAG = "PacketForwarder"
        private const val TCP_IDLE_SEC = 300
        private const val UDP_IDLE_SEC = 60
    }

    private data class TcpRelay(
        val socket: Socket,
        val outputStream: OutputStream,
        val inputStream: InputStream,
        val key: String,
        val srcIp: Int,
        val dstIp: Int,
        val srcPort: Int,
        val dstPort: Int,
        val clientIsn: Long = 0L,
        val serverIsn: Long = 0L,
        var bytesFromClient: Long = 0L,
        var lastActive: Long = System.currentTimeMillis()
    )

    private data class UdpRelay(
        val socket: DatagramSocket,
        val key: String,
        val remoteIp: InetAddress,
        val remotePort: Int,
        val srcIp: Int,
        val dstIp: Int,
        val srcPort: Int,
        val dstPort: Int,
        var lastActive: Long = System.currentTimeMillis()
    )

    private val tcpRelays = ConcurrentHashMap<String, TcpRelay>()
    private val udpRelays = ConcurrentHashMap<String, UdpRelay>()
    private val pool = Executors.newCachedThreadPool()
    private val ipId = AtomicInteger(1)

    fun shutdown() {
        pool.shutdownNow()
        tcpRelays.values.forEach { try { it.socket.close() } catch (_: Exception) {} }
        tcpRelays.clear()
        udpRelays.values.forEach { try { it.socket.close() } catch (_: Exception) {} }
        udpRelays.clear()
    }

    fun forwardUdp6(packet: ByteArray, ipv6End: Int, udpLen: Int) {
        if (ipv6End + udpLen > packet.size) return
        val payloadLen = udpLen - 8
        if (payloadLen <= 0) return

        val srcAddr = extractIpv6Addr(packet, 8)
        val dstAddr = extractIpv6Addr(packet, 24)
        val srcPort = readShort(packet, ipv6End)
        val dstPort = readShort(packet, ipv6End + 2)

        val payload = ByteArray(payloadLen)
        System.arraycopy(packet, ipv6End + 8, payload, 0, payloadLen)

        val fwdKey = "${srcAddr.hostAddress!!}:$srcPort-${dstAddr.hostAddress!!}:$dstPort"
        val revKey = "${dstAddr.hostAddress!!}:$dstPort-${srcAddr.hostAddress!!}:$srcPort"
        val relay = udpRelays[fwdKey] ?: udpRelays[revKey]

        if (relay != null) {
            try {
                relay.socket.send(DatagramPacket(payload, payload.size, relay.remoteIp, relay.remotePort))
                relay.lastActive = System.currentTimeMillis()
            } catch (e: Exception) {
                udpRelays.remove(fwdKey)
                udpRelays.remove(revKey)
                try { relay.socket.close() } catch (_: Exception) {}
            }
            return
        }

        try {
            val socket = DatagramSocket()
            vpnService.protect(socket)
            socket.soTimeout = 30000
            socket.send(DatagramPacket(payload, payload.size, dstAddr, dstPort))

            val udpRelay = UdpRelay(
                socket = socket, key = fwdKey,
                remoteIp = dstAddr, remotePort = dstPort,
                srcIp = 0, dstIp = 0,
                srcPort = srcPort, dstPort = dstPort
            )
            udpRelays[fwdKey] = udpRelay

            pool.submit {
                try {
                    val buf = ByteArray(2048)
                    while (!socket.isClosed) {
                        val recv = DatagramPacket(buf, buf.size)
                        socket.receive(recv)
                        val data = ByteArray(recv.length).also { System.arraycopy(recv.data, 0, it, 0, recv.length) }
                        tunWriter(buildUdpResponse6(srcAddr, dstAddr, srcPort, dstPort, recv.port, recv.address, data))
                        udpRelay.lastActive = System.currentTimeMillis()
                    }
                } catch (_: java.net.SocketTimeoutException) {}
                catch (_: Exception) {}
                finally {
                    udpRelays.remove(fwdKey)
                    udpRelays.remove(revKey)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("UDP6 fwd fail: ${e.message}")
        }
    }

    fun forwardUdp(packet: ByteArray, length: Int) {
        val ipHl = (packet[0].toInt() and 0x0F) * 4
        if (ipHl + 8 > length) return

        val srcIp = readInt(packet, 12)
        val dstIp = readInt(packet, 16)
        val srcPort = readShort(packet, ipHl)
        val dstPort = readShort(packet, ipHl + 2)
        val udpLen = readShort(packet, ipHl + 4)
        if (udpLen < 8 || ipHl + udpLen > length) return

        val payloadLen = udpLen - 8
        val payload = ByteArray(payloadLen)
        System.arraycopy(packet, ipHl + 8, payload, 0, payloadLen)

        val fwdKey = "$srcIp:$srcPort-$dstIp:$dstPort"
        val revKey = "$dstIp:$dstPort-$srcIp:$srcPort"
        val relay = udpRelays[fwdKey] ?: udpRelays[revKey]

        if (relay != null) {
            try {
                relay.socket.send(DatagramPacket(payload, payload.size, relay.remoteIp, relay.remotePort))
                relay.lastActive = System.currentTimeMillis()
            } catch (e: Exception) {
                udpRelays.remove(fwdKey)
                udpRelays.remove(revKey)
                try { relay.socket.close() } catch (_: Exception) {}
            }
            return
        }

        try {
            val socket = DatagramSocket()
            vpnService.protect(socket)
            socket.soTimeout = 30000
            socket.send(DatagramPacket(payload, payload.size, intToAddr(dstIp), dstPort))

            val udpRelay = UdpRelay(
                socket = socket, key = fwdKey,
                remoteIp = intToAddr(dstIp), remotePort = dstPort,
                srcIp = srcIp, dstIp = dstIp,
                srcPort = srcPort, dstPort = dstPort
            )
            udpRelays[fwdKey] = udpRelay

            pool.submit {
                try {
                    val buf = ByteArray(2048)
                    while (!socket.isClosed) {
                        val recv = DatagramPacket(buf, buf.size)
                        socket.receive(recv)
                        val data = ByteArray(recv.length).also { System.arraycopy(recv.data, 0, it, 0, recv.length) }
                        tunWriter(buildUdpResponse(srcIp, dstIp, srcPort, dstPort, recv.port, intFromAddr(recv.address), data))
                        udpRelay.lastActive = System.currentTimeMillis()
                    }
                } catch (_: java.net.SocketTimeoutException) {}
                catch (_: Exception) {}
                finally {
                    udpRelays.remove(fwdKey)
                    udpRelays.remove(revKey)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("UDP fwd fail: ${e.message}")
        }
    }

    fun handleTcpSyn(packet: ByteArray, length: Int) {
        val ipHl = (packet[0].toInt() and 0x0F) * 4
        if (ipHl + 20 > length) return

        val srcIp = readInt(packet, 12)
        val dstIp = readInt(packet, 16)
        val srcPort = readShort(packet, ipHl)
        val dstPort = readShort(packet, ipHl + 2)
        val tcpHl = ((packet[ipHl + 12].toInt() and 0xF0) shr 2)
        val flags = packet[ipHl + 13].toInt() and 0xFF
        val isSyn = (flags and 0x02) != 0
        val isAck = (flags and 0x10) != 0

        if (!isSyn || isAck) return

        val key = "$srcIp:$srcPort-$dstIp:$dstPort"
        val serverIsn = ((System.nanoTime() and 0xFFFFFFFF) + (Math.random() * 10000).toLong()) and 0xFFFFFFFFL
        val clientIsn = readTcpSeq(packet, ipHl + 4)

        try {
            val socket = Socket()
            vpnService.protect(socket)
            socket.connect(InetSocketAddress(intToAddr(dstIp), dstPort), 10000)
            socket.soTimeout = 0
            socket.setTcpNoDelay(true)

            val relay = TcpRelay(
                socket = socket,
                outputStream = socket.getOutputStream(),
                inputStream = socket.getInputStream(),
                key = key, srcIp = srcIp, dstIp = dstIp,
                srcPort = srcPort, dstPort = dstPort,
                clientIsn = clientIsn, serverIsn = serverIsn
            )
            tcpRelays[key] = relay

            tunWriter(buildTcpSynAck(
                srcIp, dstIp, srcPort, dstPort,
                serverIsn, clientIsn + 1
            ))

            if (ipHl + tcpHl < length) {
                val appDataLen = length - ipHl - tcpHl
                val appData = ByteArray(appDataLen)
                System.arraycopy(packet, ipHl + tcpHl, appData, 0, appDataLen)
                relay.outputStream.write(appData)
            }

            pool.submit { tcpReaderLoop(relay, key) }
        } catch (e: Exception) {
            Timber.tag(TAG).w("TCP connect fail to ${intToIp(dstIp)}:$dstPort: ${e.message}")
        }
    }

    fun forwardTcpData(packet: ByteArray, length: Int) {
        val ipHl = (packet[0].toInt() and 0x0F) * 4
        if (ipHl + 20 > length) return

        val srcIp = readInt(packet, 12)
        val dstIp = readInt(packet, 16)
        val srcPort = readShort(packet, ipHl)
        val dstPort = readShort(packet, ipHl + 2)
        val tcpHl = ((packet[ipHl + 12].toInt() and 0xF0) shr 2)
        val flags = packet[ipHl + 13].toInt() and 0xFF
        val payloadLen = length - ipHl - tcpHl
        val isRst = (flags and 0x04) != 0
        val isFin = (flags and 0x01) != 0

        val key = "$srcIp:$srcPort-$dstIp:$dstPort"
        val relay = tcpRelays[key] ?: tcpRelays["$dstIp:$dstPort-$srcIp:$srcPort"]

        if (relay == null) {
            if (isRst) return
            handleTcpSyn(packet, length)
            return
        }

        if (isRst) {
            cleanupTcp(relay, key)
            return
        }

        if (payloadLen > 0) {
            try {
                val appData = ByteArray(payloadLen)
                System.arraycopy(packet, ipHl + tcpHl, appData, 0, payloadLen)
                relay.outputStream.write(appData)
                relay.outputStream.flush()
                relay.bytesFromClient += payloadLen
                relay.lastActive = System.currentTimeMillis()
            } catch (e: Exception) {
                cleanupTcp(relay, key)
            }
        }

        if (isFin) {
            try { relay.socket.shutdownOutput() } catch (_: Exception) {}
        }
    }

    private fun tcpReaderLoop(relay: TcpRelay, key: String) {
        try {
            val buf = ByteArray(32768)
            var seq = (relay.serverIsn + 1) and 0xFFFFFFFFL

            while (true) {
                val n = relay.inputStream.read(buf)
                if (n <= 0) break

                val data = if (n == buf.size) buf else buf.copyOf(n)
                val ack = (relay.clientIsn + 1 + relay.bytesFromClient) and 0xFFFFFFFFL
                tunWriter(buildTcpData(
                    relay.dstIp, relay.srcIp, relay.dstPort, relay.srcPort,
                    seq, ack, data
                ))
                seq = (seq + n) and 0xFFFFFFFFL
                relay.lastActive = System.currentTimeMillis()
            }
        } catch (e: java.net.SocketException) {
            if (e.message?.contains("closed") != true) {
                Timber.tag(TAG).w("TCP reader: ${e.message}")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("TCP reader error: ${e.message}")
        } finally {
            cleanupTcp(relay, key)
        }
    }

    private fun cleanupTcp(relay: TcpRelay, key: String) {
        tcpRelays.remove(key)
        tcpRelays.remove("${relay.dstIp}:${relay.dstPort}-${relay.srcIp}:${relay.srcPort}")
        try { relay.socket.close() } catch (_: Exception) {}
    }

    private fun buildTcpSynAck(srcIp: Int, dstIp: Int, srcPort: Int, dstPort: Int, seq: Long, ack: Long): ByteArray {
        val ipHl = 20
        val tcpHl = 20
        val totalLen = ipHl + tcpHl
        val pkt = ByteArray(totalLen)

        pkt[0] = 0x45
        pkt[1] = 0
        writeShort(pkt, 2, totalLen)

        val id = ipId.getAndIncrement() and 0xFFFF
        writeShort(pkt, 4, id)

        pkt[6] = 0; pkt[7] = 0
        pkt[8] = 64; pkt[9] = 6

        writeInt(pkt, 12, dstIp)
        writeInt(pkt, 16, srcIp)

        writeShort(pkt, ipHl, dstPort)
        writeShort(pkt, ipHl + 2, srcPort)
        writeTcpSeq(pkt, ipHl + 4, seq)
        writeTcpSeq(pkt, ipHl + 8, ack)
        pkt[ipHl + 12] = (0x50).toByte()
        pkt[ipHl + 13] = (0x12).toByte()
        writeShort(pkt, ipHl + 14, 65535)
        writeShort(pkt, ipHl + 16, 0)
        writeShort(pkt, ipHl + 18, 0)

        writeIpChecksum(pkt, ipHl)
        writeTcpChecksum(pkt, ipHl, totalLen, dstIp, srcIp)
        return pkt
    }

    private fun buildTcpData(srcIp: Int, dstIp: Int, srcPort: Int, dstPort: Int, seq: Long, ack: Long, data: ByteArray): ByteArray {
        val ipHl = 20
        val tcpHl = 20
        val totalLen = ipHl + tcpHl + data.size
        val pkt = ByteArray(totalLen)

        pkt[0] = 0x45
        pkt[1] = 0
        writeShort(pkt, 2, totalLen)

        val id = ipId.getAndIncrement() and 0xFFFF
        writeShort(pkt, 4, id)

        pkt[6] = 0; pkt[7] = 0
        pkt[8] = 64; pkt[9] = 6

        writeInt(pkt, 12, srcIp)
        writeInt(pkt, 16, dstIp)

        writeShort(pkt, ipHl, srcPort)
        writeShort(pkt, ipHl + 2, dstPort)
        writeTcpSeq(pkt, ipHl + 4, seq)
        writeTcpSeq(pkt, ipHl + 8, ack)
        pkt[ipHl + 12] = (0x50).toByte()
        pkt[ipHl + 13] = (0x18).toByte()
        writeShort(pkt, ipHl + 14, 65535)
        writeShort(pkt, ipHl + 16, 0)
        writeShort(pkt, ipHl + 18, 0)

        System.arraycopy(data, 0, pkt, ipHl + tcpHl, data.size)

        writeIpChecksum(pkt, ipHl)
        writeTcpChecksum(pkt, ipHl, totalLen, srcIp, dstIp)
        return pkt
    }

    private fun buildUdpResponse6(clientAddr: InetAddress, serverAddr: InetAddress, clientPort: Int, serverPort: Int, realSrcPort: Int, realSrcAddr: InetAddress, data: ByteArray): ByteArray {
        val payloadLen = 8 + data.size
        val totalLen = 40 + payloadLen
        val pkt = ByteArray(totalLen)

        pkt[0] = 0x60.toByte()
        writeShort(pkt, 4, payloadLen)
        pkt[6] = 17.toByte()
        pkt[7] = 64.toByte()

        val srcBytes = realSrcAddr.address
        val dstBytes = clientAddr.address
        System.arraycopy(srcBytes, 0, pkt, 8, 16)
        System.arraycopy(dstBytes, 0, pkt, 24, 16)

        writeShort(pkt, 40, realSrcPort)
        writeShort(pkt, 42, clientPort)
        writeShort(pkt, 44, payloadLen)
        writeShort(pkt, 46, 0)

        System.arraycopy(data, 0, pkt, 48, data.size)

        val checksum = computeUdpChecksum6(pkt)
        writeShort(pkt, 46, checksum)
        return pkt
    }

    private fun computeUdpChecksum6(pkt: ByteArray): Int {
        val payloadLen = readShort(pkt, 4)
        val end = 40 + payloadLen
        var sum = 0L

        val pseudo = java.nio.ByteBuffer.allocate(40)
        pseudo.put(pkt, 8, 16)
        pseudo.put(pkt, 24, 16)
        pseudo.putInt(payloadLen)
        pseudo.put(0.toByte()); pseudo.put(0.toByte()); pseudo.put(0.toByte())
        pseudo.put(17.toByte())
        val ps = pseudo.array()
        for (i in ps.indices step 2) {
            sum += ((ps[i].toInt() shl 8) and 0xFFFF) or (if (i + 1 < ps.size) ps[i + 1].toInt() and 0xFF else 0)
        }
        for (i in 40 until end step 2) {
            sum += ((pkt[i].toInt() shl 8) and 0xFFFF) or (if (i + 1 < end) pkt[i + 1].toInt() and 0xFF else 0)
        }
        sum = (sum and 0xFFFF) + (sum shr 16)
        sum = (sum and 0xFFFF) + (sum shr 16)
        return ((sum.toInt() xor 0xFFFF) and 0xFFFF)
    }

    private fun extractIpv6Addr(pkt: ByteArray, off: Int): InetAddress {
        val addr = ByteArray(16)
        System.arraycopy(pkt, off, addr, 0, 16)
        return InetAddress.getByAddress(addr)
    }

    private fun buildUdpResponse(clientIp: Int, serverIp: Int, clientPort: Int, serverPort: Int, realSrcPort: Int, realSrcIp: Int, data: ByteArray): ByteArray {
        val ipHl = 20
        val udpLen = 8 + data.size
        val totalLen = ipHl + udpLen
        val pkt = ByteArray(totalLen)

        pkt[0] = 0x45
        pkt[1] = 0
        writeShort(pkt, 2, totalLen)
        writeShort(pkt, 4, ipId.getAndIncrement() and 0xFFFF)
        pkt[6] = 0; pkt[7] = 0
        pkt[8] = 64; pkt[9] = 17

        writeInt(pkt, 12, serverIp)
        writeInt(pkt, 16, clientIp)
        writeShort(pkt, ipHl, serverPort)
        writeShort(pkt, ipHl + 2, clientPort)
        writeShort(pkt, ipHl + 4, udpLen)
        writeShort(pkt, ipHl + 6, 0)

        System.arraycopy(data, 0, pkt, ipHl + 8, data.size)

        writeIpChecksum(pkt, ipHl)
        return pkt
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

    private fun writeTcpChecksum(pkt: ByteArray, ipHl: Int, totalLen: Int, srcIp: Int, dstIp: Int) {
        val tcpLen = totalLen - ipHl
        pkt[ipHl + 16] = 0; pkt[ipHl + 17] = 0
        var sum = 0L

        val p = java.nio.ByteBuffer.allocate(12)
        p.putInt(srcIp); p.putInt(dstIp); p.put(0); p.put(6); p.putShort(tcpLen.toShort())
        val ps = p.array()
        for (i in ps.indices step 2) {
            sum += ((ps[i].toInt() shl 8) and 0xFFFF) or (if (i + 1 < ps.size) ps[i + 1].toInt() and 0xFF else 0)
        }
        for (i in ipHl until totalLen step 2) {
            sum += ((pkt[i].toInt() shl 8) and 0xFFFF) or (if (i + 1 < totalLen) pkt[i + 1].toInt() and 0xFF else 0)
        }
        sum = (sum and 0xFFFF) + (sum shr 16)
        sum = (sum and 0xFFFF) + (sum shr 16)
        val c = (sum.toInt() xor 0xFFFF) and 0xFFFF
        if (c != 0) {
            pkt[ipHl + 16] = ((c shr 8) and 0xFF).toByte()
            pkt[ipHl + 17] = (c and 0xFF).toByte()
        }
    }

    private fun writeShort(pkt: ByteArray, off: Int, v: Int) {
        pkt[off] = ((v shr 8) and 0xFF).toByte()
        pkt[off + 1] = (v and 0xFF).toByte()
    }

    private fun readShort(pkt: ByteArray, off: Int): Int {
        return ((pkt[off].toInt() and 0xFF) shl 8) or (pkt[off + 1].toInt() and 0xFF)
    }

    private fun readInt(pkt: ByteArray, off: Int): Int {
        return ((pkt[off].toInt() and 0xFF) shl 24) or ((pkt[off + 1].toInt() and 0xFF) shl 16) or
                ((pkt[off + 2].toInt() and 0xFF) shl 8) or (pkt[off + 3].toInt() and 0xFF)
    }

    private fun writeInt(pkt: ByteArray, off: Int, v: Int) {
        pkt[off] = ((v shr 24) and 0xFF).toByte()
        pkt[off + 1] = ((v shr 16) and 0xFF).toByte()
        pkt[off + 2] = ((v shr 8) and 0xFF).toByte()
        pkt[off + 3] = (v and 0xFF).toByte()
    }

    private fun readTcpSeq(pkt: ByteArray, off: Int): Long {
        return ((pkt[off].toLong() and 0xFF) shl 24) or ((pkt[off + 1].toLong() and 0xFF) shl 16) or
                ((pkt[off + 2].toLong() and 0xFF) shl 8) or (pkt[off + 3].toLong() and 0xFF)
    }

    private fun writeTcpSeq(pkt: ByteArray, off: Int, v: Long) {
        pkt[off] = ((v shr 24) and 0xFF).toByte()
        pkt[off + 1] = ((v shr 16) and 0xFF).toByte()
        pkt[off + 2] = ((v shr 8) and 0xFF).toByte()
        pkt[off + 3] = (v and 0xFF).toByte()
    }

    private fun intToAddr(v: Int): InetAddress = InetAddress.getByAddress(byteArrayOf(
        ((v shr 24) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte()
    ))

    private fun intFromAddr(a: InetAddress): Int {
        val b = a.address; return ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or
                ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
    }

    private fun intToIp(v: Int) = "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"
}
