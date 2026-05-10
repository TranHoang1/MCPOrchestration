package com.orchestrator.mcp.usermanagement.repository

import com.orchestrator.mcp.usermanagement.model.DocumentType
import com.orchestrator.mcp.usermanagement.model.RolePermission
import com.orchestrator.mcp.usermanagement.model.UserRole

/** Repository interface for role-permission matrix. */
interface RolePermissionRepository {
    suspend fun findAll(): List<RolePermission>
    suspend fun findByRole(role: UserRole): List<RolePermission>
    suspend fun find(role: UserRole, docType: DocumentType): RolePermission?
    suspend fun upsert(role: UserRole, docType: DocumentType, canView: Boolean, canApprove: Boolean)
    suspend fun count(): Int
    suspend fun seedDefaults()
}
