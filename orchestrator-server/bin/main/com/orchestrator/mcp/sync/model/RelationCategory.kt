package com.orchestrator.mcp.sync.model

/**
 * Categories for ticket relationship edges in the graph.
 */
enum class RelationCategory {
    INWARD,
    OUTWARD,
    SUBTASK,
    EPIC
}
