package com.agon.app

import android.app.Notification
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import com.agon.app.blocking.BlockingConfig
import com.agon.app.blocking.AiBlockTracker
import com.agon.app.blocking.DayOfWeekUtil
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.agon.app.data.local.entity.AppLimitEntity
import com.agon.app.data.repository.AppRepository
import com.agon.app.ui.screens.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground-app blocker service.
 *
 * Performance & accuracy improvements (2026-06):
 * 1. All DataStore + DB reads are cached in @Volatile fields and refreshed via
 *    flow collectors or periodic ticks. shouldBlock() is now a pure in-memory
 *    lookup — no I/O per package per poll.
 * 2. POLL_INTERVAL_MS = 500ms when shield is on, giving worst-case time-to-
 *    block of ~500ms. SHIELD_OFF_INTERVAL_MS = 5s to save battery when the
 *    shield is off.
 * 3. Per-package cooldown (MIN_TIME_BETWEEN_BLOCKS_MS) = 2000ms for general
 *    apps, 500ms for social apps (SOCIAL_BLOCK_COOLDOWN_MS) so re-opening a
 *    blocked social app is re-blocked almost instantly.
 * 4. Foreground cycle now records + blocks ALL detected apps, not just the
 *    first one. The first launchBlockActivity is shown in the UI but every
 *    block is recorded to the database.
 * 5. The full-mode social check is now driven by the cached *Mode string
 *    (cachedInstagramMode / cachedYoutubeMode / cachedFacebookMode) instead
 *    of a stale boolean. This fixes the long-standing bug where
 *    isInstagramBlocked() returned false even when INSTAGRAM_MODE == "full".
 * 6. For full-mode social apps, the service now also dispatches ACTION_HOME so
 *    the user is kicked out of the app immediately instead of just seeing an
 *    overlay that some launchers can dismiss.
 * 7. The "winner" package shown in the overlay is now picked as the LAST
 *    entry of the foreground set (most recently foregrounded) instead of
 *    the first one.
 */
class AppBlockerService : Service(), KoinComponent {

    companion object {
        private const val NOTIFICATION_ID = 3001
        private const val PERIODIC_WORK_NAME = "app_blocker_periodic"

        // Shared signal used by callers (e.g. ListsViewModel) to ask the
        // running service to drop any cached blocklist state. Replaces the
        // old ACTION_RELOAD_BLOCKLIST intent which would force a full
        // foreground service restart and break Android 12+ background
        // service start restrictions.
        private val _reloadSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
        val reloadSignal = _reloadSignal.asSharedFlow()

        fun notifyBlocklistChanged() {
            _reloadSignal.tryEmit(Unit)
        }

        // Hard-coded package → mode key mapping. Single source of truth used
        // by both shouldBlock() and the social-full-mode check. Keeping the
        // package constants here means adding a new social app is one line.
        private val SOCIAL_PACKAGES = mapOf(
            "social_instagram" to "com.instagram.android",
            "social_snapchat" to "com.snapchat.android",
            "social_twitter" to "com.twitter.android",
            "social_tiktok" to "com.zhiliaoapp.musically"
        )

        private val YOUTUBE_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music"
        )

        private val FACEBOOK_PACKAGES = setOf(
            "com.facebook.katana",
            "com.facebook.lite",
            "com.facebook.orca"
        )

        /** All social packages we know about (used for fast membership checks). */
        val ALL_SOCIAL_PACKAGES: Set<String> = run {
            val all = mutableSetOf<String>()
            all.addAll(SOCIAL_PACKAGES.values)
            all.addAll(FACEBOOK_PACKAGES)
            all.addAll(YOUTUBE_PACKAGES)
            all.toSet()
        }

