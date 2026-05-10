package com.orchestrator.mcp.graph.views

import com.orchestrator.mcp.graph.model.GraphNode
import com.orchestrator.mcp.sync.model.TicketCache

/**
 * Hierarchy view: color by issue type, size by hierarchy level.
 * Epics are largest, Stories medium, Tasks/Bugs smallest.
 */
class HierarchyViewStrategy : ViewModeStrategy {

    override fun toNode(ticket: TicketCache, allTickets: List<TicketCache>): GraphNode {
        return GraphNode(
            id = ticket.ticketKey,
            label = ticket.summary,
            type = ticket.issueType,
            status = ticket.status,
            priority = ticket.priority,
            group = ticket.issueType,
            color = typeColor(ticket.issueType),
            size = typeSize(ticket.issueType)
        )
    }

    private fun typeColor(type: String) = when (type.lowercase()) {
        "epic" -> "#9C27B0"
        "story" -> "#4CAF50"
        "task" -> "#2196F3"
        "bug" -> "#F44336"
        "sub-task" -> "#00BCD4"
        else -> "#9E9E9E"
    }

    private fun typeSize(type: String) = when (type.lowercase()) {
        "epic" -> 12.0f
        "story" -> 8.0f
        "task" -> 5.0f
        "bug" -> 6.0f
        "sub-task" -> 3.0f
        else -> 5.0f
    }
}
