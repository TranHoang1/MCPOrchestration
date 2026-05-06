package com.orchestrator.mcp.discovery.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class FindToolsResponse(
    val tools: List<ToolResult>,
    @SerialName("search_mode")
    val searchMode: String,
    @SerialName("total_indexed")
    val totalIndexed: Int
)

@Serializable
data class ToolResult(
    val name: String,
    val description: String,
    @SerialName("input_schema")
    val inputSchema: JsonObject? = null,
    @SerialName("server_name")
    val serverName: String,
    @SerialName("server_status")
    val serverStatus: String = "CONNECTED",
    @SerialName("similarity_score")
    val similarityScore: Float
)
