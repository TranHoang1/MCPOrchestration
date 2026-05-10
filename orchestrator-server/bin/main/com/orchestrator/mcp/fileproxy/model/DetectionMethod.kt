package com.orchestrator.mcp.fileproxy.model

/**
 * Method used to detect file parameters in tool schemas.
 */
enum class DetectionMethod {
    SCHEMA_TYPE,
    DESCRIPTION_KEYWORD,
    NAME_PATTERN,
    RUNTIME_RESPONSE
}
