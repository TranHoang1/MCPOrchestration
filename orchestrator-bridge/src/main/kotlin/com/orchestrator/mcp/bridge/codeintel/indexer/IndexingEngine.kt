package com.orchestrator.mcp.bridge.codeintel.indexer

import com.orchestrator.mcp.bridge.codeintel.config.CodeIntelConfig
import com.orchestrator.mcp.bridge.codeintel.extractor.SignatureExtractor
import com.orchestrator.mcp.bridge.codeintel.model.FileEntry
import com.orchestrator.mcp.bridge.codeintel.scanner.ContentHasher
import com.orchestrator.mcp.bridge.codeintel.scanner.FileScanner
import com.orchestrator.mcp.bridge.codeintel.storage.IndexStorage
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant

/**
 * Background indexing engine. Performs full and incremental scans,
 * processes file changes, and manages indexing state.
 */
class IndexingEngine(
    private val workspaceRoot: String,
    private val config: CodeIntelConfig,
    private val storage: IndexStorage
) {

    private val logger = LoggerFactory.getLogger(IndexingEngine::class.java)
    private val scanner = FileScanner(workspaceRoot, config)

    @Volatile var status: String = "idle"
        private set
    @Volatile var progress: Int = 0
        private set

    suspend fun runFullScan() = withContext(Dispatchers.IO) {
        status = "indexing"
        progress = 0
        logger.info("Starting full workspace scan...")

        val files = scanner.scan()
        val total = files.size
        var processed = 0

        files.chunked(config.batchSize).forEach { batch ->
            batch.forEach { scannedFile ->
                indexFile(scannedFile)
                processed++
                progress = ((processed.toFloat() / total) * 100).toInt()
            }
            yield() // allow cancellation
        }

        status = "ready"
        progress = 100
        logger.info("Full scan complete: $processed files indexed")
    }

    suspend fun runIncrementalScan() = withContext(Dispatchers.IO) {
        status = "indexing"
        val storedHashes = storage.getStoredHashes()
        val currentFiles = scanner.scan()

        val currentPaths = currentFiles.map { it.relativePath }.toSet()
        val storedPaths = storedHashes.keys

        // Detect deleted files
        val deleted = storedPaths - currentPaths
        deleted.forEach { storage.deleteFile(it) }

        // Detect new or changed files
        var updated = 0
        currentFiles.forEach { scannedFile ->
            val hash = ContentHasher.hashFile(File(scannedFile.absolutePath))
            val storedHash = storedHashes[scannedFile.relativePath]
            if (storedHash == null || storedHash != hash) {
                indexFileWithHash(scannedFile, hash)
                updated++
            }
        }

        status = "ready"
        progress = 100
        logger.info("Incremental scan: $updated updated, ${deleted.size} deleted")
    }

    fun indexSingleFile(absolutePath: String) {
        val rootFile = File(workspaceRoot)
        val file = File(absolutePath)
        if (!file.exists()) {
            val relativePath = rootFile.toPath().relativize(file.toPath()).toString().replace('\\', '/')
            storage.deleteFile(relativePath)
            return
        }

        val relativePath = rootFile.toPath().relativize(file.toPath()).toString().replace('\\', '/')
        val hash = ContentHasher.hashFile(file)
        val language = com.orchestrator.mcp.bridge.codeintel.scanner.LanguageDetector.detect(file.name) ?: return
        val content = file.readText()
        val symbols = SignatureExtractor.extract(language, content, 0)
        val now = Instant.now().toString()

        val entry = FileEntry(
            path = relativePath, language = language,
            contentHash = hash, sizeBytes = file.length(), lastIndexed = now
        )
        storage.upsertFile(entry, symbols)
    }

    private fun indexFile(scannedFile: FileScanner.ScannedFile) {
        val file = File(scannedFile.absolutePath)
        val hash = ContentHasher.hashFile(file)
        indexFileWithHash(scannedFile, hash)
    }

    private fun indexFileWithHash(scannedFile: FileScanner.ScannedFile, hash: String) {
        val file = File(scannedFile.absolutePath)
        val content = file.readText()
        val symbols = SignatureExtractor.extract(scannedFile.language, content, 0)
        val now = Instant.now().toString()

        val entry = FileEntry(
            path = scannedFile.relativePath, language = scannedFile.language,
            contentHash = hash, sizeBytes = scannedFile.sizeBytes, lastIndexed = now
        )
        storage.upsertFile(entry, symbols)
    }
}
