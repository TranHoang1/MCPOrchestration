package com.orchestrator.mcp.kbstore.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Domain model for a Knowledge Base entry with 4-layer content separation.
 *
 * Content layers:
 * - publicContent: metadata visible to all roles
 * - technicalContent: logs/code/config for Developer+ roles
 * - businessRules: encrypted sensitive business logic (BA/Admin only)
 * - maskedFull: PII+BR masked version for low-privilege users
 *
 * Note: businessRules is stored as plaintext in this model.
 * Encryption happens at the repository layer before persistence.
 */
data class KbEntry(
    val id: UUID = UUID.randomUUID(),
    val issueKey: String,
    val projectKey: String,
    val publicContent: String? = null,
    val technicalContent: String? = null,
    val businessRules: String? = null,
    val maskedFull: String? = null,
    val brSensitivityLevel: BrSensitivityLevel = BrSensitivityLevel.INTERNAL,
    val contentHash: String,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val lastSyncedAt: Instant? = null
)
