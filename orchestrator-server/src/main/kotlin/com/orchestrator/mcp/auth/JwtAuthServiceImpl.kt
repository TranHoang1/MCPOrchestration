package com.orchestrator.mcp.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException as Auth0TokenExpired
import com.orchestrator.mcp.auth.model.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * JWT service using com.auth0:java-jwt.
 * Creates and validates session/bridge tokens with HS256 signing.
 */
class JwtAuthServiceImpl(
    private val config: AuthConfig
) : JwtAuthService {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)
    private val verifier = JWT.require(algorithm)
        .withIssuer(config.issuer)
        .build()

    override fun createSessionToken(
        userId: String,
        email: String,
        roles: List<String>,
        displayName: String
    ): String {
        val now = Instant.now()
        val expiry = now.plus(config.sessionExpiryHours.toLong(), ChronoUnit.HOURS)
        return buildToken(userId, email, roles, displayName, TokenType.SESSION, now, expiry)
    }

    override fun createBridgeToken(
        userId: String,
        email: String,
        roles: List<String>,
        expiryDays: Int
    ): String {
        val clampedDays = expiryDays.coerceIn(1, config.maxBridgeTokenDays)
        val now = Instant.now()
        val expiry = now.plus(clampedDays.toLong(), ChronoUnit.DAYS)
        return buildToken(userId, email, roles, null, TokenType.BRIDGE, now, expiry)
    }

    override fun validateToken(token: String): JwtClaims {
        try {
            val decoded = verifier.verify(token)
            return JwtClaims(
                userId = decoded.subject,
                email = decoded.getClaim("email").asString(),
                roles = decoded.getClaim("roles").asList(String::class.java) ?: emptyList(),
                tokenType = parseTokenType(decoded.getClaim("type").asString()),
                tokenId = decoded.id ?: ""
            )
        } catch (e: Auth0TokenExpired) {
            throw AuthException.TokenExpiredException()
        } catch (e: JWTVerificationException) {
            logger.debug("Token validation failed: {}", e.message)
            throw AuthException.InvalidTokenException(e.message ?: "Verification failed")
        }
    }

    override fun isRefreshable(token: String): Boolean {
        return try {
            val decoded = JWT.decode(token)
            val expiresAt = decoded.expiresAtAsInstant ?: return false
            val now = Instant.now()
            val minutesUntilExpiry = ChronoUnit.MINUTES.between(now, expiresAt)
            minutesUntilExpiry in 0..30
        } catch (_: Exception) {
            false
        }
    }

    private fun buildToken(
        userId: String,
        email: String,
        roles: List<String>,
        displayName: String?,
        type: TokenType,
        issuedAt: Instant,
        expiresAt: Instant
    ): String {
        val builder = JWT.create()
            .withIssuer(config.issuer)
            .withSubject(userId)
            .withClaim("email", email)
            .withClaim("roles", roles)
            .withClaim("type", type.name.lowercase())
            .withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(issuedAt)
            .withExpiresAt(expiresAt)
        if (!displayName.isNullOrBlank()) {
            builder.withClaim("name", displayName)
        }
        return builder.sign(algorithm)
    }

    private fun parseTokenType(value: String?): TokenType {
        return when (value?.lowercase()) {
            "bridge" -> TokenType.BRIDGE
            else -> TokenType.SESSION
        }
    }
}
