package com.orchestrator.mcp.kb.store.model

/**
 * Business Rules sensitivity classification.
 * Determines access control level for the business_rules column.
 */
enum class BrSensitivityLevel(val level: Int) {
    CONFIDENTIAL(1),
    INTERNAL(2),
    RESTRICTED(3);

    companion object {
        fun fromLevel(level: Int): BrSensitivityLevel =
            entries.firstOrNull { it.level == level }
                ?: throw IllegalArgumentException(
                    "Invalid sensitivity level: $level. Must be 1, 2, or 3."
                )
    }
}
