package com.orchestrator.mcp.security.br

import com.orchestrator.mcp.security.br.model.DlpHeaders

/**
 * Data Loss Prevention service for BR content.
 * Generates response headers and sanitizes content for logging.
 */
interface BrDlpService {

    /** Generate DLP headers for BR response. */
    fun generateHeaders(): DlpHeaders

    /** Sanitize BR content for safe logging (redacts actual content). */
    fun sanitizeForLogging(content: String): String
}
