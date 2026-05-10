package com.orchestrator.mcp.ocr

import com.orchestrator.mcp.ocr.extractor.FileUriResolver
import com.orchestrator.mcp.ocr.extractor.ImageTextExtractor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

/**
 * Unit tests for ImageTextExtractor.
 * STC: UT-03, UT-04, UT-09.
 */
class ImageTextExtractorTest : FunSpec({

    afterEach { clearAllMocks() }

    // UT-03: ImageTextExtractor Delegates to OcrService
    test("UT-03: delegates to OcrService with resolved URI") {
        val ocrService = mockk<OcrService>()
        val resolver = mockk<FileUriResolver>()
        every { resolver.resolve(any()) } returns "file:///tmp/test.png"
        coEvery { ocrService.extractText("file:///tmp/test.png") } returns "extracted text"

        val extractor = ImageTextExtractor(ocrService, resolver)
        val result = extractor.extract(byteArrayOf(1, 2, 3))

        result shouldBe "extracted text"
        coVerify { ocrService.extractText("file:///tmp/test.png") }
    }

    // UT-04: ImageTextExtractor Returns Empty When No URI
    test("UT-04: returns empty string when no URI resolver") {
        val ocrService = mockk<OcrService>()
        val extractor = ImageTextExtractor(ocrService, fileUriResolver = null)

        val result = extractor.extract(byteArrayOf(1, 2, 3))

        result shouldBe ""
        coVerify(exactly = 0) { ocrService.extractText(any()) }
    }

    // UT-09: Exception in Extractor Returns Empty
    test("UT-09: returns empty string when OcrService throws") {
        val ocrService = mockk<OcrService>()
        val resolver = mockk<FileUriResolver>()
        every { resolver.resolve(any()) } returns "file:///tmp/fail.png"
        coEvery { ocrService.extractText(any()) } throws RuntimeException("OCR failed")

        val extractor = ImageTextExtractor(ocrService, resolver)
        val result = extractor.extract(byteArrayOf(1, 2, 3))

        result shouldBe ""
    }

    // Additional: FileUriResolver returns null
    test("returns empty string when resolver returns null") {
        val ocrService = mockk<OcrService>()
        val resolver = mockk<FileUriResolver>()
        every { resolver.resolve(any()) } returns null

        val extractor = ImageTextExtractor(ocrService, resolver)
        val result = extractor.extract(byteArrayOf(1, 2, 3))

        result shouldBe ""
        coVerify(exactly = 0) { ocrService.extractText(any()) }
    }
})
