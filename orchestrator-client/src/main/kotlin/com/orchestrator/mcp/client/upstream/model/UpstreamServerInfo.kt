package com.orchestrator.mcp.client.upstream.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Runtime state for an upstream MCP server.
 */
data class UpstreamServerInfo(
    val name: String,
    val transport: TransportType,
    var status: ServerState = ServerState.STARTING,
    var lastHealthCheck: Instant = Clock.System.now(),
    var reconnectAttempts: Int = 0,
    var toolCount: Int = 0
)
