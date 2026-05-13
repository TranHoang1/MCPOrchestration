package com.orchestrator.mcp.auth

/**
 * Repository for bridge token metadata (revocation tracking).
 * Stores token hash — never the actual token.
 */
interface BridgeTokenRepository {

    /** Save a new bridge token record. */
    suspend fun save(
        id: String,
        userId: String,
        tokenHash: String,
        expiresAt: String
    )

    /** Check if a token hash is valid (exists and not revoked). */
    suspend fun isValid(tokenHash: String): Boolean

    /** Revoke all bridge tokens for a user except the given ID. */
    suspend fun revokeAllExcept(userId: String, keepId: String)

    /** Revoke a specific token by ID. */
    suspend fun revoke(tokenId: String)

    /** Count active (non-revoked) tokens for a user. */
    suspend fun countActive(userId: String): Int
}
