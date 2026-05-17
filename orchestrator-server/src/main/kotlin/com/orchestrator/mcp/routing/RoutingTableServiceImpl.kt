package com.orchestrator.mcp.routing

import com.orchestrator.mcp.core.config.OrchestratorConfig
import com.orchestrator.mcp.core.config.RoutingConfig
import com.orchestrator.mcp.routing.model.RoutingTable
import com.orchestrator.mcp.routing.model.ToolRoute
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

/**
 * Builds routing table from application config (MTO-132).
 * Caches result and invalidates on config change.
 */
class RoutingTableServiceImpl(
    private val config: OrchestratorConfig
) : RoutingTableService {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val cachedTable = AtomicReference<RoutingTable?>(null)
    private val cachedETag = AtomicReference<String>("")

    override fun getRoutingTable(): RoutingTable {
        cachedTable.get()?.let { return it }
        return buildAndCache()
    }

    override fun resolve(toolName: String): ToolRoute? {
        return getRoutingTable().tools[toolName]
    }

    override fun invalidate() {
        cachedTable.set(null)
        cachedETag.set("")
        logger.info("Routing table cache invalidated")
    }

    override fun getETag(): String {
        if (cachedETag.get().isBlank()) getRoutingTable()
        return cachedETag.get()
    }

    @Synchronized
    private fun buildAndCache(): RoutingTable {
        cachedTable.get()?.let { return it }
        val routingConfig = config.orchestrator.routing
        val table = buildTable(routingConfig)
        val etag = computeETag(table)
        cachedTable.set(table)
        cachedETag.set(etag)
        logger.info("Routing table built: ${table.tools.size} tool routes, version=${table.version}")
        return table
    }

    private fun buildTable(routingConfig: RoutingConfig): RoutingTable {
        val tools = buildToolRoutes(routingConfig)
        val version = computeVersion(routingConfig)
        val updatedAt = formatTimestamp(Instant.now())
        return RoutingTable(
            version = version,
            updatedAt = updatedAt,
            defaultLocation = routingConfig.defaultLocation,
            tools = tools
        )
    }

    private fun buildToolRoutes(routingConfig: RoutingConfig): Map<String, ToolRoute> {
        val routes = mutableMapOf<String, ToolRoute>()
        for (server in routingConfig.localServers) {
            for (tool in server.tools) {
                routes[tool] = ToolRoute(
                    location = "local",
                    server = server.name,
                    fallback = server.fallback
                )
            }
        }
        return routes
    }

    private fun computeVersion(routingConfig: RoutingConfig): String {
        val content = routingConfig.localServers
            .sortedBy { it.name }
            .joinToString("|") { "${it.name}:${it.tools.sorted().joinToString(",")}" }
        val hash = md5Short(content)
        return "1.0.0-$hash"
    }

    private fun computeETag(table: RoutingTable): String {
        val content = "${table.version}:${table.updatedAt}:${table.tools.size}"
        return "\"${md5Short(content)}\""
    }

    private fun md5Short(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.take(4).joinToString("") { "%02x".format(it) }
    }

    private fun formatTimestamp(instant: Instant): String {
        return DateTimeFormatter.ISO_INSTANT.format(instant.atOffset(ZoneOffset.UTC))
    }
}
