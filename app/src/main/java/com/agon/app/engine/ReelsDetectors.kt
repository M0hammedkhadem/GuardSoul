package com.agon.app.engine

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/** Result of the top-tab-strip pixel analysis (Facebook mechanism #1). */
enum class TabBarState { WHITE, BLACK, HIDDEN }

/**
 * Mechanism #1 — Facebook top tab strip color.
 *
 * Case 1: strip is WHITE  -> any tab except Reels  -> definitive negative.
 * Case 2: strip is BLACK  -> inside the Reels tab  -> strong positive.
 * Case 3: strip is HIDDEN -> scrolled inside Reels -> neutral; mechanism #2 decides.
 */
object TabBarAnalyzer {

    fun analyze(screenshot: Bitmap, statusBarPx: Int, densityDpi: Int): TabBarState {
        val dp = densityDpi / 160f
        val stripTop = (statusBarPx + (4 * dp)).toInt().coerceIn(0, screenshot.height - 2)
        val stripBottom = (stripTop + (48 * dp)).toInt().coerceAtMost(screenshot.height - 1)
        if (stripBottom <= stripTop) return TabBarState.HIDDEN

        var bright = 0
        var dark = 0
        var total = 0
        var lumSum = 0L
        val stepX = (screenshot.width / 24).coerceAtLeast(1)
        val stepY = ((stripBottom - stripTop) / 6).coerceAtLeast(1)

        var y = stripTop
        while (y <= stripBottom) {
            var x = stepX / 2
            while (x < screenshot.width) {
                val p = screenshot.getPixel(x, y)
                val r = p shr 16 and 0xFF
                val g = p shr 8 and 0xFF
                val b = p and 0xFF
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                lumSum += lum
                if (lum >= 190) bright++
                if (lum <= 60) dark++
                total++
                x += stepX
            }
            y += stepY
        }
        if (total == 0) return TabBarState.HIDDEN
        val avg = lumSum / total
        return when {
            avg >= 190 && bright * 100 / total >= 70 -> TabBarState.WHITE
            avg <= 60 && dark * 100 / total >= 70 -> TabBarState.BLACK
            else -> TabBarState.HIDDEN
        }
    }
}

/**
 * Mechanism #2 — the vertical engagement rail.
 *
 * Reels shows Like / Comment / Share / Save icons stacked vertically on one
 * screen edge. Their Y position drifts (multiple entry paths), so we cluster
 * by X alignment with tolerance instead of fixed coordinates and require at
 * least 3 of the 4 distinct categories in a single vertical column.
 */
object ActionRailDetector {

    private const val CAT_LIKE = 0
    private const val CAT_COMMENT = 1
    private const val CAT_SHARE = 2
    private const val CAT_SAVE = 3

    private val likeWords = listOf("like", "أعجبني", "إعجاب", "اعجاب", "لايك", "تفاعل")
    private val commentWords = listOf("comment", "تعليق", "التعليقات", "علق")
    private val shareWords = listOf("share", "مشاركة", "شارك")
    private val saveWords = listOf("save", "حفظ", "احفظ", "remix", "ريمكس")

    private data class Hit(val category: Int, val bounds: Rect)

    fun detect(root: AccessibilityNodeInfo?, screenW: Int, screenH: Int): Boolean {
        if (root == null || screenW <= 0 || screenH <= 0) return false
        val hits = ArrayList<Hit>(12)
        collect(root, hits, screenW, screenH, 0)
        if (hits.size < 3) return false

        // Split into edge columns: far start / far end of the screen.
        val edgeLimit = (screenW * 0.24f)
        val leftRail = hits.filter { it.bounds.centerX() <= edgeLimit }
        val rightRail = hits.filter { it.bounds.centerX() >= screenW - edgeLimit }
        return isVerticalRail(leftRail, screenW, screenH) ||
            isVerticalRail(rightRail, screenW, screenH)
    }

