package com.agon.app.engine

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Multi-dimensional precision test for keyword blocking:
 *  1. True positives (direct, diacritics, misspellings)
 *  2. False-positive traps (Scunthorpe problem, AR + EN)
 *  3. Evasion/obfuscation (leetspeak, separators, extra spaces)
 *  4. Context sensitivity (word boundaries, white phrases)
 *  5. Metrics: Precision / Recall / F1 with thresholds
 *     (recall is weighted higher: missing real content is worse than a
 *     rare false positive in a blocking product).
 */
class KeywordPrecisionMetricsTest {

    private val black = listOf(
        "sex", "porn", "porno", "sexting", "نيك", "سكس", "إباحي",
        "+18", "hot girls", "seks",
    )
    private val white = listOf("sex education")

    private fun match(page: String): String? =
        KeywordMatcher.findMatch(page, black, white)

    // ---------- Corpus ----------

    /** Dimension 1+3: content that MUST be blocked. */
    private val truePositives = listOf(
        // direct words
        "free porn videos",
        "مشاهدة سكس مباشر",
        "trending hot girls compilation",
        "sexting is dangerous",
        "قناة +18 للكبار",
        "seks arabic video",
        // Arabic diacritics + hamza variants
        "محتوى إِبَاحِيٌّ خطير",
        "محتوى أباحي جديد",
        // intentional misspelling (adjacent transposition)
        "pron videos leaked",
        // leetspeak
        "watch p0rn tonight",
        "download s3x clips",
        // separator-split letters
        "download s.e.x content",
        "p.o.r.n free access",
        "مقاطع س-ك-س حصرية",
        "s e x stories",
        // extra whitespace inside a phrase
        "hot   girls now",
    )

    /** Dimension 2+4: innocent content that must NEVER be blocked. */
    private val falsePositiveTraps = listOf(
        // Scunthorpe traps — English
        "visit sussex england",
        "middlesex county records",
        "essex university admission",
        "unisex clothing store",
        // Scunthorpe traps — Arabic shared-root words
        "تعلم تكنيك جديد في الرسم",
        "ميكانيكي محترف قريب منك",
        "النيكل معدن مهم في الصناعة",
        "مهارة وتكنيكات البرمجة",
        // whitelisted medical/educational phrase
        "sex education curriculum for schools",
        // squeeze trap: adjacent words must not fuse across the boundary
        "glass exhibition opens today",
        // 2-char leet token must not fuse with a neighbour single letter
        "samsung galaxy s3 x edition",
        // near-phrase without the exact words
        "hot girl summer playlist",
    )

    // ---------- Dimension tests (clear diagnostics per case) ----------

    @Test
    fun `dimension 1 and 3 - every true positive is caught`() {
        truePositives.forEach { page ->
            assertNotNull("MISSED (false negative): \"$page\"", match(page))
        }
    }

    @Test
    fun `dimension 2 and 4 - every innocent trap passes`() {
        falsePositiveTraps.forEach { page ->
            val hit = match(page)
            assertNull("FALSE POSITIVE on \"$page\" (matched: $hit)", hit)
        }
    }

    @Test
    fun `dimension 4 - whitelist phrase masks only its own span`() {
        // Same word outside the white phrase must still block.
        assertNotNull(
            "standalone black word outside the white phrase must block",
            match("sex education is fine but then plain sex appears"),
        )
        // Inside the phrase only -> pass.
        assertNull(match("sex education for schools"))
    }

    @Test
    fun `dimension 3 - obfuscation joiner never fuses multi-letter words`() {
        // The letter-run joiner only fuses SINGLE-char tokens.
        assertNull(match("as ex post facto ruling")) // "as ex" must not fuse into "asex/sex"
        assertNotNull(match("違反 s.e.x 内容")) // singles still fuse regardless of context
    }

    // ---------- Dimension 5: metrics ----------

    @Test
    fun `dimension 5 - precision recall and f1 meet thresholds`() {
        var tp = 0
        var fn = 0
        var fp = 0

        truePositives.forEach { if (match(it) != null) tp++ else fn++ }
        falsePositiveTraps.forEach { if (match(it) != null) fp++ }

        val precision = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
        val recall = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
        val f1 = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)

        val report = "precision=%.3f recall=%.3f f1=%.3f (tp=%d fp=%d fn=%d)"
            .format(precision, recall, f1, tp, fp, fn)

        // Recall weighted higher: in content blocking, a miss is worse
        // than a rare false positive.
        assertTrue("recall below threshold: $report", recall >= 0.95)
        assertTrue("precision below threshold: $report", precision >= 0.85)
        assertTrue("f1 below threshold: $report", f1 >= 0.90)
    }
}
