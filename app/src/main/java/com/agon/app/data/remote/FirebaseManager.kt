package com.agon.app.data.remote

import android.content.Context
import com.agon.app.data.repository.AppRepository
import com.agon.app.data.settings.AppSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class FirebaseManager(private val context: Context) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val settings = AppSettings(context)
    private val repository = AppRepository(context)

    private var userId: String = ""
    private var childDeviceId: String = ""

    private val deviceRef
        get() = if (childDeviceId.isBlank()) null
        else database.getReference("childDevices").child(childDeviceId)

    suspend fun initialize(): Boolean {
        return try {
            val user = auth.signInAnonymously().await().user
            if (user != null) {
                userId = user.uid
                childDeviceId = settings.getChildDeviceId().ifBlank {
                    settings.setChildDeviceId(user.uid)
                    user.uid
                }
                Timber.d("FirebaseManager: authenticated as $userId")
                true
            } else {
                Timber.w("FirebaseManager: anonymous auth returned null")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "FirebaseManager: auth failed")
            false
        }
    }

    suspend fun syncDeviceInfo() {
        val ref = deviceRef ?: return
        try {
            val dao = repository.blockEventDao
            val todayStart = getTodayStart()
            val blocksToday = dao.blocksSince(todayStart).size
            val totalBlocks = dao.blocksSince(0L).size
            val allEvents = dao.blocksSince(0L)
            val mostBlocked = allEvents.groupBy { it.appLabel }
                .maxByOrNull { it.value.size }?.key ?: ""

            ref.child("info").updateChildren(mapOf(
                "profileName" to settings.getProfileName(),
                "shieldActive" to settings.isShieldActive(),
                "lastSeen" to ServerValue.TIMESTAMP,
                "streak" to repository.calculateStreak(),
                "totalBlocks" to totalBlocks,
                "blocksToday" to blocksToday,
                "mostBlockedApp" to mostBlocked,
                "parentEmail" to settings.getParentEmail(),
                "fcmToken" to FirebaseMessaging.getInstance().token.await()
            )).await()

            Timber.d("FirebaseManager: device info synced")
        } catch (e: Exception) {
            Timber.e(e, "FirebaseManager: syncDeviceInfo failed")
        }
    }

    suspend fun syncAppLimits() {
        val ref = deviceRef ?: return
        try {
            val limits = repository.appLimitDao.getAll()
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
            val events = repository.blockEventDao.blocksSince(
                System.currentTimeMillis() - 3600000L
            )
            val eventsMap = events.associate { event ->
                event.id.toString() to mapOf(
                    "packageName" to event.packageName,
                    "appLabel" to event.appLabel,
                    "blockType" to event.blockType,
                    "timestamp" to event.timestamp
                )
            }
            ref.child("events").updateChildren(eventsMap).await()
            Timber.d("FirebaseManager: ${events.size} events synced")
        } catch (e: Exception) {
            Timber.e(e, "FirebaseManager: syncBlockEvents failed")
        }
    }

    suspend fun sendAlert(type: String, message: String) {
        val ref = deviceRef ?: return
        try {
            val alertRef = ref.child("alerts").push()
            alertRef.setValue(mapOf(
                "type" to type,
                "message" to message,
                "timestamp" to ServerValue.TIMESTAMP,
                "acknowledged" to false
            )).await()
            Timber.d("FirebaseManager: alert sent ($type)")
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
            val weekStart = now - (now % 604800000L)
            val events = repository.blockEventDao.blocksSince(weekStart)
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

            Timber.d("FirebaseManager: weekly report synced")
        } catch (e: Exception) {
            Timber.e(e, "FirebaseManager: syncWeeklyReport failed")
        }
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
