package com.orchestrator.mcp.synctools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Registers sync-related MCP tools with the server.
 * Tools: jira_project_sync, jira_sync_status, jira_ticket_graph
 */
class SyncToolRegistrar(
    private val syncToolHandler: SyncToolHandler,
    private val statusToolHandler: StatusToolHandler,
    private val graphToolHandler: GraphToolHandler
) {

    private val logger = LoggerFactory.getLogger(SyncToolRegistrar::class.java)

    fun register(server: Server) {
        registerProjectSync(server)
        registerSyncStatus(server)
        registerTicketGraph(server)
        logger.info("Registered 3 sync tools: jira_project_sync, jira_sync_status, jira_ticket_graph")
    }

    private fun registerProjectSync(server: Server) {
        server.addTool(
            name = "jira_project_sync",
            description = "Trigger Jira project sync. Starts background scan job. Returns immediately with status.",
            inputSchema = buildSyncSchema()
        ) { request -> syncToolHandler.handle(request.arguments) }
    }

    private fun registerSyncStatus(server: Server) {
        server.addTool(
            name = "jira_sync_status",
            description = "Check sync progress for a Jira project. Returns status, progress %, and counts.",
            inputSchema = buildStatusSchema()
        ) { request -> statusToolHandler.handle(request.arguments) }
    }

    private fun registerTicketGraph(server: Server) {
        server.addTool(
            name = "jira_ticket_graph",
            description = "Query Jira ticket relationship graph. Returns nodes and edges for visualization.",
            inputSchema = buildGraphSchema()
        ) { request -> graphToolHandler.handle(request.arguments) }
    }

    private fun buildSyncSchema() = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("projectKey") { put("type", "string"); put("description", "Jira project key (e.g. MTO)") }
            putJsonObject("fullSync") { put("type", "boolean"); put("default", false); put("description", "Force full scan ignoring last sync time") }
        },
        required = listOf("projectKey")
    )

    private fun buildStatusSchema() = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("projectKey") { put("type", "string"); put("description", "Jira project key") }
        },
        required = listOf("projectKey")
    )

    private fun buildGraphSchema() = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("projectKey") { put("type", "string"); put("description", "Jira project key") }
            putJsonObject("issueKey") { put("type", "string"); put("description", "Center issue for subgraph traversal") }
            putJsonObject("depth") { put("type", "integer"); put("default", 2); put("minimum", 1); put("maximum", 5) }
        },
        required = listOf("projectKey")
    )
}
