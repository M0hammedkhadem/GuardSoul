package com.agon.app.data.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Field-level crypto for the journal `text` column.
 *
 * Uses androidx.security [MasterKey] (AES256-GCM, key material generated and
 * held inside the Android Keystore — never extractable to app memory) and
 * drives [Cipher] against the AndroidKeyStore directly. EncryptedFile is
 * deliberately NOT used: each journal entry is a single short string, not a
 * file.
 *
 * Wire format: "gsenc1:" + base64(iv) + ":" + base64(ciphertext)
 *
 * Backward compatibility: [decrypt] returns the input UNCHANGED when it does
 * not carry the format prefix (legacy plaintext rows from installs prior to
 * this hardening) or when decryption fails for any reason — the app must
 * never crash on old data. Such entries get encrypted on their next save.
 */
class JournalCrypto(context: Context) {

    companion object {
        private const val PREFIX = "gsenc1:"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }

    private val appContext = context.applicationContext

    /** Lazily ensures the master key exists, then loads it from the Keystore. */
    private val secretKey: SecretKey? by lazy {
        runCatching {
            // Building the MasterKey generates the AES256-GCM key inside the
            // Android Keystore (under the default alias) if it doesn't exist.
            MasterKey.Builder(appContext, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            ks.getKey(MasterKey.DEFAULT_MASTER_KEY_ALIAS, null) as? SecretKey
        }.getOrNull()
    }

    /**
     * Encrypts [plain]; on any failure (no keystore in edge environments)
     * returns the input unchanged so a save never loses user data.
     */
    fun encrypt(plain: String): String {
        val key = secretKey ?: return plain
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX +
                Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(ct, Base64.NO_WRAP)
        }.getOrDefault(plain)
    }

    /**
     * Decrypts [cipherText]. Legacy plaintext (no prefix) and any decryption
     * failure fall back to returning the input as-is (never crash on old data).
     */
    fun decrypt(cipherText: String): String {
        if (!cipherText.startsWith(PREFIX)) return cipherText // legacy plaintext
        val key = secretKey ?: return cipherText
        return runCatching {
            val parts = cipherText.removePrefix(PREFIX).split(":", limit = 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrDefault(cipherText)
    }
}
