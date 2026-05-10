package com.orchestrator.mcp.linking.model

/**
 * Configuration for semantic entity linking.
 */
data class LinkingConfig(
    val similarityThreshold: Double = 0.75,
    val defaultTopK: Int = 10,
    val maxLinksPerEntry: Int = 20,
    val collectionName: String = "kb_entries"
)
