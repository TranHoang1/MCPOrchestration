/**
 * SQLite database manager for code intelligence.
 * Uses better-sqlite3 for synchronous, fast SQLite access.
 */

import { existsSync, mkdirSync } from 'fs';
import { join } from 'path';

// Dynamic import for better-sqlite3 (optional dependency)
let Database: any;
try {
  Database = require('better-sqlite3');
} catch {
  Database = null;
}

export interface CodeIntelDb {
  isReady(): boolean;
  close(): void;
  run(sql: string, ...params: any[]): void;
  get(sql: string, ...params: any[]): any;
  all(sql: string, ...params: any[]): any[];
  prepare(sql: string): any;
}

export function initializeDatabase(workspaceRoot: string): CodeIntelDb | null {
  if (!Database) {
    console.error('[code-intel] better-sqlite3 not available, code intelligence disabled');
    return null;
  }

  const bridgeDir = join(workspaceRoot, '.bridge');
  if (!existsSync(bridgeDir)) mkdirSync(bridgeDir, { recursive: true });

  const dbPath = join(bridgeDir, 'code-index.db');
  try {
    const db = new Database(dbPath);
    db.pragma('journal_mode = WAL');
    db.pragma('foreign_keys = ON');
    db.pragma('busy_timeout = 5000');
    runMigrations(db);
    return wrapDb(db);
  } catch (err) {
    console.error('[code-intel] Database init failed:', err);
    return null;
  }
}

function wrapDb(db: any): CodeIntelDb {
  return {
    isReady: () => true,
    close: () => db.close(),
    run: (sql: string, ...params: any[]) => db.prepare(sql).run(...params),
    get: (sql: string, ...params: any[]) => db.prepare(sql).get(...params),
    all: (sql: string, ...params: any[]) => db.prepare(sql).all(...params),
    prepare: (sql: string) => db.prepare(sql),
  };
}

function runMigrations(db: any): void {
  db.exec(`CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
  )`);

  const row = db.prepare('SELECT MAX(version) as v FROM schema_version').get();
  const currentVersion = row?.v ?? 0;

  if (currentVersion < 1) {
    db.exec(MIGRATION_V1);
    db.prepare('INSERT INTO schema_version (version) VALUES (?)').run(1);
  }
}

const MIGRATION_V1 = `
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
`;
