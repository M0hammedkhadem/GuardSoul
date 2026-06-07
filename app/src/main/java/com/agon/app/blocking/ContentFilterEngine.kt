package com.agon.app.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.R
import com.agon.app.data.local.entity.BlocklistItemEntity
import com.agon.app.guardianApp
import com.agon.app.ui.screens.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Keyword-trie + domain-substring filter engine.
 *
 * One half of the original [com.agon.app.blocking.GuardianEngine]
 * (which was split into [ContentFilterEngine] and
 * [UninstallGuardEngine] so the unified
 * [com.agon.app.services.GuardSoulAccessibilityService] can compose
 * them as small, single-purpose engines alongside
 * [ShortstopEngine] and [AiExplorerEngine]).
 *
 * Responsibilities:
 *  - build a [KeywordTrie] from the user-controlled blacklist of
 *    keywords (filtered through the whitelist),
 *  - hold a substring-boundary checker for the blacklisted domains
 *    list,
 *  - on every `WINDOW_STATE_CHANGED` / `WINDOW_CONTENT_CHANGED`
 *    event, walk the active window, and bounce to the home
 *    screen + record a `keyword_block` if either match hits.
 *
 * The engine never touches Settings / Phone Manager / destructive
 * buttons — that lives in [UninstallGuardEngine].
 *
 * The host service injects a shared [bounceCooldown] so the
 * content filter and the uninstall guard don't fight over the
 * home animation.
 */
