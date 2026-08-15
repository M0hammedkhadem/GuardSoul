package com.agon.app.domain.usecase

import com.agon.app.data.AppBlockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the extracted use cases reproduce the legacy MainViewModel
 * behavior verbatim (shield lock, exclusivity, list rules, shield lifecycle).
 */
class ToggleShieldUseCaseTest {

    private val uc = ToggleShieldUseCase()
    private fun state(
        active: Boolean = false,
        since: Long = 0L,
        pending: Long = 0L,
        control: Long = 0L,
    ) = ToggleShieldUseCase.ShieldState(active, since, pending, control)

    @Test
    fun `starting is instant and clears any pending stop`() {
        val out = uc(state(active = false), delayMillis = 60_000, now = 5_000)
        assertNotNull(out)
        assertTrue(out!!.active)
        assertEquals(5_000, out.since)
        assertEquals(0L, out.pendingStopAt)
    }

    @Test
    fun `stopping with zero delay stops immediately and accrues control seconds`() {
        val out = uc(state(active = true, since = 1_000), delayMillis = 0, now = 61_000)
        assertNotNull(out)
        assertFalse(out!!.active)
        assertEquals(60L, out.controlSeconds)
    }

    @Test
    fun `stopping with a delay schedules the stop and keeps the shield active`() {
        val out = uc(state(active = true, since = 1_000), delayMillis = 600_000, now = 61_000)
        assertNotNull(out)
        assertTrue(out!!.active)
        assertEquals(661_000, out.pendingStopAt)
    }

    @Test
    fun `toggling while a stop is scheduled is a no-op`() {
        assertNull(uc(state(active = true, since = 1_000, pending = 99_000), 0, 50_000))
    }

    @Test
    fun `cancelling a pending stop keeps the shield active`() {
        val out = uc.cancelPendingStop(state(active = true, since = 1_000, pending = 99_000))
        assertTrue(out.active)
        assertEquals(0L, out.pendingStopAt)
    }

    @Test
    fun `due scheduled stop is accounted at the scheduled instant not now`() {
        val out = uc.completeIfDue(state(active = true, since = 1_000, pending = 61_000), now = 999_000)
        assertNotNull(out)
        assertFalse(out!!.active)
        assertEquals(60L, out.controlSeconds) // (61000-1000)/1000, not (999000-1000)/1000
    }

    @Test
    fun `not-yet-due scheduled stop returns null`() {
        assertNull(uc.completeIfDue(state(active = true, since = 1_000, pending = 99_000), now = 50_000))
    }
}

class UpdateAppBlockUseCasesTest {

    private val full = UpdateAppFullBlockUseCase()
    private val shorts = UpdateAppShortsBlockUseCase()

    @Test
    fun `enabling full block disables shorts (exclusivity) and always persists`() {
        val map = hashMapOf("fb" to AppBlockState(fullBlock = false, shortsBlock = true))
        var persisted = false
        assertTrue(full(map, "fb", enabled = true, shieldActive = true) { persisted = true })
        assertEquals(AppBlockState(fullBlock = true, shortsBlock = false), map["fb"])
        assertTrue(persisted)
    }

    @Test
    fun `disabling full block is rejected while shield is active`() {
        val map = hashMapOf("fb" to AppBlockState(fullBlock = true))
        var persisted = false
        assertFalse(full(map, "fb", enabled = false, shieldActive = true) { persisted = true })
        assertEquals(AppBlockState(fullBlock = true), map["fb"]) // untouched
        assertFalse(persisted)
    }

    @Test
    fun `downgrade full to shorts is rejected while shield is active`() {
        val map = hashMapOf("fb" to AppBlockState(fullBlock = true))
        assertFalse(shorts(map, "fb", enabled = true, shieldActive = true) {})
        assertEquals(AppBlockState(fullBlock = true), map["fb"])
    }

    @Test
    fun `downgrade full to shorts is allowed when shield is off`() {
        val map = hashMapOf("fb" to AppBlockState(fullBlock = true))
        assertTrue(shorts(map, "fb", enabled = true, shieldActive = false) {})
        assertEquals(AppBlockState(fullBlock = false, shortsBlock = true), map["fb"])
    }

    @Test
    fun `disabling shorts is rejected while shield is active`() {
        val map = hashMapOf("fb" to AppBlockState(shortsBlock = true))
        assertFalse(shorts(map, "fb", enabled = false, shieldActive = true) {})
        assertEquals(AppBlockState(shortsBlock = true), map["fb"])
    }
}

class ListUseCasesTest {

    private val add = AddToListUseCase()
    private val remove = RemoveFromListUseCase()

    @Test
    fun `adding to blacklist is allowed even while shield is active`() {
        val list = mutableListOf<String>()
        var persisted = false
        assertTrue(add(list, black = true, value = "bad.com", shieldActive = true) { persisted = true })
        assertEquals(listOf("bad.com"), list)
        assertTrue(persisted)
    }

    @Test
    fun `adding to whitelist is rejected while shield is active`() {
        val list = mutableListOf<String>()
        var persisted = false
        assertFalse(add(list, black = false, value = "ok.com", shieldActive = true) { persisted = true })
        assertTrue(list.isEmpty())
        assertFalse(persisted)
    }

    @Test
    fun `blank input is a silent success without persisting`() {
        val list = mutableListOf<String>()
        var persisted = false
        assertTrue(add(list, black = true, value = "   ", shieldActive = false) { persisted = true })
        assertTrue(list.isEmpty())
        assertFalse(persisted) // legacy early-return: no write
    }

    @Test
    fun `duplicates are not added twice but persist still runs`() {
        val list = mutableListOf("bad.com")
        var persisted = false
        assertTrue(add(list, black = true, value = "bad.com", shieldActive = false) { persisted = true })
        assertEquals(1, list.size)
        assertTrue(persisted) // legacy behavior: persistLists() ran regardless
    }

    @Test
    fun `removing from blacklist is rejected while shield is active`() {
        val list = mutableListOf("bad.com")
        assertFalse(remove(list, black = true, value = "bad.com", shieldActive = true) {})
        assertEquals(listOf("bad.com"), list)
    }

    @Test
    fun `removing from whitelist is allowed while shield is active`() {
        val list = mutableListOf("ok.com")
        assertTrue(remove(list, black = false, value = "ok.com", shieldActive = true) {})
        assertTrue(list.isEmpty())
    }
}

class ResetAllDataUseCaseTest {

    private val uc = ResetAllDataUseCase()

    @Test
    fun `reset is rejected while shield is active and storage is untouched`() {
        var cleared = false
        assertFalse(uc(shieldActive = true) { cleared = true })
        assertFalse(cleared)
    }

    @Test
    fun `reset clears storage when shield is off`() {
        var cleared = false
        assertTrue(uc(shieldActive = false) { cleared = true })
        assertTrue(cleared)
    }
}
