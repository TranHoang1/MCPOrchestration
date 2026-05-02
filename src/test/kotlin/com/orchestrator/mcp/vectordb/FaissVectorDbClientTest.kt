package com.orchestrator.mcp.vectordb

import com.orchestrator.mcp.vectordb.model.VectorPoint
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Unit tests for FaissVectorDbClient (local fallback).
 */
class FaissVectorDbClientTest : FunSpec({

    val testDir = System.getProperty("java.io.tmpdir") + "/faiss-test-${System.nanoTime()}"

    afterSpec {
        File(testDir).deleteRecursively()
    }

    fun createClient(): FaissVectorDbClient = FaissVectorDbClient(testDir)

    fun makeVector(seed: Float, dims: Int = 768): FloatArray {
        return FloatArray(dims) { seed + it * 0.001f }
    }

    test("createCollection succeeds without error") {
        val client = createClient()
        client.createCollection("test", 768)
    }

    test("upsert and search returns matching results") {
        val client = createClient()
        client.createCollection("test", 768)

        val points = listOf(
            VectorPoint("id1", makeVector(0.1f), mapOf("name" to "tool_a", "server_name" to "s1")),
            VectorPoint("id2", makeVector(0.2f), mapOf("name" to "tool_b", "server_name" to "s1")),
            VectorPoint("id3", makeVector(0.9f), mapOf("name" to "tool_c", "server_name" to "s2"))
        )
        client.upsert("test", points)

        val queryVec = makeVector(0.1f)
        val results = client.search("test", queryVec, limit = 2, scoreThreshold = 0.0f)

        results shouldHaveSize 2
        results[0].score shouldBeGreaterThan results[1].score
    }

    test("delete removes matching points") {
        val client = createClient()
        val points = listOf(
            VectorPoint("id1", makeVector(0.1f), mapOf("name" to "tool_a", "server_name" to "s1")),
            VectorPoint("id2", makeVector(0.2f), mapOf("name" to "tool_b", "server_name" to "s2"))
        )
        client.upsert("test", points)

        client.delete("test", mapOf("server_name" to "s1"))

        val results = client.search("test", makeVector(0.1f), limit = 10, scoreThreshold = 0.0f)
        results shouldHaveSize 1
        results[0].payload["server_name"] shouldBe "s2"
    }

    test("isHealthy always returns true") {
        val client = createClient()
        client.isHealthy() shouldBe true
    }

    test("search with high threshold returns fewer results") {
        val client = createClient()
        val points = listOf(
            VectorPoint("id1", makeVector(0.1f), mapOf("name" to "tool_a")),
            VectorPoint("id2", makeVector(0.5f), mapOf("name" to "tool_b"))
        )
        client.upsert("test", points)

        val results = client.search("test", makeVector(0.1f), limit = 10, scoreThreshold = 0.99f)
        // Only the very similar vector should match
        results.size shouldBe 1
    }

    test("persist and load from disk") {
        val client1 = createClient()
        val points = listOf(
            VectorPoint("id1", makeVector(0.1f), mapOf("name" to "tool_a"))
        )
        client1.upsert("test", points)

        // New client loads from disk
        val client2 = FaissVectorDbClient(testDir)
        client2.loadFromDisk()
        val results = client2.search("test", makeVector(0.1f), limit = 5, scoreThreshold = 0.0f)
        results shouldHaveSize 1
        results[0].payload["name"] shouldBe "tool_a"
    }
})
