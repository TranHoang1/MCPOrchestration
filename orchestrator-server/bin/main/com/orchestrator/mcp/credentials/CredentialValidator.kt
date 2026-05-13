package com.orchestrator.mcp.credentials

import com.orchestrator.mcp.credentials.model.CredentialException
import com.orchestrator.mcp.credentials.model.CredentialSchemaField

/**
 * Validation logic for user credential values.
 * Extracted from UserCredentialServiceImpl to respect SRP and line limits.
 */

private val URL_PATTERN = Regex("^https?://[^\\s]+$")
private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private const val MAX_PAYLOAD_BYTES = 10 * 1024

/** Validate payload size does not exceed 10KB. */
fun validatePayloadSize(credentials: Map<String, String>) {
    val totalSize = credentials.entries.sumOf { it.key.length + it.value.length }
    if (totalSize > MAX_PAYLOAD_BYTES) {
        throw CredentialException.PayloadTooLargeException(totalSize, MAX_PAYLOAD_BYTES)
    }
}

/** Validate credential values against schema field types. */
fun validateCredentialValues(fields: List<CredentialSchemaField>, credentials: Map<String, String>) {
    for ((key, value) in credentials) {
        if (value.isBlank()) continue
        val field = fields.find { it.field_key == key } ?: continue
        validateFieldValue(field, value)
    }
}

/** Validate a single field value based on its type. */
private fun validateFieldValue(field: CredentialSchemaField, value: String) {
    when (field.field_type) {
        "url" -> {
            if (!URL_PATTERN.matches(value)) {
                throw CredentialException.InvalidUrlFormatException(field.field_key)
            }
        }
        "email" -> {
            if (!EMAIL_PATTERN.matches(value)) {
                throw CredentialException.InvalidEmailFormatException(field.field_key)
            }
        }
    }
}
