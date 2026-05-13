package com.orchestrator.mcp.kb.config

import org.slf4j.LoggerFactory

/**
 * Resolves ${ENV_VAR} placeholders in raw YAML content.
 * Falls back to literal text if env var is not set.
 */
object KbEnvVarResolver {

    private val logger = LoggerFactory.getLogger(KbEnvVarResolver::class.java)
    private val ENV_VAR_PATTERN = Regex("""\$\{([A-Z_][A-Z0-9_]*)\}""")

    /**
     * Replace all ${VAR} patterns with corresponding environment variable values.
     * Unresolved patterns are left as-is (for backward compatibility).
     */
    fun resolve(content: String): String {
        var resolvedCount = 0
        val result = ENV_VAR_PATTERN.replace(content) { matchResult ->
            val varName = matchResult.groupValues[1]
            val envValue = System.getenv(varName)
            if (envValue != null) {
                resolvedCount++
                envValue
            } else {
                matchResult.value
            }
        }
        if (resolvedCount > 0) {
            logger.info("Resolved {} environment variable(s) in config", resolvedCount)
        }
        return result
    }
}
