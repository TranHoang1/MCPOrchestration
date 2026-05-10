package com.orchestrator.mcp.brmasking.model

import kotlinx.serialization.Serializable

/**
 * Result of BR masking operation.
 */
@Serializable
data class BrMaskingResult(
    val maskedBr: String,
    val brPlaceholders: List<BrPlaceholder>,
    val processingTimeMs: Long = 0
)
