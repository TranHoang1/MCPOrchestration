package com.orchestrator.mcp.sync.model

/**
 * Represents a directed relationship edge between two tickets.
 */
data class TicketRelation(
    val sourceKey: String,
    val targetKey: String,
    val linkType: String,
    val category: RelationCategory
)
