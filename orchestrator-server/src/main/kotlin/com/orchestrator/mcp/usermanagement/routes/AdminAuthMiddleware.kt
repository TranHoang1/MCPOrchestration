package com.orchestrator.mcp.usermanagement.routes

import com.orchestrator.mcp.usermanagement.model.UserManagementException
import com.orchestrator.mcp.usermanagement.model.UserRole
import com.orchestrator.mcp.usermanagement.service.UserService
import org.slf4j.LoggerFactory

/**
 * Admin authentication middleware.
 * Validates that the requesting user has admin role (leader or system_owner).
 */
class AdminAuthMiddleware(
    private val userService: UserService,
    private val headerName: String = "X-User-Email"
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Validate admin access from request headers.
     * @return admin user's UUID string if authorized
     * @throws UserManagementException.PermissionDeniedException if not admin
     */
    suspend fun validateAdmin(headers: Map<String, String>): String {
        val email = headers[headerName]
            ?: throw UserManagementException.PermissionDeniedException(
                "Access denied. Missing $headerName header."
            )
        val user = userService.getUserByEmail(email)
            ?: throw UserManagementException.PermissionDeniedException(
                "Access denied. User not found: $email"
            )
        if (!user.active) {
            throw UserManagementException.PermissionDeniedException(
                "Account deactivated: $email"
            )
        }
        if (!user.role.isAdmin()) {
            throw UserManagementException.PermissionDeniedException(
                "Access denied. Only leader or system_owner can manage users."
            )
        }
        logger.debug("Admin access granted: email=$email, role=${user.role}")
        return user.id
    }
}
