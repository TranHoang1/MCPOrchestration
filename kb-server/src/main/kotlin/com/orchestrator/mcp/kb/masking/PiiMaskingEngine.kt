package com.orchestrator.mcp.kb.masking

import com.orchestrator.mcp.kb.masking.model.MaskingResult

/**
 * Interface for PII masking operations.
 * Detects and replaces PII with placeholder tokens.
 */
interface PiiMaskingEngine {

    /**
     * Mask all detected PII in the content.
     * Returns masked content and the token-to-original mappings.
     */
    fun mask(content: String): MaskingResult
}
