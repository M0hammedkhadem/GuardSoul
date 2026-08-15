package com.agon.app.domain.usecase

/**
 * Shield lifecycle rules, moved verbatim from MainViewModel:
 *
 *  - Starting is instant.
 *  - Stopping honours the anti-impulse delay: with a non-zero delay the stop
 *    is SCHEDULED and the shield stays fully active until the timer elapses.
 *  - While a stop is already scheduled, toggling is a no-op (returns null).
 *  - Cancelling a scheduled stop strengthens protection — always allowed.
 */
class ToggleShieldUseCase {

    /** Immutable snapshot of the shield's persisted state. */
    data class ShieldState(
        val active: Boolean,
        val since: Long,
        val pendingStopAt: Long,
        val controlSeconds: Long,
    )

    /**
     * Returns the new state to apply+persist, or null when nothing changed
     * (a stop was already scheduled — legacy early-return without persist).
     */
    operator fun invoke(current: ShieldState, delayMillis: Long, now: Long): ShieldState? {
        return if (!current.active) {
            current.copy(active = true, since = now, pendingStopAt = 0L)
        } else {
            if (current.pendingStopAt > 0L) return null // stop already scheduled
            if (delayMillis == 0L) stop(current, now)
            else current.copy(pendingStopAt = now + delayMillis)
        }
    }

    /** Cancelling a scheduled stop — always allowed (strengthening). */
    fun cancelPendingStop(current: ShieldState): ShieldState =
        current.copy(pendingStopAt = 0L)

    /**
     * Finalises a due scheduled stop. Returns the stopped state (accounted
     * at the scheduled instant, not `now`), or null when nothing is due.
     */
    fun completeIfDue(current: ShieldState, now: Long): ShieldState? =
        if (current.active && current.pendingStopAt in 1..now) {
            stop(current, current.pendingStopAt)
        } else {
            null
        }

    private fun stop(current: ShieldState, at: Long): ShieldState = current.copy(
        active = false,
        pendingStopAt = 0L,
        controlSeconds = current.controlSeconds +
            ((at - current.since) / 1000).coerceAtLeast(0),
    )
}
