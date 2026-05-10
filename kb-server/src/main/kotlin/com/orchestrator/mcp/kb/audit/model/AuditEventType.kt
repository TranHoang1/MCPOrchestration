package com.orchestrator.mcp.kb.audit.model

/**
 * Types of auditable events in the KB Server.
 */
enum class AuditEventType {
    SEARCH,
    READ,
    INGEST,
    DELETE,
    UNMASK_PII,
    UNMASK_BR,
    LINK,
    FEEDBACK,
    SYNC_TRIGGER,
    ACCESS_DENIED,
    RATE_LIMITED
}
