package com.orchestrator.mcp.audit

import com.orchestrator.mcp.audit.model.AuditEvent
import com.orchestrator.mcp.audit.model.AuditQueryFilter

/**
 * Service for querying audit events with filters.
 */
interface AuditQueryService {

    /** Query audit events with the given filter criteria. */
    suspend fun query(filter: AuditQueryFilter): List<AuditEvent>

    /** Count total events matching the filter (for pagination). */
    suspend fun count(filter: AuditQueryFilter): Long
}
