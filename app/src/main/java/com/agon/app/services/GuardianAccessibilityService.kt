package com.agon.app.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.guardianApp
import com.agon.app.R
import com.agon.app.data.local.entity.BlocklistItemEntity
import com.agon.app.ui.screens.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

class GuardianAccessibilityService : AccessibilityService() {

    companion object {
        private const val BLOCK_COOLDOWN_MS = 1500L
        private const val MAX_LAYOUT_DEPTH = 50 // Issue #253: Increased depth for complex layouts

        /**
         * Settings-related package names across OEMs. The uninstall guard
         * is only effective on these; any other package is ignored.
         */
        private val KNOWN_SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.miui.securitycenter",
            "com.miui.settings",
            "com.samsung.android.settings",
            "com.huawei.systemmanager",
            "com.coloros.settings",
            "com.oppo.settings",
        )

        /**
         * Phone Manager / device-care apps that ship a "force-stop" or
         * "uninstall" workflow that doesn't go through the AOSP Settings
         * App Info screen. Qustodio blocks all of these while
         * uninstall-protection is on — we mirror that. Each entry has
         * been seen in the wild on at least one OEM.
         */
        private val PHONE_MANAGER_PACKAGES = setOf(
            // Xiaomi / MIUI / Redmi / Poco
            "com.miui.securitycenter",
            "com.miui.managecenter",
            "com.miui.cleanmaster",
            "com.xiaomi.gamecenter",
            "com.xiaomi.joyose",
            // Huawei / Honor
            "com.huawei.systemmanager",
            "com.huawei.hwireader",
            "com.huawei.intelligent",
            "com.hihonor.systemmanager",
            // Samsung
            "com.samsung.android.lool",
            "com.samsung.android.sm_cn",
            "com.samsung.android.sm.dev",
            // OPPO / Realme / OnePlus / ColorOS
            "com.coloros.safecenter",
            "com.coloros.oppoguardelf",
            "com.oppo.safe",
            "com.realme.securitycenter",
            "com.oneplus.security",
            // Vivo
            "com.vivo.permissionmanager",
            "com.iqoo.secure",
            // Lenovo / Motorola / Asus
            "com.lenovo.safecenter",
            "com.asus.mobilemanager",
            "com.motorola.security",
            // ZTE / Nubia
            "com.zte.heartyservice",
            // Generic AOSP
            "com.android.systemui",
        )

        /**
         * Visible button labels that indicate the user is one tap away
         * from breaking GuardSoul. Matched case-insensitively against
         * the concatenated text of the foreground window.
         */
        private val DESTRUCTIVE_ACTION_KEYWORDS = listOf(
            "Force stop", "Force Stop", "إيقاف قسري",
            "Clear data", "مسح البيانات",
            "Clear cache", "مسح ذاكرة التخزين المؤقت",
            "Uninstall", "إلغاء التثبيت",
            "Disable", "تعطيل",
            "Disable app", "تعطيل التطبيق",
            "Uninstall updates", "إلغاء تحديثات",
            "Storage & cache", "التخزين وذاكرة التخزين المؤقت",
        )
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val keywordTrie = KeywordTrie()

    private val repo by lazy {
        applicationContext.guardianApp()?.repository ?: throw IllegalStateException("Repo not found")
    }

    @Volatile private var lastBlockTime = 0L
    @Volatile private var cachedShieldActive = false
    @Volatile private var cachedBlockAction = "block_screen"
    @Volatile private var cachedWhitelistKeywords = emptySet<String>()
    @Volatile private var cachedWhitelistApps = emptySet<String>()
    @Volatile private var cachedBlacklistDomains = emptySet<String>()
    /**
     * True while the user has installed GuardSoul as Device Admin and
     * either the uninstall-protection toggle or strong protection is on.
     * While this is on we watch the foreground Settings window and kick
     * the user back to home if they navigate to GuardSoul's App Info page.
     */
    @Volatile private var cachedUninstallProtection = false

    private class KeywordTrie {
        private class Node {
            val children = mutableMapOf<Char, Node>()
            var failure: Node? = null
            var isLeaf = false
            val outputs = mutableListOf<String>()
        }
        private var root = Node()

