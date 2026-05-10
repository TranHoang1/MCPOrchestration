package com.orchestrator.mcp.kb.graph.views

import com.orchestrator.mcp.kb.graph.model.GraphNode
import com.orchestrator.mcp.kb.graph.model.TicketCache

/**
 * Team view: color by assignee, group by assignee.
 * Unassigned tickets are grey.
 */
class TeamViewStrategy : ViewModeStrategy {

    private val assigneeColors = listOf(
        "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
        "#009688", "#FF5722", "#795548", "#607D8B",
        "#4CAF50", "#FF9800", "#00BCD4", "#CDDC39"
    )

    override fun toNode(ticket: TicketCache, allTickets: List<TicketCache>): GraphNode {
        val assignees = allTickets.mapNotNull { it.labels?.firstOrNull() }.distinct()
        val colorIndex = assignees.indexOf(ticket.labels?.firstOrNull())
            .takeIf { it >= 0 } ?: -1

        val color = if (colorIndex >= 0) {
            assigneeColors[colorIndex % assigneeColors.size]
        } else "#9E9E9E"

        return GraphNode(
            id = ticket.ticketKey,
            label = ticket.summary,
            type = ticket.issueType,
            status = ticket.status,
            priority = ticket.priority,
            group = ticket.labels?.firstOrNull() ?: "Unassigned",
            color = color,
            size = 6.0f
        )
    }
}
