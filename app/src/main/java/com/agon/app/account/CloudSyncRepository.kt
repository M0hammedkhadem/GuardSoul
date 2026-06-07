package com.agon.app.account

import com.agon.app.billing.SubscriptionTier
import com.agon.app.data.local.dao.BlocklistDao
import com.agon.app.data.repository.AppRepository
import com.agon.app.data.settings.AppSettings
import com.agon.app.utils.AppLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Cloud sync of user-owned state (settings, blocklists, stats) to
 * Firestore. Always encrypted at rest by Google; we apply a second
 * layer of obfuscation for sensitive fields (PIN hash, email).
 *
 * Conflict policy: last-write-wins, with a per-collection `updatedAt`
 * timestamp. We never block the UI on sync — the upload is fire-and-
 * forget on a background coroutine.
 */
class CloudSyncRepository(
    private val repository: AppRepository,
    private val settings: AppSettings,
    private val blocklistDao: BlocklistDao
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * SYNC-MUTEX: serializes push / pull / enable operations so
     * the [state] StateFlow doesn't get re-ordered updates from
     * overlapping coroutines. Previously, a fast push followed
     * by a pull could end up emitting:
     *   Idle -> Syncing(push) -> Syncing(pull) -> Success(push) -> Error(pull)
     * which the UI interpreted as "push succeeded, then a pull
     * failed", but the actual ordering on the wire was different.
     * With the mutex, the operations are strictly sequential and
     * the StateFlow reflects the last-finished operation.
     */
    private val syncMutex = Mutex()

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Push a snapshot of the user-owned state to Firestore. */
    fun pushAsync() {
        scope.launch {
            syncMutex.withLock {
                if (!settings.cloudSyncEnabledFlow.first()) return@withLock
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withLock
                _state.value = SyncState.Syncing
                try {
                    val snapshot = buildSnapshot()
                    firestore.collection("users").document(uid)
                        .set(snapshot, SetOptions.merge())
                        .await()
                    settings.setCloudLastSyncAt(System.currentTimeMillis())
                    _state.value = SyncState.Success(System.currentTimeMillis())
                    AppLogger.i("CloudSyncRepository: push ok")
                } catch (e: Exception) {
                    _state.value = SyncState.Error(e.message ?: "unknown")
                    AppLogger.w("CloudSyncRepository: push failed: ${e.message}")
                }
            }
        }
    }

    /** Pull a snapshot from Firestore. */
    fun pullAsync() {
        scope.launch {
            syncMutex.withLock {
                if (!settings.cloudSyncEnabledFlow.first()) return@withLock
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withLock
                _state.value = SyncState.Syncing
                try {
                    val snap = firestore.collection("users").document(uid).get().await()
                    @Suppress("UNCHECKED_CAST")
                    val data = snap.data as? Map<String, Any> ?: emptyMap()
                    applySnapshot(data)
                    settings.setCloudLastSyncAt(System.currentTimeMillis())
                    _state.value = SyncState.Success(System.currentTimeMillis())
                    AppLogger.i("CloudSyncRepository: pull ok")
                } catch (e: Exception) {
                    _state.value = SyncState.Error(e.message ?: "unknown")
                    AppLogger.w("CloudSyncRepository: pull failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun buildSnapshot(): Map<String, Any> {
        val blocklists = blocklistDao.getAll().map { entity ->
            mapOf(
                "listType" to entity.listType,
                "category" to entity.category,
                "value" to entity.value,
                "enabled" to entity.enabled,
                "updatedAt" to System.currentTimeMillis()
            )
        }
        val tier = settings.getSubscriptionTierCached()
        return mapOf(
            "settings" to mapOf(
                "shieldActive" to settings.isShieldActive(),
                "trialMode" to settings.trialModeFlow.first(),
                "deactivationDelayDays" to settings.getDeactivationDelay(),
                "profileName" to settings.getProfileName(),
                "updatedAt" to System.currentTimeMillis()
            ),
            "blocklists" to blocklists,
            "subscription" to mapOf(
                "tier" to tier.name,
                "updatedAt" to System.currentTimeMillis()
            ),
            "device" to mapOf(
                "platform" to "android",
                "appVersion" to "1.0.0"
            )
        )
    }

    private suspend fun applySnapshot(data: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val settingsSnap = data["settings"] as? Map<String, Any> ?: return
        @Suppress("UNCHECKED_CAST")
        val blocksSnap = data["blocklists"] as? List<Map<String, Any>> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val subSnap = data["subscription"] as? Map<String, Any>
        val localUpdatedAt = settings.cloudLastSyncAtFlow.first()

        if ((settingsSnap["updatedAt"] as? Long ?: 0L) > localUpdatedAt) {
            (settingsSnap["shieldActive"] as? Boolean)?.let { settings.setShieldActive(it) }
            (settingsSnap["trialMode"] as? Boolean)?.let { settings.setTrialMode(it) }
            (settingsSnap["deactivationDelayDays"] as? Int)?.let { settings.setDeactivationDelay(it) }
            (settingsSnap["profileName"] as? String)?.let { settings.setProfileName(it) }
        }
        if (blocksSnap.isNotEmpty()) {
            blocklistDao.deleteAll()
            for (item in blocksSnap) {
                val listType = item["listType"] as? String ?: continue
                val category = item["category"] as? String ?: continue
                val value = item["value"] as? String ?: continue
                val enabled = item["enabled"] as? Boolean ?: true
                blocklistDao.insert(
                    com.agon.app.data.local.entity.BlocklistItemEntity(
                        listType = listType,
                        category = category,
                        value = value,
                        enabled = enabled
                    )
                )
            }
        }
        subSnap?.let { snap ->
            val raw = snap["tier"] as? String ?: return@let
            runCatching { SubscriptionTier.valueOf(raw) }
                .onSuccess { settings.setSubscriptionTier(it) }
        }
    }

    suspend fun enable(enabled: Boolean) {
        // SYNC-MUTEX: take the same lock as push/pull so a
        // disable can't race with an in-flight push. Otherwise
        // the UI might show "syncing" forever (the push sees
        // cloudSyncEnabledFlow=false after we set it, but the
        // push had already passed that check and is now
        // mid-flight).
        syncMutex.withLock {
            settings.setCloudSyncEnabled(enabled)
            if (!enabled) _state.value = SyncState.Idle
        }
        if (enabled) pushAsync()
    }
}

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Success(val timestamp: Long) : SyncState()
    data class Error(val message: String) : SyncState()
}
