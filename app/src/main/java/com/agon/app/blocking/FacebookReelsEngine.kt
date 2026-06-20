package com.agon.app.blocking

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Dedicated engine for Facebook Reels blocking.
 *
 * Separated from YouTube Shorts engine because Facebook's architecture differs:
 * - com.facebook.katana (main app) uses Fragments with specific view IDs
 * - com.facebook.lite (lite app) uses WebView + different view IDs
 * - Reels appear in multiple entry points: Watch tab, Feed inline, Profile, Notifications
 * - Navigation is fragment-based, not activity-based
 */
class FacebookReelsEngine(private val host: AccessibilityService) {

    // ─── Configuration ────────────────────────────────────────────────────

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val patternMatcher = com.agon.app.blocking.PatternMatcher()

    // Cached signatures — avoids map lookup on every event (matching YouTube's youtubeSig)
    private val katanaSig: com.agon.app.blocking.PatternMatcher.Signature? = patternMatcher.signatureFor("com.facebook.katana")
    private val liteSig: com.agon.app.blocking.PatternMatcher.Signature? = patternMatcher.signatureFor("com.facebook.lite")

    // Facebook-specific fast tokens (from PatternMatcher + additional discovered)
    private val fbReelsTokens = listOf(
        "reels_viewer_fragment_container",
        "reels_video_container",
        "reels_inner_video_container",
        "reel_viewer_container",
        "reel_composer_container",
        "reels_composer_container",
        "reels_video_view",
        "reel_container",
        "reels_root_view",
        "reels_player_view",
        "com.facebook.reels",
        "reels_fullscreen_player"
    )

    private val fbLiteReelsTokens = listOf(
        "reels_video_view",
        "reel_container",
        "video_player_view",
        "reels_view"
    )

    // Per-package cooldowns (ms) — matching YouTube's aggressive 200ms
    private val katanaCooldownMs = 200L
    private val liteCooldownMs = 300L

    // Throttle for WINDOW_CONTENT_CHANGED (fires heavily in FB feed)
    private val contentChangeThrottleMs = 150L
    private val lastContentChangeMs = ConcurrentHashMap<String, Long>()

    // Last redirect tracking
    private val lastRedirectMs = ConcurrentHashMap<String, Long>()

    // Screen dimensions for position constraints
    private var screenWidth = 0
    private var screenHeight = 0

    // ─── State ────────────────────────────────────────────────────────────

    private var cachedShieldActive = false
    @Volatile private var cachedFacebookMode = "off"
    @Volatile private var cachedBlockedApps: Set<String> = emptySet()
    private val tempBan = TempBanManager.getInstance(host.applicationContext)

    // ─── Public API ───────────────────────────────────────────────────────

    fun start(app: com.agon.app.GuardianApp) {
        val settings = app.repository.getAppSettings()

        // Initialize screen dimensions — use modern API on API 30+
        val windowManager = app.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            screenWidth = metrics.bounds.width()
            screenHeight = metrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val displayMetrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
        }

