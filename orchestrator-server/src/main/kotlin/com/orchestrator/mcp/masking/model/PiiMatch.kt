package com.orchestrator.mcp.masking.model

import com.orchestrator.mcp.kbstore.model.MappingType

/**
 * Representation of a detected PII match within text.
 * Used during masking pipeline processing.
 *
 * @property startIndex Start position in original text (inclusive)
 * @property endIndex End position in original text (exclusive)
 * @property originalValue The matched PII text
 * @property mappingType PII category
 * @property priority Strategy priority that detected this match (lower = higher priority)
 */
data class PiiMatch(
    val startIndex: Int,
    val endIndex: Int,
    val originalValue: String,
    val mappingType: MappingType,
    val priority: Int
)
