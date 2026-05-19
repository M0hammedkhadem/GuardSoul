package com.agon.app.engine.filter

class AppMatcher(
    private val blocklist: Set<String> = emptySet(),
    private val allowlist: Set<String> = emptySet()
) {
    data class AppResult(
        val matched: Boolean,
        val matchedPackage: String? = null,
        val displayName: String? = null
    )

    private val knownApps: Map<String, String> = mapOf(
        "com.instagram.android" to "Instagram",
        "com.snapchat.android" to "Snapchat",
        "com.twitter.android" to "X (Twitter)",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
        "com.google.android.youtube" to "YouTube",
        "com.facebook.katana" to "Facebook",
        "com.facebook.orca" to "Messenger",
        "com.whatsapp" to "WhatsApp",
        "org.telegram.messenger" to "Telegram",
        "com.discord" to "Discord",
        "com.reddit.frontpage" to "Reddit",
        "com.linkedin.android" to "LinkedIn",
        "com.pinterest" to "Pinterest",
        "com.tumblr" to "Tumblr",
        "com.twitch.tv" to "Twitch",
        "com.android.chrome" to "Chrome",
        "org.mozilla.firefox" to "Firefox",
        "com.opera.browser" to "Opera",
        "com.brave.browser" to "Brave",
        "com.microsoft.emmx" to "Edge",
        "com.duckduckgo.mobile.android" to "DuckDuckGo",
        "com.pornhub.android" to "PornHub",
        "com.onlyfans.app" to "OnlyFans"
    )

    private val browserPackages = setOf(
        "com.android.chrome", "org.mozilla.firefox", "com.opera.browser",
        "com.microsoft.emmx", "com.brave.browser", "com.duckduckgo.mobile.android",
        "com.opera.mini.android", "com.chrome.beta", "com.chrome.dev",
        "org.mozilla.firefox_beta", "org.mozilla.fenix"
    )

    fun evaluate(packageName: String): AppResult {
        if (packageName in allowlist) return AppResult(false)
        if (packageName in blocklist) return AppResult(true, packageName, knownApps[packageName])

        for (blocked in blocklist) {
            if (packageName.startsWith(blocked.trimEnd('*'))) {
                return AppResult(true, packageName, knownApps[packageName])
            }
        }

        return AppResult(false)
    }

    fun getAppDisplayName(packageName: String): String {
        return knownApps[packageName] ?: packageName
    }

    fun isBrowser(packageName: String): Boolean = packageName in browserPackages

    fun isSocialApp(packageName: String): Boolean {
        return packageName in listOf(
            "com.instagram.android", "com.snapchat.android", "com.twitter.android",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.google.android.youtube", "com.facebook.katana", "com.facebook.orca",
            "com.reddit.frontpage", "com.linkedin.android", "com.pinterest",
            "com.tumblr", "com.twitch.tv", "com.discord", "com.whatsapp",
            "org.telegram.messenger"
        )
    }

    fun isPornApp(packageName: String): Boolean {
        return packageName in listOf(
            "com.pornhub.android", "com.onlyfans.app", "com.xvideos.android",
            "com.xnxx.android", "com.youporn.android"
        )
    }

    fun getAllKnownPackages(): Map<String, String> = knownApps

    companion object {
        fun sanitizePackageName(name: String): String {
            return name.trim().lowercase()
        }
    }
}
