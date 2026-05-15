# BACKLOG: [Bridge] Local Code Intelligence — SQLite Index + Semantic Search

**Priority:** High
**Labels:** bridge, code-intelligence, sqlite, ai
**Status:** Pending Jira creation

---

## Epic Summary

Thêm local code index vào bridge clients sử dụng SQLite (FTS5 + optional vector embeddings). Hybrid approach cho phép agents search code semantically mà không tốn context window.

## Architecture

```
Kiro IDE / Kiro CLI
  ↓ (workspace files)
Bridge Client
  ├── File Scanner (detect language, extract signatures)
  ├── SQLite DB (FTS5 + optional vector)
  ├── Ollama Client (optional, for embeddings)
  └── MCP Tools: code_search, code_symbols, code_context
  ↓ (MCP protocol)
Agents (BA, TA, SA, DEV)
  → Query local index → instant, context-efficient responses
```

## Client Tiers

| Tier | Clients | Capabilities |
|------|---------|-------------|
| Tier 1 (Full) | Kotlin, Node.js, Python | SQLite + FTS5 + Embeddings + AI Summary |
| Tier 2 (Basic) | PowerShell, Bash | SQLite FTS5 only (keyword search) |
| Tier 3 (Skip) | CMD | Too limited — no local index |

## Hybrid Layers

- **Layer 1 (always):** File scan + signature extraction + SQLite FTS5 → keyword search
- **Layer 2 (optional):** Ollama embeddings (nomic-embed-text) → semantic search
- **Layer 3 (optional):** AI summarization (qwen3:8b) → module/class summaries

## Stories

### Story 1: SQLite Schema + FTS5 Setup
- Define shared SQLite schema (tables: files, symbols, modules)
- FTS5 virtual table for full-text search
- Schema versioning (migrations)
- Same schema across all Tier 1+2 clients

### Story 2: File Scanner + Signature Extractor
- Scan workspace (respect .gitignore, exclude patterns)
- Language detection (extension-based)
- Regex-based signature extraction per language:
  - Kotlin/Java: class, interface, fun, val/var
  - TypeScript/JS: export class, export function, const
  - Python: class, def
  - Go: func, type, struct
- Content hashing for change detection (incremental)

### Story 3: SQLite Storage + Query Layer
- Insert/update/delete file entries
- FTS5 search (keyword matching)
- Query by: module, file, symbol name, language
- Incremental update (only changed files)

### Story 4: MCP Tools — code_search, code_symbols, code_context
- `code_search(query, language?, module?)` — FTS5 keyword search
- `code_symbols(file_path)` — list symbols in a file
- `code_context(query, top_k?)` — semantic search (if embeddings available)
- `code_modules()` — list all modules with summaries
- `code_index_status()` — index health, file count, last indexed

### Story 5: Ollama Embedding Integration (Optional Layer 2)
- Check Ollama availability on startup
- Generate embeddings for file summaries (nomic-embed-text)
- Store in SQLite (vector column or separate table)
- Cosine similarity search
- Graceful degradation: if Ollama unavailable → FTS5 only

### Story 6: AI Summarization (Optional Layer 3)
- Background job: summarize modules/classes using qwen3:8b
- Cache summaries in SQLite
- Refresh on file change
- Used by code_context tool for rich responses

### Story 7: Background Indexing + File Watcher
- Index on bridge startup (full scan first time, incremental after)
- Watch file changes (create/edit/delete) → update index
- Non-blocking (background coroutine/thread)
- Progress reporting via code_index_status tool

### Story 8: Kotlin Bridge Implementation (Tier 1)
- SQLite via JDBC (sqlite-jdbc)
- Ktor HTTP client for Ollama
- Coroutine-based background indexing
- Integration with existing WorkspaceContext

### Story 9: Node.js Bridge Implementation (Tier 1)
- SQLite via better-sqlite3
- fetch API for Ollama
- Worker thread for background indexing

### Story 10: Python Bridge Implementation (Tier 1)
- SQLite via stdlib sqlite3
- httpx for Ollama
- asyncio background task

### Story 11: PowerShell Bridge Implementation (Tier 2)
- SQLite via System.Data.SQLite or sqlite3.exe CLI
- FTS5 only (no embeddings)
- Simplified scanner

### Story 12: Bash Bridge Implementation (Tier 2)
- SQLite via sqlite3 CLI
- FTS5 only
- Shell-based file scanning (find + grep)

## Key Design Decisions

1. **SQLite per-workspace** — isolation by default, reset = delete .db file
2. **Hybrid layers** — FTS5 always works, AI optional (graceful degradation)
3. **Shared schema** — same tables/indexes across all clients
4. **Same MCP tool interface** — agents don't know which client they're talking to
5. **Background indexing** — never blocks IDE or agent operations
6. **Incremental updates** — content hash for change detection
7. **Kiro CLI/LSP leverage** — where available, use for accurate parsing
8. **Ollama shared** — same instance used by kb-server (qwen3:8b, nomic-embed-text)

## Value Proposition

- **100x less context** consumption for agents
- **Semantic search** on local code ("auth" finds "login", "credential")
- **Persistent knowledge** across sessions (SQLite survives restart)
- **Per-user isolation** (each user's SQLite is independent)
- **Offline capable** (FTS5 works without network)
- **Multi-language** (regex extraction works for any language)

## Dependencies

- SQLite library per language (sqlite-jdbc, better-sqlite3, stdlib sqlite3)
- Ollama (optional — for embeddings and summarization)
- Existing bridge client infrastructure (WorkspaceContext, MCP tool registration)
- File system access (already available in all clients)

## Server-Side Complement

KB Server indexes code from **VCS (git)** — authoritative, shared, for cross-team reference.
Bridge Client indexes code from **local workspace** — per-user, fast, for active development.

Both coexist:
- Agent creating BRD/FSD → uses server KB (authoritative code from main branch)
- Agent implementing code → uses local index (includes uncommitted changes)
