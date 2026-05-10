package com.orchestrator.mcp.kb.graph

import com.orchestrator.mcp.kb.graph.model.TicketCache
import com.orchestrator.mcp.kb.graph.model.TicketRelation
import com.orchestrator.mcp.kb.graph.repository.TicketCacheRepository
import com.orchestrator.mcp.kb.graph.repository.TicketGraphRepository
import org.slf4j.LoggerFactory

/**
 * Repository for querying graph data from ticket cache and graph tables.
 * Delegates to existing sync repositories.
 */
class GraphDataRepository(
    private val ticketCacheRepo: TicketCacheRepository,
    private val ticketGraphRepo: TicketGraphRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun getTicketsByProject(projectKey: String): List<TicketCache> {
        return ticketCacheRepo.findByProject(projectKey)
    }

    suspend fun getEdgesByProject(projectKey: String): List<TicketRelation> {
        return ticketGraphRepo.findAllForProject(projectKey)
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
                collectNeighbors(edge, frontier, visited, nextFrontier, resultEdges)
            }
            frontier = nextFrontier
        }

        val allTickets = ticketCacheRepo.findByProject(projectKey)
        val relevantTickets = allTickets.filter { it.ticketKey in visited }
        return Pair(relevantTickets, resultEdges)
    }

    private fun collectNeighbors(
        edge: TicketRelation,
        frontier: Set<String>,
        visited: MutableSet<String>,
        nextFrontier: MutableSet<String>,
        resultEdges: MutableList<TicketRelation>
    ) {
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
}
