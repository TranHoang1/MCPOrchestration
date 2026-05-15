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
import com.orchestrator.mcp.sync.pipeline.model.IndexEntry
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handler for kb_feature_assign tool.
 * Moves a ticket from one feature to another (or assigns to new feature).
 */
class KbFeatureAssignHandler(
    private val featureRepository: FeatureRepository,
    private val auditService: AuditService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(KbFeatureAssignHandler::class.java)

    override val toolName = "kb_feature_assign"

    override val description = "Assign a ticket to a feature. " +
        "If the ticket belongs to another feature, it is moved automatically."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("feature_id") {
                put("type", "string")
                put("description", "Target feature ID")
            }
            putJsonObject("ticket_key") {
                put("type", "string")
                put("description", "Jira ticket key to assign (e.g., 'MTO-116')")
            }
        },
        required = listOf("feature_id", "ticket_key")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val featureId = FeatureValidation.validateFeatureId(
                HandlerUtils.requireString(arguments, "feature_id")
            )
            val ticketKey = FeatureValidation.validateTicketKey(
                HandlerUtils.requireString(arguments, "ticket_key")
            )
            val entryKey = "feature:$featureId"
            val target = featureRepository.findById(entryKey)
                ?: throw KbNotFoundException(entryKey)
            val result = assignTicket(target, ticketKey, entryKey)
            logAudit(featureId, ticketKey, result)
            HandlerUtils.successResult(buildResponse(featureId, ticketKey, result))
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_feature_assign failed: {}", e.message, e)
            HandlerUtils.errorResult("FEATURE_INTERNAL_ERROR", "Failed to assign ticket: ${e.message}")
        }
    }

    private suspend fun assignTicket(
        target: IndexEntry,
        ticketKey: String,
        entryKey: String
    ): AssignResult {
        val currentTickets = parseTicketKeys(target)
        if (ticketKey in currentTickets) {
            return AssignResult(currentTickets, null, isNoOp = true)
        }
        val removedFrom = removeFromOldFeature(target.projectKey, ticketKey, entryKey)
        val newTickets = currentTickets + ticketKey
        val updatedData = buildUpdatedData(target.data, newTickets)
        val vectorText = buildVectorText(updatedData)
        featureRepository.update(entryKey, updatedData, vectorText)
        return AssignResult(newTickets, removedFrom, isNoOp = false)
    }

    private suspend fun removeFromOldFeature(
        projectKey: String,
        ticketKey: String,
        targetEntryKey: String
    ): String? {
        val oldFeature = featureRepository.findByTicketKey(projectKey, ticketKey) ?: return null
        if (oldFeature.entryKey == targetEntryKey) return null
        val oldTickets = parseTicketKeys(oldFeature).filter { it != ticketKey }
        val oldData = oldFeature.data.toMutableMap()
        oldData["ticket_keys"] = oldTickets.joinToString(",")
        val oldVectorText = "Feature: ${oldData["feature_name"]}. Tickets: ${oldTickets.joinToString(", ")}"
        featureRepository.update(oldFeature.entryKey, oldData, oldVectorText)
        return oldFeature.data["feature_id"]
    }

    private fun buildUpdatedData(
        existingData: Map<String, String?>,
        newTickets: List<String>
    ): Map<String, String?> {
        val data = existingData.toMutableMap()
        data["ticket_keys"] = newTickets.joinToString(",")
        if (data["source"] != FeatureConstants.SOURCE_MANUAL) {
            data["source"] = FeatureConstants.SOURCE_MANUAL
            data["locked"] = FeatureConstants.LOCKED_TRUE
            data["created_by"] = FeatureConstants.CREATED_BY_BA
        }
        return data
    }

    private fun buildVectorText(data: Map<String, String?>): String {
        return "Feature: ${data["feature_name"]}. Tickets: ${data["ticket_keys"]}"
    }

    private fun parseTicketKeys(entry: IndexEntry): List<String> {
        return entry.data["ticket_keys"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    private fun logAudit(featureId: String, ticketKey: String, result: AssignResult) {
        auditService.log(AuditEvent(
            eventType = AuditEventType.FEATURE_CRUD,
            action = "kb_feature_assign",
            success = true,
            metadata = mapOf("feature_id" to featureId, "ticket_key" to ticketKey)
        ))
    }

    private fun buildResponse(featureId: String, ticketKey: String, result: AssignResult): String {
        return buildJsonObject {
            put("status", if (result.isNoOp) "no_change" else "assigned")
            put("feature_id", featureId)
            put("ticket_key", ticketKey)
            put("ticket_keys", result.ticketKeys.joinToString(","))
            result.removedFrom?.let { put("removed_from", it) }
        }.toString()
    }

    private data class AssignResult(
        val ticketKeys: List<String>,
        val removedFrom: String?,
        val isNoOp: Boolean
    )
}
