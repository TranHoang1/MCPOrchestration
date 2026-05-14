package com.orchestrator.mcp.usermanagement.routes

import com.orchestrator.mcp.usermanagement.model.UserManagementException
import com.orchestrator.mcp.usermanagement.service.UserService
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * Admin authentication middleware.
 * Validates that the requesting user has admin role (leader or system_owner).
 * Supports both X-User-Email header and Authorization: Bearer JWT.
 */
class AdminAuthMiddleware(
    private val userService: UserService,
    private val headerName: String = "X-User-Email"
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Validate admin access from request headers.
     * @return admin user's UUID string if authorized
     */
    suspend fun validateAdmin(headers: Map<String, String>): String {
        val email = extractEmail(headers)
        logger.info("validateAdmin: extracted email={}, headers keys={}", email, headers.keys)
        if (email == null) {
            throw UserManagementException.PermissionDeniedException(
                "Access denied. Missing authentication."
            )
        }
        val user = userService.getUserByEmail(email)
        logger.info("validateAdmin: getUserByEmail({}) returned {}", email, user?.id ?: "NULL")
        if (user == null) {
            throw UserManagementException.PermissionDeniedException(
                "Access denied. User not found: $email"
            )
        }
        if (!user.active) {
            throw UserManagementException.PermissionDeniedException("Account deactivated: $email")
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
        // Try Authorization: Bearer JWT
        val authHeader = headers["authorization"] ?: headers["Authorization"] ?: return null
        if (!authHeader.startsWith("Bearer ")) return null
        return extractEmailFromJwt(authHeader.removePrefix("Bearer "))
    }

    private fun extractEmailFromJwt(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = String(Base64.getUrlDecoder().decode(parts[1]))
            val emailRegex = """"email"\s*:\s*"([^"]+)"""".toRegex()
            emailRegex.find(payload)?.groupValues?.get(1)
        } catch (e: Exception) {
            logger.debug("Failed to extract email from JWT: {}", e.message)
            null
        }
    }
}
