package com.orchestrator.mcp.usermanagement.model

import kotlinx.serialization.Serializable

/** Overall approval status for a document. */
@Serializable
data class ApprovalStatus(
    val ticketKey: String,
    val documentType: DocumentType,
    val overallStatus: String,
    val approvals: List<ApprovalLogEntry>,
    val pendingRoles: List<UserRole>
)

/** Result of an approval/rejection operation. */
@Serializable
data class ApprovalResult(
    val success: Boolean,
    val message: String,
    val approvalId: String? = null,
    val jiraSynced: Boolean = false
)

/** Pending approval item for list_pending_approvals tool. */
@Serializable
data class PendingApproval(
    val ticketKey: String,
    val documentType: DocumentType,
    val version: Int,
    val attachedAt: String,
    val attachedBy: String? = null
)
