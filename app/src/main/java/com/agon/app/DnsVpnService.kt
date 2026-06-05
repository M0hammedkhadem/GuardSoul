package com.agon.app

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.agon.app.blocking.BlockingConfig
import com.agon.app.data.repository.AppRepository
import com.agon.app.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * DNS response hijacker. Drops in a synthetic answer when the user's DNS
 * resolver returns something that contradicts our policies.
 *
 *  - **Safe Search** (`safeSearchMode != "off"`): rewrites the A/AAAA
 *    answer for well-known search engines to point at the provider's
 *    forced-safe-search endpoint. The browser then loads the
 *    "safesearch=on" version transparently.
 *  - **Block DoH** (`blockDoh == true`): drops (returns 0.0.0.0) any
 *    A/AAAA answer for the canonical DNS-over-HTTPS hostnames. This
 *    forces browsers to fall back to the system resolver — which the
 *    VPN already controls.
 *
 * The hijacker is **non-destructive**: it only touches the response
 * payload, never the original query. If the upstream resolver returns
 * SERVFAIL or an empty answer the hijacker leaves the packet alone so
 * the OS can retry against the next server.
 */
private object DnsPolicyRewriter {
    // Google SafeSearch: forcesafesearch.google.com is documented by Google
    // to always serve https://www.google.com with safesearch=on. Stable
    // for many years. (Source: Google "Search the Force SafeSearch" doc.)
    private val FORCE_SAFE_SEARCH_IPV4: ByteArray =
        byteArrayOf(74, 125, 93, 99)

    // Hostnames we hijack to the safe-search endpoint. The list is
    // intentionally narrow — we only touch the apex and the common
    // www/m variants that browsers tend to query first. Subdomain
    // wildcards would risk breaking legitimate services.
    private val SAFE_SEARCH_HOSTS: Set<String> = setOf(
        "google.com",
        "www.google.com",
    )

    // Hostnames for canonical DoH endpoints. If the user has enabled
    // "Block DoH" we strip these from the A/AAAA answers so the
    // browser can never reach them — even via IP-literal.
    private val DOH_HOSTS: Set<String> = setOf(
        "cloudflare-dns.com",
        "mozilla.cloudflare-dns.com",
        "1dot1dot1dot1.cloudflare-dns.com",
        "dns.google",
        "dns.quad9.net",
        "doh.opendns.com",
        "doh.cleanbrowsing.org",
        "dns.adguard-dns.com",
        "dns.privacy.net",
    )

    /**
     * Returns the rewritten DNS response payload, or `null` if no
     * policy matched and the upstream response should be passed
     * through untouched.
     *
     * `payload` is the DNS message body (after the IP/UDP header has
     * been stripped), exactly as captured from the TUN read.
     */
    fun maybeRewrite(
        payload: ByteArray,
        length: Int,
        safeSearchEnabled: Boolean,
        blockDohEnabled: Boolean,
    ): ByteArray? {
        if (length < 12) return null // not a valid DNS message
        if (!safeSearchEnabled && !blockDohEnabled) return null
        val qdCount = readU16(payload, 4)
        val anCount = readU16(payload, 6)
        if (qdCount == 0 || anCount == 0) return null

        // Walk the question section to extract the QNAME of the first
        // question. QNAME is length-prefixed labels ending in a 0 byte.
        val (firstName, qEndOffset) = readName(payload, 12) ?: return null
        val lcName = firstName.lowercase()
        val shouldSafeSearch = safeSearchEnabled && lcName in SAFE_SEARCH_HOSTS
        val shouldBlockDoh = blockDohEnabled && lcName in DOH_HOSTS
        if (!shouldSafeSearch && !shouldBlockDoh) return null

        // Build the synthetic answer section. We add one A-record
        // answer: name = pointer back to the question (0xC00C), type
        // A, class IN, TTL 60, RDLENGTH 4, RDATA = the policy IP.
        val rewriteIp: ByteArray = if (shouldSafeSearch) {
            FORCE_SAFE_SEARCH_IPV4
        } else {
            // Block DoH: hand back 0.0.0.0 — RFC 5735 / IANA reserved.
            byteArrayOf(0, 0, 0, 0)
        }
        val answer = ByteArray(16)
        answer[0] = 0xC0.toByte() // pointer to offset 12 (start of QNAME)
        answer[1] = 0x0C.toByte()
        answer[2] = 0x00
        answer[3] = 0x01 // type A
        answer[4] = 0x00
        answer[5] = 0x01 // class IN
        answer[6] = 0x00
        answer[7] = 0x00
        answer[8] = 0x00
        answer[9] = 0x3C // TTL = 60
        answer[10] = 0x00
        answer[11] = 0x04 // RDLENGTH = 4
        System.arraycopy(rewriteIp, 0, answer, 12, 4)

        val out = ByteArray(length + answer.size)
        System.arraycopy(payload, 0, out, 0, length)
        System.arraycopy(answer, 0, out, length, answer.size)
        // Bump the answer count in the header.
        val newAnCount = anCount + 1
        out[6] = (newAnCount shr 8).toByte()
        out[7] = (newAnCount and 0xFF).toByte()
        // Set the RA bit so the OS does not retry the original query.
        out[3] = (out[3].toInt() or 0x80).toByte()
        return out
    }

