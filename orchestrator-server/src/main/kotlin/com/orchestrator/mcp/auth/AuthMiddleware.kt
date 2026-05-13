package com.orchestrator.mcp.auth

import com.orchestrator.mcp.auth.model.*
import com.orchestrator.mcp.usermanagement.service.UserService
import org.slf4j.LoggerFactory
import java.security.MessageDigest

/**
 * Authentication middleware for HTTP requests.
 * Validates JWT from Authorization header or falls back to deprecated X-User-Email.
 */
class AuthMiddleware(
    private val jwtAuthService: JwtAuthService,
    private val config: AuthConfig,
    private val userService: UserService,
    private val bridgeTokenRepo: BridgeTokenRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Authenticate a request from headers.
     * Priority: Authorization Bearer > X-User-Email (deprecated).
     */
    suspend fun authenticate(headers: Map<String, String>): UserContext {
        val authHeader = headers["Authorization"] ?: headers["authorization"]
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authenticateJwt(authHeader.removePrefix("Bearer "))
        }
        if (config.allowEmailHeader) {
            return authenticateEmailHeader(headers)
        }
        throw AuthException.InvalidTokenException("Missing Authorization header")
    }

    /** Authenticate and require specific roles. */
    suspend fun authenticateWithRoles(
        headers: Map<String, String>,
        requiredRoles: List<String>
    ): UserContext {
        val ctx = authenticate(headers)
        if (!hasRequiredRole(ctx.roles, requiredRoles)) {
            throw AuthException.InsufficientRoleException(requiredRoles)
        }
        return ctx
    }

    private suspend fun authenticateJwt(token: String): UserContext {
        val claims = jwtAuthService.validateToken(token)
        if (claims.tokenType == TokenType.BRIDGE) {
            validateBridgeNotRevoked(token)
        }
        return UserContext(
            userId = claims.userId,
            email = claims.email,
            roles = claims.roles,
            tokenType = claims.tokenType
        )
    }

    private suspend fun authenticateEmailHeader(headers: Map<String, String>): UserContext {
        val email = headers[config.emailHeaderName]
            ?: headers[config.emailHeaderName.lowercase()]
            ?: throw AuthException.InvalidTokenException("Missing Authorization header")
        logger.warn("Deprecated X-User-Email header used: {}", email)
        val user = userService.getUserByEmail(email)
            ?: throw AuthException.InvalidTokenException("User not found: $email")
        return UserContext(
            userId = user.id,
            email = user.email,
            roles = listOf(user.role.name.lowercase()),
            tokenType = TokenType.SESSION
        )
    }

    private suspend fun validateBridgeNotRevoked(token: String) {
        val hash = hashToken(token)
        if (!bridgeTokenRepo.isValid(hash)) {
            throw AuthException.TokenRevokedException()
        }
    }

    private fun hasRequiredRole(userRoles: List<String>, required: List<String>): Boolean {
        return userRoles.any { role ->
            required.any { it.equals(role, ignoreCase = true) }
        }
    }

    companion object {
        /** SHA-256 hash of token for revocation lookup. */
        fun hashToken(token: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(token.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
