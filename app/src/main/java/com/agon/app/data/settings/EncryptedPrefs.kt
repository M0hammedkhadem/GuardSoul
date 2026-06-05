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
        // Initial value
        trySend(getPinHash())
        awaitClose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun clear() {
        sharedPrefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_PIN_HASH = "pin_hash_secure"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
        private const val KEY_STRONG_PROTECTION = "strong_protection"
    }
}
