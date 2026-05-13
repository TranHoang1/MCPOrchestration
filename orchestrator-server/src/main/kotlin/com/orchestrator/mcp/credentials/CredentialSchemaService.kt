package com.orchestrator.mcp.credentials

import com.orchestrator.mcp.credentials.model.CredentialSchemaField
import com.orchestrator.mcp.credentials.model.CredentialSchemaResponse
import com.orchestrator.mcp.credentials.model.DeleteFieldResponse
import com.orchestrator.mcp.credentials.model.SchemaListItem

/**
 * Service interface for credential schema CRUD operations.
 * Admin-only: defines what credentials each MCP server requires.
 */
interface CredentialSchemaService {

    /** List all schemas with summary info (field count, user count). */
    suspend fun listSchemas(): List<SchemaListItem>

    /** Get full schema detail for a specific server. */
    suspend fun getSchema(serverName: String): CredentialSchemaResponse

    /** Create or replace all fields for a server schema. */
    suspend fun saveSchema(serverName: String, fields: List<CredentialSchemaField>)

    /** Delete a single field from a server schema. */
    suspend fun deleteField(serverName: String, fieldKey: String, confirm: Boolean): DeleteFieldResponse
}
