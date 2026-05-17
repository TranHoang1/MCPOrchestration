package com.orchestrator.mcp.kb.protocol.handlers.feature

import com.orchestrator.mcp.kb.KbException
import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import com.orchestrator.mcp.kb.feature.FeatureRepository
import com.orchestrator.mcp.kb.feature.FeatureValidation
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.protocol.handlers.HandlerUtils
import com.orchestrator.mcp.sync.pipeline.model.IndexEntry
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handler for kb_feature_list tool.
 * Lists all features for a given project.
 */
class KbFeatureListHandler(
    private val featureRepository: FeatureRepository,
    private val auditService: AuditService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(KbFeatureListHandler::class.java)

    override val toolName = "kb_feature_list"

    override val description = "List all features for a project. " +
        "Returns manual and AI-detected features with their ticket assignments."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("project_key") {
                put("type", "string")
                put("description", "Project key (e.g., 'MTO')")
            }
        },
        required = listOf("project_key")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val projectKey = FeatureValidation.validateProjectKey(
                HandlerUtils.requireString(arguments, "project_key")
            )
            val entries = featureRepository.listByProject(projectKey)
            val responseJson = buildResponse(entries, projectKey)
            auditService.log(AuditEvent(
                eventType = AuditEventType.FEATURE_CRUD,
                action = "kb_feature_list",
                success = true,
                metadata = mapOf("project_key" to projectKey, "count" to entries.size.toString())
            ))
            HandlerUtils.successResult(responseJson)
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_feature_list failed: {}", e.message, e)
            HandlerUtils.errorResult("FEATURE_INTERNAL_ERROR", "Failed to list features: ${e.message}")
        }
    }

    private fun buildResponse(entries: List<IndexEntry>, projectKey: String): String {
        return buildJsonObject {
            putJsonArray("features") {
                entries.forEach { entry -> add(mapFeatureJson(entry)) }
            }
            put("total_count", entries.size)
            put("project_key", projectKey)
        }.toString()
    }

    private fun mapFeatureJson(entry: IndexEntry): JsonObject {
        return buildJsonObject {
            put("feature_id", entry.data["feature_id"] ?: "")
            put("name", entry.data["feature_name"] ?: "")
            put("source", entry.data["source"] ?: "ai_detected")
            put("locked", entry.data["locked"] ?: "false")
            put("created_by", entry.data["created_by"] ?: "ai-sync")
            put("ticket_keys", entry.data["ticket_keys"] ?: "")
            entry.data["description"]?.let { put("description", it) }
            entry.data["confidence"]?.let { put("confidence", it) }
            entry.data["epic_key"]?.let { put("epic_key", it) }
        }
    }
}
