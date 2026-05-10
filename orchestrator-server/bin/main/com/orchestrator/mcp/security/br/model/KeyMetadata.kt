package com.orchestrator.mcp.security.br.model

import kotlinx.datetime.Instant

/**
 * Metadata for a KMS-managed encryption key.
 */
data class KeyMetadata(
    val keyId: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val status: KeyStatus
)

enum class KeyStatus {
    ACTIVE,
    RETIRED,
    REVOKED
}
