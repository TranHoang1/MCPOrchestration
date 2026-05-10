package com.orchestrator.mcp.kb.store.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Domain model for PII placeholder-to-original-value mapping.
 * Each mapping links a placeholder token (e.g., [PII_NAME_01]) to its
 * original PII value. Encryption happens at the repository layer.
 */
data class PiiMapping(
    val id: UUID = UUID.randomUUID(),
    val issueKey: String,
    val placeholder: String,
    val originalValue: String,
    val mappingType: MappingType,
    val createdAt: Instant = Clock.System.now()
)
