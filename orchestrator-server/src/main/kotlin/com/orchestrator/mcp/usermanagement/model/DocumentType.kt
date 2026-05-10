package com.orchestrator.mcp.usermanagement.model

import kotlinx.serialization.Serializable

/**
 * Document types that can be approved through the approval workflow.
 */
@Serializable
enum class DocumentType(val filePrefix: String) {
    BRD("BRD"),
    FSD("FSD"),
    TDD("TDD"),
    STP_STC("STP"),
    DPG("DPG"),
    UG("UG");

    companion object {
        fun fromString(value: String): DocumentType =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Invalid document type '$value'. Valid: ${entries.joinToString { it.name }}"
                )
    }
}
