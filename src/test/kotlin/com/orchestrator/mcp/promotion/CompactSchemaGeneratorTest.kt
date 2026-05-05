package com.orchestrator.mcp.promotion

import com.orchestrator.mcp.model.ToolEntry
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveMaxLength
import kotlinx.serialization.json.*

class CompactSchemaGeneratorTest : DescribeSpec({

    describe("generate") {
        it("should truncate long descriptions to 100 chars") {
            val longDesc = "A".repeat(200)
            val tool = ToolEntry(
                name = "test_tool",
                description = longDesc,
                inputSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {})
                    put("required", buildJsonArray {})
                },
                serverName = "test-server"
            )
            val (compactDesc, _) = CompactSchemaGenerator.generate(tool)
            compactDesc shouldHaveMaxLength 100
            compactDesc.endsWith("...") shouldBe true
        }

        it("should keep short descriptions unchanged") {
            val tool = ToolEntry(
                name = "test_tool",
                description = "Short description",
                inputSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {})
                    put("required", buildJsonArray {})
                },
                serverName = "test-server"
            )
            val (compactDesc, _) = CompactSchemaGenerator.generate(tool)
            compactDesc shouldBe "Short description"
        }

        it("should strip optional parameters from schema") {
            val schema = buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", buildJsonObject {
                    put("required_param", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                    })
                    put("optional_param", buildJsonObject {
                        put("type", JsonPrimitive("integer"))
                    })
                })
                put("required", buildJsonArray {
                    add(JsonPrimitive("required_param"))
                })
            }
            val tool = ToolEntry(
                name = "test_tool",
                description = "Test",
                inputSchema = schema,
                serverName = "test-server"
            )
            val (_, compactSchema) = CompactSchemaGenerator.generate(tool)
            val props = compactSchema["properties"]?.jsonObject
            props?.containsKey("required_param") shouldBe true
            props?.containsKey("optional_param") shouldBe false
        }
    }
})
