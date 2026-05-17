package com.orchestrator.mcp.bridge.codeintel.query

import com.orchestrator.mcp.bridge.codeintel.db.DatabaseManager
import com.orchestrator.mcp.bridge.codeintel.model.IndexStats
import com.orchestrator.mcp.bridge.codeintel.model.LayerStatus
import org.slf4j.LoggerFactory

/**
 * Query layer for the code intelligence database.
 * Provides FTS5 search, symbol lookup, module listing, and stats.
 */
class QueryLayer(private val dbManager: DatabaseManager) {

    private val logger = LoggerFactory.getLogger(QueryLayer::class.java)

    fun searchFTS(query: String, language: String?, module: String?, limit: Int): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val conn = dbManager.getConnection()
        val sql = buildSearchQuery(language, module)

        return conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, query)
            stmt.setInt(2, limit)
            val rs = stmt.executeQuery()
            val results = mutableListOf<SearchResult>()
            while (rs.next()) {
                results.add(mapSearchResult(rs))
            }
            results
        }
    }

    fun getSymbolsByFile(filePath: String): List<SymbolResult> {
        val conn = dbManager.getConnection()
        val sql = """
            SELECT s.name, s.kind, s.signature, s.line_start, s.line_end, s.visibility
            FROM symbols s JOIN files f ON s.file_id = f.id
            WHERE f.path = ?
            ORDER BY s.line_start
        """.trimIndent()

        return conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, filePath)
            val rs = stmt.executeQuery()
            val results = mutableListOf<SymbolResult>()
            while (rs.next()) {
                results.add(
                    SymbolResult(
                        name = rs.getString("name"),
                        kind = rs.getString("kind"),
                        signature = rs.getString("signature"),
                        lineStart = rs.getInt("line_start"),
                        lineEnd = rs.getObject("line_end") as? Int,
                        visibility = rs.getString("visibility")
                    )
                )
            }
            results
        }
    }

    fun getModules(): List<ModuleResult> {
        val conn = dbManager.getConnection()
        val sql = """
            SELECT m.name, m.path, m.summary,
                   COUNT(DISTINCT f.id) as file_count,
                   COUNT(s.id) as symbol_count
            FROM modules m
            LEFT JOIN files f ON f.module_id = m.id
            LEFT JOIN symbols s ON s.file_id = f.id
            GROUP BY m.id
            ORDER BY m.name
        """.trimIndent()

        return conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery(sql)
            val results = mutableListOf<ModuleResult>()
            while (rs.next()) {
                results.add(
                    ModuleResult(
                        name = rs.getString("name"),
                        path = rs.getString("path"),
                        fileCount = rs.getInt("file_count"),
                        symbolCount = rs.getInt("symbol_count"),
                        summary = rs.getString("summary")
                    )
                )
            }
            results
        }
    }

    fun getStats(status: String, progress: Int): IndexStats {
        val conn = dbManager.getConnection()
        val filesCount = countTable(conn, "files")
        val symbolsCount = countTable(conn, "symbols")
        val modulesCount = countTable(conn, "modules")
        val lastIndexed = getLastIndexed(conn)

        return IndexStats(
            status = status,
            filesIndexed = filesCount,
            symbolsIndexed = symbolsCount,
            modulesDetected = modulesCount,
            lastIndexed = lastIndexed,
            indexingProgress = progress,
            layers = LayerStatus(fts5 = true),
            dbSizeMb = dbManager.getDatabaseSizeMb()
        )
    }

    private fun buildSearchQuery(language: String?, module: String?): String {
        val filters = mutableListOf<String>()
        if (language != null) filters.add("AND f.language = '$language'")
        if (module != null) filters.add("AND m.name = '$module'")
        val filterClause = filters.joinToString(" ")

        return """
            SELECT s.name, s.kind, s.signature, s.line_start, f.path, m.name as module_name
            FROM symbols_fts fts
            JOIN symbols s ON fts.rowid = s.id
            JOIN files f ON s.file_id = f.id
            LEFT JOIN modules m ON f.module_id = m.id
            WHERE symbols_fts MATCH ? $filterClause
            ORDER BY rank
            LIMIT ?
        """.trimIndent()
    }

    private fun mapSearchResult(rs: java.sql.ResultSet): SearchResult {
        return SearchResult(
            file = rs.getString("path"),
            symbol = rs.getString("name"),
            kind = rs.getString("kind"),
            signature = rs.getString("signature"),
            line = rs.getInt("line_start"),
            module = rs.getString("module_name"),
            relevance = 1.0f
        )
    }

    private fun countTable(conn: java.sql.Connection, table: String): Int {
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT COUNT(*) FROM $table")
            return if (rs.next()) rs.getInt(1) else 0
        }
    }

    private fun getLastIndexed(conn: java.sql.Connection): String? {
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT MAX(last_indexed) FROM files")
            return if (rs.next()) rs.getString(1) else null
        }
    }
}
