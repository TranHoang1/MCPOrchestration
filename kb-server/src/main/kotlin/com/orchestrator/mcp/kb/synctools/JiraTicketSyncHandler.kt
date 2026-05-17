package com.orchestrator.mcp.kb.synctools

import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.protocol.handlers.HandlerUtils
import com.orchestrator.mcp.sync.pipeline.SyncOrchestrator
import com.orchestrator.mcp.sync.pipeline.model.SyncOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handler for jira_ticket_sync tool (NEW).
 * Synchronous single-ticket sync using SyncOrchestrator.
 * Does NOT go through queue — executes immediately.
 */
class JiraTicketSyncHandler(
    private val syncOrchestrator: SyncOrchestrator
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val issueKeyRegex = Regex("^[A-Z]{1,10}-\\d+$")

    override val toolName = "jira_ticket_sync"

    override val description =
        "Sync a single Jira ticket on-demand. " +
            "Executes synchronously and returns result immediately."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("issueKey") {
                put("type", "string")
                put("description", "Jira issue key to sync (e.g., MTO-42)")
            }
            putJsonObject("includeLinked") {
                put("type", "boolean")
                put("default", true)
                put("description", "Also sync linked tickets")
            }
        },
        required = listOf("issueKey")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val issueKey = extractAndValidateIssueKey(arguments)
            val includeLinked = HandlerUtils.optionalBoolean(arguments, "includeLinked", true)
            val result = executeSingleSync(issueKey)
            HandlerUtils.successResult(buildResponse(issueKey, includeLinked, result))
        } catch (e: IllegalArgumentException) {
            HandlerUtils.errorResult("KB_VALIDATION", e.message ?: "Validation failed")
        } catch (e: Exception) {
            logger.error("jira_ticket_sync failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Ticket sync failed: ${e.message}")
        }
    }

    private fun extractAndValidateIssueKey(arguments: JsonObject?): String {
        val issueKey = HandlerUtils.requireString(arguments, "issueKey")
            ?: throw IllegalArgumentException("issueKey is required")
        if (!issueKeyRegex.matches(issueKey)) {
            throw IllegalArgumentException(
                "issueKey must match ^[A-Z]{1,10}-\\d+$ (got: $issueKey)"
            )
        }
        return issueKey
    }

    private suspend fun executeSingleSync(
        issueKey: String
    ): com.orchestrator.mcp.sync.pipeline.model.SyncResult {
        val projectKey = issueKey.substringBefore("-")
        return withContext(Dispatchers.IO) {
            syncOrchestrator.sync(projectKey, SyncOptions(fullSync = false))
        }
    }

    private fun buildResponse(
        issueKey: String,
        includeLinked: Boolean,
        result: com.orchestrator.mcp.sync.pipeline.model.SyncResult
    ): String {
        return buildJsonObject {
            put("status", "completed")
            put("issueKey", issueKey)
            put("includeLinked", includeLinked)
            put("processedTickets", result.processedTickets)
            put("entriesCreated", result.entriesCreated.values.sum())
        }.toString()
    }
}
