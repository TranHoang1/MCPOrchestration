package com.orchestrator.mcp.security.br

import com.orchestrator.mcp.security.br.model.DlpHeaders

/**
 * DLP enforcement: generates no-cache headers and redacts BR content from logs.
 */
class BrDlpServiceImpl : BrDlpService {

    override fun generateHeaders(): DlpHeaders = DlpHeaders()

    override fun sanitizeForLogging(content: String): String {
        if (content.isBlank()) return "[EMPTY]"
        val length = content.length
        return "[BR_CONTENT_REDACTED length=$length]"
    }
}
