package com.agon.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Accuracy guarantees for the screen-wide keyword guard. */
class KeywordMatcherTest {

    private val black = listOf("sex", "porn", "نيك", "سكس", "إباحي", "+18", "hot girls")

    @Test
    fun `exact word on screen is caught`() {
        assertEquals("porn", KeywordMatcher.findMatch("watch free porn online", black))
        assertEquals("سكس", KeywordMatcher.findMatch("مقاطع سكس جديدة", black))
    }

    @Test
    fun `FALSE-POSITIVE substring inside an innocent word must NOT match`() {
        // "sex" inside "sussex"/"sexton", "نيك" inside "تكنيك/ميكانيكي".
        assertNull(KeywordMatcher.findMatch("visit sussex england", black))
        assertNull(KeywordMatcher.findMatch("شرح تكنيك جديد في الميكانيكا", black))
        assertNull(KeywordMatcher.findMatch("مهارة وتكنيكات البرمجة", black))
    }

    @Test
    fun `arabic normalization catches vowelized and alef variants`() {
        // إباحي configured with hamza; screen shows bare-alef + diacritics.
        assertEquals("إباحي", KeywordMatcher.findMatch("محتوى اِباحي مخفي", black))
        assertEquals("إباحي", KeywordMatcher.findMatch("محتوى أباحي", black))
    }

    @Test
    fun `punctuation and edges count as word boundaries`() {
        assertEquals("sex", KeywordMatcher.findMatch("sex", black))
        assertEquals("sex", KeywordMatcher.findMatch("(sex)", black))
        assertEquals("porn", KeywordMatcher.findMatch("porn.com", black))
        assertEquals("+18", KeywordMatcher.findMatch("قناة +18 جديدة", black))
    }

    @Test
    fun `multi-word phrases match across spaces`() {
        assertEquals("hot girls", KeywordMatcher.findMatch("trending hot girls videos", black))
        assertNull(KeywordMatcher.findMatch("hot girlsy", black)) // boundary on tail
    }

    @Test
    fun `white words are exempt from blocking`() {
        // User whitelisted a word that would otherwise block.
        assertNull(KeywordMatcher.findMatch("sex education course", black, whiteWords = listOf("sex")))
        // Other black words still fire.
        assertEquals("porn", KeywordMatcher.findMatch("porn site", black, whiteWords = listOf("sex")))
    }

    @Test
    fun `single letters and blanks never match`() {
        assertNull(KeywordMatcher.findMatch("", black))
        assertNull(KeywordMatcher.findMatch("clean page content", listOf("a", " ")))
    }
}
