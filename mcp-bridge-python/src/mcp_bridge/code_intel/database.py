"""SQLite database manager for Python bridge code intelligence.
Uses stdlib sqlite3 — zero external dependencies for Layer 1.
"""

import sqlite3
import os
from pathlib import Path


MIGRATION_V1 = """
CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE TABLE IF NOT EXISTS modules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE, path TEXT NOT NULL,
    description TEXT, summary TEXT, embedding BLOB
);
CREATE TABLE IF NOT EXISTS files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    path TEXT NOT NULL UNIQUE, language TEXT NOT NULL,
    content_hash TEXT NOT NULL, size_bytes INTEGER NOT NULL,
    last_indexed TEXT NOT NULL,
    module_id INTEGER REFERENCES modules(id) ON DELETE SET NULL
);
CREATE TABLE IF NOT EXISTS symbols (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
    name TEXT NOT NULL, kind TEXT NOT NULL, signature TEXT NOT NULL,
    line_start INTEGER NOT NULL, line_end INTEGER, visibility TEXT
);
CREATE TABLE IF NOT EXISTS embeddings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
    vector BLOB NOT NULL, text_summary TEXT NOT NULL,
    model TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE VIRTUAL TABLE IF NOT EXISTS symbols_fts USING fts5(
    name, signature, file_path, module_name, content='', tokenize='porter unicode61'
);
CREATE INDEX IF NOT EXISTS idx_files_language ON files(language);
CREATE INDEX IF NOT EXISTS idx_symbols_file_id ON symbols(file_id);
CREATE INDEX IF NOT EXISTS idx_symbols_name ON symbols(name);
"""


class DatabaseManager:
    """Manages SQLite database lifecycle for code intelligence."""

    def __init__(self, workspace_root: str):
        self._workspace_root = workspace_root
        self._conn: sqlite3.Connection | None = None
        self._ready = False

    @property
    def is_ready(self) -> bool:
        return self._ready

    def initialize(self) -> bool:
        """Initialize database. Returns True on success."""
        try:
            bridge_dir = Path(self._workspace_root) / '.bridge'
            bridge_dir.mkdir(parents=True, exist_ok=True)
            db_path = bridge_dir / 'code-index.db'
            self._conn = sqlite3.connect(str(db_path), check_same_thread=False)
            self._conn.execute("PRAGMA journal_mode=WAL")
            self._conn.execute("PRAGMA foreign_keys=ON")
            self._conn.execute("PRAGMA busy_timeout=5000")
            self._run_migrations()
            self._ready = True
            return True
        except Exception as e:
            print(f"[code-intel] Database init failed: {e}")
            return False

    def execute(self, sql: str, params=()) -> sqlite3.Cursor:
        assert self._conn is not None
        return self._conn.execute(sql, params)

    def executemany(self, sql: str, params_list) -> None:
        assert self._conn is not None
        self._conn.executemany(sql, params_list)

    def fetchall(self, sql: str, params=()) -> list:
        return self.execute(sql, params).fetchall()

    def fetchone(self, sql: str, params=()):
        return self.execute(sql, params).fetchone()

    def commit(self) -> None:
        if self._conn:
            self._conn.commit()

    def close(self) -> None:
        if self._conn:
            self._conn.close()
            self._ready = False

    def _run_migrations(self) -> None:
        assert self._conn is not None
        self._conn.execute(
            "CREATE TABLE IF NOT EXISTS schema_version "
            "(version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL DEFAULT (datetime('now')))"
        )
        row = self._conn.execute("SELECT MAX(version) FROM schema_version").fetchone()
        current = row[0] if row[0] else 0
        if current < 1:
            self._conn.executescript(MIGRATION_V1)
            self._conn.execute("INSERT INTO schema_version (version) VALUES (1)")
            self._conn.commit()
