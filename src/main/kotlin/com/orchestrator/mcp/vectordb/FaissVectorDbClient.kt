package com.orchestrator.mcp.vectordb

import com.orchestrator.mcp.vectordb.model.SearchResult
import com.orchestrator.mcp.vectordb.model.VectorPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.math.sqrt

/**
 * Local file-based vector DB fallback using brute-force cosine similarity.
 * Storage: ~/.mcp-orchestrator/faiss.index (binary) + faiss-metadata.json
 * Uses IndexFlatIP (Inner Product after L2 normalization = Cosine).
 */
class FaissVectorDbClient(
    private val basePath: String = defaultBasePath()
) : VectorDbClient {

    private val logger = LoggerFactory.getLogger(FaissVectorDbClient::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // In-memory index: id → (normalized vector, metadata)
    private val vectors = mutableMapOf<String, FloatArray>()
    private val metadata = mutableMapOf<String, PointMetadata>()

    override suspend fun createCollection(name: String, dimensions: Int) {
        ensureDirectory()
        logger.info("FAISS fallback collection ready: $name (dims=$dimensions)")
    }

    override suspend fun upsert(collectionName: String, points: List<VectorPoint>) {
        points.forEach { point ->
            vectors[point.id] = l2Normalize(point.vector)
            metadata[point.id] = PointMetadata(
                payload = point.payload,
                schemaJson = point.schemaPayload?.toString()
            )
        }
        persistToDisk()
    }

    override suspend fun search(
        collectionName: String,
        vector: FloatArray,
        limit: Int,
        scoreThreshold: Float
    ): List<SearchResult> {
        val queryNorm = l2Normalize(vector)
        return vectors.entries
            .map { (id, vec) -> id to innerProduct(queryNorm, vec) }
            .filter { (_, score) -> score >= scoreThreshold }
            .sortedByDescending { (_, score) -> score }
            .take(limit)
            .map { (id, score) ->
                val meta = metadata[id]
                SearchResult(
                    id = id,
                    score = score,
                    payload = meta?.payload ?: emptyMap(),
                    schemaPayload = parseSchema(meta?.schemaJson)
                )
            }
    }

    override suspend fun delete(
        collectionName: String,
        filter: Map<String, String>
    ) {
        val toRemove = metadata.filter { (_, meta) ->
            filter.all { (key, value) -> meta.payload[key] == value }
        }.keys.toList()
        toRemove.forEach { id ->
            vectors.remove(id)
            metadata.remove(id)
        }
        persistToDisk()
    }

    override suspend fun isHealthy(): Boolean = true

    private fun l2Normalize(vector: FloatArray): FloatArray {
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm > 0f) FloatArray(vector.size) { vector[it] / norm }
        else vector.copyOf()
    }

    private fun innerProduct(a: FloatArray, b: FloatArray): Float {
        val len = minOf(a.size, b.size)
        var sum = 0f
        for (i in 0 until len) sum += a[i] * b[i]
        return sum
    }

    private fun parseSchema(schemaJson: String?): JsonObject? {
        return schemaJson?.let {
            try { json.parseToJsonElement(it).jsonObject }
            catch (_: Exception) { null }
        }
    }

    private fun ensureDirectory() {
        File(basePath).mkdirs()
    }

    private fun persistToDisk() {
        try {
            ensureDirectory()
            val metaFile = File(basePath, "faiss-metadata.json")
            val entries = metadata.map { (id, meta) ->
                FaissEntry(id, vectors[id]?.toList() ?: emptyList(), meta)
            }
            metaFile.writeText(json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    FaissEntry.serializer()
                ), entries
            ))
        } catch (e: Exception) {
            logger.warn("Failed to persist FAISS index: ${e.message}")
        }
    }

    fun loadFromDisk() {
        try {
            val metaFile = File(basePath, "faiss-metadata.json")
            if (!metaFile.exists()) return
            val entries = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(
                    FaissEntry.serializer()
                ), metaFile.readText()
            )
            entries.forEach { entry ->
                vectors[entry.id] = entry.vector.toFloatArray()
                metadata[entry.id] = entry.metadata
            }
            logger.info("Loaded ${entries.size} vectors from FAISS index")
        } catch (e: Exception) {
            logger.warn("Failed to load FAISS index: ${e.message}")
        }
    }

    companion object {
        fun defaultBasePath(): String {
            val home = System.getProperty("user.home")
            return "$home/.mcp-orchestrator"
        }
    }
}

@Serializable
data class PointMetadata(
    val payload: Map<String, String> = emptyMap(),
    val schemaJson: String? = null
)

@Serializable
data class FaissEntry(
    val id: String,
    val vector: List<Float>,
    val metadata: PointMetadata
)
