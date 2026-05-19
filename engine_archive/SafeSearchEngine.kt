package com.agon.app.engine.safe

import com.agon.app.engine.*
import com.agon.app.engine.filter.DomainMatcher

class SafeSearchEngine(
    private val safeSearchActive: Boolean = false
) {
    data class SafeSearchConfig(
        val googleEnabled: Boolean = true,
        val youtubeEnabled: Boolean = true,
        val bingEnabled: Boolean = true,
        val duckduckgoEnabled: Boolean = true,
        val enforceStrict: Boolean = false
    )

    companion object {
        private val SEARCH_ENGINES = mapOf(
            "google.com" to SearchEngineConfig(
                name = "Google",
                safeParam = "safe",
                safeValue = "active",
                strictValue = "strict",
                unsafeValue = "off",
                hostOverride = "forcesafesearch.google.com",
                additionalParams = mapOf("ssui" to "on")
            ),
            "youtube.com" to SearchEngineConfig(
                name = "YouTube",
                safeParam = "safe",
                safeValue = "active",
                strictValue = "strict",
                hostOverride = "restrict.youtube.com",
                additionalParams = mapOf("search_query" to "")
            ),
            "bing.com" to SearchEngineConfig(
                name = "Bing",
                safeParam = "adlt",
                safeValue = "strict",
                strictValue = "strict",
                unsafeValue = "off",
                hostOverride = "strict.bing.com"
            ),
            "duckduckgo.com" to SearchEngineConfig(
                name = "DuckDuckGo",
                safeParam = "kp",
                safeValue = "1",
                strictValue = "1",
                unsafeValue = "-1"
            ),
            "search.yahoo.com" to SearchEngineConfig(
                name = "Yahoo",
                safeParam = "vm",
                safeValue = "r",
                strictValue = "r",
                unsafeValue = "n"
            )
        )

        private val SEARCH_HOST_REDIRECTS = mapOf(
            "google.com" to "forcesafesearch.google.com",
            "www.google.com" to "forcesafesearch.google.com",
            "youtube.com" to "restrict.youtube.com",
            "www.youtube.com" to "restrict.youtube.com",
            "bing.com" to "strict.bing.com",
            "www.bing.com" to "strict.bing.com"
        )

        private val SAFE_DNS_SERVERS = listOf(
            "1.1.1.3" to "Cloudflare for Families (Malware + Adult)",
            "1.0.0.3" to "Cloudflare for Families (Secondary)",
            "208.67.222.123" to "OpenDNS Family Shield",
            "208.67.220.123" to "OpenDNS Family Shield (Secondary)",
            "185.228.168.168" to "CleanBrowsing Family Filter",
            "185.228.169.168" to "CleanBrowsing Family Filter (Secondary)"
        )

        private val YOUTUBE_RESTRICTED_DOMAINS = setOf(
            "restrict.youtube.com", "restrictmoderate.youtube.com",
            "restrictstrict.youtube.com"
        )
    }

    data class SearchEngineConfig(
        val name: String,
        val safeParam: String,
        val safeValue: String,
        val strictValue: String,
        val unsafeValue: String? = null,
        val hostOverride: String? = null,
        val additionalParams: Map<String, String> = emptyMap()
    )

    fun getSafeUrl(originalUrl: String): String? {
        if (!safeSearchActive) return null
        val domain = DomainMatcher.extractDomain(originalUrl) ?: return null
        val config = SEARCH_ENGINES.entries.firstOrNull { domain.contains(it.key) }
        val hostOverride = SEARCH_HOST_REDIRECTS[domain] ?: return null

        val separator = if (originalUrl.contains("?")) "&" else "?"
        val paramAppend = config?.let { "${it.value.safeParam}=${it.value.strictValue}" } ?: "safe=active"

        return originalUrl.split("?")[0].let { base ->
            "$base?$paramAppend${config?.value?.additionalParams?.entries?.joinToString("&") { (k, v) -> "$k=$v" }?.let { "&$it" } ?: ""}"
        }
    }

    fun needsDnsRedirect(domain: String): Boolean {
        return safeSearchActive && domain in SEARCH_HOST_REDIRECTS
    }

    fun getRedirectTarget(domain: String): String? {
        return SEARCH_HOST_REDIRECTS[domain]
    }

    fun getSafeDnsServers(): List<Pair<String, String>> {
        return if (safeSearchActive) SAFE_DNS_SERVERS else emptyList()
    }

    fun shouldForceYouTubeRestricted(): Boolean = safeSearchActive

    fun getYouTubeRestrictedDns(): List<String> = listOf(
        "restrict.youtube.com",
        "restrictstrict.youtube.com"
    )

    fun isYoutubeRestricted(domain: String): Boolean = domain in YOUTUBE_RESTRICTED_DOMAINS

    data class DnsFilterConfig(
        val primaryDns: String = "1.1.1.3",
        val secondaryDns: String = "1.0.0.3",
        val blockAdult: Boolean = true,
        val blockMalware: Boolean = true,
        val blockGambling: Boolean = false,
        val blockSocial: Boolean = false,
        val useCustomDns: Boolean = false,
        val customDnsPrimary: String = "",
        val customDnsSecondary: String = ""
    )
}
