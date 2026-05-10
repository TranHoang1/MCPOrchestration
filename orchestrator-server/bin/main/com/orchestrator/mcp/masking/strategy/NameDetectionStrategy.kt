package com.orchestrator.mcp.masking.strategy

import com.orchestrator.mcp.kbstore.model.MappingType
import com.orchestrator.mcp.masking.config.MaskingConfig
import com.orchestrator.mcp.masking.model.PiiMatch

/**
 * Detects Vietnamese person names using heuristic approach.
 * Looks for prefix indicators (Ông, Bà, Anh, Chị, KH, etc.)
 * followed by 2-4 capitalized Vietnamese words.
 * Priority 5 (lowest) — highest false positive/negative risk.
 */
class NameDetectionStrategy(
    private val config: MaskingConfig
) : PiiDetectionStrategy {

    override val mappingType: MappingType = MappingType.NAME
    override val priority: Int = 5

    override fun detect(text: String): List<PiiMatch> {
        val regex = buildNameRegex()
        return regex.findAll(text).mapNotNull { match ->
            extractNameMatch(match)
        }.toList()
    }

    private fun buildNameRegex(): Regex {
        val prefixPattern = config.namePrefixes
            .joinToString("|") { Regex.escape(it) }
        // Use Unicode uppercase letter \p{Lu} to avoid range issues with Vietnamese chars
        return Regex(
            "(?:$prefixPattern)\\s+(\\p{Lu}\\p{Ll}+(?:\\s+\\p{Lu}\\p{Ll}+){1,3})"
        )
    }

    private fun extractNameMatch(match: MatchResult): PiiMatch? {
        val nameGroup = match.groups[1] ?: return null
        return PiiMatch(
            startIndex = nameGroup.range.first,
            endIndex = nameGroup.range.last + 1,
            originalValue = nameGroup.value,
            mappingType = mappingType,
            priority = priority
        )
    }
}
