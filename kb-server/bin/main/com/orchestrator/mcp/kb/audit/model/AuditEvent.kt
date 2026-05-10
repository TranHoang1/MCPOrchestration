package com.orchestrator.mcp.kb.audit.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Represents a single audit event to be recorded.
 */
data class AuditEvent(
    val eventType: AuditEventType,
    val userId: String = "system",
    val issueKey: String? = null,
    val action: String,
    val success: Boolean,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Instant = Clock.System.now()
)
