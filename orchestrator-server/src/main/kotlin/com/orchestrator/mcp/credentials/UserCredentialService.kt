package com.orchestrator.mcp.credentials

import com.orchestrator.mcp.credentials.model.CredentialFormResponse
import com.orchestrator.mcp.credentials.model.ServerCredentialStatus

/**
 * Service interface for user credential CRUD operations.
 * Any authenticated user can manage their own credentials.
 */
interface UserCredentialService {

    /** List all servers with credential schema + user's completion status. */
    suspend fun listServers(userId: String): List<ServerCredentialStatus>

    /** Get credential form for a server: schema fields + masked current values. */
    suspend fun getCredentialForm(userId: String, serverName: String): CredentialFormResponse

    /** Save/update credentials for a server. Supports partial updates (merge). */
    suspend fun saveCredentials(userId: String, serverName: String, credentials: Map<String, String>)

    /** Delete all credentials for a server. */
    suspend fun deleteCredentials(userId: String, serverName: String): Boolean

    /** Get decrypted credentials map (internal use only — for CredentialResolver). */
    suspend fun getDecryptedCredentials(userId: String, serverName: String): Map<String, String>?

    /** Get first available credentials for a server from any user (for system sync). */
    suspend fun getFirstAvailableForServer(serverName: String): Map<String, String>?
}
