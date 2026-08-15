package com.agon.app.data

import com.agon.app.data.local.AppDatabase
import com.agon.app.data.local.Converters
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the DataStore -> Room journal migration format:
 *  - the legacy JSON payload (written by the old MainViewModel with
 *    ListSerializer<JournalEntry>) must keep decoding EXACTLY,
 *  - the Room TypeConverter for `triggers` must round-trip losslessly.
 * AppDatabase is referenced so a schema/entity rename breaks this test.
 */
class JournalMigrationParityTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val journalSer = ListSerializer(JournalEntry.serializer())

    /** Byte-format produced by the legacy persistJournal(). */
    private fun legacyPayload(entries: List<JournalEntry>): String =
        json.encodeToString(journalSer, entries)

    @Test
    fun `legacy journal payload decodes with identical content`() {
        val original = listOf(
            JournalEntry(id = 2, timestamp = 2_000, mood = 4, triggers = listOf("الملل", "السهر"), text = "يوم جيد"),
            JournalEntry(id = 1, timestamp = 1_000, mood = 0, triggers = emptyList(), text = ""),
        )
        val decoded = json.decodeFromString(journalSer, legacyPayload(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `legacy payload with unknown future fields still decodes`() {
        val raw = """[{"id":9,"timestamp":9000,"mood":2,"triggers":["التوتر"],"text":"ملاحظة","newField":true}]"""
        val decoded = runCatching { json.decodeFromString(journalSer, raw) }.getOrNull()
        assertNotNull(decoded)
        assertEquals(9L, decoded!!.single().id)
        assertEquals(listOf("التوتر"), decoded.single().triggers)
    }

    @Test
    fun `corrupt legacy payload yields null instead of crashing (skip-import path)`() {
        val decoded = runCatching { json.decodeFromString(journalSer, "{not json]") }.getOrNull()
        assertNull(decoded)
    }

    @Test
    fun `triggers TypeConverter round-trips arabic and empty lists`() {
        val conv = Converters()
        val values = listOf(listOf("الملل", "الوحدة", "وسائل التواصل"), emptyList(), listOf("a,b", "\"quoted\""))
        values.forEach { list ->
            assertEquals(list, conv.toStringList(conv.fromStringList(list)))
        }
    }

    @Test
    fun `triggers TypeConverter tolerates corrupt column data`() {
        assertEquals(emptyList<String>(), Converters().toStringList("garbage"))
    }

    @Test
    fun `database class exposes the journal dao`() {
        // Compile-time + reflective guard: entity table wiring stays present.
        val method = AppDatabase::class.java.methods.firstOrNull { it.name == "journalDao" }
        assertTrue(method != null)
    }
}
