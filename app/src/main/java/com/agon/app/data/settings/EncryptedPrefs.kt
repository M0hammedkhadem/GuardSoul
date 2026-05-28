package com.agon.app.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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

    fun getPinHash(): String? {
        return sharedPrefs.getString(KEY_PIN_HASH, null)
    }

    fun hasPin(): Boolean {
        return !getPinHash().isNullOrBlank()
    }

    fun clear() {
        sharedPrefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_PIN_HASH = "pin_hash_secure"
    }
}
