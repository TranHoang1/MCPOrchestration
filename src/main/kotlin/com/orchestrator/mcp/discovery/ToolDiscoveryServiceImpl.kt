package com.orchestrator.mcp.discovery

import com.orchestrator.mcp.discovery.model.FindToolsResponse
import com.orchestrator.mcp.discovery.model.ToolResult
import com.orchestrator.mcp.embedding.EmbeddingService
import com.orchestrator.mcp.model.EmbeddingServiceException
import com.orchestrator.mcp.model.InvalidParamsException
import com.orchestrator.mcp.model.VectorDbUnavailableException
import com.orchestrator.mcp.registry.ToolRegistry
import com.orchestrator.mcp.vectordb.VectorDbClient
import org.slf4j.LoggerFactory

/**
 * Semantic search implementation with keyword fallback.
 * Pipeline: query → validate → embed → vector search → enrich with server status → return
 * Fallback: if embedding or vector DB fails → keyword search over ToolRegistry
 */
class ToolDiscoveryServiceImpl(
    private val embeddingService: EmbeddingService,
    private val vectorDbClient: VectorDbClient,
    private val toolRegistry: ToolRegistry,
    private val keywordEngine: KeywordSearchEngine,
    private val collectionName: String = "mcp_tools",
    private val maxQueryLength: Int = 2000
) : ToolDiscoveryService {

    private val logger = LoggerFactory.getLogger(ToolDiscoveryServiceImpl::class.java)

    override suspend fun findTools(query: String, topK: Int, threshold: Float): FindToolsResponse {
        // Validate and sanitize input
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            throw InvalidParamsException("Query parameter is required and must be non-empty")
        }
        if (trimmedQuery.length > maxQueryLength) {
            throw InvalidParamsException("Query exceeds maximum length of $maxQueryLength characters")
        }

        // Clamp topK and threshold to valid ranges
        val effectiveTopK = topK.coerceIn(1, 20)
        val effectiveThreshold = threshold.coerceIn(0.0f, 1.0f)

        val truncatedQuery = if (trimmedQuery.length > 100) trimmedQuery.take(100) + "..." else trimmedQuery
        logger.info("find_tools query=\"$truncatedQuery\", topK=$effectiveTopK, threshold=$effectiveThreshold")

        return try {
            // Semantic search path
            val embedding = embeddingService.generateEmbedding(trimmedQuery)
            val results = vectorDbClient.search(collectionName, embedding, effectiveTopK, effectiveThreshold)

            val tools = results.map { result ->
                val serverName = result.payload["server_name"] ?: "unknown"
                val serverStatus = toolRegistry.lookupTool(result.payload["name"] ?: "")?.serverStatus ?: "UNKNOWN"

                ToolResult(
                    name = result.payload["name"] ?: "",
                    description = result.payload["description"] ?: "",
                    inputSchema = result.schemaPayload,
                    serverName = serverName,
                    serverStatus = serverStatus,
                    similarityScore = result.score
                )
            }

            logger.info("find_tools results=${tools.size}, mode=semantic, duration=N/A")

            FindToolsResponse(
                tools = tools,
                searchMode = "semantic",
                totalIndexed = toolRegistry.getToolCount()
            )
        } catch (e: VectorDbUnavailableException) {
            logger.warn("Vector DB unavailable, falling back to keyword search: ${e.message}")
            keywordFallback(trimmedQuery, effectiveTopK, effectiveThreshold)
        } catch (e: EmbeddingServiceException) {
            logger.warn("Embedding service unavailable, falling back to keyword search: ${e.message}")
            keywordFallback(trimmedQuery, effectiveTopK, effectiveThreshold)
        }
    }

    private fun keywordFallback(query: String, topK: Int, threshold: Float): FindToolsResponse {
        val results = keywordEngine.search(query, topK, threshold)
        logger.info("find_tools results=${results.size}, mode=keyword")
        return FindToolsResponse(
            tools = results,
            searchMode = "keyword",
            totalIndexed = toolRegistry.getToolCount()
        )
    }
}
