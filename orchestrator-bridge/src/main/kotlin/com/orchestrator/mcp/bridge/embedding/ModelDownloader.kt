package com.orchestrator.mcp.bridge.embedding

import org.slf4j.LoggerFactory
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Downloads and caches the all-MiniLM-L6-v2 ONNX model from HuggingFace.
 * Cache path: ~/.mcp-bridge/models/all-MiniLM-L6-v2/
 */
object ModelDownloader {
    private val logger = LoggerFactory.getLogger(ModelDownloader::class.java)
    private const val MODEL_NAME = "all-MiniLM-L6-v2"
    private const val BASE_URL =
        "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main"
    private const val MODEL_FILE = "onnx/model.onnx"
    private const val TOKENIZER_FILE = "tokenizer.json"
    private const val MAX_REDIRECTS = 5

    data class ModelPaths(val modelPath: Path, val tokenizerPath: Path)

    /** Get the cache directory for the embedding model. */
    fun getCacheDir(): Path {
        val home = Path.of(System.getProperty("user.home"))
        return home.resolve(".mcp-bridge").resolve("models").resolve(MODEL_NAME)
    }

    /** Check if model files are already cached locally. */
    fun isModelCached(): Boolean {
        val dir = getCacheDir()
        return Files.exists(dir.resolve("model.onnx")) &&
            Files.exists(dir.resolve("tokenizer.json"))
    }

    /** Get paths to cached model files. Returns null if not cached. */
    fun getModelPaths(): ModelPaths? {
        if (!isModelCached()) return null
        val dir = getCacheDir()
        return ModelPaths(dir.resolve("model.onnx"), dir.resolve("tokenizer.json"))
    }

    /** Download model from HuggingFace and cache locally. */
    fun downloadModel(): ModelPaths {
        val dir = getCacheDir()
        Files.createDirectories(dir)

        val modelPath = dir.resolve("model.onnx")
        val tokenizerPath = dir.resolve("tokenizer.json")

        if (!Files.exists(modelPath)) {
            logger.info("Downloading model (~80MB)...")
            downloadFile("$BASE_URL/$MODEL_FILE", modelPath)
            logger.info("Model downloaded.")
        }

        if (!Files.exists(tokenizerPath)) {
            logger.info("Downloading tokenizer...")
            downloadFile("$BASE_URL/$TOKENIZER_FILE", tokenizerPath)
            logger.info("Tokenizer downloaded.")
        }

        return ModelPaths(modelPath, tokenizerPath)
    }

    private fun downloadFile(url: String, dest: Path, redirects: Int = MAX_REDIRECTS) {
        if (redirects <= 0) throw RuntimeException("Too many redirects")
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000

        try {
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                    ?: throw RuntimeException("Redirect without Location header")
                downloadFile(location, dest, redirects - 1)
                return
            }
            if (code != 200) throw RuntimeException("Download failed: HTTP $code")

            conn.inputStream.use { input ->
                FileOutputStream(dest.toFile()).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
