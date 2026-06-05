package com.agon.app.data.remote

import android.content.Context
import com.agon.app.data.local.dao.AppLimitDao
import com.agon.app.data.local.dao.BlockEventDao
import com.agon.app.data.settings.AppSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class FirebaseManager(
    private val context: Context,
    private val blockEventDao: BlockEventDao,
    private val appLimitDao: AppLimitDao,
    private val settings: AppSettings
) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    private var userId: String = ""
    private var childDeviceId: String = ""
    private var lastFcmToken: String = ""

    private val deviceRef
        get() = if (childDeviceId.isBlank()) null
        else database.getReference("childDevices").child(childDeviceId)

    suspend fun initialize(): Boolean {
        if (userId.isNotBlank()) return true
        return try {
            val user = auth.signInAnonymously().await().user
            if (user != null) {
                userId = user.uid
                childDeviceId = settings.getChildDeviceId().ifBlank {
                    val newId = user.uid
                    settings.setChildDeviceId(newId)
                    newId
                }
                Timber.d("FirebaseManager: authenticated as $userId")
                true
            } else false
        } catch (e: Exception) {
            Timber.e(e, "FirebaseManager: auth failed")
            false
        }
    }

    suspend fun syncDeviceInfo() {
        val ref = deviceRef ?: return
        try {
            // Issue #183: Only fetch FCM token if not already cached to save battery/IPC
            val token = if (lastFcmToken.isBlank()) {
                FirebaseMessaging.getInstance().token.await().also { lastFcmToken = it }
            } else lastFcmToken

            val todayStart = settings.getTodayStart()
            val blocksToday = blockEventDao.blocksSince(todayStart).size
            val allEventsCount = blockEventDao.getCount()
            
            // Issue #179 fix was already in DAO, we just use it here
            val mostBlocked = blockEventDao.getMostBlockedApp()?.appLabel ?: ""

            ref.child("info").updateChildren(mapOf(
                "profileName" to settings.getProfileName(),
                "shieldActive" to settings.isShieldActive(),
                "lastSeen" to ServerValue.TIMESTAMP,
                "streak" to settings.calculateStreak(blockEventDao),
                "totalBlocks" to allEventsCount,
                "blocksToday" to blocksToday,
                "mostBlockedApp" to mostBlocked,
                "parentEmail" to settings.getParentEmail(),
                "fcmToken" to token
            )).await()
        } catch (e: Exception) {
            Timber.e(e, "FirebaseManager: syncDeviceInfo failed")
        }
    }

    suspend fun syncAppLimits() {
        val ref = deviceRef ?: return
        try {
            val limits = appLimitDao.getAll()
            val limitsMap = limits.associate { limit ->
                limit.packageName to mapOf(
                    "label" to limit.appLabel,
                    "dailyMinutes" to limit.dailyMinutes
                )
            }
            ref.child("appLimits").setValue(limitsMap).await()
        } catch (e: Exception) {
            Timber.e(e, "FirebaseManager: syncAppLimits failed")
        }
    }

    suspend fun syncBlockEvents() {
        val ref = deviceRef ?: return
        try {
            val events = blockEventDao.blocksSince(System.currentTimeMillis() - 86400000L)
            val eventsMap = events.associate { event ->
                event.id.toString() to mapOf(
                    "packageName" to event.packageName,
                    "appLabel" to event.appLabel,
                    "blockType" to event.blockType,
                    "timestamp" to event.timestamp
                )
            }
            ref.child("events").updateChildren(eventsMap).await()
        } catch (e: Exception) {
            Timber.e(e, "FirebaseManager: syncBlockEvents failed")
        }
    }

    suspend fun sendAlert(type: String, message: String) {
        val ref = deviceRef ?: return
        try {
            ref.child("alerts").push().setValue(mapOf(
                "type" to type,
                "message" to message,
                "timestamp" to ServerValue.TIMESTAMP,
                "acknowledged" to false
            )).await()
        } catch (e: Exception) {
            Timber.e(e, "FirebaseManager: sendAlert failed")
        }
    }

    suspend fun processPendingCommands(onCommand: suspend (command: String, data: Map<String, String>) -> Unit) {
        val ref = deviceRef ?: return
        try {
            val snapshots = ref.child("commands").get().await()
            for (snapshot in snapshots.children) {
                val command = snapshot.value as? Map<*, *> ?: continue
                if (command["status"] != "pending") continue
                val type = command["type"] as? String ?: continue
                
                @Suppress("UNCHECKED_CAST")
                val data = (command["data"] as? Map<String, String>) ?: emptyMap()
                onCommand(type, data)
                
                // Issue #190: Mark as completed or remove to prevent node bloat
                snapshot.ref.child("status").setValue("completed").await()
            }
        } catch (e: Exception) {
            Timber.e(e, "FirebaseManager: processPendingCommands failed")
        }
    }

    suspend fun syncWeeklyReport() {
        val ref = deviceRef ?: return
        try {
            val now = System.currentTimeMillis()
            // Issue #189: Use Calendar to get actual local week start
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            val weekStart = cal.timeInMillis

            val events = blockEventDao.blocksSince(weekStart)
            val daysActive = events.map { it.timestamp / 86400000L }.distinct().count()
            val perApp = events.groupBy { it.packageName }
                .mapValues { it.value.size }
                .entries.sortedByDescending { it.value }
                .take(5)
                .associate { it.key to it.value }

            ref.child("weeklyReports").child((weekStart / 1000).toString()).setValue(mapOf(
                "weekStart" to weekStart,
                "totalBlocks" to events.size,
                "daysActive" to daysActive,
                "perApp" to perApp,
                "generatedAt" to ServerValue.TIMESTAMP
            )).await()
        } catch (e: Exception) {
            Timber.e(e, "FirebaseManager: syncWeeklyReport failed")
        }
    }
}
