package com.orchestrator.mcp.usermanagement.model

import kotlinx.serialization.Serializable

/** Approval log entry — records an approval/rejection action. */
@Serializable
data class ApprovalLogEntry(
    val id: String,
    val ticketKey: String,
    val documentType: DocumentType,
    val documentVersion: Int,
    val userId: String,
    val decision: ApprovalDecision,
    val comment: String? = null,
    val jiraSynced: Boolean = false,
    val createdAt: String
)
