package com.orchestrator.mcp.kbstore.encryption

/**
 * Service for encrypting and decrypting sensitive data.
 * Uses AES-256-GCM authenticated encryption.
 */
interface EncryptionService {

    /**
     * Encrypt plaintext string to ByteArray (IV prepended).
     * @param plaintext UTF-8 string to encrypt
     * @return ByteArray containing 12-byte IV + ciphertext + GCM tag
     */
    fun encrypt(plaintext: String): ByteArray

    /**
     * Decrypt ByteArray back to plaintext string.
     * @param ciphertext ByteArray with prepended 12-byte IV
     * @return Decrypted UTF-8 string
     */
    fun decrypt(ciphertext: ByteArray): String
}
