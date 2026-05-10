package com.orchestrator.mcp.ocr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for the OCR service.
 * Loaded from application.yml under orchestrator.ocr.
 */
@Serializable
data class OcrConfig(
    val enabled: Boolean = true,
    @SerialName("server-name")
    val serverName: String = "markitdown",
    @SerialName("tool-name")
    val toolName: String = "convert_to_markdown",
    @SerialName("timeout-seconds")
    val timeoutSeconds: Int = 30,
    @SerialName("max-file-size-mb")
    val maxFileSizeMb: Int = 20,
    @SerialName("supported-formats")
    val supportedFormats: List<String> = listOf(
        "image/png",
        "image/jpeg",
        "image/tiff"
    )
)
