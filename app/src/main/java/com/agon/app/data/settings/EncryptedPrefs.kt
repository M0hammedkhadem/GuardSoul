package com.agon.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.channels.onFailure
import java.util.concurrent.CopyOnWriteArrayList

class EncryptedPrefs(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePinHash(hash: String) {
        sharedPrefs.edit().putString(KEY_PIN_HASH, hash).apply()
    }

    fun getPinHash(): String {
        return sharedPrefs.getString(KEY_PIN_HASH, "") ?: ""
    }

    fun hasPin(): Boolean {
        return getPinHash().isNotBlank()
    }

    fun setProtectionEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_PROTECTION_ENABLED, enabled).apply()
    }

    fun isProtectionEnabled(): Boolean = sharedPrefs.getBoolean(KEY_PROTECTION_ENABLED, false)

    fun setStrongProtection(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_STRONG_PROTECTION, enabled).apply()
    }

    fun isStrongProtection(): Boolean = sharedPrefs.getBoolean(KEY_STRONG_PROTECTION, false)

    // Issue #185: Enhanced callbackFlow to be more robust
    val pinHashFlow: Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_PIN_HASH) {
                trySend(getPinHash()).onFailure {
                    // If buffer is full, we can't do much in a sync listener,
                    // but usually it won't happen for a String flow.
                }
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        // Also keep a reference in the host's listener list so
        // clear() (and any other silent-broadcast write) can manually
        // re-fire this listener on OEMs that swallow the
        // OnSharedPreferenceChange callback.
        addPinHashListener(listener)
        // Initial value
        trySend(getPinHash())
        awaitClose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
            removePinHashListener(listener)
        }
    }

    fun clear() {
        sharedPrefs.edit().clear().apply()
        // EncryptedSharedPreferences (Jetpack Security) does not
        // fire OnSharedPreferenceChangeListener for `clear()` on
        // all OEMs — some treat it as a batch and emit nothing.
        // Manually notify the registered listener so the
        // pinHashFlow drops to "" immediately. The listener
        // is registered for KEY_PIN_HASH, which `clear()` does
        // delete, so re-emitting getPinHash() is the right call.
        notifyPinHashChangedExternally()
    }

    /**
     * Force-emit the current PIN-hash value to any active
     * `pinHashFlow` collectors. Used as a last-resort after
     * mutations that some OEM `EncryptedSharedPreferences`
     * implementations fail to broadcast (e.g. `clear()`).
     */
    private fun notifyPinHashChangedExternally() {
        for (listener in pinHashListeners) {
            try { listener.onSharedPreferenceChanged(sharedPrefs, KEY_PIN_HASH) }
            catch (_: Exception) {}
        }
    }

    private val pinHashListeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    /**
     * Public API for callers (or the `pinHashFlow` registration
     * path itself) to register a listener that can be re-fired
     * by [notifyPinHashChangedExternally]. Used to bridge the
     * `clear()` gap on OEMs that swallow the broadcast.
     */
    fun addPinHashListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        pinHashListeners.add(l)
    }

    fun removePinHashListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        pinHashListeners.remove(l)
    }

    // -----------------------------------------------------------------
    // PIN-RATE-LIMIT (Issue: NO-PIN-LOCKOUT)
    // -----------------------------------------------------------------
    // Persist the failed-attempt counter and the lockout-end timestamp
    // here so the lockout survives:
    //   - configuration changes (rotation, theme)
    //   - ViewModel recreation
    //   - process death (force-kill between attempts)
    //
    // Without persistence, an attacker force-killing the app between
    // wrong-PIN attempts would reset the counter and could brute-force
    // a 4-6 digit PIN at the speed of PBKDF2 (tens of ms per try).

    fun getPinFailCount(): Int =
        sharedPrefs.getInt(KEY_PIN_FAIL_COUNT, 0)

    fun setPinFailCount(count: Int) {
        sharedPrefs.edit().putInt(KEY_PIN_FAIL_COUNT, count).apply()
    }

    fun getPinLockoutUntilMs(): Long =
        sharedPrefs.getLong(KEY_PIN_LOCKOUT_UNTIL, 0L)

    fun setPinLockoutUntilMs(untilMs: Long) {
        sharedPrefs.edit().putLong(KEY_PIN_LOCKOUT_UNTIL, untilMs).apply()
    }

    /**
     * Convenience: returns the remaining lockout duration in
     * milliseconds (0 if not currently locked out).
     */
    fun pinLockoutRemainingMs(): Long {
        val until = getPinLockoutUntilMs()
        val remaining = until - System.currentTimeMillis()
        return remaining.coerceAtLeast(0L)
    }

    /**
     * Atomically clear the failure counter and the lockout timestamp.
     * Call this after a successful PIN verify.
     */
    fun clearPinFailureState() {
        sharedPrefs.edit()
            .remove(KEY_PIN_FAIL_COUNT)
            .remove(KEY_PIN_LOCKOUT_UNTIL)
            .apply()
    }

    companion object {
        private const val KEY_PIN_HASH = "pin_hash_secure"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
        private const val KEY_STRONG_PROTECTION = "strong_protection"
        private const val KEY_PIN_FAIL_COUNT = "pin_fail_count"
        private const val KEY_PIN_LOCKOUT_UNTIL = "pin_lockout_until_ms"
    }
}
