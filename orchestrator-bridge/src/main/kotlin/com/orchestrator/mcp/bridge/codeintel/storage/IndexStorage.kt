package com.orchestrator.mcp.bridge.codeintel.storage

import com.orchestrator.mcp.bridge.codeintel.db.DatabaseManager
import com.orchestrator.mcp.bridge.codeintel.model.FileEntry
import com.orchestrator.mcp.bridge.codeintel.model.SymbolEntry
import org.slf4j.LoggerFactory

/**
 * Storage layer for writing indexed data to SQLite.
 * Handles batch inserts, incremental updates, and FTS5 sync.
 */
class IndexStorage(private val dbManager: DatabaseManager) {

    private val logger = LoggerFactory.getLogger(IndexStorage::class.java)

    fun upsertFile(file: FileEntry, symbols: List<SymbolEntry>) {
        val conn = dbManager.getConnection()
        conn.autoCommit = false
        try {
            val fileId = insertOrUpdateFile(conn, file)
            deleteSymbolsForFile(conn, fileId)
            deleteFtsForFile(conn, file.path)
            insertSymbols(conn, fileId, symbols)
            insertFtsEntries(conn, fileId, symbols, file.path)
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            logger.error("Failed to upsert file ${file.path}: ${e.message}")
        } finally {
            conn.autoCommit = true
        }
    }

    fun deleteFile(filePath: String) {
        val conn = dbManager.getConnection()
        deleteFtsForFile(conn, filePath)
        conn.prepareStatement("DELETE FROM files WHERE path = ?").use { stmt ->
            stmt.setString(1, filePath)
            stmt.executeUpdate()
        }
    }

    fun getStoredHashes(): Map<String, String> {
        val conn = dbManager.getConnection()
        val result = mutableMapOf<String, String>()
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT path, content_hash FROM files")
            while (rs.next()) {
                result[rs.getString("path")] = rs.getString("content_hash")
            }
        }
        return result
    }

    fun insertModule(name: String, path: String): Long {
        val conn = dbManager.getConnection()
        conn.prepareStatement(
            "INSERT OR IGNORE INTO modules (name, path) VALUES (?, ?)"
        ).use { stmt ->
            stmt.setString(1, name)
            stmt.setString(2, path)
            stmt.executeUpdate()
        }
        conn.prepareStatement("SELECT id FROM modules WHERE name = ?").use { stmt ->
            stmt.setString(1, name)
            val rs = stmt.executeQuery()
            return if (rs.next()) rs.getLong("id") else 0
        }
    }

    private fun insertOrUpdateFile(conn: java.sql.Connection, file: FileEntry): Long {
        val sql = """
            INSERT INTO files (path, language, content_hash, size_bytes, last_indexed, module_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(path) DO UPDATE SET
                language = excluded.language,
                content_hash = excluded.content_hash,
                size_bytes = excluded.size_bytes,
                last_indexed = excluded.last_indexed,
                module_id = excluded.module_id
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, file.path)
            stmt.setString(2, file.language)
            stmt.setString(3, file.contentHash)
            stmt.setLong(4, file.sizeBytes)
            stmt.setString(5, file.lastIndexed)
            if (file.moduleId != null) stmt.setLong(6, file.moduleId)
            else stmt.setNull(6, java.sql.Types.INTEGER)
            stmt.executeUpdate()
        }
        conn.prepareStatement("SELECT id FROM files WHERE path = ?").use { stmt ->
            stmt.setString(1, file.path)
            val rs = stmt.executeQuery()
            return if (rs.next()) rs.getLong("id") else 0
        }
    }

    private fun deleteSymbolsForFile(conn: java.sql.Connection, fileId: Long) {
        conn.prepareStatement("DELETE FROM symbols WHERE file_id = ?").use { stmt ->
            stmt.setLong(1, fileId)
            stmt.executeUpdate()
        }
    }

    private fun deleteFtsForFile(conn: java.sql.Connection, filePath: String) {
        conn.prepareStatement(
            "DELETE FROM symbols_fts WHERE file_path = ?"
        ).use { stmt ->
            stmt.setString(1, filePath)
            stmt.executeUpdate()
        }
    }

    private fun insertSymbols(conn: java.sql.Connection, fileId: Long, symbols: List<SymbolEntry>) {
        val sql = """
            INSERT INTO symbols (file_id, name, kind, signature, line_start, line_end, visibility)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            symbols.forEach { s ->
                stmt.setLong(1, fileId)
                stmt.setString(2, s.name)
                stmt.setString(3, s.kind)
                stmt.setString(4, s.signature)
                stmt.setInt(5, s.lineStart)
                if (s.lineEnd != null) stmt.setInt(6, s.lineEnd) else stmt.setNull(6, java.sql.Types.INTEGER)
                if (s.visibility != null) stmt.setString(7, s.visibility) else stmt.setNull(7, java.sql.Types.VARCHAR)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun insertFtsEntries(
        conn: java.sql.Connection, fileId: Long, symbols: List<SymbolEntry>, filePath: String
    ) {
        val moduleName = getModuleNameForFile(conn, fileId)
        val sql = "INSERT INTO symbols_fts (rowid, name, signature, file_path, module_name) VALUES (?, ?, ?, ?, ?)"
        conn.prepareStatement(sql).use { stmt ->
            // Get symbol IDs just inserted
            val idSql = "SELECT id, name, signature FROM symbols WHERE file_id = ? ORDER BY line_start"
            conn.prepareStatement(idSql).use { idStmt ->
                idStmt.setLong(1, fileId)
                val rs = idStmt.executeQuery()
                while (rs.next()) {
                    stmt.setLong(1, rs.getLong("id"))
                    stmt.setString(2, rs.getString("name"))
                    stmt.setString(3, rs.getString("signature"))
                    stmt.setString(4, filePath)
                    stmt.setString(5, moduleName ?: "")
                    stmt.addBatch()
                }
            }
            stmt.executeBatch()
        }
    }

    private fun getModuleNameForFile(conn: java.sql.Connection, fileId: Long): String? {
        val sql = """
            SELECT m.name FROM modules m
            JOIN files f ON f.module_id = m.id
            WHERE f.id = ?
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, fileId)
            val rs = stmt.executeQuery()
            return if (rs.next()) rs.getString("name") else null
        }
    }
}
