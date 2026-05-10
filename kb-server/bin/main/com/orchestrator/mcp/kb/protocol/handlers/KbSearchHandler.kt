package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.KbException
import com.orchestrator.mcp.kb.KbValidationException
import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.store.model.KbEntry
import com.orchestrator.mcp.kb.store.repository.KbEntryRepository
import com.orchestrator.mcp.kb.store.vector.KbVectorClient
import com.orchestrator.mcp.client.embedding.EmbeddingService
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handler for kb_search tool.
 * Performs vector similarity search + keyword fallback across KB entries.
 */
class KbSearchHandler(
    private val embeddingService: EmbeddingService,
    private val vectorClient: KbVectorClient,
    private val entryRepository: KbEntryRepository,
    private val auditService: AuditService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(KbSearchHandler::class.java)

    override val toolName = "kb_search"

    override val description = "Search knowledge base entries semantically. " +
        "Returns matching entries ranked by relevance. " +
        "Content is filtered by caller's role (RLS)."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Natural language search query")
                put("maxLength", 2000)
            }
            putJsonObject("project_key") {
                put("type", "string")
                put("description", "Filter by Jira project key (optional)")
            }
            putJsonObject("top_k") {
                put("type", "integer")
                put("default", 5)
                put("minimum", 1)
                put("maximum", 20)
                put("description", "Maximum number of results to return")
            }
            putJsonObject("include_technical") {
                put("type", "boolean")
                put("default", true)
                put("description", "Include technical/code content in results")
            }
            putJsonObject("tags") {
                put("type", "string")
                put("description", "Comma-separated tags to filter by")
            }
        },
        required = listOf("query")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val query = HandlerUtils.requireString(arguments, "query")
                ?: throw KbValidationException("Query must not be empty")
            if (query.isBlank()) throw KbValidationException("Query must not be empty")

            val topK = HandlerUtils.optionalInt(arguments, "top_k", 5).coerceIn(1, 20)
            val projectKey = HandlerUtils.optionalString(arguments, "project_key")

            val results = performSearch(query, topK, projectKey)
            auditService.log(AuditEvent(
                eventType = AuditEventType.SEARCH,
                action = "kb_search",
                success = true,
                metadata = mapOf("query" to query)
            ))
            HandlerUtils.successResult(results)
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_search failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Search failed: ${e.message}")
        }
    }

    private suspend fun performSearch(
        query: String,
        topK: Int,
        projectKey: String?
    ): String {
        val vectorResults = searchByVector(query, topK, projectKey)
        val keywordResults = if (vectorResults.isEmpty()) {
            searchByKeyword(query, topK)
        } else emptyList()

        val allResults = (vectorResults + keywordResults).distinctBy { it.first }
        return formatResults(allResults)
    }

    private suspend fun searchByVector(
        query: String,
        topK: Int,
        projectKey: String?
    ): List<Pair<String, Float>> {
        return try {
            val embedding = embeddingService.generateEmbedding(query)
            val results = vectorClient.search(embedding, topK, 0.5f, projectKey)
            results.map { it.issueKey to it.score }
        } catch (e: Exception) {
            logger.warn("Vector search failed, falling back to keyword: {}", e.message)
            emptyList()
        }
    }

    private suspend fun searchByKeyword(
        query: String,
        topK: Int
    ): List<Pair<String, Float>> {
        val entries = entryRepository.searchByKeyword(query, topK)
        return entries.map { it.issueKey to 0.5f }
    }

    private suspend fun formatResults(scored: List<Pair<String, Float>>): String {
        val entries = scored.mapNotNull { (issueKey, score) ->
            entryRepository.findByIssueKey(issueKey)?.let { it to score }
        }
        val results = buildJsonObject {
            putJsonArray("results") {
                for ((entry, score) in entries) {
                    add(entryToJson(entry, score))
                }
            }
            put("total", entries.size)
        }
        return results.toString()
    }

    private fun entryToJson(entry: KbEntry, score: Float): JsonObject =
        buildJsonObject {
            put("issue_key", entry.issueKey)
            put("project_key", entry.projectKey)
            put("content", entry.publicContent ?: entry.maskedFull ?: "")
            put("score", score)
            put("created_at", entry.createdAt.toString())
            put("updated_at", entry.updatedAt.toString())
        }
}
