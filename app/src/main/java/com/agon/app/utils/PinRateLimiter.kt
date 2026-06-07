package com.agon.app.utils

import com.agon.app.data.settings.EncryptedPrefs
import kotlin.math.min
import kotlin.math.pow

/**
 * PIN-RATE-LIMIT — exponential-backoff rate limiter for the shield-
 * deactivation PIN dialog and any other path that verifies a PIN.
 *
 * Why: the previous implementation only counted failures in a plain
 * `Int` field on the ViewModel, which lost state on configuration
 * change and process death. An attacker could simply force-kill the
 * app between wrong-PIN attempts to reset the counter, then brute-
 * force a 4-6 digit PIN at the speed of PBKDF2 (tens of ms per
 * attempt).
 *
 * Where: stored in `EncryptedPrefs` so it survives ViewModel
 * recreation, rotation, theme switches, AND process death.
 *
 * Behaviour:
 *   - Attempts 1..3  → no lockout (still logs a tamper alert at 3)
 *   - Attempts 4..6  → 30 s lockout
 *   - Attempts 7..9  → 5 min lockout
 *   - Attempts 10+   → 1 h lockout (doubles up to 24 h)
 *
 * A successful verify clears the counter and the lockout end.
 */
class PinRateLimiter(private val prefs: EncryptedPrefs) {

    /**
     * Returns the remaining lockout duration in milliseconds
     * (0 if not currently locked out).
     */
    fun remainingMs(): Long = prefs.pinLockoutRemainingMs()

    /**
     * Returns true when the user is currently allowed to attempt a
     * PIN verification. False when a lockout is active.
     */
    fun isLockedOut(): Boolean = remainingMs() > 0L

    /**
     * Records a failed PIN attempt, increments the persistent
     * counter, and (if the threshold is reached) sets the
     * exponential-backoff lockout.
     */
    fun recordFailure() {
        val count = prefs.getPinFailCount() + 1
        prefs.setPinFailCount(count)
        if (count >= LOCKOUT_THRESHOLD) {
            // Exponential: 30s, 60s, 120s, 240s, … capped at 24h.
            val tier = count - LOCKOUT_THRESHOLD
            val baseSeconds = 30.0
            val multiplier = 2.0.pow(tier.coerceAtMost(8).toDouble())
            val seconds = (baseSeconds * multiplier).toLong()
            val capped = min(seconds, MAX_LOCKOUT_SECONDS)
            prefs.setPinLockoutUntilMs(System.currentTimeMillis() + capped * 1000L)
        }
    }

    /** Resets the failure state. Call after a successful verify. */
    fun reset() {
        prefs.clearPinFailureState()
    }

    /** Read-only: how many failed attempts have been recorded. */
    fun currentFailCount(): Int = prefs.getPinFailCount()

    companion object {
        /** Failures before the first exponential backoff kicks in. */
        const val LOCKOUT_THRESHOLD: Int = 3

        /** Cap on the lockout duration (24 h). */
        const val MAX_LOCKOUT_SECONDS: Long = 24L * 60L * 60L
    }
}
