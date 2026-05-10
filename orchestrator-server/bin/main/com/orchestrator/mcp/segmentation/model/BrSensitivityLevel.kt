package com.orchestrator.mcp.segmentation.model

import kotlinx.serialization.Serializable

/**
 * Sensitivity classification for Business Rules content.
 */
@Serializable
enum class BrSensitivityLevel(val label: String, val description: String) {
    LEVEL_1("Confidential", "Interest rates, fees, commissions, pricing"),
    LEVEL_2("Internal", "Approval conditions, risk thresholds, scoring"),
    LEVEL_3("Restricted", "General processes, SLAs, standard procedures")
}
