package com.orchestrator.mcp.masking

import com.orchestrator.mcp.kbstore.model.MappingType
import com.orchestrator.mcp.masking.config.MaskingConfig
import com.orchestrator.mcp.masking.model.MaskingResult
import com.orchestrator.mcp.masking.model.PiiMappingEntry
import com.orchestrator.mcp.masking.model.PiiMatch
import com.orchestrator.mcp.masking.strategy.PiiDetectionStrategy
import org.slf4j.LoggerFactory

/**
 * Default implementation of PiiMaskingEngine.
 * Orchestrates strategy execution, resolves overlaps, and builds result.
 */
class PiiMaskingEngineImpl(
    private val strategies: List<PiiDetectionStrategy>,
    private val config: MaskingConfig
) : PiiMaskingEngine {

    private val logger = LoggerFactory.getLogger(PiiMaskingEngineImpl::class.java)

    override fun mask(text: String): MaskingResult {
        if (text.isBlank()) return MaskingResult(text, emptyList())

        val allMatches = collectMatches(text)
        if (allMatches.isEmpty()) return MaskingResult(text, emptyList())

        val resolved = removeOverlaps(allMatches)
        return buildResult(text, resolved)
    }

    private fun collectMatches(text: String): List<PiiMatch> {
        return strategies
            .filter { it.mappingType in config.enabledStrategies }
            .sortedBy { it.priority }
            .flatMap { strategy -> executeStrategy(strategy, text) }
    }

    private fun executeStrategy(
        strategy: PiiDetectionStrategy,
        text: String
    ): List<PiiMatch> {
        return runCatching { strategy.detect(text) }
            .onFailure { e ->
                logger.warn(
                    "Strategy {} failed: {}",
                    strategy.mappingType, e.message
                )
            }
            .getOrElse { emptyList() }
    }

    private fun removeOverlaps(matches: List<PiiMatch>): List<PiiMatch> {
        val sorted = matches.sortedWith(
            compareBy({ it.startIndex }, { it.priority })
        )
        val resolved = mutableListOf<PiiMatch>()
        var lastEnd = -1
        for (match in sorted) {
            if (match.startIndex >= lastEnd) {
                resolved.add(match)
                lastEnd = match.endIndex
            }
        }
        return resolved
    }

    private fun buildResult(
        text: String,
        matches: List<PiiMatch>
    ): MaskingResult {
        val counters = mutableMapOf<MappingType, Int>()
        val mappings = mutableListOf<PiiMappingEntry>()
        var maskedText = text

        for (match in matches.sortedByDescending { it.startIndex }) {
            val count = counters.merge(match.mappingType, 1, Int::plus)!!
            val placeholder = formatPlaceholder(match.mappingType, count)
            maskedText = maskedText.replaceRange(
                match.startIndex, match.endIndex, placeholder
            )
            mappings.add(
                PiiMappingEntry(placeholder, match.originalValue, match.mappingType)
            )
        }

        return MaskingResult(maskedText, mappings.reversed())
    }

    private fun formatPlaceholder(type: MappingType, count: Int): String {
        val label = when (type) {
            MappingType.NAME -> "NAME"
            MappingType.ID_CARD -> "ID"
            MappingType.PHONE -> "PHONE"
            MappingType.BANK_ACCOUNT -> "ACCOUNT"
            MappingType.EMAIL -> "EMAIL"
        }
        return "[PII_${label}_${count.toString().padStart(2, '0')}]"
    }
}
