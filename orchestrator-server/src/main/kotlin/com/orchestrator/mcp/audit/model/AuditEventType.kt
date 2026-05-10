package com.orchestrator.mcp.audit.model

/**
 * Types of auditable events in the KB Refinery system.
 */
enum class AuditEventType {
    VIEW_BR,
    UNMASK_PII,
    QUERY_KB,
    EXPORT_DATA,
    SESSION_CREATE,
    SESSION_REVOKE,
    ACCESS_DENIED,
    RATE_LIMITED
}
