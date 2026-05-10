package com.orchestrator.mcp.usermanagement.service

import com.orchestrator.mcp.usermanagement.model.*
import com.orchestrator.mcp.usermanagement.repository.RolePermissionRepository
import com.orchestrator.mcp.usermanagement.repository.UserProjectRepository
import com.orchestrator.mcp.usermanagement.repository.ApprovalLogRepository
import java.util.UUID

/** Permission validation result. */
sealed class PermissionResult {
    data object Authorized : PermissionResult()
    data class Denied(val reason: String, val requiredRoles: List<UserRole> = emptyList()) : PermissionResult()
    data class AlreadyApproved(val approvedAt: String) : PermissionResult()
}

/** Permission validation service interface. */
interface PermissionService {
    suspend fun canApprove(userId: UUID, role: UserRole, ticketKey: String, docType: DocumentType, docVersion: Int): PermissionResult
    suspend fun getPermissionMatrix(): List<RolePermission>
    suspend fun updatePermissions(role: UserRole, permissions: List<PermissionUpdate>): List<RolePermission>
    suspend fun getApproverRoles(docType: DocumentType): List<UserRole>
    suspend fun seedIfEmpty()
}
