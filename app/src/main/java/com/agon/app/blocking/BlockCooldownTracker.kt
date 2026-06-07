package com.agon.app.blocking

import java.util.concurrent.atomic.AtomicLong

/**
 * Simple, thread-safe cooldown tracker used by the various
 * accessibility-service engines to throttle home-bounce and
 * block-screen actions.
 *
 * A single shared instance is constructed by
 * [com.agon.app.services.GuardSoulAccessibilityService] and
 * injected into the engines that need it. Sharing the tracker
 * across engines is intentional: if the content filter and the
 * uninstall guard both want to fire a home action in the same
 * event burst, the cooldown suppresses the duplicate so the
 * user only experiences one home animation.
 *
 * The state lives in an [AtomicLong] with compare-and-set so
 * the read-then-write is atomic even when both engines call
 * `tryFire` concurrently from different threads. The previous
 * `@Volatile Long` design had a benign race that could let
 * through two near-simultaneous fires.
 */
class BlockCooldownTracker(private val cooldownMs: Long) {

    private val lastFireAt = AtomicLong(0L)

    /**
     * Returns `true` if the cooldown has elapsed since the last
     * `tryFire()` succeeded (the caller should perform the
     * action); returns `false` if we are still inside the
     * cooldown window. On `true`, the cooldown is reset.
     */
    fun tryFire(now: Long = System.currentTimeMillis()): Boolean {
        while (true) {
            val prev = lastFireAt.get()
            if (now - prev < cooldownMs) return false
            if (lastFireAt.compareAndSet(prev, now)) return true
            // CAS lost the race — another thread updated lastFireAt
            // between our read and CAS. Retry: the new value is
            // either still within cooldown (return false) or the
            // cooldown elapsed (set ours and return true).
        }
    }
}
