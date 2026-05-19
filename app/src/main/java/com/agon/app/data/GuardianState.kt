package com.agon.app.data

import java.util.UUID

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

    val blacklistKeywords: List<String> = defaultBlacklistKeywords,
    val blacklistWebsites: List<String> = defaultBlacklistWebsites,
    val blacklistApps: List<String> = emptyList(),

    val whitelistKeywords: List<String> = emptyList(),
    val whitelistWebsites: List<String> = emptyList(),
    val whitelistApps: List<String> = emptyList(),

    // F4: PIN Protection
    val pinCode: String? = null,
    val appUnlocked: Boolean = false,

    // F1: Onboarding
    val onboardingCompleted: Boolean = false,
    val profileName: String = "",

    // F6: Trial Expiration
    val installTimestamp: Long? = null,

    // F2: Schedule-based blocking
    val scheduleRules: List<ScheduleRule> = emptyList(),

    // F3: Daily time limits
    val dailyTimeLimits: List<DailyTimeLimit> = emptyList(),

    // F5: Usage statistics
    val blockEvents: List<BlockEvent> = emptyList(),
) {
    // Trial expiry computed property
    val isTrialExpired: Boolean
        get() {
            if (!isTrialModeActive || installTimestamp == null) return false
            val elapsed = System.currentTimeMillis() - installTimestamp
            return elapsed > 7 * 24 * 60 * 60 * 1000L
        }
    val permissionsGranted: Boolean
        get() = accessibilityGranted && vpnGranted && deviceAdminGranted && overlayGranted && usageAccessGranted

    val blockedPackageNames: Set<String>
        get() = buildSet {
            addAll(blacklistApps)
            if (instagramBlocked) add("com.instagram.android")
            if (snapchatBlocked) add("com.snapchat.android")
            if (twitterBlocked) add("com.twitter.android")
            if (tiktokBlocked) {
                add("com.zhiliaoapp.musically")
                add("com.ss.android.ugc.trill")
            }
            if (youtubeMode == "full") add("com.google.android.youtube")
            if (facebookMode == "full") {
                add("com.facebook.katana")
                add("com.facebook.orca")
                add("com.facebook.lite")
                add("com.facebook.mlite")
            }
        }
}

data class ScheduleRule(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val daysOfWeek: Set<Int> = emptySet(), // 1=Mon .. 7=Sun
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 8,
    val endMinute: Int = 0
)

data class DailyTimeLimit(
    val packageName: String = "",
    val appLabel: String = "",
    val dailyMinutes: Int = 30
)

data class BlockEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String = "",
    val blockType: String = "manual" // "manual", "porn", "ai_scan", "time_limit"
)

val defaultBlacklistKeywords = listOf(
    "porn", "xxx", "sex", "nude", "naked", "hentai", "adult", "onlyfans",
    "escort", "cam", "masturbat", "erotic", "lewd", "nsfw", "rule34",
    "milf", "anal", "blowjob", "hardcore", "softcore",
    "اباحية", "جنس", "عري", "سكس", "افلام ساخنة", "اثارة جنسية"
)

val defaultBlacklistWebsites = listOf(
    "pornhub.com", "xvideos.com", "xnxx.com", "redtube.com", "youporn.com",
    "xhamster.com", "tube8.com", "spankbang.com", "eporner.com", "tnaflix.com",
    "drtuber.com", "slutload.com", "beeg.com", "hclips.com",
    "nhentai.net", "hanime.tv", "hentaihaven.xxx", "gelbooru.com", "rule34.xxx",
    "onlyfans.com", "chaturbate.com", "livejasmin.com", "cam4.com",
    "myfreecams.com", "bongacams.com"
)