class ContentFilterEngine(
    private val host: AccessibilityService,
    private val bounceCooldown: BlockCooldownTracker,
) {

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val keywordTrie = KeywordTrie()

    private val repo by lazy {
        host.applicationContext.guardianApp()?.repository
            ?: throw IllegalStateException("Repo not found")
    }

    @Volatile private var cachedShieldActive = false
    @Volatile private var cachedBlockAction = "block_screen"
    @Volatile private var cachedWhitelistKeywords = emptySet<String>()
    @Volatile private var cachedWhitelistApps = emptySet<String>()
    @Volatile private var cachedBlacklistDomains = emptySet<String>()

    /** Subscribe to settings/blocklist flows. Called from the host. */
    fun start() {
        ioScope.launch { observeSettings() }
    }

    /** Unsubscribe and cancel the ioScope. Called from the host. */
    fun stop() {
        ioScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private suspend fun observeSettings() = coroutineScope {
        val settings = repo.getAppSettings()

        // Issue #143 & #170: Bind all settings to Flows for real-time updates
        launch {
            settings.shieldActiveFlow.collect { cachedShieldActive = it }
        }

        launch {
            settings.shortsBlockActionFlow.collect { cachedBlockAction = it }
        }

        launch {
            combine(
                repo.getBlocklistFlow("whitelist", "keywords"),
                repo.getBlocklistFlow("whitelist", "apps"),
                repo.getBlocklistFlow("blacklist", "keywords"),
                repo.getBlocklistFlow("blacklist", "websites"),
            ) { whiteKw, whiteApps, blackKw, blackSites ->
                arrayOf(whiteKw, whiteApps, blackKw, blackSites)
            }.distinctUntilChanged().collect { arr ->
                @Suppress("UNCHECKED_CAST")
                val whiteKw = arr[0] as List<BlocklistItemEntity>
                @Suppress("UNCHECKED_CAST")
                val whiteApps = arr[1] as List<BlocklistItemEntity>
                @Suppress("UNCHECKED_CAST")
                val blackKw = arr[2] as List<BlocklistItemEntity>
                @Suppress("UNCHECKED_CAST")
                val blackSites = arr[3] as List<BlocklistItemEntity>

                cachedWhitelistKeywords = whiteKw.map { it.value.lowercase() }.toSet()
                cachedWhitelistApps = whiteApps.map { it.value }.toSet()

                val filteredKeywords = blackKw.map { it.value }
                    .filter { it.lowercase() !in cachedWhitelistKeywords }
                cachedBlacklistDomains = blackSites.map { it.value.lowercase() }
                    .filter { it !in cachedWhitelistKeywords }
                    .toSet()

                keywordTrie.build(filteredKeywords)
            }
        }
    }

    /**
     * Main event entry point — called by the host service.
     *
     * @param preFetchedRoot CF-001: an already-resolved
     *   `host.rootInActiveWindow` from the host's dispatch loop.
     *   When non-null we skip the duplicate IPC round-trip that
     *   would otherwise be made by each of the four engines per
     *   event (3 wasted IPCs in the previous design).
     *   The caller still owns the node and is responsible for
     *   recycling it.
     */
    fun onAccessibilityEvent(event: AccessibilityEvent, preFetchedRoot: AccessibilityNodeInfo? = null) {
        if (!cachedShieldActive) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == host.packageName || pkg == "com.android.systemui" || pkg == "android") return
        if (pkg in cachedWhitelistApps) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            // CF-001: prefer the pre-fetched root from the host; only
            // call `rootInActiveWindow` if the host didn't supply one
            // (e.g. unit tests, direct engine invocation).
            if (preFetchedRoot == null) {
                // Fall back to the per-engine IPC. We own the node
                // in that branch.
                val root = host.rootInActiveWindow ?: return
                try {
                    evaluateRoot(root, pkg)
                } finally {
                    root.recycle()
                }
            } else {
                // Host owns the node — no recycle here.
                evaluateRoot(preFetchedRoot, pkg)
            }
        }
    }

    private fun evaluateRoot(root: AccessibilityNodeInfo, pkg: String) {
        val fullText = AccessibilityTreeUtils.extractAllText(root)
        if (fullText.isBlank()) return
        val keywordMatch = keywordTrie.hasMatch(fullText)
        val domainMatch = containsBlockedDomain(fullText)
        if (keywordMatch || domainMatch) {
            handleProhibitedContent(pkg)
        }
    }

    /** Called from the host when the framework interrupts us. */
    fun onInterrupt() {
        // No overlay to dismiss in this engine.
    }

    private fun handleProhibitedContent(pkg: String) {
        if (!bounceCooldown.tryFire()) return

        val label = getAppLabel(pkg)
        ioScope.launch {
            repo.recordBlock(pkg, label, "keyword_block")
        }

        if (cachedBlockAction == "exit") {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            host.startActivity(home)
        } else {
            host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            val intent = Intent(host, BlockActivity::class.java).apply {
                putExtra("APP_NAME", label)
                putExtra("BLOCK_REASON", "keyword_block")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            host.startActivity(intent)
        }
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val ai = host.packageManager.getApplicationInfo(pkg, 0)
            host.packageManager.getApplicationLabel(ai).toString()
        } catch (_: PackageManager.NameNotFoundException) { pkg }
    }

    /**
     * Check whether [text] contains any blacklisted domain. A match is counted
     * when a domain appears as a substring that is bounded by either:
     * - a non-letter (e.g. "pornhub.com/x" or "pornhub.com "),
     * - the start/end of the text.
     *
     * We use simple `String.contains` rather than building a full URL parser:
     * accessibility events surface URL text exactly as the user (or the
     * app) wrote it, so substring-with-boundary-check is sufficient and
     * keeps the hot path allocation-free.
     */
    private fun containsBlockedDomain(text: String): Boolean {
        if (cachedBlacklistDomains.isEmpty()) return false
        val lower = text.lowercase()
        for (domain in cachedBlacklistDomains) {
            if (domain.isEmpty()) continue
            val idx = lower.indexOf(domain)
            if (idx < 0) continue
            // Make sure the character after the match is not a domain-char
            // (avoids matching "pornhub.com.evil.com" when "pornhub.com" is
            // blocked — actually we DO want to match it; we only want to
            // skip a domain fragment that is part of a longer domain name
            // whose first label is different). We approximate "different
            // first label" by requiring a non-letter, slash, or '.' before
            // the match position.
            val before = if (idx > 0) lower[idx - 1] else ' '
            val after = lower.getOrElse(idx + domain.length) { ' ' }
            val beforeOk = !before.isLetterOrDigit() && before != '-'
            val afterOk = !after.isLetterOrDigit() && after != '-'
            if (beforeOk && afterOk) return true
        }
        return false
    }
}
