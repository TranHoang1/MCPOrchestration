package com.orchestrator.mcp.security.config

import com.orchestrator.mcp.security.model.KbRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for RLS (Row-Level Security) behavior.
 * Loaded from application.yml under orchestrator.security.rls section.
 */
@Serializable
data class RlsConfig(
    val enabled: Boolean = true,
    @SerialName("default_role")
    val defaultRole: KbRole = KbRole.LOW_PRIVILEGE,
    @SerialName("force_rls")
    val forceRls: Boolean = true,
    @SerialName("role_mappings")
    val roleMappings: Map<String, KbRole> = mapOf(
        "ROLE_DEVELOPER" to KbRole.DEVELOPER,
        "ROLE_BA" to KbRole.BA_ADMIN,
        "ROLE_ADMIN" to KbRole.BA_ADMIN,
        "ROLE_USER" to KbRole.LOW_PRIVILEGE
    )
)
