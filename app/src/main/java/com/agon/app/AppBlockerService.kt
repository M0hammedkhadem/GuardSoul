package com.agon.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.agon.app.data.local.entity.AppLimitEntity
import com.agon.app.data.local.entity.ScheduleRuleEntity
import com.agon.app.data.repository.AppRepository
import com.agon.app.data.settings.AppSettings
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

class AppBlockerService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 3001
        private const val POLL_INTERVAL_MS = 1500L
        private const val MIN_TIME_BETWEEN_BLOCKS_MS = 3000L

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
            val intent = Intent(context, AppBlockerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AppBlockerService::class.java))
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

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        lastCheckTime = System.currentTimeMillis()
        Timber.d("AppBlockerService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startPolling()
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
                    val settings = repo.getAppSettings()
                    val isShieldActive = settings.isShieldActive()
                    val isSchoolTime = settings.isSchoolTimeActive()
                    val isBedtime = settings.isBedtimeActive()
                    if (isShieldActive || isSchoolTime || isBedtime) {
                        checkForegroundApp()
                    }
                } catch (e: Exception) {
                    Timber.w(e, "AppBlocker polling error")
                }
                delay(POLL_INTERVAL_MS)
            }
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
            @Suppress("DEPRECATION") if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
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

        val settings = repo.getAppSettings()
        val shieldActive = settings.isShieldActive()
        if (!shieldActive) return

        for (pkg in foregroundPackages) {
            val lastBlockTime = lastBlockTimes[pkg] ?: 0L
            if (now - lastBlockTime < MIN_TIME_BETWEEN_BLOCKS_MS) continue
            if (isAppBlocked(pkg, settings)) {
                lastBlockTimes[pkg] = now
                val appLabel = getAppLabel(pkg)
                repo.recordBlock(pkg, appLabel, "app_blocker")
                launchBlockActivity(pkg, appLabel)
                break
            }
        }
    }

    private suspend fun isAppBlocked(pkg: String, settings: AppSettings): Boolean {
        if (pkg == this.packageName) return false
        if (pkg.startsWith("com.agon.")) return false
        val whitelistedApps = repo.getBlocklist("whitelist", "apps").map { it.value }
        if (pkg in whitelistedApps) return false

        val isSchoolTime = settings.isSchoolTimeActive()
        val isBedtime = settings.isBedtimeActive()
        val isSpecialTime = isSchoolTime || isBedtime

        // During school/bedtime: only block social media apps and custom blocklist apps
        if (isSpecialTime) {
            val isSocial = SOCIAL_PACKAGES.values.any { it == pkg }
            val isFb = pkg in FACEBOOK_PACKAGES
            val isYt = pkg in YOUTUBE_PACKAGES
            val blockedApps = repo.getFullBlocklist("blocked_apps")
            val isCustomBlocked = blockedApps.any { it.value == pkg }
            if (isSocial || isFb || isYt || isCustomBlocked) return true
        }

        // Check from social app toggles
        for ((key, packageName) in SOCIAL_PACKAGES) {
            if (pkg == packageName) {
                return when (key) {
                    "social_instagram" -> settings.socialInstagramFlow.first()
                    "social_snapchat" -> settings.socialSnapchatFlow.first()
                    "social_twitter" -> settings.socialTwitterFlow.first()
                    "social_tiktok" -> settings.socialTiktokFlow.first()
                    else -> false
                }
            }
        }

        // Check Facebook mode
        if (pkg in FACEBOOK_PACKAGES) {
            val fbMode = settings.getFacebookMode()
            if (fbMode == "full") return true
        }

        // Check YouTube mode
        if (pkg in YOUTUBE_PACKAGES) {
            val ytMode = settings.getYoutubeMode()
            if (ytMode == "full") return true
        }

        // Check custom blocklist from Room
        val blockedApps = repo.getFullBlocklist("blocked_apps")
        if (blockedApps.any { it.value == pkg }) return true

        // Check time limits
        val limit: AppLimitEntity? = repo.getAppLimit(pkg)
        if (limit != null && isTimeLimitExceeded(limit)) return true

        // Check schedules - only block social media and blocklist apps during schedule
        if (isScheduleActive()) {
            val isSocial = SOCIAL_PACKAGES.values.any { it == pkg }
            val isFb = pkg in FACEBOOK_PACKAGES
            val isYt = pkg in YOUTUBE_PACKAGES
            val blockedApps = repo.getFullBlocklist("blocked_apps")
            val isCustomBlocked = blockedApps.any { it.value == pkg }
            if (isSocial || isFb || isYt || isCustomBlocked) return true
        }

        return false
    }

    private fun isTimeLimitExceeded(limit: AppLimitEntity): Boolean {
        val usageMinutes = getUsageMinutesToday(limit.packageName)
        return usageMinutes >= limit.dailyMinutes
    }

    private fun getUsageMinutesToday(packageName: String): Long {
        return try {
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, now)
            val appStat = stats.find { it.packageName == packageName }
            (appStat?.totalTimeInForeground ?: 0L) / 60000L
        } catch (e: Exception) {
            Timber.w(e, "AppBlocker: failed to query usage stats")
            0L
        }
    }

    private suspend fun isScheduleActive(): Boolean {
        val rules: List<ScheduleRuleEntity> = try { repo.getAllScheduleRules().first() } catch (_: Exception) { emptyList() }
        if (rules.isEmpty()) return false

        val now = java.util.Calendar.getInstance()
        val dayOfWeek = now.get(java.util.Calendar.DAY_OF_WEEK)
        val currentDay = when (dayOfWeek) {
            java.util.Calendar.MONDAY -> 1
            java.util.Calendar.TUESDAY -> 2
            java.util.Calendar.WEDNESDAY -> 3
            java.util.Calendar.THURSDAY -> 4
            java.util.Calendar.FRIDAY -> 5
            java.util.Calendar.SATURDAY -> 6
            java.util.Calendar.SUNDAY -> 7
            else -> 1
        }
        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(java.util.Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute

        val rulesList = rules
        for (i in rulesList.indices) {
            val rule = rulesList[i]
            if (!rule.enabled) continue
            val days = rule.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (currentDay !in days) continue

            val startTotal = rule.startHour * 60 + rule.startMinute
            val endTotal = rule.endHour * 60 + rule.endMinute

            val isActive = if (startTotal <= endTotal) {
                currentTotalMinutes >= startTotal && currentTotalMinutes <= endTotal
            } else {
                currentTotalMinutes >= startTotal || currentTotalMinutes <= endTotal
            }
            if (isActive) return true
        }
        return false
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

    private fun launchBlockActivity(pkg: String, appLabel: String) {
        val intent = Intent(this, BlockActivity::class.java).apply {
            putExtra("APP_NAME", appLabel)
            putExtra("BLOCK_REASON", "app_blocker")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AppNotificationChannels.APP_BLOCKER)
            .setContentTitle(getString(R.string.notification_blocker_title))
            .setContentText(getString(R.string.notification_blocker_text))
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun getTodayStart(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
