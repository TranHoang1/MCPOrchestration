# Technical Design Document (TDD)

## MTO-121: SQLite Schema + FTS5 Setup

---

## Document Information

| Field | Value |
|-------|-------|
| Ticket | MTO-121 |
| Title | SQLite Schema + FTS5 Setup |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2025-07-09 |
| Status | Draft |
| Related FSD | FSD-v1-MTO-120.docx (Section 3.1) |

---

## 1. Architecture Overview

### 1.1 Component Placement

The SQLite schema and database management lives in the `orchestrator-bridge` module under a new `codeintel` package:

```
orchestrator-bridge/src/main/kotlin/com/orchestrator/mcp/bridge/codeintel/
├── db/
│   ├── DatabaseManager.kt          # Database lifecycle (open, migrate, close)
│   ├── SchemaDefinitions.kt        # DDL constants for all tables
│   ├── MigrationRunner.kt          # Sequential migration execution
│   └── migrations/
│       └── Migration001Initial.kt  # V1 schema creation
├── model/
│   ├── FileEntry.kt                # File table data class
│   ├── SymbolEntry.kt              # Symbol table data class
│   ├── ModuleEntry.kt              # Module table data class
│   └── IndexStats.kt               # Statistics DTO
└── config/
    └── CodeIntelConfig.kt          # Configuration data class
```

### 1.2 Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| SQLite driver | `org.xerial:sqlite-jdbc:3.46.1.0` | Mature, well-tested, supports FTS5 out of box |
| Connection management | Single connection with WAL | SQLite single-writer model; WAL allows concurrent reads |
| Migration strategy | Version table + sequential migrations | Simple, predictable, no external framework needed |
| Schema location | Kotlin constants (not SQL files) | Keeps schema close to code, type-safe references |
| Database path | `{workspace}/.bridge/code-index.db` | Per-workspace isolation, easy reset by deletion |

### 1.3 Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| `org.xerial:sqlite-jdbc` | 3.46.1.0 | SQLite JDBC driver with FTS5 support |

Add to `gradle/libs.versions.toml`:
```toml
[versions]
sqlite-jdbc = "3.46.1.0"

[libraries]
sqlite-jdbc = { module = "org.xerial:sqlite-jdbc", version.ref = "sqlite-jdbc" }
```

Add to `orchestrator-bridge/build.gradle.kts`:
```kotlin
implementation(libs.sqlite.jdbc)
```

---

## 2. Detailed Design

### 2.1 DatabaseManager

**Responsibility:** Open/create SQLite database, enable WAL, run migrations, provide connection.

**Interface:**

```kotlin
interface DatabaseManager {
    fun initialize(): Result<Unit>
    fun getConnection(): java.sql.Connection
    fun close()
    fun isReady(): Boolean
    fun getSchemaVersion(): Int
}
```

**Lifecycle:**
1. Resolve path: `{workspaceRoot}/.bridge/code-index.db`
2. Create `.bridge/` directory if missing
3. Open JDBC connection: `jdbc:sqlite:{path}`
4. Execute: `PRAGMA journal_mode=WAL`
5. Execute: `PRAGMA foreign_keys=ON`
6. Execute: `PRAGMA busy_timeout=5000`
7. Check `schema_version` table → run pending migrations
8. Set state = READY

**Error handling:** Returns `Result.failure()` on any initialization error. Bridge continues without code intelligence.

### 2.2 SchemaDefinitions

**Responsibility:** Hold all DDL as string constants.

**Tables:**

```sql
-- schema_version: tracks applied migrations
CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- files: indexed source files
CREATE TABLE IF NOT EXISTS files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    path TEXT NOT NULL UNIQUE,
    language TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    last_indexed TEXT NOT NULL,
    module_id INTEGER REFERENCES modules(id) ON DELETE SET NULL
);

-- symbols: extracted code signatures
CREATE TABLE IF NOT EXISTS symbols (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,
    signature TEXT NOT NULL,
    line_start INTEGER NOT NULL,
    line_end INTEGER,
    visibility TEXT
);

-- modules: detected project modules
CREATE TABLE IF NOT EXISTS modules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    path TEXT NOT NULL,
    description TEXT,
    summary TEXT,
    embedding BLOB
);

-- embeddings: vector embeddings for semantic search (Layer 2)
CREATE TABLE IF NOT EXISTS embeddings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
    vector BLOB NOT NULL,
    text_summary TEXT NOT NULL,
    model TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- FTS5 virtual table for full-text search
CREATE VIRTUAL TABLE IF NOT EXISTS symbols_fts USING fts5(
    name,
    signature,
    file_path,
    module_name,
    content='',
    tokenize='porter unicode61'
);
```

