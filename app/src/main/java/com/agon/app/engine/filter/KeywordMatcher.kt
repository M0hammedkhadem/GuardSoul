package com.agon.app.engine.filter

import com.agon.app.engine.*

class KeywordMatcher(
    private val blocklist: Set<String> = emptySet(),
    private val allowlist: Set<String> = emptySet(),
    private val useRegex: Boolean = false,
    private val caseSensitive: Boolean = false
) {
    private val compiledBlockPatterns: List<Regex> by lazy {
        if (useRegex) blocklist.map { Regex(if (caseSensitive) it else "(?i)$it") }
        else emptyList()
    }

    private val compiledAllowPatterns: List<Regex> by lazy {
        if (useRegex) allowlist.map { Regex(if (caseSensitive) it else "(?i)$it") }
        else emptyList()
    }

    data class KeywordResult(
        val matched: Boolean,
        val matchedKeyword: String? = null,
        val position: Int = -1,
        val isRegex: Boolean = false
    )

    fun match(text: String): KeywordResult {
        if (text.isBlank()) return KeywordResult(false)
        val searchText = if (caseSensitive) text else text.lowercase()
        val searchBlocklist = if (caseSensitive) blocklist else blocklist.map { it.lowercase() }.toSet()
        val searchAllowlist = if (caseSensitive) allowlist else allowlist.map { it.lowercase() }.toSet()

        for (keyword in searchAllowlist) {
            if (searchText.contains(keyword)) return KeywordResult(false)
        }

        if (useRegex && compiledBlockPatterns.isNotEmpty()) {
            for (pattern in compiledBlockPatterns) {
                val match = pattern.find(text)
                if (match != null) {
                    return KeywordResult(true, match.value, match.range.first, true)
                }
            }
        }

        for (keyword in searchBlocklist) {
            val idx = searchText.indexOf(keyword)
            if (idx >= 0) {
                return KeywordResult(true, keyword, idx, false)
            }
        }

        return KeywordResult(false)
    }

    fun matchAny(vararg texts: String?): KeywordResult {
        for (text in texts) {
            if (text != null) {
                val result = match(text)
                if (result.matched) return result
            }
        }
        return KeywordResult(false)
    }

    companion object {
        fun defaultPornKeywords(): Set<String> = setOf(
            "porn", "xxx", "adult", "sex", "nude", "nsfw", "hentai", "erotic",
            "porno", "xxxvideos", "adultcontent", "sexcam", "naked", "nudity",
            "explicit", "mature", "18+", "onlyfans", "strip", "stripper",
            "camgirl", "webcam", "livecam", "sexchat", "fuck", "fucking",
            "blowjob", "cumshot", "orgasm", "dildo", "vibrator", "bdsm",
            "mistress", "dominatrix", "escort", "massage", "adultdating"
        )

        fun defaultPornRegex(): Set<String> = setOf(
            "\\b(porn|porno|xxx)\\b",
            "\\b(sex|sexy|sexual)\\b",
            "\\b(nude|nudity|naked)\\b",
            "\\b(erotic|erotica)\\b",
            "\\b(hentai|ecchi|yaoi|yuri)\\b",
            "\\b(nsfw|nsfl)\\b",
            "\\b(onlyfans|fancentro)\\b"
        )
    }
}
