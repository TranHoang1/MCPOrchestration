package com.orchestrator.mcp.credentials

import com.orchestrator.mcp.credentials.model.*
import org.slf4j.LoggerFactory

/**
 * Implementation of CredentialSchemaService.
 * Validates field keys and delegates to repository.
 */
class CredentialSchemaServiceImpl(
    private val repository: CredentialSchemaRepository
) : CredentialSchemaService {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val fieldKeyPattern = Regex("^[a-z][a-z0-9_]{0,49}$")

    override suspend fun listSchemas(): List<SchemaListItem> {
        return repository.listSchemas()
    }

    override suspend fun getSchema(serverName: String): CredentialSchemaResponse {
        val fields = repository.getByServerName(serverName)
        if (fields.isEmpty()) {
            throw CredentialException.SchemaNotFoundException(serverName)
        }
        return buildSchemaResponse(serverName, fields)
    }

    override suspend fun saveSchema(serverName: String, fields: List<CredentialSchemaField>) {
        validateFields(serverName, fields)
        repository.saveSchema(serverName, fields)
        logger.info("Schema saved for server '{}': {} fields", serverName, fields.size)
    }

    override suspend fun deleteField(
        serverName: String,
        fieldKey: String,
        confirm: Boolean
    ): DeleteFieldResponse {
        return handleFieldDeletion(serverName, fieldKey, confirm)
    }

    private fun validateFields(serverName: String, fields: List<CredentialSchemaField>) {
        val seenKeys = mutableSetOf<String>()
        for (field in fields) {
            validateFieldKey(field.field_key)
            validateFieldType(field.field_type)
            if (!seenKeys.add(field.field_key)) {
                throw CredentialException.DuplicateFieldKeyException(serverName, field.field_key)
            }
        }
    }

    private fun validateFieldKey(key: String) {
        if (!fieldKeyPattern.matches(key)) {
            throw CredentialException.InvalidFieldKeyException(key)
        }
    }

    private fun validateFieldType(type: String) {
        FieldType.fromString(type)
    }

    private suspend fun handleFieldDeletion(
        serverName: String,
        fieldKey: String,
        confirm: Boolean
    ): DeleteFieldResponse {
        if (!repository.fieldExists(serverName, fieldKey)) {
            throw CredentialException.SchemaNotFoundException(serverName)
        }
        val affectedUsers = repository.countUsersWithData(serverName)
        if (affectedUsers > 0 && !confirm) {
            throw CredentialException.FieldHasUserDataException(serverName, fieldKey, affectedUsers)
        }
        val deleted = repository.deleteField(serverName, fieldKey)
        return DeleteFieldResponse(
            deleted = deleted,
            affected_users = affectedUsers,
            message = "Field '$fieldKey' deleted from '$serverName'"
        )
    }

    private fun buildSchemaResponse(
        serverName: String,
        fields: List<CredentialSchemaField>
    ): CredentialSchemaResponse {
        val sorted = fields.sortedBy { it.display_order }
        val createdAt = sorted.firstOrNull()?.id?.let { "N/A" }
        return CredentialSchemaResponse(
            server_name = serverName,
            fields = sorted,
            created_at = createdAt,
            updated_at = createdAt
        )
    }
}
