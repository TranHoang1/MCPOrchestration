package com.orchestrator.mcp.crawler

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*

class AdfParserTest : DescribeSpec({

    val parser = AdfParser()

    describe("toPlainText") {
        it("extracts text from simple paragraph") {
            val adf = buildJsonObject {
                put("type", "doc")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "paragraph")
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "text")
                                put("text", "Hello world")
                            }
                        }
                    }
                }
            }
            parser.toPlainText(adf) shouldBe "Hello world"
        }

        it("extracts text from multiple paragraphs") {
            val adf = buildJsonObject {
                put("type", "doc")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "paragraph")
                        putJsonArray("content") {
                            addJsonObject { put("type", "text"); put("text", "Line 1") }
                        }
                    }
                    addJsonObject {
                        put("type", "paragraph")
                        putJsonArray("content") {
                            addJsonObject { put("type", "text"); put("text", "Line 2") }
                        }
                    }
                }
            }
            val result = parser.toPlainText(adf)
            result shouldBe "Line 1Line 2"
        }

        it("returns empty string for null input") {
            parser.toPlainText(null) shouldBe ""
        }

        it("returns empty string for JsonNull") {
            parser.toPlainText(JsonNull) shouldBe ""
        }

        it("handles nested content with inline formatting") {
            val adf = buildJsonObject {
                put("type", "doc")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "paragraph")
                        putJsonArray("content") {
                            addJsonObject { put("type", "text"); put("text", "Bold ") }
                            addJsonObject { put("type", "text"); put("text", "text") }
                        }
                    }
                }
            }
            parser.toPlainText(adf) shouldBe "Bold text"
        }
    }
})
