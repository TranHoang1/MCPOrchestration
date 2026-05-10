package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.discovery.ToolDiscoveryService
import com.orchestrator.mcp.discovery.model.FindToolsResponse
import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.execution.model.ExecutionContentItem
import com.orchestrator.mcp.execution.model.ExecutionMeta
import com.orchestrator.mcp.protocol.model.JsonRpcRequest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.*

class JsonRpcHandlerTest : FunSpec({

    lateinit var discoveryService: ToolDiscoveryService
    lateinit var executionDispatcher: ToolExecutionDispatcher
    lateinit var handler: JsonRpcHandler

    beforeEach {
        discoveryService = mockk()
        executionDispatcher = mockk()
        val protocolHandler = McpProtocolHandler(discoveryService, executionDispatcher)
        handler = JsonRpcHandler(protocolHandler)
    }

    // STC: UT-034 — parseRequest valid JSON-RPC request parsed correctly
    test("UT-034: parseRequest parses valid JSON-RPC request") {
        val raw = """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_tools","arguments":{"query":"test"}}}"""
        val request = handler.parseRequest(raw)

        request.method shouldBe "tools/call"
        request.params?.get("name")?.jsonPrimitive?.content shouldBe "find_tools"
    }

    // STC: UT-035 — handleInitialize returns correct MCP capabilities
    test("UT-035: handleInitialize returns correct MCP capabilities") {
        val request = """{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2024-11-05"}}"""
        val response = handler.handleMessage(request)!!

        val json = Json.parseToJsonElement(response).jsonObject
        val result = json["result"]!!.jsonObject
        result["protocolVersion"]?.jsonPrimitive?.content shouldBe "2024-11-05"
        result["serverInfo"]?.jsonObject?.get("name")?.jsonPrimitive?.content shouldBe "mcp-orchestrator"
        result["capabilities"]?.jsonObject?.containsKey("tools") shouldBe true
    }

    test("tools/list returns exactly 2 tools") {
        val request = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
        val response = handler.handleMessage(request)!!

        val json = Json.parseToJsonElement(response).jsonObject
        val result = json["result"]!!.jsonObject
        val tools = result["tools"]!!.jsonArray
        tools.size shouldBe 2

        val names = tools.map { it.jsonObject["name"]?.jsonPrimitive?.content }
        names shouldBe listOf("find_tools", "execute_dynamic_tool")
    }

    test("ping returns empty result") {
        val request = """{"jsonrpc":"2.0","id":1,"method":"ping"}"""
        val response = handler.handleMessage(request)!!

        val json = Json.parseToJsonElement(response).jsonObject
        val result = json["result"]!!.jsonObject
        result.size shouldBe 0
    }

    test("tools/call with find_tools dispatches correctly") {
        coEvery { discoveryService.findTools(any(), any(), any()) } returns FindToolsResponse(
            tools = emptyList(),
            searchMode = "semantic",
            totalIndexed = 0
        )

        val request = """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_tools","arguments":{"query":"test","top_k":5}}}"""
        val response = handler.handleMessage(request)!!

        response shouldContain "result"
    }

    test("tools/call with execute_dynamic_tool dispatches correctly") {
        coEvery { executionDispatcher.execute(any(), any()) } returns ExecuteToolResponse(
            content = listOf(ExecutionContentItem(text = "result data")),
            meta = ExecutionMeta(upstreamServer = "test-server", executionTimeMs = 50)
        )

        val request = """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"execute_dynamic_tool","arguments":{"tool_name":"read_logs","arguments":{"path":"/tmp"}}}}"""
        val response = handler.handleMessage(request)!!

        response shouldContain "result"
    }

    test("notifications are not responded to") {
        val request = """{"jsonrpc":"2.0","method":"notifications/initialized"}"""
        val response = handler.handleMessage(request)

        response shouldBe null
    }

    // STC: PBT-006 — JSON-RPC request/response serialization roundtrip
    test("PBT-006: JSON-RPC serialization roundtrip") {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        forAll(100, Arb.int(1..1000), Arb.element("tools/call", "tools/list", "initialize", "ping")) { id, method ->
            val request = JsonRpcRequest(
                id = JsonPrimitive(id),
                method = method,
                params = if (method == "tools/call") buildJsonObject {
                    put("name", "find_tools")
                } else null
            )
            val serialized = json.encodeToString(JsonRpcRequest.serializer(), request)
            val deserialized = json.decodeFromString(JsonRpcRequest.serializer(), serialized)
            deserialized.method == request.method && deserialized.id == request.id
        }
    }
})
