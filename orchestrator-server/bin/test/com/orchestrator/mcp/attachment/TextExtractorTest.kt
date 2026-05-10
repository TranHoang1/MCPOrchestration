package com.orchestrator.mcp.attachment

import com.orchestrator.mcp.attachment.extractors.PlainTextExtractor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

class TextExtractorTest : DescribeSpec({

    val extractors = mapOf<String, ContentExtractor>(
        "text/plain" to PlainTextExtractor(),
        "text/markdown" to PlainTextExtractor()
    )
    val textExtractor = TextExtractor(extractors)

    describe("extract") {
        it("extracts plain text from bytes") {
            runTest {
                val bytes = "Hello World".toByteArray()
                val result = textExtractor.extract(bytes, "text/plain")
                result shouldBe "Hello World"
            }
        }

        it("extracts markdown as plain text") {
            runTest {
                val bytes = "# Title\n\nContent".toByteArray()
                val result = textExtractor.extract(bytes, "text/markdown")
                result shouldBe "# Title\n\nContent"
            }
        }

        it("throws UnsupportedMimeTypeException for unknown type") {
            runTest {
                shouldThrow<UnsupportedMimeTypeException> {
                    textExtractor.extract("data".toByteArray(), "application/unknown")
                }
            }
        }
    }

    describe("supports") {
        it("returns true for supported MIME types") {
            textExtractor.supports("text/plain") shouldBe true
            textExtractor.supports("text/markdown") shouldBe true
        }

        it("returns false for unsupported MIME types") {
            textExtractor.supports("application/unknown") shouldBe false
            textExtractor.supports("image/png") shouldBe false
        }
    }
})
