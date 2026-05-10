package com.orchestrator.mcp.audit

import com.orchestrator.mcp.audit.model.AuditEvent
import com.orchestrator.mcp.audit.model.AuditQueryFilter
import com.orchestrator.mcp.audit.repository.AuditEventRepository

/**
 * Implementation of audit query service.
 * Delegates to repository with filter translation.
 */
class AuditQueryServiceImpl(
    private val repository: AuditEventRepository
) : AuditQueryService {

    override suspend fun query(filter: AuditQueryFilter): List<AuditEvent> =
        repository.findByFilter(filter)

    override suspend fun count(filter: AuditQueryFilter): Long =
        repository.countByFilter(filter)
}
