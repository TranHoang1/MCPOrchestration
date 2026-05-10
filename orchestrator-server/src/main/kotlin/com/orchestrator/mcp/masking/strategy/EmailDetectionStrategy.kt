package com.orchestrator.mcp.masking.strategy

import com.orchestrator.mcp.kbstore.model.MappingType
import com.orchestrator.mcp.masking.model.PiiMatch

/**
 * Detects email addresses using standard email regex.
 * Priority 1 (highest) — most specific pattern with lowest false positive rate.
 */
class EmailDetectionStrategy : PiiDetectionStrategy {

    override val mappingType: MappingType = MappingType.EMAIL
    override val priority: Int = 1

    private val pattern = Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}")

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
