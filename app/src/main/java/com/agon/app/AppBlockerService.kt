package com.agon.app

import android.app.Notification
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AppBlockerService : Service() {

    companion object {
        private const val ACTION_RELOAD_BLOCKLIST = "com.agon.app.action.RELOAD_BLOCKLIST"
        private const val NOTIFICATION_ID = 3001
        private const val POLL_INTERVAL_MS = 500L
        private const val MIN_TIME_BETWEEN_BLOCKS_MS = 2000L
        private const val PERIODIC_WORK_NAME = "app_blocker_periodic"
        private const val TIME_LIMIT_WORK_NAME = "time_limit_check"

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
            val request = PeriodicWorkRequestBuilder<AppBlockerCheckWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun reloadBlocklist(context: Context) {
            val intent = Intent(context, AppBlockerService::class.java).apply {
                action = ACTION_RELOAD_BLOCKLIST
            }
            context.startService(intent)
        }

        fun scheduleTimeLimitCheck(context: Context) {
            val request = PeriodicWorkRequestBuilder<TimeLimitCheckWorker>(
                1, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TIME_LIMIT_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pollingJob: Job? = null
    private val repo: AppRepository by lazy {
        (applicationContext as GuardianApp).repository
    }
    private lateinit var usageStatsManager: UsageStatsManager
    private val lastBlockTimes = mutableMapOf<String, Long>()
    private var lastCheckTime = 0L
    private var currentDayStart = 0L
    private val todayTimeLimitBlocked = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        lastCheckTime = now
        currentDayStart = getDayStart(now)
        Timber.d("AppBlockerService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RELOAD_BLOCKLIST) {
            return START_NOT_STICKY
        }
        ForegroundServiceHelper.startForegroundCompat(
            this, NOTIFICATION_ID, createNotification()
        )
        startPolling()
        schedulePeriodicCheck(this)
        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        serviceScope.cancel()
        Timber.d("AppBlockerService destroyed")
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    if (repo.getAppSettings().isShieldActive()) {
                        refreshDayBoundary()
                        checkForegroundApp()
                    }
                } catch (e: Exception) {
                    Timber.w(e, "AppBlocker polling error")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun refreshDayBoundary() {
        val today = getDayStart(System.currentTimeMillis())
        if (today != currentDayStart) {
            currentDayStart = today
            todayTimeLimitBlocked.clear()
        }
    }

    private suspend fun checkForegroundApp() {
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(lastCheckTime, now)
        lastCheckTime = now

        val foregroundPackages = mutableSetOf<String>()
        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                && event.packageName != null
                && event.packageName != packageName
                && !event.packageName!!.startsWith("com.agon.")
                && event.packageName != "com.android.systemui"
                && event.packageName != "android"
            ) {
                foregroundPackages.add(event.packageName)
            }
        }

        if (foregroundPackages.isEmpty()) return
        if (!repo.getAppSettings().isShieldActive()) return

        for (pkg in foregroundPackages) {
            if (now - (lastBlockTimes[pkg] ?: 0L) < MIN_TIME_BETWEEN_BLOCKS_MS) continue

            val reason = shouldBlock(pkg) ?: continue
            lastBlockTimes[pkg] = now

            val appLabel = getAppLabel(pkg)
            repo.recordBlock(pkg, appLabel, reason)

            if (reason == "time_limit") {
                todayTimeLimitBlocked.add(pkg)
            }

            launchBlockActivity(pkg, appLabel, reason)
            break
        }
    }

    /**
     * Returns the block reason if the app should be blocked, or null if allowed.
     * Checks: whitelist, blacklist, school/bedtime mode, social toggles,
     * Facebook/YouTube modes, custom blocklist, time limits, and schedule rules.
     */
    private suspend fun shouldBlock(pkg: String): String? {
        if (pkg == this.packageName) return null
        if (pkg.startsWith("com.agon.")) return null

        // Whitelist check
        val whitelisted = repo.getBlocklist("whitelist", "apps").map { it.value }
        if (pkg in whitelisted) return null

        // Blacklist check (BlocklistDao — listType="blacklist", category="apps")
        val blacklisted = repo.getBlocklist("blacklist", "apps").map { it.value }
        if (pkg in blacklisted) return "app_blocker"

        val settings = repo.getAppSettings()

        // School / Bedtime mode
        if (settings.isSchoolTimeActive() || settings.isBedtimeActive()) {
            if (isSocialOrBlocklistApp(pkg)) return "app_blocker"
        }

        // Social media per-app toggles
        val socialKey = SOCIAL_PACKAGES.entries.find { it.value == pkg }?.key
        if (socialKey != null) {
            val blocked = when (socialKey) {
                "social_instagram" -> settings.socialInstagramFlow.first()
                "social_snapchat" -> settings.socialSnapchatFlow.first()
                "social_twitter" -> settings.socialTwitterFlow.first()
                "social_tiktok" -> settings.socialTiktokFlow.first()
                else -> false
            }
            if (blocked) return "app_blocker"
        }

        // Facebook full block
        if (pkg in FACEBOOK_PACKAGES && settings.getFacebookMode() == "full") return "app_blocker"

        // YouTube full block
        if (pkg in YOUTUBE_PACKAGES && settings.getYoutubeMode() == "full") return "app_blocker"

        // Custom blocklist ("blocked_apps")
        val blockedApps = repo.getFullBlocklist("blocked_apps")
        if (blockedApps.any { it.value == pkg }) return "app_blocker"

        // Time limit — block until next day
        if (pkg !in todayTimeLimitBlocked) {
            val limit = repo.getAppLimit(pkg)
            if (limit != null && isTimeLimitExceeded(limit)) return "time_limit"
        }

        // Schedule rules — block social + blocklist apps during active schedule
        if (isScheduleActive() && isSocialOrBlocklistApp(pkg)) return "app_blocker"

        return null
    }

    private suspend fun isSocialOrBlocklistApp(pkg: String): Boolean {
        if (pkg in SOCIAL_PACKAGES.values) return true
        if (pkg in FACEBOOK_PACKAGES) return true
        if (pkg in YOUTUBE_PACKAGES) return true
        return repo.getFullBlocklist("blocked_apps").any { it.value == pkg }
    }

    private fun isTimeLimitExceeded(limit: AppLimitEntity): Boolean {
        return getUsageMinutesToday(limit.packageName) >= limit.dailyMinutes
    }

    private fun getUsageMinutesToday(packageName: String): Long {
        return try {
            val now = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                currentDayStart,
                now
            )
            val appStat = stats.find { it.packageName == packageName }
            (appStat?.totalTimeInForeground ?: 0L) / 60000L
        } catch (e: Exception) {
            Timber.w(e, "getUsageMinutesToday failed")
            0L
        }
    }

    private suspend fun isScheduleActive(): Boolean {
        val rules = try {
            repo.getAllScheduleRules().first()
        } catch (_: Exception) {
            emptyList()
        }
        if (rules.isEmpty()) return false

        val now = Calendar.getInstance()
        val currentDay = dayOfWeekToInt(now.get(Calendar.DAY_OF_WEEK))
        val currentTotal = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        for (rule in rules) {
            if (!rule.enabled) continue
            val days = rule.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (currentDay !in days) continue

            val startTotal = rule.startHour * 60 + rule.startMinute
            val endTotal = rule.endHour * 60 + rule.endMinute

            val active = if (startTotal <= endTotal) {
                currentTotal in startTotal..endTotal
            } else {
                currentTotal >= startTotal || currentTotal <= endTotal
            }
            if (active) return true
        }
        return false
    }

    private fun dayOfWeekToInt(calendarDay: Int): Int = when (calendarDay) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    private fun launchBlockActivity(pkg: String, appLabel: String, reason: String) {
        val intent = Intent(this, BlockActivity::class.java).apply {
            putExtra("APP_NAME", appLabel)
            putExtra("BLOCK_REASON", reason)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun createNotification(): Notification {
        return ForegroundServiceHelper.buildSilentNotification(
            context = this,
            title = getString(R.string.notification_blocker_title),
            text = getString(R.string.notification_blocker_text)
        )
    }

    private fun getDayStart(nowMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

/**
 * WorkManager worker that periodically checks the foreground app
 * and blocks it if needed. Provides resilience when the service
 * is not running (e.g. after app restart).
 */
class AppBlockerCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("AppBlockerCheckWorker: periodic check starting")
        return try {
            val app = applicationContext as GuardianApp
            val settings = app.repository.getAppSettings()
            if (!settings.isShieldActive()) return Result.success()

            val usageStatsManager =
                applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(now - 60_000, now)

            var foregroundPkg: String? = null
            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)
                @Suppress("DEPRECATION")
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                    && event.packageName != null
                    && event.packageName != applicationContext.packageName
                    && !event.packageName!!.startsWith("com.agon.")
                ) {
                    foregroundPkg = event.packageName
                }
            }

            val pkg = foregroundPkg ?: return Result.success()

            val whitelisted = app.repository.getBlocklist("whitelist", "apps").map { it.value }
            if (pkg in whitelisted) return Result.success()

            // Blacklist check
            val blacklisted = app.repository.getBlocklist("blacklist", "apps").map { it.value }
            if (pkg in blacklisted) {
                blockApp(app, pkg, "app_blocker")
                return Result.success()
            }

            // Custom blocklist
            val blockedApps = app.repository.getFullBlocklist("blocked_apps")
            if (blockedApps.any { it.value == pkg }) {
                blockApp(app, pkg, "app_blocker")
                return Result.success()
            }

            // Time limit
            val limit = app.repository.getAppLimit(pkg)
            if (limit != null) {
                val dayStart = getDayStart(now)
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, dayStart, now
                )
                val usageMinutes = (stats.find { it.packageName == pkg }?.totalTimeInForeground ?: 0L) / 60000L
                if (usageMinutes >= limit.dailyMinutes) {
                    blockApp(app, pkg, "time_limit")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Timber.w(e, "AppBlockerCheckWorker failed")
            Result.retry()
        }
    }

    private suspend fun blockApp(app: GuardianApp, pkg: String, reason: String) {
        val label = try {
            val ai = applicationContext.packageManager.getApplicationInfo(pkg, 0)
            applicationContext.packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkg
        }
        app.repository.recordBlock(pkg, label, reason)
        val intent = Intent(applicationContext, BlockActivity::class.java).apply {
            putExtra("APP_NAME", label)
            putExtra("BLOCK_REASON", reason)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        applicationContext.startActivity(intent)
    }

    private fun getDayStart(nowMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

class TimeLimitCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as GuardianApp
            val limits = app.repository.appLimitDao.getAll()
            if (limits.isEmpty()) return Result.success()

            val usm = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val dayStart = getDayStart(now)

            for (limit in limits) {
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, now)
                val usageMinutes = (stats.find { it.packageName == limit.packageName }?.totalTimeInForeground ?: 0L) / 60000L
                if (usageMinutes >= limit.dailyMinutes) {
                    val label = try {
                        val ai = applicationContext.packageManager.getApplicationInfo(limit.packageName, 0)
                        applicationContext.packageManager.getApplicationLabel(ai).toString()
                    } catch (_: Exception) { limit.appLabel.ifBlank { limit.packageName } }
                    app.repository.recordBlock(limit.packageName, label, "time_limit")
                    val intent = Intent(applicationContext, BlockActivity::class.java).apply {
                        putExtra("APP_NAME", label)
                        putExtra("BLOCK_REASON", "time_limit")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    applicationContext.startActivity(intent)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Timber.w(e, "TimeLimitCheckWorker failed")
            Result.retry()
        }
    }

    private fun getDayStart(nowMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
