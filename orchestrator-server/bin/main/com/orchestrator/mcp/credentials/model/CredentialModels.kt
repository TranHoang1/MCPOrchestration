package com.orchestrator.mcp.credentials.model

import kotlinx.serialization.Serializable

/** Field type enum for credential schema definitions. */
enum class FieldType {
    url, email, secret, text, number;

    companion object {
        fun fromString(value: String): FieldType =
            entries.find { it.name == value }
                ?: throw InvalidFieldTypeException(value)
    }
}

/** Single field definition within a credential schema. */
@Serializable
data class CredentialSchemaField(
    val id: String? = null,
    val field_key: String,
    val field_label: String,
    val field_type: String,
    val field_required: Boolean = true,
    val field_description: String? = null,
    val field_placeholder: String? = null,
    val display_order: Int = 0
)

/** Response for GET /api/admin/credential-schemas (list). */
@Serializable
data class SchemaListItem(
    val server_name: String,
    val field_count: Int,
    val users_configured: Int,
    val updated_at: String?
)

/** Response for GET /api/admin/credential-schemas (wrapper). */
@Serializable
data class SchemaListResponse(val schemas: List<SchemaListItem>)

/** Response for GET /api/admin/credential-schemas/{serverName}. */
@Serializable
data class CredentialSchemaResponse(
    val server_name: String,
    val fields: List<CredentialSchemaField>,
    val created_at: String? = null,
    val updated_at: String? = null
)

/** Request body for PUT /api/admin/credential-schemas/{serverName}. */
@Serializable
data class SaveSchemaRequest(val fields: List<CredentialSchemaField>)

/** Response for DELETE with affected users. */
@Serializable
data class DeleteFieldResponse(
    val deleted: Boolean,
    val affected_users: Int = 0,
    val message: String
)
