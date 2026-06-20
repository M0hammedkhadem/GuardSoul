package com.agon.app.blocking

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.GuardianApp
import com.agon.app.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.regex.Pattern

/**
 * Keyword Detector for blocking content based on text input
 * Listens for TYPE_VIEW_TEXT_CHANGED events and checks against keyword list
 */
class KeywordDetector(private val host: AccessibilityService) {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var keywordPatterns: List<Pattern> = emptyList()
    private var cachedShieldActive = false
    private var cachedKeywordBlockingEnabled = false

    // Common browser package names for URL bar detection
    private val browserPackages = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.kiwibrowser.browser",
        "com.vivaldi.browser"
    )

    fun start() {
        serviceScope.launch {
            val app = host.applicationContext as GuardianApp
            val settings = app.repository.getAppSettings()

            // Load initial state
            cachedShieldActive = settings.shieldActiveFlow.first()
            cachedKeywordBlockingEnabled = settings.keywordBlockingEnabledFlow.first()
            
            // Observe changes
            launch { settings.shieldActiveFlow.collect { cachedShieldActive = it } }
            launch { settings.keywordBlockingEnabledFlow.collect { cachedKeywordBlockingEnabled = it } }
            launch { settings.blockedKeywordsFlow.collect { keywords -> 
                updateKeywordPatterns(keywords)
            }}

            Timber.d("KeywordDetector started: shield=$cachedShieldActive, keywords=${keywordPatterns.size}")
        }
    }

    private fun updateKeywordPatterns(keywords: Set<String>) {
        keywordPatterns = keywords.mapNotNull { keyword ->
            try {
                // Convert wildcards to regex: * -> .*, ? -> .
                val regex = keyword
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .replace("?", ".")
                Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
            } catch (e: Exception) {
                Timber.w(e, "Invalid keyword pattern: $keyword")
                null
            }
        }
        Timber.d("Updated keyword patterns: ${keywordPatterns.size}")
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!cachedShieldActive || !cachedKeywordBlockingEnabled) return
        if (keywordPatterns.isEmpty()) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        
        // Only check browsers for URL bar, or check all for general text input
        val isBrowser = browserPackages.contains(pkg)
        if (!isBrowser && eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val text = event.text?.toString() ?: return
        if (text.isBlank()) return

        checkTextForKeywords(pkg, text, isBrowser)
    }

    private fun checkTextForKeywords(pkg: String, text: String, isBrowser: Boolean) {
        for (pattern in keywordPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val matchedKeyword = matcher.group()
                Timber.w("Keyword detected: '$matchedKeyword' in $pkg (browser=$isBrowser)")
                
                // For browsers, check if it's in URL bar (more aggressive blocking)
                // For other apps, just log and optionally block
                if (isBrowser || isLikelyUrlBar(eventSourcePackage = pkg)) {
                    performBlockingAction(pkg, matchedKeyword)
                }
                break // Stop at first match
            }
        }
    }

    private fun isLikelyUrlBar(eventSourcePackage: String): Boolean {
        // Could be enhanced to check view IDs for URL bars
        // For now, we block on any text change in browsers
        return browserPackages.contains(eventSourcePackage)
    }

    private fun performBlockingAction(pkg: String, keyword: String) {
        host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        
        // Log the block event
        serviceScope.launch {
            try {
                val app = host.applicationContext as GuardianApp
                app.repository.recordBlock(pkg, "Keyword Filter", "keyword:$keyword")
            } catch (e: Exception) {
                Timber.w(e, "Failed to log keyword block")
            }
        }
        
        Timber.w("Blocked $pkg due to keyword: $keyword")
    }

    fun shutdown() {
        serviceScope.cancel()
    }
}