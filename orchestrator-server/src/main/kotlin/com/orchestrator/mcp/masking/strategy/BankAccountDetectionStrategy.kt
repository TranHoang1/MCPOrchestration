package com.orchestrator.mcp.masking.strategy

import com.orchestrator.mcp.kbstore.model.MappingType
import com.orchestrator.mcp.masking.config.MaskingConfig
import com.orchestrator.mcp.masking.model.PiiMatch

/**
 * Detects bank account numbers (10-19 digits) with context awareness.
 * Only masks numbers when context keywords are found nearby.
 * Priority 3.
 */
class BankAccountDetectionStrategy(
    private val config: MaskingConfig
) : PiiDetectionStrategy {

    override val mappingType: MappingType = MappingType.BANK_ACCOUNT
    override val priority: Int = 3

    private val pattern = Regex("\\b\\d{10,19}\\b")

    override fun detect(text: String): List<PiiMatch> {
        return pattern.findAll(text)
            .filter { match -> hasContext(text, match.range) }
            .map { match ->
                PiiMatch(
                    startIndex = match.range.first,
                    endIndex = match.range.last + 1,
                    originalValue = match.value,
                    mappingType = mappingType,
                    priority = priority
                )
            }.toList()
    }

    private fun hasContext(text: String, matchRange: IntRange): Boolean {
        val windowStart = maxOf(0, matchRange.first - config.contextWindow)
        val windowEnd = minOf(text.length, matchRange.last + 1 + config.contextWindow)
        val window = text.substring(windowStart, windowEnd).lowercase()
        return config.contextKeywords.any { keyword ->
            keyword.lowercase() in window
        }
    }
}
