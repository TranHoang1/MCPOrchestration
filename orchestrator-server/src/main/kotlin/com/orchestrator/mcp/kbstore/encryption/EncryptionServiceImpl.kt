package com.orchestrator.mcp.kbstore.encryption

import com.orchestrator.mcp.kbstore.model.KbStoreException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption implementation.
 *
 * Format: [12-byte IV][ciphertext + 16-byte GCM tag]
 * Key: 32-byte (256-bit) provided as Base64-encoded string.
 */
class EncryptionServiceImpl(
    base64Key: String
) : EncryptionService {

    private val secretKey: SecretKeySpec
    private val secureRandom = SecureRandom()

    init {
        val keyBytes = decodeKey(base64Key)
        secretKey = SecretKeySpec(keyBytes, ALGORITHM)
    }

    override fun encrypt(plaintext: String): ByteArray {
        return try {
            val iv = generateIv()
            val cipher = createCipher(Cipher.ENCRYPT_MODE, iv)
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            iv + ciphertext
        } catch (e: Exception) {
            throw KbStoreException.EncryptionException("Encryption failed", e)
        }
    }

    override fun decrypt(ciphertext: ByteArray): String {
        return try {
            require(ciphertext.size > IV_LENGTH) { "Ciphertext too short" }
            val iv = ciphertext.copyOfRange(0, IV_LENGTH)
            val encrypted = ciphertext.copyOfRange(IV_LENGTH, ciphertext.size)
            val cipher = createCipher(Cipher.DECRYPT_MODE, iv)
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            throw KbStoreException.EncryptionException("Decryption failed", e)
        }
    }

    private fun generateIv(): ByteArray =
        ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }

    private fun createCipher(mode: Int, iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }

    private fun decodeKey(base64Key: String): ByteArray {
        if (base64Key.isBlank()) {
            throw KbStoreException.ConfigException(
                "Encryption key is empty. Set KB_ENCRYPTION_KEY env var."
            )
        }
        val keyBytes = Base64.getDecoder().decode(base64Key)
        if (keyBytes.size != KEY_LENGTH) {
            throw KbStoreException.ConfigException(
                "Encryption key must be $KEY_LENGTH bytes, got ${keyBytes.size}"
            )
        }
        return keyBytes
    }

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val KEY_LENGTH = 32
    }
}
