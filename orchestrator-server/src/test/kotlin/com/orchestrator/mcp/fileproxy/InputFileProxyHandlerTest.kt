package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.execution.model.ExecutionContentItem
import com.orchestrator.mcp.fileproxy.model.FileProxyEntry
import com.orchestrator.mcp.fileproxy.model.FileProxyStatus
import com.orchestrator.mcp.core.model.FileNotFoundException
import com.orchestrator.mcp.core.model.FileTooLargeException
import com.orchestrator.mcp.core.model.InvalidFilePathException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.util.*

/**
 * Unit tests for InputFileProxyHandlerImpl.
 * STC: UT-05 — File not found
 * STC: UT-06 — File too large
 * STC: UT-08 — Path traversal
 */
class InputFileProxyHandlerTest : DescribeSpec({

    val registry = mockk<FileProxyRegistry>(relaxed = true)
    val dispatcher = mockk<ToolExecutionDispatcher>()
    val sessionId = UUID.randomUUID()
    val config = FileProxyConfig(maxSizeMb = 50)

    val handler = InputFileProxyHandlerImpl(registry, config, dispatcher, sessionId)

    beforeEach {
        clearMocks(registry, dispatcher)
        coEvery { registry.createEntry(any()) } answers { firstArg() }
        coEvery { registry.updateStatus(any(), any(), any()) } just Runs
        coEvery { registry.deleteEntry(any()) } just Runs
    }

    describe("processInputProxy") {
        // STC: UT-05 — File not found
        it("throws FileNotFoundException for non-existent file") {
            val tempDir = Files.createTempDirectory("handler-test")
            val nonExistentPath = "${tempDir}/nonexistent_xyz_99999.pdf"
            shouldThrow<FileNotFoundException> {
                handler.processInputProxy(
                    "convert_pdf", "server1",
                    nonExistentPath, "content", emptyMap()
                )
            }
            Files.deleteIfExists(tempDir)
        }

        // STC: UT-08 — Path traversal
        it("throws InvalidFilePathException for path traversal") {
            val tempDir = Files.createTempDirectory("handler-traversal")
            shouldThrow<InvalidFilePathException> {
                handler.processInputProxy(
                    "convert_pdf", "server1",
                    "${tempDir}/../../../etc/passwd", "content", emptyMap()
                )
            }
            Files.deleteIfExists(tempDir)
        }

        it("resolves relative path and throws FileNotFoundException") {
            shouldThrow<FileNotFoundException> {
                handler.processInputProxy(
                    "convert_pdf", "server1",
                    "relative/file.pdf", "content", emptyMap()
                )
            }
        }

        // STC: UT-06 — File too large
        it("throws FileTooLargeException when file exceeds max size") {
            val smallConfig = FileProxyConfig(maxSizeMb = 1) // 1MB limit
            val smallHandler = InputFileProxyHandlerImpl(registry, smallConfig, dispatcher, sessionId)

            // Create a file larger than 1MB
            val tempFile = Files.createTempFile("large-test", ".bin")
            try {
                val bytes = ByteArray(2 * 1024 * 1024) // 2MB
                Files.write(tempFile, bytes)

                shouldThrow<FileTooLargeException> {
                    smallHandler.processInputProxy(
                        "tool", "server", tempFile.toString(), "content", emptyMap()
                    )
                }
            } finally {
                Files.deleteIfExists(tempFile)
            }
        }

        it("reads file, encodes to base64, and calls upstream") {
            val tempFile = Files.createTempFile("test-input", ".txt")
            val content = "Hello, World!"
            Files.writeString(tempFile, content)

            val expectedBase64 = Base64.getEncoder().encodeToString(content.toByteArray())
            val upstreamResponse = ExecuteToolResponse(
                content = listOf(ExecutionContentItem(text = "Converted successfully"))
            )

            coEvery { dispatcher.execute(any(), any()) } returns upstreamResponse

            try {
                val response = handler.processInputProxy(
                    "convert_pdf", "server1",
                    tempFile.toString(), "content",
                    mapOf("format" to "text")
                )

                response.content[0].text shouldBe "Converted successfully"

                // Verify upstream was called with base64 content
                coVerify {
                    dispatcher.execute("convert_pdf", match { args ->
                        args["content"]?.jsonPrimitive?.content == expectedBase64
                    })
                }

                // Verify registry lifecycle
                coVerify(ordering = Ordering.ORDERED) {
                    registry.createEntry(any())
                    registry.updateStatus(any(), FileProxyStatus.PROCESSED, any())
                    registry.deleteEntry(any())
                }
            } finally {
                Files.deleteIfExists(tempFile)
            }
        }
    }
})