        serviceScope.launch {
            cachedShieldActive = settings.shieldActiveFlow.first()
            cachedFacebookMode = settings.facebookModeFlow.first()
            cachedBlockedApps = settings.blockedAppsFlow.first()

            launch { settings.shieldActiveFlow.collect { cachedShieldActive = it } }
            launch { settings.facebookModeFlow.collect { cachedFacebookMode = it } }
            launch { settings.blockedAppsFlow.collect { cachedBlockedApps = it } }

            Timber.d("FacebookReelsEngine started: shield=$cachedShieldActive, mode=$cachedFacebookMode, screen=${screenWidth}x$screenHeight")
        }
    }

    /** Main entry point from GuardSoulAccessibilityService */
    fun onAccessibilityEvent(event: AccessibilityEvent, root: AccessibilityNodeInfo?) {
        if (!cachedShieldActive) return

        val pkg = event.packageName?.toString() ?: return
        if (!isFacebookPackage(pkg)) return

        // Check temp ban first — block immediately if in cooldown
        if (tempBan.isInCooldown(pkg)) {
            Timber.w("FacebookReels: $pkg is in TEMP BAN, forcing HOME")
            redirectToHome(pkg, "temp_ban_cooldown", System.currentTimeMillis(), isFullBlock = true)
            return
        }

        val now = System.currentTimeMillis()
        val eventType = event.eventType

        // 1. Fast path: VIEW_CLICKED — user tapped a Reel thumbnail or tab
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            handleViewClicked(event, pkg, now)
            return
        }

        // 2. WINDOW_STATE_CHANGED — new screen opened
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (root != null) handleWindowStateChanged(root, pkg, now)
            return
        }

        // 3. WINDOW_CONTENT_CHANGED — content updated (throttled)
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            handleContentChanged(root, pkg, now)
        }
    }

    // ─── Core Detection Logic ─────────────────────────────────────────────

    private fun handleViewClicked(event: AccessibilityEvent, pkg: String, now: Long) {
        val source = event.source ?: return

        // Full-block check first (highest priority)
        if (isFullBlockRequired(pkg)) {
            redirectToHome(pkg, "full_block_click", now, isFullBlock = true)
            return
        }

        // Feed blocking only if mode == "reels"
        if (!isFeedBlockingEnabled(pkg)) return

        // Immediate token check on clicked node
        if (hasFacebookReelsToken(source, pkg)) {
            redirectToHome(pkg, "click_direct", now)
            return
        }

        // Walk up to 3 ancestors (Reels player often parent/sibling of clicked thumbnail)
        var ancestor = source.parent
        var depth = 0
        while (ancestor != null && depth < 3) {
            if (hasFacebookReelsToken(ancestor, pkg)) {
                redirectToHome(pkg, "click_ancestor_depth$depth", now)
                return
            }
            ancestor = ancestor.parent
            depth++
        }
    }

    private fun handleWindowStateChanged(root: AccessibilityNodeInfo, pkg: String, now: Long) {
        // Full-block check first
        if (isFullBlockRequired(pkg)) {
            redirectToHome(pkg, "full_block_window", now, isFullBlock = true)
            return
        }

        // Feed blocking only if mode == "reels"
        if (!isFeedBlockingEnabled(pkg)) return

        // Smart ignore: Check if we're in a DM/conversation context
        if (isInDmsContext(root, pkg)) {
            Timber.d("FacebookReels: Skipping block - DM context detected")
            return
        }

        // Improved detection per report:
        // 1. Check for "Reels, tab" with isSelected == true (user actively selected Reels tab)
        // 2. Check for Reels video with position constraints (top < 50% screen, height > 30% screen)
        if (isReelsTabSelected(root) || isReelsVideoActive(root)) {
            redirectToHome(pkg, "window_state_reels_detected", now)
            return
        }

        // Fallback: fast token pre-check (original logic)
        if (hasAnyFacebookReelsToken(root, pkg)) {
            redirectToHome(pkg, "window_state_precheck", now)
            return
        }

        // Fallback: full findFeedViewId search using CACHED signature
        val sig = if (pkg == "com.facebook.lite") liteSig else katanaSig
        if (sig != null && findFeedViewIdFast(root, pkg, sig) != null) {
            redirectToHome(pkg, "window_state_full", now)
        }
    }

    private fun handleContentChanged(root: AccessibilityNodeInfo?, pkg: String, now: Long) {
        if (root == null) return

        // Full-block check
        if (isFullBlockRequired(pkg)) {
            redirectToHome(pkg, "full_block_content", now, isFullBlock = true)
            return
        }

        // Feed blocking only if mode == "reels"
        if (!isFeedBlockingEnabled(pkg)) return

        // Smart ignore: Check if we're in a DM/conversation context
        if (isInDmsContext(root, pkg)) {
            return
        }

        // Throttle: Facebook fires WINDOW_CONTENT_CHANGED aggressively during scroll
        val last = lastContentChangeMs[pkg] ?: 0L
        if (now - last < contentChangeThrottleMs) return
        lastContentChangeMs[pkg] = now

        // Improved detection: only block on confirmed Reels surfaces, not feed scroll
        if (isReelsTabSelected(root) || isReelsVideoActive(root)) {
            redirectToHome(pkg, "content_change_reels", now)
        }
    }

    // ─── Fast Token Detection ─────────────────────────────────────────────

    /** Ultra-fast token check on a single node */
    private fun hasFacebookReelsToken(node: AccessibilityNodeInfo?, pkg: String): Boolean {
        if (node == null) return false
        val viewId = node.viewIdResourceName
        if (viewId == null) return false

        val tokens = if (pkg == "com.facebook.lite") fbLiteReelsTokens else fbReelsTokens
        for (token in tokens) {
            if (viewId.contains(token)) return true
        }
        return false
    }

    /** Recursively scans tree for any Facebook Reels token — fast pre-check */
    private fun hasAnyFacebookReelsToken(node: AccessibilityNodeInfo?, pkg: String): Boolean {
        if (node == null) return false
        if (hasFacebookReelsToken(node, pkg)) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (hasAnyFacebookReelsToken(child, pkg)) {
                    child.recycle()
                    return true
                }
                child.recycle()
            }
        }
        return false
    }

    /** Fast findFeedViewId using native API with early exit */
    private fun findFeedViewIdFast(root: AccessibilityNodeInfo, pkg: String, sig: com.agon.app.blocking.PatternMatcher.Signature): String? {
        for (token in sig.surfaceViewIdTokens) {
            val matches = root.findAccessibilityNodeInfosByViewId("$pkg:id/$token")
            if (matches.isNotEmpty()) {
                matches.forEach { it.recycle() }
                return token
            }
        }
        return null
    }

    // ─── Action & Cooldown ────────────────────────────────────────────────

    private fun redirectToHome(pkg: String, reason: String, now: Long, isFullBlock: Boolean = false) {
        val last = lastRedirectMs[pkg] ?: 0L
        val cooldown = if (pkg == "com.facebook.lite") liteCooldownMs else katanaCooldownMs
        if (now - last < cooldown) return
        lastRedirectMs[pkg] = now

        if (isFullBlock) {
            // Full block: HOME directly (no BACK needed)
            host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            Timber.w("FacebookReels: HOME (full block) from $pkg ($reason)")
        } else {
            // Feed block: BACK pops the Reels player
            host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            Timber.w("FacebookReels: BACK (feed block) from $pkg ($reason)")
        }

        // Record strike for temp-ban logic
        tempBan.recordStrike(pkg)

        // Record block event
        serviceScope.launch {
            try {
                val app = host.applicationContext as com.agon.app.GuardianApp
                val reasonStr = if (isFullBlock) "facebook_reels_full" else "facebook_reels_feed"
                app.repository.recordBlock(pkg, "Facebook Reels", reasonStr)
            } catch (e: Exception) {
                Timber.w(e, "recordBlock failed")
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun isFacebookPackage(pkg: String): Boolean =
        pkg == "com.facebook.katana" || pkg == "com.facebook.lite"

    private fun isFeedBlockingEnabled(pkg: String): Boolean =
        isFacebookPackage(pkg) && cachedFacebookMode == "reels"

    private fun isFullBlockRequired(pkg: String): Boolean =
        cachedBlockedApps.contains(pkg) ||
        (pkg == "com.facebook.katana" && cachedFacebookMode == "full") ||
        (pkg == "com.facebook.lite" && cachedFacebookMode == "full")

    /**
     * Smart ignore: Detect if user is in a DM/conversation context.
     * Checks for reply bar edit text or sender username - if present, don't block Reels.
     * Uses the actual package name for view ID prefix (handles both katana and lite).
     */
    private fun isInDmsContext(root: AccessibilityNodeInfo, pkg: String): Boolean {
        val liteViewIds = listOf(
            "reply_bar_edittext",
            "message_input",
            "composer_text_input",
            "sender_username_or_fullname"
        )
        val viewIds = if (pkg == "com.facebook.lite") liteViewIds else listOf(
            "reply_bar_edittext",
            "sender_username_or_fullname",
            "message_input",
            "composer_text_input"
        )
        
        for (viewId in viewIds) {
            val matches = root.findAccessibilityNodeInfosByViewId("$pkg:id/$viewId")
            if (matches.isNotEmpty()) {
                matches.forEach { it.recycle() }
                return true
            }
        }
        return false
    }

    /**
     * Detection 1: Check if Reels tab is selected (user actively navigated to Reels tab).
     * Uses viewId-based detection (locale-independent) + contentDescription fallback.
     * Per report: verify node.isSelected == true to distinguish from passive visibility in nav bar.
     */
    private fun isReelsTabSelected(root: AccessibilityNodeInfo): Boolean {
        // Primary: viewId-based detection (locale-independent)
        val tabViewIds = listOf(
            "reels_tab", "tab_reels", "navigation_reels", "reels_tab_button"
        )
        
        for (viewId in tabViewIds) {
            val matches = root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/$viewId")
            if (matches.isNotEmpty()) {
                for (match in matches) {
                    if (match.isSelected) {
                        matches.forEach { it.recycle() }
                        return true
                    }
                    match.recycle()
                }
            }
        }
        
        // Fallback: contentDescription with locale-independent patterns
        // Look for "Reels" + selected state, avoiding false positives from Arabic "ريلز" etc.
        fun checkNode(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val isSelected = node.isSelected
            
            // Check if content description contains "reels" AND node is selected
            // This works for English "Reels, tab" and similar patterns
            if (isSelected && contentDesc.contains("reels") && (contentDesc.contains("tab") || contentDesc.contains(",") || contentDesc.length < 20)) {
                return true
            }
            
            // Check children
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    if (checkNode(child)) {
                        child.recycle()
                        return true
                    }
                    child.recycle()
                }
            }
            return false
        }
        
        return checkNode(root)
    }

    /**
     * Detection 2: Check for active Reels video player with position constraints.
     * Per report: node bounds top < screenHeight * 0.5 AND height > screenHeight * 0.3
     * This distinguishes actual video player from feed thumbnails.
     */
    private fun isReelsVideoActive(root: AccessibilityNodeInfo): Boolean {
        if (screenHeight <= 0) return false
        
        val maxTop = screenHeight * 0.5f
        val minHeight = screenHeight * 0.3f
        
        fun checkNode(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            
            // Check if this node looks like a Reels video player
            val isReelsContent = contentDesc.startsWith("reels") || 
                viewId.contains("reels") && (viewId.contains("player") || viewId.contains("video") || viewId.contains("container"))
            
            if (isReelsContent) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                val nodeTop = bounds.top
                val nodeHeight = bounds.height()
                
                // Position constraint: top < 50% screen, height > 30% screen
                if (nodeTop < maxTop && nodeHeight > minHeight) {
                    return true
                }
            }
            
            // Check children
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    if (checkNode(child)) {
                        child.recycle()
                        return true
                    }
                    child.recycle()
                }
            }
            return false
        }
        
        return checkNode(root)
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────

    fun stop() {
        serviceScope.cancel()
    }

    fun onInterrupt() = Unit
}