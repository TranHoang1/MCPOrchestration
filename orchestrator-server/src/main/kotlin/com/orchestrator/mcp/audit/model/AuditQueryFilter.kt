package com.orchestrator.mcp.audit.model

import kotlinx.datetime.Instant

/**
 * Filter criteria for querying audit events.
 */
data class AuditQueryFilter(
    val userId: String? = null,
    val eventType: AuditEventType? = null,
    val issueKey: String? = null,
    val fromDate: Instant? = null,
    val toDate: Instant? = null,
    val successOnly: Boolean? = null,
    val limit: Int = 50,
    val offset: Int = 0
)