    private fun isVerticalRail(hits: List<Hit>, screenW: Int, screenH: Int): Boolean {
        if (hits.size < 3) return false
        // X alignment cluster with tolerance (icons drift slightly).
        val tolerance = screenW * 0.09f
        val sorted = hits.sortedBy { it.bounds.top }
        for (anchor in sorted) {
            val column = sorted.filter {
                abs(it.bounds.centerX() - anchor.bounds.centerX()) <= tolerance
            }
            val categories = column.map { it.category }.toSet()
            if (categories.size >= 3) {
                // Sanity: the column must actually be vertical, in the lower
                // 3/4 of the screen, with a plausible total spread.
                val top = column.minOf { it.bounds.top }
                val bottom = column.maxOf { it.bounds.bottom }
                val spread = bottom - top
                if (top > screenH * 0.2f &&
                    spread in (screenH * 0.12f).toInt()..(screenH * 0.75f).toInt()
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun collect(
        node: AccessibilityNodeInfo,
        out: MutableList<Hit>,
        screenW: Int,
        screenH: Int,
        depth: Int,
    ) {
        if (depth > 40 || out.size > 40) return
        val label = buildString {
            node.contentDescription?.let { append(it) }
            append(' ')
            node.text?.let { if (it.length <= 40) append(it) }
        }.lowercase()

        if (label.isNotBlank()) {
            val category = categorize(label)
            if (category >= 0) {
                val bounds = Rect()
                node.boundsInScreen(bounds)
                // Icons are compact; reject huge containers that merely mention
                // the words, and off-screen nodes.
                if (bounds.width() in 1..(screenW * 0.3f).toInt() &&
                    bounds.height() in 1..(screenH * 0.2f).toInt() &&
                    bounds.top >= 0 && bounds.bottom <= screenH + 10
                ) {
                    out.add(Hit(category, bounds))
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, screenW, screenH, depth + 1)
        }
    }

    private fun categorize(label: String): Int = when {
        commentWords.any { label.contains(it) } -> CAT_COMMENT
        shareWords.any { label.contains(it) } -> CAT_SHARE
        saveWords.any { label.contains(it) } -> CAT_SAVE
        likeWords.any { label.contains(it) } -> CAT_LIKE
        else -> -1
    }

    private fun AccessibilityNodeInfo.boundsInScreen(rect: Rect) = getBoundsInScreen(rect)
}

/**
 * The Facebook Reels brain — fuses the two parallel mechanisms.
 *
 * Decision table (either mechanism alone is sufficient):
 *  - WHITE strip          -> definitely NOT Reels, overrides everything.
 *  - BLACK strip          -> mechanism #1 satisfied  -> BLOCK.
 *  - Action rail (>=3/4)  -> mechanism #2 satisfied  -> BLOCK
 *                            (works even when the strip is hidden/unknown,
 *                             and tolerates the icons drifting up/down).
 *  - HIDDEN strip, no rail -> no block.
 */
class FacebookReelsBrain {

    private var lastBlockedAt = 0L

    fun reset() = Unit // kept for package-change lifecycle symmetry

    /** Called by the engine whenever a reels/shorts block actually fires. */
    fun notifyBlocked(now: Long) {
        lastBlockedAt = now
    }

    fun evaluate(tabBar: TabBarState?, railDetected: Boolean, now: Long): Boolean {
        // Mechanism #1 negative case: white strip = any tab except Reels.
        if (tabBar == TabBarState.WHITE) return false
        // Either mechanism confirms -> block.
        return tabBar == TabBarState.BLACK || railDetected
    }
}

/**
 * Generic shorts detection for YouTube Shorts and Instagram Reels.
 *
 * YouTube coverage — all entry paths (bottom tab, home shelf tap, channel
 * shorts, notifications, deep links, autoplay-next):
 *  1. Player view-ids that only exist while a Short is actually playing
 *     (reel_recycler / reel_player / reel_watch / shorts_player ...).
 *  2. The selected "Shorts" pivot in the bottom navigation (AR + EN).
 *  3. The vertical engagement rail (like/comment/share/remix) — same
 *     mechanism as Facebook, catches any UI variant or future id rename.
 */
object GenericShortsDetector {

    // Ids present only inside the Shorts player itself — deliberately NOT the
    // generic "shorts_lockup" ids used by home-feed shelves, to avoid blocking
    // the home feed for merely showing a Shorts shelf.
    private val youtubePlayerIds = listOf(
        "reel_recycler", "reel_player", "reel_watch", "reel_progress",
        "shorts_player", "shorts_video", "shorts_container", "shorts_frame",
        "reel_persistent", "reel_scrim",
    )
    private val instagramIds = listOf("clips_viewer", "clips_video", "clips_tab")
    private val shortsWords = listOf("shorts", "المقاطع القصيرة", "مقاطع قصيرة", "reels", "ريلز")

    fun detect(
        root: AccessibilityNodeInfo?,
        packageName: String,
        screenW: Int,
        screenH: Int,
    ): Boolean {
        if (root == null) return false
        return when {
            packageName.contains("youtube") ->
                scan(root, youtubePlayerIds, 0) ||
                    ActionRailDetector.detect(root, screenW, screenH)
            packageName.contains("instagram") ->
                scan(root, instagramIds, 0) ||
                    ActionRailDetector.detect(root, screenW, screenH)
            else -> false
        }
    }

    private fun scan(node: AccessibilityNodeInfo, ids: List<String>, depth: Int): Boolean {
        if (depth > 40) return false
        node.viewIdResourceName?.lowercase()?.let { id ->
            if (ids.any { id.contains(it) }) return true
        }
        node.contentDescription?.toString()?.lowercase()?.let { desc ->
            // Selected "Shorts"/"Reels" pivot in the navigation bar.
            if (node.isSelected && shortsWords.any { desc.contains(it) }) return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (scan(child, ids, depth + 1)) return true
        }
        return false
    }
}
