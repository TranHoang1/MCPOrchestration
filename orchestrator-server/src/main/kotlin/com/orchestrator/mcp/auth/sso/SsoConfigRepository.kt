package com.orchestrator.mcp.auth.sso

import com.orchestrator.mcp.auth.sso.model.SsoConfig

/**
 * Repository interface for SSO configuration persistence.
 * Uses singleton pattern — only one SSO config exists per deployment.
 */
interface SsoConfigRepository {

    /** Get the current SSO configuration, or null if not configured. */
    suspend fun get(): SsoConfig?

    /** Upsert SSO configuration (singleton row). */
    suspend fun save(config: SsoConfig): SsoConfig
}
