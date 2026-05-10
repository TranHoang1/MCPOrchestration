package com.orchestrator.mcp.brmasking.model

import kotlinx.serialization.Serializable

/**
 * Categories for Business Rules classification.
 */
@Serializable
enum class BrCategory(val label: String) {
    RATE("Interest rates, fees, pricing"),
    APPROVAL("Approval conditions, criteria"),
    THRESHOLD("Risk thresholds, limits"),
    PROCESS("Business processes, workflows"),
    COMMISSION("Commissions, agent fees"),
    UNKNOWN("Unclassified business rule")
}