**Indexes:**

```sql
CREATE INDEX IF NOT EXISTS idx_files_language ON files(language);
CREATE INDEX IF NOT EXISTS idx_files_module_id ON files(module_id);
CREATE INDEX IF NOT EXISTS idx_symbols_file_id ON symbols(file_id);
CREATE INDEX IF NOT EXISTS idx_symbols_kind ON symbols(kind);
CREATE INDEX IF NOT EXISTS idx_symbols_name ON symbols(name);
CREATE INDEX IF NOT EXISTS idx_embeddings_file_id ON embeddings(file_id);
```

### 2.3 MigrationRunner

**Responsibility:** Execute migrations sequentially, track versions.

**Interface:**

```kotlin
interface Migration {
    val version: Int
    val description: String
    fun up(connection: Connection)
}
```

**Algorithm:**
1. Query current version: `SELECT MAX(version) FROM schema_version`
2. Filter migrations where `migration.version > currentVersion`
3. Sort by version ascending
4. For each migration: execute in transaction, insert version record
5. If any migration fails: log error, return failure

### 2.4 Model Classes

**FileEntry:**
```kotlin
@Serializable
data class FileEntry(
    val id: Long = 0,
    val path: String,
    val language: String,
    val contentHash: String,
    val sizeBytes: Long,
    val lastIndexed: String,
    val moduleId: Long? = null
)
```

**SymbolEntry:**
```kotlin
@Serializable
data class SymbolEntry(
    val id: Long = 0,
    val fileId: Long,
    val name: String,
    val kind: String,
    val signature: String,
    val lineStart: Int,
    val lineEnd: Int? = null,
    val visibility: String? = null
)
```

**ModuleEntry:**
```kotlin
@Serializable
data class ModuleEntry(
    val id: Long = 0,
    val name: String,
    val path: String,
    val description: String? = null,
    val summary: String? = null
)
```

### 2.5 CodeIntelConfig

```kotlin
@Serializable
data class CodeIntelConfig(
    val enabled: Boolean = true,
    val databasePath: String = ".bridge/code-index.db"
)
```

---

## 3. Implementation Checklist

| # | File | Action | Lines (est.) |
|---|------|--------|-------------|
| 1 | `gradle/libs.versions.toml` | Add sqlite-jdbc version + library | +3 |
| 2 | `orchestrator-bridge/build.gradle.kts` | Add sqlite-jdbc dependency | +1 |
| 3 | `codeintel/config/CodeIntelConfig.kt` | Create config data class | ~20 |
| 4 | `codeintel/model/FileEntry.kt` | Create file model | ~25 |
| 5 | `codeintel/model/SymbolEntry.kt` | Create symbol model | ~25 |
| 6 | `codeintel/model/ModuleEntry.kt` | Create module model | ~20 |
| 7 | `codeintel/model/IndexStats.kt` | Create stats DTO | ~25 |
| 8 | `codeintel/db/SchemaDefinitions.kt` | DDL constants | ~80 |
| 9 | `codeintel/db/MigrationRunner.kt` | Migration interface + runner | ~60 |
| 10 | `codeintel/db/migrations/Migration001Initial.kt` | V1 schema creation | ~50 |
| 11 | `codeintel/db/DatabaseManager.kt` | DB lifecycle management | ~100 |

---

## 4. Error Handling

| Error | Detection | Recovery |
|-------|-----------|----------|
| Disk full | `SQLException` on CREATE | Return failure, bridge continues without CI |
| Permission denied | `IOException` on directory creation | Return failure, log warning |
| Corrupt DB | `PRAGMA integrity_check` fails | Backup → delete → recreate |
| Migration failure | Exception during `up()` | Rollback transaction, return failure |
| SQLite not available | `ClassNotFoundException` | Return failure, disable CI |

---

## 5. Testing Strategy

| Test | Type | Description |
|------|------|-------------|
| Schema creation on fresh DB | Unit | Verify all tables created correctly |
| WAL mode enabled | Unit | Verify PRAGMA returns "wal" |
| Migration versioning | Unit | Verify migrations run in order |
| Idempotent initialization | Unit | Call initialize() twice → no error |
| FTS5 table functional | Integration | Insert data, verify MATCH query works |
| Corrupt DB recovery | Unit | Simulate corrupt file, verify recreation |

---
