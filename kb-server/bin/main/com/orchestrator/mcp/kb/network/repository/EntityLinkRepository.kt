package com.orchestrator.mcp.kb.network.repository

import com.orchestrator.mcp.kb.network.model.EntityLink

/**
 * Repository for entity link persistence.
 * Used by network service to build feature network graphs.
 */
interface EntityLinkRepository {

    /** Find all links for a given issue key (as source or target). */
    suspend fun findByIssueKey(issueKey: String): List<EntityLink>
}
