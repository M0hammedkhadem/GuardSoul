package com.agon.app.data

import kotlinx.serialization.Serializable

@Serializable
data class JournalEntry(
    val id: Long,
    val timestamp: Long,
    val mood: Int,
    val triggers: List<String>,
    val text: String,
)

@Serializable
data class AppBlockState(
    val fullBlock: Boolean = false,
    val shortsBlock: Boolean = false,
)

data class BlockableApp(val id: String, val name: String, val hasShorts: Boolean)

/**
 * Smart catalog: apps that contain a short-video feed (Reels/Shorts/Spotlight)
 * expose BOTH options; apps without one — or apps that are 100% short clips
 * (TikTok, Likee) where "shorts only" is meaningless — expose full block only.
 */
val blockableApps = listOf(
    BlockableApp("facebook", "فيسبوك", true),
    BlockableApp("instagram", "إنستغرام", true),
    BlockableApp("youtube", "يوتيوب", true),
    BlockableApp("snapchat", "سناب شات", true),
    BlockableApp("tiktok", "تيك توك", false),
    BlockableApp("likee", "لايكي", false),
    BlockableApp("x", "إكس (تويتر)", false),
    BlockableApp("reddit", "ريديت", false),
    BlockableApp("pinterest", "بينترست", false),
    BlockableApp("twitch", "تويتش", false),
    BlockableApp("telegram", "تيليغرام", false),
    BlockableApp("whatsapp", "واتساب", false),
)

val searchEngineNames = listOf("Google", "Bing", "YouTube", "DuckDuckGo", "Yahoo")

fun engineDisplayName(name: String): String =
    if (name == "Google" || name == "YouTube") "بحث $name" else name

data class ContentFilter(val key: String, val title: String, val desc: String)

val contentFilterList = listOf(
    ContentFilter("images", "تصفية الصور الصريحة", "حجب الصور الإباحية من النتائج"),
    ContentFilter("videos", "تصفية الفيديوهات", "حجب مقاطع الفيديو للبالغين"),
    ContentFilter("sites", "حجب المواقع الإباحية", "منع فتح المواقع المصنفة للبالغين"),
    ContentFilter("keywords", "تصفية الكلمات المفتاحية", "حجب البحث بكلمات ذات محتوى صريح"),
)

// 5 engines + 4 content filters
const val TOTAL_SAFE_SEARCH = 9

// One active protection per app (full XOR shorts) + search engines.
val TOTAL_PROTECTIONS = blockableApps.size + searchEngineNames.size

data class Quote(val text: String, val source: String)

val quotes = listOf(
    Quote("وَمَن جَاهَدَ فَإِنَّمَا يُجَاهِدُ لِنَفْسِهِ", "القرآن الكريم"),
    Quote("وَمَن يَتَّقِ اللَّهَ يَجْعَل لَّهُ مَخْرَجًا وَيَرْزُقْهُ مِنْ حَيْثُ لَا يَحْتَسِبُ", "سورة الطلاق"),
    Quote("إِنَّ اللَّهَ لَا يُغَيِّرُ مَا بِقَوْمٍ حَتَّىٰ يُغَيِّرُوا مَا بِأَنفُسِهِمْ", "سورة الرعد"),
    Quote("وَالَّذِينَ جَاهَدُوا فِينَا لَنَهْدِيَنَّهُمْ سُبُلَنَا", "سورة العنكبوت"),
    Quote("قُل لِّلْمُؤْمِنِينَ يَغُضُّوا مِنْ أَبْصَارِهِمْ وَيَحْفَظُوا فُرُوجَهُمْ ۚ ذَٰلِكَ أَزْكَىٰ لَهُمْ", "سورة النور"),
    Quote("كل يوم تصمد فيه هو انتصار جديد، والصبر مفتاح الفرج", "حكمة"),
    Quote("أنت أقوى من رغبتك اللحظية.. الرغبة موجة تصل قمتها ثم تنكسر", "تذكير"),
)

val delayOptions = listOf("بدون تأخير", "10 دقائق", "ساعة واحدة", "24 ساعة")

val moods = listOf(
    "😔" to "متضايق",
    "😟" to "قلق",
    "😐" to "محايد",
    "🙂" to "جيد",
    "😄" to "ممتاز",
)

val triggerOptions = listOf("الملل", "الوحدة", "التوتر", "السهر", "وسائل التواصل", "مشاعر سلبية")

// ---------- Lists (black / white × words / sites / apps) ----------

enum class ListCategory { WORDS, SITES, APPS }

val defaultBlackWords = listOf(
    // English
    "porn", "porno", "xxx", "sex video", "nsfw", "hentai", "milf",
    "camgirl", "chaturbate", "onlyfans", "xnxx", "xvideos", "redtube",
    "brazzers", "nude", "naked girls", "erotic", "escort", "fetish",
    "bdsm", "blowjob", "anal", "boobs", "threesome", "stripchat",
    "rule34", "lewd", "18+", "+18", "adult movies", "hot girls",
    // Arabic
    "إباحي", "إباحية", "اباحي", "سكس", "جنس عنيف", "نيك", "طيز",
    "بزاز", "عاهرة", "شرموطة", "شراميط", "مثيرة ساخنة", "بنات سكس",
    "افلام سكس", "أفلام للكبار", "سحاق", "لواط", "عاريات", "تعري",
    "مقاطع ساخنة", "رقص مثير", "بدون ملابس",
)

val defaultBlackSites = listOf(
    "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
    "youporn.com", "spankbang.com", "chaturbate.com", "onlyfans.com", "stripchat.com",
    "brazzers.com", "bongacams.com", "livejasmin.com", "cam4.com", "camsoda.com",
    "myfreecams.com", "motherless.com", "rule34.xxx", "e-hentai.org", "nhentai.net",
    "hqporner.com", "eporner.com", "porntrex.com", "txxx.com", "beeg.com",
    "tnaflix.com", "drtuber.com", "sex.com", "fapello.com", "erome.com",
    "javhd.com", "hclips.com", "upornia.com", "thumbzilla.com", "porn300.com",
    "sxyprn.com", "noodlemagazine.com", "paradisehill.cc", "vjav.com", "shameless.com",
)

val defaultWhiteSites = listOf("quran.com", "wikipedia.org", "khanacademy.org")

fun dayLabel(n: Long): String = when {
    n == 1L -> "يوم"
    n == 2L -> "يومان"
    n in 3..10 -> "أيام"
    else -> "يوماً"
}

fun dayLabel(n: Int): String = dayLabel(n.toLong())

fun milestoneName(days: Int): String = when (days) {
    1 -> "يوم واحد"
    3 -> "ثلاثة أيام"
    7 -> "أسبوع كامل"
    14 -> "أسبوعان"
    30 -> "ثلاثون يومًا"
    60 -> "ستون يومًا"
    90 -> "تسعون يومًا"
    180 -> "ستة أشهر"
    365 -> "سنة كاملة"
    else -> "$days ${dayLabel(days)}"
}
