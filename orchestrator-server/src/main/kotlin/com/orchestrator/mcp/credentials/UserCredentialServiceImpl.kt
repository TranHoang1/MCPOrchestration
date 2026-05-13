package com.orchestrator.mcp.credentials

import com.orchestrator.mcp.credentials.model.*
import com.orchestrator.mcp.usermanagement.service.TokenEncryptionService
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Implementation of UserCredentialService.
 * Handles encryption, masking, merge logic, and validation.
 */
class UserCredentialServiceImpl(
    private val credentialRepo: UserCredentialRepository,
    private val schemaRepo: CredentialSchemaRepository,
    private val encryptionService: TokenEncryptionService
) : UserCredentialService {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val maxPayloadBytes = 10 * 1024

    override suspend fun listServers(userId: String): List<ServerCredentialStatus> {
        val schemas = schemaRepo.listSchemas()
        return schemas.map { buildServerStatus(userId, it) }
    }

    override suspend fun getCredentialForm(userId: String, serverName: String): CredentialFormResponse {
        val fields = schemaRepo.getByServerName(serverName)
        if (fields.isEmpty()) throw CredentialException.SchemaNotFoundException(serverName)
        val existing = getDecryptedCredentials(userId, serverName) ?: emptyMap()
        return buildFormResponse(serverName, fields, existing)
    }

    override suspend fun saveCredentials(userId: String, serverName: String, credentials: Map<String, String>) {
        validatePayloadSize(credentials)
        val fields = schemaRepo.getByServerName(serverName)
        if (fields.isEmpty()) throw CredentialException.SchemaNotFoundException(serverName)
        validateCredentialValues(fields, credentials)
        val merged = mergeWithExisting(userId, serverName, credentials)
        val encrypted = encryptCredentials(merged)
        credentialRepo.save(userId, serverName, encrypted)
        logger.info("Credentials saved for user={} server={}", userId, serverName)
    }

    override suspend fun deleteCredentials(userId: String, serverName: String): Boolean {
        return credentialRepo.delete(userId, serverName)
    }

    override suspend fun getDecryptedCredentials(userId: String, serverName: String): Map<String, String>? {
        val encrypted = credentialRepo.getEncrypted(userId, serverName) ?: return null
        return decryptCredentials(serverName, encrypted)
    }

    private suspend fun buildServerStatus(userId: String, schema: SchemaListItem): ServerCredentialStatus {
        val existing = getDecryptedCredentials(userId, schema.server_name)
        val filledCount = existing?.count { it.value.isNotBlank() } ?: 0
        return ServerCredentialStatus(
            server_name = schema.server_name,
            required_fields = schema.field_count,
            filled_fields = filledCount,
            is_complete = filledCount >= schema.field_count
        )
    }

    private fun buildFormResponse(
        serverName: String,
        fields: List<CredentialSchemaField>,
        existing: Map<String, String>
    ): CredentialFormResponse {
        val formFields = fields.sortedBy { it.display_order }.map { field ->
            val value = existing[field.field_key]
            CredentialFormField(
                field_key = field.field_key,
                field_label = field.field_label,
                field_type = field.field_type,
                field_required = field.field_required,
                field_description = field.field_description,
                field_placeholder = field.field_placeholder,
                masked_value = maskValue(field.field_type, value),
                has_value = !value.isNullOrBlank()
            )
        }
        val requiredKeys = fields.filter { it.field_required }.map { it.field_key }
        val isComplete = requiredKeys.all { existing[it]?.isNotBlank() == true }
        return CredentialFormResponse(server_name = serverName, fields = formFields, is_complete = isComplete)
    }

    private suspend fun mergeWithExisting(
        userId: String,
        serverName: String,
        newValues: Map<String, String>
    ): Map<String, String> {
        val existing = getDecryptedCredentials(userId, serverName) ?: emptyMap()
        return existing + newValues.filterValues { it.isNotBlank() }
    }

    private fun encryptCredentials(credentials: Map<String, String>): String {
        val plainJson = json.encodeToString(
            kotlinx.serialization.serializer<Map<String, String>>(), credentials
        )
        return encryptionService.encrypt(plainJson)
    }

    private fun decryptCredentials(serverName: String, encrypted: String): Map<String, String> {
        return try {
            val plainJson = encryptionService.decrypt(encrypted)
            json.decodeFromString<Map<String, String>>(plainJson)
        } catch (e: Exception) {
            logger.error("Failed to decrypt credentials for server={}", serverName, e)
            throw CredentialException.DecryptionException(serverName)
        }
    }

    companion object {
        /** Mask secret values: show "****" + last 4 chars. Non-secret fields show full value. */
        fun maskValue(fieldType: String, value: String?): String? {
            if (value.isNullOrBlank()) return null
            if (fieldType != "secret") return value
            return if (value.length <= 4) "****" else "****${value.takeLast(4)}"
        }
    }
}
