package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.logging.AgentLogEntry
import com.orchestrator.mcp.logging.AgentLogService
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Registers agent_log tool for real-time agent execution logging.
 */
object AgentLogToolRegistrar {

    private val logger = LoggerFactory.getLogger(AgentLogToolRegistrar::class.java)

    fun register(server: Server, agentLogService: AgentLogService?) {
        server.addTool(
            name = "agent_log",
            description = agentLogDescription(),
            inputSchema = agentLogSchema()
        ) { request ->
            handleAgentLog(request.arguments, agentLogService)
        }
    }

    private fun agentLogDescription(): String =
        "Write an execution log entry for agent activity tracking. " +
        "Use this to log each step of your work in real-time."

    private fun agentLogSchema(): ToolSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("ticket_key") {
                put("type", "string")
                put("description", "Jira ticket key (e.g. MTO-12)")
            }
            putJsonObject("agent_name") {
                put("type", "string")
                put("description", "Agent: SM, BA, TA, SA, QA, DEV, DEVOPS")
            }
            putJsonObject("step") {
                put("type", "string")
                put("description", "Step ID (e.g. Step-1, Self-Check)")
            }
            putJsonObject("status") {
                put("type", "string")
                put("description", "START|DONE|ARTIFACT|SKIP|ERROR|WARN|VERIFY")
            }
            putJsonObject("message") {
                put("type", "string")
                put("description", "What happened")
            }
            putJsonObject("artifacts") {
                put("type", "string")
                put("description", "Optional JSON of artifact paths")
            }
        },
        required = listOf("ticket_key", "agent_name", "step", "status", "message")
    )

    private suspend fun handleAgentLog(
        arguments: JsonObject?,
        agentLogService: AgentLogService?
    ): CallToolResult {
        if (agentLogService == null) {
            return CallToolResult(
                content = listOf(TextContent(text = """{"error":"agent_log service not configured"}""")),
                isError = true
            )
        }

        val ticketKey = arguments?.getString("ticket_key")
            ?: return errorResult("ticket_key is required")
        val agentName = arguments.getString("agent_name")
            ?: return errorResult("agent_name is required")
        val step = arguments.getString("step")
            ?: return errorResult("step is required")
        val status = arguments.getString("status")
            ?: return errorResult("status is required")
        val message = arguments.getString("message")
            ?: return errorResult("message is required")
        val artifacts = arguments.getString("artifacts")

        return try {
            val id = agentLogService.writeLog(AgentLogEntry(
                ticketKey = ticketKey,
                agentName = agentName,
                step = step,
                status = status,
                message = message,
                artifacts = artifacts
            ))
            logger.debug("[Agent-Log] {}|{}|{}|{} — {}", ticketKey, agentName, step, status, message)
            CallToolResult(content = listOf(TextContent(
                text = """{"success":true,"id":$id}"""
            )))
        } catch (e: Exception) {
            logger.error("Failed to write agent log: ${e.message}", e)
            CallToolResult(
                content = listOf(TextContent(text = """{"error":"${e.message}"}""")),
                isError = true
            )
        }
    }

    private fun errorResult(msg: String) = CallToolResult(
        content = listOf(TextContent(text = """{"error":"$msg"}""")),
        isError = true
    )

    private fun JsonObject.getString(key: String): String? =
        this[key]?.let { it as? JsonPrimitive }?.content
}
