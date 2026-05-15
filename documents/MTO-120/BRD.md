# Business Requirements Document (BRD)

## Bridge Clients — MTO-120: Local Code Intelligence — SQLite Index + Semantic Search Across All Bridge Clients

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-120 |
| Title | [Bridge] Local Code Intelligence — SQLite Index + Semantic Search Across All Bridge Clients |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2025-07-08 |
| Status | Draft |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | BA Agent – Business Analyst | Create document |
| Peer Reviewer | SA Agent – Solution Architect | Review technical feasibility |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-08 | BA Agent | Initiate document — auto-generated from Epic MTO-120 backlog |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |

---

## 1. Introduction

### 1.1 Scope

This document defines the business requirements for implementing a **Local Code Intelligence** system across all bridge clients. The system provides SQLite-based code indexing with full-text search (FTS5), optional semantic search via Ollama embeddings, and optional AI-powered summarization. The scope includes:

1. **Shared SQLite schema** with FTS5 virtual tables for keyword-based code search
2. **File scanner and signature extractor** supporting multiple programming languages
3. **Five MCP tools** (`code_search`, `code_symbols`, `code_context`, `code_modules`, `code_index_status`) exposed to all agents
4. **Optional Ollama integration** for semantic embeddings (nomic-embed-text) and AI summarization (qwen3:8b)
5. **Background indexing with file watching** for real-time index updates
6. **Implementation across 6 bridge clients** in two tiers: Tier 1 (Kotlin, Node.js, Python — full capabilities) and Tier 2 (PowerShell, Bash — FTS5 only)
7. **Server-side VCS Index complement** for authoritative cross-team code reference

### 1.2 Out of Scope

- CMD bridge implementation (too limited for local indexing)
- Cross-workspace search (each workspace has isolated SQLite DB)
- Remote code indexing (handled by KB Server VCS index)
- IDE plugin UI for search results
- Code refactoring suggestions based on index
- Real-time collaborative editing awareness
- Custom language grammar definitions (regex-based extraction only)

### 1.3 Preliminary Requirement

- Bridge client infrastructure must be operational (WorkspaceContext, MCP tool registration)
- File system access available in all bridge clients
- Ollama installed locally with `nomic-embed-text` and `qwen3:8b` models (optional — for Layer 2+3)
- Hardware: GPU with ≥ 8GB VRAM recommended for embedding generation (RTX 4060 8GB target)
- SQLite library available per language runtime

---

## 2. Business Requirements

### 2.1 High Level Process Map

The Local Code Intelligence system operates as a layered architecture within each bridge client:

1. **Indexing Phase** — On bridge startup, the File Scanner traverses the workspace, extracts code signatures, and stores them in a per-workspace SQLite database with FTS5 indexing
2. **Embedding Phase (Optional)** — If Ollama is available, file summaries are embedded using `nomic-embed-text` and stored for semantic search
3. **Summarization Phase (Optional)** — Background job uses `qwen3:8b` to generate module/class summaries cached in SQLite
4. **Query Phase** — Agents invoke MCP tools (`code_search`, `code_symbols`, `code_context`, `code_modules`, `code_index_status`) to query the local index
5. **Maintenance Phase** — File watcher detects changes and incrementally updates the index in the background

**Architecture:**

```
Kiro IDE / Kiro CLI
  ↓ (workspace files)
Bridge Client
  ├── File Scanner (detect language, extract signatures)
  ├── SQLite DB (FTS5 + optional vector)
  ├── Ollama Client (optional, for embeddings + summarization)
  └── MCP Tools: code_search, code_symbols, code_context, code_modules, code_index_status
  ↓ (MCP protocol)
Agents (BA, TA, SA, DEV)
  → Query local index → instant, context-efficient responses
```

### 2.2 List of User Stories / Use Cases

| # | Story / Use Case | Priority | Tier |
|---|------------------|----------|------|
| 1 | SQLite Schema + FTS5 Setup | MUST HAVE | All |
| 2 | File Scanner + Signature Extractor | MUST HAVE | All |
| 3 | SQLite Storage + Query Layer | MUST HAVE | All |
| 4 | MCP Tools (code_search, code_symbols, code_context, code_modules, code_index_status) | MUST HAVE | All |
| 5 | Ollama Embedding Integration (Optional Layer 2) | SHOULD HAVE | Tier 1 |
| 6 | AI Summarization (Optional Layer 3) | COULD HAVE | Tier 1 |
| 7 | Background Indexing + File Watcher | MUST HAVE | All |
| 8 | Kotlin Bridge Implementation (Tier 1) | MUST HAVE | Tier 1 |
| 9 | Node.js Bridge Implementation (Tier 1) | MUST HAVE | Tier 1 |
| 10 | Python Bridge Implementation (Tier 1) | MUST HAVE | Tier 1 |
| 11 | PowerShell + Bash Bridge Implementation (Tier 2) | SHOULD HAVE | Tier 2 |
| 12 | Server-side VCS Index (KB Server complement) | COULD HAVE | Server |

---

### 2.3 Details of User Stories

---

#### Business Flow

**Step 1:** Bridge client starts → detects workspace root and initializes SQLite database (creates if not exists)

**Step 2:** File Scanner traverses workspace (respecting .gitignore), detects languages by extension, extracts code signatures via regex patterns

**Step 3:** Extracted signatures and file metadata are stored in SQLite with FTS5 indexing for full-text search

**Step 4:** (Optional) If Ollama is available, embeddings are generated for file summaries and stored for semantic search

**Step 5:** (Optional) AI summarization generates module/class summaries in background

**Step 6:** File watcher monitors workspace for changes → incrementally updates index (add/update/delete)

**Step 7:** Agents invoke MCP tools to search code, list symbols, get context, browse modules, or check index status

**Step 8:** Results are returned with minimal context consumption — agents get precise code references without loading entire files

> **Note:** Layer 1 (FTS5) always works offline. Layers 2+3 gracefully degrade if Ollama is unavailable.

---

#### STORY 1: SQLite Schema + FTS5 Setup

> As a developer, I want a well-defined SQLite schema with FTS5 virtual tables so that all bridge clients use a consistent data model for code indexing.

**Requirement Details:**

