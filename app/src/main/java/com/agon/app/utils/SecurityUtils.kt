package com.agon.app.utils

import android.util.Base64
import com.agon.app.data.settings.EncryptedPrefs
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SecurityUtils {
    private const val PBKDF2_ITERATIONS = 10000
    private const val PBKDF2_KEY_LENGTH = 256
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SALT_LENGTH = 16
    private const val PREFIX_PBKDF2 = "pbkdf2$"
    private const val PREFIX_SHA256 = "sha256$"

    private val secureRandom = SecureRandom()

    /**
     * Hashes the PIN using PBKDF2WithHmacSHA256 (10000 iterations, 256-bit).
     * Output format: "pbkdf2$base64(salt)$base64(hash)".
     * Legacy SHA-256 hashes ("sha256$...") are still produced and verified for
     * backward compatibility with PINs stored before this upgrade.
     */
    fun hashPin(pin: String): String {
        val salt = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val key = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        return PREFIX_PBKDF2 +
            Base64.encodeToString(salt, Base64.NO_WRAP) + "$" +
            Base64.encodeToString(key, Base64.NO_WRAP)
    }

    /**
     * Legacy SHA-256 hash (still useful for migrating old stored hashes and
     * for callers that explicitly need a single-shot hash).
     */
    fun hashPinLegacy(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray())
        return PREFIX_SHA256 + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Verifies the entered PIN against the stored hash.
     * Supports both PBKDF2 ("pbkdf2$...") and legacy SHA-256 ("sha256$...") formats,
     * and also the bare hex SHA-256 hashes used before this upgrade.
     */
    fun verifyPin(pin: String, encryptedPrefs: EncryptedPrefs): Boolean {
        val stored = encryptedPrefs.getPinHash()
        if (stored.isBlank()) return false
        return verifyPinAgainstHash(pin, stored)
    }

    /**
     * Verifies a PIN against a stored hash string. Public so callers that hold a
     * hash directly (e.g. tests) can use it.
     */
    fun verifyPinAgainstHash(pin: String, stored: String): Boolean {
        if (stored.isBlank()) return false
        return when {
            stored.startsWith(PREFIX_PBKDF2) -> {
                val parts = stored.removePrefix(PREFIX_PBKDF2).split("$")
                if (parts.size != 2) return false
                val salt = try {
                    Base64.decode(parts[0], Base64.NO_WRAP)
                } catch (_: Exception) {
                    return false
                }
                val expected = try {
                    Base64.decode(parts[1], Base64.NO_WRAP)
                } catch (_: Exception) {
                    return false
                }
                val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
                val key = try {
                    SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
                } catch (_: Exception) {
                    return false
                }
                MessageDigest.isEqual(key, expected)
            }
            stored.startsWith(PREFIX_SHA256) -> {
                val expected = try {
                    Base64.decode(stored.removePrefix(PREFIX_SHA256), Base64.NO_WRAP)
                } catch (_: Exception) {
                    return false
                }
                val actual = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
                MessageDigest.isEqual(actual, expected)
            }
            // Legacy bare hex SHA-256 (no prefix).
            else -> {
                val digest = MessageDigest.getInstance("SHA-256")
                val actual = digest.digest(pin.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                MessageDigest.isEqual(actual.toByteArray(Charsets.UTF_8), stored.toByteArray(Charsets.UTF_8))
            }
        }
    }
}
