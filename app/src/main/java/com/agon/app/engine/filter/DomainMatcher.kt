package com.agon.app.engine.filter

import com.agon.app.engine.*

class DomainMatcher(
    private val blocklist: Set<String> = emptySet(),
    private val allowlist: Set<String> = emptySet()
) {
    data class DomainResult(
        val matched: Boolean,
        val matchedDomain: String? = null,
        val pattern: String? = null,
        val matchType: MatchType = MatchType.EXACT
    )

    enum class MatchType { EXACT, SUBDOMAIN, WILDCARD, SUFFIX }

    fun match(domain: String): DomainResult {
        val d = normalize(domain)

        for (allowed in allowlist) {
            if (matchesPattern(d, normalize(allowed))) return DomainResult(false)
        }

        for (blocked in blocklist) {
            if (matchesPattern(d, normalize(blocked))) {
                val matchType = when {
                    blocked.startsWith("*.") -> MatchType.WILDCARD
                    d != blocked && d.endsWith(".$blocked") -> MatchType.SUBDOMAIN
                    else -> MatchType.EXACT
                }
                return DomainResult(true, d, blocked, matchType)
            }
        }

        return DomainResult(false)
    }

    fun matchUrl(url: String): DomainResult {
        val domain = extractDomain(url) ?: return DomainResult(false)
        return match(domain)
    }

    private fun matchesPattern(domain: String, pattern: String): Boolean {
        if (domain == pattern) return true
        if (pattern.startsWith("*.") && domain.endsWith(pattern.removePrefix("*"))) return true
        if (domain.endsWith(".$pattern")) return true

        if (pattern.contains('*')) {
            val regex = Regex(
                "^${pattern.replace(".", "\\.").replace("*", ".*")}$",
                RegexOption.IGNORE_CASE
            )
            if (regex.matches(domain)) return true
        }

        return false
    }

    fun getTld(domain: String): String? {
        val parts = domain.split(".")
        return if (parts.size >= 2) parts.last() else null
    }

    fun getSld(domain: String): String? {
        val parts = domain.split(".")
        return if (parts.size >= 2) parts[parts.size - 2] else null
    }

    fun matchesAnySuffix(domain: String, suffixes: Set<String>): Boolean {
        val d = normalize(domain)
        return suffixes.any { d.endsWith(".$it") || d == it }
    }

    companion object {
        private val IP_REGEX = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")

        fun normalize(domain: String): String {
            return domain.trim().lowercase().trim('.')
        }

        fun extractDomain(url: String): String? {
            return try {
                val clean = url.trim()
                val withoutProtocol = clean
                    .removePrefix("https://").removePrefix("http://")
                    .removePrefix("ftp://").removePrefix("//")
                withoutProtocol.split("/", "?", "#", ":").firstOrNull()?.lowercase()
            } catch (e: Exception) { null }
        }

        fun isIpAddress(domain: String): Boolean = IP_REGEX.matches(domain)

        fun buildWildcard(subdomain: String, domain: String): String = "*.$domain"

        fun stripWww(domain: String): String {
            return domain.removePrefix("www.").removePrefix("ww2.").removePrefix("m.")
        }
    }
}
