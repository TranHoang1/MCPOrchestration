package com.orchestrator.mcp.audit.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Represents a single audit event to be recorded.
 */
data class AuditEvent(
    val eventType: AuditEventType,
    val userId: String,
    val issueKey: String? = null,
    val action: String,
    val success: Boolean,
    val metadata: Map<String, String> = emptyMap(),
    val ipAddress: String? = null,
    val timestamp: Instant = Clock.System.now()
)
