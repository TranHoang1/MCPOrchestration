package com.orchestrator.mcp.masking.strategy

import com.orchestrator.mcp.kbstore.model.MappingType
import com.orchestrator.mcp.masking.model.PiiMatch

/**
 * Detects Vietnamese ID card numbers:
 * - CMND (old): 9 digits
 * - CCCD (new): 12 digits
 * Priority 4. Accepts false positives for any 9/12 digit number.
 */
class IdCardDetectionStrategy : PiiDetectionStrategy {

    override val mappingType: MappingType = MappingType.ID_CARD
    override val priority: Int = 4

    private val pattern = Regex("\\b\\d{9}\\b|\\b\\d{12}\\b")

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
