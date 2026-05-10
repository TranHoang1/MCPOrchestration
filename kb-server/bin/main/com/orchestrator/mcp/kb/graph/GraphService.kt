package com.orchestrator.mcp.kb.graph

import com.orchestrator.mcp.kb.graph.model.*
import com.orchestrator.mcp.kb.graph.views.ViewModeStrategy
import org.slf4j.LoggerFactory

/**
 * Business logic for graph data transformation and view mode application.
 */
class GraphService(
    private val repository: GraphDataRepository,
    private val viewStrategies: Map<ViewMode, ViewModeStrategy>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun getProjectGraph(projectKey: String, view: ViewMode): GraphResponse {
        val tickets = repository.getTicketsByProject(projectKey)
        val edges = repository.getEdgesByProject(projectKey)
        val strategy = resolveStrategy(view)

        val nodes = tickets.map { strategy.toNode(it, tickets) }
        val graphEdges = edges.map { toGraphEdge(it) }

        return GraphResponse(
            nodes = nodes,
            edges = graphEdges,
            metadata = GraphMetadata(projectKey, view.name, nodes.size, graphEdges.size)
        )
    }

    suspend fun getSubgraph(
        projectKey: String,
        issueKey: String,
        depth: Int,
        view: ViewMode
    ): GraphResponse {
        val (tickets, edges) = repository.bfsTraversal(issueKey, projectKey, depth)
        val strategy = resolveStrategy(view)

        val nodes = tickets.map { ticket ->
            val node = strategy.toNode(ticket, tickets)
            if (ticket.ticketKey == issueKey) node.copy(size = node.size * 1.5f) else node
        }

        return GraphResponse(
            nodes = nodes,
            edges = edges.map { toGraphEdge(it) },
            metadata = GraphMetadata(projectKey, view.name, nodes.size, edges.size)
        )
    }

    private fun resolveStrategy(view: ViewMode): ViewModeStrategy {
        return viewStrategies[view] ?: viewStrategies[ViewMode.HIERARCHY]!!
    }

    private fun toGraphEdge(relation: TicketRelation): GraphEdge {
        return GraphEdge(
            source = relation.sourceKey,
            target = relation.targetKey,
            label = relation.linkType,
            color = edgeColor(relation.linkType),
            width = edgeWidth(relation.linkType)
        )
    }

    private fun edgeColor(linkType: String) = when (linkType.lowercase()) {
        "blocks" -> "#F44336"
        "is blocked by" -> "#FF9800"
        "is parent of", "parent" -> "#9C27B0"
        "is child of", "child" -> "#CE93D8"
        "relates to" -> "#666666"
        else -> "#888888"
    }

    private fun edgeWidth(linkType: String) = when (linkType.lowercase()) {
        "blocks", "is blocked by" -> 2.0f
        "is parent of", "parent" -> 1.5f
        else -> 1.0f
    }
}
