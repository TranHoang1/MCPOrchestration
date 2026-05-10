package com.orchestrator.mcp.security

import com.orchestrator.mcp.security.config.RlsConfig
import com.orchestrator.mcp.security.model.KbRole
import org.slf4j.LoggerFactory

/**
 * Default implementation of [RoleContextService].
 * Resolves roles from configuration-based mappings with fallback to default role.
 */
class RoleContextServiceImpl(
    private val config: RlsConfig
) : RoleContextService {

    private val logger = LoggerFactory.getLogger(RoleContextServiceImpl::class.java)

    override fun resolveRole(userIdentity: String): KbRole {
        val mappedRole = config.roleMappings[userIdentity]
        if (mappedRole != null) {
            logger.debug("Resolved role for '{}': {}", userIdentity, mappedRole)
            return mappedRole
        }
        logger.warn(
            "No role mapping for '{}', using default: {}",
            userIdentity,
            config.defaultRole
        )
        return config.defaultRole
    }

    override fun getDefaultRole(): KbRole = config.defaultRole
}
