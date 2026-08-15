package com.agon.app.engine

import android.view.accessibility.AccessibilityNodeInfo

/** What the browser guard decided about the current page. */
sealed class BrowserVerdict {
    data object Allow : BrowserVerdict()
    data class BlockedDomain(val domain: String) : BrowserVerdict()
    data class BlockedKeyword(val keyword: String) : BrowserVerdict()
    data class UnsafeSearch(val engine: String) : BrowserVerdict()
}

/**
 * Watches browser URL bars, applies the blacklist/whitelist, the keyword
 * filter and SafeSearch enforcement for the supported search engines.
 */
object BrowserGuard {

    /** Browser package -> url bar view id. */
    val browserUrlBars = mapOf(
        "com.android.chrome" to "com.android.chrome:id/url_bar",
        "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
        "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.opera.browser" to "com.opera.browser:id/url_field",
        "com.brave.browser" to "com.brave.browser:id/url_bar",
        "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput",
    )

    fun isBrowser(packageName: String): Boolean = browserUrlBars.containsKey(packageName)

    fun extractUrl(root: AccessibilityNodeInfo?, packageName: String): String? {
        val barId = browserUrlBars[packageName] ?: return null
        val nodes = root?.findAccessibilityNodeInfosByViewId(barId) ?: return null
        val text = nodes.firstOrNull()?.text?.toString()?.trim()?.lowercase()
        return if (text.isNullOrBlank()) null else text
    }

    fun judge(
        url: String,
        blacklist: List<String>,
        whitelist: List<String>,
        keywords: List<String>,
        keywordFilterOn: Boolean,
        siteFilterOn: Boolean,
        safeEngines: Map<String, Boolean>,
    ): BrowserVerdict {
        val host = hostOf(url)

        // Whitelist always wins.
        if (whitelist.any { host.contains(it.lowercase()) }) return BrowserVerdict.Allow

        if (siteFilterOn) {
            blacklist.firstOrNull { host.contains(it.lowercase()) }?.let {
                return BrowserVerdict.BlockedDomain(it)
            }
        }

        if (keywordFilterOn) {
            // High-accuracy matcher: word boundaries + Arabic normalization +
            // leet folding — no Scunthorpe false positives on innocent URLs.
            KeywordMatcher.findMatch(url, keywords)?.let {
                return BrowserVerdict.BlockedKeyword(it)
            }
        }

        // SafeSearch enforcement per engine.
        detectUnsafeSearch(url, host, safeEngines)?.let { return BrowserVerdict.UnsafeSearch(it) }

        return BrowserVerdict.Allow
    }

    private fun detectUnsafeSearch(
        url: String,
        host: String,
        safeEngines: Map<String, Boolean>,
    ): String? {
        fun on(name: String) = safeEngines[name] == true
        return when {
            on("Google") && host.contains("google.") && url.contains("/search") &&
                !url.contains("safe=active") -> "Google"
            on("Bing") && host.contains("bing.com") && url.contains("/search") &&
                !url.contains("adlt=strict") -> "Bing"
            on("Yahoo") && host.contains("search.yahoo.") && url.contains("/search") &&
                !url.contains("vm=r") -> "Yahoo"
            on("DuckDuckGo") && host.contains("duckduckgo.com") && url.contains("q=") &&
                !url.contains("kp=1") -> "DuckDuckGo"
            on("YouTube") && host.contains("youtube.com") &&
                url.contains("search_query=") && !url.contains("safe=active") -> "YouTube"
            else -> null
        }
    }

    private fun hostOf(url: String): String {
        var s = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        val slash = s.indexOf('/')
        if (slash >= 0) s = s.substring(0, slash)
        val q = s.indexOf('?')
        if (q >= 0) s = s.substring(0, q)
        return s
    }
}