        @Synchronized
        fun build(keywords: List<String>) {
            val newRoot = Node()
            for (kw in keywords) {
                val word = kw.lowercase().trim()
                if (word.isEmpty()) continue
                var current = newRoot
                for (char in word) current = current.children.getOrPut(char) { Node() }
                current.isLeaf = true
                current.outputs.add(word)
            }
            
            val queue = java.util.ArrayDeque<Node>()
            for (child in newRoot.children.values) { child.failure = newRoot; queue.add(child) }
            while (queue.isNotEmpty()) {
                val current = queue.poll() ?: continue
                for ((char, child) in current.children) {
                    var f = current.failure
                    while (f != null && !f.children.containsKey(char)) f = f.failure
                    child.failure = if (f == null) newRoot else f.children[char]
                    child.outputs.addAll(child.failure?.outputs ?: emptyList())
                    queue.add(child)
                }
            }
            root = newRoot
        }

        @Synchronized
        fun hasMatch(text: String): Boolean {
            val lower = text.lowercase()
            var current = root
            for (i in lower.indices) {
                while (current != root && !current.children.containsKey(lower[i])) current = current.failure ?: root
                current = current.children[lower[i]] ?: root
                if (current.isLeaf || current.outputs.isNotEmpty()) {
                    // Check word boundaries for better accuracy
                    val longestMatch = current.outputs.maxByOrNull { it.length } ?: ""
                    val start = i - longestMatch.length + 1
                    val before = if (start > 0) lower[start - 1] else ' '
                    val after = if (i + 1 < lower.length) lower[i + 1] else ' '
                    if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
                }
            }
            return false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Register as the global bounce delegate so background services
        // (AiScannerService, etc.) can pop the back stack and drop the
        // user on the home screen via `BounceHelper.backToHome`.
        guardianApp()?.accessibilityBounceDelegate = this
        observeSettings()
        Timber.d("GuardianAccessibilityService connected")
    }

    private fun observeSettings() {
        ioScope.launch {
            val settings = repo.getAppSettings()

            // Issue #143 & #170: Bind all settings to Flows for real-time updates
            launch {
                settings.shieldActiveFlow.collect { cachedShieldActive = it }
            }

            launch {
                settings.shortsBlockActionFlow.collect { cachedBlockAction = it }
            }

            // FEATURES_SPEC §5: watch the uninstall-protection flag and
            // block attempts to reach GuardSoul's App Info screen.
            launch {
                settings.uninstallProtectionFlow.collect { cachedUninstallProtection = it }
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
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear the global bounce delegate reference so background
        // services fall back to the home intent rather than calling
        // into a dead service.
        if (guardianApp()?.accessibilityBounceDelegate === this) {
            guardianApp()?.accessibilityBounceDelegate = null
        }
        ioScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!cachedShieldActive) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == this.packageName || pkg == "com.android.systemui" || pkg == "android") return
        if (pkg in cachedWhitelistApps) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Uninstall protection guard. The Settings app's window class
            // name for "App Info" varies across OEMs ("Settings$AppDetailsActivity"
            // on AOSP, "SubSettings" on Samsung, "ManageApplications" on
            // some MIUI builds). We watch for any of those + the Settings
            // package and confirm via a text scan that GuardSoul is the
            // target app before kicking the user out.
            if (cachedUninstallProtection) {
                handleUninstallProtectionAttempt(event, pkg)
                // Qustodio-style: also block the OEM "Phone Manager" apps
                // that ship their own force-stop / uninstall workflow.
                handlePhoneManagerAttempt(event, pkg)
                // Heavier: catch destructive buttons (Force stop / Clear
                // data / Disable / Uninstall) on App Info pages that
                // don't carry the usual class name.
                handleForceStopOrClearDataAttempt(event, pkg)
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val root = rootInActiveWindow ?: return
            try {
                val fullText = extractAllText(root)
                if (fullText.isBlank()) return
                val keywordMatch = keywordTrie.hasMatch(fullText)
                val domainMatch = containsBlockedDomain(fullText)
                if (keywordMatch || domainMatch) {
                    handleProhibitedContent(pkg)
                }
            } finally {
                root.recycle()
            }
        }
    }

