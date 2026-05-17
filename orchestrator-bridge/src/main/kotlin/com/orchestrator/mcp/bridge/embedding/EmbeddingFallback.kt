package com.orchestrator.mcp.bridge.embedding

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

/**
 * Embedding fallback with priority chain:
 * 1. Local MCP "embed" tool → 2. Local ONNX model → 3. Download model → 4. Disable
 */
class EmbeddingFallback : EmbeddingProvider {
    private val logger = LoggerFactory.getLogger(EmbeddingFallback::class.java)
    private val initMutex = Mutex()
    private var initialized = false
    private var available = false
    private var mcpEmbedFn: (suspend (List<String>) -> List<FloatArray>)? = null

    /** Register an MCP "embed" tool function (highest priority). */
    fun setMcpEmbed(fn: (suspend (List<String>) -> List<FloatArray>)?) {
        mcpEmbedFn = fn
    }

    override fun isAvailable(): Boolean {
        if (mcpEmbedFn != null) return true
        return available
    }

    override fun dimensions(): Int = OnnxInference.DIMENSIONS

    override suspend fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()

        // Priority 1: MCP embed tool
        mcpEmbedFn?.let { fn ->
            try {
                return fn(texts)
            } catch (e: Exception) {
                logger.warn("MCP embed failed: {}, falling back to ONNX", e.message)
            }
        }

        // Lazy init ONNX on first call (thread-safe)
        if (!initialized) {
            initMutex.withLock {
                if (!initialized) initOnnx()
            }
        }

        if (!available) {
            logger.debug("No embedding source available")
            return texts.map { FloatArray(OnnxInference.DIMENSIONS) }
        }

        return inferBatched(texts)
    }

    private fun initOnnx() {
        initialized = true
        try {
            // Priority 2: Cached model
            if (ModelDownloader.isModelCached()) {
                val paths = ModelDownloader.getModelPaths()!!
                available = OnnxInference.initSession(paths.modelPath, paths.tokenizerPath)
                if (available) {
                    logger.info("ONNX model loaded from cache")
                    return
                }
            }

            // Priority 3: Download model
            logger.info("Downloading model...")
            val paths = ModelDownloader.downloadModel()
            available = OnnxInference.initSession(paths.modelPath, paths.tokenizerPath)
            if (available) {
                logger.info("ONNX model ready after download")
            } else {
                logger.warn("ONNX Runtime not available — embedding disabled")
            }
        } catch (e: Exception) {
            // Priority 4: Disable gracefully
            logger.error("Embedding init failed: {} — disabled", e.message)
            available = false
        }
    }

    private fun inferBatched(texts: List<String>): List<FloatArray> {
        val results = mutableListOf<FloatArray>()
        for (i in texts.indices step OnnxInference.MAX_BATCH) {
            val batch = texts.subList(i, minOf(i + OnnxInference.MAX_BATCH, texts.size))
            results.addAll(OnnxInference.infer(batch))
        }
        return results
    }
}
