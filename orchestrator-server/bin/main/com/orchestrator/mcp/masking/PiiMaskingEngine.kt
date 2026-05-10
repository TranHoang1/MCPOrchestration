package com.orchestrator.mcp.masking

import com.orchestrator.mcp.masking.model.MaskingResult

/**
 * Engine for detecting and masking PII (Personally Identifiable Information)
 * in text content. Uses regex-based strategies optimized for Vietnamese
 * financial context.
 *
 * Thread-safe: stateless per call, safe for concurrent use.
 */
interface PiiMaskingEngine {

    /**
     * Mask all detected PII in the given text.
     *
     * @param text Raw text potentially containing PII
     * @return MaskingResult with masked text and mapping list
     */
    fun mask(text: String): MaskingResult
}