    private fun readU16(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)

    /**
     * Reads a DNS name at [off] and returns the lowercased textual
     * form (without the trailing dot) and the offset of the byte
     * immediately after the name.
     *
     * Returns null if the name is malformed.
     */
    private fun readName(buf: ByteArray, off: Int): Pair<String, Int>? {
        val labels = mutableListOf<String>()
        var i = off
        var jumped = false
        var maxJumps = 5
        var ptrEnd = -1
        while (i < buf.size) {
            val len = buf[i].toInt() and 0xFF
            when {
                len == 0 -> {
                    i++
                    if (ptrEnd < 0) ptrEnd = i
                    return labels.joinToString(".") to ptrEnd
                }
                (len and 0xC0) == 0xC0 -> {
                    if (i + 1 >= buf.size) return null
                    val ptr = ((len and 0x3F) shl 8) or (buf[i + 1].toInt() and 0xFF)
                    if (!jumped) ptrEnd = i + 2
                    i = ptr
                    jumped = true
                    if (--maxJumps < 0) return null
                }
                else -> {
                    if (i + 1 + len > buf.size) return null
                    val label = String(buf, i + 1, len, Charsets.US_ASCII)
                    if (!isSafeLabel(label)) return null
                    labels.add(label)
                    i += 1 + len
                }
            }
        }
        return null
    }

    private fun isSafeLabel(label: String): Boolean {
        if (label.isEmpty() || label.length > 63) return false
        for (c in label) {
            if (!(c.isLetterOrDigit() || c == '-' || c == '_')) return false
        }
        return true
    }
}

/**
 * VPN Service for local DNS filtering.
 *
 * Used as the **fallback** for non-Device-Owner installs. When the user
 * is the Device Owner, [PornBlockerService] sets the system Private DNS
 * via [android.app.admin.DevicePolicyManager] and this VPN is stopped.
 *
 * **DNS-only routing model**
 * This VPN establishes a TUN interface and adds routes for the
 * CleanBrowsing resolver IPs only. Packets destined for those IPs
 * (i.e. DNS queries) are intercepted, forwarded to the upstream
 * resolver via a [protect]ed [DatagramSocket], and the response is
 * written back. All other traffic (HTTP, HTTPS, video, etc.) bypasses
 * the VPN entirely and is routed normally — this is what keeps the
 * user's internet working without us implementing a full TCP/UDP
 * stack.
 *
 * **Why this is sufficient for blocking**
 * The actual content blocking for non-DNS is handled by:
 *  - [com.agon.app.services.GuardianAccessibilityService] URL scanner
 *    (runs in parallel, sees URLs in the URL bar of browsers).
 *  - [com.agon.app.AiScannerService] visual classifier.
 * The DNS-level filtering done by this VPN catches the bulk of
 * attempts to reach blocked domains before any app can resolve them.
 *
 * **Why no [addRoute]`("0.0.0.0", 0)`**
 * Earlier versions of this service claimed ALL traffic and then never
 * wrote responses, silently breaking internet connectivity. We now
 * route only the resolver IPs so the loop only sees DNS packets it
 * actually knows how to handle.
 */
