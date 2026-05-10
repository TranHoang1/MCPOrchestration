package com.orchestrator.mcp.usermanagement.model

import kotlinx.serialization.Serializable

/** Request to create a new user account. */
@Serializable
data class CreateUserRequest(
    val email: String,
    val jiraToken: String,
    val role: UserRole,
    val displayName: String
)

/** Request to update an existing user account. */
@Serializable
data class UpdateUserRequest(
    val role: UserRole? = null,
    val displayName: String? = null,
    val jiraToken: String? = null
)

/** Filter criteria for listing users. */
data class UserFilter(
    val role: UserRole? = null,
    val active: Boolean? = null
)

/** Request to assign a project to a user. */
@Serializable
data class AssignProjectRequest(
    val projectKey: String
)

/** Request to update permissions for a role. */
@Serializable
data class PermissionUpdateRequest(
    val permissions: List<PermissionUpdate>
)

/** Single permission update entry. */
@Serializable
data class PermissionUpdate(
    val documentType: DocumentType,
    val canView: Boolean,
    val canApprove: Boolean
)

/** Request for approve_document MCP tool. */
@Serializable
data class ApprovalRequest(
    val ticketKey: String,
    val documentType: DocumentType,
    val decision: ApprovalDecision,
    val comment: String? = null,
    val userId: String? = null
)
