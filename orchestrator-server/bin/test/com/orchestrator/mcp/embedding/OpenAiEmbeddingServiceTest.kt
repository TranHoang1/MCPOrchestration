package com.orchestrator.mcp.embedding

import com.orchestrator.mcp.client.embedding.OpenAiEmbeddingService
import com.orchestrator.mcp.core.model.EmbeddingServiceException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class OpenAiEmbeddingServiceTest : FunSpec({

    fun createMockClient(responseBody: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    respond(
                        content = responseBody,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
    }

    fun mockEmbeddingResponse(dimensions: Int = 768): String {
        val embedding = (0 until dimensions).joinToString(",") { "0.01" }
        return """{"object":"list","data":[{"object":"embedding","index":0,"embedding":[$embedding]}],"model":"text-embedding-3-small","usage":{"prompt_tokens":5,"total_tokens":5}}"""
    }

    fun mockBatchEmbeddingResponse(count: Int, dimensions: Int = 768): String {
        val embedding = (0 until dimensions).joinToString(",") { "0.01" }
        val data = (0 until count).joinToString(",") { i ->
            """{"object":"embedding","index":$i,"embedding":[$embedding]}"""
        }
        return """{"object":"list","data":[$data],"model":"text-embedding-3-small","usage":{"prompt_tokens":10,"total_tokens":10}}"""
    }

    // STC: UT-022 — generateEmbedding returns correct dimension vector
    test("UT-022: generateEmbedding returns correct dimension vector") {
        val client = createMockClient(mockEmbeddingResponse(768))
        val service = OpenAiEmbeddingService(client, "test-key", dimensions = 768)

        val result = service.generateEmbedding("read log files")

        result.size shouldBe 768
    }

    // STC: UT-023 — generateEmbeddings batch processing
    test("UT-023: generateEmbeddings batch processing") {
        val client = createMockClient(mockBatchEmbeddingResponse(3, 768))
        val service = OpenAiEmbeddingService(client, "test-key", dimensions = 768)

        val results = service.generateEmbeddings(listOf("tool1 desc", "tool2 desc", "tool3 desc"))

        results.size shouldBe 3
        results.forEach { it.size shouldBe 768 }
    }

    test("generateEmbedding throws EmbeddingServiceException on error") {
        val client = createMockClient("""{"error":{"message":"Invalid API key"}}""", HttpStatusCode.Unauthorized)
        val service = OpenAiEmbeddingService(client, "bad-key", dimensions = 768)

        shouldThrow<EmbeddingServiceException> {
            service.generateEmbedding("test")
        }
    }
})
