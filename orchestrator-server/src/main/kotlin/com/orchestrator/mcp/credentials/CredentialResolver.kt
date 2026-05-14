package com.orchestrator.mcp.credentials

import com.orchestrator.mcp.credentials.model.ResolvedConfig

/**
 * Interface for resolving credential placeholders in server configurations.
 * Replaces {placeholder} patterns with user's decrypted credential values.
 */
interface CredentialResolver {

    /**
     * Resolve placeholders in server config using user's stored credentials.
     * @param userId The authenticated user's ID
     * @param serverName The upstream server name
     * @param command The command template (may contain {placeholders})
     * @param args The args list (each may contain {placeholders})
     * @param env The env map (values may contain {placeholders})
     * @return ResolvedConfig with substituted values and computed poolKey
     */
    suspend fun resolve(
        userId: String,
        serverName: String,
        command: String,
        args: List<String>,
        env: Map<String, String>
    ): ResolvedConfig

    /**
     * Check if a config has any credential placeholders.
     * @return true if any {placeholder} patterns found
     */
    fun hasPlaceholders(command: String, args: List<String>, env: Map<String, String>): Boolean

    /**
     * Get decrypted credentials for a user and server.
     * @return Map of field_key to plaintext value, or null if none stored
     */
    suspend fun getDecryptedCredentials(userId: String, serverName: String): Map<String, String>?

    /**
     * Get first available credentials for a server (any user who has them configured).
     * Used by system-level operations (sync) that don't have a specific user context.
     * @return Map of field_key to plaintext value, or null if no user has credentials
     */
    suspend fun getFirstAvailableCredentials(serverName: String): Map<String, String>?
}
