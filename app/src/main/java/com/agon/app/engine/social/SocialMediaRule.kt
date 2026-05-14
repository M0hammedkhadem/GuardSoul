package com.agon.app.engine.social

data class SocialMediaRule(
    val platform: SocialPlatform,
    val mode: BlockMode,
    val partialTargets: List<PartialTarget> = emptyList()
)

enum class SocialPlatform(val displayName: String, val packageNames: List<String>, val domains: List<String>) {
    INSTAGRAM("Instagram", listOf("com.instagram.android"), listOf("instagram.com", "cdninstagram.com")),
    SNAPCHAT("Snapchat", listOf("com.snapchat.android"), listOf("snapchat.com")),
    TWITTER("X (Twitter)", listOf("com.twitter.android", "org.joinmastodon.android"), listOf("twitter.com", "x.com", "t.co")),
    TIKTOK("TikTok", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), listOf("tiktok.com", "tiktokcdn.com")),
    YOUTUBE("YouTube", listOf("com.google.android.youtube"), listOf("youtube.com", "youtu.be", "ytimg.com")),
    FACEBOOK("Facebook", listOf("com.facebook.katana", "com.facebook.orca"), listOf("facebook.com", "fb.com", "fbcdn.net", "messenger.com")),
    LINKEDIN("LinkedIn", listOf("com.linkedin.android"), listOf("linkedin.com")),
    REDDIT("Reddit", listOf("com.reddit.frontpage"), listOf("reddit.com", "redd.it")),
    WHATSAPP("WhatsApp", listOf("com.whatsapp"), listOf("whatsapp.net")),
    TELEGRAM("Telegram", listOf("org.telegram.messenger"), listOf("telegram.org", "t.me"))
}

enum class BlockMode(val label: String, val description: String) {
    OFF("No Block", "App is fully accessible"),
    FULL("Full Block", "Prevents opening the app entirely"),
    PARTIAL("Partial Block", "Selectively blocks specific features"),
    TIMED("Timed Block", "Blocks only during scheduled hours"),
    USAGE_LIMIT("Usage Limit", "Allows limited time per day")
}

enum class PartialTarget(val label: String) {
    SHORTS("Block Shorts/Reels"),
    COMMENTS("Block Comments"),
    RECOMMENDATIONS("Block Recommendations"),
    FEED("Block Feed/Newsfeed"),
    NOTIFICATIONS("Block Notifications"),
    STORIES("Block Stories"),
    EXPLORE("Block Explore Tab"),
    SEARCH("Block Search"),
    MESSAGES("Block Messages/DMs"),
    LIVE("Block Live Streams")
}

data class TimeSchedule(
    val enabled: Boolean = false,
    val startHour: Int = 9,
    val endHour: Int = 17,
    val activeDays: Set<DayOfWeek> = DayOfWeek.entries.toSet()
)

enum class DayOfWeek(val index: Int) {
    MONDAY(1), TUESDAY(2), WEDNESDAY(3), THURSDAY(4), FRIDAY(5), SATURDAY(6), SUNDAY(7)
}

data class UsageLimit(
    val enabled: Boolean = false,
    val minutesPerDay: Int = 30,
    val resetHour: Int = 0
)

data class PlatformConfig(
    val mode: BlockMode = BlockMode.OFF,
    val partialTargets: Set<PartialTarget> = emptySet(),
    val schedule: TimeSchedule = TimeSchedule(),
    val usageLimit: UsageLimit = UsageLimit(),
    val blockAcrossApps: Boolean = false,
    val blockAcrossBrowsers: Boolean = false,
    val notifyOnBlock: Boolean = true,
    val passwordBypass: Boolean = false
)
