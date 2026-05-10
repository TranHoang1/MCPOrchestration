package com.orchestrator.mcp.masking.strategy

import com.orchestrator.mcp.kbstore.model.MappingType
import com.orchestrator.mcp.masking.model.PiiMatch

/**
 * Detects Vietnamese phone numbers (10 digits starting with 0).
 * Covers mobile (03x, 05x, 07x, 08x, 09x) and landline (02x).
 * Priority 2.
 */
class PhoneDetectionStrategy : PiiDetectionStrategy {

    override val mappingType: MappingType = MappingType.PHONE
    override val priority: Int = 2

    private val pattern = Regex("\\b0\\d{9}\\b")

    override fun detect(text: String): List<PiiMatch> {
        return pattern.findAll(text).map { match ->
            PiiMatch(
                startIndex = match.range.first,
                endIndex = match.range.last + 1,
                originalValue = match.value,
                mappingType = mappingType,
                priority = priority
            )
        }.toList()
    }
}
