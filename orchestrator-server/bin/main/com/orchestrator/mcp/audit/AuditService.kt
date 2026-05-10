package com.orchestrator.mcp.audit

import com.orchestrator.mcp.audit.model.AuditEvent

/**
 * Centralized audit logging service.
 * Provides both fire-and-forget (log) and suspending (logSuspend) variants.
 */
interface AuditService {

    /** Fire-and-forget audit log. Non-blocking, never throws. */
    fun log(event: AuditEvent)

    /** Suspending audit log. Waits for write completion. */
    suspend fun logSuspend(event: AuditEvent)
}
