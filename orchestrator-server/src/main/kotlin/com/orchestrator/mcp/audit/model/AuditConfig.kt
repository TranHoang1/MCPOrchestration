package com.orchestrator.mcp.audit.model

/**
 * Configuration for the audit subsystem.
 */
data class AuditConfig(
    val retentionDays: Int = 90,
    val asyncEnabled: Boolean = true,
    val maxMetadataSize: Int = 1024
)
