package com.orchestrator.mcp.synctools

import com.orchestrator.mcp.sync.TicketCacheRepository
import com.orchestrator.mcp.sync.TicketGraphRepository
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.*

/**
 * Handles jira_ticket_graph tool invocations.
 * Returns nodes and edges for visualization or analysis.
 */
class GraphToolHandler(
    private val graphRepository: TicketGraphRepository,
    private val ticketCacheRepository: TicketCacheRepository
) {

    private val json = Json { encodeDefaults = true }

    suspend fun handle(arguments: JsonObject?): CallToolResult {
        val projectKey = arguments?.get("projectKey")?.jsonPrimitive?.contentOrNull
            ?: return errorResult("projectKey is required")
        val issueKey = arguments["issueKey"]?.jsonPrimitive?.contentOrNull
        val depth = arguments["depth"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 5) ?: 2

        return try {
            val edges = if (issueKey != null) {
                traverseFromNode(issueKey, depth)
            } else {
                graphRepository.findAllForProject(projectKey)
            }

            val nodeKeys = edges.flatMap { listOf(it.sourceKey, it.targetKey) }.toSet()
            val tickets = ticketCacheRepository.findByProject(projectKey)
            val nodeMap = tickets.associateBy { it.ticketKey }

            val nodesJson = buildJsonArray {
                nodeKeys.take(1000).forEach { key ->
                    val ticket = nodeMap[key]
                    addJsonObject {
                        put("key", key)
                        put("summary", ticket?.summary ?: "")
                        put("status", ticket?.status ?: "unknown")
                        put("issueType", ticket?.issueType ?: "unknown")
                        put("labels", buildJsonArray {
                            ticket?.labels?.forEach { label -> add(label) }
                        })
                        put("createdAt", ticket?.createdAt?.toString() ?: "")
                    }
                }
            }

            val edgesJson = buildJsonArray {
                edges.forEach { edge ->
                    addJsonObject {
                        put("source", edge.sourceKey)
                        put("target", edge.targetKey)
                        put("type", edge.linkType)
                        put("category", edge.category.name.lowercase())
                    }
                }
            }

            val response = buildJsonObject {
                put("nodes", nodesJson)
                put("edges", edgesJson)
                putJsonObject("metadata") {
                    put("totalNodes", nodeKeys.size)
                    put("totalEdges", edges.size)
                    put("projectKey", projectKey)
                    if (issueKey != null) put("centerIssue", issueKey)
                    put("depth", depth)
                }
            }

            CallToolResult(content = listOf(TextContent(text = json.encodeToString(JsonObject.serializer(), response))))
        } catch (e: Exception) {
            errorResult("Graph query failed: ${e.message}")
        }
    }

    private suspend fun traverseFromNode(issueKey: String, maxDepth: Int): List<com.orchestrator.mcp.sync.model.TicketRelation> {
        val visited = mutableSetOf<String>()
        val allEdges = mutableListOf<com.orchestrator.mcp.sync.model.TicketRelation>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(issueKey to 0)

        while (queue.isNotEmpty()) {
            val (current, depth) = queue.removeFirst()
            if (depth >= maxDepth || !visited.add(current)) continue

            val outgoing = graphRepository.findOutgoing(current)
            val incoming = graphRepository.findIncoming(current)
            allEdges.addAll(outgoing)
            allEdges.addAll(incoming)

            outgoing.forEach { queue.add(it.targetKey to depth + 1) }
            incoming.forEach { queue.add(it.sourceKey to depth + 1) }
        }

        return allEdges.distinctBy { Triple(it.sourceKey, it.targetKey, it.linkType) }
    }

    private fun errorResult(message: String): CallToolResult {
        val error = buildJsonObject { put("error", message) }
        return CallToolResult(content = listOf(TextContent(text = error.toString())), isError = true)
    }
}
