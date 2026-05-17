package com.orchestrator.mcp.bridge.codeintel.model

import kotlinx.serialization.Serializable

/**
 * Represents a detected project module (by build file presence).
 */
@Serializable
data class ModuleEntry(
    val id: Long = 0,
    val name: String,
    val path: String,
    val description: String? = null,
    val summary: String? = null
)
