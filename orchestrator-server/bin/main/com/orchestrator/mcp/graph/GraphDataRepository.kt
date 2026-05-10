package com.orchestrator.mcp.graph

import com.orchestrator.mcp.sync.TicketCacheRepository
import com.orchestrator.mcp.sync.TicketGraphRepository
import com.orchestrator.mcp.sync.model.TicketCache
import com.orchestrator.mcp.sync.model.TicketRelation
import org.slf4j.LoggerFactory

/**
 * Repository for querying graph data from ticket cache and graph tables.
 * Delegates to existing sync repositories.
 */
class GraphDataRepository(
    private val ticketCacheRepo: TicketCacheRepository,
    private val ticketGraphRepo: TicketGraphRepository
) {

    private val logger = LoggerFactory.getLogger(GraphDataRepository::class.java)

    suspend fun getTicketsByProject(projectKey: String): List<TicketCache> {
        return ticketCacheRepo.findByProject(projectKey)
    }

    suspend fun getEdgesByProject(projectKey: String): List<TicketRelation> {
        return ticketGraphRepo.findAllForProject(projectKey)
    }

    suspend fun getTicket(issueKey: String, projectKey: String): TicketCache? {
        return ticketCacheRepo.findByProject(projectKey)
            .firstOrNull { it.ticketKey == issueKey }
    }

    suspend fun bfsTraversal(
        issueKey: String,
        projectKey: String,
        depth: Int
    ): Pair<List<TicketCache>, List<TicketRelation>> {
        val allEdges = ticketGraphRepo.findAllForProject(projectKey)
        val visited = mutableSetOf(issueKey)
        val resultEdges = mutableListOf<TicketRelation>()
        var frontier = setOf(issueKey)

        repeat(depth) {
            val nextFrontier = mutableSetOf<String>()
            for (edge in allEdges) {
                if (edge.sourceKey in frontier && edge.targetKey !in visited) {
                    visited.add(edge.targetKey)
                    nextFrontier.add(edge.targetKey)
                    resultEdges.add(edge)
                }
                if (edge.targetKey in frontier && edge.sourceKey !in visited) {
                    visited.add(edge.sourceKey)
                    nextFrontier.add(edge.sourceKey)
                    resultEdges.add(edge)
                }
            }
            frontier = nextFrontier
        }

        val allTickets = ticketCacheRepo.findByProject(projectKey)
        val relevantTickets = allTickets.filter { it.ticketKey in visited }
        return Pair(relevantTickets, resultEdges)
    }
}
