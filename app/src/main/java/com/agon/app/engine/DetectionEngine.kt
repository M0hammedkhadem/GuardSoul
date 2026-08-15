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
    val cause: BlockCause = BlockCause.APP,
    /** For APP blocks: the offending package — drives the ferocity watchdog. */
    val targetPackage: String? = null,
    /** false = don't auto-execute BACK/HOME; the overlay alone handles it. */
    val autoAction: Boolean = true,
    /** true = fully opaque overlay (camouflage mode for the NSFW filter). */
    val opaqueOverlay: Boolean = false,
    /** true = show a "continue anyway" secondary button on the shield. */
    val allowContinue: Boolean = false,
)

/** Why a block fired — selects the motivational message pool. */
enum class BlockCause { APP, SHORTS, KEYWORD, SITE, UNSAFE_SEARCH, NSFW, TAMPER }

/**
 * Rotating motivational messages per cause. The overlay title stays a plain
 * cause statement (no attempt numbers); the message changes on every block
 * so repetition never feels mechanical.
 */
object BlockMessages {

    private val pools: Map<BlockCause, List<String>> = mapOf(
        BlockCause.APP to listOf(
            "قررت بنفسك حظر هذا التطبيق لحماية وقتك ونقائك. ارجع لشيء مفيد 💪",
            "الوقت الذي كان سيضيع هنا، استثمره في هدفك الحقيقي 🌱",
            "إرادتك أقوى من هذه اللحظة — لا تتراجع عن قرارك 🔥",
            "كل مرة تصمد فيها تزداد قوة. واصل الطريق 🛡️",
        ),
        BlockCause.SHORTS to listOf(
            "هذه الحفرة تبتلع ساعاتك — تمت إعادتك للصفحة الرئيسية ✓",
            "دقيقة واحدة تتحول لساعة دون أن تشعر. وقتك أغلى 🕐",
            "عقلك يستحق محتوى يبنيه، لا مقاطع تشتته 🌟",
            "أنت من يتحكم بالتطبيق، لا العكس. أحسنت الرجوع 💪",
        ),
        BlockCause.KEYWORD to listOf(
            "ظهرت كلمة محظورة على الشاشة — تم إرجاعك فورًا. عيناك أمانة 🛡️",
            "لا تطارد السراب — أنت أقوى من هذه اللحظة 💪",
            "لحظة الفضول تمر، وقرارك الصحيح يبقى. واصل 🌱",
            "حماك الله من شر ما ظهر — استمر في طريق النقاء ✨",
        ),
        BlockCause.SITE to listOf(
            "هذا الموقع في قائمتك السوداء. تم إيقاف التصفح ✓",
            "أغلقنا الباب الذي قررت أنت إغلاقه. ثبات ممتاز 🔒",
            "تذكر لماذا وضعت هذا الموقع في القائمة السوداء 🛡️",
            "كل إغلاق لهذا الباب هو انتصار جديد لك 🔥",
        ),
        BlockCause.UNSAFE_SEARCH to listOf(
            "محرك البحث يعمل بدون SafeSearch — تم إيقاف الصفحة لحمايتك ✓",
            "البحث الآمن درعك الأول — فعّله قبل المتابعة 🛡️",
        ),
        BlockCause.NSFW to listOf(
            "رصد الذكاء الاصطناعي محتوى غير لائق وحجبه فورًا — عيناك أمانة، وغض البصر يورث نور القلب ✨",
            "لحظة صبر واحدة تحميك من ساعات من الندم. أحسنت الابتعاد 🛡️",
            "ما تراه يسكن ذاكرتك — اختر لعقلك صورًا تستحق البقاء 🌱",
            "⸵قُل لِّلْمُؤْمِنِينَ يَغُضُّوا مِنْ أَبْصَارِهِمْ⸴ — ثبتك الله 💚",
        ),
        BlockCause.TAMPER to listOf(
            "منع إلغاء التثبيت مفعل والدرع قائم — لا يمكن إزالة التطبيق أو إيقافه الآن 🔒",
        ),
    )

    private val counters = HashMap<BlockCause, Int>()

