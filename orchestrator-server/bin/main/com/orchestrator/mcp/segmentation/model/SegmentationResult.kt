package com.orchestrator.mcp.segmentation.model

import kotlinx.serialization.Serializable

/**
 * Result of content segmentation — classified into 3 categories.
 */
@Serializable
data class SegmentationResult(
    val publicContent: String,
    val technicalContent: String,
    val businessRules: String,
    val brSensitivityLevel: BrSensitivityLevel? = null,
    val processingTimeMs: Long = 0,
    val provider: String = "",
    val degraded: Boolean = false
)
