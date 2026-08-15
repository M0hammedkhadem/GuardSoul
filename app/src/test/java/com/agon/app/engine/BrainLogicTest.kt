package com.agon.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the blocking brain: blocks fire, repeats are NEVER swallowed,
 * and repeated attempts escalate.
 */
class BlockGovernorTest {

    @Test
    fun `first block is granted without escalation`() {
        val g = BlockGovernor()
        val grant = g.request("app:facebook", 1_000)
        assertNotNull(grant)
        assertEquals(0, grant!!.repeatCount)
        assertFalse(grant.escalated)
    }

    @Test
    fun `duplicate detection while overlay is showing is suppressed`() {
        val g = BlockGovernor(suppressMs = 2_000)
        assertNotNull(g.request("app:facebook", 1_000))
        // Event storm 500ms later — same target, overlay still up.
        assertNull(g.request("app:facebook", 1_500))
    }

    @Test
    fun `repeat attempt after overlay closes is blocked again and escalated`() {
        val g = BlockGovernor(suppressMs = 2_000, repeatWindowMs = 60_000)
        assertNotNull(g.request("app:facebook", 1_000))
        val second = g.request("app:facebook", 4_000) // user reopened the app
        assertNotNull("repeat attempt must be blocked again", second)
        assertEquals(1, second!!.repeatCount)
        assertTrue(second.escalated)
        val third = g.request("app:facebook", 8_000)
        assertNotNull(third)
        assertEquals(2, third!!.repeatCount)
    }

    @Test
    fun `different target resets the escalation counter`() {
        val g = BlockGovernor()
        assertNotNull(g.request("app:facebook", 1_000))
        assertNotNull(g.request("app:facebook", 4_000)) // repeat -> count 1
        val other = g.request("site:bad.com", 6_000)
        assertNotNull(other)
        assertEquals(0, other!!.repeatCount)
    }

    @Test
    fun `escalation expires after the repeat window`() {
        val g = BlockGovernor(repeatWindowMs = 60_000)
        assertNotNull(g.request("app:facebook", 1_000))
        val later = g.request("app:facebook", 70_000) // more than a minute later
        assertNotNull(later)
        assertEquals(0, later!!.repeatCount)
        assertFalse(later.escalated)
    }

    @Test
    fun `REGRESSION instant app relaunch bypasses the long suppress window`() {
        // Reported bug: relaunching a blocked app within the 2s suppress
        // window was silently allowed. Launch events must re-block.
        val g = BlockGovernor(suppressMs = 2_000)
        assertNotNull(g.request("app:tiktok", 1_000))
        // Relaunch 1.2s later (inside old suppress window) -> MUST block.
        val relaunch = g.request("app:tiktok", 2_200, bypassSuppress = true)
        assertNotNull("instant relaunch must be re-blocked", relaunch)
        assertTrue(relaunch!!.escalated)
    }

    @Test
    fun `launch bypass still absorbs the launch animation event storm`() {
        val g = BlockGovernor(suppressMs = 2_000)
        assertNotNull(g.request("app:tiktok", 1_000, bypassSuppress = true))
        // Same launch fires several window-state events within 600ms.
        assertNull(g.request("app:tiktok", 1_300, bypassSuppress = true))
        // A real relaunch after the short window is blocked again.
        assertNotNull(g.request("app:tiktok", 1_700, bypassSuppress = true))
    }
}

/**
 * Facebook Reels fusion brain — the two parallel mechanisms.
 * Rules: WHITE strip = definitive negative; Reels tab node selected = block;
 * vertical action rail (3 of 4) = block; BLACK pixels ALONE never block
 * (full-screen video/photo viewers paint that region black with NO strip).
 */
class FacebookReelsBrainTest {

    private val stripAbsent = TabStripInfo.ABSENT
    private val stripPresent = TabStripInfo(present = true, reelsSelected = false)
    private val stripReelsSelected = TabStripInfo(present = true, reelsSelected = true)

    @Test
    fun `white tab strip is a definitive negative even with a rail`() {
        val b = FacebookReelsBrain()
        assertFalse(b.evaluate(TabBarState.WHITE, stripPresent, railDetected = true, now = 1_000))
    }

    @Test
    fun `REGRESSION full-screen video player - black pixels, strip absent, no rail - must NOT block`() {
        // Exact reported bug: tapping a video post in the home feed opens the
        // full-screen player whose top region is pure black (no tab strip).
        val b = FacebookReelsBrain()
        assertFalse(b.evaluate(TabBarState.BLACK, stripAbsent, railDetected = false, now = 1_000))
    }

    @Test
    fun `REGRESSION photo viewer - black pixels, strip absent, no rail - must NOT block`() {
        val b = FacebookReelsBrain()
        assertFalse(b.evaluate(TabBarState.BLACK, stripAbsent, railDetected = false, now = 1_000))
    }

