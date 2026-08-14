package com.agon.app.engine

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo

/** A blocking decision emitted by the brain. */
data class BlockDecision(
    val title: String,
    val message: String,
    val goHome: Boolean, // true = kick to launcher, false = just back out
    val overlayMs: Long = 3500L,
    val repeatCount: Int = 0,
    val buttonLabel: String = "أخرجني من هنا",
)

/**
 * The decision core. Pure logic — no Android service dependencies — so every
 * rule is independently testable. The AccessibilityService feeds it events,
 * node trees and (optionally) screenshots; it answers with BlockDecisions.
 *
 * Repeat handling: every decision goes through [BlockGovernor]. A user who
 * re-attempts the same blocked action is ALWAYS blocked again, and repeats
 * escalate (HOME instead of BACK, longer overlay, firmer message).
 */
class DetectionEngine(private val nsfw: NsfwClassifier) {

    @Volatile
    var settings: EngineSettings = EngineSettings()

    private val fbBrain = FacebookReelsBrain()
    private val governor = BlockGovernor()
    private var currentPackage: String = ""

    // Throttles (detection sampling only — never suppress a confirmed block)
    private var lastFbCheckAt = 0L
    private var lastGenericCheckAt = 0L
    private var lastNsfwCheckAt = 0L

    fun onPackageChanged(packageName: String) {
        if (packageName != currentPackage) {
            currentPackage = packageName
            fbBrain.reset()
        }
    }

    /** Full app block — cheap lookups, evaluated on every event. */
    fun checkFullBlock(packageName: String, now: Long): BlockDecision? {
        if (!settings.shieldActive) return null
        val pkg = packageName.lowercase()
        // Whitelisted apps are always exempt.
        if (settings.whiteApps.any { it.isNotBlank() && pkg.contains(it.trim().lowercase()) }) return null

        val appId = AppPolicy.appIdFor(packageName)
        val blockedByToggle = appId != null && settings.appBlocks[appId]?.fullBlock == true
        val blockedByList = settings.blackApps.any {
            it.isNotBlank() && it.trim().length >= 3 && pkg.contains(it.trim().lowercase())
        }
        if (!blockedByToggle && !blockedByList) return null
        return decision(
            target = "app:${appId ?: pkg}",
            title = "التطبيق محظور",
            message = "قررت بنفسك حظر هذا التطبيق لحماية وقتك ونقائك. ارجع لشيء مفيد 💪",
            goHome = true,
            now = now,
        )
    }

    /** Should we sample a screenshot right now? Which analyses are needed? */
    fun screenshotNeeds(packageName: String, now: Long): ScreenshotNeeds {
        if (!settings.shieldActive) return ScreenshotNeeds(tabBar = false, nsfw = false)
        val shortsOn = AppPolicy.isFacebook(packageName) &&
            settings.appBlocks["facebook"]?.shortsBlock == true &&
            now - lastFbCheckAt >= 700
        val nsfwOn = settings.aiImageFilter && nsfw.isReady &&
            AppPolicy.isRiskyForNsfw(packageName) &&
            now - lastNsfwCheckAt >= 2500
        return ScreenshotNeeds(tabBar = shortsOn, nsfw = nsfwOn)
    }

    data class ScreenshotNeeds(val tabBar: Boolean, val nsfw: Boolean) {
        val any: Boolean get() = tabBar || nsfw
    }

    /**
     * Facebook Reels — mechanism #1 (tab strip pixels) + #2 (action rail)
     * fused inside FacebookReelsBrain.
     */
    fun checkFacebookReels(
        root: AccessibilityNodeInfo?,
        screenshot: Bitmap?,
        statusBarPx: Int,
        densityDpi: Int,
        screenW: Int,
        screenH: Int,
        now: Long,
    ): BlockDecision? {
        if (!settings.shieldActive) return null
        if (settings.appBlocks["facebook"]?.shortsBlock != true) return null
        lastFbCheckAt = now

        val tabBar = screenshot?.let { TabBarAnalyzer.analyze(it, statusBarPx, densityDpi) }
        val rail = ActionRailDetector.detect(root, screenW, screenH)

        if (!fbBrain.evaluate(tabBar, rail, now)) return null
        return decision(
            target = "reels:facebook",
            title = "مقاطع ريلز محظورة",
            message = "رصد العقل دخولك للمقاطع القصيرة — هذه الحفرة تبتلع ساعاتك. تمت إعادتك للصفحة الرئيسية ✓",
            goHome = false,
            now = now,
        )
    }

