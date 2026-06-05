package com.agon.app.utils

import com.agon.app.data.settings.AppSettings
import kotlinx.coroutines.flow.first

/**
 * Bonus-time release valve. Mirrors Family Link's "5 more minutes"
 * pattern: when a schedule window blocks the user from opening an
 * app, they can grant themselves a small buffer of extra time (the
 * cap is configurable, default 30 minutes/day). The cap is global,
 * not per-window — once the user burns their bonus, it stays
 * burned until the next day.
 */
object BonusTime {

    const val DEFAULT_GRANT_MINUTES: Int = 5
    const val DEFAULT_CAP_MINUTES: Int = 30

    suspend fun capMinutes(settings: AppSettings): Int =
        settings.bonusTimeCapMinutesFlow.first().coerceAtLeast(0)

    /**
     * Returns the number of bonus minutes the user has *remaining*
     * (i.e. unused). 0 means the user has spent their daily bonus.
     */
    suspend fun remainingMinutes(settings: AppSettings): Int {
        val cap = capMinutes(settings)
        val spent = settings.bonusTimeRemainingMsFlow.first().coerceAtLeast(0L) / 60_000L
        return (cap - spent.toInt()).coerceAtLeast(0)
    }

    suspend fun canGrant(settings: AppSettings, minutes: Int = DEFAULT_GRANT_MINUTES): Boolean {
        val cap = capMinutes(settings)
        if (cap == 0) return false
        val spent = settings.bonusTimeRemainingMsFlow.first().coerceAtLeast(0L) / 60_000L
        return (spent + minutes) <= cap
    }

    /**
     * Spend [minutes] of bonus time. The block-screen decrements
     * this once per minute while the user is in the bonus window.
     */
    suspend fun spend(settings: AppSettings, minutes: Int = 1) {
        val current = settings.bonusTimeRemainingMsFlow.first().coerceAtLeast(0L)
        settings.setBonusTimeRemainingMs(current + minutes * 60_000L)
    }

    suspend fun reset(settings: AppSettings) {
        settings.setBonusTimeRemainingMs(0L)
        settings.setBonusTimeGrantedAt(System.currentTimeMillis())
    }
}
