package com.orchestrator.mcp.routing

import com.orchestrator.mcp.auth.AuthMiddleware
import com.orchestrator.mcp.auth.model.AuthException
import com.orchestrator.mcp.auth.model.TokenType
import com.orchestrator.mcp.auth.model.UserContext
import com.orchestrator.mcp.routing.model.RoutingTable
import com.orchestrator.mcp.routing.model.ToolRoute
import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI

/**
 * Integration tests for RoutingTableRoutes using a real HttpServer.
 * Cannot mock HttpExchange (JDK module restriction), so we test via HTTP.
 */
class RoutingTableRoutesTest : FunSpec({

    fun createService(): RoutingTableService {
        val service = mockk<RoutingTableService>()
        every { service.getRoutingTable() } returns RoutingTable(
            version = "1.0.0-abc12345",
            updatedAt = "2026-05-17T12:00:00Z",
            defaultLocation = "remote",
            tools = mapOf(
                "read_file" to ToolRoute("local", "filesystem-server"),
                "jira_search" to ToolRoute("remote", "atlassian")
            )
        )
        every { service.getETag() } returns "\"abc12345\""
        return service
    }

    fun createAuthMiddleware(shouldPass: Boolean = true): AuthMiddleware {
        val middleware = mockk<AuthMiddleware>()
        if (shouldPass) {
            coEvery { middleware.authenticate(any()) } returns UserContext(
                userId = "user-1",
                email = "test@example.com",
                roles = listOf("user"),
                tokenType = TokenType.SESSION
            )
        } else {
            coEvery { middleware.authenticate(any()) } throws
                AuthException.InvalidTokenException("Invalid token")
        }
        return middleware
    }

    fun startServer(service: RoutingTableService, auth: AuthMiddleware): HttpServer {
        val routes = RoutingTableRoutes(service, auth)
        val srv = HttpServer.create(InetSocketAddress(0), 0)
        srv.createContext("/api/routing-table") { exchange -> routes.handle(exchange) }
        srv.start()
        return srv
    }

    fun httpGet(port: Int, path: String, headers: Map<String, String> = emptyMap()): HttpURLConnection {
        val url = URI("http://localhost:$port$path").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.connect()
        return conn
    }

    test("GET returns 200 with routing table JSON") {
        val server = startServer(createService(), createAuthMiddleware())
        try {
            val port = server.address.port
            val conn = httpGet(port, "/api/routing-table", mapOf("Authorization" to "Bearer test"))
            conn.responseCode shouldBe 200
            val body = conn.inputStream.bufferedReader().readText()
            body shouldContain "\"version\""
            body shouldContain "\"read_file\""
            body shouldContain "\"defaultLocation\""
        } finally {
            server.stop(0)
        }
    }

    test("GET returns ETag header") {
        val server = startServer(createService(), createAuthMiddleware())
        try {
            val port = server.address.port
            val conn = httpGet(port, "/api/routing-table", mapOf("Authorization" to "Bearer test"))
            conn.responseCode shouldBe 200
            conn.getHeaderField("ETag") shouldBe "\"abc12345\""
        } finally {
            server.stop(0)
        }
    }

    test("GET returns 304 when ETag matches If-None-Match") {
        val server = startServer(createService(), createAuthMiddleware())
        try {
            val port = server.address.port
            val conn = httpGet(
                port, "/api/routing-table",
                mapOf("Authorization" to "Bearer test", "If-None-Match" to "\"abc12345\"")
            )
            conn.responseCode shouldBe 304
        } finally {
            server.stop(0)
        }
    }

    test("GET returns 200 when ETag does not match") {
        val server = startServer(createService(), createAuthMiddleware())
        try {
            val port = server.address.port
            val conn = httpGet(
                port, "/api/routing-table",
                mapOf("Authorization" to "Bearer test", "If-None-Match" to "\"old-etag\"")
            )
            conn.responseCode shouldBe 200
        } finally {
            server.stop(0)
        }
    }

    test("returns 401 when auth fails") {
        val server = startServer(createService(), createAuthMiddleware(shouldPass = false))
        try {
            val port = server.address.port
            val conn = httpGet(port, "/api/routing-table", mapOf("Authorization" to "Bearer bad"))
            conn.responseCode shouldBe 401
            val body = conn.errorStream.bufferedReader().readText()
            body shouldContain "INVALID_TOKEN"
        } finally {
            server.stop(0)
        }
    }

    test("OPTIONS returns 204 for CORS preflight") {
        val server = startServer(createService(), createAuthMiddleware())
        try {
            val port = server.address.port
            val url = URI("http://localhost:$port/api/routing-table").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "OPTIONS"
            conn.connect()
            conn.responseCode shouldBe 204
        } finally {
            server.stop(0)
        }
    }
})
