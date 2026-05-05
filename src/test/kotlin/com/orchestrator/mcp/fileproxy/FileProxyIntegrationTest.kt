package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.execution.model.ExecutionContentItem
import com.orchestrator.mcp.fileproxy.model.FileProxyEntry
import com.orchestrator.mcp.fileproxy.model.FileProxyStatus
import com.orchestrator.mcp.model.ToolEntry
import com.orchestrator.mcp.registry.ToolRegistry
import com.orchestrator.mcp.registry.ToolRegistryImpl
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.util.UUID

/**
 * Integration tests for the full FileProxy flow:
 * startup → detect → wrap → execute proxy call.
 *
 * These tests verify that components are wired together correctly,
 * not just that individual units work in isolation.
 */
class FileProxyIntegrationTest : DescribeSpec({

    describe("Full flow: detect → wrap → execute (STDIO input proxy)") {

        it("detects base64 param, generates wrapper, and proxies file_path call") {
            // --- Setup real components (not mocks) ---
            val toolRegistry = ToolRegistryImpl()
            val detector = FileProxyDetector()
            val wrapperGenerator = WrapperToolGenerator(toolRegistry)
            val registry = mockk<FileProxyRegistry>(relaxed = true)
            val config = FileProxyConfig(enabled = true, maxSizeMb = 50)
            val sessionId = UUID.randomUUID()

            // Mock execution dispatcher — simulates upstream tool call
            val executionDispatcher = mockk<ToolExecutionDispatcher>()
            coEvery { executionDispatcher.execute(any(), any()) } returns ExecuteToolResponse(
                content = listOf(ExecutionContentItem(type = "text", text = """{"result": "converted"}"""))
            )

            val inputHandler = InputFileProxyHandlerImpl(registry, config, executionDispatcher, sessionId)
            val outputHandler = OutputFileProxyHandlerImpl(registry, config)
            val uploadHandler = FileUploadHandler(registry, config, sessionId)

            val fileProxyService = FileProxyServiceImpl(
                detector, wrapperGenerator, inputHandler, outputHandler,
                registry, config, toolRegistry, uploadHandler, executionDispatcher
            )

            // --- Step 1: Register upstream tool with base64 param ---
            val upstreamTool = ToolEntry(
                name = "convert_pdf",
                description = "Convert PDF to text",
                inputSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("content") {
                            put("type", JsonPrimitive("string"))
                            put("contentEncoding", JsonPrimitive("base64"))
                            put("description", JsonPrimitive("Base64-encoded PDF"))
                        }
                        putJsonObject("format") {
                            put("type", JsonPrimitive("string"))
                        }
                    }
                    putJsonArray("required") { add(JsonPrimitive("content")) }
                },
                serverName = "pdf-tools"
            )
            toolRegistry.registerTool(upstreamTool)

            // --- Step 2: Initialize FileProxy (detect + wrap) ---
            fileProxyService.initialize(sessionId)

            // --- Verify: wrapper created, original hidden ---
            fileProxyService.isProxyTool("convert_pdf") shouldBe true

            val wrapperTool = toolRegistry.lookupTool("convert_pdf")!!
            val props = wrapperTool.inputSchema!!["properties"]!!.jsonObject
            props.containsKey("file_path") shouldBe true
            props.containsKey("content") shouldBe false  // base64 param replaced
            props.containsKey("format") shouldBe true    // non-file param preserved

            // --- Step 3: Execute proxy call with file_path ---
            val tempFile = Files.createTempFile("test-proxy-", ".pdf")
            Files.write(tempFile, "fake PDF content".toByteArray())

            try {
                val args = buildJsonObject {
                    put("file_path", JsonPrimitive(tempFile.toAbsolutePath().toString()))
                    put("format", JsonPrimitive("markdown"))
                }

                val response = fileProxyService.handleProxyCall(
                    "convert_pdf", "pdf-tools", args, "stdio"
                )

                // --- Verify: upstream was called with base64 content ---
                coVerify {
                    executionDispatcher.execute("convert_pdf", match { jsonObj ->
                        val content = jsonObj!!["content"]?.jsonPrimitive?.content ?: ""
                        content.isNotEmpty() // base64 encoded content
                    })
                }

                response.content[0].text shouldContain "converted"
            } finally {
                Files.deleteIfExists(tempFile)
            }
        }
    }

    describe("Full flow: detect → wrap → execute (output proxy)") {

        it("detects output tool, adds output_path, and saves file") {
            val toolRegistry = ToolRegistryImpl()
            val detector = FileProxyDetector()
            val wrapperGenerator = WrapperToolGenerator(toolRegistry)
            val registry = mockk<FileProxyRegistry>(relaxed = true)
            val config = FileProxyConfig(enabled = true)
            val sessionId = UUID.randomUUID()

            // Create a temp source file that "upstream" would produce
            val sourceFile = Files.createTempFile("upstream-output-", ".pdf")
            Files.write(sourceFile, "generated PDF content".toByteArray())

            val executionDispatcher = mockk<ToolExecutionDispatcher>()
            coEvery { executionDispatcher.execute(any(), any()) } returns ExecuteToolResponse(
                content = listOf(ExecutionContentItem(
                    type = "text",
                    text = buildJsonObject {
                        putJsonArray("artifacts") {
                            addJsonObject {
                                put("path", JsonPrimitive(sourceFile.toAbsolutePath().toString()))
                            }
                        }
                    }.toString()
                ))
            )

            val inputHandler = InputFileProxyHandlerImpl(registry, config, executionDispatcher, sessionId)
            val outputHandler = OutputFileProxyHandlerImpl(registry, config)
            val uploadHandler = FileUploadHandler(registry, config, sessionId)

            val fileProxyService = FileProxyServiceImpl(
                detector, wrapperGenerator, inputHandler, outputHandler,
                registry, config, toolRegistry, uploadHandler, executionDispatcher
            )

            // Register tool with output schema indicator
            val upstreamTool = ToolEntry(
                name = "export_report",
                description = "Export report to PDF",
                inputSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("report_id") { put("type", JsonPrimitive("string")) }
                    }
                },
                serverName = "report-server"
            )
            toolRegistry.registerTool(upstreamTool)

            // Manually add output detection (since static detection needs outputSchema)
            val outputDetection = com.orchestrator.mcp.fileproxy.model.DetectionResult(
                "export_report", "report-server", "response",
                com.orchestrator.mcp.fileproxy.model.ProxyDirection.OUTPUT,
                com.orchestrator.mcp.fileproxy.model.DetectionMethod.SCHEMA_TYPE, 0.8f
            )
            wrapperGenerator.generateWrapper(upstreamTool, listOf(outputDetection), "stdio")

            // Execute with output_path
            val outputDir = Files.createTempDirectory("proxy-output-")
            val outputPath = outputDir.resolve("result.pdf")

            try {
                val args = buildJsonObject {
                    put("report_id", JsonPrimitive("RPT-001"))
                    put("output_path", JsonPrimitive(outputPath.toAbsolutePath().toString()))
                }

                val response = fileProxyService.handleProxyCall(
                    "export_report", "report-server", args, "stdio"
                )

                // Verify file was saved
                Files.exists(outputPath) shouldBe true
                String(Files.readAllBytes(outputPath)) shouldBe "generated PDF content"

                // Verify response contains save confirmation
                response.content[0].text shouldContain "saved_to"
                response.content[0].text shouldContain "bytes_written"
            } finally {
                Files.deleteIfExists(outputPath)
                Files.deleteIfExists(outputDir)
                Files.deleteIfExists(sourceFile)
            }
        }
    }

    describe("Feature disabled — no detection, no wrappers") {

        it("skips initialization when file-proxy.enabled = false") {
            val toolRegistry = ToolRegistryImpl()
            val detector = FileProxyDetector()
            val wrapperGenerator = WrapperToolGenerator(toolRegistry)
            val registry = mockk<FileProxyRegistry>(relaxed = true)
            val config = FileProxyConfig(enabled = false)  // DISABLED
            val sessionId = UUID.randomUUID()
            val executionDispatcher = mockk<ToolExecutionDispatcher>()
            val inputHandler = mockk<InputFileProxyHandler>()
            val outputHandler = mockk<OutputFileProxyHandler>()
            val uploadHandler = FileUploadHandler(registry, config, sessionId)

            val fileProxyService = FileProxyServiceImpl(
                detector, wrapperGenerator, inputHandler, outputHandler,
                registry, config, toolRegistry, uploadHandler, executionDispatcher
            )

            // Register a tool that would normally be detected
            toolRegistry.registerTool(ToolEntry(
                name = "convert_pdf",
                description = "Convert PDF",
                inputSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("content") {
                            put("contentEncoding", JsonPrimitive("base64"))
                        }
                    }
                },
                serverName = "pdf-tools"
            ))

            fileProxyService.initialize(sessionId)

            // No wrapper should be created
            fileProxyService.isProxyTool("convert_pdf") shouldBe false
        }
    }

    describe("No file params — no wrappers created") {

        it("leaves tools unchanged when no file parameters detected") {
            val toolRegistry = ToolRegistryImpl()
            val detector = FileProxyDetector()
            val wrapperGenerator = WrapperToolGenerator(toolRegistry)
            val registry = mockk<FileProxyRegistry>(relaxed = true)
            val config = FileProxyConfig(enabled = true)
            val sessionId = UUID.randomUUID()
            val executionDispatcher = mockk<ToolExecutionDispatcher>()
            val inputHandler = mockk<InputFileProxyHandler>()
            val outputHandler = mockk<OutputFileProxyHandler>()
            val uploadHandler = FileUploadHandler(registry, config, sessionId)

            val fileProxyService = FileProxyServiceImpl(
                detector, wrapperGenerator, inputHandler, outputHandler,
                registry, config, toolRegistry, uploadHandler, executionDispatcher
            )

            // Register tools with NO file parameters
            toolRegistry.registerTool(ToolEntry(
                name = "search_issues",
                description = "Search Jira issues",
                inputSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("query") {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("JQL query"))
                        }
                    }
                },
                serverName = "jira"
            ))
            toolRegistry.registerTool(ToolEntry(
                name = "get_weather",
                description = "Get weather",
                inputSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("city") {
                            put("type", JsonPrimitive("string"))
                        }
                    }
                },
                serverName = "weather"
            ))

            fileProxyService.initialize(sessionId)

            // No wrappers
            fileProxyService.isProxyTool("search_issues") shouldBe false
            fileProxyService.isProxyTool("get_weather") shouldBe false
        }
    }

    describe("Error handling — file not found") {

        it("throws FileNotFoundException for non-existent file_path") {
            val toolRegistry = ToolRegistryImpl()
            val detector = FileProxyDetector()
            val wrapperGenerator = WrapperToolGenerator(toolRegistry)
            val registry = mockk<FileProxyRegistry>(relaxed = true)
            val config = FileProxyConfig(enabled = true)
            val sessionId = UUID.randomUUID()
            val executionDispatcher = mockk<ToolExecutionDispatcher>()
            val inputHandler = InputFileProxyHandlerImpl(registry, config, executionDispatcher, sessionId)
            val outputHandler = OutputFileProxyHandlerImpl(registry, config)
            val uploadHandler = FileUploadHandler(registry, config, sessionId)

            val fileProxyService = FileProxyServiceImpl(
                detector, wrapperGenerator, inputHandler, outputHandler,
                registry, config, toolRegistry, uploadHandler, executionDispatcher
            )

            // Register and detect tool
            toolRegistry.registerTool(ToolEntry(
                name = "convert_pdf", description = "Convert",
                inputSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("content") {
                            put("contentEncoding", JsonPrimitive("base64"))
                        }
                    }
                },
                serverName = "pdf-tools"
            ))
            fileProxyService.initialize(sessionId)

            val args = buildJsonObject {
                put("file_path", JsonPrimitive("C:\\nonexistent\\path\\file.pdf"))
            }

            val exception = runCatching {
                fileProxyService.handleProxyCall("convert_pdf", "pdf-tools", args, "stdio")
            }.exceptionOrNull()

            exception.shouldNotBeNull()
            (exception is com.orchestrator.mcp.model.McpOrchestratorException) shouldBe true
        }
    }
})
