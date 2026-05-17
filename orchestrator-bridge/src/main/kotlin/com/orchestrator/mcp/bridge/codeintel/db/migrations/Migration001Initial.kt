package com.orchestrator.mcp.bridge.codeintel.db.migrations

import com.orchestrator.mcp.bridge.codeintel.db.Migration
import com.orchestrator.mcp.bridge.codeintel.db.SchemaDefinitions
import java.sql.Connection

/**
 * V1: Initial schema — creates all core tables, indexes, and FTS5 virtual table.
 */
class Migration001Initial : Migration {

    override val version = 1
    override val description = "Initial schema: files, symbols, modules, embeddings, FTS5"

    override fun up(connection: Connection) {
        connection.createStatement().use { stmt ->
            stmt.execute(SchemaDefinitions.CREATE_MODULES)
            stmt.execute(SchemaDefinitions.CREATE_FILES)
            stmt.execute(SchemaDefinitions.CREATE_SYMBOLS)
            stmt.execute(SchemaDefinitions.CREATE_EMBEDDINGS)
            stmt.execute(SchemaDefinitions.CREATE_SYMBOLS_FTS)
            SchemaDefinitions.INDEXES.forEach { stmt.execute(it) }
        }
    }
}
