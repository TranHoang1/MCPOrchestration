package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.graph.GraphService
import com.orchestrator.mcp.kb.graph.model.ViewMode
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * MCP tool handler for kb_graph.
 * Returns project graph or subgraph for 3D visualization.
 */
class KbGraphHandler(
    private val graphService: GraphService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val toolName = "kb_graph"

    override val description = "Get Jira project ticket graph for 3D visualization. " +
        "Returns nodes (tickets) and edges (relationships) with visual properties. " +
        "Supports view modes: hierarchy, dependency, team."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("project_key") {
                put("type", "string")
                put("description", "Jira project key (e.g., MTO)")
            }
            putJsonObject("issue_key") {
                put("type", "string")
                put("description", "Center issue key for subgraph (optional)")
            }
            putJsonObject("view") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("hierarchy"); add("dependency"); add("team")
                })
                put("default", "hierarchy")
                put("description", "View mode for node coloring/sizing")
            }
            putJsonObject("depth") {
                put("type", "integer")
                put("default", 2)
                put("minimum", 1)
                put("maximum", 5)
                put("description", "BFS depth for subgraph (1-5)")
            }
        },
        required = listOf("project_key")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val projectKey = HandlerUtils.requireString(arguments, "project_key")
                ?: return HandlerUtils.errorResult("KB_VALIDATION", "project_key is required")

            val issueKey = HandlerUtils.optionalString(arguments, "issue_key")
            val view = ViewMode.fromString(HandlerUtils.optionalString(arguments, "view"))
            val depth = HandlerUtils.optionalInt(arguments, "depth", 2).coerceIn(1, 5)

            val response = if (issueKey != null) {
                graphService.getSubgraph(projectKey, issueKey, depth, view)
            } else {
                graphService.getProjectGraph(projectKey, view)
            }

            HandlerUtils.successResult(json.encodeToString(response))
        } catch (e: Exception) {
            logger.error("kb_graph failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Graph query failed: ${e.message}")
        }
    }
}
