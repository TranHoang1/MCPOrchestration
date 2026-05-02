package com.orchestrator.mcp.it

import com.orchestrator.mcp.discovery.ToolDiscoveryService
import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.protocol.JsonRpcHandler
import com.orchestrator.mcp.protocol.McpProtocolHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.serialization.json.*

/**
 * Integration tests for MCP Protocol layer.
 */
class McpProtocolIntegrationTest : FunSpec({

    lateinit var discoveryService: ToolDiscoveryService
    lateinit var executionDispatcher: ToolExecutionDispatcher
    lateinit var handler: JsonRpcHandler

    beforeEach {
        discoveryService = mockk()
        executionDispatcher = mockk()
        val protocolHandler = McpProtocolHandler(discoveryService, executionDispatcher)
        handler = JsonRpcHandler(protocolHandler)
    }

    // STC: IT-023 — MCP initialize handshake
    test("IT-023: MCP initialize handshake") {
        val request = """
            {"jsonrpc":"2.0","id":0,"method":"initialize","params":{
                "protocolVersion":"2024-11-05",
                "capabilities":{},
                "clientInfo":{"name":"kiro","version":"1.0.0"}
            }}
        """.trimIndent()

        val response = handler.handleMessage(request)!!

        response.contains("2024-11-05") shouldBe true
        response.contains("mcp-orchestrator") shouldBe true
        response.contains("tools") shouldBe true
    }

    // STC: IT-024 — MCP tools/list returns exactly 2 tools
    test("IT-024: MCP tools/list returns exactly 2 tools") {
        val request = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""

        val response = handler.handleMessage(request)!!
        val parsed = Json.parseToJsonElement(response).jsonObject
        val result = parsed["result"]!!.jsonObject
        val tools = result["tools"]!!.jsonArray

        tools.size shouldBe 2
        val names = tools.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        names.contains("find_tools") shouldBe true
        names.contains("execute_dynamic_tool") shouldBe true
    }

    // STC: IT-025 — MCP ping returns empty result
    test("IT-025: MCP ping returns empty result") {
        val request = """{"jsonrpc":"2.0","id":2,"method":"ping"}"""

        val response = handler.handleMessage(request)
        response.shouldNotBeNull()
        val parsed = Json.parseToJsonElement(response).jsonObject
        val result = parsed["result"]!!.jsonObject

        result.size shouldBe 0
    }

    test("IT-023b: all responses have jsonrpc 2.0") {
        val requests = listOf(
            """{"jsonrpc":"2.0","id":0,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""",
            """{"jsonrpc":"2.0","id":2,"method":"ping"}"""
        )

        requests.forEach { request ->
            val response = handler.handleMessage(request)
            response.shouldNotBeNull()
            response.contains("\"jsonrpc\":\"2.0\"") shouldBe true
        }
    }

    test("IT-024b: each tool has description and inputSchema") {
        val request = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
        val response = handler.handleMessage(request)
        response.shouldNotBeNull()
        val parsed = Json.parseToJsonElement(response).jsonObject
        val tools = parsed["result"]!!.jsonObject["tools"]!!.jsonArray

        tools.forEach { tool ->
            val obj = tool.jsonObject
            obj.containsKey("description") shouldBe true
            obj.containsKey("inputSchema") shouldBe true
        }
    }

    test("notification does not return response") {
        val notification = """{"jsonrpc":"2.0","method":"notifications/initialized"}"""
        val response = handler.handleMessage(notification)
        response shouldBe null
    }
})
