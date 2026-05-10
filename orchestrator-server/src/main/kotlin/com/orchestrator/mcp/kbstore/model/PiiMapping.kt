package com.orchestrator.mcp.kbstore.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Domain model for PII placeholder-to-original-value mapping.
 *
 * Each mapping links a placeholder token (e.g., [PII_NAME_01]) to its
 * original PII value. The originalValue is stored as plaintext here;
 * encryption happens at the repository layer before persistence.
 */
data class PiiMapping(
    val id: UUID = UUID.randomUUID(),
    val issueKey: String,
    val placeholder: String,
    val originalValue: String,
    val mappingType: MappingType,
    val createdAt: Instant = Clock.System.now()
)
