package com.orchestrator.mcp.bridge.codeintel.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for the local code intelligence system.
 * Loaded from .bridge/code-intelligence.json in workspace root.
 */
@Serializable
data class CodeIntelConfig(
    val enabled: Boolean = true,
    @SerialName("exclude_patterns")
    val excludePatterns: List<String> = DEFAULT_EXCLUDES,
    @SerialName("max_file_size_kb")
    val maxFileSizeKb: Int = 500,
    @SerialName("max_depth")
    val maxDepth: Int = 20,
    @SerialName("batch_size")
    val batchSize: Int = 100,
    @SerialName("scan_timeout_ms")
    val scanTimeoutMs: Int = 100
) {
    companion object {
        val DEFAULT_EXCLUDES = listOf(
            "node_modules/**", "build/**", "dist/**",
            ".git/**", ".gradle/**", "__pycache__/**",
            "*.min.js", "*.bundle.js"
        )
    }
}
