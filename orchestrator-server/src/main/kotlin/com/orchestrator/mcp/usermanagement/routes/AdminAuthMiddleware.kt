package com.orchestrator.mcp.usermanagement.routes

import com.orchestrator.mcp.auth.JwtAuthService
import com.orchestrator.mcp.usermanagement.model.UserManagementException
import com.orchestrator.mcp.usermanagement.service.UserService
import org.slf4j.LoggerFactory

/**
 * Admin authentication middleware.
 * Validates that the requesting user has admin role (leader or system_owner).
 * Uses JwtAuthService for proper signature-verified JWT validation.
 */
class AdminAuthMiddleware(
    private val userService: UserService,
    private val jwtAuthService: JwtAuthService,
    private val headerName: String = "X-User-Email"
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Validate admin access from request headers.
     * @return admin user's UUID string if authorized
     */
    suspend fun validateAdmin(headers: Map<String, String>): String {
        val email = extractEmail(headers)
        if (email == null) {
            throw UserManagementException.PermissionDeniedException(
                "Access denied. Missing authentication."
            )
        }
        val user = userService.getUserByEmail(email)
        if (user == null) {
            throw UserManagementException.PermissionDeniedException(
                "Access denied. User not found: $email"
            )
        }
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

    private fun extractEmail(headers: Map<String, String>): String? {
        // Try X-User-Email header first (case-insensitive)
        val emailHeader = headers[headerName.lowercase()] ?: headers[headerName]
        if (emailHeader != null) return emailHeader
        // Try Authorization: Bearer JWT — with signature validation
        val authHeader = headers["authorization"] ?: headers["Authorization"]
            ?: return null
        if (!authHeader.startsWith("Bearer ")) return null
        return extractEmailFromJwt(authHeader.removePrefix("Bearer "))
    }

    /**
     * Extract email from JWT using JwtAuthService which validates
     * the signature. Prevents forged JWT bypass (MTO-109 Finding #9).
     */
    private fun extractEmailFromJwt(token: String): String? {
        return try {
            val claims = jwtAuthService.validateToken(token)
            claims.email
        } catch (e: Exception) {
            logger.debug("JWT validation failed: {}", e.message)
            null
        }
    }
}
