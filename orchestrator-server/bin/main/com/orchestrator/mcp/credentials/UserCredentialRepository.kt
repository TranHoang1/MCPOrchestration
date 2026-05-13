package com.orchestrator.mcp.credentials

/**
 * Repository interface for user_credentials table operations.
 * Stores encrypted credential blobs per user per server.
 */
interface UserCredentialRepository {

    /** Get encrypted credentials JSON for a user+server. Returns null if not set. */
    suspend fun getEncrypted(userId: String, serverName: String): String?

    /** Save (upsert) encrypted credentials JSON for a user+server. */
    suspend fun save(userId: String, serverName: String, encryptedJson: String)

    /** Delete all credentials for a user+server. Returns true if row existed. */
    suspend fun delete(userId: String, serverName: String): Boolean

    /** List all server names where user has stored credentials. */
    suspend fun listServerNames(userId: String): List<String>

    /** Count filled fields for a user+server (requires decryption externally). */
    suspend fun exists(userId: String, serverName: String): Boolean
}