    /** Next message for [cause] — rotates through the pool on every call. */
    fun next(cause: BlockCause): String {
        val pool = pools.getValue(cause)
        val idx = counters.getOrDefault(cause, 0)
        counters[cause] = idx + 1
        return pool[idx % pool.size]
    }
}

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
    private var lastKeywordCheckAt = 0L

    /** Per-package snooze set by the "continue anyway" button (2 minutes). */
    private val keywordSnooze = HashMap<String, Long>()

    /** Whitelisted apps are exempt from EVERY kind of blocking. */
    fun isWhitelistedApp(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return settings.whiteApps.any { it.isNotBlank() && pkg.contains(it.trim().lowercase()) }
    }

    /** Called when the user chooses to continue despite a keyword warning. */
    fun snoozeKeywords(packageName: String, now: Long) {
        keywordSnooze[packageName.lowercase()] = now + 120_000
    }

    fun onPackageChanged(packageName: String) {
        if (packageName != currentPackage) {
            currentPackage = packageName
            fbBrain.reset()
        }
    }

    /**
     * Full app block — FIERCE mode. Applies to apps fully blocked from the
     * protection page AND apps in the black apps list, identically:
     *  - evaluated on EVERY accessibility event from the app,
     *  - ALWAYS bypasses the long suppress window (only a 600ms micro-window
     *    absorbs a single launch animation's event storm),
     *  - carries the offending package so the service can run a watchdog
     *    that keeps kicking to HOME until the app is really gone.
     */
    fun checkFullBlock(packageName: String, now: Long, isLaunchEvent: Boolean = false): BlockDecision? {
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
            cause = BlockCause.APP,
            goHome = true,
            now = now,
            bypassSuppress = true, // fierce: every event re-blocks
            targetPackage = packageName,
        )
    }

    /**
     * Screen-wide keyword guard: blocks (and BACKs out) whenever ANY
     * blacklisted word is visible on screen — in any app, not just search
     * bars. Controlled by the "keywords" content filter toggle.
     */
    fun checkScreenKeywords(root: AccessibilityNodeInfo?, packageName: String, now: Long): BlockDecision? {
        if (!settings.shieldActive) return null
        if (!settings.keywordFilterOn()) return null
        if (settings.blackWords.isEmpty()) return null
        val pkg = packageName.lowercase()
        if (pkg == "com.android.systemui") return null
        if (isWhitelistedApp(packageName)) return null
        // "Continue anyway" snooze for this package still active?
        keywordSnooze[pkg]?.let { until ->
            if (now < until) return null else keywordSnooze.remove(pkg)
        }
        // Whitelisted SITE open in a browser -> no keyword blocking either.
        if (BrowserGuard.isBrowser(packageName)) {
            BrowserGuard.extractUrl(root, packageName)?.let { url ->
                val host = url.removePrefix("https://").removePrefix("http://")
                    .removePrefix("www.").substringBefore('/')
                if (settings.whiteSites.any { it.isNotBlank() && host.contains(it.trim().lowercase()) }) {
                    return null
                }
            }
        }
        if (now - lastKeywordCheckAt < 800) return null
        lastKeywordCheckAt = now

        val sb = StringBuilder()
        harvestText(root ?: return null, sb, 0)
        // High-accuracy matching: word boundaries + Arabic normalization +
        // white-words subtraction (KeywordMatcher).
        val hit = KeywordMatcher.findMatch(sb.toString(), settings.blackWords, settings.whiteWords)
            ?: return null
        val continueOption = settings.keywordContinueOption
        return decision(
            target = "kwscreen:${hit.trim().lowercase()}",
            title = "كلمات محظورة",
            cause = BlockCause.KEYWORD,
            goHome = false,
            now = now,
            targetPackage = packageName,
            allowContinue = continueOption,
            autoAction = !continueOption,
        )
    }

    // ---------- Uninstall guard ----------

    private val installerPackages = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.android.settings",
        "com.samsung.android.packageinstaller",
        "com.miui.packageinstaller",
    )

    private val uninstallWords = listOf(
        "uninstall", "إلغاء التثبيت", "الغاء التثبيت", "إزالة التطبيق",
        "force stop", "إيقاف إجباري", "ايقاف اجباري",
        "clear data", "clear storage", "مسح البيانات", "محو البيانات",
        "إيقاف تشغيل", "disable",
    )

    private val appIdentityWords = listOf("طريق النقاء", "com.agon.app")

    /**
     * Tamper protection: while the shield is active with the uninstall guard
     * on, any system screen that shows OUR app together with an uninstall /
     * force-stop / clear-data action gets blocked instantly.
     */
    fun checkUninstallGuard(root: AccessibilityNodeInfo?, packageName: String, now: Long): BlockDecision? {
        if (!settings.shieldActive || !settings.uninstallGuard) return null
        if (packageName !in installerPackages) return null
        val text = StringBuilder()
        harvestText(root ?: return null, text, 0)
        val page = text.toString().lowercase()
        if (!appIdentityWords.any { page.contains(it.lowercase()) }) return null
        if (!uninstallWords.any { page.contains(it) }) return null
        return decision(
            target = "tamper:uninstall",
            title = "محاولة إزالة الحماية",
            cause = BlockCause.TAMPER,
            goHome = true,
            now = now,
        )
    }

    private fun harvestText(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (depth > 30 || out.length > 4000) return
        node.text?.let { out.append(it).append(' ') }
        node.contentDescription?.let { out.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            harvestText(child, out, depth + 1)
        }
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
        // Node evidence about the tab strip: distinguishes "strip painted
        // black (Reels)" from "strip absent over a black background" (full
        // screen video player / photo viewer) — the confirmed false-positive.
        val strip = TabStripLocator.locate(root, screenW, screenH)
        val rail = ActionRailDetector.detect(root, screenW, screenH)

        if (!fbBrain.evaluate(tabBar, strip, rail, now)) return null
        return decision(
            target = "reels:facebook",
            title = "المقاطع القصيرة محظورة",
            cause = BlockCause.SHORTS,
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
            cause = BlockCause.SHORTS,
            goHome = false,
            now = now,
        )
    }

    /** Browser URL judgement: blacklist, keywords, SafeSearch. */
    fun checkBrowser(root: AccessibilityNodeInfo?, packageName: String, now: Long): BlockDecision? {
        if (!settings.shieldActive) return null
        if (!BrowserGuard.isBrowser(packageName)) return null
        // "Continue anyway" snooze covers browser keyword verdicts too.
        keywordSnooze[packageName.lowercase()]?.let { until ->
            if (now < until) return null else keywordSnooze.remove(packageName.lowercase())
        }
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
                cause = BlockCause.SITE,
                goHome = false,
                now = now,
            )
            is BrowserVerdict.BlockedKeyword -> {
                val continueOption = settings.keywordContinueOption
                decision(
                    target = "keyword:${verdict.keyword}",
                    title = "كلمات محظورة",
                    cause = BlockCause.KEYWORD,
                    goHome = false,
                    now = now,
                    targetPackage = packageName,
                    allowContinue = continueOption,
                    autoAction = !continueOption,
                )
            }
            is BrowserVerdict.UnsafeSearch -> decision(
                target = "unsafe:${verdict.engine}",
                title = "البحث الآمن مطلوب",
                cause = BlockCause.UNSAFE_SEARCH,
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
        // bypassSuppress: NSFW checks are already throttled to one per 2.5s,
        // so the governor's long window must never swallow a confirmed hit —
        // the shield overlay MUST appear for every NSFW block.
        // Camouflage mode: opaque overlay covers the content, no auto-kick;
        // re-detection keeps re-covering while the content stays on screen.
        val blur = settings.nsfwBlurMode
        return decision(
            target = "nsfw",
            title = "محتوى غير لائق",
            cause = BlockCause.NSFW,
            goHome = false,
            now = now,
            bypassSuppress = true,
            autoAction = !blur,
            opaque = blur,
        )
    }

    /**
     * Route every block through the governor. Repeats are never swallowed —
     * escalation stays fierce (HOME + longer overlay + extra BACK), but the
     * overlay NEVER shows attempt numbers: the title is always the plain
     * cause statement and the motivational message rotates on every block.
     */
    private fun decision(
        target: String,
        title: String,
        cause: BlockCause,
        goHome: Boolean,
        now: Long,
        bypassSuppress: Boolean = false,
        targetPackage: String? = null,
        autoAction: Boolean = true,
        opaque: Boolean = false,
        allowContinue: Boolean = false,
    ): BlockDecision? {
        val grant = governor.request(target, now, bypassSuppress) ?: return null
        // Shorts/Reels blocks must keep the user INSIDE the app — we only back
        // them out of the shorts section to the app's main page, never to the
        // launcher, even when the attempt is repeated.
        val isShorts = cause == BlockCause.SHORTS
        if (isShorts) fbBrain.notifyBlocked(now)
        val label = if (isShorts) "العودة للصفحة الرئيسية" else "أخرجني من هنا"
        val message = BlockMessages.next(cause)
        return if (grant.escalated) {
            BlockDecision(
                title = title,
                message = message,
                // escalation: kick to HOME (except shorts stay in-app; manual
                // modes — continue-option keywords / camouflage NSFW — keep
                // their configured behaviour but with a longer overlay).
                goHome = if (autoAction) !isShorts else goHome,
                overlayMs = (4500L + grant.repeatCount * 1500L).coerceAtMost(9000L),
                repeatCount = grant.repeatCount,
                buttonLabel = label,
                cause = cause,
                targetPackage = targetPackage,
                autoAction = autoAction,
                opaqueOverlay = opaque,
                allowContinue = allowContinue,
            )
        } else {
            BlockDecision(
                title = title,
                message = message,
                goHome = goHome,
                overlayMs = 3500L,
                repeatCount = 0,
                buttonLabel = label,
                cause = cause,
                targetPackage = targetPackage,
                autoAction = autoAction,
                opaqueOverlay = opaque,
                allowContinue = allowContinue,
            )
        }
    }
}
