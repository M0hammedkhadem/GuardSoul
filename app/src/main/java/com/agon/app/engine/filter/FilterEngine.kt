package com.agon.app.engine.filter

import com.agon.app.engine.*

class FilterEngine(
    private val blacklistKeywords: Set<String> = emptySet(),
    private val blacklistWebsites: Set<String> = emptySet(),
    private val blacklistApps: Set<String> = emptySet(),
    private val whitelistKeywords: Set<String> = emptySet(),
    private val whitelistWebsites: Set<String> = emptySet(),
    private val whitelistApps: Set<String> = emptySet(),
    private val pornBlockerActive: Boolean = false,
    private val domainBlocklist: DomainBlocklist = DomainBlocklist()
) {
    data class DomainBlocklist(
        val socialDomains: Set<String> = emptySet(),
        val pornDomains: Set<String> = emptySet(),
        val adultDomains: Set<String> = emptySet(),
        val customDomains: Set<String> = emptySet()
    )

    fun evaluate(ctx: FilterContext): BlockMatch? {
        if (ctx.packageName in whitelistApps) return null

        val appMatch = checkAppBlock(ctx.packageName)
        if (appMatch != null) return appMatch

        val url = ctx.url ?: return null

        val whitelistMatch = checkWhitelistDomain(url)
        if (whitelistMatch != null) return null

        val domainMatch = checkDomainBlock(url)
        if (domainMatch != null) return domainMatch

        val keywordMatch = checkKeywordBlock(ctx.pageTitle, ctx.visibleText, ctx.isSearchQuery)
        if (keywordMatch != null) return keywordMatch

        return null
    }

    fun evaluateUrl(url: String, packageName: String? = null): BlockMatch? {
        return evaluate(FilterContext(url = url, packageName = packageName))
    }

    fun evaluateText(text: String, isSearch: Boolean = false): BlockMatch? {
        return evaluate(FilterContext(visibleText = text, isSearchQuery = isSearch))
    }

    private fun checkAppBlock(packageName: String?): BlockMatch? {
        val pkg = packageName ?: return null
        if (pkg in whitelistApps) return null
        if (pkg in blacklistApps) {
            return BlockMatch(BlockAction.BLOCK_FULL, "Blacklisted app", MatchSource.BLACKLIST_APP, 100)
        }
        return null
    }

    private fun checkWhitelistDomain(url: String): Boolean {
        val domain = extractDomain(url) ?: return false
        return whitelistWebsites.any { domainMatches(domain, it) }
    }

    private fun checkDomainBlock(url: String): BlockMatch? {
        val domain = extractDomain(url) ?: return null

        for (blocked in blacklistWebsites) {
            if (domainMatches(domain, blocked)) {
                val source = when {
                    domainBlocklist.pornDomains.any { domainMatches(domain, it) } -> MatchSource.PORN_CONTENT
                    domainBlocklist.adultDomains.any { domainMatches(domain, it) } -> MatchSource.PORN_CONTENT
                    domainBlocklist.socialDomains.any { domainMatches(domain, it) } -> MatchSource.SOCIAL_FULL_BLOCK
                    else -> MatchSource.BLACKLIST_WEBSITE
                }
                val action = if (source == MatchSource.PORN_CONTENT) BlockAction.BLOCK_FULL else BlockAction.BLOCK_FULL
                return BlockMatch(action, "Blocked domain: $domain", source, 80)
            }
        }

        return null
    }

    private fun checkKeywordBlock(pageTitle: String?, visibleText: String?, isSearch: Boolean): BlockMatch? {
        val textToCheck = buildString {
            pageTitle?.let { append(" $it") }
            visibleText?.let { append(" $it") }
        }.lowercase()
        if (textToCheck.isBlank()) return null

        for (keyword in whitelistKeywords) {
            if (textToCheck.contains(keyword.lowercase())) return null
        }

        for (keyword in blacklistKeywords) {
            if (textToCheck.contains(keyword.lowercase())) {
                val source = if (pornBlockerActive) MatchSource.PORN_CONTENT else MatchSource.BLACKLIST_KEYWORD
                return BlockMatch(BlockAction.BLOCK_FULL, "Keyword match: $keyword", source, 85)
            }
        }

        return null
    }

    private fun domainMatches(domain: String, pattern: String): Boolean {
        val d = domain.lowercase().trim('.')
        val p = pattern.lowercase().trim('.')

        if (d == p) return true
        if (d.endsWith(".$p")) return true
        if (p.startsWith("*.") && d.endsWith(p.removePrefix("*."))) return true

        val wildcardIdx = p.indexOf('*')
        if (wildcardIdx >= 0) {
            val prefix = p.substring(0, wildcardIdx)
            val suffix = p.substring(wildcardIdx + 1)
            if (d.startsWith(prefix) && d.endsWith(suffix) && d.length >= prefix.length + suffix.length) return true
        }

        return false
    }

    private fun extractDomain(url: String): String? {
        return try {
            val clean = url.trim()
            val withoutProtocol = clean
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("ftp://")
            val withoutPath = withoutProtocol.split("/", "?", "#").firstOrNull() ?: return null
            withoutPath.split(":").firstOrNull()?.lowercase()
        } catch (e: Exception) { null }
    }

    fun getDomainBlocklist(): DomainBlocklist = domainBlocklist

    companion object {
        fun defaultPornDomains(): Set<String> = setOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "redtube.com",
            "youporn.com", "onlyfans.com", "onlyfans.com", "tube8.com",
            "xhamster.com", "livejasmin.com", "chaturbate.com",
            "stripchat.com", "cams.com", "adultfriendfinder.com",
            "fling.com", "ashleymadison.com", "fetlife.com",
            "xvideos.red", "spankbang.com", "motherless.com",
            "eporner.com", "hclips.com", "tnaflix.com",
            "porntrex.com", "pornhd.com", "porn.com",
            "playboy.com", "penthouse.com", "brazzers.com",
            "bangbros.com", "naughtyamerica.com", "vivid.com",
            "nudevista.com", "samantha.com", "erox.com"
        )

        fun defaultSocialDomains(): Set<String> = setOf(
            "facebook.com", "fb.com", "fbcdn.net", "messenger.com",
            "instagram.com", "cdninstagram.com",
            "twitter.com", "x.com", "t.co",
            "youtube.com", "youtu.be", "ytimg.com",
            "tiktok.com", "tiktokcdn.com",
            "snapchat.com", "reddit.com", "redd.it",
            "linkedin.com", "pinterest.com", "tumblr.com",
            "whatsapp.net", "discord.com", "discordapp.com",
            "telegram.org", "t.me", "twitch.tv",
            "onlyfans.com", "patreon.com"
        )
    }
}
