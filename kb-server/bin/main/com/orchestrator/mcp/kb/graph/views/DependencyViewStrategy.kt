package com.orchestrator.mcp.kb.graph.views

import com.orchestrator.mcp.kb.graph.model.GraphNode
import com.orchestrator.mcp.kb.graph.model.TicketCache

/**
 * Dependency view: color by status, size by connection count.
 * Highly-connected nodes appear larger (potential bottlenecks).
 */
class DependencyViewStrategy : ViewModeStrategy {

    override fun toNode(ticket: TicketCache, allTickets: List<TicketCache>): GraphNode {
        return GraphNode(
            id = ticket.ticketKey,
            label = ticket.summary,
            type = ticket.issueType,
            status = ticket.status,
            priority = ticket.priority,
            group = ticket.status,
            color = statusColor(ticket.status),
            size = 6.0f
        )
    }

    private fun statusColor(status: String) = when (status.lowercase()) {
        "to do" -> "#9E9E9E"
        "in progress" -> "#FF9800"
        "in review" -> "#2196F3"
        "done" -> "#4CAF50"
        "blocked" -> "#F44336"
        else -> "#757575"
    }
}
