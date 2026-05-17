package com.orchestrator.mcp.bridge.routing

import com.orchestrator.mcp.bridge.BridgeConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Routing definition for a single tool.
 */
data class ToolRoute(
    val location: String, // "local" or "remote"
    val server: String? = null,
    val fallback: String? = null,
    val priority: Int = 0,
)

/**
 * Full routing table data from orchestrator.
 */
data class RoutingTableData(
    val version: String = "0.0.0",
    val updatedAt: String = "",
    val defaultLocation: String = "remote",
    val tools: Map<String, ToolRoute> = emptyMap(),
)

/**
 * Fetches, caches, and refreshes the tool routing table from the orchestrator.
 * Supports ETag caching and periodic refresh.
 */
class RoutingTable(
    private val config: BridgeConfig,
    private val refreshIntervalMs: Long = 60_000,
) {
    private val logger = LoggerFactory.getLogger(RoutingTable::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private var cached = RoutingTableData()
    private var etag: String? = null
    private var refreshJob: Job? = null

    private val httpClient = HttpClient(CIO) {
        engine { requestTimeout = 10_000 }
    }

    /** Get the cached routing table. */
    fun getCached(): RoutingTableData = cached

    /** Resolve a tool name to its route. */
    fun resolve(toolName: String): ToolRoute? = cached.tools[toolName]

    /** Get the default location when tool is not in table. */
    val defaultLocation: String get() = cached.defaultLocation

    /** Set routing table from initialize response _meta. */
    fun setFromMeta(meta: JsonObject) {
        val rt = meta["routingTable"]?.jsonObject ?: return
        cached = parseTable(rt)
        logger.info("Loaded from _meta ({} routes)", cached.tools.size)
    }

    /** Fetch routing table from orchestrator with ETag caching. */
    suspend fun fetch(): Boolean {
        return try {
            val response = httpClient.get("${config.orchestratorUrl}/api/routing-table") {
                header(HttpHeaders.Accept, "application/json")
                config.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                etag?.let { header(HttpHeaders.IfNoneMatch, it) }
            }

            if (response.status == HttpStatusCode.NotModified) return true
            if (!response.status.isSuccess()) {
                logger.warn("Fetch failed: HTTP {}", response.status.value)
                return false
            }

            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            if (body["tools"] == null) {
                logger.warn("Malformed response: missing tools")
                return false
            }

            cached = parseTable(body)
            etag = response.headers[HttpHeaders.ETag]
            logger.info("Updated ({} routes, v{})", cached.tools.size, cached.version)
            true
        } catch (e: Exception) {
            logger.warn("Fetch error: {} (keeping cache)", e.message)
            false
        }
    }

    /** Start periodic refresh. */
    fun startRefresh(scope: CoroutineScope) {
        if (refreshIntervalMs <= 0) return
        refreshJob = scope.launch { refreshLoop() }
    }

    /** Stop periodic refresh. */
    fun stopRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /** Trigger an immediate refresh. */
    suspend fun refresh() { fetch() }

    private suspend fun refreshLoop() {
        while (currentCoroutineContext().isActive) {
            delay(refreshIntervalMs)
            fetch()
        }
    }

    private fun parseTable(obj: JsonObject): RoutingTableData {
        val tools = mutableMapOf<String, ToolRoute>()
        obj["tools"]?.jsonObject?.forEach { (name, element) ->
            val route = element.jsonObject
            tools[name] = ToolRoute(
                location = route["location"]?.jsonPrimitive?.contentOrNull ?: "remote",
                server = route["server"]?.jsonPrimitive?.contentOrNull,
                fallback = route["fallback"]?.jsonPrimitive?.contentOrNull,
                priority = route["priority"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
        return RoutingTableData(
            version = obj["version"]?.jsonPrimitive?.contentOrNull ?: "0.0.0",
            updatedAt = obj["updatedAt"]?.jsonPrimitive?.contentOrNull ?: "",
            defaultLocation = obj["defaultLocation"]?.jsonPrimitive?.contentOrNull ?: "remote",
            tools = tools,
        )
    }
}
