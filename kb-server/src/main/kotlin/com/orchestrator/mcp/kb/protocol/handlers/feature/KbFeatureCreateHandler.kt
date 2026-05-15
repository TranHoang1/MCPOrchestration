package com.orchestrator.mcp.kb.protocol.handlers.feature

import com.orchestrator.mcp.kb.KbDuplicateException
import com.orchestrator.mcp.kb.KbException
import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import com.orchestrator.mcp.kb.feature.FeatureConstants
import com.orchestrator.mcp.kb.feature.FeatureRepository
import com.orchestrator.mcp.kb.feature.FeatureValidation
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.protocol.handlers.HandlerUtils
import com.orchestrator.mcp.sync.pipeline.model.IndexEntry
import com.orchestrator.mcp.sync.pipeline.model.SourceRef
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.UUID

/**
 * Handler for kb_feature_create tool.
 * Creates a new manual feature with ticket assignments.
 */
class KbFeatureCreateHandler(
    private val featureRepository: FeatureRepository,
    private val auditService: AuditService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(KbFeatureCreateHandler::class.java)

    override val toolName = "kb_feature_create"

    override val description = "Create a new feature grouping with ticket assignments. " +
        "Features are locked (protected from AI overwrite) by default."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("project_key") {
                put("type", "string")
                put("description", "Project key (e.g., 'MTO')")
            }
            putJsonObject("name") {
                put("type", "string")
                put("description", "Feature name (max 200 chars)")
            }
            putJsonObject("ticket_keys") {
                put("type", "array")
                put("description", "List of Jira ticket keys to assign")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "Optional feature description (max 2000 chars)")
            }
        },
        required = listOf("project_key", "name", "ticket_keys")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val params = validateParams(arguments)
            checkDuplicate(params.projectKey, params.name)
            val entry = buildEntry(params)
            featureRepository.create(entry)
            logAudit(params)
            HandlerUtils.successResult(buildResponse(params))
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_feature_create failed: {}", e.message, e)
            HandlerUtils.errorResult("FEATURE_INTERNAL_ERROR", "Failed to create feature: ${e.message}")
        }
    }

    private fun validateParams(arguments: JsonObject?): CreateParams {
        val projectKey = FeatureValidation.validateProjectKey(
            HandlerUtils.requireString(arguments, "project_key")
        )
        val name = FeatureValidation.validateFeatureName(
            HandlerUtils.requireString(arguments, "name")
        )
        val ticketKeys = extractTicketKeys(arguments)
        val description = FeatureValidation.validateDescription(
            HandlerUtils.optionalString(arguments, "description")
        )
        return CreateParams(projectKey, name, FeatureValidation.validateTicketKeys(ticketKeys), description)
    }

    private fun extractTicketKeys(arguments: JsonObject?): List<String> {
        val arr = arguments?.get("ticket_keys") as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.content }
    }

    private suspend fun checkDuplicate(projectKey: String, name: String) {
        if (featureRepository.existsByName(projectKey, name)) {
            throw KbDuplicateException("feature", name)
        }
    }

    private fun buildEntry(params: CreateParams): IndexEntry {
        val featureId = generateFeatureId(params.projectKey, params.name)
        val entryKey = "feature:$featureId"
        return IndexEntry(
            id = UUID.nameUUIDFromBytes(entryKey.toByteArray(Charsets.UTF_8)).toString(),
            dimensionId = FeatureConstants.DIMENSION_ID,
            projectKey = params.projectKey,
            ticketKey = null,
            entryKey = entryKey,
            sourceRef = SourceRef(
                type = "manual",
                path = "manual:feature/$featureId",
                syncedAt = Clock.System.now()
            ),
            data = buildDataMap(params, featureId),
            vectorText = "Feature: ${params.name}. Tickets: ${params.ticketKeys.joinToString(", ")}"
        )
    }

    private fun buildDataMap(params: CreateParams, featureId: String): Map<String, String?> = mapOf(
        "feature_id" to featureId,
        "feature_name" to params.name,
        "source" to FeatureConstants.SOURCE_MANUAL,
        "created_by" to FeatureConstants.CREATED_BY_BA,
        "locked" to FeatureConstants.LOCKED_TRUE,
        "ticket_keys" to params.ticketKeys.joinToString(","),
        "description" to params.description,
        "detection_method" to "manual",
        "confidence" to null,
        "epic_key" to null
    )

    private fun generateFeatureId(projectKey: String, name: String): String {
        val input = "$projectKey:$name"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
        return "manual-$hash"
    }

    private fun logAudit(params: CreateParams) {
        auditService.log(AuditEvent(
            eventType = AuditEventType.FEATURE_CRUD,
            action = "kb_feature_create",
            success = true,
            metadata = mapOf("project_key" to params.projectKey, "name" to params.name)
        ))
    }

    private fun buildResponse(params: CreateParams): String {
        val featureId = generateFeatureId(params.projectKey, params.name)
        return buildJsonObject {
            put("status", "created")
            put("feature_id", featureId)
            put("name", params.name)
            put("source", FeatureConstants.SOURCE_MANUAL)
            put("locked", true)
            put("ticket_keys", params.ticketKeys.joinToString(","))
        }.toString()
    }

    private data class CreateParams(
        val projectKey: String,
        val name: String,
        val ticketKeys: List<String>,
        val description: String?
    )
}
