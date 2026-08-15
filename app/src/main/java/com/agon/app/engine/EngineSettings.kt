package com.agon.app.engine

import com.agon.app.data.AppBlockState

/** Immutable snapshot of everything the brain needs to make decisions. */
data class EngineSettings(
    val shieldActive: Boolean = false,
    val aiImageFilter: Boolean = false,
    val uninstallGuard: Boolean = false,
    val appBlocks: Map<String, AppBlockState> = emptyMap(),
    val searchEngines: Map<String, Boolean> = emptyMap(),
    val contentFilters: Map<String, Boolean> = emptyMap(),
    val blackWords: List<String> = emptyList(),
    val blackSites: List<String> = emptyList(),
    val blackApps: List<String> = emptyList(),
    val whiteWords: List<String> = emptyList(),
    val whiteSites: List<String> = emptyList(),
    val whiteApps: List<String> = emptyList(),
    /** Show a "continue anyway" button on keyword-block shields (default off). */
    val keywordContinueOption: Boolean = false,
    /** NSFW filter action: false = kick out (default), true = camouflage overlay. */
    val nsfwBlurMode: Boolean = false,
) {
    fun keywordFilterOn(): Boolean = contentFilters["keywords"] == true
    fun siteFilterOn(): Boolean = contentFilters["sites"] == true
}

/** Maps installed package names to our logical app ids. */
object AppPolicy {
    private val packageToApp = mapOf(
        "com.instagram.android" to "instagram",
        "com.instagram.lite" to "instagram",
        "com.zhiliaoapp.musically" to "tiktok",
        "com.ss.android.ugc.trill" to "tiktok",
        "com.zhiliaoapp.musically.go" to "tiktok",
        "com.google.android.youtube" to "youtube",
        "com.facebook.katana" to "facebook",
        "com.facebook.lite" to "facebook",
        "com.snapchat.android" to "snapchat",
        "com.twitter.android" to "x",
        "com.whatsapp" to "whatsapp",
        "com.whatsapp.w4b" to "whatsapp",
        "org.telegram.messenger" to "telegram",
        "org.telegram.messenger.web" to "telegram",
        "com.reddit.frontpage" to "reddit",
        "com.pinterest" to "pinterest",
        "video.like" to "likee",
        "tv.twitch.android.app" to "twitch",
    )

    fun appIdFor(packageName: String): String? = packageToApp[packageName]

    fun isFacebook(packageName: String): Boolean = appIdFor(packageName) == "facebook"

    /** Apps where the AI image filter takes screenshot samples. */
    fun isRiskyForNsfw(packageName: String): Boolean =
        packageToApp.containsKey(packageName) || BrowserGuard.isBrowser(packageName)
}
