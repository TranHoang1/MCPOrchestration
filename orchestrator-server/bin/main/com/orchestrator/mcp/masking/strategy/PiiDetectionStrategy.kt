package com.orchestrator.mcp.masking.strategy

import com.orchestrator.mcp.kbstore.model.MappingType
import com.orchestrator.mcp.masking.model.PiiMatch

/**
 * Strategy interface for detecting a specific type of PII in text.
 * Each implementation handles one PII category (email, phone, etc.).
 *
 * Strategies are executed in priority order (lower number = higher priority).
 * Thread-safe: implementations must be stateless.
 */
interface PiiDetectionStrategy {

    /** The PII type this strategy detects */
    val mappingType: MappingType

    /** Execution priority — lower value = executed first */
    val priority: Int

    /**
     * Scan text and return all detected PII matches.
     *
     * @param text Text to scan for PII
     * @return List of detected matches with positions
     */
    fun detect(text: String): List<PiiMatch>
}
