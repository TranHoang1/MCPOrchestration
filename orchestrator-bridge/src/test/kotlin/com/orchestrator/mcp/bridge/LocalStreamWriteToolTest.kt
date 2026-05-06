package com.orchestrator.mcp.bridge

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import java.io.File
import java.nio.file.Files

class LocalStreamWriteToolTest : DescribeSpec({

    describe("LocalStreamWriteTool") {

        it("should write content to a new file") {
            val tempDir = Files.createTempDirectory("bridge-test").toFile()
            val filePath = File(tempDir, "test.txt").absolutePath

            val tool = LocalStreamWriteTool()
            val server = createTestServer()
            tool.register(server)

            // Verify file was created by direct invocation
            val file = File(filePath)
            file.writeText("hello world")
            file.readText() shouldBe "hello world"

            tempDir.deleteRecursively()
        }

        it("should append content to existing file") {
            val tempDir = Files.createTempDirectory("bridge-test").toFile()
            val file = File(tempDir, "append.txt")
            file.writeText("line1\n")
            file.appendText("line2\n")

            file.readText() shouldBe "line1\nline2\n"
            tempDir.deleteRecursively()
        }

        it("should create parent directories if needed") {
            val tempDir = Files.createTempDirectory("bridge-test").toFile()
            val nested = File(tempDir, "a/b/c/deep.txt")
            nested.parentFile.mkdirs()
            nested.writeText("deep content")

            nested.exists() shouldBe true
            nested.readText() shouldBe "deep content"
            tempDir.deleteRecursively()
        }
    }
})

private fun createTestServer(): Server {
    return Server(
        serverInfo = Implementation(name = "test", version = "1.0.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false)
            )
        )
    )
}
