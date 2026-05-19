package com.agon.app.engine.safe

import com.agon.app.engine.*
import com.agon.app.engine.filter.DomainMatcher
import com.agon.app.engine.filter.KeywordMatcher
import java.util.regex.Pattern

class PornBlockerEngine(
    private val active: Boolean = false,
    private val customBlocklist: Set<String> = emptySet(),
    private val customKeywords: Set<String> = emptySet(),
    private val customAllowlist: Set<String> = emptySet(),
    private val customAllowlistedDomains: Set<String> = emptySet(),
    private val useStrictMode: Boolean = false
) {
    data class ContentCategory(
        val name: String,
        val domains: Set<String>,
        val keywords: Set<String>,
        val keywordRegex: Set<String>
    )

    companion object {
        val ADULT_CONTENT_CATEGORIES = listOf(
            ContentCategory(
                name = "Pornography",
                domains = setOf(
                    "pornhub.com", "xvideos.com", "xnxx.com", "redtube.com",
                    "youporn.com", "xhamster.com", "tube8.com", "spankbang.com",
                    "eporner.com", "porntrex.com", "pornhd.com", "hclips.com",
                    "tnaflix.com", "porn.com", "nudevista.com", "porn300.com",
                    "xvideos.red", "xvideo.com", "pornzog.com", "vporn.com",
                    "extremetube.com", "keezmovies.com", "cliphunter.com",
                    "sunporno.com", "pornrabbit.com", "porntube.com"
                ),
                keywords = setOf(
                    "porn", "xxx", "porno", "xvideos", "xnxx", "pornhub",
                    "redtube", "youporn", "xhamster", "spankbang"
                ),
                keywordRegex = setOf(
                    "\\b(porn|porno|xxx)\\b",
                    "\\b(xvideos|xnxx|pornhub|redtube)\\b"
                )
            ),
            ContentCategory(
                name = "Adult Dating/Cams",
                domains = setOf(
                    "onlyfans.com", "livejasmin.com", "chaturbate.com",
                    "stripchat.com", "cams.com", "adultfriendfinder.com",
                    "fling.com", "ashleymadison.com", "fetlife.com",
                    "eurogirlsescort.com", "skokka.com", "eros.com",
                    "adultwork.com", "vivastreet.com", "backpage.com"
                ),
                keywords = setOf("onlyfans", "livejasmin", "chaturbate", "stripchat", "adultfriendfinder"),
                keywordRegex = setOf("\\b(onlyfans|livejasmin|chaturbate)\\b")
            ),
            ContentCategory(
                name = "Erotica/Adult Content",
                domains = setOf(
                    "literotica.com", "hentai.com", "nhentai.net",
                    "e-hentai.org", "exhentai.org", "rule34.xxx",
                    "deviantart.com", "furaffinity.net", "aryion.com"
                ),
                keywords = setOf("hentai", "ecchi", "yaoi", "yuri", "rule34", "doujinshi"),
                keywordRegex = setOf("\\b(hentai|doujinshi|rule34)\\b")
            ),
            ContentCategory(
                name = "Adult Streaming",
                domains = setOf(
                    "brazzers.com", "bangbros.com", "naughtyamerica.com",
                    "vivid.com", "playboy.com", "penthouse.com",
                    "realitykings.com", "twistys.com", "mofos.com",
                    "teamskeet.com", "girlsway.com", "wicked.com",
                    "elegantangel.com", "digitalplayground.com"
                ),
                keywords = setOf("brazzers", "bangbros", "playboy", "penthouse"),
                keywordRegex = emptySet()
            )
        )

        val ALL_PORN_DOMAINS: Set<String> by lazy {
            ADULT_CONTENT_CATEGORIES.flatMap { it.domains }.toSet()
        }

        // Keywords safe for literal substring matching (domain-specific or unique terms)
        private val SAFE_SUBSTRING_KEYWORDS: Set<String> = setOf(
            "nsfw", "camgirl", "webcam", "livecam", "sexchat",
            "fuck", "fucking", "blowjob", "cumshot", "orgasm",
            "dildo", "vibrator", "bdsm", "dominatrix",
            "adultdating", "swinger", "milf", "lesbian",
            "gayporn", "shemale", "bigcock", "threesome",
            "gangbang", "creampie", "squirting",
            "18+", "pornhub", "xvideos", "xnxx", "redtube",
            "youporn", "xhamster", "spankbang", "onlyfans",
            "livejasmin", "chaturbate", "stripchat",
            "hentai", "ecchi", "yaoi", "yuri", "rule34",
            "doujinshi", "brazzers", "bangbros", "playboy",
            "penthouse", "adultfriendfinder"
        )

        // Keywords that need word-boundary detection to avoid false positives
        private val WORD_BOUNDED_KEYWORDS: Set<String> = setOf(
            "porn", "porno", "xxx",
            "adult", "sex", "sexy", "sexual",
            "nude", "naked", "nudity", "erotic", "erotica",
            "explicit", "mature", "strip", "stripper",
            "escort", "massage", "anal", "ebony", "tranny"
        )

        // Pre-compiled word-bounded regex patterns
        private val wordBoundedPatterns: List<Regex> by lazy {
            WORD_BOUNDED_KEYWORDS.map {
                Regex("\\b${Pattern.quote(it)}\\b", RegexOption.IGNORE_CASE)
            }
        }

        // Explicit regex patterns from categories
        private val EXPLICIT_REGEX_PATTERNS: Set<String> by lazy {
            ADULT_CONTENT_CATEGORIES.flatMap { it.keywordRegex }.toSet()
        }

        // Regex matcher — now ONLY uses explicit regex patterns
        private val regexMatcher by lazy {
            KeywordMatcher(
                blocklist = EXPLICIT_REGEX_PATTERNS,
                useRegex = true,
                caseSensitive = false
            )
        }

        // Combined for logging/reporting
        val ALL_PORN_KEYWORDS: Set<String> by lazy {
            SAFE_SUBSTRING_KEYWORDS + WORD_BOUNDED_KEYWORDS +
                ADULT_CONTENT_CATEGORIES.flatMap { it.keywords }.toSet()
        }

        val DNS_BLOCK_ZONES = setOf(
            "adult", "porn", "sex", "xxx", "erotic", "hentai",
            "adultdating", "adultvideo", "adultlive", "adultcam",
            "adultchat", "adultweb", "adultcontent"
        )

        // Domains that should never be blocked (educational, medical, gov, etc.)
        val ALLOWLISTED_DOMAINS: Set<String> = setOf(
            "middlesex.edu", "sussex.edu", "essex.edu", "wessex.edu",
            "sexyman.com", "sexysoftware.com", "adultswim.com",
            "matureswim.com", "nakedjuice.com", "nakedpizza.com",
            "erictherobot.com", "analytics.google.com",
            "analytics.yahoo.com", "analytics.microsoft.com",
            "analytics.facebook.com", "sexologo.it",
            "av-med.com", "medicalsexology.org",
            "sexeducation.com", "sex-ed.com",
            "18+.com", "1800contacts.com", "1800flowers.com",
            "1888.com", "sexonthebeach.com"
        )
    }

    private fun isAllowlistedDomain(domain: String): Boolean {
        return ALLOWLISTED_DOMAINS.any { domain.endsWith(it, ignoreCase = true) || domain == it }
    }

    fun evaluate(ctx: FilterContext): BlockMatch? {
        if (!active) return null

        val url = ctx.url
        if (url != null) {
            val domain = DomainMatcher.extractDomain(url) ?: return null

            // Allowlist check — highest priority, before any blocking
            if (isAllowlistedDomain(domain)) return null
            if (customAllowlistedDomains.any { domain.endsWith(it, ignoreCase = true) || domain == it }) return null

            for (category in ADULT_CONTENT_CATEGORIES) {
                for (blocked in category.domains + customBlocklist) {
                    if (domainMatches(domain, blocked)) {
                        return BlockMatch(BlockAction.BLOCK_FULL, "${category.name}: $blocked", MatchSource.PORN_CONTENT, 95)
                    }
                }
            }

            if (useStrictMode) {
                for (zone in DNS_BLOCK_ZONES) {
                    if (domainMatchesZone(domain, zone)) {
                        return BlockMatch(BlockAction.BLOCK_FULL, "DNS zone block: $zone", MatchSource.DNS_FILTER, 90)
                    }
                }
            }
        }

        val textToCheck = buildString {
            ctx.pageTitle?.let { append(" $it") }
            ctx.visibleText?.let { append(" $it") }
        }
        if (textToCheck.isNotBlank()) {
            // 0. Allowlist override (text-based) — highest priority (AdGuard pattern)
            val allAllowlisted = customAllowlist
            if (allAllowlisted.any { textToCheck.contains(it, ignoreCase = true) }) {
                return null
            }

            // 1. Word-bounded keywords (prevents "anal" matching "analysis")
            for (pattern in wordBoundedPatterns) {
                if (pattern.containsMatchIn(textToCheck)) {
                    return BlockMatch(BlockAction.BLOCK_FULL, "Porn keyword: ${pattern.pattern}", MatchSource.PORN_CONTENT, 87)
                }
            }

            // 2. Safe literal substring keywords
            val allSafe = SAFE_SUBSTRING_KEYWORDS + customKeywords
            for (keyword in allSafe) {
                if (textToCheck.contains(keyword, ignoreCase = true)) {
                    return BlockMatch(BlockAction.BLOCK_FULL, "Porn keyword: $keyword", MatchSource.PORN_CONTENT, 85)
                }
            }

            // 3. Strict mode: explicit regex patterns only
            if (useStrictMode) {
                val result = regexMatcher.match(textToCheck)
                if (result.matched) {
                    return BlockMatch(BlockAction.BLOCK_FULL, "Porn regex: ${result.matchedKeyword}", MatchSource.PORN_CONTENT, 88)
                }
            }
        }

        return null
    }

    fun isPornDomain(domain: String): Boolean {
        return ALL_PORN_DOMAINS.any { domainMatches(domain, it) } ||
               customBlocklist.any { domainMatches(domain, it) } ||
               DNS_BLOCK_ZONES.any { domainMatchesZone(domain, it) }
    }

    fun isPornKeyword(text: String): Boolean {
        if (SAFE_SUBSTRING_KEYWORDS.any { text.contains(it, ignoreCase = true) }) return true
        if (customKeywords.any { text.contains(it, ignoreCase = true) }) return true
        return wordBoundedPatterns.any { it.containsMatchIn(text) }
    }

    fun getPornCategory(domain: String): String? {
        return ADULT_CONTENT_CATEGORIES.firstOrNull { category ->
            category.domains.any { domainMatches(domain, it) }
        }?.name
    }

    fun getAllBlockedDomains(): Set<String> = ALL_PORN_DOMAINS + customBlocklist

    private fun domainMatches(domain: String, pattern: String): Boolean {
        val d = domain.lowercase().trim('.')
        val p = pattern.lowercase().trim('.')
        if (d == p) return true
        if (d.endsWith(".$p")) return true
        if (p.startsWith("*.") && d.endsWith(p.removePrefix("*."))) return true
        return false
    }

    private fun domainMatchesZone(domain: String, zone: String): Boolean {
        val d = domain.lowercase().trim('.')
        return d == zone ||
               d.startsWith("$zone.") ||
               d.endsWith(".$zone") ||
               d.contains(".$zone.")
    }
}
