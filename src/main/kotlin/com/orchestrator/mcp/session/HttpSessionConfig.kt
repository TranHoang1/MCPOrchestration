package com.orchestrator.mcp.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for HTTP Streamable transport session management.
 */
@Serializable
data class HttpSessionConfig(
    @SerialName("max_sessions")
    val maxSessions: Int = 100,
    @SerialName("session_ttl_minutes")
    val sessionTtlMinutes: Int = 30,
    @SerialName("event_buffer_size")
    val eventBufferSize: Int = 1000,
    @SerialName("cleanup_interval_seconds")
    val cleanupIntervalSeconds: Int = 60
)