1. Define shared SQLite schema with three core tables: `files`, `symbols`, `modules`
2. Create FTS5 virtual table for full-text search across symbol names, file content summaries, and module descriptions
3. Implement schema versioning with migration support (version table tracks current schema version)
4. Schema must be identical across all Tier 1 and Tier 2 clients
5. Database file stored at `{workspace_root}/.bridge/code-index.db`

**Data Fields — `files` table:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| id | INTEGER PK | Yes | Auto-increment primary key | 1 |
| path | TEXT UNIQUE | Yes | Relative path from workspace root | "src/main/kotlin/App.kt" |
| language | TEXT | Yes | Detected programming language | "kotlin" |
| content_hash | TEXT | Yes | SHA-256 hash for change detection | "a1b2c3..." |
| size_bytes | INTEGER | Yes | File size in bytes | 4096 |
| last_indexed | TEXT | Yes | ISO-8601 timestamp | "2025-07-08T10:00:00Z" |
| module_id | INTEGER | No | FK to modules table | 3 |

**Data Fields — `symbols` table:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| id | INTEGER PK | Yes | Auto-increment primary key | 1 |
| file_id | INTEGER FK | Yes | Reference to files table | 1 |
| name | TEXT | Yes | Symbol name | "processRequest" |
| kind | TEXT | Yes | Symbol type | "function" / "class" / "interface" |
| signature | TEXT | Yes | Full signature line | "suspend fun processRequest(req: Request): Response" |
| line_start | INTEGER | Yes | Starting line number | 42 |
| line_end | INTEGER | No | Ending line number | 58 |
| visibility | TEXT | No | Access modifier | "public" / "private" / "internal" |

**Data Fields — `modules` table:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| id | INTEGER PK | Yes | Auto-increment primary key | 1 |
| name | TEXT UNIQUE | Yes | Module/package name | "sync-pipeline" |
| path | TEXT | Yes | Module root path | "sync-pipeline/src" |
| description | TEXT | No | Module description | "Data synchronization pipeline" |
| summary | TEXT | No | AI-generated summary (Layer 3) | "Handles Jira sync..." |
| embedding | BLOB | No | Vector embedding (Layer 2) | binary data |

**Acceptance Criteria:**

1. SQLite database is created automatically on first bridge startup
2. FTS5 virtual table indexes symbol names, signatures, and module descriptions
3. Schema version is tracked — migrations run automatically on version mismatch
4. Same schema works across Kotlin (JDBC), Node.js (better-sqlite3), Python (sqlite3), PowerShell (System.Data.SQLite), and Bash (sqlite3 CLI)
5. Database reset is achievable by deleting the `.bridge/code-index.db` file

**Validation Rules:**

- `path` must be relative (no absolute paths stored)
- `language` must be from supported set: kotlin, java, typescript, javascript, python, go, rust, bash, powershell
- `content_hash` must be valid SHA-256 hex string (64 chars)
- `kind` must be one of: class, interface, function, method, property, enum, struct, type

**Error Handling:**

- Database creation failure: Log error, disable indexing (bridge continues without code intelligence)
- Migration failure: Backup existing DB, recreate from scratch, log warning
- Disk full: Log error, disable write operations, read-only mode for existing index

---

#### STORY 2: File Scanner + Signature Extractor

> As a bridge client, I want to scan workspace files and extract code signatures so that the index contains searchable symbol information for all supported languages.

**Requirement Details:**

1. Scan workspace directory recursively, respecting `.gitignore` patterns and configurable exclude patterns
2. Detect programming language based on file extension
3. Extract code signatures using regex patterns per language:
   - **Kotlin/Java:** class, interface, object, fun, val/var (top-level and class members)
   - **TypeScript/JavaScript:** export class, export function, export const, interface, type
   - **Python:** class, def (with decorators)
   - **Go:** func, type, struct, interface
   - **Rust:** fn, struct, enum, trait, impl
   - **Bash/PowerShell:** function declarations
4. Compute content hash (SHA-256) for each file for incremental change detection
5. Support configurable scan depth and file size limits

**Data Fields (Configuration):**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| exclude_patterns | List<String> | No | Glob patterns to exclude | ["node_modules/**", "build/**", ".git/**"] |
| max_file_size_kb | Integer | No | Skip files larger than this | 500 |
| max_depth | Integer | No | Maximum directory depth | 20 |
| include_languages | List<String> | No | Only scan these languages (null = all) | ["kotlin", "typescript"] |

**Acceptance Criteria:**

1. Scanner respects `.gitignore` patterns (files ignored by git are not indexed)
2. Scanner extracts at least class/interface/function signatures for all supported languages
3. Content hash enables incremental scanning — unchanged files are skipped on re-scan
4. Scanner handles large workspaces (10,000+ files) without blocking the main thread
5. Scanner reports progress (files scanned, symbols found) via `code_index_status`
6. Binary files and generated files (e.g., `.class`, `.pyc`, `node_modules`) are automatically excluded

**Validation Rules:**

- File path must be valid UTF-8
- File must be readable (skip unreadable files with warning)
- Regex extraction must not hang on pathological input (timeout per file: 100ms)
- Maximum symbols per file: 1000 (skip remainder with warning)

**Error Handling:**

- Permission denied on file: Skip file, log warning, continue scanning
- Malformed file (binary detected): Skip file silently
- Regex timeout: Skip file, log warning with file path
- Symlink loop: Detect and skip circular symlinks

---

#### STORY 3: SQLite Storage + Query Layer

> As a bridge client, I want a storage and query layer that efficiently manages indexed data so that search operations are fast and incremental updates are lightweight.

**Requirement Details:**

1. Insert/update/delete file entries with associated symbols
2. FTS5 search supporting keyword matching with ranking
3. Query by multiple dimensions: module, file path, symbol name, language, symbol kind
4. Incremental update strategy: compare content_hash, only re-index changed files
5. Batch operations for initial full scan (bulk insert performance)
6. Transaction support for atomic updates (file + its symbols updated together)

**Query Interface:**

