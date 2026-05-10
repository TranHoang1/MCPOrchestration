package com.orchestrator.mcp.linking.repository

import com.orchestrator.mcp.linking.model.EntityLink

/**
 * Repository for entity link persistence.
 */
interface EntityLinkRepository {

    /** Find all links for a given issue key (as source or target). */
    suspend fun findByIssueKey(issueKey: String): List<EntityLink>

    /** Save multiple links. Returns count of links actually created. */
    suspend fun saveAll(links: List<EntityLink>): Int

    /** Delete all links for an issue key. */
    suspend fun deleteByIssueKey(issueKey: String): Int
}