    /**
     * Called from [onAccessibilityEvent] when the user has uninstall
     * protection turned on and the foreground window appears to be the
     * system Settings app. We look for a GuardSoul marker in the visible
     * text; if present, we record a tamper alert and bounce the user back
     * to the home screen.
     */
    private fun handleUninstallProtectionAttempt(event: AccessibilityEvent, pkg: String) {
        // Settings package names vary across OEMs. AOSP and most modern
        // Android skins funnel App Info through com.android.settings, but
        // MIUI / Xiaomi routes it through com.miui.securitycenter or
        // com.miui.settings, and some Samsung builds use
        // com.samsung.android.settings. The full list lives in
        // [KNOWN_SETTINGS_PACKAGES] so the lookup is allocation-free.
        if (pkg !in KNOWN_SETTINGS_PACKAGES) return
        val className = event.className?.toString() ?: return
        if (!className.contains("Settings", ignoreCase = true) &&
            !className.contains("ManageApplications", ignoreCase = true) &&
            !className.contains("AppInfo", ignoreCase = true) &&
            !className.contains("ApplicationDetails", ignoreCase = true)) {
            return
        }
        val root = rootInActiveWindow ?: return
        try {
            val text = extractAllText(root)
            if (text.isBlank()) return
            val targetIsGuardian = text.contains("com.agon.app", ignoreCase = true) ||
                text.contains("GuardSoul", ignoreCase = true) ||
                text.contains(getString(R.string.app_name), ignoreCase = true)
            if (targetIsGuardian) {
                ioScope.launch {
                    try {
                        repo.recordTamperAlert(
                            "uninstall_attempt",
                            "User opened GuardSoul's App Info page while uninstall protection was on."
                        )
                    } catch (_: Exception) {}
                }
                // Bounce to home — the user can reopen GuardSoul from the
                // launcher, but they can't reach the App Info / Uninstall
                // button through Settings.
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        } finally {
            root.recycle()
        }
    }

    /**
     * Qustodio-style guard: block the OEM "Phone Manager" / "Security
     * Center" apps that can be used to force-stop or uninstall any
     * package, including us. We treat any window from a known Phone
     * Manager package as an attempt to tamper with the device and
     * bounce the user back to home while recording a tamper alert.
     *
     * The cooldown lives on [lastBlockTime] so we don't spam alerts if
     * the user keeps trying.
     */
    private fun handlePhoneManagerAttempt(event: AccessibilityEvent, pkg: String) {
        if (pkg !in PHONE_MANAGER_PACKAGES) return
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < BLOCK_COOLDOWN_MS) return
        lastBlockTime = now

        val appLabel = getAppLabel(pkg)
        ioScope.launch {
            try {
                repo.recordTamperAlert(
                    "phone_manager_opened",
                    "Opened $appLabel ($pkg) while uninstall protection was on."
                )
            } catch (_: Exception) {}
        }
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Heavier guard: when the user is inside an App Info page and the
     * visible screen contains *both* a GuardSoul marker and one of the
     * destructive buttons (Force stop / Clear data / Uninstall / Disable),
     * bounce the user back to home even if the class name doesn't match
     * the AOSP `Settings$*` pattern. This catches OEM-specific
     * confirm-dialog screens that don't carry the usual class name but
     * still expose a clickable destructive action.
     *
     * Only triggered on Settings-related packages (already filtered
     * upstream) and only when [cachedUninstallProtection] is on.
     */
    private fun handleForceStopOrClearDataAttempt(event: AccessibilityEvent, pkg: String) {
        if (pkg !in KNOWN_SETTINGS_PACKAGES) return
        val root = rootInActiveWindow ?: return
        try {
            val text = extractAllText(root)
            if (text.isBlank()) return
            val guardian =
                text.contains("com.agon.app", ignoreCase = true) ||
                text.contains("GuardSoul", ignoreCase = true) ||
                text.contains(getString(R.string.app_name), ignoreCase = true)
            if (!guardian) return
            val destructive = DESTRUCTIVE_ACTION_KEYWORDS.any { text.contains(it, ignoreCase = true) }
            if (!destructive) return

            val now = System.currentTimeMillis()
            if (now - lastBlockTime < BLOCK_COOLDOWN_MS) return
            lastBlockTime = now

            ioScope.launch {
                try {
                    repo.recordTamperAlert(
                        "destructive_action",
                        "Destructive action button visible for GuardSoul: ${text.take(160)}"
                    )
                } catch (_: Exception) {}
            }
            performGlobalAction(GLOBAL_ACTION_HOME)
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() {}

    private fun handleProhibitedContent(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < BLOCK_COOLDOWN_MS) return
        lastBlockTime = now

        val label = getAppLabel(pkg)
        ioScope.launch {
            repo.recordBlock(pkg, label, "keyword_block")
        }

        if (cachedBlockAction == "exit") {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(home)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
            val intent = Intent(this, BlockActivity::class.java).apply {
                putExtra("APP_NAME", label)
                putExtra("BLOCK_REASON", "keyword_block")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        }
    }

    private fun extractAllText(node: AccessibilityNodeInfo, depth: Int = 0): String {
        if (depth > MAX_LAYOUT_DEPTH) return ""
        
        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            sb.append(extractAllText(child, depth + 1))
            child.recycle()
        }
        return sb.toString()
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
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
