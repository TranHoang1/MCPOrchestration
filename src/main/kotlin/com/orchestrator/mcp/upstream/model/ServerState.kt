package com.orchestrator.mcp.upstream.model

/**
 * Server health state machine.
 * Transitions: STARTING → CONNECTED | ERROR
 *              CONNECTED → DISCONNECTED
 *              DISCONNECTED → STARTING (auto-reconnect) | ERROR (max attempts)
 *              ERROR → STARTING (manual retry)
 */
enum class ServerState {
    STARTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

/** Transport type for upstream MCP server connections */
enum class TransportType {
    STDIO,
    HTTP
}
