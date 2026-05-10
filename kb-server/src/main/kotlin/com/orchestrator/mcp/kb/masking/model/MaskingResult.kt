package com.orchestrator.mcp.kb.masking.model

/**
 * Result of PII masking operation.
 * Contains masked content and the mapping of tokens to original values.
 */
data class MaskingResult(
    val maskedContent: String,
    val mappings: List<PiiDetection>
)

/**
 * A single PII detection with its replacement token.
 */
data class PiiDetection(
    val originalValue: String,
    val token: String,
    val piiType: PiiType,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * Supported PII types for detection and masking.
 */
enum class PiiType(val label: String) {
    EMAIL("EMAIL"),
    PHONE("PHONE"),
    BANK_ACCOUNT("BANK_ACCOUNT"),
    ID_CARD("ID_CARD"),
    NAME("NAME");

    companion object {
        fun fromLabel(label: String): PiiType =
            entries.first { it.label.equals(label, ignoreCase = true) }
    }
}
