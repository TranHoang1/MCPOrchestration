package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.execution.model.ExecutionContentItem
import com.orchestrator.mcp.model.InvalidFilePathException
import com.orchestrator.mcp.model.OutputSaveFailedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.util.*

/**
 * Unit tests for OutputFileProxyHandlerImpl.
 * STC: UT-12 — Output directory not found
 * STC: UT-13 — Output path not writable
 */
class OutputFileProxyHandlerTest : DescribeSpec({

    val registry = mockk<FileProxyRegistry>(relaxed = true)
    val config = FileProxyConfig()
    val handler = OutputFileProxyHandlerImpl(registry, config)

    describe("processOutputProxy") {
        it("saves artifacts path to output_path") {
            val tempDir = Files.createTempDirectory("output-test")
            val sourceFile = Files.createTempFile(tempDir, "source", ".pdf")
            Files.writeString(sourceFile, "PDF content here")

            val outputPath = "${tempDir}/result.pdf"
            val responseJson = buildJsonObject {
                putJsonArray("artifacts") {
                    addJsonObject { put("path", JsonPrimitive(sourceFile.toString())) }
                }
            }
            val upstreamResponse = ExecuteToolResponse(
                content = listOf(ExecutionContentItem(text = responseJson.toString()))
            )

            try {
                val result = handler.processOutputProxy(upstreamResponse, outputPath)
                val resultText = result.content[0].text
                resultText shouldContain "saved_to"
                resultText shouldContain "bytes_written"

                // Verify file was copied
                val outputContent = Files.readString(java.nio.file.Path.of(outputPath))
                outputContent shouldBe "PDF content here"
            } finally {
                Files.deleteIfExists(sourceFile)
                Files.deleteIfExists(java.nio.file.Path.of(outputPath))
                Files.deleteIfExists(tempDir)
            }
        }

        it("decodes base64 content and saves to output_path") {
            val tempDir = Files.createTempDirectory("output-b64-test")
            val outputPath = "${tempDir}/decoded.bin"
            val originalContent = "Binary file content for testing"
            val base64Content = Base64.getEncoder().encodeToString(originalContent.toByteArray())

            val responseJson = buildJsonObject {
                put("base64_data", JsonPrimitive(base64Content))
            }
            val upstreamResponse = ExecuteToolResponse(
                content = listOf(ExecutionContentItem(text = responseJson.toString()))
            )

            try {
                val result = handler.processOutputProxy(upstreamResponse, outputPath)
                result.content[0].text shouldContain "saved_to"
                result.content[0].text shouldContain "BASE64"

                Files.readString(java.nio.file.Path.of(outputPath)) shouldBe originalContent
            } finally {
                Files.deleteIfExists(java.nio.file.Path.of(outputPath))
                Files.deleteIfExists(tempDir)
            }
        }

        // STC: UT-12 — Output directory not found
        it("throws for non-existent output directory") {
            val upstreamResponse = ExecuteToolResponse(
                content = listOf(ExecutionContentItem(text = """{"artifacts":[]}"""))
            )

            shouldThrow<InvalidFilePathException> {
                handler.processOutputProxy(upstreamResponse, "/nonexistent_dir_xyz/file.pdf")
            }
        }

        it("throws OutputSaveFailedException when no file content in response") {
            val tempDir = Files.createTempDirectory("output-empty-test")
            val outputPath = "${tempDir}/output.pdf"
            val upstreamResponse = ExecuteToolResponse(
                content = listOf(ExecutionContentItem(text = """{"result": "no file"}"""))
            )

            try {
                shouldThrow<OutputSaveFailedException> {
                    handler.processOutputProxy(upstreamResponse, outputPath)
                }
            } finally {
                Files.deleteIfExists(tempDir)
            }
        }
    }

    describe("containsFileContent") {
        it("returns true for response with artifacts") {
            val response = ExecuteToolResponse(
                content = listOf(ExecutionContentItem(text = """{"artifacts":[{"path":"/tmp/f.pdf"}]}"""))
            )
            handler.containsFileContent(response) shouldBe true
        }

        it("returns true for response with base64 field") {
            val response = ExecuteToolResponse(
                content = listOf(ExecutionContentItem(text = """{"base64_content":"abc123"}"""))
            )
            handler.containsFileContent(response) shouldBe true
        }

        it("returns false for plain text response") {
            val response = ExecuteToolResponse(
                content = listOf(ExecutionContentItem(text = """{"message":"hello"}"""))
            )
            handler.containsFileContent(response) shouldBe false
        }
    }
})
