package com.orchestrator.mcp.ocr

import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.execution.model.ExecutionContentItem
import com.orchestrator.mcp.ocr.di.ocrModule
import com.orchestrator.mcp.ocr.extractor.ImageTextExtractor
import com.orchestrator.mcp.ocr.model.OcrConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Integration tests for OCR module with Koin DI.
 * STC: IT-01 through IT-06, E2E-01 through E2E-03.
 */
class OcrIntegrationTest : FunSpec({

    afterEach {
        clearAllMocks()
        try { stopKoin() } catch (_: Exception) {}
    }

    fun textResponse(text: String) = ExecuteToolResponse(
        content = listOf(ExecutionContentItem(type = "text", text = text))
    )

    fun startWithMock(response: ExecuteToolResponse): OcrService {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        coEvery { dispatcher.execute(any(), any()) } returns response
        val koin = startKoin {
            modules(
                module { single<ToolExecutionDispatcher> { dispatcher } },
                ocrModule
            )
        }.koin
        return koin.get()
    }

    // IT-01: PNG Image OCR End-to-End
    test("IT-01: PNG image OCR through full DI") {
        val service = startWithMock(
            textResponse("# Invoice\nDate: 2024-01-15\nAmount: \$1,500.00")
        )
        val result = service.extractText("file:///tmp/document.png")
        result shouldBe "# Invoice\nDate: 2024-01-15\nAmount: \$1,500.00"
    }

    // IT-02: JPG Image OCR
    test("IT-02: JPG image OCR works") {
        val service = startWithMock(textResponse("Photo caption text"))
        val result = service.extractText("file:///tmp/photo.jpg")
        result shouldBe "Photo caption text"
    }

    // IT-03: TIFF Document OCR
    test("IT-03: TIFF document OCR works") {
        val service = startWithMock(textResponse("Scanned document content"))
        val result = service.extractText("file:///tmp/scan.tiff")
        result shouldBe "Scanned document content"
    }

    // IT-04: MCP Server Unavailable — Graceful
    test("IT-04: graceful degradation when MCP server unavailable") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        coEvery { dispatcher.execute(any(), any()) } throws
            RuntimeException("Connection refused")
        val koin = startKoin {
            modules(
                module { single<ToolExecutionDispatcher> { dispatcher } },
                ocrModule
            )
        }.koin
        val service = koin.get<OcrService>()

        val result = service.extractText("file:///tmp/image.png")
        result shouldBe ""
    }

    // IT-05: Empty Image Response
    test("IT-05: returns empty for empty image response") {
        val service = startWithMock(ExecuteToolResponse(content = emptyList()))
        val result = service.extractText("file:///tmp/blank.png")
        result shouldBe ""
    }

    // IT-06: Timeout Integration
    test("IT-06: timeout handled gracefully in coroutine context") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        coEvery { dispatcher.execute(any(), any()) } coAnswers {
            delay(3000)
            textResponse("too late")
        }
        val koin = startKoin {
            modules(
                module { single<ToolExecutionDispatcher> { dispatcher } },
                module {
                    single { OcrConfig(timeoutSeconds = 1) }
                    single<OcrService> { OcrServiceImpl(get(), get()) }
                    single { ImageTextExtractor(ocrService = get()) }
                }
            )
        }.koin
        val service = koin.get<OcrService>()

        val result = service.extractText("file:///tmp/slow.png")
        result shouldBe ""
    }

    // E2E-01: Full DI Container OCR
    test("E2E-01: full DI container resolves and executes OCR") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        coEvery { dispatcher.execute(any(), any()) } returns textResponse("DI works")
        val koin = startKoin {
            modules(
                module { single<ToolExecutionDispatcher> { dispatcher } },
                ocrModule
            )
        }.koin

        val service = koin.get<OcrService>()
        val result = service.extractText("file:///tmp/test.png")
        result shouldBe "DI works"
    }

    // E2E-02: ImageTextExtractor via DI
    test("E2E-02: ImageTextExtractor resolved via DI") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        coEvery { dispatcher.execute(any(), any()) } returns textResponse("text")
        val koin = startKoin {
            modules(
                module { single<ToolExecutionDispatcher> { dispatcher } },
                ocrModule
            )
        }.koin

        val extractor = koin.get<ImageTextExtractor>()
        extractor shouldNotBe null
    }

    // E2E-03: Module Wiring Verification
    test("E2E-03: all DI bindings resolve correctly") {
        val dispatcher = mockk<ToolExecutionDispatcher>()
        coEvery { dispatcher.execute(any(), any()) } returns textResponse("")
        val koin = startKoin {
            modules(
                module { single<ToolExecutionDispatcher> { dispatcher } },
                ocrModule
            )
        }.koin

        koin.get<OcrConfig>() shouldNotBe null
        koin.get<OcrService>() shouldNotBe null
        koin.get<ImageTextExtractor>() shouldNotBe null
    }
})
