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
}

/**
 * Facebook Reels fusion brain — the two parallel mechanisms.
 * Rule: WHITE strip = definitive negative; otherwise EITHER mechanism
 * (black strip OR the 3-of-4 vertical action rail) confirms => block.
 */
class FacebookReelsBrainTest {

    @Test
    fun `white tab strip is a definitive negative even with a rail`() {
        val b = FacebookReelsBrain()
        assertFalse(b.evaluate(TabBarState.WHITE, railDetected = true, now = 1_000))
    }

    @Test
    fun `black strip plus action rail blocks immediately`() {
        val b = FacebookReelsBrain()
        assertTrue(b.evaluate(TabBarState.BLACK, railDetected = true, now = 1_000))
    }

    @Test
    fun `mechanism 1 alone - black strip without rail - blocks`() {
        val b = FacebookReelsBrain()
        assertTrue(b.evaluate(TabBarState.BLACK, railDetected = false, now = 1_000))
    }

    @Test
    fun `mechanism 2 alone - rail with hidden strip - blocks`() {
        val b = FacebookReelsBrain()
        assertTrue(b.evaluate(TabBarState.HIDDEN, railDetected = true, now = 1_000))
    }

    @Test
    fun `mechanism 2 alone - rail with no screenshot available - blocks`() {
        val b = FacebookReelsBrain()
        assertTrue(b.evaluate(null, railDetected = true, now = 1_000))
    }

    @Test
    fun `hidden strip without rail does not block`() {
        val b = FacebookReelsBrain()
        assertFalse(b.evaluate(TabBarState.HIDDEN, railDetected = false, now = 1_000))
    }

    @Test
    fun `re-entering reels right after a block is still blocked`() {
        val b = FacebookReelsBrain()
        b.notifyBlocked(now = 1_000)
        assertTrue(b.evaluate(TabBarState.HIDDEN, railDetected = true, now = 4_000))
    }

    @Test
    fun `white strip clears even after a recent block`() {
        val b = FacebookReelsBrain()
        b.notifyBlocked(now = 1_000)
        assertFalse(b.evaluate(TabBarState.WHITE, railDetected = false, now = 3_000))
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
