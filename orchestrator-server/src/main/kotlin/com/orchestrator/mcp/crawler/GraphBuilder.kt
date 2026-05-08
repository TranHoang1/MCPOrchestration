package com.orchestrator.mcp.crawler

import com.orchestrator.mcp.crawler.model.IssueLink
import com.orchestrator.mcp.sync.TicketGraphRepository
import com.orchestrator.mcp.sync.model.RelationCategory
import com.orchestrator.mcp.sync.model.TicketRelation
import org.slf4j.LoggerFactory

/**
 * Builds bidirectional relationship edges in the ticket graph.
 * Each link creates both forward and reverse edges.
 */
class GraphBuilder(private val repository: TicketGraphRepository) {

    private val logger = LoggerFactory.getLogger(GraphBuilder::class.java)

    suspend fun buildEdges(issueKey: String, links: List<IssueLink>, parentKey: String?): Int {
        val relations = mutableListOf<TicketRelation>()

        for (link in links) {
            val (fwdType, revType) = mapLinkType(link.type)
            val category = if (link.direction == "outward") RelationCategory.OUTWARD else RelationCategory.INWARD
            val reverseCategory = if (category == RelationCategory.OUTWARD) RelationCategory.INWARD else RelationCategory.OUTWARD

            relations.add(TicketRelation(issueKey, link.targetKey, fwdType, category))
            relations.add(TicketRelation(link.targetKey, issueKey, revType, reverseCategory))
        }

        if (parentKey != null) {
            relations.add(TicketRelation(issueKey, parentKey, "child-of", RelationCategory.SUBTASK))
            relations.add(TicketRelation(parentKey, issueKey, "parent-of", RelationCategory.SUBTASK))
        }

        if (relations.isEmpty()) return 0

        repository.deleteBySource(issueKey)
        val inserted = repository.insertBatch(relations)
        logger.debug("Built {} edges for {}", inserted, issueKey)
        return inserted
    }

    private fun mapLinkType(type: String): Pair<String, String> = when (type.lowercase()) {
        "blocks" -> "blocks" to "is-blocked-by"
        "is blocked by" -> "is-blocked-by" to "blocks"
        "relates" -> "relates-to" to "relates-to"
        "duplicates" -> "duplicates" to "is-duplicated-by"
        "is duplicated by" -> "is-duplicated-by" to "duplicates"
        "clones" -> "clones" to "is-cloned-by"
        else -> type.lowercase() to type.lowercase()
    }
}
