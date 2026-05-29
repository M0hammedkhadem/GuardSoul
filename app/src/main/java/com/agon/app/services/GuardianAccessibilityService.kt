package com.agon.app.services

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.GuardianApp
import com.agon.app.R
import com.agon.app.data.local.entity.BlockEventEntity
import com.agon.app.ui.screens.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar

class GuardianAccessibilityService : AccessibilityService() {

    companion object {
        private const val SCREEN_TIME_FLUSH_INTERVAL = 30_000L
        private const val BLOCK_COOLDOWN_MS = 1500L
        private const val KEYWORD_SCAN_INTERVAL_MS = 800L
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val keywordTrie = KeywordTrie()

    private val repo by lazy {
        (applicationContext as GuardianApp).repository
    }

    private var lastBlockTime = 0L
    private var lastKeywordLoadTime = 0L
    private var lastScanTime = 0L
    private var lastScreenTimeFlush = 0L
    private var lastCachedSettingsRefresh = 0L

    private var cachedShieldActive = false
    private var cachedBlockAction = "block_screen"
    private var cachedKeywords = emptyList<String>()
    private var cachedWhitelistKeywords = emptySet<String>()
    private var cachedWhitelistApps = emptySet<String>()

    private val screenTimeMap = mutableMapOf<String, Long>()
    private val foregroundPkg = mutableListOf<String>()

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
                for (char in word) current = current.children.getOrPut(char) { Node() }
                current.isLeaf = true
                current.outputs.add(word)
            }
            val queue = java.util.ArrayDeque<Node>()
            for (child in root.children.values) { child.failure = root; queue.add(child) }
            while (queue.isNotEmpty()) {
                val current = queue.poll()
                for ((char, child) in current.children) {
                    var f = current.failure
                    while (f != null && !f.children.containsKey(char)) f = f.failure
                    child.failure = if (f == null) root else f.children[char]
                    child.outputs.addAll(child.failure?.outputs ?: emptyList())
                    queue.add(child)
                }
            }
        }

        fun hasMatch(text: String): Boolean {
            val lower = text.lowercase()
            var current = root
            var i = 0
            while (i < lower.length) {
                while (current != root && !current.children.containsKey(lower[i])) current = current.failure ?: root
                current = current.children[lower[i]] ?: root
                if (current.isLeaf || current.outputs.isNotEmpty()) {
                    val start = i - (current.outputs.firstOrNull()?.length ?: 1) + 1
                    val before = if (start > 0) lower[start - 1] else ' '
                    val after = if (i + 1 < lower.length) lower[i + 1] else ' '
                    if ((!before.isLetterOrDigit()) && (!after.isLetterOrDigit())) return true
                }
                i++
            }
            return false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshCache()
        loadKeywords()
        Timber.d("GuardianAccessibilityService connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        screenTimeMap.clear()
        Timber.d("GuardianAccessibilityService destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == this.packageName) return
        if (pkg.startsWith("com.agon.")) return
        if (pkg == "com.android.systemui") return

        val now = System.currentTimeMillis()
        refreshCacheIfStale()
        if (!cachedShieldActive) return

        trackScreenTime(pkg, now)

        if (now - lastScreenTimeFlush > SCREEN_TIME_FLUSH_INTERVAL) {
            lastScreenTimeFlush = now
            ioScope.launch { flushScreenTime() }
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val root = rootInActiveWindow ?: return
            try {
                val fullText = extractAllText(root)
                if (fullText.isBlank()) return
                if (cachedWhitelistKeywords.any { fullText.contains(it, true) }) return
                if (keywordTrie.hasMatch(fullText)) {
                    handleProhibitedContent(pkg, fullText)
                }
            } finally {
                root.recycle()
            }
        }
    }

    override fun onInterrupt() {
        Timber.d("GuardianAccessibilityService interrupted")
    }

    private fun handleProhibitedContent(pkg: String, text: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < BLOCK_COOLDOWN_MS) return
        lastBlockTime = now

        val label = getAppLabel(pkg)
        ioScope.launch {
            try {
                repo.recordBlock(pkg, label, "keyword_block")
            } catch (_: Exception) {}
        }

        when (cachedBlockAction) {
            "exit" -> {
                val home = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(home)
            }
            "pixelate" -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            else -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                val intent = Intent(this, BlockActivity::class.java).apply {
                    putExtra("APP_NAME", label)
                    putExtra("BLOCK_REASON", "keyword_block")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
        }
    }

    private fun trackScreenTime(pkg: String, now: Long) {
        if (foregroundPkg.lastOrNull() != pkg) {
            foregroundPkg.add(pkg)
            if (foregroundPkg.size > 2) foregroundPkg.removeAt(0)
        }
    }

    private suspend fun flushScreenTime() {
        try {
            val events = repo.blockEventDao.blocksSince(0L)
            val todayStart = getTodayStart()
            val todayEvents = events.filter { it.timestamp >= todayStart }
            Timber.d("Screen time: ${todayEvents.size} events today, current packages: $foregroundPkg")
        } catch (_: Exception) {}
    }

    private fun extractAllText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if (text.isNotBlank()) sb.append(text).append(" ")
        if (desc.isNotBlank()) sb.append(desc).append(" ")
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { sb.append(extractAllText(child)) } finally { child.recycle() }
        }
        return sb.toString()
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: PackageManager.NameNotFoundException) { pkg }
    }

    private fun getTodayStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun refreshCacheIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastCachedSettingsRefresh > 5000L) refreshCache()
    }

    private fun refreshCache() {
        lastCachedSettingsRefresh = System.currentTimeMillis()
        ioScope.launch {
            try {
                val settings = repo.getAppSettings()
                cachedShieldActive = settings.isShieldActive()
                cachedWhitelistKeywords = repo.getBlocklist("whitelist", "keywords").map { it.value }.toSet()
                cachedWhitelistApps = repo.getBlocklist("whitelist", "apps").map { it.value }.toSet()
            } catch (_: Exception) {}
        }
    }

    private fun loadKeywords() {
        ioScope.launch {
            try {
                cachedKeywords = repo.getBlocklist("blacklist", "keywords")
                    .map { it.value }
                    .filter { it !in cachedWhitelistKeywords }
                keywordTrie.build(cachedKeywords)
                Timber.d("Loaded ${cachedKeywords.size} keywords")
            } catch (_: Exception) {}
        }
    }
}