    @Test
    fun `dark-mode feed - black pixels with strip present but no rail - must NOT block`() {
        val b = FacebookReelsBrain()
        assertFalse(b.evaluate(TabBarState.BLACK, stripPresent, railDetected = false, now = 1_000))
    }

    @Test
    fun `reels tab selected blocks even without the rail`() {
        val b = FacebookReelsBrain()
        assertTrue(b.evaluate(TabBarState.BLACK, stripReelsSelected, railDetected = false, now = 1_000))
    }

    @Test
    fun `reels tab selected blocks even without a screenshot`() {
        val b = FacebookReelsBrain()
        assertTrue(b.evaluate(null, stripReelsSelected, railDetected = false, now = 1_000))
    }

    @Test
    fun `black strip plus action rail blocks immediately`() {
        val b = FacebookReelsBrain()
        assertTrue(b.evaluate(TabBarState.BLACK, stripPresent, railDetected = true, now = 1_000))
    }

    @Test
    fun `rail with hidden strip blocks - scrolled inside reels`() {
        val b = FacebookReelsBrain()
        assertTrue(b.evaluate(TabBarState.HIDDEN, stripAbsent, railDetected = true, now = 1_000))
    }

    @Test
    fun `rail with no screenshot available blocks - below API 30 path`() {
        val b = FacebookReelsBrain()
        assertTrue(b.evaluate(null, stripAbsent, railDetected = true, now = 1_000))
    }

    @Test
    fun `hidden strip without rail does not block`() {
        val b = FacebookReelsBrain()
        assertFalse(b.evaluate(TabBarState.HIDDEN, stripAbsent, railDetected = false, now = 1_000))
    }

    @Test
    fun `re-entering reels right after a block is still blocked`() {
        val b = FacebookReelsBrain()
        b.notifyBlocked(now = 1_000)
        assertTrue(b.evaluate(TabBarState.HIDDEN, stripAbsent, railDetected = true, now = 4_000))
    }

    @Test
    fun `white strip clears even after a recent block`() {
        val b = FacebookReelsBrain()
        b.notifyBlocked(now = 1_000)
        assertFalse(b.evaluate(TabBarState.WHITE, stripPresent, railDetected = false, now = 3_000))
    }
}

/** Browser guard: blacklist, whitelist, keywords and SafeSearch enforcement. */
class BrowserGuardTest {

    private val blacklist = listOf("badsite.com")
    private val whitelist = listOf("quran.com")
    private val keywords = listOf("porn", "xxx")
    private val safeOn = mapOf("Google" to true, "Bing" to true, "YouTube" to true)

    private fun judge(url: String, engines: Map<String, Boolean> = safeOn) =
        BrowserGuard.judge(
            url = url,
            blacklist = blacklist,
            whitelist = whitelist,
            keywords = keywords,
            keywordFilterOn = true,
            siteFilterOn = true,
            safeEngines = engines,
        )

    @Test
    fun `blacklisted domain is blocked`() {
        val v = judge("https://badsite.com/watch")
        assertTrue(v is BrowserVerdict.BlockedDomain)
    }

    @Test
    fun `whitelisted domain always wins`() {
        val v = judge("https://quran.com/porn") // even with a bad keyword
        assertTrue(v is BrowserVerdict.Allow)
    }

    @Test
    fun `blocked keyword in url is caught`() {
        val v = judge("https://example.com/search?q=xxx+videos")
        assertTrue(v is BrowserVerdict.BlockedKeyword)
    }

    @Test
    fun `google search without safesearch is stopped`() {
        val v = judge("https://www.google.com/search?q=anything")
        assertTrue(v is BrowserVerdict.UnsafeSearch)
        assertEquals("Google", (v as BrowserVerdict.UnsafeSearch).engine)
    }

    @Test
    fun `google search with safesearch active is allowed`() {
        val v = judge("https://www.google.com/search?q=anything&safe=active")
        assertTrue(v is BrowserVerdict.Allow)
    }

    @Test
    fun `engine toggle off means no safesearch enforcement`() {
        val v = judge(
            "https://www.google.com/search?q=anything",
            engines = mapOf("Google" to false),
        )
        assertTrue(v is BrowserVerdict.Allow)
    }

    @Test
    fun `repeating the same blocked url keeps getting blocked by the governor`() {
        // End-to-end of the repeat path: verdict stays blocked, governor escalates.
        val g = BlockGovernor()
        val url = "https://badsite.com/feed"
        var escalations = 0
        var blocks = 0
        var t = 1_000L
        repeat(3) {
            val verdict = judge(url)
            assertTrue(verdict is BrowserVerdict.BlockedDomain)
            val grant = g.request("site:badsite.com", t)
            assertNotNull("every repeat must be granted a block", grant)
            blocks++
            if (grant!!.escalated) escalations++
            t += 3_000 // user retries every 3 seconds
        }
        assertEquals(3, blocks)
        assertEquals(2, escalations) // attempts 2 and 3 escalate
    }
}
