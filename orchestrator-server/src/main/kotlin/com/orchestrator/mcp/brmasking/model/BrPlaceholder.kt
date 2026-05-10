package com.orchestrator.mcp.brmasking.model

import kotlinx.serialization.Serializable

/**
 * Represents a masked business rule placeholder with encrypted original.
 */
@Serializable
data class BrPlaceholder(
    val id: String,
    val category: BrCategory,
    val encryptedOriginal: String,
    val iv: String,
    val summary: String
)
