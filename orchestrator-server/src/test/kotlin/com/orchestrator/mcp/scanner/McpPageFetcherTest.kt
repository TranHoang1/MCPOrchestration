package com.orchestrator.mcp.scanner

import com.orchestrator.mcp.client.upstream.McpConnection
import com.orchestrator.mcp.client.upstream.UpstreamServerManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*

class McpPageFetcherTest : DescribeSpec({

    val serverManager = mockk<UpstreamServerManager>()
    val connection = mockk<McpConnection>()

    beforeEach { clearMocks(serverManager, connection) }

    fun createFetcher() = McpPageFetcher(serverManager)

    describe("fetchPage") {

        it("calls jira_search tool and parses response") {
            val mcpResponse = buildJsonObject {
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put("text", buildJsonObject {
                            put("total", 2)
                            putJsonArray("issues") {
                                addJsonObject {
                                    put("id", "10001")
                                    put("key", "MTO-1")
                                    put("self", "https://jira/rest/api/3/issue/10001")
                                    putJsonObject("fields") {
                                        put("summary", "First issue")
                                        putJsonObject("status") { put("name", "To Do") }
                                        putJsonObject("issuetype") { put("name", "Story") }
                                    }
                                }
                                addJsonObject {
                                    put("id", "10002")
                                    put("key", "MTO-2")
                                    put("self", "https://jira/rest/api/3/issue/10002")
                                    putJsonObject("fields") {
                                        put("summary", "Second issue")
                                        putJsonObject("status") { put("name", "Done") }
                                        putJsonObject("issuetype") { put("name", "Bug") }
                                    }
                                }
                            }
                        }.toString())
                    }
                }
            }

            every { serverManager.getConnection("atlassian") } returns connection
            coEvery { connection.sendRequest("tools/call", any()) } returns mcpResponse

            val result = createFetcher().fetchPage("project = MTO", 0, 50)

            result.total shouldBe 2
            result.startAt shouldBe 0
            result.maxResults shouldBe 50
            result.issues.size shouldBe 2
            result.issues[0].key shouldBe "MTO-1"
            result.issues[1].key shouldBe "MTO-2"

            coVerify {
                connection.sendRequest("tools/call", match { params ->
                    params["name"]?.jsonPrimitive?.content == "jira_search" &&
                        params["arguments"]?.jsonObject?.get("jql")?.jsonPrimitive?.content == "project = MTO"
                })
            }
        }

        it("throws McpPageFetchException when no connection") {
            every { serverManager.getConnection("atlassian") } returns null

            shouldThrow<McpPageFetchException> {
                createFetcher().fetchPage("project = MTO", 0, 50)
            }.message shouldBe "No connection to upstream server 'atlassian'"
        }

        it("throws McpPageFetchException when response has no content") {
            val emptyResponse = buildJsonObject { }

            every { serverManager.getConnection("atlassian") } returns connection
            coEvery { connection.sendRequest("tools/call", any()) } returns emptyResponse

            shouldThrow<McpPageFetchException> {
                createFetcher().fetchPage("project = MTO", 0, 50)
            }.message shouldBe "MCP response missing 'content' array"
        }

        it("handles empty issues array") {
            val mcpResponse = buildJsonObject {
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put("text", buildJsonObject {
                            put("total", 0)
                            putJsonArray("issues") { }
                        }.toString())
                    }
                }
            }

            every { serverManager.getConnection("atlassian") } returns connection
            coEvery { connection.sendRequest("tools/call", any()) } returns mcpResponse

            val result = createFetcher().fetchPage("project = MTO", 10, 25)

            result.total shouldBe 0
            result.startAt shouldBe 10
            result.maxResults shouldBe 25
            result.issues shouldBe emptyList()
        }

        it("passes startAt and limit correctly in tool arguments") {
            val mcpResponse = buildJsonObject {
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put("text", buildJsonObject {
                            put("total", 100)
                            putJsonArray("issues") { }
                        }.toString())
                    }
                }
            }

            every { serverManager.getConnection("atlassian") } returns connection
            coEvery { connection.sendRequest("tools/call", any()) } returns mcpResponse

            createFetcher().fetchPage("project = MTO ORDER BY updated", 50, 25)

            coVerify {
                connection.sendRequest("tools/call", match { params ->
                    val args = params["arguments"]?.jsonObject
                    args?.get("start_at")?.jsonPrimitive?.int == 50 &&
                        args?.get("limit")?.jsonPrimitive?.int == 25
                })
            }
        }
    }
})
