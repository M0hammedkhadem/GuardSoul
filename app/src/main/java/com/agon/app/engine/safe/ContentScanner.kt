package com.agon.app.engine.safe

import com.agon.app.engine.*
import com.agon.app.engine.filter.KeywordMatcher

class ContentScanner(
    private val scannerActive: Boolean = false,
    private val scanIntervalMs: Long = 3000L,
    private val detectionThreshold: Int = 3,
    private val thresholdWindowMs: Long = 240_000L
) {
    data class ScanResult(
        val isExplicit: Boolean = false,
        val confidence: Float = 0f,
        val matchedKeywords: List<String> = emptyList(),
        val scanDurationMs: Long = 0
    )

    data class BanState(
        val isBanned: Boolean = false,
        val bannedUntil: Long = 0L,
        val detectionCount: Int = 0,
        val firstDetectionTime: Long = 0L
    )

    private val detectionLog = mutableMapOf<String, MutableList<Long>>()
    private val banCache = mutableMapOf<String, BanState>()

    private val keywordMatcher = KeywordMatcher(
        blocklist = setOf(
            "porn", "xxx", "sex", "nude", "nsfw", "hentai", "erotic",
            "naked", "explicit", "adult", "mature", "18+"
        ),
        useRegex = true,
        caseSensitive = false
    )

    fun scanText(text: String): ScanResult {
        if (!scannerActive || text.isBlank()) return ScanResult()
        val startTime = System.currentTimeMillis()

        val matchedKeywords = mutableListOf<String>()
        for (keyword in listOf("porn", "xxx", "sex", "nude", "nsfw", "hentai", "erotic")) {
            if (text.contains(keyword, ignoreCase = true)) {
                matchedKeywords.add(keyword)
            }
        }

        return ScanResult(
            isExplicit = matchedKeywords.isNotEmpty(),
            confidence = (matchedKeywords.size.toFloat() / 7f).coerceAtMost(1f),
            matchedKeywords = matchedKeywords,
            scanDurationMs = System.currentTimeMillis() - startTime
        )
    }

    fun scanResult(result: ScanResult, appPackage: String): BanState {
        if (!result.isExplicit) {
            detectionLog[appPackage]?.clear()
            return banCache[appPackage] ?: BanState()
        }

        val now = System.currentTimeMillis()
        val log = detectionLog.getOrPut(appPackage) { mutableListOf() }
        log.add(now)
        log.removeAll { now - it > thresholdWindowMs }

        val firstDetect = log.firstOrNull() ?: now
        val count = log.size

        val banState = if (count >= detectionThreshold) {
            BanState(
                isBanned = true,
                bannedUntil = now + 15 * 60 * 1000L,
                detectionCount = count,
                firstDetectionTime = firstDetect
            )
        } else {
            BanState(
                isBanned = false,
                detectionCount = count,
                firstDetectionTime = firstDetect
            )
        }

        banCache[appPackage] = banState
        return banState
    }

    fun isBanned(appPackage: String): Boolean {
        val ban = banCache[appPackage] ?: return false
        if (!ban.isBanned) return false
        if (System.currentTimeMillis() > ban.bannedUntil) {
            banCache.remove(appPackage)
            return false
        }
        return true
    }

    fun getRemainingBanTime(appPackage: String): Long {
        val ban = banCache[appPackage] ?: return 0L
        if (!ban.isBanned) return 0L
        return (ban.bannedUntil - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun clearBan(appPackage: String) {
        banCache.remove(appPackage)
        detectionLog.remove(appPackage)
    }

    fun resetAll() {
        detectionLog.clear()
        banCache.clear()
    }

    fun getDetectionCount(appPackage: String): Int {
        val log = detectionLog[appPackage] ?: return 0
        val now = System.currentTimeMillis()
        log.removeAll { now - it > thresholdWindowMs }
        return log.size
    }

    companion object {
        const val DEFAULT_SCAN_INTERVAL = 3000L
        const val DEFAULT_THRESHOLD = 3
        const val DEFAULT_WINDOW_MS = 240_000L
        const val BAN_DURATION_MS = 900_000L
    }
}
