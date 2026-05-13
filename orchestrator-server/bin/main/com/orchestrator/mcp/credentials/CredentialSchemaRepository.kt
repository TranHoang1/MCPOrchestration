package com.orchestrator.mcp.credentials

import com.orchestrator.mcp.credentials.model.CredentialSchemaField
import com.orchestrator.mcp.credentials.model.SchemaListItem

/**
 * Repository interface for credential_schemas table operations.
 */
interface CredentialSchemaRepository {

    /** List all schemas grouped by server_name with summary info. */
    suspend fun listSchemas(): List<SchemaListItem>

    /** Get all fields for a specific server. */
    suspend fun getByServerName(serverName: String): List<CredentialSchemaField>

    /** Save (upsert) all fields for a server. Replaces existing fields. */
    suspend fun saveSchema(serverName: String, fields: List<CredentialSchemaField>)

    /** Delete a single field by server_name + field_key. */
    suspend fun deleteField(serverName: String, fieldKey: String): Boolean

    /** Count users who have credentials for a given server. */
    suspend fun countUsersWithData(serverName: String): Int

    /** Check if a specific field_key exists for a server. */
    suspend fun fieldExists(serverName: String, fieldKey: String): Boolean
}
