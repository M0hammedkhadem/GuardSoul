package com.agon.app.engine.safe

import com.agon.app.engine.*
import com.agon.app.engine.filter.DomainMatcher
import com.agon.app.engine.filter.KeywordMatcher

class PornBlockerEngine(
    private val active: Boolean = false,
    private val customBlocklist: Set<String> = emptySet(),
    private val customKeywords: Set<String> = emptySet(),
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

        val ALL_PORN_KEYWORDS: Set<String> by lazy {
            ADULT_CONTENT_CATEGORIES.flatMap { it.keywords }.toSet() +
            setOf("adult", "sex", "nude", "nsfw", "erotic", "naked", "nudity",
                  "explicit", "mature", "18+", "strip", "camgirl", "webcam",
                  "livecam", "sexchat", "fuck", "blowjob", "orgasm", "dildo",
                  "vibrator", "bdsm", "dominatrix", "escort", "massage",
                  "adultdating", "swinger", "milf", "ebony", "lesbian",
                  "gayporn", "tranny", "shemale", "bigcock", "anal",
                  "threesome", "gangbang", "creampie", "squirting")
        }

        val ALL_PORN_DOMAINS: Set<String> by lazy {
            ADULT_CONTENT_CATEGORIES.flatMap { it.domains }.toSet()
        }

        private val regexMatcher by lazy {
            KeywordMatcher(
                blocklist = ALL_PORN_KEYWORDS + ADULT_CONTENT_CATEGORIES.flatMap { it.keywordRegex }.toSet(),
                useRegex = true,
                caseSensitive = false
            )
        }

        val DNS_BLOCK_ZONES = setOf(
            "adult", "porn", "sex", "xxx", "erotic", "hentai",
            "adultdating", "adultvideo", "adultlive", "adultcam",
            "adultchat", "adultweb", "adultcontent"
        )
    }

    fun evaluate(ctx: FilterContext): BlockMatch? {
        if (!active) return null

        val url = ctx.url
        if (url != null) {
            val domain = DomainMatcher.extractDomain(url) ?: return null
            for (category in ADULT_CONTENT_CATEGORIES) {
                for (blocked in category.domains + customBlocklist) {
                    if (domainMatches(domain, blocked)) {
                        return BlockMatch(BlockAction.BLOCK_FULL, "${category.name}: $blocked", MatchSource.PORN_CONTENT, 95)
                    }
                }
            }

            if (useStrictMode) {
                for (zone in DNS_BLOCK_ZONES) {
                    if (domain.contains(".$zone.") || domain.contains(".$zone/")) {
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
            val keywords = ALL_PORN_KEYWORDS + customKeywords
            for (keyword in keywords) {
                if (textToCheck.contains(keyword, ignoreCase = true)) {
                    return BlockMatch(BlockAction.BLOCK_FULL, "Porn keyword: $keyword", MatchSource.PORN_CONTENT, 85)
                }
            }

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
               DNS_BLOCK_ZONES.any { domain.contains(".$it.") }
    }

    fun isPornKeyword(text: String): Boolean {
        return ALL_PORN_KEYWORDS.any { text.contains(it, ignoreCase = true) } ||
               customKeywords.any { text.contains(it, ignoreCase = true) }
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
}
