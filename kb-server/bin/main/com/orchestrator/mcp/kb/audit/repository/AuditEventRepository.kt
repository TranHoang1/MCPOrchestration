package com.orchestrator.mcp.kb.audit.repository

import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import kotlinx.datetime.Instant

/**
 * Repository for persisting and querying audit events.
 */
interface AuditEventRepository {

    suspend fun save(event: AuditEvent)

    suspend fun query(
        eventType: AuditEventType? = null,
        issueKey: String? = null,
        fromDate: Instant? = null,
        toDate: Instant? = null,
        limit: Int = 50
    ): List<AuditEvent>
}
