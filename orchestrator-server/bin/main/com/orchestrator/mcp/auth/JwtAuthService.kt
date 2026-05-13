package com.orchestrator.mcp.auth

import com.orchestrator.mcp.auth.model.JwtClaims

/**
 * JWT token creation and validation service.
 * Supports session tokens (short-lived) and bridge tokens (long-lived).
 */
interface JwtAuthService {

    /** Create a short-lived session token for Admin Portal. */
    fun createSessionToken(
        userId: String,
        email: String,
        roles: List<String>,
        displayName: String = ""
    ): String

    /** Create a long-lived bridge token for IDE client. */
    fun createBridgeToken(
        userId: String,
        email: String,
        roles: List<String>,
        expiryDays: Int
    ): String

    /** Validate and decode a JWT token. Throws on invalid/expired. */
    fun validateToken(token: String): JwtClaims

    /** Check if a session token is eligible for refresh (within last 30 min). */
    fun isRefreshable(token: String): Boolean
}