| Operation | Parameters | Returns | Description |
|-----------|-----------|---------|-------------|
| searchFTS | query: String, limit: Int | List<SearchResult> | Full-text search across symbols and files |
| getSymbolsByFile | filePath: String | List<Symbol> | All symbols in a specific file |
| getSymbolsByModule | moduleName: String | List<Symbol> | All symbols in a module |
| getFilesByLanguage | language: String | List<File> | All indexed files of a language |
| getModules | — | List<Module> | All detected modules |
| getChangedFiles | hashes: Map<Path, Hash> | List<Path> | Files whose hash differs from stored |
| getIndexStats | — | IndexStats | File count, symbol count, last indexed time |

**Acceptance Criteria:**

1. FTS5 search returns results ranked by relevance (BM25 algorithm)
2. Incremental update only re-indexes files with changed content_hash
3. Bulk insert of 10,000 symbols completes in < 5 seconds
4. Single file update (delete old symbols + insert new) completes in < 100ms
5. Search query returns results in < 200ms for indexes with 100,000+ symbols
6. Database size remains reasonable (< 50MB for a 10,000-file workspace)

**Error Handling:**

- Concurrent access: SQLite WAL mode for read concurrency; single writer with retry
- Corrupted database: Detect via integrity_check, recreate if corrupted
- Query timeout: Cancel long-running queries after 5 seconds

---

#### STORY 4: MCP Tools — code_search, code_symbols, code_context, code_modules, code_index_status

> As an agent (BA, TA, SA, DEV), I want MCP tools to query the local code index so that I can find relevant code without consuming excessive context window.

**Requirement Details:**

1. **`code_search(query, language?, module?, limit?)`** — FTS5 keyword search across all indexed symbols and files
2. **`code_symbols(file_path)`** — List all symbols (functions, classes, interfaces) in a specific file
3. **`code_context(query, top_k?)`** — Semantic search using embeddings (falls back to FTS5 if embeddings unavailable)
4. **`code_modules()`** — List all detected modules with file counts and optional AI summaries
5. **`code_index_status()`** — Report index health: file count, symbol count, last indexed time, indexing progress, layers available
6. All tools follow the same MCP tool interface regardless of which bridge client serves them

**Tool Specifications:**

**Tool: `code_search`**

| Parameter | Type | Required | Description | Default |
|-----------|------|----------|-------------|---------|
| query | String | Yes | Search query (keywords) | — |
| language | String | No | Filter by language | null (all) |
| module | String | No | Filter by module name | null (all) |
| limit | Integer | No | Max results to return | 20 |

**Response:**

```json
{
  "results": [
    {
      "file": "src/main/kotlin/service/AuthService.kt",
      "symbol": "authenticateUser",
      "kind": "function",
      "signature": "suspend fun authenticateUser(token: String): User?",
      "line": 42,
      "module": "auth-service",
      "relevance": 0.95
    }
  ],
  "total_matches": 15,
  "query_time_ms": 12
}
```

**Tool: `code_symbols`**

| Parameter | Type | Required | Description | Default |
|-----------|------|----------|-------------|---------|
| file_path | String | Yes | Relative file path | — |

**Response:**

```json
{
  "file": "src/main/kotlin/service/AuthService.kt",
  "language": "kotlin",
  "symbols": [
    {
      "name": "AuthService",
      "kind": "class",
      "signature": "class AuthService(private val repo: UserRepository)",
      "line_start": 10,
      "line_end": 85,
      "visibility": "public"
    }
  ],
  "symbol_count": 8
}
```

**Tool: `code_context`**

| Parameter | Type | Required | Description | Default |
|-----------|------|----------|-------------|---------|
| query | String | Yes | Natural language query | — |
| top_k | Integer | No | Number of results | 5 |

**Response:**

```json
{
  "results": [
    {
      "file": "src/main/kotlin/service/AuthService.kt",
      "summary": "Authentication service handling JWT token validation and user session management",
      "symbols": ["authenticateUser", "validateToken", "refreshSession"],
      "relevance": 0.92,
      "search_method": "embedding" 
    }
  ],
  "search_method": "embedding|fts5",
  "query_time_ms": 45
}
```

**Tool: `code_modules`**

| Parameter | Type | Required | Description | Default |
|-----------|------|----------|-------------|---------|
| — | — | — | No parameters | — |

**Response:**

```json
{
  "modules": [
    {
      "name": "sync-pipeline",
      "path": "sync-pipeline/src",
      "file_count": 45,
      "symbol_count": 320,
      "languages": ["kotlin"],
      "summary": "Data synchronization pipeline for Jira integration"
    }
  ],
  "total_modules": 5
}
```

**Tool: `code_index_status`**

| Parameter | Type | Required | Description | Default |
|-----------|------|----------|-------------|---------|
| — | — | — | No parameters | — |

**Response:**

```json
{
  "status": "ready|indexing|error",
  "files_indexed": 1250,
  "symbols_indexed": 8500,
  "modules_detected": 5,
  "last_indexed": "2025-07-08T10:30:00Z",
  "indexing_progress": 100,
  "layers": {
    "fts5": true,
    "embeddings": true,
    "summaries": false
  },
  "db_size_mb": 12.5
}
```

**Acceptance Criteria:**

1. `code_search("authenticate")` returns relevant symbols across all indexed files
2. `code_symbols("src/main/kotlin/App.kt")` returns all symbols in that file
3. `code_context("how does authentication work")` returns semantically relevant files (if embeddings available) or FTS5 results (fallback)
4. `code_modules()` returns all detected modules with file/symbol counts
5. `code_index_status()` accurately reports current index state and available layers
6. All tools respond within 500ms for typical queries
7. Tools return consistent JSON schema regardless of bridge client implementation

**Error Handling:**

- Index not ready: Return `{"status": "indexing", "message": "Index is being built, try again shortly"}`
- File not found (code_symbols): Return `{"error": "FILE_NOT_FOUND", "message": "File not in index"}`
- Empty query: Return validation error
- Ollama unavailable (code_context): Graceful fallback to FTS5 with `"search_method": "fts5"` in response

---

#### STORY 5: Ollama Embedding Integration (Optional Layer 2)

> As a Tier 1 bridge client, I want to generate vector embeddings for code files so that agents can perform semantic search (finding conceptually related code, not just keyword matches).

**Requirement Details:**

