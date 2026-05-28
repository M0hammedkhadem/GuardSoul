package com.agon.app.utils

import java.security.MessageDigest

object SecurityUtils {
    /**
     * Hashes the PIN using SHA-256. 
     * Note: For production, consider using Argon2 or PBKDF2 with salt.
     */
    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
