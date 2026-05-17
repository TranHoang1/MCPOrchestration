package com.orchestrator.mcp.bridge.embedding

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.sqrt

/**
 * ONNX Runtime wrapper for all-MiniLM-L6-v2 inference.
 * Returns zeros if onnxruntime is not on classpath (graceful degradation).
 */
object OnnxInference {
    private val logger = LoggerFactory.getLogger(OnnxInference::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    const val DIMENSIONS = 384
    const val MAX_SEQ_LEN = 256
    const val MAX_BATCH = 32

    private var session: Any? = null
    private var tokenizer: WordPieceTokenizer? = null
    private var ortAvailable: Boolean? = null

    /** Check if ONNX Runtime is on classpath. */
    fun isOrtAvailable(): Boolean {
        if (ortAvailable != null) return ortAvailable!!
        ortAvailable = try {
            Class.forName("ai.onnxruntime.OrtEnvironment")
            true
        } catch (_: ClassNotFoundException) {
            logger.warn("onnxruntime not on classpath — using stub")
            false
        }
        return ortAvailable!!
    }

    /** Initialize ONNX session and tokenizer. Returns true on success. */
    fun initSession(modelPath: Path, tokenizerPath: Path): Boolean {
        if (session != null) return true
        if (!isOrtAvailable()) return false
        return try {
            session = createOrtSession(modelPath)
            val raw = Files.readString(tokenizerPath)
            val config = json.decodeFromString<TokenizerConfig>(raw)
            tokenizer = WordPieceTokenizer(config.model.vocab)
            true
        } catch (e: Exception) {
            logger.error("Failed to init ONNX session: {}", e.message)
            false
        }
    }

    /** Check if ONNX inference is ready. */
    fun isReady(): Boolean = session != null && tokenizer != null

    /** Run inference on texts. Returns list of 384-dim embeddings. */
    fun infer(texts: List<String>): List<FloatArray> {
        if (!isReady()) return texts.map { FloatArray(DIMENSIONS) }
        val batch = texts.take(MAX_BATCH)
        return runOnnxInference(batch)
    }

    private fun runOnnxInference(batch: List<String>): List<FloatArray> {
        return try {
            runRealInference(batch)
        } catch (e: Exception) {
            logger.error("ONNX inference failed: {}", e.message)
            batch.map { FloatArray(DIMENSIONS) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun runRealInference(batch: List<String>): List<FloatArray> {
        val tok = tokenizer ?: return batch.map { FloatArray(DIMENSIONS) }
        val batchSize = batch.size

        // Tokenize
        val allIds = batch.map { tok.tokenize(it, MAX_SEQ_LEN) }
        val inputIds = Array(batchSize) { i -> LongArray(MAX_SEQ_LEN) { j -> allIds[i][j].toLong() } }
        val attMask = Array(batchSize) { i -> LongArray(MAX_SEQ_LEN) { j -> if (allIds[i][j] != 0) 1L else 0L } }
        val typeIds = Array(batchSize) { LongArray(MAX_SEQ_LEN) }

        // Use reflection to call ONNX Runtime
        val envClass = Class.forName("ai.onnxruntime.OrtEnvironment")
        val env = envClass.getMethod("getEnvironment").invoke(null)
        val tensorClass = Class.forName("ai.onnxruntime.OnnxTensor")
        val createMethod = tensorClass.getMethod("createTensor", envClass, Array<LongArray>::class.java)

        val inputIdsTensor = createMethod.invoke(null, env, inputIds)
        val attMaskTensor = createMethod.invoke(null, env, attMask)
        val typeIdsTensor = createMethod.invoke(null, env, typeIds)

        val feeds = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attMaskTensor,
            "token_type_ids" to typeIdsTensor,
        )

        val sessionObj = session!!
        val runMethod = sessionObj.javaClass.getMethod("run", Map::class.java)
        val result = runMethod.invoke(sessionObj, feeds)

        // Extract output
        val resultMap = result as Map<String, Any>
        val outputTensor = resultMap.values.first()
        val getValue = outputTensor.javaClass.getMethod("getValue")
        val rawOutput = getValue.invoke(outputTensor) as Array<Array<FloatArray>>

        return meanPoolNormalize(rawOutput, attMask, batchSize)
    }

    private fun createOrtSession(modelPath: Path): Any {
        val envClass = Class.forName("ai.onnxruntime.OrtEnvironment")
        val env = envClass.getMethod("getEnvironment").invoke(null)
        val createSession = env.javaClass.getMethod("createSession", String::class.java)
        return createSession.invoke(env, modelPath.toString())
    }

    private fun meanPoolNormalize(
        hidden: Array<Array<FloatArray>>,
        mask: Array<LongArray>,
        batchSize: Int,
    ): List<FloatArray> {
        return (0 until batchSize).map { i ->
            val embedding = FloatArray(DIMENSIONS)
            var tokenCount = 0
            for (j in 0 until MAX_SEQ_LEN) {
                if (mask[i][j] == 0L) continue
                tokenCount++
                for (d in 0 until DIMENSIONS) {
                    embedding[d] += hidden[i][j][d]
                }
            }
            // Mean pooling
            if (tokenCount > 0) {
                for (d in 0 until DIMENSIONS) embedding[d] /= tokenCount
            }
            // L2 normalize
            var norm = 0f
            for (d in 0 until DIMENSIONS) norm += embedding[d] * embedding[d]
            norm = sqrt(norm)
            if (norm > 0f) {
                for (d in 0 until DIMENSIONS) embedding[d] /= norm
            }
            embedding
        }
    }
}

@Serializable
private data class TokenizerConfig(val model: TokenizerModel)

@Serializable
private data class TokenizerModel(val vocab: Map<String, Int>)