1. Check Ollama availability on bridge startup (HTTP health check to `http://localhost:11434`)
2. Generate embeddings for file summaries using `nomic-embed-text` model (768-dimension vectors)
3. Store embeddings in SQLite (BLOB column in `modules` table or separate `embeddings` table)
4. Implement cosine similarity search for `code_context` tool
5. Graceful degradation: if Ollama is unavailable or model not loaded → FTS5 only (no error to user)
6. Batch embedding generation during initial index build
7. Re-generate embeddings only when file content changes (content_hash comparison)

**Data Fields — `embeddings` table (optional):**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| id | INTEGER PK | Yes | Auto-increment primary key | 1 |
| file_id | INTEGER FK | Yes | Reference to files table | 1 |
| vector | BLOB | Yes | 768-dim float32 vector (3072 bytes) | binary |
| text_summary | TEXT | Yes | Text that was embedded | "AuthService: JWT validation..." |
| model | TEXT | Yes | Model used for embedding | "nomic-embed-text" |
| created_at | TEXT | Yes | ISO-8601 timestamp | "2025-07-08T10:00:00Z" |

**Acceptance Criteria:**

1. Bridge detects Ollama availability without blocking startup (async health check)
2. Embeddings are generated in background (non-blocking)
3. `code_context` uses cosine similarity when embeddings are available
4. `code_context` falls back to FTS5 when embeddings are unavailable (transparent to agent)
5. `code_index_status` reports `layers.embeddings: true/false` based on availability
6. Embedding generation rate: ≥ 50 files/minute on RTX 4060 8GB
7. Re-embedding only occurs for files with changed content_hash

**Validation Rules:**

- Ollama endpoint must respond within 5 seconds (timeout → mark unavailable)
- Vector dimension must be exactly 768 (nomic-embed-text output)
- Text summary for embedding must be ≤ 8192 tokens (model context limit)

**Error Handling:**

- Ollama not running: Set `layers.embeddings = false`, log info, continue with FTS5
- Model not loaded: Attempt `ollama pull nomic-embed-text` once, if fails → disable Layer 2
- Embedding generation timeout: Skip file, retry in next background cycle
- VRAM exhaustion: Reduce batch size, log warning
- Ollama becomes unavailable mid-indexing: Pause embedding generation, retry periodically (every 60s)

---

#### STORY 6: AI Summarization (Optional Layer 3)

> As a Tier 1 bridge client, I want AI-generated summaries for modules and classes so that agents get rich contextual descriptions when browsing code structure.

**Requirement Details:**

1. Background job generates summaries for modules and significant classes using `qwen3:8b` model
2. Summaries are cached in SQLite (`modules.summary` and optionally `files.summary` columns)
3. Summaries are refreshed when underlying files change (content_hash triggers re-summarization)
4. Summary generation is lowest priority (after indexing and embedding)
5. Summaries are used by `code_modules()` and `code_context()` tools for richer responses
6. Rate-limited to avoid overwhelming Ollama (max 1 concurrent summarization request)

**Summary Generation Strategy:**

| Target | Input | Output | Max Length |
|--------|-------|--------|------------|
| Module | All file paths + top-level symbols in module | 2-3 sentence module purpose description | 200 words |
| Class | Class signature + method signatures | 1-2 sentence class responsibility description | 100 words |
| File | All symbols in file | 1 sentence file purpose | 50 words |

**Acceptance Criteria:**

1. Module summaries are generated within 5 minutes of initial index completion
2. Summaries are human-readable and accurately describe code purpose
3. `code_modules()` includes AI summaries when available
4. Summary generation does not block search operations
5. `code_index_status` reports `layers.summaries: true/false` and progress percentage
6. Stale summaries (file changed) are marked for refresh and regenerated in background

**Validation Rules:**

- Summary must be non-empty and ≤ 500 characters
- Summary must be in English
- Ollama must be available with `qwen3:8b` model loaded

**Error Handling:**

- Ollama unavailable: Disable Layer 3, log info, summaries remain null
- Model generates garbage: Validate output length and coherence, discard if invalid
- Timeout (> 30s per summary): Skip, retry later
- Queue overflow (> 1000 pending summaries): Process in priority order (modules first, then classes)

---

#### STORY 7: Background Indexing + File Watcher

> As a bridge client, I want background indexing with file watching so that the code index stays up-to-date without blocking IDE or agent operations.

**Requirement Details:**

1. Full index scan on first bridge startup (workspace never indexed before)
2. Incremental scan on subsequent startups (only changed files based on content_hash)
3. File watcher monitors workspace for create/edit/delete events → triggers incremental update
4. All indexing operations run in background (coroutine/thread/async task — never blocks main thread)
5. Progress reporting via `code_index_status` tool (percentage, files remaining)
6. Debounce file change events (batch changes within 2-second window)
7. Respect system resource limits (CPU throttling during heavy IDE usage)

**Indexing Lifecycle:**

| Phase | Trigger | Action | Priority |
|-------|---------|--------|----------|
| Initial Full Scan | First startup (no DB exists) | Scan all files, extract symbols, build FTS5 | High |
| Startup Incremental | Subsequent startup | Compare hashes, re-index changed files | High |
| File Change | File watcher event | Re-index single file (delete old + insert new) | Medium |
| File Delete | File watcher event | Remove file and its symbols from index | Medium |
| Embedding Generation | After file indexed | Generate embedding for new/changed file | Low |
| Summary Generation | After embeddings done | Generate/refresh AI summaries | Lowest |

**Acceptance Criteria:**

1. Initial full scan of 5,000 files completes in < 60 seconds
2. Incremental startup scan (no changes) completes in < 5 seconds
3. Single file change is reflected in index within 5 seconds of save
4. File deletion removes all associated symbols and embeddings from index
5. Indexing never blocks MCP tool responses (tools return current state, even if stale)
6. `code_index_status` shows real-time progress during indexing
7. Bridge shutdown gracefully stops indexing (no corrupted database)

**Validation Rules:**

- File watcher must handle rapid successive changes (debounce 2s)
- Maximum concurrent file processing: 4 (avoid disk I/O saturation)
- Indexing must be interruptible (graceful shutdown within 5s)

**Error Handling:**