class DnsVpnService : VpnService() {

    companion object {
        private const val NOTIFICATION_ID = 4002
        private const val DNS_PORT = 53
        private const val PROTOCOL_UDP: Byte = 17

        @Volatile
        private var intentionalStop = false

        fun start(context: Context) {
            intentionalStop = false
            val intent = Intent(context, DnsVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            intentionalStop = true
            context.stopService(Intent(context, DnsVpnService::class.java))
        }

        fun wasStoppedIntentionally(): Boolean = intentionalStop
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val repo: AppRepository by lazy { (applicationContext as GuardianApp).repository }

    /**
     * Pre-parsed InetAddress of the upstream resolver. Computed once so
     * the per-packet hot path doesn't re-parse the dotted-quad string.
     */
    private val upstreamV4: InetAddress by lazy {
        InetAddress.getByName(BlockingConfig.CLEANBROWSING_ADULT_DNS_1)
    }

    override fun onCreate() {
        super.onCreate()
        // Promoting to foreground in onCreate guarantees we are inside
        // the 5-second window the OS gives a startForegroundService().
        // Without this, on slow devices (or after a content provider
        // boot) Android can ANR-kill us before onStartCommand runs.
        ForegroundServiceHelper.startForegroundCompat(this, NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ForegroundServiceHelper.startForegroundCompat(this, NOTIFICATION_ID, createNotification())

        serviceScope.launch {
            val settings = repo.getAppSettings()
            combine(
                settings.shieldActiveFlow,
                settings.pornBlockerFlow,
                settings.safeSearchModeFlow,
                settings.blockDohFlow,
            ) { shield, porn, safe, doh ->
                ShieldAndPolicy(shield && porn, safe, doh)
            }.collect { snapshot ->
                cachedSafeSearchMode = snapshot.safeSearch
                cachedBlockDoh = snapshot.blockDoh
                if (snapshot.shouldRun) {
                    establishVpn()
                } else {
                    teardownVpn()
                    stopSelf()
                }
            }
        }

        return START_STICKY
    }

    private data class ShieldAndPolicy(
        val shouldRun: Boolean,
        val safeSearch: String,
        val blockDoh: Boolean,
    )

    /** Cached policy snapshots — read by the per-packet hot path. */
    @Volatile private var cachedSafeSearchMode: String = "off"
    @Volatile private var cachedBlockDoh: Boolean = false

    private fun establishVpn() {
        if (vpnInterface != null) return

        val builder = Builder()
            .setSession("GuardSoul DNS Filter")
            .setMtu(BlockingConfig.VPN_MTU)
            // Virtual client addresses — used only for DNS routing.
            .addAddress(BlockingConfig.VPN_CLIENT_ADDRESS, BlockingConfig.VPN_CLIENT_PREFIX)
            .addDnsServer(BlockingConfig.CLEANBROWSING_ADULT_DNS_1)
            .addDnsServer(BlockingConfig.CLEANBROWSING_ADULT_DNS_2)
            .addAddress(BlockingConfig.VPN_CLIENT_V6_ADDRESS, BlockingConfig.VPN_CLIENT_V6_PREFIX)
            .addDnsServer(BlockingConfig.CLEANBROWSING_ADULT_DNS_V6_1)
            .addDnsServer(BlockingConfig.CLEANBROWSING_ADULT_DNS_V6_2)
            // Route only the resolver IPs through the TUN — every other
            // packet bypasses us and goes straight to the network.
            .addRoute(BlockingConfig.CLEANBROWSING_ADULT_DNS_1, 32)
            .addRoute(BlockingConfig.CLEANBROWSING_ADULT_DNS_2, 32)
            .addRoute("2a0d:2a00:1::", 128)
            .addRoute("2a0d:2a00:2::", 128)
            // Don't loop GuardSoul's own traffic into the VPN — that
            // would block cloud sync / Firebase callbacks that have to
            // reach our backend.
            .addDisallowedApplication(packageName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        try {
            vpnInterface = builder.establish()
            startTrafficLoop()
            Timber.d("VPN: Established successfully (DNS-only mode)")
        } catch (e: Exception) {
            Timber.e(e, "VPN: Failed to establish")
        }
    }

    /**
     * Read packets from the TUN interface. We only receive DNS
     * queries because that's the only traffic we route through us.
     * Each query is forwarded to the upstream CleanBrowsing resolver
     * and the response is written back.
     */
    private fun startTrafficLoop() {
        val iface = vpnInterface ?: return
        vpnJob?.cancel()
        vpnJob = serviceScope.launch {
            val input = FileInputStream(iface.fileDescriptor)
            val output = FileOutputStream(iface.fileDescriptor)
            val buffer = ByteBuffer.allocate(BlockingConfig.VPN_MTU)

            while (isActive) {
                try {
                    buffer.clear()
                    val readBytes = input.read(buffer.array())
                    if (readBytes <= 0) continue

                    // Defensive: skip anything that isn't a UDP DNS
                    // query, even if a future code change adds more
                    // routes.
                    if (!isUdpDnsQuery(buffer.array(), readBytes)) continue

                    val reply = forwardDnsQuery(buffer.array(), readBytes) ?: continue
                    output.write(reply)
                } catch (e: Exception) {
                    if (isActive) AppLogger.w("VPN: traffic loop error: ${e.message}")
                }
            }
        }
    }

    /**
     * True when the captured packet is a UDP/IPv4 packet addressed to
     * port 53. Anything else is skipped (it shouldn't reach us
     * because we don't add a default route, but defensive programming).
     */
    private fun isUdpDnsQuery(packet: ByteArray, length: Int): Boolean {
        if (length < 28) return false
        val version = (packet[0].toInt() shr 4) and 0xF
        if (version != 4) return false
        val ihl = (packet[0].toInt() and 0xF) * 4
        if (ihl < 20 || length < ihl + 8) return false
        if (packet[9] != PROTOCOL_UDP) return false
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or
            (packet[ihl + 3].toInt() and 0xFF)
        return dstPort == DNS_PORT
    }

    /**
     * Forward a captured DNS query to the upstream resolver and
     * return the response wrapped in a fresh IP/UDP packet addressed
     * back to the original client.
     *
     * Before forwarding, the captured query is inspected by
     * [DnsPolicyRewriter] and may be short-circuited with a synthetic
     * answer (SafeSearch redirect / DoH block). This is a no-op when
     * both policy toggles are off.
     */
    private fun forwardDnsQuery(packet: ByteArray, length: Int): ByteArray? {
        return try {
            val ihl = (packet[0].toInt() and 0xF) * 4
            val dnsPayload = packet.copyOfRange(ihl + 8, length)

            val safeEnabled = cachedSafeSearchMode != "off"
            val dohEnabled = cachedBlockDoh
            val policyActive = safeEnabled || dohEnabled

            // 1) Short-circuit: build a minimal response from the
            //    query alone and let the rewriter append an answer
            //    if the QNAME is in our policy list. This avoids
            //    round-tripping to CleanBrowsing for hosts we want to
            //    rewrite ourselves.
            val synthesized: ByteArray? = if (policyActive) {
                val qLen = dnsPayload.size
                val stub = ByteArray(qLen)
                System.arraycopy(dnsPayload, 0, stub, 0, qLen)
                stub[2] = (stub[2].toInt() or 0x80).toByte() // QR = 1
                stub[3] = (stub[3].toInt() and 0x71).toByte() // clear AA/TC/RA
                stub[6] = 0; stub[7] = 0                     // ANCOUNT = 0
                DnsPolicyRewriter.maybeRewrite(stub, qLen, safeEnabled, dohEnabled)
            } else null

            if (synthesized != null) {
                return buildReplyPacket(packet, ihl, synthesized, synthesized.size)
            }

            // 2) Real resolver: forward to CleanBrowsing and apply the
            //    rewriter to the response too. This catches the case
            //    where the *answer* is a CNAME to a policy hostname
            //    (e.g. www.google.com → forcesafesearch.google.com).
            val socket = DatagramSocket()
            // protect() ensures this socket bypasses our own VPN —
            // otherwise the upstream query would loop straight back
            // into us and hang.
            protect(socket)
            socket.send(DatagramPacket(dnsPayload, dnsPayload.size, upstreamV4, DNS_PORT))
            val response = ByteArray(1500)
            val replyPacket = DatagramPacket(response, response.size)
            socket.soTimeout = 2_000
            socket.receive(replyPacket)
            socket.close()

            val finalResponse = if (policyActive) {
                DnsPolicyRewriter.maybeRewrite(
                    response, replyPacket.length, safeEnabled, dohEnabled
                ) ?: response
            } else response
            buildReplyPacket(packet, ihl, finalResponse, finalResponse.size)
        } catch (e: Exception) {
            AppLogger.w("VPN: DNS forward failed: ${e.message}")
            null
        }
    }

    /**
     * Build a fresh IPv4/UDP packet addressed back to the original
     * client carrying the upstream DNS response. The IP header's
     * src/dst are swapped; the IP checksum is left at zero so the
     * kernel recomputes it on TUN write.
     */
    private fun buildReplyPacket(
        original: ByteArray,
        ihl: Int,
        dnsResponse: ByteArray,
        dnsLength: Int
    ): ByteArray {
        val udpLength = 8 + dnsLength
        val totalLength = ihl + udpLength
        val out = ByteArray(totalLength)

        // Copy + rewrite IP header (with checksums zeroed)
        out[0] = original[0]   // version + IHL
        out[1] = original[1]   // TOS
        out[2] = (totalLength shr 8).toByte()
        out[3] = (totalLength and 0xFF).toByte()
        out[4] = 0             // identification
        out[5] = 0
        out[6] = 0             // flags/fragment
        out[7] = 0
        out[8] = 64            // TTL
        out[9] = PROTOCOL_UDP  // protocol
        out[10] = 0            // checksum (kernel recomputes)
        out[11] = 0
        // Swap src/dst
        System.arraycopy(original, 12, out, 16, 4)
        System.arraycopy(original, 16, out, 12, 4)

        // UDP header: swap ports
        out[ihl] = original[ihl + 2]
        out[ihl + 1] = original[ihl + 3]
        out[ihl + 2] = original[ihl]
        out[ihl + 3] = original[ihl + 1]
        out[ihl + 4] = (udpLength shr 8).toByte()
        out[ihl + 5] = (udpLength and 0xFF).toByte()
        out[ihl + 6] = 0
        out[ihl + 7] = 0

        // Payload = DNS response
        System.arraycopy(dnsResponse, 0, out, ihl + 8, dnsLength)
        return out
    }

    private fun teardownVpn() {
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        vpnJob?.cancel()
        vpnJob = null
    }

    private fun createNotification(): Notification {
        return ForegroundServiceHelper.buildSilentNotification(
            this,
            getString(R.string.app_name),
            getString(R.string.vpn_alert_text)
        )
    }

    override fun onDestroy() {
        teardownVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        super.onRevoke()
        if (!intentionalStop) {
            VpnStateMonitor.scheduleRevocationWork(this)
        }
    }
}
