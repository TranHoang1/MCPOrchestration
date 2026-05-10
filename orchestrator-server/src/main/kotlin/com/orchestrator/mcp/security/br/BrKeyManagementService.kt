package com.orchestrator.mcp.security.br

import com.orchestrator.mcp.security.br.model.KeyMetadata

/**
 * Key Management Service for BR encryption.
 * Supports key rotation: new key for encryption, old keys for decryption.
 */
interface BrKeyManagementService {

    /** Get the currently active key ID for new encryptions. */
    fun getActiveKeyId(): String

    /** Encrypt plaintext with the active key. Returns "keyId:iv:ciphertext". */
    fun encrypt(plaintext: String): String

    /** Decrypt payload using the key identified by keyId. Returns null on failure. */
    fun decrypt(encryptedPayload: String, keyId: String): String?

    /** Register a new key for rotation. */
    fun registerKey(keyId: String, keyBase64: String): Boolean

    /** Get metadata for a specific key. */
    fun getKeyMetadata(keyId: String): KeyMetadata?
}
