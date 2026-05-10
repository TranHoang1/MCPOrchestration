package com.orchestrator.mcp.brmasking

import com.orchestrator.mcp.brmasking.model.BrMaskingResult
import com.orchestrator.mcp.brmasking.model.BrPlaceholder

/**
 * Service for AI-based Business Rules masking.
 * Identifies individual BRs, categorizes them, and replaces with placeholders.
 * Original content is encrypted with AES-256-GCM.
 */
interface BrMaskingService {

    /**
     * Masks business rules in the given content.
     * Each identified BR is replaced with a placeholder and encrypted.
     */
    suspend fun maskBusinessRules(brContent: String): BrMaskingResult

    /**
     * Decrypts and returns the original BR text for an authorized unmask request.
     */
    fun unmask(placeholder: BrPlaceholder): String
}
