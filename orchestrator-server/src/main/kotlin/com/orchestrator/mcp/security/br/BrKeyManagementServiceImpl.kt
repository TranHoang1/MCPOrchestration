package com.orchestrator.mcp.security.br

import com.orchestrator.mcp.security.br.model.BrAccessConfig
import com.orchestrator.mcp.security.br.model.KeyMetadata
import com.orchestrator.mcp.security.br.model.KeyStatus
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * File-based KMS implementation for BR encryption.
 * Supports multiple keys for rotation (old keys retained for decryption).
 */
class BrKeyManagementServiceImpl(
    private val config: BrAccessConfig
) : BrKeyManagementService {

    private val logger = LoggerFactory.getLogger(BrKeyManagementServiceImpl::class.java)
    private val keys = ConcurrentHashMap<String, SecretKey>()
    private val metadata = ConcurrentHashMap<String, KeyMetadata>()
    private val secureRandom = SecureRandom()

    init {
        if (config.encryptionKeyBase64.isNotBlank()) {
            registerKey(config.activeKeyId, config.encryptionKeyBase64)
        }
    }

    override fun getActiveKeyId(): String = config.activeKeyId

    override fun encrypt(plaintext: String): String {
        val key = keys[config.activeKeyId]
            ?: throw IllegalStateException("Active key not found: ${config.activeKeyId}")
        val iv = generateIv()
        val cipher = createCipher(Cipher.ENCRYPT_MODE, key, iv)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.getEncoder().encodeToString(iv)
        val ctB64 = Base64.getEncoder().encodeToString(ciphertext)
        return "${config.activeKeyId}:$ivB64:$ctB64"
    }

    override fun decrypt(encryptedPayload: String, keyId: String): String? {
        return try {
            val parts = encryptedPayload.split(":", limit = 3)
            val (storedKeyId, ivB64, ctB64) = if (parts.size == 3) {
                Triple(parts[0], parts[1], parts[2])
            } else {
                Triple(keyId, parts[0], parts.getOrElse(1) { "" })
            }
            val key = keys[storedKeyId] ?: keys[keyId]
                ?: run { logger.error("Key not found: {}", storedKeyId); return null }
            val iv = Base64.getDecoder().decode(ivB64)
            val ciphertext = Base64.getDecoder().decode(ctB64)
            val cipher = createCipher(Cipher.DECRYPT_MODE, key, iv)
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            logger.error("Decryption failed for keyId={}: {}", keyId, e.message)
            null
        }
    }

    override fun registerKey(keyId: String, keyBase64: String): Boolean {
        return try {
            val keyBytes = Base64.getDecoder().decode(keyBase64)
            require(keyBytes.size == KEY_LENGTH_BYTES) { "Key must be $KEY_LENGTH_BYTES bytes" }
            keys[keyId] = SecretKeySpec(keyBytes, "AES")
            metadata[keyId] = KeyMetadata(
                keyId = keyId,
                createdAt = Clock.System.now(),
                expiresAt = null,
                status = KeyStatus.ACTIVE
            )
            logger.info("Registered encryption key: {}", keyId)
            true
        } catch (e: Exception) {
            logger.error("Failed to register key {}: {}", keyId, e.message)
            false
        }
    }

    override fun getKeyMetadata(keyId: String): KeyMetadata? = metadata[keyId]

    private fun createCipher(mode: Int, key: SecretKey, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(mode, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
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
    }
}
