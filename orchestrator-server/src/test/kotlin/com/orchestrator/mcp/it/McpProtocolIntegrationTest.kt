package com.orchestrator.mcp.it

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*

/**
 * IT tests for MCP Protocol layer.
 * Real: JsonRpcHandler → McpProtocolHandler →
 *       ToolDiscoveryServiceImpl, ToolExecutionDispatcherImpl,
 *       ToolRegistryImpl, KeywordSearchEngine
 * Mock: EmbeddingService, VectorDbClient (external services)
 */
class McpProtocolIntegrationTest : FunSpec({

    lateinit var stack: ProtocolStack

    beforeEach {
        stack = IntegrationTestBase.protocolStack()
    }

    // STC: IT-023 — MCP initialize handshake
    test("IT-023: initialize returns protocol version") {
        val req = """
            {"jsonrpc":"2.0","id":0,"method":"initialize",
             "params":{"protocolVersion":"2024-11-05",
             "capabilities":{},
             "clientInfo":{"name":"kiro","version":"1.0.0"}}}
        """.trimIndent()

        val resp = stack.handler.handleMessage(req)!!

        resp.contains("2024-11-05") shouldBe true
        resp.contains("mcp-orchestrator") shouldBe true
        resp.contains("tools") shouldBe true
    }

    // STC: IT-024 — tools/list returns exactly 2 tools
    test("IT-024: tools/list returns 2 tools") {
        val req = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""

        val resp = stack.handler.handleMessage(req)!!
        val parsed = Json.parseToJsonElement(resp).jsonObject
        val tools = parsed["result"]!!
            .jsonObject["tools"]!!.jsonArray

        tools.size shouldBe 2
        val names = tools.map {
            it.jsonObject["name"]!!.jsonPrimitive.content
        }
        names.contains("find_tools") shouldBe true
        names.contains("execute_dynamic_tool") shouldBe true
    }

    // STC: IT-025 — ping returns empty result
    test("IT-025: ping returns empty result") {
        val req = """{"jsonrpc":"2.0","id":2,"method":"ping"}"""

        val resp = stack.handler.handleMessage(req)
        resp.shouldNotBeNull()
        val parsed = Json.parseToJsonElement(resp).jsonObject
        val result = parsed["result"]!!.jsonObject

        result.size shouldBe 0
    }

    test("IT-023b: all responses have jsonrpc 2.0") {
        val requests = listOf(
            """{"jsonrpc":"2.0","id":0,"method":"initialize",
               "params":{}}""",
            """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""",
            """{"jsonrpc":"2.0","id":2,"method":"ping"}"""
        )

        requests.forEach { request ->
            val resp = stack.handler.handleMessage(request)
            resp.shouldNotBeNull()
            resp.contains("\"jsonrpc\":\"2.0\"") shouldBe true
        }
    }

    test("IT-024b: each tool has description and inputSchema") {
        val req = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
        val resp = stack.handler.handleMessage(req)
        resp.shouldNotBeNull()
        val parsed = Json.parseToJsonElement(resp).jsonObject
        val tools = parsed["result"]!!
            .jsonObject["tools"]!!.jsonArray

        tools.forEach { tool ->
            val obj = tool.jsonObject
            obj.containsKey("description") shouldBe true
            obj.containsKey("inputSchema") shouldBe true
        }
    }

    test("notification does not return response") {
        val notif = """
            {"jsonrpc":"2.0",
             "method":"notifications/initialized"}
        """.trimIndent()
        val resp = stack.handler.handleMessage(notif)
        resp shouldBe null
    }
})
