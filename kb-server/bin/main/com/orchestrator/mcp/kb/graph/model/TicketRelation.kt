package com.orchestrator.mcp.kb.graph.model

/**
 * Represents a directed relationship edge between two tickets.
 * Local copy — decouples kb-server from orchestrator-server sync package.
 */
data class TicketRelation(
    val sourceKey: String,
    val targetKey: String,
    val linkType: String,
    val category: RelationCategory
)

/**
 * Categories for ticket relationship edges in the graph.
 */
enum class RelationCategory {
    INWARD,
    OUTWARD,
    SUBTASK,
    EPIC
}
