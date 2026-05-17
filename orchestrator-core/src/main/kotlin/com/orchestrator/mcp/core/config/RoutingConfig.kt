package com.orchestrator.mcp.core.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for the routing table feature (MTO-132).
 * Defines which tools should be executed locally vs remotely by bridge clients.
 */
@Serializable
data class RoutingConfig(
    val enabled: Boolean = false,
    @SerialName("defaultLocation")
    val defaultLocation: String = "remote",
    @SerialName("refreshIntervalMs")
    val refreshIntervalMs: Long = 60000,
    @SerialName("localServers")
    val localServers: List<LocalServerRouting> = emptyList()
)

@Serializable
data class LocalServerRouting(
    val name: String = "",
    val tools: List<String> = emptyList(),
    val fallback: String? = null
)
