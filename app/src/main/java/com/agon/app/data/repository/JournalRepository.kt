package com.agon.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.agon.app.data.JournalEntry
import com.agon.app.data.PrefKeys
import com.agon.app.data.local.dao.JournalDao
import com.agon.app.data.local.entity.JournalEntryEntity
import com.agon.app.data.security.JournalCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Journal persistence backed by Room (journal_entries table).
 *
 * The legacy store was a single JSON string under [PrefKeys.JOURNAL] in
 * Preferences DataStore; [migrateIfNeeded] imports it into Room exactly once
 * (guarded by the [PrefKeys.JOURNAL_MIGRATED] flag) and then removes the old
 * key, so pre-existing user journals survive the update with no data loss.
 *
 * Security: the `text` field is encrypted at rest via [JournalCrypto]
 * (AES256-GCM, Android Keystore). Encryption/decryption happens ONLY in this
 * data layer — the [JournalEntry] handed to ViewModel/UI is always plaintext.
 * Legacy unencrypted rows decrypt as-is (fallback) and get encrypted on the
 * next save.
 */
class JournalRepository(
    private val dao: JournalDao,
    private val ds: DataStore<Preferences>,
    private val crypto: JournalCrypto,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val journalSer = ListSerializer(JournalEntry.serializer())
    private val migrationMutex = Mutex()

    /**
     * Live journal stream, newest first. Runs the one-time DataStore -> Room
     * migration before the first emission so old entries are always included.
     */
    fun observeJournal(): Flow<List<JournalEntry>> = flow {
        migrateIfNeeded()
        emitAll(dao.getAll().map { list -> list.map { it.toModel() } })
    }

    suspend fun add(entry: JournalEntry) {
        dao.insert(entry.toEntity())
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    /** Full wipe — used by "reset all data". */
    suspend fun clearAll() {
        dao.deleteAll()
    }

    /**
     * One-time import of the legacy JSON journal into Room:
     *  1. Skip when the migrated flag is already set.
     *  2. Decode [PrefKeys.JOURNAL] with the SAME serializer previously used
     *     by MainViewModel; insert every entry into Room.
     *  3. Remove the legacy key and set the flag ONLY after a successful
     *     import (an undecodable/absent payload just sets the flag).
     */
    suspend fun migrateIfNeeded() {
        migrationMutex.withLock {
            val prefs = ds.data.first()
            if (prefs[PrefKeys.JOURNAL_MIGRATED] == true) return

            val raw = prefs[PrefKeys.JOURNAL]
            if (!raw.isNullOrBlank()) {
                val legacy = runCatching { json.decodeFromString(journalSer, raw) }.getOrNull()
                legacy?.forEach { dao.insert(it.toEntity()) }
            }
            ds.edit {
                it.remove(PrefKeys.JOURNAL)
                it[PrefKeys.JOURNAL_MIGRATED] = true
            }
        }
    }

    // ---------- Mapping (UI model unchanged; crypto at the boundary) ----------

    private fun JournalEntryEntity.toModel() = JournalEntry(
        id = id,
        timestamp = timestamp,
        mood = mood,
        triggers = triggers,
        text = crypto.decrypt(text), // legacy plaintext falls through unchanged
    )

    private fun JournalEntry.toEntity() = JournalEntryEntity(
        id = id,
        timestamp = timestamp,
        mood = mood,
        triggers = triggers,
        text = crypto.encrypt(text),
    )
}
