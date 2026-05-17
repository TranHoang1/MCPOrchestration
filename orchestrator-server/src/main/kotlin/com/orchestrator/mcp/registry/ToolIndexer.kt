package com.orchestrator.mcp.registry

import com.orchestrator.mcp.client.embedding.EmbeddingService
import com.orchestrator.mcp.core.model.ToolDefinition
import com.orchestrator.mcp.core.model.ToolEntry
import com.orchestrator.mcp.client.upstream.UpstreamServerManager
import com.orchestrator.mcp.client.vectordb.VectorDbClient
import com.orchestrator.mcp.client.vectordb.model.VectorPoint
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Scans upstream servers, extracts tool metadata,
 * generates embeddings, and upserts to VectorDB + ToolRegistry.
 */
class ToolIndexer(
    private val serverManager: UpstreamServerManager,
    private val embeddingService: EmbeddingService,
    private val vectorDbClient: VectorDbClient,
    private val toolRegistry: ToolRegistry,
    private val collectionName: String = "mcp_tools",
    private val batchSize: Int = 100
) {
    private val logger = LoggerFactory.getLogger(ToolIndexer::class.java)

    /**
     * Full scan: fetch tools from all connected servers,
     * generate embeddings, upsert to VectorDB, update registry.
     * Also indexes hidden/builtin tools so they are discoverable via find_tools.
     */
    suspend fun indexAll(): IndexResult {
        var totalIndexed = 0
        var totalFailed = 0
        val serverResults = mutableMapOf<String, Int>()

        val states = serverManager.getAllServerStates()
        for ((serverName, _) in states) {
            try {
                val count = indexServer(serverName)
                if (count > 0) {
                    serverResults[serverName] = count
                    totalIndexed += count
                }
            } catch (e: Exception) {
                logger.error("Failed to index $serverName: ${e.message}")
                totalFailed++
            }
        }

        // Index hidden/builtin tools so find_tools can discover them
        val hiddenCount = indexHiddenTools()
        if (hiddenCount > 0) {
            serverResults["__builtin__"] = hiddenCount
            totalIndexed += hiddenCount
        }

        logger.info("Indexed $totalIndexed tools from ${serverResults.size} servers")
        return IndexResult(totalIndexed, totalFailed, serverResults)
    }

    /**
     * Index hidden/builtin tools (e.g. export_drawio) into VectorDB
     * so they are discoverable via find_tools semantic search.
     */
    private suspend fun indexHiddenTools(): Int {
        val hiddenEntries = toolRegistry.getToolsByServer("__builtin__")
        if (hiddenEntries.isEmpty()) return 0

        val tools = hiddenEntries.map { entry ->
            ToolDefinition(
                name = entry.name,
                description = entry.description,
                inputSchema = entry.inputSchema
            )
        }
        val texts = tools.map { buildEmbeddingText(it) }
        val embeddings = embeddingService.generateEmbeddings(texts)
        val points = tools.zip(embeddings).map { (tool, embedding) ->
            buildVectorPoint("__builtin__", tool, embedding)
        }

        if (points.isNotEmpty()) {
            vectorDbClient.upsert(collectionName, points)
        }

        logger.info("Indexed ${tools.size} hidden/builtin tools")
        return tools.size
    }

    /**
     * Index tools from a single upstream server.
     * Supports incremental: adds new, removes stale.
     */
    suspend fun indexServer(serverName: String): Int {
        val connection = serverManager.getConnection(serverName)
            ?: run {
                logger.warn("No connection for $serverName, skipping")
                return 0
            }

        val toolsResponse = connection.sendRequest("tools/list", null)
        val tools = parseToolsList(toolsResponse)

        // MTO-113: Warn when connected server returns 0 tools
        if (tools.isEmpty()) {
            logger.warn(
                "Server '$serverName' returned 0 tools after " +
                    "successful connection. This likely indicates " +
                    "a malformed config (e.g. missing comma in " +
                    "args array) or server-side issue."
            )
            return 0
        }

        return indexTools(serverName, tools)
    }

    /**
     * Index a list of tools for a given server.
     * Handles batching for embeddings (max batchSize per call).
     */
    suspend fun indexTools(
        serverName: String,
        tools: List<ToolDefinition>
    ): Int {
        val existingNames = toolRegistry.getToolsByServer(serverName)
            .map { it.name }.toSet()
        val newNames = tools.map { it.name }.toSet()

        // Remove stale tools
        removeStaleTools(serverName, existingNames, newNames)

        // Batch embed and upsert
        val points = mutableListOf<VectorPoint>()
        for (batch in tools.chunked(batchSize)) {
            val texts = batch.map { buildEmbeddingText(it) }
            val embeddings = embeddingService.generateEmbeddings(texts)
            batch.zip(embeddings).forEach { (tool, embedding) ->
                points.add(buildVectorPoint(serverName, tool, embedding))
                registerInMemory(serverName, tool)
            }
        }

        if (points.isNotEmpty()) {
            vectorDbClient.upsert(collectionName, points)
        }

        logger.info("Indexed ${tools.size} tools from $serverName")
        return tools.size
    }

    private fun removeStaleTools(
        serverName: String,
        existing: Set<String>,
        current: Set<String>
    ) {
        val stale = existing - current
        stale.forEach { toolName ->
            toolRegistry.removeTool(toolName)
            logger.debug("Removed stale tool: $toolName from $serverName")
        }
    }

    private fun buildEmbeddingText(tool: ToolDefinition): String {
        return "${tool.name}: ${tool.description}"
    }

    private fun buildVectorPoint(
        serverName: String,
        tool: ToolDefinition,
        embedding: FloatArray
    ): VectorPoint {
        return VectorPoint(
            id = "$serverName::${tool.name}",
            vector = embedding,
            payload = mapOf(
                "name" to tool.name,
                "tool_name" to tool.name,
                "description" to tool.description,
                "server_name" to serverName
            ),
            schemaPayload = tool.inputSchema
        )
    }

    private fun registerInMemory(serverName: String, tool: ToolDefinition) {
        toolRegistry.registerTool(
            ToolEntry(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.inputSchema,
                serverName = serverName
            )
        )
    }

    companion object {
        fun parseToolsList(response: JsonObject): List<ToolDefinition> {
            return try {
                val toolsArray = response["tools"]
                    ?: return emptyList()
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }
                json.decodeFromJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(
                        ToolDefinition.serializer()
                    ),
                    toolsArray
                )
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

/**
 * Result of an indexing operation.
 */
data class IndexResult(
    val totalIndexed: Int,
    val totalFailed: Int,
    val serverResults: Map<String, Int>
)
