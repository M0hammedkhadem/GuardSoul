package com.agon.app.engine

import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class DnsResolver(
    private val blockedDomains: Set<String>,
    private val cacheMaxSize: Int = 256
) {
    companion object {
        private const val TAG = "DnsResolver"
        private const val CLOUDFLARE_PRIMARY = "1.1.1.3"
        private const val CLOUDFLARE_SECONDARY = "1.0.0.3"
        private const val DNS_PORT = 53
        private const val CACHE_TTL_MS = 30_000L
    }

    private data class CachedEntry(
        val response: ByteArray,
        val expiresAt: Long
    )

    private val dnsCache = object : java.util.LinkedHashMap<String, CachedEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedEntry>): Boolean {
            return size > cacheMaxSize
        }
    }

    data class DnsQuestion(
        val domain: String,
        val queryId: Int,
        val rawQuery: ByteArray,
        val dnsOffset: Int,
        val dnsLength: Int
    )

    fun parseDnsQuestion(packet: ByteArray, offset: Int, length: Int): DnsQuestion? {
        var pos = offset + 12
        val end = offset + length
        if (pos >= end) return null

        val queryId = ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
        val parts = mutableListOf<String>()

        while (pos < end) {
            val len = packet[pos].toInt() and 0xFF

            if (len == 0) {
                pos++
                break
            }

            if ((len and 0xC0) == 0xC0) {
                val pointerOffset = ((len and 0x3F) shl 8) or (packet[pos + 1].toInt() and 0xFF)
                var ptr = pointerOffset
                while (ptr < end) {
                    val l = packet[ptr].toInt() and 0xFF
                    if (l == 0) break
                    if (l > 63) {
                        ptr = ptr + 2
                        break
                    }
                    ptr++
                    parts.add(String(packet, ptr, l, Charsets.UTF_8))
                    ptr += l
                }
                pos += 2
                break
            }

            if (len > 63 || pos + len >= end) {
                pos += (len and 0x3F) + 1
                continue
            }
            pos++
            parts.add(String(packet, pos, len, Charsets.UTF_8))
            pos += len
        }

        val domain = if (parts.isEmpty()) null else parts.joinToString(".").lowercase()
        return domain?.let {
            DnsQuestion(
                domain = it,
                queryId = queryId,
                rawQuery = packet.copyOfRange(offset, offset + length),
                dnsOffset = offset,
                dnsLength = length
            )
        }
    }

    fun resolve(question: DnsQuestion, isBlocked: Boolean): ByteArray? {
        val cacheKey = question.domain

        if (!isBlocked) {
            val cached = dnsCache[cacheKey]
            if (cached != null && System.currentTimeMillis() < cached.expiresAt) {
                val response = cached.response
                return rewriteQueryId(response, question.queryId)
            }
        }

        if (isBlocked) {
            return buildBlockedResponse(question)
        }

        val response = forwardDns(question.rawQuery)
        if (response != null) {
            dnsCache[cacheKey] = CachedEntry(response, System.currentTimeMillis() + CACHE_TTL_MS)
            return rewriteQueryId(response, question.queryId)
        }

        return buildServfailResponse(question)
    }

    private fun forwardDns(query: ByteArray): ByteArray? {
        for (server in listOf(CLOUDFLARE_PRIMARY, CLOUDFLARE_SECONDARY)) {
            try {
                val socket = DatagramSocket()
                socket.soTimeout = 2000

                val sendPacket = DatagramPacket(
                    query, query.size,
                    InetAddress.getByName(server), DNS_PORT
                )
                socket.send(sendPacket)

                val recvBuf = ByteArray(512)
                val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
                socket.receive(recvPacket)

                val response = ByteArray(recvPacket.length)
                System.arraycopy(recvPacket.data, 0, response, 0, recvPacket.length)
                socket.close()
                return response
            } catch (e: Exception) {
                Timber.tag(TAG).w("DNS forward failed for $server: ${e.message}")
            }
        }
        return null
    }

    private fun buildBlockedResponse(question: DnsQuestion): ByteArray {
        val q = question.rawQuery
        val response = ByteArray(q.size)
        System.arraycopy(q, 0, response, 0, q.size)

        response[0] = (question.queryId shr 8 and 0xFF).toByte()
        response[1] = (question.queryId and 0xFF).toByte()
        response[2] = 0x81.toByte()
        response[3] = 0x83.toByte()

        response[6] = 0
        response[7] = 0
        response[8] = 0
        response[9] = 0
        response[10] = 0
        response[11] = 0

        return response
    }

    private fun buildServfailResponse(question: DnsQuestion): ByteArray {
        val q = question.rawQuery
        val response = ByteArray(q.size)
        System.arraycopy(q, 0, response, 0, q.size)

        response[0] = (question.queryId shr 8 and 0xFF).toByte()
        response[1] = (question.queryId and 0xFF).toByte()
        response[2] = 0x81.toByte()
        response[3] = 0x02.toByte()

        response[6] = 0
        response[7] = 0
        response[8] = 0
        response[9] = 0
        response[10] = 0
        response[11] = 0

        return response
    }

    private fun rewriteQueryId(response: ByteArray, queryId: Int): ByteArray {
        val copy = response.copyOf()
        copy[0] = (queryId shr 8 and 0xFF).toByte()
        copy[1] = (queryId and 0xFF).toByte()
        return copy
    }

    fun isDomainBlocked(domain: String): Boolean {
        if (domain in blockedDomains) return true
        for (blocked in blockedDomains) {
            if (domain.endsWith(".$blocked")) return true
        }
        return false
    }

    fun clearCache() {
        dnsCache.clear()
    }
}
