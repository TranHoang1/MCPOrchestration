package com.orchestrator.mcp.kb.feature

/**
 * Constants for feature CRUD operations.
 * Shared across handlers and repository.
 */
object FeatureConstants {
    const val DIMENSION_ID = "feature_grouping"
    const val SOURCE_MANUAL = "manual"
    const val SOURCE_AI_DETECTED = "ai_detected"
    const val SOURCE_EPIC_HIERARCHY = "epic_hierarchy"
    const val CREATED_BY_BA = "ba-agent"
    const val CREATED_BY_AI = "ai-sync"
    const val LOCKED_TRUE = "true"
    const val LOCKED_FALSE = "false"
}
