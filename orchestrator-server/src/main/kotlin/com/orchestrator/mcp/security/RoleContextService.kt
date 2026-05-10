package com.orchestrator.mcp.security

import com.orchestrator.mcp.security.model.KbRole

/**
 * Resolves the KB role for a given user identity.
 * Maps application-level user groups/roles to PostgreSQL RLS roles.
 */
interface RoleContextService {

    /**
     * Determine the KB role for the given user identity string.
     * @param userIdentity The authenticated user's role/group identifier
     * @return The appropriate KbRole for database access
     */
    fun resolveRole(userIdentity: String): KbRole

    /**
     * Get the default role used when no explicit mapping exists.
     * Always returns the least-privilege role.
     */
    fun getDefaultRole(): KbRole
}
