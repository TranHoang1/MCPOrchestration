package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.fileproxy.model.DetectionMethod
import com.orchestrator.mcp.fileproxy.model.ProxyDirection
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*

/**
 * Unit tests for FileProxyDetector — schema-based detection heuristics.
 * STC: UT-01 — Input file parameter auto-detection (schema type)
 * STC: UT-03 — Multiple file parameters in same tool
 */
class FileProxyDetectorTest : DescribeSpec({

    val detector = FileProxyDetector()

    describe("detectInputFileParams") {
        // STC: UT-01 — Schema type detection (contentEncoding: base64)
        it("detects parameter with contentEncoding base64") {
            val schema = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("content") {
                        put("type", JsonPrimitive("string"))
                        put("contentEncoding", JsonPrimitive("base64"))
                        put("description", JsonPrimitive("PDF file content"))
                    }
                }
            }

            val results = detector.detectInputFileParams("convert_pdf", "pdf-tools", schema)
            results shouldHaveSize 1
            results[0].paramName shouldBe "content"
            results[0].method shouldBe DetectionMethod.SCHEMA_TYPE
            results[0].confidence shouldBe 0.95f
            results[0].direction shouldBe ProxyDirection.INPUT
        }

        // STC: UT-01 — Name pattern detection
        it("detects parameter by name pattern (file_content, base64, etc)") {
            val schema = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("file_content") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("The file data"))
                    }
                }
            }

            val results = detector.detectInputFileParams("process_file", "server1", schema)
            results shouldHaveSize 1
            results[0].paramName shouldBe "file_content"
            results[0].method shouldBe DetectionMethod.NAME_PATTERN
        }

        // STC: UT-01 — Description keyword detection
        it("detects parameter by description keywords") {
            val schema = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("payload") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Base64 encoded file content to process"))
                    }
                }
            }

            val results = detector.detectInputFileParams("analyze", "server1", schema)
            results shouldHaveSize 1
            results[0].paramName shouldBe "payload"
            results[0].method shouldBe DetectionMethod.DESCRIPTION_KEYWORD
        }

        // STC: UT-03 — Multiple file parameters
        it("detects multiple file parameters in same tool") {
            val schema = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("file_content") {
                        put("type", JsonPrimitive("string"))
                    }
                    putJsonObject("image_data") {
                        put("type", JsonPrimitive("string"))
                    }
                    putJsonObject("format") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Output format"))
                    }
                }
            }

            val results = detector.detectInputFileParams("merge_docs", "server1", schema)
            results shouldHaveSize 2
        }

        it("returns empty for tool without file parameters") {
            val schema = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Search query text"))
                    }
                }
            }

            val results = detector.detectInputFileParams("search", "server1", schema)
            results.shouldBeEmpty()
        }
    }

    describe("detectOutputFromResponse") {
        it("detects artifacts path in response") {
            val response = buildJsonObject {
                putJsonArray("artifacts") {
                    addJsonObject { put("path", JsonPrimitive("/tmp/output.pdf")) }
                }
            }
            detector.detectOutputFromResponse("export_report", response) shouldBe true
        }

        it("returns false for text-only response") {
            val response = buildJsonObject {
                put("result", JsonPrimitive("success"))
            }
            detector.detectOutputFromResponse("simple_tool", response) shouldBe false
        }
    }
})
