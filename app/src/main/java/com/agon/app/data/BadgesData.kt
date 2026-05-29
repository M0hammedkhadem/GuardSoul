package com.agon.app.data

data class Badge(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val condition: (xp: Int, level: Int, streak: Int, totalBlocks: Int, daysActive: Int) -> Boolean
) {
    fun isUnlocked(xp: Int, level: Int, streak: Int, totalBlocks: Int, daysActive: Int): Boolean =
        condition(xp, level, streak, totalBlocks, daysActive)
}

data class BadgeWithState(
    val badge: Badge,
    val isUnlocked: Boolean
)

object BadgesData {
    val allBadges = listOf(
        Badge("beginner", "المبتدئ", "ابدأ رحلتك مع GuardSoul", "🌱",
            { _, level, _, _, _ -> level >= 1 }),
        Badge("steadfast", "الصامد", "حافظ على النشاط 7 أيام متتالية", "🔥",
            { _, _, streak, _, _ -> streak >= 7 }),
        Badge("guardian", "الحارس", "احجب 100 محتوى", "🛡️",
            { _, _, _, totalBlocks, _ -> totalBlocks >= 100 }),
        Badge("victorious", "المنتصر", "وصل إلى المستوى 10", "🏆",
            { _, level, _, _, _ -> level >= 10 }),
        Badge("nightowl", "الليلي", "احجب محتوى بعد منتصف الليل", "🌙",
            { _, _, _, _, _ -> true }), // always earns this on first block
        Badge("diligent", "المذاكر", "استخدم التطبيق 30 يوماً", "📚",
            { _, _, _, _, daysActive -> daysActive >= 30 }),
        Badge("defender", "المدافع", "احجب 500 محتوى", "⚔️",
            { _, _, _, totalBlocks, _ -> totalBlocks >= 500 }),
        Badge("champion", "البطل", "وصل إلى المستوى 20", "👑",
            { _, level, _, _, _ -> level >= 20 })
    )
}
