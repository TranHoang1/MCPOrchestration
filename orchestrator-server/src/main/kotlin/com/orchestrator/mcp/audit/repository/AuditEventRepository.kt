package com.orchestrator.mcp.audit.repository

import com.orchestrator.mcp.audit.model.AuditEvent
import com.orchestrator.mcp.audit.model.AuditQueryFilter

/**
 * Repository for audit event persistence.
 * Append-only — no update or delete operations.
 */
interface AuditEventRepository {

    /** Save a new audit event. */
    suspend fun save(event: AuditEvent)

    /** Find events matching the given filter. */
    suspend fun findByFilter(filter: AuditQueryFilter): List<AuditEvent>

    /** Count events matching the given filter. */
    suspend fun countByFilter(filter: AuditQueryFilter): Long
}
