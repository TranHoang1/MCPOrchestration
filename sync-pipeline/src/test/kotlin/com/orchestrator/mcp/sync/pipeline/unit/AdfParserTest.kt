package com.orchestrator.mcp.sync.pipeline.unit

import com.orchestrator.mcp.sync.pipeline.crawl.AdfParser
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*

// STC: UT-002, UT-003 — AdfParser plain text extraction
class AdfParserTest : FunSpec({

    val parser = AdfParser()

    test("simple paragraph extracts plain text") {
        val adf = buildJsonObject {
            put("type", "doc")
            putJsonArray("content") {
                addJsonObject {
                    put("type", "paragraph")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", "Hello World")
                        }
                    }
                }
            }
        }
        parser.toPlainText(adf) shouldBe "Hello World"
    }

    test("multiple paragraphs joined by newline") {
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
        parser.toPlainText(adf) shouldBe "Line 1\nLine 2"
    }

    test("null input returns empty string") {
        parser.toPlainText(null) shouldBe ""
    }

    test("JsonNull returns empty string") {
        parser.toPlainText(JsonNull) shouldBe ""
    }

    test("empty doc returns empty string") {
        val adf = buildJsonObject {
            put("type", "doc")
            putJsonArray("content") {}
        }
        parser.toPlainText(adf) shouldBe ""
    }

    test("nested list extracts text content") {
        val adf = buildJsonObject {
            put("type", "doc")
            putJsonArray("content") {
                addJsonObject {
                    put("type", "bulletList")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "listItem")
                            putJsonArray("content") {
                                addJsonObject {
                                    put("type", "paragraph")
                                    putJsonArray("content") {
                                        addJsonObject {
                                            put("type", "text")
                                            put("text", "Item 1")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val result = parser.toPlainText(adf)
        result.contains("Item 1") shouldBe true
    }

    test("code block extracts text") {
        val adf = buildJsonObject {
            put("type", "doc")
            putJsonArray("content") {
                addJsonObject {
                    put("type", "codeBlock")
                    putJsonArray("content") {
                        addJsonObject { put("type", "text"); put("text", "val x = 1") }
                    }
                }
            }
        }
        parser.toPlainText(adf).contains("val x = 1") shouldBe true
    }

    test("mention node extracts @username") {
        val adf = buildJsonObject {
            put("type", "doc")
            putJsonArray("content") {
                addJsonObject {
                    put("type", "paragraph")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "mention")
                            putJsonObject("attrs") { put("text", "john") }
                        }
                    }
                }
            }
        }
        parser.toPlainText(adf).contains("@john") shouldBe true
    }
})
