package com.orchestrator.mcp.kb.protocol.handlers.feature

import com.orchestrator.mcp.kb.KbException
import com.orchestrator.mcp.kb.KbNotFoundException
import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import com.orchestrator.mcp.kb.feature.FeatureConstants
import com.orchestrator.mcp.kb.feature.FeatureRepository
import com.orchestrator.mcp.kb.feature.FeatureValidation
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.protocol.handlers.HandlerUtils
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handler for kb_feature_delete tool.
 * Deletes a feature. Warns if AI-detected feature may be re-created on next sync.
 */
class KbFeatureDeleteHandler(
    private val featureRepository: FeatureRepository,
    private val auditService: AuditService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(KbFeatureDeleteHandler::class.java)

    override val toolName = "kb_feature_delete"

    override val description = "Delete a feature. AI-detected features may be re-created " +
        "on next sync cycle. Manual features are permanently removed."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("feature_id") {
                put("type", "string")
                put("description", "Feature ID to delete")
            }
        },
        required = listOf("feature_id")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val featureId = FeatureValidation.validateFeatureId(
                HandlerUtils.requireString(arguments, "feature_id")
            )
            val entryKey = "feature:$featureId"
            val existing = featureRepository.findById(entryKey)
                ?: throw KbNotFoundException(entryKey)
            featureRepository.delete(entryKey)
            val source = existing.data["source"] ?: FeatureConstants.SOURCE_AI_DETECTED
            val featureName = existing.data["feature_name"] ?: ""
            val warning = buildWarning(source)
            logAudit(featureId, source)
            HandlerUtils.successResult(buildResponse(featureId, featureName, source, warning))
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_feature_delete failed: {}", e.message, e)
            HandlerUtils.errorResult("FEATURE_INTERNAL_ERROR", "Failed to delete feature: ${e.message}")
        }
    }

    private fun buildWarning(source: String): String? {
        return when (source) {
            FeatureConstants.SOURCE_AI_DETECTED,
            FeatureConstants.SOURCE_EPIC_HIERARCHY ->
                "This feature may be re-created by AI on the next sync cycle."
            else -> null
        }
    }

    private fun logAudit(featureId: String, source: String) {
        auditService.log(AuditEvent(
            eventType = AuditEventType.FEATURE_CRUD,
            action = "kb_feature_delete",
            success = true,
            metadata = mapOf("feature_id" to featureId, "source" to source)
        ))
    }

    private fun buildResponse(
        featureId: String,
        featureName: String,
        source: String,
        warning: String?
    ): String {
        return buildJsonObject {
            put("status", "deleted")
            put("feature_id", featureId)
            put("feature_name", featureName)
            put("source", source)
            warning?.let { put("warning", it) }
        }.toString()
    }
}
