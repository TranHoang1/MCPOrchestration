package com.orchestrator.mcp.auth.model

import kotlinx.serialization.Serializable

/** Token type distinguishes session (short-lived) from bridge (long-lived). */
enum class TokenType { SESSION, BRIDGE }

/** Decoded JWT claims after validation. */
data class JwtClaims(
    val userId: String,
    val email: String,
    val roles: List<String>,
    val tokenType: TokenType,
    val tokenId: String
)

/** User context extracted from authenticated request. */
data class UserContext(
    val userId: String,
    val email: String,
    val roles: List<String>,
    val tokenType: TokenType
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val expires_at: String,
    val user: UserInfo
)

@Serializable
data class UserInfo(
    val id: String,
    val email: String,
    val name: String,
    val roles: List<String>
)

@Serializable
data class BridgeTokenRequest(
    val expiry_days: Int = 30
)

@Serializable
data class BridgeTokenResponse(
    val bridge_token: String,
    val expires_at: String,
    val token_id: String
)

@Serializable
data class RefreshResponse(
    val token: String,
    val expires_at: String
)

@Serializable
data class AuthErrorResponse(
    val error: String,
    val message: String
)
