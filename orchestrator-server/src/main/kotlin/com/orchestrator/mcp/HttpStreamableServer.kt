package com.orchestrator.mcp

import com.orchestrator.mcp.core.config.OrchestratorConfig
import com.orchestrator.mcp.fileproxy.FileProxyCleanupService
import com.orchestrator.mcp.fileproxy.FileProxyService
import com.orchestrator.mcp.protocol.HiddenToolRegistrar
import com.orchestrator.mcp.protocol.McpServerFactory
import com.orchestrator.mcp.protocol.HttpToolRouter
import com.orchestrator.mcp.registry.ToolIndexer
import com.orchestrator.mcp.registry.ToolRegistry
import com.orchestrator.mcp.client.upstream.HealthMonitor
import com.orchestrator.mcp.client.upstream.UpstreamServerManager
import com.orchestrator.mcp.server.addCorsHeaders
import com.orchestrator.mcp.server.addSecurityHeaders
import com.orchestrator.mcp.server.authenticatedContext
import com.orchestrator.mcp.server.authenticatedPageContext
import com.orchestrator.mcp.server.handleCorsPreflightIfNeeded
import com.orchestrator.mcp.server.registerSyncDashboardRoutes
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.Executors

private val httpLogger = LoggerFactory.getLogger(
    "com.orchestrator.mcp.HttpStreamableServer"
)

/**
 * HTTP Streamable mode using Java's built-in HttpServer.
 * Avoids Ktor Netty event loop issues with suspend functions.
 * Each request runs on its own thread from a thread pool.
 */
