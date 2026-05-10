package com.orchestrator.mcp.usermanagement.service

import org.slf4j.LoggerFactory
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption service for Jira API tokens.
 * Key sourced from environment variable.
 */
interface TokenEncryptionService {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

class TokenEncryptionServiceImpl(
    private val encryptionKeyEnv: String
) : TokenEncryptionService {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val GCM_TAG_LENGTH = 128
    private val GCM_IV_LENGTH = 12

    private val secretKey: SecretKeySpec by lazy {
        val keyBase64 = System.getenv(encryptionKeyEnv)
            ?: throw IllegalStateException("Encryption key env '$encryptionKeyEnv' not set")
        val keyBytes = Base64.getDecoder().decode(keyBase64)
        require(keyBytes.size == 32) { "Encryption key must be 32 bytes (256 bits)" }
        SecretKeySpec(keyBytes, "AES")
    }

    override fun encrypt(plaintext: String): String {
        val iv = ByteArray(GCM_IV_LENGTH).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.getEncoder().encodeToString(combined)
    }

    override fun decrypt(ciphertext: String): String {
        val combined = Base64.getDecoder().decode(ciphertext)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
}
