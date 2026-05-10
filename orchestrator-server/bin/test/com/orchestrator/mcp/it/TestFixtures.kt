package com.orchestrator.mcp.it

import com.orchestrator.mcp.core.config.*
import com.orchestrator.mcp.core.model.ToolDefinition
import com.orchestrator.mcp.core.model.ToolEntry
import com.orchestrator.mcp.client.vectordb.model.SearchResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Shared test fixtures for IT and E2E tests.
 */
object TestFixtures {

    fun mockEmbedding(dims: Int = 768): FloatArray = FloatArray(dims) { 0.01f }

    fun searchResults(count: Int, baseScore: Float = 0.95f): List<SearchResult> {
        return (0 until count).map { i ->
            SearchResult(
                id = "test-server::tool_$i",
                score = baseScore - (i * 0.05f),
                payload = mapOf(
                    "name" to "tool_$i",
                    "description" to "Test tool $i description",
                    "server_name" to "test-server"
                )
            )
        }
    }

    fun sampleTools(count: Int = 10): List<ToolEntry> {
        val names = listOf(
            "read_logs", "tail_logs", "search_logs",
            "create_issue", "search_issues", "get_issue",
            "query_db", "list_tables", "describe_table", "backup_db"
        )
        return names.take(count).mapIndexed { i, name ->
            val server = when {
                i < 3 -> "log-server"
                i < 6 -> "jira-server"
                else -> "db-server"
            }
            ToolEntry(
                name = name,
                description = "Description for $name",
                inputSchema = null,
                serverName = server
            )
        }
    }

    fun sampleToolDefinitions(count: Int = 5): List<ToolDefinition> {
        return (0 until count).map { i ->
            ToolDefinition(
                name = "tool_$i",
                description = "Tool $i does something useful",
                inputSchema = buildJsonObject {
                    put("type", "object")
                }
            )
        }
    }

    fun testConfig(
        timeoutSeconds: Int = 5,
        maxReconnectAttempts: Int = 3,
        healthCheckIntervalSeconds: Int = 2
    ): OrchestratorConfig {
        return OrchestratorConfig(
            orchestrator = OrchestratorSettings(
                execution = ExecutionConfig(timeoutSeconds = timeoutSeconds),
                health = HealthConfig(
                    checkIntervalSeconds = healthCheckIntervalSeconds,
                    autoReconnect = true,
                    maxReconnectAttempts = maxReconnectAttempts
                ),
                discovery = DiscoveryConfig(
                    topK = 5,
                    similarityThreshold = 0.7f,
                    fallbackToKeyword = true
                )
            )
        )
    }

    fun mockUpstreamToolsResponse(tools: List<ToolDefinition>): JsonObject {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true; encodeDefaults = true
        }
        return buildJsonObject {
            put("tools", json.encodeToJsonElement(
                kotlinx.serialization.builtins.ListSerializer(
                    ToolDefinition.serializer()
                ), tools
            ))
        }
    }

    fun mockToolCallResponse(text: String = "result data"): JsonObject {
        return buildJsonObject {
            put("content", kotlinx.serialization.json.buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
            })
        }
    }

    fun mockErrorResponse(message: String = "Internal error"): JsonObject {
        return buildJsonObject {
            put("error", buildJsonObject {
                put("code", -32000)
                put("message", message)
            })
        }
    }
}
