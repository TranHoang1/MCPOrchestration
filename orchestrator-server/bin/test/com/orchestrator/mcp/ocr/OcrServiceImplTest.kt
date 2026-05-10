package com.orchestrator.mcp.ocr

import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.execution.model.ExecutionContentItem
import com.orchestrator.mcp.ocr.model.OcrConfig
import com.orchestrator.mcp.ocr.model.OcrException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/**
 * Unit tests for OcrServiceImpl.
 * STC: UT-01 through UT-13, PBT-01.
 */
class OcrServiceImplTest : FunSpec({

    val defaultConfig = OcrConfig()

    afterEach { clearAllMocks() }

    fun textResponse(vararg texts: String) = ExecuteToolResponse(
        content = texts.map { ExecutionContentItem(type = "text", text = it) }
    )

    fun emptyResponse() = ExecuteToolResponse(content = emptyList())

    // UT-01: extractText Routes Through Dispatcher
    test("UT-01: routes extractText through dispatcher with correct tool name") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        val captured = slot<String>()
        coEvery { dispatcher.execute(capture(captured), any()) } returns
            textResponse("extracted text")

        val service = OcrServiceImpl(dispatcher, defaultConfig)
        service.extractText("file:///tmp/image.png")

        captured.captured shouldBe "markitdown/convert_to_markdown"
        coVerify {
            dispatcher.execute(
                "markitdown/convert_to_markdown",
                match { it?.get("uri")?.toString()?.contains("file:///tmp/image.png") == true }
            )
        }
    }

    // UT-02: Server Unavailable Returns Empty String
    test("UT-02: returns empty string when server unavailable") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        coEvery { dispatcher.execute(any(), any()) } throws
            RuntimeException("Connection refused")

        val service = OcrServiceImpl(dispatcher, defaultConfig)
        val result = service.extractText("file:///tmp/image.png")

        result shouldBe ""
    }

    // UT-05: Response Text Extracted as Markdown
    test("UT-05: extracts markdown text from response") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        coEvery { dispatcher.execute(any(), any()) } returns
            textResponse("# Header\nParagraph text")

        val service = OcrServiceImpl(dispatcher, defaultConfig)
        val result = service.extractText("file:///tmp/doc.png")

        result shouldBe "# Header\nParagraph text"
    }

    // UT-06: Empty Response Returns Empty String
    test("UT-06: returns empty string for empty response") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        coEvery { dispatcher.execute(any(), any()) } returns emptyResponse()

        val service = OcrServiceImpl(dispatcher, defaultConfig)
        val result = service.extractText("file:///tmp/blank.png")

        result shouldBe ""
    }

    // UT-07: MCP Tool Call Format Correct
    test("UT-07: formats MCP tool call correctly") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        coEvery { dispatcher.execute(any(), any()) } returns textResponse("text")

        val config = OcrConfig(serverName = "markitdown", toolName = "convert_to_markdown")
        val service = OcrServiceImpl(dispatcher, config)
        service.extractText("file:///path/to/image.png")

        coVerify {
            dispatcher.execute(
                "markitdown/convert_to_markdown",
                match { json ->
                    json != null && json.containsKey("uri")
                }
            )
        }
    }

    // UT-08: Timeout Configuration Respected
    test("UT-08: returns empty string on timeout") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        coEvery { dispatcher.execute(any(), any()) } coAnswers {
            delay(3000)
            textResponse("late response")
        }

        val config = OcrConfig(timeoutSeconds = 1)
        val service = OcrServiceImpl(dispatcher, config)
        val result = service.extractText("file:///tmp/slow.png")

        result shouldBe ""
    }

    // UT-10: Blank URI Throws FileNotFoundException
    test("UT-10: throws FileNotFoundException for blank URI") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        val service = OcrServiceImpl(dispatcher, defaultConfig)

        shouldThrow<OcrException.FileNotFoundException> {
            service.extractText("")
        }
    }

    // UT-11: Disabled Config Returns Empty Immediately
    test("UT-11: returns empty immediately when disabled") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        val config = OcrConfig(enabled = false)
        val service = OcrServiceImpl(dispatcher, config)

        val result = service.extractText("file:///tmp/any.png")

        result shouldBe ""
        coVerify(exactly = 0) { dispatcher.execute(any(), any()) }
    }

    // UT-12: Multiple Text Content Joined
    test("UT-12: joins multiple text content items with newline") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        coEvery { dispatcher.execute(any(), any()) } returns
            textResponse("Line 1", "Line 2")

        val service = OcrServiceImpl(dispatcher, defaultConfig)
        val result = service.extractText("file:///tmp/multi.png")

        result shouldBe "Line 1\nLine 2"
    }

    // UT-13: Non-Text Content Filtered Out
    test("UT-13: filters out non-text content types") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        coEvery { dispatcher.execute(any(), any()) } returns ExecuteToolResponse(
            content = listOf(
                ExecutionContentItem(type = "text", text = "visible"),
                ExecutionContentItem(type = "image", text = "base64data"),
                ExecutionContentItem(type = "text", text = "also visible")
            )
        )

        val service = OcrServiceImpl(dispatcher, defaultConfig)
        val result = service.extractText("file:///tmp/mixed.png")

        result shouldBe "visible\nalso visible"
    }
})
