package com.agon.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.agon.app.MainActivity
import com.agon.app.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class PornBlockerVpnService : VpnService() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        const val NOTIFICATION_ID = 200
        const val CHANNEL_ID = "porn_blocker_vpn_channel"

        const val DNS_PRIMARY = "185.228.168.168"
        const val DNS_SECONDARY = "185.228.169.168"

        const val VPN_ADDRESS = "10.0.0.2"
        const val VPN_PREFIX = 24

        const val TAG = "PornBlockerVPN"

        fun isVpnActive(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
                return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
            @Suppress("DEPRECATION")
            val networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_VPN)
            @Suppress("DEPRECATION")
            return networkInfo?.isConnected == true
        }

        fun start(context: Context) {
            val intent = Intent(context, PornBlockerVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PornBlockerVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    private var upstreamSocket: DatagramSocket? = null

    @Volatile
    private var running = false

    private data class ClientInfo(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int
    )

    private val pendingQueries = ConcurrentHashMap<Int, ClientInfo>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        connectVpn()
        return START_STICKY
    }

    private fun connectVpn() {
        if (running) return

        try {
            val builder = Builder()
                .setSession("GuardSoul Porn Blocker")
                .setMtu(1500)
                .addAddress(VPN_ADDRESS, VPN_PREFIX)
                .addRoute(DNS_PRIMARY, 32)
                .addRoute(DNS_SECONDARY, 32)
                .addDnsServer(DNS_PRIMARY)
                .addDnsServer(DNS_SECONDARY)

            try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to create VPN interface")
                stopSelf()
                return
            }

            val fd = vpnInterface!!.fileDescriptor
            inputStream = FileInputStream(fd)
            outputStream = FileOutputStream(fd)

            upstreamSocket = DatagramSocket().apply {
                if (!protect(this)) {
                    Log.e(TAG, "protect() failed")
                }
                connect(InetAddress.getByName(DNS_PRIMARY), 53)
            }

            running = true

            thread(isDaemon = true, name = "TunReader") { tunReaderLoop() }
            thread(isDaemon = true, name = "UpstreamReader") { upstreamReaderLoop() }

            Log.i(TAG, "VPN started with CleanBrowsing Family")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            stopVpn()
        }
    }

    private fun tunReaderLoop() {
        val buffer = ByteArray(32767)
        try {
            while (running) {
                val len = inputStream!!.read(buffer)
                if (len <= 0) break
                processTunPacket(buffer, len)
            }
        } catch (e: Exception) {
            if (running) Log.e(TAG, "Error reading TUN", e)
        }
    }

    private fun processTunPacket(packet: ByteArray, len: Int) {
        val version = packet[0].toInt() ushr 4
        if (version != 4) return

        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (len < ihl + 8) return

        if ((packet[9].toInt() and 0xFF) != 17) return

        val udpOffset = ihl
        val srcPort = getShort(packet, udpOffset)
        val dstPort = getShort(packet, udpOffset + 2)

        if (dstPort != 53) return

        val udpLen = getShort(packet, udpOffset + 4)
        val dnsOffset = udpOffset + 8
        val dnsLen = udpLen - 8
        if (dnsLen <= 0 || len < dnsOffset + dnsLen) return

        val dnsPayload = packet.copyOfRange(dnsOffset, dnsOffset + dnsLen)
        val txnId = getShort(dnsPayload, 0)

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)

        pendingQueries[txnId] = ClientInfo(srcIp, dstIp, srcPort)

        upstreamSocket?.send(DatagramPacket(dnsPayload, dnsPayload.size))
    }

    private fun upstreamReaderLoop() {
        val buffer = ByteArray(4096)
        try {
            while (running) {
                val pkt = DatagramPacket(buffer, buffer.size)
                upstreamSocket?.receive(pkt)

                val dns = pkt.data.copyOfRange(pkt.offset, pkt.offset + pkt.length)
                val txnId = getShort(dns, 0)

                val client = pendingQueries.remove(txnId) ?: continue

                val response = buildResponsePacket(
                    client.srcIp,
                    client.dstIp,
                    client.srcPort,
                    dns
                )

                outputStream?.write(response)
            }
        } catch (e: Exception) {
            if (running) Log.e(TAG, "Error reading upstream", e)
        }
    }

    private fun buildResponsePacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        clientSrcPort: Int,
        dnsPayload: ByteArray
    ): ByteArray {
        val ipLen = 20
        val udpLen = 8 + dnsPayload.size
        val totalLen = ipLen + udpLen
        val packet = ByteArray(totalLen)

        packet[0] = ((4 shl 4) or 5).toByte()
        packet[1] = 0
        setShort(packet, 2, totalLen)
        packet[4] = 0; packet[5] = 0
        setShort(packet, 6, 0x4000)
        packet[8] = 64
        packet[9] = 17

        System.arraycopy(dstIp, 0, packet, 12, 4)
        System.arraycopy(srcIp, 0, packet, 16, 4)

        setIpChecksum(packet)

        setShort(packet, ipLen, 53)
        setShort(packet, ipLen + 2, clientSrcPort)
        setShort(packet, ipLen + 4, udpLen)

        System.arraycopy(dnsPayload, 0, packet, ipLen + 8, dnsPayload.size)

        return packet
    }

    private fun stopVpn() {
        if (!running && vpnInterface == null) return
        running = false

        runCatching { inputStream?.close() }
        runCatching { upstreamSocket?.close() }
        runCatching { vpnInterface?.close() }

        inputStream = null
        outputStream = null
        upstreamSocket = null
        vpnInterface = null

        pendingQueries.clear()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Porn Blocker VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GuardSoul Porn Blocker")
            .setContentText("DNS Filtering Active (CleanBrowsing Family)")
            .setSmallIcon(R.drawable.ic_vpn_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun getShort(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0xFF) shl 8) or
                (buf[offset + 1].toInt() and 0xFF)
    }

    private fun setShort(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value shr 8).toByte()
        buf[offset + 1] = value.toByte()
    }

    private fun setIpChecksum(packet: ByteArray) {
        setShort(packet, 10, checksum(packet, 0, 20))
    }

    private fun checksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        val end = offset + length
        var i = offset
        while (i < end - 1) {
            sum += getShort(buf, i)
            i += 2
        }
        if (length and 1 == 1) {
            sum += (buf[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFFL).toInt()
    }
}