    /** YouTube Shorts / Instagram Reels via player ids + nav pivot + action rail. */
    fun checkGenericShorts(
        root: AccessibilityNodeInfo?,
        packageName: String,
        screenW: Int,
        screenH: Int,
        now: Long,
    ): BlockDecision? {
        if (!settings.shieldActive) return null
        val appId = AppPolicy.appIdFor(packageName) ?: return null
        if (appId != "youtube" && appId != "instagram") return null
        if (settings.appBlocks[appId]?.shortsBlock != true) return null
        if (now - lastGenericCheckAt < 600) return null
        lastGenericCheckAt = now
        if (!GenericShortsDetector.detect(root, packageName, screenW, screenH)) return null
        return decision(
            target = "shorts:$appId",
            title = "المقاطع القصيرة محظورة",
            message = "حظرت المقاطع القصيرة في هذا التطبيق بنفسك — تمت إعادتك للصفحة الرئيسية ✓",
            goHome = false,
            now = now,
        )
    }

    /** Browser URL judgement: blacklist, keywords, SafeSearch. */
    fun checkBrowser(root: AccessibilityNodeInfo?, packageName: String, now: Long): BlockDecision? {
        if (!settings.shieldActive) return null
        if (!BrowserGuard.isBrowser(packageName)) return null
        val url = BrowserGuard.extractUrl(root, packageName) ?: return null
        return when (val verdict = BrowserGuard.judge(
            url = url,
            blacklist = settings.blackSites,
            whitelist = settings.whiteSites,
            keywords = settings.blackWords,
            keywordFilterOn = settings.keywordFilterOn(),
            siteFilterOn = settings.siteFilterOn(),
            safeEngines = settings.searchEngines,
        )) {
            is BrowserVerdict.BlockedDomain -> decision(
                target = "site:${verdict.domain}",
                title = "موقع محظور",
                message = "الموقع «${verdict.domain}» في قائمتك السوداء. تم إيقاف التصفح ✓",
                goHome = false,
                now = now,
            )
            is BrowserVerdict.BlockedKeyword -> decision(
                target = "keyword:${verdict.keyword}",
                title = "كلمة بحث محظورة",
                message = "رصد العقل كلمة محظورة في العنوان. لا تطارد السراب — أنت أقوى من هذا 💪",
                goHome = false,
                now = now,
            )
            is BrowserVerdict.UnsafeSearch -> decision(
                target = "unsafe:${verdict.engine}",
                title = "البحث الآمن مطلوب",
                message = "محرك ${verdict.engine} يعمل بدون SafeSearch. تم إيقاف الصفحة لحمايتك ✓",
                goHome = false,
                now = now,
            )
            BrowserVerdict.Allow -> null
        }
    }

    /** AI image filter over a downsampled screenshot. */
    fun checkNsfw(screenshot: Bitmap, now: Long): BlockDecision? {
        if (!settings.shieldActive || !settings.aiImageFilter) return null
        lastNsfwCheckAt = now
        val score = nsfw.nsfwScore(screenshot)
        if (score < NsfwClassifier.BLOCK_THRESHOLD) return null
        return decision(
            target = "nsfw",
            title = "محتوى غير لائق",
            message = "فلتر الذكاء الصناعي رصد محتوى مثيرًا وحجبه فورًا. عيناك أمانة 🛡️",
            goHome = false,
            now = now,
        )
    }

    /**
     * Route every block through the governor. Repeats are never swallowed —
     * they escalate instead.
     */
    private fun decision(
        target: String,
        title: String,
        message: String,
        goHome: Boolean,
        now: Long,
    ): BlockDecision? {
        val grant = governor.request(target, now) ?: return null
        // Shorts/Reels blocks must keep the user INSIDE the app — we only back
        // them out of the shorts section to the app's main page, never to the
        // launcher, even when the attempt is repeated.
        val isShorts = target.startsWith("reels:") || target.startsWith("shorts:")
        if (isShorts) fbBrain.notifyBlocked(now)
        val label = if (isShorts) "العودة للصفحة الرئيسية" else "أخرجني من هنا"
        return if (grant.escalated) {
            val attempts = grant.repeatCount + 1
            BlockDecision(
                title = "توقف — المحاولة رقم $attempts",
                message = if (isShorts) {
                    "أعدت المحاولة نفسها خلال وقت قصير. تذكّر لماذا بدأت هذا الطريق. " +
                        "تمت إعادتك للصفحة الرئيسية للتطبيق 🛡️"
                } else {
                    "أعدت المحاولة نفسها خلال وقت قصير. تذكّر لماذا بدأت هذا الطريق. " +
                        "تم تشديد الحظر وإعادتك للشاشة الرئيسية 🛡️"
                },
                goHome = !isShorts,
                overlayMs = (4500L + grant.repeatCount * 1500L).coerceAtMost(9000L),
                repeatCount = grant.repeatCount,
                buttonLabel = label,
            )
        } else {
            BlockDecision(
                title, message, goHome,
                overlayMs = 3500L,
                repeatCount = 0,
                buttonLabel = label,
            )
        }
    }
}
