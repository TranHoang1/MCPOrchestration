package com.orchestrator.mcp.kb.synctools

import com.orchestrator.mcp.kb.graph.model.TicketRelation
import com.orchestrator.mcp.kb.graph.repository.TicketCacheRepository
import com.orchestrator.mcp.kb.graph.repository.TicketGraphRepository
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.protocol.handlers.HandlerUtils
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Unified handler for jira_ticket_graph tool.
 * Direct port of GraphToolHandler from orchestrator-server.
 * BFS traversal logic preserved with node limit of 1000.
 */
class JiraTicketGraphHandler(
    private val graphRepository: TicketGraphRepository,
    private val ticketCacheRepository: TicketCacheRepository
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { encodeDefaults = true }

    override val toolName = "jira_ticket_graph"

    override val description =
        "Query ticket relationship graph with BFS traversal. " +
            "Returns nodes and edges for visualization or analysis."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("projectKey") {
                put("type", "string")
                put("description", "Jira project key (e.g., MTO)")
            }
            putJsonObject("issueKey") {
                put("type", "string")
                put("description", "Center issue key for subgraph traversal (optional)")
            }
            putJsonObject("depth") {
                put("type", "integer")
                put("default", 2)
                put("minimum", 1)
                put("maximum", 5)
                put("description", "BFS depth for subgraph (1-5)")
            }
        },
        required = listOf("projectKey")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        val projectKey = HandlerUtils.requireString(arguments, "projectKey")
            ?: return HandlerUtils.errorResult("KB_VALIDATION", "projectKey is required")
        val issueKey = HandlerUtils.optionalString(arguments, "issueKey")
        val depth = HandlerUtils.optionalInt(arguments, "depth", 2).coerceIn(1, 5)

        return try {
            val edges = fetchEdges(projectKey, issueKey, depth)
            val response = buildGraphResponse(projectKey, issueKey, depth, edges)
            CallToolResult(content = listOf(TextContent(
                text = json.encodeToString(JsonObject.serializer(), response)
            )))
        } catch (e: Exception) {
            logger.error("jira_ticket_graph failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Graph query failed: ${e.message}")
        }
    }

    private suspend fun fetchEdges(
        projectKey: String,
        issueKey: String?,
        depth: Int
    ): List<TicketRelation> {
        return if (issueKey != null) {
            traverseFromNode(issueKey, depth)
        } else {
            graphRepository.findAllForProject(projectKey)
        }
    }

    private suspend fun buildGraphResponse(
        projectKey: String,
        issueKey: String?,
        depth: Int,
        edges: List<TicketRelation>
    ): JsonObject {
        val nodeKeys = edges.flatMap { listOf(it.sourceKey, it.targetKey) }.toSet()
        val tickets = ticketCacheRepository.findByProject(projectKey)
        val nodeMap = tickets.associateBy { it.ticketKey }
        return buildJsonObject {
            put("nodes", buildNodesJson(nodeKeys, nodeMap))
            put("edges", buildEdgesJson(edges))
            putJsonObject("metadata") {
                put("totalNodes", nodeKeys.size)
                put("totalEdges", edges.size)
                put("projectKey", projectKey)
                issueKey?.let { put("centerIssue", it) }
                put("depth", depth)
            }
        }
    }

    private fun buildNodesJson(
        nodeKeys: Set<String>,
        nodeMap: Map<String, com.orchestrator.mcp.kb.graph.model.TicketCache>
    ): JsonArray {
        return buildJsonArray {
            nodeKeys.take(NODE_LIMIT).forEach { key ->
                addJsonObject {
                    val ticket = nodeMap[key]
                    put("key", key)
                    put("summary", ticket?.summary ?: "")
                    put("status", ticket?.status ?: "unknown")
                    put("issueType", ticket?.issueType ?: "unknown")
                    put("labels", buildJsonArray {
                        ticket?.labels?.forEach { add(it) }
                    })
                    put("createdAt", ticket?.createdAt?.toString() ?: "")
                }
            }
        }
    }

    private fun buildEdgesJson(edges: List<TicketRelation>): JsonArray {
        return buildJsonArray {
            edges.forEach { edge ->
                addJsonObject {
                    put("source", edge.sourceKey)
                    put("target", edge.targetKey)
                    put("type", edge.linkType)
                    put("category", edge.category.name.lowercase())
                }
            }
        }
    }

    private suspend fun traverseFromNode(
        issueKey: String,
        maxDepth: Int
    ): List<TicketRelation> {
        val visited = mutableSetOf<String>()
        val allEdges = mutableListOf<TicketRelation>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(issueKey to 0)

        while (queue.isNotEmpty()) {
            val (current, currentDepth) = queue.removeFirst()
            if (currentDepth >= maxDepth || !visited.add(current)) continue
            collectEdges(current, allEdges, queue, currentDepth)
        }
        return allEdges.distinctBy { Triple(it.sourceKey, it.targetKey, it.linkType) }
    }

    private suspend fun collectEdges(
        current: String,
        allEdges: MutableList<TicketRelation>,
        queue: ArrayDeque<Pair<String, Int>>,
        currentDepth: Int
    ) {
        val outgoing = graphRepository.findOutgoing(current)
        val incoming = graphRepository.findIncoming(current)
        allEdges.addAll(outgoing)
        allEdges.addAll(incoming)
        outgoing.forEach { queue.add(it.targetKey to currentDepth + 1) }
        incoming.forEach { queue.add(it.sourceKey to currentDepth + 1) }
    }

    companion object {
        private const val NODE_LIMIT = 1000
    }
}
