package com.orchestrator.mcp.graph.views

import com.orchestrator.mcp.graph.model.GraphNode
import com.orchestrator.mcp.sync.model.TicketCache

/**
 * Strategy interface for computing node visual properties per view mode.
 */
interface ViewModeStrategy {
    fun toNode(ticket: TicketCache, allTickets: List<TicketCache>): GraphNode
}
