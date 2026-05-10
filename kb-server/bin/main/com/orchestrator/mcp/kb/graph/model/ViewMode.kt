package com.orchestrator.mcp.kb.graph.model

import kotlinx.serialization.Serializable

/**
 * Available view modes for graph visualization.
 * Each mode applies different color/size/group strategies.
 */
@Serializable
enum class ViewMode {
    HIERARCHY,
    DEPENDENCY,
    TEAM,
    COMPLEXITY,
    FUNCTIONAL,
    BUSINESS,
    TIMELINE;

    companion object {
        fun fromString(value: String?): ViewMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: HIERARCHY
        }
    }
}
