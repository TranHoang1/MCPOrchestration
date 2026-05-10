package com.orchestrator.mcp.brmasking.crypto

import com.orchestrator.mcp.brmasking.model.BrMaskingException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption/decryption service for Business Rules.
 * Each BR gets a unique IV for security.
 */
class BrEncryptionService(keyBase64: String) {

    private val secretKey: SecretKey = decodeKey(keyBase64)
    private val secureRandom = SecureRandom()

    /**
     * Encrypts plaintext BR content.
     * @return Pair of (ciphertext Base64, IV Base64)
     */
    fun encrypt(plaintext: String): Pair<String, String> {
        val iv = generateIv()
        val cipher = createCipher(Cipher.ENCRYPT_MODE, iv)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(ciphertext) to
            Base64.getEncoder().encodeToString(iv)
    }

    /**
     * Decrypts ciphertext back to original BR content.
     */
    fun decrypt(ciphertextB64: String, ivB64: String): String {
        return try {
            val iv = Base64.getDecoder().decode(ivB64)
            val cipher = createCipher(Cipher.DECRYPT_MODE, iv)
            val plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertextB64))
            String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            throw BrMaskingException.DecryptionException("Decryption failed", e)
        }
    }

    private fun createCipher(mode: Int, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(mode, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher
    }

    private fun generateIv(): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12
        private const val KEY_LENGTH_BYTES = 32

        private fun decodeKey(base64Key: String): SecretKey {
            if (base64Key.isBlank()) {
                throw BrMaskingException.InvalidKeyException(
                    "BR_ENCRYPTION_KEY not configured"
                )
            }
            val keyBytes = Base64.getDecoder().decode(base64Key)
            require(keyBytes.size == KEY_LENGTH_BYTES) {
                "BR encryption key must be $KEY_LENGTH_BYTES bytes"
            }
            return SecretKeySpec(keyBytes, "AES")
        }
    }
}