- File watcher initialization failure: Fall back to periodic polling (every 30s)
- File changed during indexing: Re-queue for next cycle
- Database locked during write: Retry with exponential backoff (max 3 retries)
- Out of memory during large scan: Process in smaller batches, log warning

---

#### STORY 8: Kotlin Bridge Implementation (Tier 1)

> As the Kotlin bridge client, I want full code intelligence capabilities (FTS5 + embeddings + summarization) so that agents using the Kotlin bridge get the richest code search experience.

**Requirement Details:**

1. SQLite via `sqlite-jdbc` library (already available in JVM ecosystem)
2. Ktor HTTP client for Ollama API communication (embeddings + summarization)
3. Coroutine-based background indexing (non-blocking, structured concurrency)
4. Integration with existing `WorkspaceContext` for workspace root detection
5. File watcher via Java NIO `WatchService` or kotlinx-io equivalent
6. All 5 MCP tools registered in existing tool registration infrastructure
7. Configuration via workspace-level config file (`.bridge/code-intelligence.json`)

**Technical Stack:**

| Component | Library | Version |
|-----------|---------|---------|
| SQLite | org.xerial:sqlite-jdbc | 3.45+ |
| HTTP Client | io.ktor:ktor-client-cio | existing |
| Coroutines | org.jetbrains.kotlinx:kotlinx-coroutines | existing |
| File Watcher | java.nio.file.WatchService | JDK stdlib |
| JSON | kotlinx.serialization | existing |
| Hashing | java.security.MessageDigest (SHA-256) | JDK stdlib |

**Acceptance Criteria:**

1. Kotlin bridge starts with code intelligence enabled by default
2. SQLite database created at `{workspace}/.bridge/code-index.db`
3. All 5 MCP tools functional and returning correct JSON responses
4. Background indexing uses structured concurrency (cancellable, no leaked coroutines)
5. Ollama integration works when available, gracefully degrades when not
6. Memory usage for indexing stays under 256MB heap for 10,000-file workspace
7. Integration tests cover all MCP tool responses

**Error Handling:**

- sqlite-jdbc not on classpath: Log error, disable code intelligence feature
- WorkspaceContext not initialized: Defer indexing until workspace is set
- Coroutine cancellation: Clean up database connections, flush pending writes

---

#### STORY 9: Node.js Bridge Implementation (Tier 1)

> As the Node.js bridge client, I want full code intelligence capabilities so that agents using the Node.js bridge get equivalent search functionality to the Kotlin bridge.

**Requirement Details:**

1. SQLite via `better-sqlite3` (synchronous, fast, native bindings)
2. `fetch` API for Ollama HTTP communication (Node.js 18+ built-in)
3. Worker thread for background indexing (non-blocking main event loop)
4. File watcher via `fs.watch` or `chokidar` for cross-platform reliability
5. All 5 MCP tools registered in existing MCP tool infrastructure
6. Same SQLite schema as Kotlin bridge (cross-client compatibility)

**Technical Stack:**

| Component | Library | Version |
|-----------|---------|---------|
| SQLite | better-sqlite3 | 11.x |
| HTTP Client | fetch (built-in) | Node.js 18+ |
| Worker | worker_threads (built-in) | Node.js 18+ |
| File Watcher | chokidar | 3.x |
| Hashing | crypto.createHash (built-in) | Node.js stdlib |

**Acceptance Criteria:**

1. Node.js bridge starts with code intelligence enabled by default
2. Worker thread handles all indexing without blocking MCP message processing
3. `better-sqlite3` provides synchronous queries (no callback complexity)
4. All 5 MCP tools return identical JSON schema as Kotlin bridge
5. File watcher reliably detects changes on Windows, macOS, and Linux
6. Memory usage stays under 512MB for 10,000-file workspace
7. npm package size increase is acceptable (better-sqlite3 native addon)

**Error Handling:**

- `better-sqlite3` native module build failure: Provide pre-built binaries, fallback instructions
- Worker thread crash: Restart worker, re-initialize index connection
- `chokidar` unavailable: Fall back to `fs.watch` with polling

---

#### STORY 10: Python Bridge Implementation (Tier 1)

> As the Python bridge client, I want full code intelligence capabilities so that agents using the Python bridge get equivalent search functionality.

**Requirement Details:**

1. SQLite via stdlib `sqlite3` module (zero external dependencies for core)
2. `httpx` for async Ollama HTTP communication
3. `asyncio` background task for indexing (non-blocking)
4. File watcher via `watchdog` library or `asyncio` polling
5. All 5 MCP tools registered in existing MCP tool infrastructure
6. Same SQLite schema as Kotlin and Node.js bridges

**Technical Stack:**

| Component | Library | Version |
|-----------|---------|---------|
| SQLite | sqlite3 (stdlib) | Python 3.10+ |
| HTTP Client | httpx | 0.27+ |
| Async | asyncio (stdlib) | Python 3.10+ |
| File Watcher | watchdog | 4.x |
| Hashing | hashlib (stdlib) | Python stdlib |

**Acceptance Criteria:**

1. Python bridge starts with code intelligence enabled by default
2. Core indexing uses only stdlib (sqlite3, hashlib, os, re) — zero external deps for Layer 1
3. Layer 2+3 require `httpx` (optional dependency, graceful if missing)
4. `asyncio` task handles background indexing without blocking MCP responses
5. All 5 MCP tools return identical JSON schema as other bridges
6. Works on Python 3.10+ (minimum supported version)
7. Memory usage stays under 256MB for 10,000-file workspace

**Error Handling:**

- `httpx` not installed: Disable Layer 2+3, log info, FTS5 only
- `watchdog` not installed: Fall back to periodic polling (every 30s)
- asyncio event loop not running: Use threading fallback for background indexing
- SQLite version too old (no FTS5): Check at startup, log error, disable feature

---

#### STORY 11: PowerShell + Bash Bridge Implementation (Tier 2)

> As the PowerShell and Bash bridge clients, I want basic code intelligence (FTS5 keyword search) so that agents using these bridges can still search code, even without semantic capabilities.

**Requirement Details:**

