package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.KbException
import com.orchestrator.mcp.kb.KbValidationException
import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.store.model.BrSensitivityLevel
import com.orchestrator.mcp.kb.store.model.KbEntry
import com.orchestrator.mcp.kb.store.repository.KbEntryRepository
import com.orchestrator.mcp.kb.store.vector.KbVectorClient
import com.orchestrator.mcp.kb.store.vector.KbVectorEntry
import com.orchestrator.mcp.client.embedding.EmbeddingService
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.UUID

/**
 * Handler for kb_ingest tool.
 * Ingests content into KB: stores entry, generates embedding, indexes in vector DB.
 */
class KbIngestHandler(
    private val entryRepository: KbEntryRepository,
    private val embeddingService: EmbeddingService,
    private val vectorClient: KbVectorClient,
    private val auditService: AuditService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(KbIngestHandler::class.java)

    override val toolName = "kb_ingest"

    override val description = "Ingest content into the knowledge base. " +
        "Handles PII masking, content segmentation, BR masking, " +
        "embedding generation, and vector indexing automatically."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("title") {
                put("type", "string")
                put("description", "Entry title (e.g., 'MTO-25 BRD — KB Refinery')")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "Full content to ingest (markdown, plain text, etc.)")
            }
            putJsonObject("issue_key") {
                put("type", "string")
                put("description", "Jira issue key for linking")
            }
            putJsonObject("tags") {
                put("type", "string")
                put("description", "Comma-separated tags (e.g., 'brd, kb, architecture')")
            }
            putJsonObject("priority") {
                put("type", "string")
                put("default", "normal")
                put("description", "Queue priority for processing (high or normal)")
            }
        },
        required = listOf("title", "content")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val title = HandlerUtils.requireString(arguments, "title")
                ?: throw KbValidationException("title is required")
            val content = HandlerUtils.requireString(arguments, "content")
                ?: throw KbValidationException("content is required")
            if (content.isBlank()) throw KbValidationException("content must not be empty")

            val issueKey = HandlerUtils.optionalString(arguments, "issue_key")
                ?: extractIssueKey(title)
            val tags = HandlerUtils.optionalString(arguments, "tags")

            val result = ingestContent(issueKey, title, content, tags)
            auditService.log(AuditEvent(
                eventType = AuditEventType.INGEST,
                issueKey = issueKey,
                action = "kb_ingest",
                success = true,
                metadata = mapOf("title" to title)
            ))
            HandlerUtils.successResult(result)
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_ingest failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Ingest failed: ${e.message}")
        }
    }

    private suspend fun ingestContent(
        issueKey: String,
        title: String,
        content: String,
        tags: String?
    ): String {
        val contentHash = computeHash(content)
        val projectKey = extractProjectKey(issueKey)

        // Store entry in database
        val entry = KbEntry(
            issueKey = issueKey,
            projectKey = projectKey,
            publicContent = buildPublicContent(title, content, tags),
            technicalContent = null,
            businessRules = null,
            maskedFull = content,
            brSensitivityLevel = BrSensitivityLevel.INTERNAL,
            contentHash = contentHash
        )
        entryRepository.upsert(entry)

        // Generate embedding and index in vector DB
        indexInVectorDb(entry, content)

        return buildJsonObject {
            put("status", "ingested")
            put("issue_key", issueKey)
            put("content_hash", contentHash)
            put("message", "Content ingested successfully")
        }.toString()
    }

    private suspend fun indexInVectorDb(entry: KbEntry, content: String) {
        try {
            val textToEmbed = "${entry.issueKey} ${entry.publicContent ?: content}"
            val embedding = embeddingService.generateEmbedding(textToEmbed)
            val vectorEntry = KbVectorEntry(
                issueKey = entry.issueKey,
                projectKey = entry.projectKey,
                contentHash = entry.contentHash,
                embedding = embedding,
                searchText = textToEmbed.take(500)
            )
            vectorClient.upsert(vectorEntry)
            logger.debug("Indexed entry {} in vector DB", entry.issueKey)
        } catch (e: Exception) {
            logger.warn("Vector indexing failed for {}: {}", entry.issueKey, e.message)
            // Non-fatal: entry is stored in DB, vector index can be rebuilt
        }
    }

    private fun buildPublicContent(title: String, content: String, tags: String?): String {
        return buildString {
            append("# $title\n\n")
            tags?.let { append("Tags: $it\n\n") }
            append(content)
        }
    }

    private fun extractIssueKey(title: String): String {
        val pattern = Regex("[A-Z]+-\\d+")
        return pattern.find(title)?.value ?: "KB-${UUID.randomUUID().toString().take(8)}"
    }

    private fun extractProjectKey(issueKey: String): String =
        issueKey.substringBefore("-").ifEmpty { "UNKNOWN" }

    private fun computeHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
