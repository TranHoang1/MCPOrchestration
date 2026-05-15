package com.orchestrator.mcp.kb.protocol.handlers.feature

import com.orchestrator.mcp.kb.KbDuplicateException
import com.orchestrator.mcp.kb.KbException
import com.orchestrator.mcp.kb.KbNotFoundException
import com.orchestrator.mcp.kb.KbValidationException
import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import com.orchestrator.mcp.kb.feature.FeatureConstants
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
 * Handler for kb_feature_update tool.
 * Partially updates a feature. Adopts AI features when updated by BA.
 */
class KbFeatureUpdateHandler(
    private val featureRepository: FeatureRepository,
    private val auditService: AuditService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(KbFeatureUpdateHandler::class.java)

    override val toolName = "kb_feature_update"

    override val description = "Update an existing feature (name, tickets, description). " +
        "Updating an AI-detected feature adopts it as manual/locked."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("feature_id") {
                put("type", "string")
                put("description", "Feature ID to update")
            }
            putJsonObject("name") {
                put("type", "string")
                put("description", "New feature name (optional)")
            }
            putJsonObject("ticket_keys") {
                put("type", "array")
                put("description", "New ticket keys list (optional, replaces existing)")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "New description (optional)")
            }
        },
        required = listOf("feature_id")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val featureId = FeatureValidation.validateFeatureId(
                HandlerUtils.requireString(arguments, "feature_id")
            )
            val updates = extractUpdates(arguments)
            validateAtLeastOneField(updates)
            val entryKey = "feature:$featureId"
            val existing = featureRepository.findById(entryKey)
                ?: throw KbNotFoundException(entryKey)
            checkNameConflict(updates, existing)
            val result = applyUpdate(existing, updates, entryKey)
            logAudit(featureId, result)
            HandlerUtils.successResult(buildResponse(featureId, result))
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_feature_update failed: {}", e.message, e)
            HandlerUtils.errorResult("FEATURE_INTERNAL_ERROR", "Failed to update feature: ${e.message}")
        }
    }

    private fun extractUpdates(arguments: JsonObject?): UpdateFields {
        val name = HandlerUtils.optionalString(arguments, "name")?.let {
            FeatureValidation.validateFeatureName(it)
        }
        val ticketKeys = extractTicketKeys(arguments)?.let {
            FeatureValidation.validateTicketKeys(it)
        }
        val description = if (arguments?.containsKey("description") == true) {
            FeatureValidation.validateDescription(HandlerUtils.optionalString(arguments, "description"))
        } else null
        val hasDescription = arguments?.containsKey("description") == true
        return UpdateFields(name, ticketKeys, description, hasDescription)
    }

    private fun extractTicketKeys(arguments: JsonObject?): List<String>? {
        val arr = arguments?.get("ticket_keys") as? JsonArray ?: return null
        return arr.mapNotNull { (it as? JsonPrimitive)?.content }
    }

    private fun validateAtLeastOneField(updates: UpdateFields) {
        if (updates.name == null && updates.ticketKeys == null && !updates.hasDescription) {
            throw KbValidationException("At least one field (name, ticket_keys, description) is required")
        }
    }

    private suspend fun checkNameConflict(updates: UpdateFields, existing: IndexEntry) {
        val newName = updates.name ?: return
        val currentName = existing.data["feature_name"]
        if (newName != currentName && featureRepository.existsByName(existing.projectKey, newName)) {
            throw KbDuplicateException("feature", newName)
        }
    }

    private suspend fun applyUpdate(
        existing: IndexEntry,
        updates: UpdateFields,
        entryKey: String
    ): UpdateResult {
        val adopted = existing.data["source"] != FeatureConstants.SOURCE_MANUAL
        val updatedData = buildUpdatedData(existing.data, updates, adopted)
        val vectorText = buildVectorText(updatedData)
        featureRepository.update(entryKey, updatedData, vectorText)
        return UpdateResult(adopted, buildUpdatedFieldsList(updates), updatedData)
    }

    private fun buildUpdatedData(
        existingData: Map<String, String?>,
        updates: UpdateFields,
        adopted: Boolean
    ): Map<String, String?> {
        val data = existingData.toMutableMap()
        updates.name?.let { data["feature_name"] = it }
        updates.ticketKeys?.let { data["ticket_keys"] = it.joinToString(",") }
        if (updates.hasDescription) data["description"] = updates.description
        if (adopted) {
            data["source"] = FeatureConstants.SOURCE_MANUAL
            data["locked"] = FeatureConstants.LOCKED_TRUE
            data["created_by"] = FeatureConstants.CREATED_BY_BA
        }
        return data
    }

    private fun buildVectorText(data: Map<String, String?>): String {
        val name = data["feature_name"] ?: ""
        val tickets = data["ticket_keys"] ?: ""
        return "Feature: $name. Tickets: $tickets"
    }

    private fun buildUpdatedFieldsList(updates: UpdateFields): List<String> = buildList {
        if (updates.name != null) add("name")
        if (updates.ticketKeys != null) add("ticket_keys")
        if (updates.hasDescription) add("description")
    }

    private fun logAudit(featureId: String, result: UpdateResult) {
        auditService.log(AuditEvent(
            eventType = AuditEventType.FEATURE_CRUD,
            action = "kb_feature_update",
            success = true,
            metadata = mapOf("feature_id" to featureId, "adopted" to result.adopted.toString())
        ))
    }

    private fun buildResponse(featureId: String, result: UpdateResult): String {
        return buildJsonObject {
            put("status", "updated")
            put("feature_id", featureId)
            putJsonArray("updated_fields") { result.updatedFields.forEach { add(it) } }
            put("source", result.data["source"] ?: FeatureConstants.SOURCE_MANUAL)
            put("locked", result.data["locked"] ?: FeatureConstants.LOCKED_TRUE)
            put("adopted", result.adopted)
        }.toString()
    }

    private data class UpdateFields(
        val name: String?,
        val ticketKeys: List<String>?,
        val description: String?,
        val hasDescription: Boolean
    )

    private data class UpdateResult(
        val adopted: Boolean,
        val updatedFields: List<String>,
        val data: Map<String, String?>
    )
}