1. **PowerShell:** SQLite via `System.Data.SQLite` .NET assembly or `sqlite3.exe` CLI tool
2. **Bash:** SQLite via `sqlite3` CLI tool (commonly available on Linux/macOS)
3. FTS5 only — no embeddings, no AI summarization (Layer 1 only)
4. Simplified file scanner using native shell commands (`Get-ChildItem` / `find`)
5. Regex-based signature extraction using shell-native regex (`Select-String` / `grep -P`)
6. Same SQLite schema (subset — no embedding/summary columns populated)
7. Same MCP tool interface (code_context falls back to FTS5 always)

**PowerShell Implementation:**

| Component | Approach |
|-----------|----------|
| SQLite Access | `System.Data.SQLite` or `sqlite3.exe` subprocess |
| File Scanning | `Get-ChildItem -Recurse -Exclude` |
| Regex Extraction | `Select-String -Pattern` |
| File Watcher | `FileSystemWatcher` .NET class |
| Background Job | PowerShell `Start-Job` or `ThreadJob` |

**Bash Implementation:**

| Component | Approach |
|-----------|----------|
| SQLite Access | `sqlite3` CLI (pipe SQL commands) |
| File Scanning | `find . -name "*.kt" | grep -v node_modules` |
| Regex Extraction | `grep -nP "^(class|fun|interface)"` |
| File Watcher | `inotifywait` (Linux) or polling with `find -newer` |
| Background Job | Background process (`&`) or `nohup` |

**Acceptance Criteria:**

