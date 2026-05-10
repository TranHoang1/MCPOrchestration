package com.orchestrator.mcp.kb.masking

import com.orchestrator.mcp.kb.config.KbMaskingConfig
import com.orchestrator.mcp.kb.masking.model.MaskingResult
import com.orchestrator.mcp.kb.masking.model.PiiDetection
import com.orchestrator.mcp.kb.masking.model.PiiType
import org.slf4j.LoggerFactory

/**
 * Implementation of PII masking using regex-based detection.
 * Replaces PII with tokens like [EMAIL_1], [PHONE_2], etc.
 */
class PiiMaskingEngineImpl(
    private val config: KbMaskingConfig
) : PiiMaskingEngine {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val detector = PiiDetector()
    private val enabledTypes: Set<PiiType> = config.strategies
        .mapNotNull { strategy -> PiiType.entries.find { it.label.equals(strategy, true) } }
        .toSet()

    override fun mask(content: String): MaskingResult {
        val detections = detector.detect(content, enabledTypes)
        if (detections.isEmpty()) {
            return MaskingResult(maskedContent = content, mappings = emptyList())
        }

        val tokenCounters = mutableMapOf<PiiType, Int>()
        val tokenizedDetections = detections.map { detection ->
            val count = tokenCounters.merge(detection.piiType, 1, Int::plus) ?: 1
            val token = formatToken(detection.piiType, count)
            detection.copy(token = token)
        }

        val maskedContent = buildMaskedContent(content, tokenizedDetections)
        logger.debug("Masked {} PII occurrences in content", tokenizedDetections.size)

        return MaskingResult(
            maskedContent = maskedContent,
            mappings = tokenizedDetections
        )
    }

    private fun formatToken(type: PiiType, index: Int): String =
        config.placeholderFormat
            .replace("{TYPE}", type.label)
            .replace("{INDEX}", index.toString())

    private fun buildMaskedContent(
        original: String,
        detections: List<PiiDetection>
    ): String {
        val sb = StringBuilder()
        var lastEnd = 0
        for (detection in detections) {
            sb.append(original, lastEnd, detection.startIndex)
            sb.append(detection.token)
            lastEnd = detection.endIndex
        }
        sb.append(original, lastEnd, original.length)
        return sb.toString()
    }
}
