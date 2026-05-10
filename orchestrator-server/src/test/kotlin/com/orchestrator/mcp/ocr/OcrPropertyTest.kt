package com.orchestrator.mcp.ocr

import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.execution.model.ExecutionContentItem
import com.orchestrator.mcp.ocr.model.OcrConfig
import com.orchestrator.mcp.ocr.model.OcrException
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Property-based tests for OCR module.
 * STC: PBT-01, PBT-02.
 */
class OcrPropertyTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    afterEach { clearAllMocks() }

    // PBT-01: URI Validation — Non-Blank URIs Accepted
    test("PBT-01: non-blank URIs do not throw FileNotFoundException") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        coEvery { dispatcher.execute(any(), any()) } returns
            ExecuteToolResponse(content = listOf(ExecutionContentItem(text = "ok")))

        val config = OcrConfig()
        val service = OcrServiceImpl(dispatcher, config)

        checkAll(100, Arb.string(1..200).filter { it.isNotBlank() }) { uri ->
            // Should not throw FileNotFoundException for non-blank URIs
            val result = service.extractText(uri)
            result shouldNotBe null
        }
    }

    // PBT-02: OcrConfig Serialization Roundtrip
    test("PBT-02: OcrConfig serialization roundtrip preserves data") {
        val configArb = Arb.bind(
            Arb.boolean(),
            Arb.string(1..50).filter { it.isNotBlank() },
            Arb.string(1..50).filter { it.isNotBlank() },
            Arb.int(1..120),
            Arb.int(1..100)
        ) { enabled, serverName, toolName, timeout, maxSize ->
            OcrConfig(
                enabled = enabled,
                serverName = serverName,
                toolName = toolName,
                timeoutSeconds = timeout,
                maxFileSizeMb = maxSize
            )
        }

        checkAll(100, configArb) { config ->
            val encoded = json.encodeToString(config)
            val decoded = json.decodeFromString<OcrConfig>(encoded)
            decoded shouldBe config
        }
    }
})
