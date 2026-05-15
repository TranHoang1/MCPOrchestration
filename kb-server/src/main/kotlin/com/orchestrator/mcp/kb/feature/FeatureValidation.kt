package com.orchestrator.mcp.kb.feature

import com.orchestrator.mcp.kb.KbValidationException

/**
 * Shared validation logic for feature CRUD handlers.
 * All methods throw KbValidationException on invalid input.
 */
object FeatureValidation {

    private val PROJECT_KEY_PATTERN = Regex("^[A-Z][A-Z0-9_]+$")
    private val TICKET_KEY_PATTERN = Regex("^[A-Z]+-\\d+$")
    private const val MAX_NAME_LENGTH = 200
    private const val MAX_DESCRIPTION_LENGTH = 2000

    /** Validate and return project key. */
    fun validateProjectKey(value: String?): String {
        if (value.isNullOrBlank()) {
            throw KbValidationException("project_key is required")
        }
        if (!PROJECT_KEY_PATTERN.matches(value)) {
            throw KbValidationException("project_key must match ^[A-Z][A-Z0-9_]+\$")
        }
        return value
    }

    /** Validate and return feature name. */
    fun validateFeatureName(value: String?): String {
        if (value.isNullOrBlank()) {
            throw KbValidationException("name is required")
        }
        if (value.length > MAX_NAME_LENGTH) {
            throw KbValidationException("name must not exceed $MAX_NAME_LENGTH characters")
        }
        return value.trim()
    }

    /** Validate and return list of ticket keys. */
    fun validateTicketKeys(keys: List<String>?): List<String> {
        if (keys.isNullOrEmpty()) {
            throw KbValidationException("ticket_keys must not be empty")
        }
        return keys.map { validateTicketKey(it) }
    }

    /** Validate and return a single ticket key. */
    fun validateTicketKey(key: String?): String {
        if (key.isNullOrBlank()) {
            throw KbValidationException("ticket_key is required")
        }
        if (!TICKET_KEY_PATTERN.matches(key.trim())) {
            throw KbValidationException("ticket_key '$key' must match ^[A-Z]+-\\d+\$")
        }
        return key.trim()
    }

    /** Validate optional description (nullable). */
    fun validateDescription(value: String?): String? {
        if (value == null) return null
        if (value.length > MAX_DESCRIPTION_LENGTH) {
            throw KbValidationException("description must not exceed $MAX_DESCRIPTION_LENGTH characters")
        }
        return value
    }

    /** Validate and return feature ID. */
    fun validateFeatureId(value: String?): String {
        if (value.isNullOrBlank()) {
            throw KbValidationException("feature_id is required")
        }
        return value.trim()
    }
}
