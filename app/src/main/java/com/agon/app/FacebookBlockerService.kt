package com.agon.app

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.agon.app.data.local.entity.AppLimitEntity
import com.agon.app.data.repository.AppRepository
import com.agon.app.data.settings.AppSettings
import com.agon.app.ui.screens.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar

class FacebookBlockerService : AccessibilityService() {

    companion object {
        private const val VIDEO_BLOCK_NOTIFICATION_ID = 2001
        private const val APP_BLOCK_NOTIFICATION_ID = 2002
        private const val BLOCK_COOLDOWN_MS = 1000L
        private const val BACK_PRESS_COUNT = 3
        private const val BACK_PRESS_INTERVAL_MS = 30L
        private const val PARTIAL_BLOCK_DELAY_MS = 50L

        private val REEL_TABS = listOf("reels", "reel", "ريلز", "shorts", "شورت")
        private val REEL_CONTENT = listOf("reels", "reel", "ريلز", "shorts", "شورت")
        private val YOUTUBE_SHORTS_KEYWORDS = listOf("shorts", "شورت")
        private val FACEBOOK_REELS_KEYWORDS = listOf("reels", "reel", "ريلز", "reels_tab")
        private val FULLSCREEN_KEYWORDS = listOf(
            "fullscreen", "exit fullscreen",
            "double tap to like"
        )

        private const val ANTI_SCROLL_WINDOW_MS = 3_000L
        private const val ANTI_SCROLL_THRESHOLD = 5
        private const val ANTI_SCROLL_COOLDOWN_MS = 10_000L

        private val FEED_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.instagram.android",
            "com.snapchat.android",
            "com.twitter.android",
            "com.zhiliaoapp.musically",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.tiktok.tiktok"
        )