suspend fun CoroutineScope.startHttpStreamableServer(
    config: OrchestratorConfig,
    mcpServerFactory: McpServerFactory,
    serverManager: UpstreamServerManager,
    healthMonitor: HealthMonitor,
    toolIndexer: ToolIndexer,
    fileProxyService: FileProxyService,
    fileProxySessionId: UUID,
    fileProxyCleanup: FileProxyCleanupService
) {
    val port = config.orchestrator.server.port
    httpLogger.info(
        "Starting MCP Orchestrator in HTTP Streamable " +
            "mode on port $port"
    )

    val router = HttpToolRouter(mcpServerFactory)

    // Connect upstream servers in background
    launch {
        try {
            serverManager.connectAll()
            val states = serverManager.getAllServerStates()
            val connected = states.count {
                it.value.status ==
                    com.orchestrator.mcp.client.upstream
                        .model.ServerState.CONNECTED
            }
            httpLogger.info(
                "Upstream: $connected connected, " +
                    "${states.size - connected} failed"
            )
            // Register hidden + sync tools BEFORE indexing (so they get indexed too)
            val toolRegistry = org.koin.java.KoinJavaComponent.getKoin()
                .get<com.orchestrator.mcp.registry.ToolRegistry>()
            HiddenToolRegistrar.registerHiddenTools(toolRegistry)
            registerUserManagementTools(toolRegistry)

            // Run User Management migration + seed permissions
            runUserManagementStartup()

            httpLogger.info("Starting tool indexing...")
            val result = toolIndexer.indexAll()
            httpLogger.info(
                "Tool indexing completed: " +
                    "${result.totalIndexed} tools indexed"
            )
            // File Proxy wrapping disabled in server mode — bridges handle file transfer
            httpLogger.info("File Proxy wrapping skipped (server mode)")
        } catch (e: Exception) {
            httpLogger.error(
                "Failed to connect upstream: ${e.message}"
            )
        }
    }

    healthMonitor.start(this)

    // Use Java HttpServer with thread pool
    val executor = Executors.newFixedThreadPool(8)
    val server = HttpServer.create(
        InetSocketAddress(port), 0
    )
    server.executor = executor

    server.createContext("/mcp") { exchange ->
        if (handleCorsPreflightIfNeeded(exchange)) return@createContext
        val headers = exchange.requestHeaders.entries.associate { (k, v) ->
            k to (v.firstOrNull() ?: "")
        }
        val authMiddleware = org.koin.java.KoinJavaComponent.getKoin()
            .get<com.orchestrator.mcp.auth.AuthMiddleware>()
        val userCtx = try {
            runBlocking { authMiddleware.authenticate(headers) }
        } catch (e: com.orchestrator.mcp.auth.model.AuthException) {
            val err = """{"error":"${e.errorCode}","message":"Authentication required"}"""
            val bytes = err.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            addSecurityHeaders(exchange)
            exchange.sendResponseHeaders(e.httpStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            return@createContext
        }
        handleMcpRequest(exchange, router)
    }

    server.createContext("/health") { exchange ->
        val response = "OK"
        exchange.sendResponseHeaders(200, response.length.toLong())
        exchange.responseBody.use { it.write(response.toByteArray()) }
    }

    // Graph API endpoints (MTO-22) — protected by auth (MTO-109)
    val graphService = org.koin.java.KoinJavaComponent.getKoin()
        .getOrNull<com.orchestrator.mcp.graph.GraphService>()
    val authMiddleware = org.koin.java.KoinJavaComponent.getKoin()
        .get<com.orchestrator.mcp.auth.AuthMiddleware>()

    if (graphService != null) {
        authenticatedContext(server, "/sync/graph", authMiddleware) { exchange, _ ->
            handleGraphRequest(exchange, graphService)
        }
        httpLogger.info("Graph API registered: /sync/graph/* (authenticated)")
    }

    // Projects list API (MTO-83) — protected by auth (MTO-109)
    val ticketCacheRepo = org.koin.java.KoinJavaComponent.getKoin()
        .getOrNull<com.orchestrator.mcp.sync.TicketCacheRepository>()
    if (ticketCacheRepo != null) {
        authenticatedContext(server, "/sync/projects", authMiddleware) { exchange, _ ->
            handleProjectsRequest(exchange, ticketCacheRepo)
        }
        httpLogger.info("Projects API registered: /sync/projects (authenticated)")
    }

    // Sync Dashboard API endpoints (MTO-83) — protected by auth (MTO-109)
    val dashboardService = org.koin.java.KoinJavaComponent.getKoin()
        .getOrNull<com.orchestrator.mcp.dashboard.SyncDashboardService>()
    val eventBus = org.koin.java.KoinJavaComponent.getKoin()
        .getOrNull<com.orchestrator.mcp.dashboard.SyncEventBus>()
    if (dashboardService != null && eventBus != null) {
        registerSyncDashboardRoutes(server, authMiddleware, dashboardService, eventBus)
    }

    // Admin API endpoints (MTO-39: User Management)
    val adminRoutes = org.koin.java.KoinJavaComponent.getKoin()
        .getOrNull<com.orchestrator.mcp.usermanagement.routes.AdminRoutes>()
    if (adminRoutes != null) {
        server.createContext("/admin") { exchange: HttpExchange ->
            adminRoutes.handle(exchange)
        }
        httpLogger.info("Admin API registered: /admin/*")
    }

    // Auth API endpoints (MTO-95: JWT Auth)
    val authRouteHandler = org.koin.java.KoinJavaComponent.getKoin()
        .getOrNull<com.orchestrator.mcp.auth.AuthRouteHandler>()
    if (authRouteHandler != null) {
        server.createContext("/api/auth") { exchange: HttpExchange ->
            authRouteHandler.handle(exchange)
        }
        httpLogger.info("Auth API registered: /api/auth/*")
    }

    // SSO API endpoints (MTO-101: SSO Integration)
    val ssoRoutes = org.koin.java.KoinJavaComponent.getKoin()
        .getOrNull<com.orchestrator.mcp.auth.sso.SsoRoutes>()
    if (ssoRoutes != null) {
        server.createContext("/api/auth/sso") { exchange: HttpExchange ->
            ssoRoutes.handlePublic(exchange)
        }
        server.createContext("/api/admin/sso/config") { exchange: HttpExchange ->
            ssoRoutes.handleAdmin(exchange)
        }
        httpLogger.info("SSO API registered: /api/auth/sso/*, /api/admin/sso/config")
    }

    // User Credential API endpoints (MTO-94: User credentials)
    val userCredentialRoutes = org.koin.java.KoinJavaComponent.getKoin()
        .getOrNull<com.orchestrator.mcp.credentials.UserCredentialRoutes>()
    if (userCredentialRoutes != null) {
        server.createContext("/api/credentials") { exchange: HttpExchange ->
            userCredentialRoutes.handle(exchange)
        }
        httpLogger.info("User Credential API registered: /api/credentials/*")
    }

    // Credential Schema API endpoints (MTO-96: Admin CRUD)
    val credentialSchemaRoutes = org.koin.java.KoinJavaComponent.getKoin()
        .getOrNull<com.orchestrator.mcp.credentials.CredentialSchemaRoutes>()
    if (credentialSchemaRoutes != null) {
        server.createContext("/api/admin/credential-schemas") { exchange: HttpExchange ->
            credentialSchemaRoutes.handle(exchange)
        }
        httpLogger.info("Credential Schema API registered: /api/admin/credential-schemas/*")
    }

    // Routing Table API endpoint (MTO-132: Bridge routing config)
    val routingTableRoutes = org.koin.java.KoinJavaComponent.getKoin()
        .getOrNull<com.orchestrator.mcp.routing.RoutingTableRoutes>()
    if (routingTableRoutes != null) {
        server.createContext("/api/routing-table") { exchange: HttpExchange ->
            routingTableRoutes.handle(exchange)
        }
        httpLogger.info("Routing Table API registered: /api/routing-table")
    }

    // Static file serving (graph-viewer.html, sync-dashboard.html)
    server.createContext("/static") { exchange ->
        handleStaticFile(exchange)
    }
    // HTML pages served public — auth enforced by API endpoints they call (MTO-109)
    // JS in each page checks token validity; if API returns 401 → redirects to /login
    server.createContext("/sync/graph-viewer") { exchange ->
        serveResource(exchange, "static/graph-viewer.html")
    }
    server.createContext("/sync/dashboard") { exchange ->
        serveResource(exchange, "static/sync-dashboard.html")
    }
    // Convenience aliases for auth UI pages (MTO-94)
    server.createContext("/login") { exchange ->
        serveResource(exchange, "static/login.html")
    }
    server.createContext("/profile") { exchange ->
        serveResource(exchange, "static/profile.html")
    }
    server.createContext("/admin/schemas") { exchange ->
        serveResource(exchange, "static/admin-schemas.html")
    }
    // Serve nav-bar.js from root so alias routes can load it via relative path
    server.createContext("/nav-bar.js") { exchange ->
        serveResource(exchange, "static/nav-bar.js")
    }

    server.start()
    httpLogger.info(
        "HTTP Streamable server listening on port $port"
    )

    // Block forever
    val done = kotlinx.coroutines.Job()
    done.join()
}

private fun handleMcpRequest(
    exchange: HttpExchange,
    router: HttpToolRouter
) {
    if (exchange.requestMethod != "POST") {
        exchange.sendResponseHeaders(405, -1)
        exchange.close()
        return
    }

    val body = exchange.requestBody.bufferedReader()
        .use { it.readText() }

    if (body.isBlank()) {
        exchange.sendResponseHeaders(400, -1)
        exchange.close()
        return
    }

    // Extract headers for auth context propagation
    val headers = exchange.requestHeaders.entries.associate { (k, v) ->
        k to (v.firstOrNull() ?: "")
    }

    // Run suspend function in blocking context
    // (each request has its own thread from pool)
    val response = runBlocking {
        router.handle(body, headers)
    }

    val bytes = response.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.add(
        "Content-Type", "application/json"
    )
    addSecurityHeaders(exchange)
    addCorsHeaders(exchange)
    exchange.sendResponseHeaders(200, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

private fun handleProjectsRequest(
    exchange: HttpExchange,
    repo: com.orchestrator.mcp.sync.TicketCacheRepository
) {
    if (exchange.requestMethod != "GET") {
        exchange.sendResponseHeaders(405, -1)
        return
    }
    val projects = kotlinx.coroutines.runBlocking { repo.listProjects() }
    val body = kotlinx.serialization.json.Json.encodeToString(projects)
    exchange.responseHeaders.add("Content-Type", "application/json")
    addCorsHeaders(exchange)
    addSecurityHeaders(exchange)
    val bytes = body.toByteArray()
    exchange.sendResponseHeaders(200, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

private fun handleGraphRequest(
    exchange: HttpExchange,
    graphService: com.orchestrator.mcp.graph.GraphService
) {
    if (exchange.requestMethod != "GET") {
        exchange.sendResponseHeaders(405, -1)
        exchange.close()
        return
    }

    val path = exchange.requestURI.path.removePrefix("/sync/graph")
    val parts = path.split("/").filter { it.isNotBlank() }
    val query = exchange.requestURI.query?.split("&")
        ?.associate {
            val (k, v) = it.split("=", limit = 2)
            k to v
        } ?: emptyMap()

    val view = com.orchestrator.mcp.graph.model.ViewMode
        .fromString(query["view"])
    val depth = query["depth"]?.toIntOrNull()?.coerceIn(1, 5) ?: 2

    val json = kotlinx.serialization.json.Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    val response = runBlocking {
        when (parts.size) {
            1 -> graphService.getProjectGraph(parts[0], view)
            2 -> graphService.getSubgraph(parts[0], parts[1], depth, view)
            else -> null
        }
    }

    if (response == null) {
        val err = """{"error":"Usage: /sync/graph/{projectKey} or /sync/graph/{projectKey}/{issueKey}"}"""
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(400, err.length.toLong())
        exchange.responseBody.use { it.write(err.toByteArray()) }
        return
    }

    val body = json.encodeToString(
        com.orchestrator.mcp.graph.model.GraphResponse.serializer(),
        response
    )
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.add("Content-Type", "application/json")
    addCorsHeaders(exchange)
    addSecurityHeaders(exchange)
    exchange.sendResponseHeaders(200, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

private fun handleStaticFile(exchange: HttpExchange) {
    val path = exchange.requestURI.path.removePrefix("/static/")
    serveResource(exchange, "static/$path")
}

private fun serveResource(exchange: HttpExchange, resourcePath: String) {
    val stream = Thread.currentThread().contextClassLoader
        .getResourceAsStream(resourcePath)

    if (stream == null) {
        val msg = "Not found: $resourcePath"
        exchange.sendResponseHeaders(404, msg.length.toLong())
        exchange.responseBody.use { it.write(msg.toByteArray()) }
        return
    }

    val bytes = stream.use { it.readAllBytes() }
    val contentType = when {
        resourcePath.endsWith(".html") -> "text/html; charset=utf-8"
        resourcePath.endsWith(".js") -> "application/javascript"
        resourcePath.endsWith(".css") -> "text/css"
        resourcePath.endsWith(".png") -> "image/png"
        resourcePath.endsWith(".svg") -> "image/svg+xml"
        else -> "application/octet-stream"
    }

    exchange.responseHeaders.add("Content-Type", contentType)
    addCorsHeaders(exchange)
    addSecurityHeaders(exchange)
    exchange.sendResponseHeaders(200, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}


/**
 * Register User Management MCP tools (MTO-39).
 * Tools: approve_document, get_approval_status, list_pending_approvals
 */
private fun registerUserManagementTools(toolRegistry: ToolRegistry) {
    val entries = com.orchestrator.mcp.usermanagement.tools.UserManagementToolRegistrar.getToolEntries()
    entries.forEach { entry ->
        toolRegistry.registerTool(entry)
        toolRegistry.setHidden(entry.name, true)
    }
    httpLogger.info("Registered user management tools: ${entries.joinToString { it.name }}")
}

/**
 * Run User Management startup tasks: permission seeding + admin seeding.
 * Note: Schema migration is handled by Flyway (MTO-108). No DDL here.
 */
private fun runUserManagementStartup() {
    try {
        val permissionService = org.koin.java.KoinJavaComponent.getKoin()
            .get<com.orchestrator.mcp.usermanagement.service.PermissionService>()
        kotlinx.coroutines.runBlocking { permissionService.seedIfEmpty() }
    } catch (e: Exception) {
        httpLogger.warn("Permission seeding failed (non-critical): ${e.message}")
    }

    // Seed default admin user if users table is empty (MTO-95)
    try {
        val adminSeeder = org.koin.java.KoinJavaComponent.getKoin()
            .getOrNull<com.orchestrator.mcp.auth.AdminSeeder>()
        adminSeeder?.seedIfEmpty()
    } catch (e: Exception) {
        httpLogger.warn("Admin seeding failed: ${e.message}")
    }

    httpLogger.info("User Management startup complete")

    // Start Jira approval sync background job
    try {
        val syncJob = org.koin.java.KoinJavaComponent.getKoin()
            .get<com.orchestrator.mcp.usermanagement.service.ApprovalJiraSyncJob>()
        syncJob.start()
    } catch (e: Exception) {
        httpLogger.warn("Approval Jira sync job failed to start: ${e.message}")
    }
}
