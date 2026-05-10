package com.orchestrator.mcp.usermanagement.model

import kotlinx.serialization.Serializable

/** Permission matrix entry — maps role + document type to permissions. */
@Serializable
data class RolePermission(
    val role: UserRole,
    val documentType: DocumentType,
    val canView: Boolean = true,
    val canApprove: Boolean = false
)
