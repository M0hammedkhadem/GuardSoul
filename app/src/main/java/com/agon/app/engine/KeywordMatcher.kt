package com.agon.app.engine

/**
 * High-accuracy keyword matching for on-screen text.
 *
 * Dimensions covered (precision-test driven):
 *  1. TRUE POSITIVES — direct words, Arabic diacritics/tashkeel ignored,
 *     alef variants (أ/إ/آ/ٱ→ا), ى→ي, ة→ه, common intentional misspellings
 *     via adjacent-transposition variants (porn→pron) for words of length≥4.
 *  2. FALSE-POSITIVE TRAPS (Scunthorpe problem) — strict word boundaries:
 *     "sex" never matches sussex/middlesex/unisex, "نيك" never matches
 *     تكنيك/ميكانيكي/النيكل. White phrases ("sex education") mask hits
 *     inside their span.
 *  3. EVASION/OBFUSCATION — leetspeak digit folding (s3x→sex, p0rn→porn),
 *     separator-split letters (s.e.x / س-ك-س / s e x) via single-letter-run
 *     joining, repeated whitespace collapsed.
 *  4. CONTEXT — word-boundary matching plus white-phrase spans; the
 *     letter-run joiner only fuses runs of SINGLE-character tokens, so
 *     "glass exhibition" (squeeze trap) and "galaxy s3 x" can never match.
 */
object KeywordMatcher {

    // ---------- Normalization ----------

    /**
     * Lowercase; Arabic normalization (strip harakat/tatweel, unify alef/
     * ya/ta-marbuta); leetspeak digit folding; whitespace runs collapsed.
     * Applied identically to page text AND configured words.
     */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        var lastSpace = false
        for (raw in s.lowercase()) {
            val c = when (raw) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ى' -> 'ي'
                'ة' -> 'ه'
                // Leetspeak digit folding (evasion: s3x, p0rn, 5ex...)
                '0' -> 'o'
                '1' -> 'i'
                '3' -> 'e'
                '4' -> 'a'
                '5' -> 's'
                '7' -> 't'
                '8' -> 'b'
                else -> raw
            }
            if (c == '\u0640' || c in '\u064B'..'\u065F' || c == '\u0670') continue // tatweel/harakat
            if (c.isWhitespace()) {
                if (!lastSpace) {
                    sb.append(' '); lastSpace = true
                }
            } else {
                sb.append(c); lastSpace = false
            }
        }
        return sb.toString()
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit()

    // ---------- Boundary-aware span search ----------

    /** All occurrences of [word] in [page] delimited by non-word chars. */
    private fun wordSpans(page: String, word: String): List<IntRange> {
        if (word.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>(2)
        var idx = page.indexOf(word)
        while (idx >= 0) {
            val before = if (idx == 0) null else page[idx - 1]
            val afterIdx = idx + word.length
            val after = if (afterIdx >= page.length) null else page[afterIdx]
            if ((before == null || !isWordChar(before)) && (after == null || !isWordChar(after))) {
                out.add(idx..(afterIdx - 1))
            }
            idx = page.indexOf(word, idx + 1)
        }
        return out
    }

    /** True when [word] occurs in [page] delimited by non-word characters. */
    fun containsWord(page: String, word: String): Boolean =
        wordSpans(page, word).isNotEmpty()

    // ---------- Misspelling variants ----------

    /** Adjacent transpositions (porn→pron) for single words of length >= 4. */
    private fun variants(word: String): List<String> {
        if (word.length < 4 || word.contains(' ')) return emptyList()
        val out = ArrayList<String>(word.length)
        val chars = word.toCharArray()
        for (i in 0 until chars.size - 1) {
            if (chars[i] == chars[i + 1]) continue
            val v = chars.copyOf()
            val t = v[i]; v[i] = v[i + 1]; v[i + 1] = t
            out.add(String(v))
        }
        return out
    }

    // ---------- Tokenizer (for the letter-run joiner) ----------

    private class Token(val text: String, val start: Int, val endExclusive: Int)

    private fun tokenize(page: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        while (i < page.length) {
            if (isWordChar(page[i])) {
                val s = i
                while (i < page.length && isWordChar(page[i])) i++
                out.add(Token(page.substring(s, i), s, i))
            } else {
                i++
            }
        }
        return out
    }

    // ---------- Main entry ----------

    /**
     * Returns the first black word found in [pageRaw] (original configured
     * casing), or null. White words/phrases mask matches inside their spans.
     */
    fun findMatch(
        pageRaw: String,
        blackWords: List<String>,
        whiteWords: List<String> = emptyList(),
    ): String? {
        if (pageRaw.isBlank() || blackWords.isEmpty()) return null
        val page = normalize(pageRaw)
        if (page.isBlank()) return null

        val whiteNorm = whiteWords.mapNotNull { w ->
            normalize(w.trim()).takeIf { it.isNotEmpty() }
        }
        val whiteSet = whiteNorm.toHashSet()
        val whiteSpans = ArrayList<IntRange>()
        for (w in whiteNorm) whiteSpans.addAll(wordSpans(page, w))

        fun covered(r: IntRange): Boolean =
            whiteSpans.any { it.first <= r.first && r.last <= it.last }

        // Pass 1: boundary matches for each word + its misspelling variants.
        // Also collect spaceless join-forms for the obfuscation pass.
        val joinTargets = HashMap<String, String>() // joined form -> original word
        for (orig in blackWords) {
            val t = normalize(orig.trim())
            if (t.length < 2 || t in whiteSet) continue
            val candidates = ArrayList<String>(t.length)
            candidates.add(t)
            candidates.addAll(variants(t))
            for (cand in candidates) {
                for (span in wordSpans(page, cand)) {
                    if (!covered(span)) return orig
                }
                val joined = cand.replace(" ", "")
                if (joined.length >= 3) joinTargets.putIfAbsent(joined, orig)
            }
        }

        // Pass 2: obfuscation — join runs of consecutive SINGLE-letter tokens
        // (s.e.x / س-ك-س / s e x). Only single-char tokens fuse, so ordinary
        // adjacent words ("glass exhibition") can never produce a hit.
        if (joinTargets.isNotEmpty()) {
            val tokens = tokenize(page)
            var i = 0
            while (i < tokens.size) {
                if (tokens[i].text.length == 1) {
                    var j = i
                    while (j + 1 < tokens.size && tokens[j + 1].text.length == 1 && j - i < 10) j++
                    if (j > i) {
                        for (a in i..j) {
                            val sb = StringBuilder(tokens[a].text)
                            for (b in (a + 1)..j) {
                                sb.append(tokens[b].text)
                                if (sb.length >= 3) {
                                    joinTargets[sb.toString()]?.let { orig ->
                                        val span = tokens[a].start..(tokens[b].endExclusive - 1)
                                        if (!covered(span)) return orig
                                    }
                                }
                            }
                        }
                    }
                    i = j + 1
                } else {
                    i++
                }
            }
        }
        return null
    }
}
