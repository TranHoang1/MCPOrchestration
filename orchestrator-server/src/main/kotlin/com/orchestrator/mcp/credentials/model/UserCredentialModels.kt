package com.orchestrator.mcp.credentials.model

import kotlinx.serialization.Serializable

/** Response for GET /api/credentials/servers — list with completion status. */
@Serializable
data class ServerCredentialStatus(
    val server_name: String,
    val required_fields: Int,
    val filled_fields: Int,
    val is_complete: Boolean
)

/** Wrapper response for server list. */
@Serializable
data class ServerListResponse(val servers: List<ServerCredentialStatus>)

/** Single field with schema info + masked value for display. */
@Serializable
data class CredentialFormField(
    val field_key: String,
    val field_label: String,
    val field_type: String,
    val field_required: Boolean,
    val field_description: String? = null,
    val field_placeholder: String? = null,
    val masked_value: String? = null,
    val has_value: Boolean = false
)

/** Response for GET /api/credentials/{serverName} — form schema + masked values. */
@Serializable
data class CredentialFormResponse(
    val server_name: String,
    val fields: List<CredentialFormField>,
    val is_complete: Boolean
)

/** Request body for PUT /api/credentials/{serverName}. */
@Serializable
data class SaveCredentialsRequest(val credentials: Map<String, String>)

/** Generic success response. */
@Serializable
data class CredentialActionResponse(
    val success: Boolean,
    val message: String
)
