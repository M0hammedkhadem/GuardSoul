package com.agon.app.utils

import com.agon.app.data.settings.EncryptedPrefs
import java.security.MessageDigest

object SecurityUtils {
    /**
     * Hashes the PIN using SHA-256.
     * Note: PIN hash is stored via EncryptedSharedPreferences (EncryptedPrefs).
     */
    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, encryptedPrefs: EncryptedPrefs): Boolean {
        val stored = encryptedPrefs.getPinHash()
        if (stored.isBlank()) return false
        return hashPin(pin) == stored
    }
}
