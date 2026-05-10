package com.orchestrator.mcp.usermanagement.model

import kotlinx.serialization.Serializable

/** Approval decision: approve or reject a document. */
@Serializable
enum class ApprovalDecision {
    APPROVE,
    REJECT;

    companion object {
        fun fromString(value: String): ApprovalDecision =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Invalid decision '$value'. Valid: approve, reject"
                )
    }
}
