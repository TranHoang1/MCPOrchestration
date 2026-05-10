package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.fileproxy.model.DetectionMethod
import com.orchestrator.mcp.fileproxy.model.DetectionResult
import com.orchestrator.mcp.fileproxy.model.ProxyDirection
import com.orchestrator.mcp.core.model.ToolEntry
import com.orchestrator.mcp.registry.ToolRegistry
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.*

/**
 * Unit tests for WrapperToolGenerator.
 * STC: UT-02 — Wrapper generation with file_path replacement
 */
class WrapperToolGeneratorTest : DescribeSpec({

    val toolRegistry = mockk<ToolRegistry>(relaxed = true)
    val generator = WrapperToolGenerator(toolRegistry)

    describe("generateWrapper") {
        it("generates STDIO wrapper replacing base64 param with file_path") {
            val originalTool = ToolEntry(
                name = "convert_pdf",
                description = "Convert PDF to text",
                inputSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("content") {
                            put("type", JsonPrimitive("string"))
                            put("contentEncoding", JsonPrimitive("base64"))
                        }
                        putJsonObject("format") {
                            put("type", JsonPrimitive("string"))
                        }
                    }
                    putJsonArray("required") { add(JsonPrimitive("content")) }
                },
                serverName = "pdf-tools"
            )

            val detections = listOf(
                DetectionResult("convert_pdf", "pdf-tools", "content", ProxyDirection.INPUT, DetectionMethod.SCHEMA_TYPE, 0.95f)
            )

            val wrapper = generator.generateWrapper(originalTool, detections, "stdio")
            wrapper.shouldNotBeNull()
            wrapper.name shouldBe "convert_pdf"
            wrapper.description shouldContain "file_path"
            wrapper.description shouldContain "automatically"

            // Verify schema has file_path instead of content
            val props = wrapper.inputSchema!!["properties"]!!.jsonObject
            props.containsKey("file_path") shouldBe true
            props.containsKey("content") shouldBe false
            props.containsKey("format") shouldBe true

            verify { toolRegistry.registerTool(wrapper) }
        }

        it("generates HTTP wrapper replacing base64 param with file_id") {
            val originalTool = ToolEntry(
                name = "analyze_image",
                description = "Analyze image content",
                inputSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("image") {
                            put("type", JsonPrimitive("string"))
                        }
                    }
                },
                serverName = "image-server"
            )

            val detections = listOf(
                DetectionResult("analyze_image", "image-server", "image", ProxyDirection.INPUT, DetectionMethod.NAME_PATTERN, 0.9f)
            )

            val wrapper = generator.generateWrapper(originalTool, detections, "http")
            wrapper.shouldNotBeNull()
            val props = wrapper.inputSchema!!["properties"]!!.jsonObject
            props.containsKey("file_id") shouldBe true
            props.containsKey("image") shouldBe false
        }

        it("adds output_path param when output detection present") {
            val originalTool = ToolEntry(
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

            val detections = listOf(
                DetectionResult("export_report", "report-server", "response", ProxyDirection.OUTPUT, DetectionMethod.SCHEMA_TYPE, 0.8f)
            )

            val wrapper = generator.generateWrapper(originalTool, detections, "stdio")
            wrapper.shouldNotBeNull()
            val props = wrapper.inputSchema!!["properties"]!!.jsonObject
            props.containsKey("output_path") shouldBe true
            props.containsKey("report_id") shouldBe true
        }

        it("returns null for empty detection results") {
            val tool = ToolEntry("tool", "desc", null, "server")
            val wrapper = generator.generateWrapper(tool, emptyList(), "stdio")
            wrapper.shouldBeNull()
        }
    }

    describe("hasWrapper / getOriginalTool") {
        it("tracks wrapper mappings") {
            val tool = ToolEntry(
                name = "my_tool", description = "desc",
                inputSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("data") { put("type", JsonPrimitive("string")) }
                    }
                },
                serverName = "srv"
            )
            val detections = listOf(
                DetectionResult("my_tool", "srv", "data", ProxyDirection.INPUT, DetectionMethod.NAME_PATTERN, 0.9f)
            )

            generator.generateWrapper(tool, detections, "stdio")
            generator.hasWrapper("my_tool") shouldBe true
            generator.getOriginalTool("my_tool").shouldNotBeNull()
            generator.hasWrapper("unknown") shouldBe false
        }
    }
})
