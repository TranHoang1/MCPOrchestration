package com.orchestrator.mcp.linking

import com.orchestrator.mcp.linking.model.EntityLink
import com.orchestrator.mcp.linking.model.LinkingResult

/**
 * Service for semantic entity linking between KB entries.
 * Uses embedding cosine similarity to find related tickets.
 */
interface EntityLinkingService {

    /** Find similar entries for a given issue key. */
    suspend fun findSimilar(issueKey: String, topK: Int = 10): List<EntityLink>

    /** Link a new entry by finding and storing similar entries. */
    suspend fun linkEntry(issueKey: String, content: String): LinkingResult

    /** Batch link multiple entries. */
    suspend fun batchLink(entries: List<Pair<String, String>>): List<LinkingResult>

    /** Get all existing links for an issue key. */
    suspend fun getLinks(issueKey: String): List<EntityLink>
}
