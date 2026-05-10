package com.orchestrator.mcp.kb.audit

import com.orchestrator.mcp.kb.audit.model.AuditEvent

/**
 * Centralized audit logging service for KB operations.
 * Provides both fire-and-forget (log) and suspending (logSuspend) variants.
 */
interface AuditService {

    /** Fire-and-forget audit log. Non-blocking, never throws. */
    fun log(event: AuditEvent)

    /** Suspending audit log. Waits for write completion. */
    suspend fun logSuspend(event: AuditEvent)
}
