package com.orchestrator.mcp.bridge.routing

import com.orchestrator.mcp.bridge.HttpStreamableClient
import com.orchestrator.mcp.bridge.local.LocalServerManager
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Metrics for a single tool.
 */
data class ToolMetrics(
    var callCount: Long = 0,
    var errorCount: Long = 0,
    var totalLatencyMs: Long = 0,
    var lastCallAt: Long? = null,
)

/**
 * Result of a routed tool call.
 */
data class RouteResult(
    val content: List<Map<String, String>>,
    val isError: Boolean = false,
)

/**
 * Routes tool calls to local stdio or remote HTTP.
 * O(1) lookup by tool name with fallback support and metrics collection.
 */
class SmartRouter(
    private val routingTable: RoutingTable,
    private val localManager: LocalServerManager,
    private val httpClient: HttpStreamableClient,
) {
    private val logger = LoggerFactory.getLogger(SmartRouter::class.java)
    private val metrics = ConcurrentHashMap<String, ToolMetrics>()

    /** Route a tool call to the appropriate destination. */
    suspend fun route(toolName: String, args: JsonObject): RouteResult {
        val start = System.currentTimeMillis()
        return try {
            val result = doRoute(toolName, args)
            recordMetric(toolName, System.currentTimeMillis() - start, false)
            result
        } catch (e: Exception) {
            recordMetric(toolName, System.currentTimeMillis() - start, true)
            throw e
        }
    }

    /** Get metrics for all tools. */
    fun getMetrics(): Map<String, ToolMetrics> = metrics.toMap()

    private suspend fun doRoute(toolName: String, args: JsonObject): RouteResult {
        val route = routingTable.resolve(toolName)

        if (route != null) return routeByDefinition(toolName, args, route)

        // No explicit route — check local servers
        val localServer = localManager.findServerForTool(toolName)
        if (localServer != null) return callLocal(localServer, toolName, args)

        // Default to routing table's default location
        if (routingTable.defaultLocation == "local") {
            throw RuntimeException("Tool '$toolName' not found in any local server")
        }
        return callRemote(toolName, args)
    }

    private suspend fun routeByDefinition(
        toolName: String, args: JsonObject, route: ToolRoute
    ): RouteResult {
        return when (route.location) {
            "local" -> tryLocalWithFallback(toolName, args, route)
            "remote" -> tryRemoteWithFallback(toolName, args, route)
            else -> throw RuntimeException("Invalid route location: ${route.location}")
        }
    }

    private suspend fun tryLocalWithFallback(
        toolName: String, args: JsonObject, route: ToolRoute
    ): RouteResult {
        return try {
            val server = route.server ?: localManager.findServerForTool(toolName)
                ?: throw RuntimeException("No local server for tool '$toolName'")
            callLocal(server, toolName, args)
        } catch (e: Exception) {
            if (route.fallback == "remote") {
                logger.info("Local failed for '{}', falling back to remote", toolName)
                callRemote(toolName, args)
            } else throw e
        }
    }

    private suspend fun tryRemoteWithFallback(
        toolName: String, args: JsonObject, route: ToolRoute
    ): RouteResult {
        return try {
            callRemote(toolName, args)
        } catch (e: Exception) {
            if (route.fallback == "local") {
                val server = localManager.findServerForTool(toolName)
                if (server != null) {
                    logger.info("Remote failed for '{}', falling back to local", toolName)
                    return callLocal(server, toolName, args)
                }
            }
            throw e
        }
    }

    private suspend fun callLocal(
        serverName: String, toolName: String, args: JsonObject
    ): RouteResult {
        val result = localManager.callTool(serverName, toolName, args)
        return parseLocalResult(result)
    }

    private suspend fun callRemote(toolName: String, args: JsonObject): RouteResult {
        val params = buildJsonObject {
            put("name", JsonPrimitive(toolName))
            put("arguments", args)
        }
        val response = httpClient.sendRequest("tools/call", params)
        return parseRemoteResult(response)
    }

    private fun parseLocalResult(result: Any?): RouteResult {
        if (result is JsonObject) {
            val content = result["content"]?.jsonArray?.map { el ->
                val obj = el.jsonObject
                mapOf(
                    "type" to (obj["type"]?.jsonPrimitive?.contentOrNull ?: "text"),
                    "text" to (obj["text"]?.jsonPrimitive?.contentOrNull ?: ""),
                )
            } ?: listOf(mapOf("type" to "text", "text" to result.toString()))
            return RouteResult(content, result["isError"]?.jsonPrimitive?.booleanOrNull ?: false)
        }
        return RouteResult(listOf(mapOf("type" to "text", "text" to result.toString())))
    }

    private fun parseRemoteResult(response: JsonObject): RouteResult {
        val resultObj = response["result"]?.jsonObject
        val content = resultObj?.get("content")?.jsonArray?.map { el ->
            val obj = el.jsonObject
            mapOf(
                "type" to (obj["type"]?.jsonPrimitive?.contentOrNull ?: "text"),
                "text" to (obj["text"]?.jsonPrimitive?.contentOrNull ?: ""),
            )
        } ?: listOf(mapOf("type" to "text", "text" to "{}"))
        val isError = resultObj?.get("isError")?.jsonPrimitive?.booleanOrNull ?: false
        return RouteResult(content, isError)
    }

    private fun recordMetric(toolName: String, latencyMs: Long, isError: Boolean) {
        val m = metrics.getOrPut(toolName) { ToolMetrics() }
        m.callCount++
        if (isError) m.errorCount++
        m.totalLatencyMs += latencyMs
        m.lastCallAt = System.currentTimeMillis()
    }
}
