package com.orchestrator.mcp.auth

import com.orchestrator.mcp.auth.model.AuthException
import com.orchestrator.mcp.auth.model.BridgeTokenRequest
import com.orchestrator.mcp.auth.model.BridgeTokenResponse
import com.orchestrator.mcp.auth.model.LoginRequest
import com.orchestrator.mcp.auth.model.LoginResponse
import com.orchestrator.mcp.auth.model.RefreshResponse
import com.orchestrator.mcp.auth.model.SetupRequest
import com.orchestrator.mcp.auth.model.UserInfo
import com.orchestrator.mcp.usermanagement.repository.UserRepository
import at.favre.lib.crypto.bcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Business logic for authentication operations.
 * Handles login validation, bridge token generation, and token refresh.
 */
class AuthLoginHandler(
    private val jwtAuthService: JwtAuthService,
    private val passwordHashService: PasswordHashService,
    private val bridgeTokenRepo: BridgeTokenRepository,
    private val userRepository: UserRepository,
    private val authMiddleware: AuthMiddleware,
    private val config: AuthConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun login(request: LoginRequest): LoginResponse {
        val user = userRepository.findByEmail(request.username)
            ?: throw AuthException.InvalidCredentialsException()
        if (!user.active) throw AuthException.AccountDisabledException(user.email)
        val hash = PasswordHashQuery.getHash(userRepository, user.id)
        if (hash == null || !passwordHashService.verify(request.password, hash)) {
            throw AuthException.InvalidCredentialsException()
        }
        val roles = listOf(user.role.name.lowercase())
        val token = jwtAuthService.createSessionToken(
            user.id, user.email, roles, user.displayName
        )
        val expiresAt = Instant.now().plus(config.sessionExpiryHours.toLong(), ChronoUnit.HOURS)
        logger.info("Login success: email={}", user.email)
        return LoginResponse(
            token = token, expires_at = expiresAt.toString(),
            user = UserInfo(id = user.id, email = user.email, name = user.displayName, roles = roles)
        )
    }

    suspend fun bridgeToken(headers: Map<String, String>, request: BridgeTokenRequest): BridgeTokenResponse {
        val ctx = authMiddleware.authenticate(headers)
        val days = request.expiry_days.coerceIn(1, config.maxBridgeTokenDays)
        val tokenId = UUID.randomUUID().toString()
        val token = jwtAuthService.createBridgeToken(ctx.userId, ctx.email, ctx.roles, days)
        val tokenHash = AuthMiddleware.hashToken(token)
        val expiresAt = Instant.now().plus(days.toLong(), ChronoUnit.DAYS)
        bridgeTokenRepo.revokeAllExcept(ctx.userId, tokenId)
        bridgeTokenRepo.save(tokenId, ctx.userId, tokenHash, expiresAt.toString())
        logger.info("Bridge token: userId={}, days={}", ctx.userId, days)
        return BridgeTokenResponse(
            bridge_token = token, expires_at = expiresAt.toString(), token_id = tokenId
        )
    }

    fun refresh(headers: Map<String, String>): RefreshResponse {
        val authHeader = headers["Authorization"] ?: headers["authorization"]
            ?: throw AuthException.InvalidTokenException("Missing Authorization header")
        val oldToken = authHeader.removePrefix("Bearer ")
        if (!jwtAuthService.isRefreshable(oldToken)) {
            throw AuthException.InvalidTokenException("Token not eligible for refresh")
        }
        val claims = jwtAuthService.validateToken(oldToken)
        val newToken = jwtAuthService.createSessionToken(claims.userId, claims.email, claims.roles)
        val expiresAt = Instant.now().plus(config.sessionExpiryHours.toLong(), ChronoUnit.HOURS)
        return RefreshResponse(token = newToken, expires_at = expiresAt.toString())
    }

    /** Check if any users exist in the system (for setup wizard). */
    suspend fun hasAnyUsers(): Boolean {
        return userRepository.countAll() > 0
    }

    /** Create the first admin user (only works when DB is empty). */
    suspend fun setupFirstAdmin(request: SetupRequest) {
        require(request.email.contains("@")) { "Invalid email" }
        require(request.password.length >= 6) { "Password must be at least 6 characters" }
        require(request.displayName.length in 2..100) { "Name must be 2-100 characters" }

        val passwordHash = BCrypt.withDefaults()
            .hashToString(12, request.password.toCharArray())
        userRepository.createWithPassword(
            email = request.email,
            displayName = request.displayName,
            role = "SYSTEM_OWNER",
            passwordHash = passwordHash
        )
        logger.info("First admin created via setup wizard: {}", request.email)
    }
}
