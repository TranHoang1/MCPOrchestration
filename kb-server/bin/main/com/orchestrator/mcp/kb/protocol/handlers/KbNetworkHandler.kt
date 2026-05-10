package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.network.NetworkService
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * MCP tool handler for kb_network.
 * Returns feature network (semantic link graph) for visualization.
 */
class KbNetworkHandler(
    private val networkService: NetworkService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val toolName = "kb_network"

    override val description = "Get feature network graph showing semantic relationships " +
        "between KB entries. Returns N-hop neighborhood centered on an issue key, " +
        "or full project network."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("issue_key") {
                put("type", "string")
                put("description", "Center issue key for N-hop neighborhood")
            }
            putJsonObject("project_key") {
                put("type", "string")
                put("description", "Project key for full network (used when issue_key is absent)")
            }
            putJsonObject("hops") {
                put("type", "integer")
                put("default", 2)
                put("minimum", 1)
                put("maximum", 5)
                put("description", "Number of hops for neighborhood traversal")
            }
        },
        required = listOf()
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val issueKey = HandlerUtils.optionalString(arguments, "issue_key")
            val projectKey = HandlerUtils.optionalString(arguments, "project_key")
            val hops = HandlerUtils.optionalInt(arguments, "hops", 2).coerceIn(1, 5)

            val response = if (issueKey != null) {
                networkService.getNetwork(issueKey, hops)
            } else {
                networkService.getFullNetwork(projectKey)
            }

            HandlerUtils.successResult(json.encodeToString(response))
        } catch (e: Exception) {
            logger.error("kb_network failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Network query failed: ${e.message}")
        }
    }
}