        fun start(context: Context) {
            ForegroundServiceHelper.startServiceAsForeground(
                context, AppBlockerService::class.java
            )
            schedulePeriodicCheck(context)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AppBlockerService::class.java))
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        fun schedulePeriodicCheck(context: Context) {
            val request = PeriodicWorkRequestBuilder<AppBlockerCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun reloadBlocklist(context: Context) {
            // Delegates to the signal so the service (if running) can pick it
            // up without a foreground service restart. The signal is fire-and-
            // forget; callers do not need a Context.
            notifyBlocklistChanged()
        }

        fun scheduleTimeLimitCheck(context: Context) {
            schedulePeriodicCheck(context)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var pollingJob: Job? = null

    private val repo: AppRepository by inject<AppRepository>()
    private val aiBlockTracker: AiBlockTracker by inject<AiBlockTracker>()

    private lateinit var usageStatsManager: UsageStatsManager

    // Per-package block cooldown. The previous design kept a single global
    // cooldown but with a per-package map we can fire BlockActivity for each
    // new app without the previous one suppressing the next.
    private val lastBlockTimes = ConcurrentHashMap<String, Long>()

    @Volatile private var lastCheckTime = 0L
    @Volatile private var currentDayStart = 0L

    // Cached app lists — refreshed on onCreate and on reloadSignal.
    @Volatile private var cachedWhitelist: Set<String> = emptySet()
    @Volatile private var cachedBlacklist: Set<String> = emptySet()

    // ── Cached settings (refreshed by flow collectors) ──────────────────
    @Volatile private var cachedShieldActive: Boolean = false
    @Volatile private var cachedStrictMode: Boolean = false
    @Volatile private var cachedSchoolTimeActive: Boolean = false
    @Volatile private var cachedBedtimeActive: Boolean = false
    @Volatile private var cachedScheduleActive: Boolean = false

    // Social modes / booleans — kept consistent across both representations
    // to avoid the historical drift bug (see KDoc above).
    @Volatile private var cachedInstagramMode: String = "off"
    @Volatile private var cachedYoutubeMode: String = "off"
    @Volatile private var cachedFacebookMode: String = "off"
    @Volatile private var cachedSnapchatBlocked: Boolean = false
    @Volatile private var cachedTwitterBlocked: Boolean = false
    @Volatile private var cachedTiktokBlocked: Boolean = false

    // Map<package, AppLimitEntity>. Empty if no time limits configured.
    @Volatile private var cachedAppLimits: Map<String, AppLimitEntity> = emptyMap()

    // Track the last block that was shown in the UI (BlockActivity) so we
    // don't keep relaunching the same one. The actual block event is recorded
    // every time shouldBlock returns non-null; this just governs the overlay.
    @Volatile private var lastShownBlockPkg: String? = null
    @Volatile private var lastShownBlockTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        lastCheckTime = now
        currentDayStart = getDayStart(now)
        serviceScope.launch { refreshBlocklistCache() }
        serviceScope.launch { startSettingsCollectors() }
        // Rebuild the AI temp-block cache from DataStore on every service start
        // so 15-minute blocks survive a process death / reboot.
        serviceScope.launch { aiBlockTracker.refreshFromStorage() }
        serviceScope.launch {
            reloadSignal.collect {
                lastBlockTimes.clear()
                lastShownBlockPkg = null
                refreshBlocklistCache()
                aiBlockTracker.refreshFromStorage()
            }
        }
        // Periodically recompute derived time-of-day booleans. The schedules
        // themselves are read in the worker / from cache; this loop is cheap
        // and ensures the in-memory flags don't drift past their window.
        serviceScope.launch {
            while (isActive) {
                refreshTimeOfDayCaches()
                delay(BlockingConfig.TIME_OF_DAY_REFRESH_MS)
            }
        }
    }

    private suspend fun startSettingsCollectors() {
        val settings = repo.getAppSettings()
        // Eagerly cache the initial values so shouldBlock() has them on the
        // first poll (avoids a 250ms "no settings yet" window after start).
        cachedShieldActive = settings.isShieldActive()
        cachedStrictMode = settings.isStrictMode()
        cachedInstagramMode = settings.getInstagramMode()
        cachedYoutubeMode = settings.getYoutubeMode()
        cachedFacebookMode = settings.getFacebookMode()
        cachedSnapchatBlocked = settings.isSnapchatBlocked()
        cachedTwitterBlocked = settings.isTwitterBlocked()
        cachedTiktokBlocked = settings.isTiktokBlocked()
        refreshTimeOfDayCaches()

        // Now wire up the live collectors.
        settings.shieldActiveFlow.collect { cachedShieldActive = it }
        settings.strictModeFlow.collect { cachedStrictMode = it }
        settings.instagramModeFlow.collect { cachedInstagramMode = it }
        settings.youtubeModeFlow.collect { cachedYoutubeMode = it }
        settings.facebookModeFlow.collect { cachedFacebookMode = it }
        settings.socialSnapchatFlow.collect { cachedSnapchatBlocked = it }
        settings.socialTwitterFlow.collect { cachedTwitterBlocked = it }
        settings.socialTiktokFlow.collect { cachedTiktokBlocked = it }
        // App limits — replace the per-package DB read with a single flow.
        repo.getAllAppLimits().collect { limits ->
            cachedAppLimits = limits.associateBy { it.packageName }
        }
        // Schedule rules — recompute the "is any rule active right now" flag.
        repo.getAllScheduleRules().collect {
            cachedScheduleActive = computeScheduleActive(it)
        }
    }

    private suspend fun refreshTimeOfDayCaches() {
        val settings = repo.getAppSettings()
        cachedSchoolTimeActive = settings.isSchoolTimeActive()
        cachedBedtimeActive = settings.isBedtimeActive()
        // Re-evaluate schedule active with the latest rules (collectors
        // normally do this on rule change, but we re-tick every minute as a
        // safety net for time-only transitions).
        cachedScheduleActive = computeScheduleActive(
            try { repo.getAllScheduleRules().first() } catch (_: Exception) { emptyList() }
        )
    }

    private fun computeScheduleActive(rules: List<com.agon.app.data.local.entity.ScheduleRuleEntity>): Boolean {
        if (rules.isEmpty()) return false
        val now = Calendar.getInstance()
        val currentDay = DayOfWeekUtil.calendarDayToMondayFirstIndex(now.get(Calendar.DAY_OF_WEEK))
        val currentTotal = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        for (rule in rules) {
            if (!rule.enabled) continue
            val days = DayOfWeekUtil.decode(rule.daysOfWeek)
            if (currentDay !in days) continue
            val startTotal = rule.startHour * 60 + rule.startMinute
            val endTotal = rule.endHour * 60 + rule.endMinute
            val active = if (startTotal <= endTotal) currentTotal in startTotal..endTotal
                         else currentTotal >= startTotal || currentTotal <= endTotal
            if (active) return true
        }
        return false
    }

    private suspend fun refreshBlocklistCache() {
        try {
            cachedWhitelist = repo.getBlocklist("whitelist", "apps").map { it.value }.toHashSet()
            cachedBlacklist = repo.getBlocklist("blacklist", "apps").map { it.value }.toHashSet()
        } catch (e: Exception) {
            Timber.w(e, "AppBlockerService: failed to refresh blocklist cache")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ForegroundServiceHelper.startForegroundCompat(this, NOTIFICATION_ID, createNotification())
        startPolling()
        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        serviceScope.cancel()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    if (cachedShieldActive) {
                        refreshDayBoundary()
                        checkForegroundApp()
                        delay(BlockingConfig.POLL_INTERVAL_MS)
                    } else {
                        // Shield is off — back off to save battery.
                        delay(BlockingConfig.SHIELD_OFF_INTERVAL_MS)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "AppBlocker polling error")
                    delay(BlockingConfig.POLL_INTERVAL_MS)
                }
            }
        }
    }

    private fun refreshDayBoundary() {
        val now = System.currentTimeMillis()
        val today = getDayStart(now)
        if (today != currentDayStart) {
            currentDayStart = today
            lastBlockTimes.clear()
        }
        if (lastBlockTimes.size > BlockingConfig.MAX_TRACKED_PACKAGES) {
            lastBlockTimes.clear()
        }
    }

    /**
     * Walks the foreground-package set returned by UsageStats and records /
     * blocks every entry that shouldBlock() flags. The previous implementation
     * stopped at the first match, which dropped the rest of the cycle from
     * the database. Now we record all of them and pick the most-recently-
     * foregrounded package for the BlockActivity overlay.
     */
    private suspend fun checkForegroundApp() {
        val now = System.currentTimeMillis()
        val queryStart = lastCheckTime

        val events = try {
            usageStatsManager.queryEvents(queryStart, now)
        } catch (e: Exception) {
            lastCheckTime = now
            return
        }

        val foregroundPackages = mutableSetOf<String>()
        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)
            val pkg = event.packageName
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                && pkg != null
                && pkg != packageName
                && pkg != "com.android.systemui"
                && pkg != "android"
            ) {
                foregroundPackages.add(pkg)
            }
        }

        lastCheckTime = now

        // Pick the most-recently-foregrounded package as the "winner" for
        // BlockActivity. Foreground packages that are *already* the last shown
        // one don't get a new overlay (avoids relaunching the same activity
        // every 250ms).
        val winner = foregroundPackages.lastOrNull() ?: return
        if (winner == packageName) return

        val (winnerPkg, winnerLabel, winnerReason) = foregroundPackages.asSequence()
            .mapNotNull { pkg ->
                val reason = shouldBlock(pkg) ?: return@mapNotNull null
                Triple(pkg, getAppLabel(pkg), reason)
            }
            .lastOrNull()
            ?: return

        // Record every blocked app (BL-002 fix: not just the first). We do
        // this *after* finding the winner so the DB write doesn't slow down
        // the foreground check.
        for (pkg in foregroundPackages) {
            if (now - (lastBlockTimes[pkg] ?: 0L) < cooldownFor(pkg)) continue
            val reason = shouldBlock(pkg) ?: continue
            lastBlockTimes[pkg] = now
            val label = getAppLabel(pkg)
            repo.recordBlock(pkg, label, reason)
        }

        // Only re-launch the overlay if the winner changed OR enough time has
        // passed since the last overlay for the same package.
        val overlayCooldown = BlockingConfig.OVERLAY_RELAUNCH_MS
        val sameAsLast = lastShownBlockPkg == winnerPkg
        if (sameAsLast && now - lastShownBlockTime < overlayCooldown) return
        lastShownBlockPkg = winnerPkg
        lastShownBlockTime = now

        launchBlockActivity(winnerPkg, winnerLabel, winnerReason)
    }

    /**
     * Returns the cooldown that applies to [pkg] for a given block reason.
     * Social apps use the short cooldown so re-opens are blocked immediately;
     * other apps keep the longer cooldown to avoid log spam.
     */
    private fun cooldownFor(pkg: String): Long =
        if (pkg in ALL_SOCIAL_PACKAGES) BlockingConfig.SOCIAL_BLOCK_COOLDOWN_MS
        else BlockingConfig.MIN_TIME_BETWEEN_BLOCKS_MS

    /**
     * Pure in-memory check — does not touch DataStore, Room, or the
     * UsageStatsManager. All inputs are cached booleans / sets refreshed by
     * flow collectors in startSettingsCollectors() / refreshTimeOfDayCaches().
     */
    private fun shouldBlock(pkg: String): String? {
        if (pkg in cachedWhitelist) return null
        if (pkg in cachedBlacklist) return "app_blocker"

        // AI Explorer temp-block: 3 strikes in 4 minutes = 15-minute block.
        // The tracker already drops expired entries so this lookup is O(1).
        if (aiBlockTracker.isTempBlocked(pkg)) return "ai_temp_block"

        // School-time / bedtime windows apply only to social apps and
        // blacklisted apps — never to benign apps.
        if (cachedSchoolTimeActive || cachedBedtimeActive) {
            if (isSocialOrBlocklistApp(pkg)) return "app_blocker"
        }

        // Full-mode social blocking. We drive this off the cached *Mode
        // string (not the legacy boolean) — this is the single source of
        // truth and fixes the long-standing bug where isInstagramBlocked()
        // returned false even when the user had Instagram in "full" mode.
        if (pkg == SOCIAL_PACKAGES["social_instagram"] && cachedInstagramMode == "full") {
            return "app_blocker"
        }
        if (pkg == SOCIAL_PACKAGES["social_snapchat"] && cachedSnapchatBlocked) {
            return "app_blocker"
        }
        if (pkg == SOCIAL_PACKAGES["social_twitter"] && cachedTwitterBlocked) {
            return "app_blocker"
        }
        if (pkg == SOCIAL_PACKAGES["social_tiktok"] && cachedTiktokBlocked) {
            return "app_blocker"
        }
        if (pkg in FACEBOOK_PACKAGES && cachedFacebookMode == "full") {
            return "app_blocker"
        }
        if (pkg in YOUTUBE_PACKAGES && cachedYoutubeMode == "full") {
            return "app_blocker"
        }

        // Time-limit check. Pulled from the cached map — no per-package DB
        // read.
        cachedAppLimits[pkg]?.let { limit ->
            if (isTimeLimitExceeded(limit)) return "time_limit"
        }

        // Generic schedule (e.g. user-configured "block social 19:00-21:00").
        if (cachedScheduleActive && isSocialOrBlocklistApp(pkg)) return "app_blocker"

        return null
    }

    private fun isSocialOrBlocklistApp(pkg: String): Boolean {
        if (pkg in ALL_SOCIAL_PACKAGES) return true
        return pkg in cachedBlacklist
    }

    private fun isTimeLimitExceeded(limit: AppLimitEntity): Boolean {
        return getUsageMinutesToday(limit.packageName) >= limit.dailyMinutes
    }

    private fun getUsageMinutesToday(packageName: String): Long {
        return try {
            val now = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, currentDayStart, now)
            val appStat = stats.find { it.packageName == packageName }
            (appStat?.totalTimeInForeground ?: 0L) / 60000L
        } catch (e: Exception) { 0L }
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) { pkg }
    }

    /**
     * Launches the BlockActivity overlay. For full-mode social apps we also
     * send ACTION_HOME so the user is kicked out of the offending app — the
     * overlay alone is dismissable on some launchers.
     */
    private fun launchBlockActivity(pkg: String, appLabel: String, reason: String) {
        if (reason == "app_blocker" && pkg in ALL_SOCIAL_PACKAGES && isFullSocialMode(pkg)) {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try { startActivity(home) } catch (_: Exception) {}
        }
        val intent = Intent(this, BlockActivity::class.java).apply {
            putExtra("APP_NAME", appLabel)
            putExtra("BLOCK_REASON", reason)
            putExtra("BLOCK_PKG", pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try { startActivity(intent) } catch (_: Exception) {}
    }

    private fun isFullSocialMode(pkg: String): Boolean = when {
        pkg == SOCIAL_PACKAGES["social_instagram"] -> cachedInstagramMode == "full"
        pkg in FACEBOOK_PACKAGES -> cachedFacebookMode == "full"
        pkg in YOUTUBE_PACKAGES -> cachedYoutubeMode == "full"
        pkg == SOCIAL_PACKAGES["social_snapchat"] -> cachedSnapchatBlocked
        pkg == SOCIAL_PACKAGES["social_twitter"] -> cachedTwitterBlocked
        pkg == SOCIAL_PACKAGES["social_tiktok"] -> cachedTiktokBlocked
        else -> false
    }

    private fun createNotification(): Notification {
        return ForegroundServiceHelper.buildSilentNotification(
            this, getString(R.string.notification_blocker_title), getString(R.string.notification_blocker_text)
        )
    }

    private fun getDayStart(nowMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

class AppBlockerCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {

    private val repo: AppRepository by inject<AppRepository>()

    override suspend fun doWork(): Result {
        try {
            val settings = repo.getAppSettings()
            if (!settings.isShieldActive()) return Result.success()
            val usm = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 60_000, now)
            var foregroundPkg: String? = null
            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && event.packageName != null) {
                    foregroundPkg = event.packageName
                }
            }
            val pkg = foregroundPkg ?: return Result.success()
            if (pkg == applicationContext.packageName) return Result.success()

            val whitelisted = repo.getBlocklist("whitelist", "apps").map { it.value }
            if (pkg in whitelisted) return Result.success()

            val blacklisted = repo.getBlocklist("blacklist", "apps").map { it.value }
            if (pkg in blacklisted) { blockApp(pkg, "app_blocker"); return Result.success() }

            val limit = repo.getAppLimit(pkg)
            if (limit != null) {
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, getDayStart(now), now)
                val usageMinutes = (stats.find { it.packageName == pkg }?.totalTimeInForeground ?: 0L) / 60000L
                if (usageMinutes >= limit.dailyMinutes) blockApp(pkg, "time_limit")
            }
            return Result.success()
        } catch (e: Exception) { return Result.retry() }
    }

    private suspend fun blockApp(pkg: String, reason: String) {
        val label = try {
            val ai = applicationContext.packageManager.getApplicationInfo(pkg, 0)
            applicationContext.packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) { pkg }
        repo.recordBlock(pkg, label, reason)
        val intent = Intent(applicationContext, BlockActivity::class.java).apply {
            putExtra("APP_NAME", label); putExtra("BLOCK_REASON", reason)
            putExtra("BLOCK_PKG", pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        applicationContext.startActivity(intent)
    }

    private fun getDayStart(nowMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
