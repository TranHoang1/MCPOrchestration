package com.orchestrator.mcp.kb.graph.views

import com.orchestrator.mcp.kb.graph.model.GraphNode
import com.orchestrator.mcp.kb.graph.model.TicketCache

/**
 * Strategy interface for computing node visual properties per view mode.
 */
interface ViewModeStrategy {
    fun toNode(ticket: TicketCache, allTickets: List<TicketCache>): GraphNode
}
