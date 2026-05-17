package com.orchestrator.mcp.bridge.codeintel.db

/**
 * DDL constants for the code intelligence SQLite schema.
 * All table definitions, indexes, and FTS5 virtual table.
 */
object SchemaDefinitions {

    const val CREATE_SCHEMA_VERSION = """
        CREATE TABLE IF NOT EXISTS schema_version (
            version INTEGER PRIMARY KEY,
            applied_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
    """

    const val CREATE_MODULES = """
        CREATE TABLE IF NOT EXISTS modules (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            path TEXT NOT NULL,
            description TEXT,
            summary TEXT,
            embedding BLOB
        )
    """

    const val CREATE_FILES = """
        CREATE TABLE IF NOT EXISTS files (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            path TEXT NOT NULL UNIQUE,
            language TEXT NOT NULL,
            content_hash TEXT NOT NULL,
            size_bytes INTEGER NOT NULL,
            last_indexed TEXT NOT NULL,
            module_id INTEGER REFERENCES modules(id) ON DELETE SET NULL
        )
    """

    const val CREATE_SYMBOLS = """
        CREATE TABLE IF NOT EXISTS symbols (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            file_id INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
            name TEXT NOT NULL,
            kind TEXT NOT NULL,
            signature TEXT NOT NULL,
            line_start INTEGER NOT NULL,
            line_end INTEGER,
            visibility TEXT
        )
    """

    const val CREATE_EMBEDDINGS = """
        CREATE TABLE IF NOT EXISTS embeddings (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            file_id INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
            vector BLOB NOT NULL,
            text_summary TEXT NOT NULL,
            model TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
    """

    const val CREATE_SYMBOLS_FTS = """
        CREATE VIRTUAL TABLE IF NOT EXISTS symbols_fts USING fts5(
            name,
            signature,
            file_path,
            module_name,
            content='',
            tokenize='porter unicode61'
        )
    """

    val INDEXES = listOf(
        "CREATE INDEX IF NOT EXISTS idx_files_language ON files(language)",
        "CREATE INDEX IF NOT EXISTS idx_files_module_id ON files(module_id)",
        "CREATE INDEX IF NOT EXISTS idx_symbols_file_id ON symbols(file_id)",
        "CREATE INDEX IF NOT EXISTS idx_symbols_kind ON symbols(kind)",
        "CREATE INDEX IF NOT EXISTS idx_symbols_name ON symbols(name)",
        "CREATE INDEX IF NOT EXISTS idx_embeddings_file_id ON embeddings(file_id)"
    )
}
