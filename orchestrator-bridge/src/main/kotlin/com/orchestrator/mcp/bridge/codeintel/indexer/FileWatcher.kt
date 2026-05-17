package com.orchestrator.mcp.bridge.codeintel.indexer

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 * File watcher using Java NIO WatchService.
 * Monitors workspace for file changes with debounce buffer.
 */
class FileWatcher(
    private val workspaceRoot: String,
    private val debounceMs: Long = 2000,
    private val onFileChanged: (String) -> Unit
) {

    private val logger = LoggerFactory.getLogger(FileWatcher::class.java)
    private var watchService: WatchService? = null
    private var watchJob: Job? = null
    private val pendingChanges = mutableSetOf<String>()

    fun start(scope: CoroutineScope) {
        try {
            watchService = FileSystems.getDefault().newWatchService()
            registerDirectories(Path.of(workspaceRoot))
            watchJob = scope.launch(Dispatchers.IO) { watchLoop() }
            logger.info("File watcher started on: $workspaceRoot")
        } catch (e: Exception) {
            logger.warn("File watcher failed to start: ${e.message}")
        }
    }

    fun stop() {
        watchJob?.cancel()
        watchService?.close()
        logger.info("File watcher stopped")
    }

    private suspend fun watchLoop() {
        val ws = watchService ?: return
        while (currentCoroutineContext().isActive) {
            val key = ws.poll(1, java.util.concurrent.TimeUnit.SECONDS) ?: continue
            processEvents(key)
            key.reset()
            debouncedFlush()
        }
    }

    private fun processEvents(key: WatchKey) {
        val dir = key.watchable() as? Path ?: return
        key.pollEvents().forEach { event ->
            val kind = event.kind()
            if (kind == StandardWatchEventKinds.OVERFLOW) return@forEach
            val path = dir.resolve(event.context() as Path)
            synchronized(pendingChanges) {
                pendingChanges.add(path.toAbsolutePath().toString())
            }
        }
    }

    private suspend fun debouncedFlush() {
        delay(debounceMs)
        val changes: Set<String>
        synchronized(pendingChanges) {
            changes = pendingChanges.toSet()
            pendingChanges.clear()
        }
        changes.forEach { path -> onFileChanged(path) }
    }

    private fun registerDirectories(root: Path) {
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val name = dir.fileName?.toString() ?: ""
                if (name.startsWith(".") || name in SKIP_DIRS) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                dir.register(
                    watchService!!,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
                )
                return FileVisitResult.CONTINUE
            }
        })
    }

    companion object {
        private val SKIP_DIRS = setOf("node_modules", "build", "dist", ".gradle", "__pycache__")
    }
}