1. PowerShell bridge provides `code_search` and `code_symbols` via FTS5
2. Bash bridge provides `code_search` and `code_symbols` via FTS5
3. `code_context` on Tier 2 bridges always uses FTS5 (no embedding fallback needed — it's the only option)
4. `code_index_status` reports `layers.embeddings: false, layers.summaries: false`
5. Same MCP tool JSON response schema as Tier 1 bridges
6. Works without any external dependencies beyond `sqlite3` CLI
7. Indexing completes within 2 minutes for 5,000-file workspace

**Error Handling:**

- `sqlite3` CLI not found: Log error, disable code intelligence
- `inotifywait` not available: Fall back to periodic polling
- Shell regex fails on complex files: Skip file, log warning
- Large workspace timeout: Increase batch size, reduce scan frequency

---

#### STORY 12: Server-side VCS Index (KB Server Complement)

> As the system, I want a server-side VCS-based code index so that agents can reference authoritative code from the main branch for cross-team documentation and analysis.

**Requirement Details:**

1. KB Server indexes code from VCS (git) — authoritative, shared, for cross-team reference
2. Bridge Client indexes code from local workspace — per-user, fast, for active development
3. Both coexist with clear separation of concerns:
   - Agent creating BRD/FSD → uses server KB (authoritative code from main branch)
   - Agent implementing code → uses local index (includes uncommitted changes)
4. Server index updated on git push/merge events (webhook or polling)
5. Server provides same MCP tool interface but with `source: "vcs"` in responses
6. Local index takes priority for implementation tasks; server index for documentation tasks

**Coexistence Model:**

| Aspect | Local Index (Bridge) | Server Index (KB Server) |
|--------|---------------------|--------------------------|
| Source | Local filesystem (uncommitted changes) | Git repository (main/develop branch) |
| Scope | Single workspace | All project repositories |
| Freshness | Real-time (file watcher) | Near real-time (webhook on push) |
| Isolation | Per-user, per-workspace | Shared across team |
| Use Case | Active development, code navigation | Documentation, cross-team reference |
| Availability | Always (offline capable) | Requires server connectivity |
| Storage | SQLite on developer machine | PostgreSQL on KB Server |

**Acceptance Criteria:**

1. Server index is populated from git repository main branch
2. Server index updates within 5 minutes of a push to main
3. Agents can query both local and server indexes (tool routing based on context)
4. Server index provides cross-repository search (all project repos)
5. Local index is preferred for implementation tasks (fresher, includes WIP)
6. Server index is preferred for documentation/analysis tasks (authoritative)
7. No data duplication — each index serves its specific purpose

**Error Handling:**

- Server unavailable: Agents fall back to local index only
- Git webhook missed: Periodic polling (every 15 minutes) as backup
- Large repository (> 100,000 files): Incremental indexing with priority on recently changed files

---

## 3. Dependencies

| Dependency | Type | Related Ticket | Description |
|------------|------|----------------|-------------|
| SQLite libraries | System | N/A | sqlite-jdbc (Kotlin), better-sqlite3 (Node.js), sqlite3 stdlib (Python), sqlite3 CLI (Bash/PS) |
| Ollama | Infrastructure | N/A | Local AI runtime for embeddings (nomic-embed-text) and summarization (qwen3:8b) |
| Bridge client infrastructure | System | N/A | WorkspaceContext, MCP tool registration, message handling |
| File system access | System | N/A | Read access to workspace files (already available in all clients) |
| .gitignore parsing | System | N/A | Respect git ignore patterns during file scanning |
| RTX 4060 8GB VRAM | Hardware | N/A | Recommended GPU for embedding generation performance |
| KB Server | System | N/A | Server-side VCS index complement (Story 12) |
| Kiro CLI/LSP | System | N/A | Leverage for accurate parsing where available |

---

## 4. Stakeholders

| Role | Name / Team | Responsibility | Source |
|------|-------------|----------------|--------|
| All Agents (BA, TA, SA, DEV) | Agent Team | Primary consumers of MCP code search tools | Tool consumers |
| Bridge Client Developers | Development Team | Implement code intelligence in each bridge client | Implementation |
| SA Agent | Solution Architecture | Design shared schema, validate architecture decisions | Technical review |
| DevOps Agent | DevOps Team | Server-side VCS index deployment and monitoring | Infrastructure |
| QA Agent | Quality Assurance | Validate search accuracy, performance benchmarks | Testing |
| End Users (Developers) | All developers using Kiro | Benefit from faster, context-efficient agent responses | Indirect beneficiary |

---

## 5. Risks and Assumptions

### 5.1 Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Regex-based extraction misses complex signatures (generics, annotations, multi-line) | Medium | High | Start with common patterns, iterate; Kiro LSP can provide accurate parsing where available |
| SQLite database corruption on unexpected shutdown | High | Low | WAL mode + periodic integrity checks; reset = delete DB file |
| Ollama VRAM exhaustion when running embeddings + summarization concurrently | Medium | Medium | Sequential processing; rate limiting; batch size reduction under memory pressure |
| Large workspace (50,000+ files) causes slow initial indexing | Medium | Medium | Progressive indexing with priority (recently modified files first); configurable exclude patterns |
| Inconsistent behavior across 6 bridge clients | High | Medium | Shared schema + shared test suite; integration tests validate identical MCP responses |
| File watcher reliability varies across OS (Windows vs Linux vs macOS) | Medium | Medium | Use proven libraries (chokidar, WatchService); fallback to polling |
| SQLite FTS5 not available in older SQLite versions | Medium | Low | Check SQLite version at startup; minimum version requirement documented |
| Embedding model changes break stored vectors | Medium | Low | Store model name with embeddings; re-embed on model change |

### 5.2 Assumptions

- All target machines have SQLite available (built into Python, available as library for others)
- Ollama is optional — system must be fully functional with FTS5 only (Layer 1)
- Workspace size is typically < 20,000 files (enterprise monorepos are out of scope for v1)
- Developers have at least 1GB free disk space for SQLite database
- File system supports file watching (all modern OS do)
- Bridge clients already have MCP tool registration infrastructure
- `nomic-embed-text` and `qwen3:8b` models are pre-pulled on developer machines with Ollama
- Network access to Ollama is localhost only (no remote Ollama servers for v1)
- Content hash (SHA-256) is sufficient for change detection (no need for modification timestamps)

---

## 6. Non-Functional Requirements

| Category | Requirement | Details |
|----------|-------------|---------|
| Performance | Initial full scan < 60 seconds | For workspace with 5,000 files on SSD |
| Performance | Incremental scan < 5 seconds | Startup check when no files changed |
| Performance | Search query response < 200ms | FTS5 keyword search with 100,000+ symbols indexed |
| Performance | Semantic search response < 500ms | Embedding cosine similarity with 10,000 vectors |
| Performance | Single file re-index < 100ms | After file save event |
| Resource Usage | Memory < 256MB (Kotlin/Python) | During active indexing of 10,000-file workspace |
| Resource Usage | Memory < 512MB (Node.js) | Including worker thread overhead |
| Resource Usage | Database size < 50MB | For 10,000-file workspace with embeddings |
| Resource Usage | CPU usage < 25% sustained | Background indexing should not impact IDE responsiveness |
| Reliability | Graceful degradation | Each layer independent — failure in Layer 2/3 does not affect Layer 1 |
| Reliability | Crash recovery | Database survives unexpected shutdown (WAL mode) |
| Reliability | Atomic updates | File + symbols updated in single transaction |
| Scalability | Support up to 20,000 files | Per workspace, with acceptable performance |
| Scalability | Support up to 200,000 symbols | Total indexed symbols per workspace |
| Compatibility | Cross-platform | Windows, macOS, Linux for all Tier 1 clients |
| Compatibility | Consistent MCP interface | All bridge clients return identical JSON response schemas |
| Availability | Offline capable | Layer 1 (FTS5) works without any network access |
| Availability | Non-blocking | Indexing never blocks agent MCP tool responses |
| Data Integrity | Content hash verification | SHA-256 ensures accurate change detection |
| Data Integrity | Isolation | Per-workspace database — no cross-workspace data leakage |
| Security | Local only | Database stored locally, no data transmitted externally |
| Security | No secrets indexed | Scanner excludes `.env`, credential files, private keys |
| Observability | Index status tool | `code_index_status` provides real-time health metrics |
| Observability | Logging | All indexing operations logged with timing and error details |

---

## 7. Related Tickets

| Ticket Key | Summary | Status | Type | Relationship |
|------------|---------|--------|------|--------------|
| MTO-120 | [Bridge] Local Code Intelligence — SQLite Index + Semantic Search Across All Bridge Clients | Planned | Epic | Main epic |
| MTO-120-1 | SQLite Schema + FTS5 Setup | Planned | Story | Subtask of MTO-120 |
| MTO-120-2 | File Scanner + Signature Extractor | Planned | Story | Subtask of MTO-120 |
| MTO-120-3 | SQLite Storage + Query Layer | Planned | Story | Subtask of MTO-120 |
| MTO-120-4 | MCP Tools Implementation | Planned | Story | Subtask of MTO-120 |
| MTO-120-5 | Ollama Embedding Integration | Planned | Story | Subtask of MTO-120 |
| MTO-120-6 | AI Summarization | Planned | Story | Subtask of MTO-120 |
| MTO-120-7 | Background Indexing + File Watcher | Planned | Story | Subtask of MTO-120 |
| MTO-120-8 | Kotlin Bridge Implementation | Planned | Story | Subtask of MTO-120 |
| MTO-120-9 | Node.js Bridge Implementation | Planned | Story | Subtask of MTO-120 |
| MTO-120-10 | Python Bridge Implementation | Planned | Story | Subtask of MTO-120 |
| MTO-120-11 | PowerShell + Bash Bridge Implementation | Planned | Story | Subtask of MTO-120 |
| MTO-120-12 | Server-side VCS Index | Planned | Story | Subtask of MTO-120 |

---

## 8. Appendix

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Agent Layer                                │
│  BA Agent │ TA Agent │ SA Agent │ DEV Agent │ QA Agent           │
└─────────────────────────┬───────────────────────────────────────┘
                          │ MCP Protocol
┌─────────────────────────▼───────────────────────────────────────┐
│                     MCP Tools Interface                           │
│  code_search │ code_symbols │ code_context │ code_modules │ status│
└─────────────────────────┬───────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────┐
│                    Bridge Client Core                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ File Scanner │  │ Query Layer  │  │ Background Indexer    │  │
│  │ + Extractor  │  │ (FTS5/Vector)│  │ + File Watcher        │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘  │
│         │                  │                      │              │
│  ┌──────▼──────────────────▼──────────────────────▼───────────┐ │
│  │              SQLite Database (WAL mode)                      │ │
│  │  ┌────────┐  ┌─────────┐  ┌─────────┐  ┌──────────────┐  │ │
│  │  │ files  │  │ symbols │  │ modules │  │ embeddings   │  │ │
│  │  └────────┘  └─────────┘  └─────────┘  └──────────────┘  │ │
│  │  ┌────────────────────────────────────────────────────────┐│ │
│  │  │ FTS5 Virtual Table (full-text search index)            ││ │
│  │  └────────────────────────────────────────────────────────┘│ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                          │ (Optional)
┌─────────────────────────▼───────────────────────────────────────┐
│                    Ollama (localhost:11434)                        │
│  ┌─────────────────────┐  ┌────────────────────────────────┐   │
│  │ nomic-embed-text    │  │ qwen3:8b                       │   │
│  │ (768-dim vectors)   │  │ (summarization)                │   │
│  └─────────────────────┘  └────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Client Tier Capabilities Matrix

| Capability | Kotlin (T1) | Node.js (T1) | Python (T1) | PowerShell (T2) | Bash (T2) | CMD (T3) |
|------------|:-----------:|:------------:|:-----------:|:---------------:|:---------:|:---------:|
| File Scanning | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Signature Extraction | ✅ | ✅ | ✅ | ✅ (simplified) | ✅ (simplified) | ❌ |
| SQLite FTS5 | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| code_search | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| code_symbols | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| code_context | ✅ (semantic) | ✅ (semantic) | ✅ (semantic) | ✅ (FTS5 only) | ✅ (FTS5 only) | ❌ |
| code_modules | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| code_index_status | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Ollama Embeddings | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| AI Summarization | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Background Indexing | ✅ (coroutines) | ✅ (worker thread) | ✅ (asyncio) | ✅ (jobs) | ✅ (bg process) | ❌ |
| File Watcher | ✅ (WatchService) | ✅ (chokidar) | ✅ (watchdog) | ✅ (FSWatcher) | ✅ (inotifywait) | ❌ |

### SQLite Schema DDL

```sql
-- Schema version tracking
CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Files table
CREATE TABLE IF NOT EXISTS files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    path TEXT UNIQUE NOT NULL,
    language TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    last_indexed TEXT NOT NULL,
    module_id INTEGER REFERENCES modules(id)
);

-- Symbols table
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

-- Modules table
CREATE TABLE IF NOT EXISTS modules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    path TEXT NOT NULL,
    description TEXT,
    summary TEXT,
    embedding BLOB
);

-- Embeddings table (Layer 2)
CREATE TABLE IF NOT EXISTS embeddings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
    vector BLOB NOT NULL,
    text_summary TEXT NOT NULL,
    model TEXT NOT NULL DEFAULT 'nomic-embed-text',
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- FTS5 virtual table
CREATE VIRTUAL TABLE IF NOT EXISTS symbols_fts USING fts5(
    name,
    signature,
    file_path,
    module_name,
    content='symbols',
    content_rowid='id',
    tokenize='porter unicode61'
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_files_language ON files(language);
CREATE INDEX IF NOT EXISTS idx_files_module ON files(module_id);
CREATE INDEX IF NOT EXISTS idx_symbols_file ON symbols(file_id);
CREATE INDEX IF NOT EXISTS idx_symbols_kind ON symbols(kind);
CREATE INDEX IF NOT EXISTS idx_symbols_name ON symbols(name);
CREATE INDEX IF NOT EXISTS idx_embeddings_file ON embeddings(file_id);
```

### Supported Language Patterns

| Language | Extensions | Signature Patterns |
|----------|-----------|-------------------|
| Kotlin | .kt, .kts | `class`, `interface`, `object`, `fun`, `val`, `var`, `enum class`, `sealed class`, `data class` |
| Java | .java | `class`, `interface`, `enum`, `public/private/protected` methods |
| TypeScript | .ts, .tsx | `export class`, `export function`, `export const`, `interface`, `type`, `enum` |
| JavaScript | .js, .jsx, .mjs | `export class`, `export function`, `export const`, `module.exports` |
| Python | .py | `class`, `def`, `async def`, decorated functions |
| Go | .go | `func`, `type`, `struct`, `interface` |
| Rust | .rs | `fn`, `pub fn`, `struct`, `enum`, `trait`, `impl` |
| Bash | .sh | `function name()`, `name()` |
| PowerShell | .ps1, .psm1 | `function Verb-Noun`, `filter`, `class` |

### Glossary

| Term | Definition |
|------|------------|
| FTS5 | Full-Text Search version 5 — SQLite extension for efficient text search with BM25 ranking |
| Bridge Client | Language-specific MCP client that connects agents to tools and workspace |
| Tier 1 | Full-capability bridge clients (Kotlin, Node.js, Python) with all 3 layers |
| Tier 2 | Basic bridge clients (PowerShell, Bash) with Layer 1 only |
| Layer 1 | FTS5 keyword search — always available, no external dependencies |
| Layer 2 | Ollama embedding-based semantic search — optional, requires Ollama |
| Layer 3 | AI-powered summarization — optional, requires Ollama with qwen3:8b |
| WAL Mode | Write-Ahead Logging — SQLite journaling mode enabling concurrent reads during writes |
| Content Hash | SHA-256 hash of file content used for incremental change detection |
| Signature | Code declaration line (function/class/interface definition) extracted by regex |
| Cosine Similarity | Vector distance metric used for semantic search (closer to 1.0 = more similar) |
| nomic-embed-text | Open-source embedding model producing 768-dimensional vectors |
| qwen3:8b | 8-billion parameter language model used for code summarization |
| WorkspaceContext | Bridge client component that manages workspace root and configuration |

### Reference Documents

| Document | Link / Location |
|----------|-----------------|
| Backlog Document | documents/BACKLOG-bridge-code-intelligence.md |
| Architecture Overview | docs/diagrams/architecture-overview.drawio |
| Bridge Clients Diagram | docs/diagrams/bridge-clients.drawio |
| Kotlin Bridge Source | mcp-client-bridge/src/main/kotlin/ |
| Node.js Bridge Source | mcp-client-bridge/ (package.json) |
| Python Bridge Source | mcp-bridge-python/ |
| BRD Template | documents/templates/BRD-TEMPLATE.md |
| Ollama API Documentation | https://github.com/ollama/ollama/blob/main/docs/api.md |
| SQLite FTS5 Documentation | https://www.sqlite.org/fts5.html |
| nomic-embed-text Model | https://ollama.com/library/nomic-embed-text |
