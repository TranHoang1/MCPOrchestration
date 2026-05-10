package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.KbException
import com.orchestrator.mcp.kb.KbValidationException
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.store.repository.KbEntryRepository
import com.orchestrator.mcp.kb.store.vector.KbVectorClient
import com.orchestrator.mcp.client.embedding.EmbeddingService
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handler for kb_link tool.
 * Finds semantically similar KB entries using vector similarity.
 */
class KbLinkHandler(
    private val embeddingService: EmbeddingService,
    private val vectorClient: KbVectorClient,
    private val entryRepository: KbEntryRepository
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(KbLinkHandler::class.java)

    override val toolName = "kb_link"

    override val description = "Find semantically similar KB entries to a given entry or query. " +
        "Uses vector similarity for entity linking."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("issue_key") {
                put("type", "string")
                put("description", "Find entries similar to this issue")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Or find entries similar to this text query")
            }
            putJsonObject("top_k") {
                put("type", "integer")
                put("default", 5)
                put("description", "Maximum number of similar entries")
            }
            putJsonObject("min_score") {
                put("type", "number")
                put("default", 0.7)
                put("description", "Minimum similarity score threshold")
            }
        }
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val issueKey = HandlerUtils.optionalString(arguments, "issue_key")
            val query = HandlerUtils.optionalString(arguments, "query")
            val topK = HandlerUtils.optionalInt(arguments, "top_k", 5).coerceIn(1, 20)

            val searchText = resolveSearchText(issueKey, query)
                ?: throw KbValidationException("Either issue_key or query is required")

            val embedding = embeddingService.generateEmbedding(searchText)
            val results = vectorClient.search(embedding, topK, 0.7f)

            val responseJson = buildJsonObject {
                putJsonArray("links") {
                    results.filter { it.issueKey != issueKey }.forEach { r ->
                        add(buildJsonObject {
                            put("issue_key", r.issueKey)
                            put("score", r.score)
                        })
                    }
                }
            }
            HandlerUtils.successResult(responseJson.toString())
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_link failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Link failed: ${e.message}")
        }
    }

    private suspend fun resolveSearchText(issueKey: String?, query: String?): String? {
        if (query != null) return query
        if (issueKey != null) {
            val entry = entryRepository.findByIssueKey(issueKey)
            return entry?.publicContent ?: entry?.maskedFull
        }
        return null
    }
}
