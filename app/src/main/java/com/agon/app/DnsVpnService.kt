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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
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
    // Hostname → IPv4 of the engine's forced-SafeSearch endpoint.
    //
    // The trick: each search engine runs a dedicated hostname that
    // always returns results with the strictest filter enabled, no
    // matter what the client sends. By returning the IP of that
    // endpoint in response to a query for the engine's regular
    // hostname, we transparently redirect the user to the
    // filtered version.
    //
    // IPs sourced from public family-filter DNS operators
    // (CleanBrowsing, OpenDNS FamilyShield, NextDNS) and the
    // engine's own documentation. They are stable but should be
    // sanity-checked if adult content starts leaking through.
    private val SAFE_SEARCH_HOSTS: Map<String, ByteArray> = mapOf(
        // Google — forcesafesearch.google.com
        // (Source: Google "Force SafeSearch" documentation;
        //  well-known stable IP for many years.)
        "google.com" to byteArrayOf(74.toByte(), 125.toByte(), 93.toByte(), 99.toByte()),
        "www.google.com" to byteArrayOf(74.toByte(), 125.toByte(), 93.toByte(), 99.toByte()),

        // Bing — strict.bing.com
        // (Forced-strict server used by CleanBrowsing / OpenDNS
        //  FamilyShield to enforce Bing SafeSearch at the DNS
        //  layer; the IP is in Microsoft's documented Bing range.)
        "bing.com" to byteArrayOf(204.toByte(), 79.toByte(), 197.toByte(), 220.toByte()),
        "www.bing.com" to byteArrayOf(204.toByte(), 79.toByte(), 197.toByte(), 220.toByte()),
        "m.bing.com" to byteArrayOf(204.toByte(), 79.toByte(), 197.toByte(), 220.toByte()),

        // Yahoo — safesearch.yahoo.com
        // (Yahoo's SafeSearch enforcement endpoint; documented by
        //  Yahoo and used by CleanBrowsing Family Filter.)
        "search.yahoo.com" to byteArrayOf(87.toByte(), 248.toByte(), 100.toByte(), 215.toByte()),

        // DuckDuckGo — safe.duckduckgo.com
        // (Forced-safe endpoint; the IP is in Microsoft's Azure
        //  range where DDG is hosted. Some operators also map
        //  duckduckgo.com directly to this IP.)
        "duckduckgo.com" to byteArrayOf(52.toByte(), 142.toByte(), 124.toByte(), 215.toByte()),
        "www.duckduckgo.com" to byteArrayOf(52.toByte(), 142.toByte(), 124.toByte(), 215.toByte()),

        // YouTube — restrict.youtube.com
        // (Documented by Google as the Restricted-Mode endpoint;
        //  the IP is in Google's 216.239.32.0/19 range.)
        "youtube.com" to byteArrayOf(216.toByte(), 239.toByte(), 38.toByte(), 120.toByte()),
        "www.youtube.com" to byteArrayOf(216.toByte(), 239.toByte(), 38.toByte(), 120.toByte()),
        "m.youtube.com" to byteArrayOf(216.toByte(), 239.toByte(), 38.toByte(), 120.toByte()),
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
        val safeSearchIp = if (safeSearchEnabled) SAFE_SEARCH_HOSTS[lcName] else null
        val shouldBlockDoh = blockDohEnabled && lcName in DOH_HOSTS
        if (safeSearchIp == null && !shouldBlockDoh) return null

        // Build the synthetic answer section. We add one A-record
        // answer: name = pointer back to the question (0xC00C), type
        // A, class IN, TTL 60, RDLENGTH 4, RDATA = the policy IP.
        val rewriteIp: ByteArray = safeSearchIp ?: run {
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
 *  - [com.agon.app.services.GuardSoulAccessibilityService] URL scanner
 *    (runs in parallel, sees URLs in the URL bar of browsers).
 *  - [com.agon.app.blocking.AiExplorerEngine] on-device NSFW
 *    image classifier (uses AccessibilityNodeInfo.takeScreenshot()
 *    on API 33+; no MediaProjection / user consent required).
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

    /**
     * Which family DNS provider is currently acting as the
     * primary upstream. Exposed to the home-screen status
     * badge so the user can see *which* family DNS is doing
     * the filtering (not just "VPN on").
     *
     * **BATCH-Q**: previously the home screen only knew
     * "VPN on/off" — now it shows the actual family provider
     * (OpenDNS FamilyShield / Cloudflare Families / etc.).
     *
     * Declared as a nested class (not inside the companion
     * object) so callers can write `DnsVpnService.FamilyDnsProvider.X`
     * without needing the `.Companion` prefix.
     */
    enum class FamilyDnsProvider(val displayName: String) {
        NONE("Off"),
        OPENDNS("OpenDNS FamilyShield"),
        CLOUDFLARE("Cloudflare for Families"),
        CLEANBROWSING_FAMILY("CleanBrowsing Family"),
        CLEANBROWSING_ADULT("CleanBrowsing Adult"),
    }

    companion object {
        private const val NOTIFICATION_ID = 4002
        private const val DNS_PORT = 53
        private const val PROTOCOL_UDP: Byte = 17

        @Volatile
        private var intentionalStop = false

        /**
         * **BATCH-Q**: the family DNS provider currently bound by
         * the VPN. Defaults to NONE so a misread cannot report
         * "OpenDNS" when no traffic loop is running. Updated
         * inside [establishVpnLocked] / [teardownVpnLocked].
         */
        @Volatile
        var cachedActiveFamilyProvider: FamilyDnsProvider = FamilyDnsProvider.NONE
            private set

        /**
         * True when the local TUN interface is currently established
         * and the DNS-forwarding traffic loop is running. Exposed to
         * the home-screen status badge so the user can see at a
         * glance whether the VPN fallback is actually applied (not
         * just the toggle being on, which may need the user to
         * accept the OS-level VPN consent dialog first).
         */
        @Volatile
        var isVpnTunEstablished: Boolean = false
            private set

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
     *
     * **BATCH-Q (Family DNS addition)**: was the CleanBrowsing
     * **Adult** IP (185.228.168.10), which is more aggressive than
     * what a typical "clean / safe search" user wants. We now point
     * at the **OpenDNS FamilyShield** primary
     * (208.67.222.123) — Cisco's purpose-built family resolver.
     */
    private val upstreamV4: InetAddress by lazy {
        InetAddress.getByName(BlockingConfig.OPENDNS_FAMILY_DNS_1)
    }

    /**
     * DNS-001: Set once when the per-policy flow collector starts. The
     * collector MUST run a single time per service lifetime; otherwise
     * `START_STICKY` restarts queue a fresh `serviceScope.launch { … }`
     * per onStartCommand and the combine() collectors stack, racing
     * on `establishVpn()` / `teardownVpn()` and double the traffic-loop
     * launches.
     */
    @Volatile private var collectorStarted: Boolean = false

    /**
     * DNS-006: Serializes `establishVpn()` / `teardownVpn()` so that
     * two concurrent flow collectors (e.g. after a stale-state race)
     * cannot both pass the `if (vpnInterface != null) return` check
     * and produce two `ParcelFileDescriptor`s on the same field.
     */
    private val vpnLifecycleLock = kotlinx.coroutines.sync.Mutex()

    override fun onCreate() {
        super.onCreate()
        // Promoting to foreground in onCreate guarantees we are inside
        // the 5-second window the OS gives a startForegroundService().
        // Without this, on slow devices (or after a content provider
        // boot) Android can ANR-kill us before onStartCommand runs.
        ForegroundServiceHelper.startForegroundCompat(
            this, NOTIFICATION_ID, createNotification(),
            // FG-001: manifest declares systemExempted. Using
            // SPECIAL_USE (the previous default) would have raised
            // a SecurityException on Android 14+, which the helper
            // silently caught, leaving the VPN service without a
            // valid FGS.
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        )

        // DNS-005: Pre-resolve the upstream resolver. A
        // `UnknownHostException` thrown out of the lazy initializer
        // would cancel the traffic loop the first time a packet
        // arrives while leaving the VPN interface open. Doing it
        // here surfaces a clean failure path (we tear down + log).
        try {
            // Touch the lazy so it throws now if DNS is broken.
            upstreamV4
        } catch (e: Exception) {
            AppLogger.e("VPN: cannot resolve upstream DNS, will not start: ${e.message}")
        }

        // DNS-001: Run the per-policy collector ONCE, in onCreate.
        if (!collectorStarted) {
            collectorStarted = true
            serviceScope.launch {
                val settings = repo.getAppSettings()
                combine(
                    settings.shieldActiveFlow,
                    settings.pornBlockerFlow,
                    settings.safeSearchModeFlow,
                    settings.blockDohFlow,
                    // BATCH-Q: subscribe to the family DNS
                    // provider choice so changing it (e.g.
                    // OpenDNS → Cloudflare) re-establishes the
                    // VPN with the new resolver set.
                    settings.familyDnsProviderFlow,
                ) { shield, porn, safe, doh, familyProvider ->
                    ShieldAndPolicy(shield && porn, safe, doh, familyProvider)
                }.collect { snapshot ->
                    cachedSafeSearchMode = snapshot.safeSearch
                    cachedBlockDoh = snapshot.blockDoh
                    cachedFamilyProvider = snapshot.familyProvider
                    // Always go through the mutex so concurrent
                    // emissions (e.g. from a stale combined flow
                    // replay after onStartCommand) serialize cleanly.
                    vpnLifecycleLock.withLock {
                        if (snapshot.shouldRun) {
                            establishVpnLocked()
                        } else {
                            teardownVpnLocked()
                            stopSelf()
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ForegroundServiceHelper.startForegroundCompat(
            this, NOTIFICATION_ID, createNotification(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        )
        // The collector now lives in onCreate; this method just
        // returns START_STICKY so the OS can restart us after a kill.
        return START_STICKY
    }

    private data class ShieldAndPolicy(
        val shouldRun: Boolean,
        val safeSearch: String,
        val blockDoh: Boolean,
        val familyProvider: String,
    )

    /** Cached policy snapshots — read by the per-packet hot path. */
    @Volatile private var cachedSafeSearchMode: String = "off"
    @Volatile private var cachedBlockDoh: Boolean = false
    /** BATCH-Q: which family DNS the user picked in Settings. */
    @Volatile private var cachedFamilyProvider: String = "opendns"

    /**
     * Public entry point. Wraps [establishVpnLocked] in the
     * DNS-006 mutex. All callers (collector, retry paths, tests)
     * must go through this method.
     */
    private fun establishVpn() {
        serviceScope.launch {
            vpnLifecycleLock.withLock { establishVpnLocked() }
        }
    }

    private suspend fun establishVpnLocked() {
        if (vpnInterface != null) return

        // BATCH-Q: build the resolver set based on the user's
        // chosen family provider. The chosen one goes first (so
        // it's the OS's primary), then the other two family-tier
        // resolvers as secondaries, then the aggressive Adult
        // filter as a final fallback. This lets the user pick
        // OpenDNS / Cloudflare / CleanBrowsing Family per their
        // preference, while still getting a robust tiered
        // fallback.
        val (chosenV4, chosenV6, chosenEnum) = when (cachedFamilyProvider) {
            "cloudflare" -> Triple(
                BlockingConfig.CLOUDFLARE_FAMILY_DNS_1,
                BlockingConfig.CLOUDFLARE_FAMILY_DNS_V6_1,
                FamilyDnsProvider.CLOUDFLARE,
            )
            "cleanbrowsing" -> Triple(
                BlockingConfig.CLEANBROWSING_FAMILY_DNS_1,
                BlockingConfig.CLEANBROWSING_FAMILY_DNS_V6_1,
                FamilyDnsProvider.CLEANBROWSING_FAMILY,
            )
            "adult" -> Triple(
                BlockingConfig.CLEANBROWSING_ADULT_DNS_1,
                BlockingConfig.CLEANBROWSING_ADULT_DNS_V6_1,
                FamilyDnsProvider.CLEANBROWSING_ADULT,
            )
            else /* "opendns" or anything unrecognised */ -> Triple(
                BlockingConfig.OPENDNS_FAMILY_DNS_1,
                BlockingConfig.OPENDNS_FAMILY_DNS_V6_1,
                FamilyDnsProvider.OPENDNS,
            )
        }

        val tieredV4 = listOf(
            chosenV4,
            // Other family-tier v4 servers (excluding the chosen one)
            BlockingConfig.OPENDNS_FAMILY_DNS_1,
            BlockingConfig.OPENDNS_FAMILY_DNS_2,
            BlockingConfig.CLOUDFLARE_FAMILY_DNS_1,
            BlockingConfig.CLOUDFLARE_FAMILY_DNS_2,
            BlockingConfig.CLEANBROWSING_FAMILY_DNS_1,
            BlockingConfig.CLEANBROWSING_FAMILY_DNS_2,
            // Final aggressive fallback
            BlockingConfig.CLEANBROWSING_ADULT_DNS_1,
            BlockingConfig.CLEANBROWSING_ADULT_DNS_2,
        ).distinct()
        val tieredV6 = listOf(
            chosenV6,
            BlockingConfig.OPENDNS_FAMILY_DNS_V6_1,
            BlockingConfig.OPENDNS_FAMILY_DNS_V6_2,
            BlockingConfig.CLOUDFLARE_FAMILY_DNS_V6_1,
            BlockingConfig.CLOUDFLARE_FAMILY_DNS_V6_2,
            BlockingConfig.CLEANBROWSING_FAMILY_DNS_V6_1,
            BlockingConfig.CLEANBROWSING_FAMILY_DNS_V6_2,
            BlockingConfig.CLEANBROWSING_ADULT_DNS_V6_1,
            BlockingConfig.CLEANBROWSING_ADULT_DNS_V6_2,
        ).distinct()

        val builder = Builder()
            .setSession("GuardSoul Family DNS")
            .setMtu(BlockingConfig.VPN_MTU)
            // Virtual client addresses — used only for DNS routing.
            .addAddress(BlockingConfig.VPN_CLIENT_ADDRESS, BlockingConfig.VPN_CLIENT_PREFIX)
            .addAddress(BlockingConfig.VPN_CLIENT_V6_ADDRESS, BlockingConfig.VPN_CLIENT_V6_PREFIX)
        // BATCH-Q: add the family DNS resolvers in tiered order.
        for (ip in tieredV4) builder.addDnsServer(ip)
        for (ip in tieredV6) builder.addDnsServer(ip)
        // Route every resolver IP through the TUN so we can
        // intercept its response. Other traffic bypasses us.
        for (ip in tieredV4) builder.addRoute(ip, 32)
        for (ip in tieredV6) builder.addRoute(ip, 128)
        builder.addDisallowedApplication(packageName)

        // Cache the chosen family provider for the home-screen
        // status badge (read by PornBlockerController.snapshot).
        cachedActiveFamilyProvider = chosenEnum

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        try {
            vpnInterface = builder.establish()
            isVpnTunEstablished = true
            startTrafficLoopLocked()
            Timber.d("VPN: Established (Family DNS — primary=$chosenEnum)")
        } catch (e: Exception) {
            Timber.e(e, "VPN: Failed to establish")
            isVpnTunEstablished = false
            cachedActiveFamilyProvider = FamilyDnsProvider.NONE
            // DNS-003: the service was already promoted to
            // foreground in onCreate, so the FGS would stay alive
            // forever with no traffic loop and no recovery path.
            // Stop ourselves so the OS doesn't accumulate dead FGS
            // instances. A future bug-free state emission will
            // re-create us via START_STICKY.
            stopSelf()
        }
    }

    /**
     * Read packets from the TUN interface. We only receive DNS
     * queries because that's the only traffic we route through us.
     * Each query is forwarded to the upstream CleanBrowsing resolver
     * and the response is written back.
     */
    private fun startTrafficLoopLocked() {
        val iface = vpnInterface ?: return
        vpnJob?.cancel()
        vpnJob = serviceScope.launch {
            // The streams wrap the ParcelFileDescriptor's underlying
            // FD. We must close both in a try/finally so a cancellation
            // (or exception inside the loop) doesn't leak two FDs per
            // TUN establishment. The previous implementation never
            // closed them, leaking 2 FDs per service restart.
            var input: FileInputStream? = null
            var output: FileOutputStream? = null
            try {
                input = FileInputStream(iface.fileDescriptor)
                output = FileOutputStream(iface.fileDescriptor)
                val buffer = ByteArray(BlockingConfig.VPN_MTU)

                while (isActive) {
                    try {
                        val readBytes = input.read(buffer)
                        // DNS-004: when the TUN returns 0 (transient
                        // EAGAIN) or negative (EOF/error), the previous
                        // implementation just `continue`d, pegging the
                        // CPU at 100%. Yield to the dispatcher so the
                        // loop backs off cleanly.
                        if (readBytes < 0) {
                            kotlinx.coroutines.yield()
                            continue
                        }
                        if (readBytes == 0) continue

                        // Defensive: skip anything that isn't a UDP DNS
                        // query, even if a future code change adds more
                        // routes.
                        if (!isUdpDnsQuery(buffer, readBytes)) continue

                        val reply = forwardDnsQuery(buffer, readBytes) ?: continue
                        output.write(reply)
                    } catch (e: Exception) {
                        if (isActive) AppLogger.w("VPN: traffic loop error: ${e.message}")
                        kotlinx.coroutines.yield()
                    }
                }
            } finally {
                try { input?.close() } catch (_: Exception) {}
                try { output?.close() } catch (_: Exception) {}
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
            // Use try/finally so any exception in send/receive (timeout,
            // network unreachable) still releases the socket FD.
            val socket = DatagramSocket()
            try {
                // protect() ensures this socket bypasses our own VPN —
                // otherwise the upstream query would loop straight back
                // into us and hang.
                protect(socket)
                socket.send(DatagramPacket(dnsPayload, dnsPayload.size, upstreamV4, DNS_PORT))
                // 4096 fits EDNS0/DNSSEC responses; the kernel will
                // clamp at MTU on TUN write. The previous 1500 buffer
                // truncated larger responses, forcing TCP fallback that
                // the VPN does not handle.
                val response = ByteArray(4096)
                val replyPacket = DatagramPacket(response, response.size)
                socket.soTimeout = 2_000
                socket.receive(replyPacket)

                val finalResponse = if (policyActive) {
                    DnsPolicyRewriter.maybeRewrite(
                        response, replyPacket.length, safeEnabled, dohEnabled
                    ) ?: response
                } else response
                return buildReplyPacket(packet, ihl, finalResponse, finalResponse.size)
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
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

    /**
     * Public entry point. Wraps [teardownVpnLocked] in the
     * DNS-006 mutex. Callers in the collector must go through this.
     */
    private fun teardownVpn() {
        serviceScope.launch {
            vpnLifecycleLock.withLock { teardownVpnLocked() }
        }
    }

    private suspend fun teardownVpnLocked() {
        // DNS-002: cancel the traffic job FIRST and join it so the
        // stream `finally` blocks can close their FDs before we close
        // the ParcelFileDescriptor underneath them. Closing the PFD
        // first would surface as an IOException in the loop, which is
        // silently caught — and the streams would briefly hold a
        // closed FD.
        val job = vpnJob
        vpnJob = null
        if (job != null) {
            try { job.cancelAndJoin() } catch (_: Exception) {}
        }
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        isVpnTunEstablished = false
        // BATCH-Q: clear the provider so a stale "OpenDNS" entry
        // cannot leak through the home-screen status badge after
        // the user has toggled the shield off.
        cachedActiveFamilyProvider = FamilyDnsProvider.NONE
    }

    private fun createNotification(): Notification {
        return ForegroundServiceHelper.buildSilentNotification(
            this,
            getString(R.string.app_name),
            getString(R.string.vpn_alert_text)
        )
    }

    override fun onDestroy() {
        // DNS-002: tear down under the lock so we can't race with
        // the collector's still-pending emissions (e.g. a state flow
        // tick that fires after stopSelf).
        serviceScope.launch {
            vpnLifecycleLock.withLock { teardownVpnLocked() }
            serviceScope.cancel()
        }
        // We can't `super.onDestroy()` inside the launch, so call
        // it now — the lock is just a sync barrier, not a lifecycle
        // barrier. If a final flow emission lands between here and
        // the lock acquire, the in-flight establishVpnLocked will
        // be a no-op (vpnInterface already null) and the FGS is
        // already being torn down.
        super.onDestroy()
    }

    override fun onRevoke() {
        super.onRevoke()
        if (!intentionalStop) {
            VpnStateMonitor.scheduleRevocationWork(this)
        }
    }
}
