package com.agon.app.blocking

import com.agon.app.GuardianApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * WhitelistCache — كاش القائمة البيضاء للمحركات.
 *
 * رؤية التطبيق: "أي كلمة أو موقع أو تطبيق سيكون في white list لن تسري عليه
 * اي ميزة من تطبيقنا".
 *
 * يحتفظ بنسخة في الذاكرة (آمنة للقراءة من main thread في AccessibilityService)
 * للتطبيقات والكلمات والمواقع المسموح بها، ويُحدّثها تلقائياً عند تغيّر القائمة.
 *
 * الاستخدام:
 *   - [isAppAllowed] / [isKeywordAllowed] / [isWebsiteAllowed]: فحص فوري غير حاجب.
 *
 * ملاحظة: المواقع (websites) تُطابق كـ substring على الـ domain/URL lowercase،
 * لذا "example.com" يُعفي أي رابط يحتوي هذا النطاق.
 */
class WhitelistCache(app: GuardianApp, scope: CoroutineScope) {

    private val settings = app.repository.getAppSettings()
    private val repo = app.repository

    @Volatile private var allowedApps: Set<String> = emptySet()
    @Volatile private var allowedWebsites: Set<String> = emptySet()
    @Volatile private var allowedKeywords: Set<String> = emptySet()

    // Pattern cache for keyword whitelist (regex from wildcard), rebuilt on change.
    @Volatile private var allowedKeywordPatterns: List<java.util.regex.Pattern> = emptyList()

    // domain -> pre-normalized lookup (lowercased) for fast website matching
    private val websiteIndex = ConcurrentHashMap<String, Boolean>().apply { /* lazy */ }

    init {
        scope.launch {
            // Seed then observe apps + websites (DataStore).
            launch {
                combine(settings.whitelistAppsFlow, settings.whitelistWebsitesFlow) { apps, sites ->
                    apps to sites
                }.collect { (apps, sites) ->
                    allowedApps = apps
                    allowedWebsites = sites.map { it.lowercase().trim() }.filter { it.isNotEmpty() }.toSet()
                    websiteIndex.clear()
                    allowedWebsites.forEach { websiteIndex[it] = true }
                    Timber.d("WhitelistCache: ${allowedApps.size} apps, ${allowedWebsites.size} websites")
                }
            }

            // Seed then observe keywords (Room).
            launch {
                repo.getWhitelistKeywords().collect { keywords ->
                    allowedKeywords = keywords.toSet()
                    allowedKeywordPatterns = keywords
                        .filter { it.isNotBlank() }
                        .mapNotNull { kw ->
                            try {
                                java.util.regex.Pattern.compile(
                                    kw.trim().replace(".", "\\.").replace("*", ".*").replace("?", "."),
                                    java.util.regex.Pattern.CASE_INSENSITIVE
                                )
                            } catch (_: Exception) { null }
                        }
                    Timber.d("WhitelistCache: ${allowedKeywords.size} keywords")
                }
            }

            // Initial warm-up (in case flows haven't emitted yet on this process).
            try {
                if (allowedApps.isEmpty()) {
                    allowedApps = settings.whitelistAppsFlow.first()
                }
                if (allowedWebsites.isEmpty()) {
                    allowedWebsites = settings.whitelistWebsitesFlow.first().map { it.lowercase().trim() }.filter { it.isNotEmpty() }.toSet()
                    allowedWebsites.forEach { websiteIndex[it] = true }
                }
                if (allowedKeywords.isEmpty()) {
                    val kw = repo.getWhitelistKeywords().first()
                    allowedKeywords = kw.toSet()
                    allowedKeywordPatterns = kw.filter { it.isNotBlank() }.mapNotNull { k ->
                        try { java.util.regex.Pattern.compile(k.trim().replace(".", "\\.").replace("*", ".*").replace("?", "."), java.util.regex.Pattern.CASE_INSENSITIVE) } catch (_: Exception) { null }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "WhitelistCache: warm-up failed")
            }
        }
    }

    /** التطبيق في القائمة البيضاء → يُعفى من جميع ميزات الحظر. */
    fun isAppAllowed(pkg: String): Boolean = allowedApps.contains(pkg)

    /** الكلمة في القائمة البيضاء (مطابقة regex للـ wildcards) → يُعفى حظر الكلمات. */
    fun isKeywordAllowed(text: String): Boolean {
        if (allowedKeywordPatterns.isEmpty()) return false
        for (pattern in allowedKeywordPatterns) {
            if (pattern.matcher(text).find()) return true
        }
        return false
    }

    /** الموقع/النطاق في القائمة البيضاء → يُعفى حظر المواقع. يطابق substring على lowercase. */
    fun isWebsiteAllowed(urlOrDomain: String): Boolean {
        if (allowedWebsites.isEmpty()) return false
        val lower = urlOrDomain.lowercase().trim()
        if (lower.isEmpty()) return false
        return allowedWebsites.any { lower.contains(it) }
    }
}
