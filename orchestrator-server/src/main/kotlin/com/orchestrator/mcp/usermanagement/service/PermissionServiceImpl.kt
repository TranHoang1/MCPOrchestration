package com.orchestrator.mcp.usermanagement.service

import com.orchestrator.mcp.usermanagement.model.*
import com.orchestrator.mcp.usermanagement.repository.ApprovalLogRepository
import com.orchestrator.mcp.usermanagement.repository.RolePermissionRepository
import com.orchestrator.mcp.usermanagement.repository.UserProjectRepository
import org.slf4j.LoggerFactory
import java.util.UUID

/** Implementation of PermissionService with caching. */
class PermissionServiceImpl(
    private val rolePermissionRepo: RolePermissionRepository,
    private val userProjectRepo: UserProjectRepository,
    private val approvalLogRepo: ApprovalLogRepository
) : PermissionService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun canApprove(
        userId: UUID, role: UserRole, ticketKey: String,
        docType: DocumentType, docVersion: Int
    ): PermissionResult {
        // Check role permission
        val permission = rolePermissionRepo.find(role, docType)
        if (permission == null || !permission.canApprove) {
            val approverRoles = getApproverRoles(docType)
            return PermissionResult.Denied(
                "Role '${role.name}' cannot approve ${docType.name}. Required: ${approverRoles.joinToString { it.name }}",
                approverRoles
            )
        }
        // Check project assignment
        val projectKey = extractProjectKey(ticketKey)
        if (!userProjectRepo.exists(userId, projectKey)) {
            return PermissionResult.Denied("Not assigned to project '$projectKey'. Contact admin for access.")
        }
        // Check duplicate approval
        if (approvalLogRepo.exists(userId, ticketKey, docType, docVersion)) {
            return PermissionResult.AlreadyApproved("Already approved")
        }
        return PermissionResult.Authorized
    }

    override suspend fun getPermissionMatrix(): List<RolePermission> = rolePermissionRepo.findAll()

    override suspend fun updatePermissions(role: UserRole, permissions: List<PermissionUpdate>): List<RolePermission> {
        permissions.forEach { p ->
            rolePermissionRepo.upsert(role, p.documentType, p.canView, p.canApprove)
        }
        logger.info("Updated permissions for role=${role.name}: ${permissions.size} entries")
        return rolePermissionRepo.findByRole(role)
    }

    override suspend fun getApproverRoles(docType: DocumentType): List<UserRole> {
        return rolePermissionRepo.findAll()
            .filter { it.documentType == docType && it.canApprove }
            .map { it.role }
    }

    override suspend fun seedIfEmpty() {
        if (rolePermissionRepo.count() == 0) {
            logger.info("Seeding default permission matrix (42 entries)")
            rolePermissionRepo.seedDefaults()
        }
    }

    private fun extractProjectKey(ticketKey: String): String {
        return ticketKey.substringBefore('-')
    }
}
