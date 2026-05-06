package com.orchestrator.mcp.fileproxy.model

/**
 * Result of schema-based detection of file parameters.
 */
data class DetectionResult(
    val toolName: String,
    val serverName: String,
    val paramName: String,
    val direction: ProxyDirection,
    val method: DetectionMethod,
    val confidence: Float
)
