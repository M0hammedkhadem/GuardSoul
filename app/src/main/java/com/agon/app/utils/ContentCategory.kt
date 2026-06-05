package com.agon.app.utils

/**
 * Content categories for app classification. We mirror the Qustodio +
 * Net Nanny taxonomy since those are the most recognizable for parents:
 *
 *  - "Social" implies interaction with a public feed
 *  - "Messaging" implies private DMs
 *  - "Entertainment" implies passive video consumption
 *  - "Games" implies an explicit Game-Engine activity
 *  - "Porn" is the high-risk bucket — anything that *looks* like it
 *    is bucketed here and blocked regardless of toggle state.
 *
 * The enum ordinal is used as the SQLite primary key for the user's
 * category override table, so adding new categories *must* go at the
 * end (don't re-order).
 */
enum class ContentCategory(val displayName: String) {
    SOCIAL_MEDIA("Social Media"),
    MESSAGING("Messaging"),
    ENTERTAINMENT("Video & Streaming"),
    MUSIC("Music & Audio"),
    GAMES("Games"),
    DATING("Dating"),
    NEWS("News"),
    EDUCATION("Education"),
    PRODUCTIVITY("Productivity"),
    SHOPPING("Shopping"),
    BROWSERS("Browsers"),
    EMAIL("Email"),
    HEALTH_FITNESS("Health & Fitness"),
    PHOTO_VIDEO("Photo & Video"),
    BOOKS("Books & Reading"),
    FINANCE("Finance & Banking"),
    NAVIGATION("Maps & Navigation"),
    FOOD_DRINK("Food & Drink"),
    TRAVEL("Travel"),
    WEATHER("Weather"),
    SPORTS("Sports"),
    ANIME_MANGA("Anime & Manga"),
    PORN("Adult Content"),
    GAMBLING("Gambling"),
    VPN_PROXY("VPN & Proxy"),
    REFERENCE("Reference"),
    NOTES("Notes & Lists"),
    AI_ASSISTANTS("AI Assistants"),
    CLOUD_STORAGE("Cloud Storage"),
    FORUMS("Forums & Communities"),
    KIDS("Kids"),
    OTHER("Other")
}
