package com.orchestrator.mcp.masking.model

import com.orchestrator.mcp.kbstore.model.MappingType

/**
 * Result of PII masking operation.
 *
 * @property maskedText Text with all detected PII replaced by placeholders
 * @property mappings Ordered list of placeholder-to-original mappings
 */
data class MaskingResult(
    val maskedText: String,
    val mappings: List<PiiMappingEntry>
)

/**
 * Single PII mapping entry produced by the masking engine.
 * Compatible with MTO-26 PiiMapping model for persistence.
 *
 * @property placeholder Placeholder token, e.g., "[PII_PHONE_01]"
 * @property originalValue Original PII text that was masked
 * @property mappingType PII category from MTO-26 MappingType enum
 */
data class PiiMappingEntry(
    val placeholder: String,
    val originalValue: String,
    val mappingType: MappingType
)
