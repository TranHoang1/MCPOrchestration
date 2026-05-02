package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.discovery.ToolDiscoveryService
import com.orchestrator.mcp.discovery.model.FindToolsResponse
import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.execution.model.ExecutionContentItem
import com.orchestrator.mcp.execution.model.ExecutionMeta
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockk

class McpServerFactoryTest : FunSpec({

    lateinit var discoveryService: ToolDiscoveryService
    lateinit var executionDispatcher: ToolExecutionDispatcher
    lateinit var factory: McpServerFactory

    beforeEach {
        discoveryService = mockk()
        executionDispatcher = mockk()
        factory = McpServerFactory(discoveryService, executionDispatcher)
    }

    test("create returns a non-null Server instance") {
        val server = factory.create()
        server shouldNotBe null
    }

    test("create with mock dependencies succeeds") {
        coEvery { discoveryService.findTools(any(), any(), any()) } returns FindToolsResponse(
            tools = emptyList(),
            searchMode = "semantic",
            totalIndexed = 0
        )
        coEvery { executionDispatcher.execute(any(), any()) } returns ExecuteToolResponse(
            content = listOf(ExecutionContentItem(text = "test")),
            meta = ExecutionMeta(upstreamServer = "test", executionTimeMs = 10)
        )

        val server = factory.create()
        server shouldNotBe null
    }
})
