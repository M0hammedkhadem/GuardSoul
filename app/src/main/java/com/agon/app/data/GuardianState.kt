package com.agon.app.data

data class GuardianState(
    val isShieldActive: Boolean = false,
    val isTrialModeActive: Boolean = false,
    val deactivationDelayMinutes: Long = 7 * 24 * 60,
    val countdownEndTime: Long? = null,
    val shieldActivatedAt: Long? = null,
    val blocksCount: Int = 0,
    
    val accessibilityGranted: Boolean = false,
    val vpnGranted: Boolean = false,
    val deviceAdminGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val usageAccessGranted: Boolean = false,

    val instagramBlocked: Boolean = false,
    val snapchatBlocked: Boolean = false,
    val twitterBlocked: Boolean = false,
    val tiktokBlocked: Boolean = false,
    val youtubeMode: String = "off",
    val facebookMode: String = "off",
    
    val pornBlockerActive: Boolean = false,
    val aiExplorerActive: Boolean = false,
    val uninstallProtectionActive: Boolean = false,

    val blacklistKeywords: List<String> = listOf(
        "porn", "xxx", "sex", "nude", "naked", "hentai", "adult", "onlyfans",
        "escort", "cam", "masturbat", "erotic", "lewd", "nsfw", "rule34",
        "milf", "anal", "blowjob", "hardcore", "softcore",
        "اباحية", "جنس", "عري", "سكس", "افلام ساخنة", "اثارة جنسية"
    ),
    val blacklistWebsites: List<String> = listOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "redtube.com", "youporn.com",
        "xhamster.com", "tube8.com", "spankbang.com", "eporner.com", "tnaflix.com",
        "drtuber.com", "slutload.com", "beeg.com", "hclips.com",
        "nhentai.net", "hanime.tv", "hentaihaven.xxx", "gelbooru.com", "rule34.xxx",
        "onlyfans.com", "chaturbate.com", "livejasmin.com", "cam4.com",
        "myfreecams.com", "bongacams.com"
    ),
    val blacklistApps: List<String> = emptyList(),
    
    val whitelistKeywords: List<String> = emptyList(),
    val whitelistWebsites: List<String> = emptyList(),
    val whitelistApps: List<String> = emptyList()
) {
    val permissionsGranted: Boolean
        get() = accessibilityGranted && vpnGranted && deviceAdminGranted && overlayGranted && usageAccessGranted
}
