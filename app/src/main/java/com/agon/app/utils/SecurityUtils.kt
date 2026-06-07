package com.agon.app.utils

import android.util.Base64
import com.agon.app.data.settings.EncryptedPrefs
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SecurityUtils {
    /**
     * OWASP Password Storage Cheat Sheet (2023) recommends
     *   600 000 iterations
     * for PBKDF2-HMAC-SHA256. The previous default of 10 000
     * is well below that bar; a modern GPU can brute-force a
     * 6-digit PIN hashed with 10 k iterations in under a second.
     *
     * Hashes are tagged with the iteration count they were
     * produced with (`pbkdf2$<iter>$...`) so existing PINs
     * hashed with the legacy 10 k default are still verifiable
     * (with the old work factor) on next login — and can then
     * be transparently re-hashed with the new default.
     */
    const val PBKDF2_ITERATIONS_CURRENT = 600_000
    private const val PBKDF2_ITERATIONS_LEGACY = 10_000
    private const val PBKDF2_KEY_LENGTH = 256
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SALT_LENGTH = 16
    private const val PREFIX_PBKDF2 = "pbkdf2$"
    private const val PREFIX_SHA256 = "sha256$"

    private val secureRandom = SecureRandom()

    /**
     * Hashes the PIN using PBKDF2WithHmacSHA256
     * (600 000 iterations, 256-bit) with a fresh 128-bit salt.
     * Output format: "pbkdf2$<iter>$base64(salt)$base64(hash)".
     * Legacy SHA-256 hashes ("sha256$...") are still produced
     * and verified for backward compatibility with PINs stored
     * before this upgrade.
     */
    fun hashPin(pin: String): String {
        val salt = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS_CURRENT, PBKDF2_KEY_LENGTH)
        val key = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        return PREFIX_PBKDF2 +
            PBKDF2_ITERATIONS_CURRENT + "$" +
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
     * Supports both PBKDF2 ("pbkdf2$<iter>$..." — iter is read
     * from the tag) and legacy SHA-256 ("sha256$...") formats,
     * and also the bare hex SHA-256 hashes used before the
     * iter-tagged PBKDF2 upgrade.
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
                // New format: "pbkdf2$<iter>$<salt>$<hash>" (4 parts
                // after the prefix). Old format: "pbkdf2$<salt>$<hash>"
                // (3 parts after the prefix) — assume the legacy
                // 10 k iteration count.
                val (iter, salt, expected) = when (parts.size) {
                    3 -> Triple(PBKDF2_ITERATIONS_LEGACY, parts[0], parts[1])
                    4 -> {
                        val it = parts[0].toIntOrNull() ?: return false
                        Triple(it, parts[1], parts[2])
                    }
                    else -> return false
                }
                val saltBytes = try {
                    Base64.decode(salt, Base64.NO_WRAP)
                } catch (_: Exception) {
                    return false
                }
                val expectedBytes = try {
                    Base64.decode(expected, Base64.NO_WRAP)
                } catch (_: Exception) {
                    return false
                }
                val spec = PBEKeySpec(pin.toCharArray(), saltBytes, iter, PBKDF2_KEY_LENGTH)
                val key = try {
                    SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
                } catch (_: Exception) {
                    return false
                }
                MessageDigest.isEqual(key, expectedBytes)
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
