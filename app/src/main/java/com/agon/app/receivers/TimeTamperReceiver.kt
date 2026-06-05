package com.agon.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.agon.app.guardianApp
import com.agon.app.utils.AppLogger
import kotlinx.coroutines.launch

/**
 * Detects tampering with the device's clock. The user (or a malicious
 * script) might try to set the clock backwards to bypass the
 * deactivation-delay countdown, or forwards to fast-forward a
 * school-time / bedtime window. Qustodio and Bark both treat any
 * time jump > 5 min as a tamper event.
 *
 * Strategy:
 *  1. Persist `(wallClockMs, monotonicMs)` on every event.
 *  2. On the next event compute `expected = previousMonotonic + (now - previousWallClock)`.
 *     - If the monotonic component is smaller than expected but wall-clock went
 *       forward, the user *manually* moved time backwards. (Tamper.)
 *     - If the gap between expected and actual is larger than [MAX_DRIFT_MS]
 *       (e.g. 5 minutes) the user jumped the clock forward. (Tamper.)
 *  3. Record a tamper alert and re-anchor the last-known pair.
 *
 * `monotonicMs` comes from `SystemClock.elapsedRealtime()` — it never
 * moves backwards even when the user changes the clock.
 */
class TimeTamperReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TimeTamperReceiver"
        private const val PREFS_NAME = "time_tamper"
        private const val KEY_LAST_WALL = "last_wall_ms"
        private const val KEY_LAST_MONO = "last_mono_ms"

        /** Allowed drift before we flag a tamper event. 5 min matches Qustodio. */
        private const val MAX_DRIFT_MS = 5L * 60L * 1_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> Unit
            else -> return
        }

        val app = context.applicationContext.guardianApp() ?: return
        val pendingResult = goAsync()

        app.applicationScope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val nowWall = System.currentTimeMillis()
                val nowMono = android.os.SystemClock.elapsedRealtime()
                val lastWall = prefs.getLong(KEY_LAST_WALL, 0L)
                val lastMono = prefs.getLong(KEY_LAST_MONO, 0L)

                if (lastWall == 0L || lastMono == 0L) {
                    // First observation — just anchor.
                    prefs.edit()
                        .putLong(KEY_LAST_WALL, nowWall)
                        .putLong(KEY_LAST_MONO, nowMono)
                        .apply()
                    return@launch
                }

                val wallDelta = nowWall - lastWall          // can be negative
                val monoDelta = nowMono - lastMono          // always >= 0
                val drift = wallDelta - monoDelta           // positive = user jumped forward
                val backward = wallDelta < 0L
                val forwardJump = drift > MAX_DRIFT_MS

                AppLogger.d(
                    TAG,
                    "time change observed: wallΔ=${wallDelta}ms monoΔ=${monoDelta}ms drift=${drift}ms"
                )

                if (backward || forwardJump) {
                    val reason = if (backward) "clock_set_backward" else "clock_jumped_forward"
                    val detail = buildString {
                        append("Clock changed: wallΔ=").append(wallDelta).append("ms, drift=")
                            .append(drift).append("ms")
                        if (forwardJump) {
                            val jumpedBy = drift / 1_000L
                            append(" (jumped ${jumpedBy}s forward)")
                        }
                    }
                    try {
                        val shieldActive = app.repository.getAppSettings().isShieldActive()
                        if (shieldActive) {
                            app.repository.recordTamperAlert(reason, detail)
                        }
                    } catch (t: Throwable) {
                        AppLogger.w(TAG, "Failed to record tamper alert", t)
                    }
                }

                // Re-anchor to the latest reading.
                prefs.edit()
                    .putLong(KEY_LAST_WALL, nowWall)
                    .putLong(KEY_LAST_MONO, nowMono)
                    .apply()
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Time tamper check failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
