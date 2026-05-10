package com.orchestrator.mcp.kb.masking

import com.orchestrator.mcp.kb.masking.model.PiiDetection
import com.orchestrator.mcp.kb.masking.model.PiiType

/**
 * Regex-based PII detector.
 * Detects email, phone, bank account, ID card, and name patterns.
 */
class PiiDetector {

    private val patterns: Map<PiiType, Regex> = mapOf(
        PiiType.EMAIL to Regex(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"
        ),
        PiiType.PHONE to Regex(
            "(?:\\+84|0)\\d{9,10}|\\(\\d{2,4}\\)\\s?\\d{6,8}"
        ),
        PiiType.BANK_ACCOUNT to Regex(
            "\\b\\d{9,19}\\b"
        ),
        PiiType.ID_CARD to Regex(
            "\\b(?:\\d{9}|\\d{12})\\b"
        )
    )

    /**
     * Detect all PII occurrences in the given text.
     * Returns detections sorted by start index.
     */
    fun detect(text: String, enabledTypes: Set<PiiType>): List<PiiDetection> {
        val detections = mutableListOf<PiiDetection>()

        for ((type, pattern) in patterns) {
            if (type !in enabledTypes) continue
            pattern.findAll(text).forEach { match ->
                detections.add(
                    PiiDetection(
                        originalValue = match.value,
                        token = "", // assigned later by masking engine
                        piiType = type,
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1
                    )
                )
            }
        }

        return detections
            .sortedBy { it.startIndex }
            .filterOverlapping()
    }

    /**
     * Remove overlapping detections, keeping the longer match.
     */
    private fun List<PiiDetection>.filterOverlapping(): List<PiiDetection> {
        if (isEmpty()) return this
        val result = mutableListOf(first())
        for (i in 1 until size) {
            val prev = result.last()
            val curr = this[i]
            if (curr.startIndex >= prev.endIndex) {
                result.add(curr)
            } else if (curr.originalValue.length > prev.originalValue.length) {
                result[result.lastIndex] = curr
            }
        }
        return result
    }
}