        private val SOCIAL_PACKAGES = mapOf(
            "social_instagram" to "com.instagram.android",
            "social_snapchat" to "com.snapchat.android",
            "social_twitter" to "com.twitter.android",
            "social_tiktok" to "com.zhiliaoapp.musically"
        )
    }

    // High-performance Aho-Corasick Keyword Trie
    private class KeywordTrie {
        private class Node {
            val children = mutableMapOf<Char, Node>()
            var failure: Node? = null
            var isLeaf = false
            val outputs = mutableListOf<String>()
        }

        private val root = Node()

        fun build(keywords: List<String>) {
            root.children.clear()
            root.failure = null
            root.isLeaf = false
            root.outputs.clear()

            for (kw in keywords) {
                val word = kw.lowercase().trim()
                if (word.isEmpty()) continue
                var current = root
                for (char in word) {
                    current = current.children.getOrPut(char) { Node() }
                }
                current.isLeaf = true
                current.outputs.add(word)
            }

            val queue = java.util.ArrayDeque<Node>()
            for (child in root.children.values) {
                child.failure = root
                queue.add(child)
            }

            while (queue.isNotEmpty()) {
                val current = queue.poll()!!
                for ((char, child) in current.children) {
                    var f = current.failure
                    while (f != null && !f.children.containsKey(char)) {
                        f = f.failure
                    }
                    child.failure = if (f == null) root else f.children[char]
                    child.outputs.addAll(child.failure?.outputs ?: emptyList())
                    queue.add(child)
                }
            }
        }

        fun hasMatch(text: String): Boolean {
            val lowerText = text.lowercase()
            var current = root
            var i = 0
            while (i < lowerText.length) {
                while (current != root && !current.children.containsKey(lowerText[i])) {
                    current = current.failure ?: root
                }
                current = current.children[lowerText[i]] ?: root
                if (current.isLeaf || current.outputs.isNotEmpty()) {
                    val start = i - (current.outputs.firstOrNull()?.length ?: 1) + 1
                    val before = if (start > 0) lowerText[start - 1] else ' '
                    val end = i + 1
                    val after = if (end < lowerText.length) lowerText[end] else ' '
                    val isWordBoundaryBefore = !before.isLetterOrDigit()
                    val isWordBoundaryAfter = !after.isLetterOrDigit()
                    if (isWordBoundaryBefore && isWordBoundaryAfter) {
                        return true
                    }
                }
                i++
            }
            return false
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val keywordTrie = KeywordTrie()
    
    private val repo: AppRepository by lazy {
        (applicationContext as GuardianApp).repository
    }
    
    private var lastBlockTime = 0L
    private val scrollTimestamps = mutableMapOf<String, MutableList<Long>>()
    private var lastAntiScrollBlock = 0L
    private var lastAppSwitchBlock = 0L
    private var lastSettingsBlockNotification = 0L
    private var lastKeywordLoadTime = 0L
    private var lastUsageCheckTime = 0L
    private val appUsageCache = mutableMapOf<String, Long>()
    
    private var lastScreenTextScanTime = 0L
    private var lastScannedText = ""

    private var pendingPartialBlock = false
    private var pendingPartialBlockPkg = ""

    private var cachedShieldActive = false
    private var cachedShortsMode = false
    private var cachedReelsMode = false
    private var cachedUninstallProtection = false
    private var cachedSocialStates = mapOf<String, Boolean>()
    private var cachedYoutubeMode = "off"
    private var cachedFacebookMode = "off"
    private var cachedShortsBlockAction = "redirect"
    private var cachedBlockedApps = emptyList<com.agon.app.data.local.entity.BlocklistItemEntity>()
    private var cachedScheduleRules = emptyList<com.agon.app.data.local.entity.ScheduleRuleEntity>()
    private var cachedAppLimits = mapOf<String, com.agon.app.data.local.entity.AppLimitEntity>()
    private var cachedWhitelistApps = emptySet<String>()
    private var cachedWhitelistKeywords = emptySet<String>()
    private var cachedWhitelistWebsites = emptySet<String>()
    private var lastModeCacheTime = 0L
    private val MODE_CACHE_DURATION_MS = 5000L

    private fun refreshModeCacheAsync() {
        val now = System.currentTimeMillis()
        if (now - lastModeCacheTime <= MODE_CACHE_DURATION_MS) return
        lastModeCacheTime = now
        ioScope.launch {
            try {
                val settings = repo.getAppSettings()
                cachedShieldActive = settings.isShieldActive()
                cachedShortsMode = settings.isYoutubeShortsMode()
                cachedReelsMode = settings.isFacebookReelsMode()
                cachedUninstallProtection = settings.uninstallProtectionFlow.first()
                cachedYoutubeMode = settings.getYoutubeMode()
                cachedFacebookMode = settings.getFacebookMode()
                val socialMap = mutableMapOf<String, Boolean>()
                socialMap["social_instagram"] = settings.socialInstagramFlow.first()
                socialMap["social_snapchat"] = settings.socialSnapchatFlow.first()
                socialMap["social_twitter"] = settings.socialTwitterFlow.first()
                socialMap["social_tiktok"] = settings.socialTiktokFlow.first()
                cachedSocialStates = socialMap
                cachedBlockedApps = repo.getFullBlocklist("blocked_apps")
                cachedScheduleRules = repo.getAllScheduleRules().first()
                val limits = repo.getAllAppLimits().first()
                cachedAppLimits = limits.associateBy { it.packageName }
                cachedWhitelistApps = repo.getBlocklist("whitelist", "apps").map { it.value }.toSet()
                cachedWhitelistKeywords = repo.getBlocklist("whitelist", "keywords").map { it.value }.toSet()
                cachedWhitelistWebsites = repo.getBlocklist("whitelist", "websites").map { it.value }.toSet()
                cachedShortsBlockAction = settings.getShortsBlockAction()
            } catch (_: Exception) {}
        }
    }

    private var lastShieldCheckTime = 0L
    private val SHIELD_CACHE_DURATION_MS = 1000L

    private fun getCachedShieldActive(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastShieldCheckTime > SHIELD_CACHE_DURATION_MS) {
            lastShieldCheckTime = now
            ioScope.launch {
                try {
                    val settings = repo.getAppSettings()
                    cachedShieldActive = settings.isShieldActive()
                } catch (_: Exception) {}
            }
        }
        return cachedShieldActive
    }

    private fun getCachedShortsMode(): Boolean {
        refreshModeCacheAsync()
        return cachedShortsMode
    }

    private fun getCachedReelsMode(): Boolean {
        refreshModeCacheAsync()
        return cachedReelsMode
    }

    private val partialBlockRunnable = Runnable {
        pendingPartialBlock = false
        val root = rootInActiveWindow ?: return@Runnable
        try {
            if (shouldBlockPartial(pendingPartialBlockPkg, root)) {
                blockReels(pendingPartialBlockPkg)
            }
        } finally {
            root.recycle()
        }
    }

    private fun delayedBlockReels(pkg: String) {
        if (pendingPartialBlock) {
            pendingPartialBlockPkg = pkg
            return
        }
        pendingPartialBlock = true
        pendingPartialBlockPkg = pkg
        mainHandler.postDelayed({
            pendingPartialBlock = false
            blockReels(pkg)
        }, PARTIAL_BLOCK_DELAY_MS)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        reloadKeywords()
        Timber.d("Unified Accessibility Service Connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        scrollTimestamps.clear()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        if (packageName.startsWith("com.agon.")) return
        if (packageName == "com.android.systemui") return
        if (packageName == "com.android.launcher") return
        if (packageName == "com.android.launcher3") return
        if (packageName == "com.google.android.apps.nexuslauncher") return
        if (packageName == "com.google.android.apps.nexuslauncher2") return
        if (packageName == "android") return
        if (packageName == "com.android.settings") {
            if (!cachedUninstallProtection) return
        }

        val now = System.currentTimeMillis()
        checkReloadKeywords()

        // 1. App Blocking (custom blocklist, time limits, active schedules)
        if (isAppBlocked(packageName)) {
            triggerAppBlock(packageName, getAppLabel(packageName), "app_blocker")
            return
        }

        // All features below require shield to be active
        if (!getCachedShieldActive()) return

        // 2. Settings Lockout (Uninstall Protection + Guardian settings block)
        if (packageName == "com.android.settings") {
            handleWindowChange(packageName)
            val root = rootInActiveWindow ?: return
            try {
                if (isUninstallAttempt(root)) {
                    Timber.d("Settings lockout triggered!")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    mainHandler.post {
                        val intent = Intent(this, BlockActivity::class.java).apply {
                            putExtra("APP_NAME", getString(R.string.app_name))
                            putExtra("BLOCK_REASON", "uninstall_protection")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(intent)
                    }
                }
            } finally {
                root.recycle()
            }
            return
        }

        // 3. Anti-scroll detection
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            handleScrollEvent(packageName)
        }

        // 4. Layout checks for targeted social media
        val isYt = packageName == "com.google.android.youtube" ||
                packageName == "com.google.android.apps.youtube.music"
        val isFb = packageName == "com.facebook.katana" || packageName == "com.facebook.lite"
        val isIg = packageName == "com.instagram.android"
        
        if (isYt) {
            if (cachedYoutubeMode == "shorts") {
                when (event.eventType) {
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                        val root = rootInActiveWindow ?: return
                        try {
                            if (isShortsContent(root)) blockReels(packageName)
                        } finally { root.recycle() }
                    }
                    AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                        val source = event.source ?: return
                        try {
                            val desc = source.contentDescription?.toString() ?: ""
                            if (listOf("shorts","شورت").any { desc.contains(it, true) })
                                blockReels(packageName)
                        } finally { source.recycle() }
                    }
                }
            } else if (cachedYoutubeMode == "full") {
                triggerAppBlock(packageName, getAppLabel(packageName), "app_blocker")
            }
            return
        }

        if (isFb || isIg) {
            val isReelsEnabled = getCachedReelsMode()

            if (isReelsEnabled) {
                when (event.eventType) {
                    AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                        val source = event.source ?: return
                        try {
                            val desc = source.contentDescription?.toString() ?: ""
                            if (REEL_TABS.any { desc.contains(it, true) }) {
                                delayedBlockReels(packageName)
                            }
                        } finally {
                            source.recycle()
                        }
                    }
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                        val root = rootInActiveWindow ?: return
                        try {
                            if (shouldBlockPartial(packageName, root)) {
                                schedulePartialBlockCheck(packageName)
                            }
                        } finally {
                            root.recycle()
                        }
                    }
                }
            }
        }

        // 5. Browser URL & Keyword checks
        val isBrowser = packageName.contains("browser", true) || packageName.contains("chrome", true) || packageName.contains("firefox", true)
        if (isBrowser) {
            val root = rootInActiveWindow ?: return
            try {
                checkBrowserUrl(root, packageName)
            } finally {
                root.recycle()
            }
        }

        // 6. Keyword screen text scan
        val root = rootInActiveWindow ?: return
        try {
            scanScreenText(root, packageName)
        } finally {
            root.recycle()
        }
    }

    private fun isAppBlocked(pkg: String): Boolean {
        if (pkg == this.packageName) return false
        if (pkg.startsWith("com.agon.")) return false
        if (!getCachedShieldActive()) return false
        if (pkg in cachedWhitelistApps) return false

        // Check social app toggles
        for ((key, packageName) in SOCIAL_PACKAGES) {
            if (pkg == packageName) {
                val blocked = cachedSocialStates[key] ?: false
                if (blocked) return true
            }
        }

        // Check YouTube full block mode
        if (pkg == "com.google.android.youtube" || pkg == "com.google.android.apps.youtube.music") {
            if (cachedYoutubeMode == "full") return true
        }

        // Check Facebook full block mode
        if (pkg == "com.facebook.katana" || pkg == "com.facebook.lite") {
            if (cachedFacebookMode == "full") return true
        }

        // Check custom lists in Room DB
        if (cachedBlockedApps.any { it.value == pkg }) return true

        // Check time limits
        val usageMinutes = getUsageMinutesToday(pkg)
        val limit = cachedAppLimits[pkg]
        if (limit != null && usageMinutes >= limit.dailyMinutes.toDouble()) {
            return true
        }

        // Check schedules - only block social media and blocklist apps during schedule
        if (isScheduleActive()) {
            val isSocial = SOCIAL_PACKAGES.values.any { it == pkg }
            val isFb = pkg == "com.facebook.katana" || pkg == "com.facebook.lite"
            val isYt = pkg == "com.google.android.youtube" || pkg == "com.google.android.apps.youtube.music"
            val isCustomBlocked = cachedBlockedApps.any { it.value == pkg }
            if (isSocial || isFb || isYt || isCustomBlocked) return true
        }

        return false
    }

    private fun isScheduleActive(): Boolean {
        val rules = cachedScheduleRules
        if (rules.isEmpty()) return false
        
        val now = Calendar.getInstance()
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        val currentDay = when (dayOfWeek) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute

        for (rule in rules) {
            if (!rule.enabled) continue
            val days = rule.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (currentDay !in days) continue

            val startTotal = rule.startHour * 60 + rule.startMinute
            val endTotal = rule.endHour * 60 + rule.endMinute

            val isActive = if (startTotal <= endTotal) {
                currentTotalMinutes in startTotal..endTotal
            } else {
                currentTotalMinutes >= startTotal || currentTotalMinutes <= endTotal
            }
            if (isActive) return true
        }
        return false
    }

    private fun getUsageMinutesToday(packageName: String): Long {
        val now = System.currentTimeMillis()
        if (now - lastUsageCheckTime > 30_000L) {
            lastUsageCheckTime = now
            try {
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, now)
                appUsageCache.clear()
                for (s in stats) {
                    appUsageCache[s.packageName] = s.totalTimeInForeground / 60000L
                }
            } catch (e: Exception) {
                Timber.w(e, "Unified BlockService: failed to query usage stats")
            }
        }
        return appUsageCache[packageName] ?: 0L
    }

    private fun isUninstallAttempt(root: AccessibilityNodeInfo): Boolean {
        if (!cachedUninstallProtection) return false

        val flatText = extractAllText(root).lowercase()
        val hasApp = flatText.contains("guardsoul") || flatText.contains("حارس النفس") || flatText.contains("agon")
        if (!hasApp) return false

        val isTampering = flatText.contains("uninstall") || 
               flatText.contains("force stop") || 
               flatText.contains("deactivate") || 
               flatText.contains("clear data") || 
               flatText.contains("إلغاء التثبيت") || 
               flatText.contains("إيقاف إجباري") || 
               flatText.contains("إلغاء تفعيل") || 
               flatText.contains("مسح البيانات")

        if (isTampering) {
            showTamperAlert()
        }

        return isTampering
    }

    private fun showTamperAlert() {
        val notification = NotificationCompat.Builder(this, AppNotificationChannels.TAMPER_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("⚠️ محاولة تعديل!")
            .setContentText("تم اكتشاف محاولة تعديل على حماية التطبيق")
            .setStyle(NotificationCompat.BigTextStyle().bigText("تم اكتشاف محاولة تعديل على حماية التطبيق. إذا لم تكن أنت، يرجى التحقق من أمان جهازك."))
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(2003, notification)
        } catch (e: SecurityException) {
            Timber.w(e, "Notification permission not granted for tamper alert")
        }
    }

    private fun containsReelsLayout(node: AccessibilityNodeInfo): Boolean {
        val cd = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""

        val isReelsContent = REEL_CONTENT.any { cd == it || cd == "$it tab" || cd.endsWith(" $it") }
        val isShortsContent = YOUTUBE_SHORTS_KEYWORDS.any { cd == it || cd == "$it tab" || cd.endsWith(" $it") }
        val isReelsViewId = FACEBOOK_REELS_KEYWORDS.any { viewId.endsWith(it) || viewId.contains(".$it") }
        val isShortsViewId = YOUTUBE_SHORTS_KEYWORDS.any { viewId.endsWith(it) || viewId.contains(".$it") }
        val isPlayerClass = className.contains("shortsplayer") || className.contains("reelsplayer")

        if (isReelsContent || isShortsContent || isReelsViewId || isShortsViewId || isPlayerClass) return true

        for (i in 0 until minOf(node.childCount, 8)) {
            val child = node.getChild(i) ?: continue
            try {
                val ccd = child.contentDescription?.toString()?.lowercase() ?: ""
                val cViewId = child.viewIdResourceName?.lowercase() ?: ""
                val cClassName = child.className?.toString()?.lowercase() ?: ""

                val cIsReels = REEL_CONTENT.any { ccd == it || ccd == "$it tab" || ccd.endsWith(" $it") }
                val cIsShorts = YOUTUBE_SHORTS_KEYWORDS.any { ccd == it || ccd == "$it tab" || ccd.endsWith(" $it") }
                val cIsReelsViewId = FACEBOOK_REELS_KEYWORDS.any { cViewId.endsWith(it) || cViewId.contains(".$it") }
                val cIsShortsViewId = YOUTUBE_SHORTS_KEYWORDS.any { cViewId.endsWith(it) || cViewId.contains(".$it") }
                val cIsPlayerClass = cClassName.contains("shortsplayer") || cClassName.contains("reelsplayer")

                if (cIsReels || cIsShorts || cIsReelsViewId || cIsShortsViewId || cIsPlayerClass) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun schedulePartialBlockCheck(pkg: String) {
        if (pendingPartialBlock) {
            pendingPartialBlockPkg = pkg
            return
        }
        pendingPartialBlock = true
        pendingPartialBlockPkg = pkg
        mainHandler.postDelayed(partialBlockRunnable, PARTIAL_BLOCK_DELAY_MS)
    }

    private fun shouldBlockPartial(pkg: String, root: AccessibilityNodeInfo): Boolean {
        if (pkg == this.packageName) return false
        if (pkg.startsWith("com.agon.")) return false

        val isYt = pkg == "com.google.android.youtube" || pkg == "com.google.android.apps.youtube.music"
        val isFb = pkg == "com.facebook.katana" || pkg == "com.facebook.lite"
        val isIg = pkg == "com.instagram.android"

        if (isYt) {
            if (isYoutubeShortsTabSelected(root)) return true
            if (isYoutubeShortsPlayer(root)) return true
        }

        if (isFb || isIg) {
            if (isFacebookReelsTabSelected(root)) return true
        }

        return false
    }

    private fun isYoutubeShortsPlayer(root: AccessibilityNodeInfo): Boolean {
        return hasNodeInTree(root) { node ->
            val viewId = node.viewIdResourceName ?: ""
            val className = node.className?.toString() ?: ""
            viewId.contains("shorts_player", true) ||
            viewId.contains("reels_player", true) ||
            className.contains("ShortsPlayer", true) ||
            className.contains("YouTubeShortsPlayer", true)
        }
    }

    private fun hasNodeInTree(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (predicate(node)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (hasNodeInTree(child, predicate)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun isTabSelected(node: AccessibilityNodeInfo, tabName: String): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        
        val nameMatch = desc == tabName || desc == "$tabName tab" || 
                        desc.endsWith(" $tabName") || desc.startsWith("$tabName ") ||
                        viewId.endsWith(".$tabName") || viewId.contains(".$tabName.")
        if (!nameMatch) return false
        
        return node.isSelected || node.isChecked ||
                desc.contains("selected") || desc.contains("\u0645\u062D\u062F\u062F")
    }

    private fun isYoutubeShortsTabSelected(root: AccessibilityNodeInfo): Boolean {
        return hasNodeInTree(root) { node ->
            isTabSelected(node, "shorts")
        }
    }

    private fun isFacebookReelsTabSelected(root: AccessibilityNodeInfo): Boolean {
        return hasNodeInTree(root) { node ->
            isTabSelected(node, "reels")
        }
    }

    private fun isFacebookFullscreenVideo(root: AccessibilityNodeInfo): Boolean {
        return hasNodeInTree(root) { node ->
            val desc = node.contentDescription?.toString() ?: ""
            val viewId = node.viewIdResourceName ?: ""
            val className = node.className?.toString() ?: ""
            
            FULLSCREEN_KEYWORDS.any { desc.contains(it, true) } ||
            viewId.contains("fullscreen", true) ||
            viewId.contains("video_player", true) ||
            className.contains("VideoView", true) ||
            className.contains("ExoPlayer", true)
        }
    }

    private fun checkBrowserUrl(root: AccessibilityNodeInfo, packageName: String) {
        if (packageName == this.packageName) return
        if (packageName.startsWith("com.agon.")) return
        val url = findBrowserUrlString(root) ?: return
        if (cachedWhitelistWebsites.any { url.contains(it, true) }) return
        ioScope.launch {
            val isBlocked = try {
                val blockedSites = repo.getBlocklist("blacklist", "websites")
                if (blockedSites.any { url.contains(it.value, true) }) true
                else if (keywordTrie.hasMatch(url)) true
                else false
            } catch (_: Exception) { false }

            if (isBlocked) {
                mainHandler.post {
                    triggerAppBlock(packageName, getAppLabel(packageName), "website_block")
                }
            }
        }
    }

    private fun findBrowserUrlString(node: AccessibilityNodeInfo): String? {
        val viewId = node.viewIdResourceName ?: ""
        val text = node.text?.toString() ?: ""
        
        val knownIds = setOf(
            "com.android.chrome:id/url_bar",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "org.mozilla.firefox:id/url_bar_title"
        )
        
        if (viewId in knownIds && text.isNotBlank()) {
            return text
        }
        
        val className = node.className?.toString() ?: ""
        if ((className.contains("EditText") || className.contains("TextView")) && 
            (text.contains("http") || text.contains(".") && text.length > 3) &&
            !text.contains(" ")
        ) {
            return text
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val url = findBrowserUrlString(child)
                if (url != null) return url
            } finally {
                child.recycle()
            }
        }
        return null
    }

    private fun scanScreenText(root: AccessibilityNodeInfo, packageName: String) {
        if (packageName == this.packageName) return
        if (packageName.startsWith("com.agon.")) return

        val now = System.currentTimeMillis()
        if (now - lastScreenTextScanTime < 500L) return
        lastScreenTextScanTime = now

        val fullText = extractAllText(root)
        if (fullText == lastScannedText) return
        lastScannedText = fullText

        if (cachedWhitelistKeywords.any { fullText.contains(it, true) }) return

        ioScope.launch {
            if (keywordTrie.hasMatch(fullText)) {
                mainHandler.post {
                    triggerAppBlock(packageName, getAppLabel(packageName), "keyword_block")
                }
            }
        }
    }

    private fun extractAllText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if (text.isNotBlank()) sb.append(text).append(" ")
        if (desc.isNotBlank()) sb.append(desc).append(" ")
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                sb.append(extractAllText(child))
            } finally {
                child.recycle()
            }
        }
        return sb.toString()
    }

    private fun reloadKeywords() {
        ioScope.launch {
            try {
                val keywords = repo.getBlocklist("blacklist", "keywords")
                    .map { it.value }
                    .filter { it !in cachedWhitelistKeywords }
                keywordTrie.build(keywords)
                Timber.d("Unified BlockService: loaded ${keywords.size} keywords")
            } catch (e: Exception) {
                Timber.e(e, "Unified BlockService: failed to load keywords")
            }
        }
    }

    private fun checkReloadKeywords() {
        val now = System.currentTimeMillis()
        if (now - lastKeywordLoadTime > 15_000L) {
            lastKeywordLoadTime = now
            reloadKeywords()
        }
    }

    private fun blockReels(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < BLOCK_COOLDOWN_MS) return
        lastBlockTime = now

        ioScope.launch {
            try {
                repo.recordBlock(packageName, getAppLabel(packageName), "shorts_reels_block")
            } catch (e: Exception) {
                Timber.e(e, "Unified BlockService: failed to record reels block")
            }
        }

        val blockAction = cachedShortsBlockAction

        mainHandler.post {
            when (blockAction) {
                "exit" -> {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(homeIntent)
                    showBlockNotification()
                }
                "block_screen" -> {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    val intent = Intent(this, BlockActivity::class.java).apply {
                        putExtra("APP_NAME", getAppLabel(packageName))
                        putExtra("BLOCK_REASON", "shorts_reels_block")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                }
                else -> {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    showBlockNotification()
                }
            }
        }
    }

    private fun triggerAppBlock(packageName: String, appLabel: String, reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < BLOCK_COOLDOWN_MS) return
        lastBlockTime = now

        Timber.d("Unified BlockService: blocking $packageName due to $reason")

        ioScope.launch {
            try {
                repo.recordBlock(packageName, appLabel, reason)
            } catch (e: Exception) {
                Timber.e(e, "Unified BlockService: failed to record block")
            }
        }

        showAppBlockNotification(appLabel, reason)

        mainHandler.post {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(homeIntent)

            val intent = Intent(this, BlockActivity::class.java).apply {
                putExtra("APP_NAME", appLabel)
                putExtra("BLOCK_REASON", reason)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        }
    }

    private fun showBlockNotification() {
        val notification = NotificationCompat.Builder(this, AppNotificationChannels.FACEBOOK_VIDEO)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle(getString(R.string.notification_video_blocked_title))
            .setContentText(getString(R.string.notification_video_blocked_text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(VIDEO_BLOCK_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Timber.w(e, "Notification permission not granted")
        }
    }

    private fun showAppBlockNotification(appLabel: String, reason: String) {
        val title = getString(R.string.notification_app_blocked_title)
        val text = getString(R.string.notification_app_blocked_text, appLabel)
        val notification = NotificationCompat.Builder(this, AppNotificationChannels.APP_BLOCKER)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(APP_BLOCK_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Timber.w(e, "Notification permission not granted")
        }
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    private fun handleScrollEvent(packageName: String) {
        if (packageName !in FEED_PACKAGES) return

        val now = System.currentTimeMillis()

        ioScope.launch {
            val shieldActive = try {
                repo.getAppSettings().isShieldActive()
            } catch (_: Exception) { false }
            if (!shieldActive) return@launch
        }

        val timestamps = scrollTimestamps.getOrPut(packageName) { mutableListOf() }
        timestamps.add(now)

        timestamps.removeAll { now - it > ANTI_SCROLL_WINDOW_MS }

        if (timestamps.size >= ANTI_SCROLL_THRESHOLD) {
            if (now - lastAntiScrollBlock > ANTI_SCROLL_COOLDOWN_MS) {
                lastAntiScrollBlock = now
                Timber.d("Anti-scroll triggered for $packageName (${timestamps.size} scrolls in ${ANTI_SCROLL_WINDOW_MS}ms)")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            timestamps.clear()
        }
    }

    private fun handleWindowChange(packageName: String) {
        if (packageName != "com.android.settings") return

        ioScope.launch {
            val shieldActive = try {
                repo.getAppSettings().isShieldActive()
            } catch (_: Exception) { false }
            if (!shieldActive) return@launch

            android.os.Handler(mainLooper).post {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }

            val now = System.currentTimeMillis()
            if (now - lastSettingsBlockNotification > 10_000) {
                lastSettingsBlockNotification = now
                showSettingsBlockedNotification()
            }
        }
    }

    private fun showSettingsBlockedNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val notification = NotificationCompat.Builder(this, AppNotificationChannels.TAMPER_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.tamper_settings_blocked_title))
            .setContentText(getString(R.string.tamper_settings_blocked_text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(9001, notification)
    }

    private fun isShortsContent(root: AccessibilityNodeInfo): Boolean {
        return hasNodeInTree(root) { node ->
            val cd = node.contentDescription?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName ?: ""
            val className = node.className?.toString() ?: ""

            val hasShortsDescription = listOf("shorts", "شورت").any { cd.contains(it) }
            val hasReelPlayerId = viewId == "com.google.android.youtube:id/reel_player"
            val hasShortsPlayerClass = className.contains("ShortsPlayer", true) ||
                    className.contains("ReelPlayer", true) ||
                    (className == "android.widget.FrameLayout" &&
                            (cd.contains("shorts") || viewId.contains("reel")))

            hasShortsDescription || hasReelPlayerId || hasShortsPlayerClass
        }
    }

    override fun onInterrupt() {
        Timber.d("Unified Accessibility Service Interrupted")
    }
}
