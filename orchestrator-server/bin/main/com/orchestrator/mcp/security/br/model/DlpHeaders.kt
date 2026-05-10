package com.orchestrator.mcp.security.br.model

/**
 * DLP (Data Loss Prevention) response headers for BR content.
 * Applied to all responses containing decrypted Business Rules.
 */
data class DlpHeaders(
    val cacheControl: String = "no-store, no-cache, must-revalidate",
    val pragma: String = "no-cache",
    val contentTypeOptions: String = "nosniff",
    val dlpFlag: String = "enforced"
) {
    fun toMap(): Map<String, String> = mapOf(
        "Cache-Control" to cacheControl,
        "Pragma" to pragma,
        "X-Content-Type-Options" to contentTypeOptions,
        "X-BR-DLP" to dlpFlag
    )
}
