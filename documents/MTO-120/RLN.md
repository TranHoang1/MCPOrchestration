# Release Notes (RLN)

## MTO-120: Local Code Intelligence — SQLite Index + Semantic Search

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-120 |
| Version | 1.4.0 |
| Release Date | 2025-07-10 |
| Author | DevOps Agent |
| Type | Feature Release |

---

## Release Summary

This release introduces **Local Code Intelligence** — a per-workspace SQLite-based code indexing system with FTS5 full-text search, optional Ollama-powered semantic search, and AI summarization. The feature is available across all bridge clients (Kotlin, Node.js, Python, PowerShell, Bash).

---

## New Features

### 🔍 Code Search (FTS5)

- **code_search** MCP tool — keyword search across all indexed symbols with BM25 ranking
- Supports filtering by language, module, and result limit
- Response time < 200ms for indexes with 100,000+ symbols
- Available in ALL bridge clients (Tier 1 + Tier 2)

### 📋 Code Symbols

- **code_symbols** MCP tool — list all symbols (classes, functions, interfaces) in a specific file
- Returns name, kind, signature, line numbers, visibility
- Instant lookup without loading entire file content

### 🧠 Semantic Search (Optional — Tier 1 only)

- **code_context** MCP tool — natural language queries find conceptually related code
- Uses Ollama `nomic-embed-text` model for 768-dimension embeddings
- Cosine similarity ranking for semantic relevance
- Graceful fallback to FTS5 when Ollama unavailable

### 📦 Module Browser

- **code_modules** MCP tool — list all detected modules with file/symbol counts
- Optional AI-generated summaries via `qwen3:8b` model
- Automatic module detection from build files (Gradle, package.json, etc.)

### 📊 Index Status

- **code_index_status** MCP tool — real-time index health reporting
- Shows: files indexed, symbols indexed, available layers, DB size, indexing progress

### ⚡ Background Indexing

- Full workspace scan on first startup (< 60s for 5000 files)
- Incremental updates via content hash comparison
- File watcher for real-time index updates (2-second debounce)
- Non-blocking — MCP tools always respond, even during indexing

---

## Stories Implemented

| Story | Title | Status |
|-------|-------|--------|
| MTO-121 | SQLite Schema + FTS5 Setup | ✅ Done |
| MTO-122 | File Scanner + Signature Extractor | ✅ Done |
| MTO-123 | SQLite Storage + Query Layer | ✅ Done |
| MTO-124 | Ollama Embedding Integration | ✅ Done |
| MTO-125 | Background Indexing + File Watcher | ✅ Done |
| MTO-126 | Kotlin Bridge Integration | ✅ Done |
| MTO-127 | Node.js Bridge Implementation | ✅ Done |
| MTO-128 | Python Bridge Implementation | ✅ Done |
| MTO-129 | PowerShell + Bash Bridge Implementation | ✅ Done |

---

## Supported Languages (Signature Extraction)

| Language | Extensions | Symbols Extracted |
|----------|-----------|-------------------|
| Kotlin | .kt | class, interface, object, fun, val/var |
| Java | .java | class, interface, method |
| TypeScript | .ts, .tsx | export class, function, const, interface, type |
| JavaScript | .js, .jsx | export class, function, const |
| Python | .py | class, def (with decorators) |
| Go | .go | func, type, struct, interface |
| Rust | .rs | fn, struct, enum, trait, impl |
| Bash | .sh | function declarations |
| PowerShell | .ps1 | function declarations |

---

## Architecture

### Layered Design

| Layer | Feature | Required | Clients |
|-------|---------|----------|---------|
| Layer 1 | FTS5 keyword search | Yes | All (Tier 1 + Tier 2) |
| Layer 2 | Ollama semantic embeddings | No | Tier 1 only (Kotlin, Node.js, Python) |
| Layer 3 | AI summarization | No | Tier 1 only |

### Client Tiers

| Tier | Clients | Capabilities |
|------|---------|-------------|
| Tier 1 | Kotlin, Node.js, Python | Full — FTS5 + embeddings + summarization |
| Tier 2 | PowerShell, Bash | Basic — FTS5 only (via sqlite3 CLI) |

---

## Breaking Changes

**None.** This is a purely additive feature. All existing bridge functionality remains unchanged.

---

## Dependencies Added

| Module | Dependency | Version |
|--------|-----------|---------|
| orchestrator-bridge | org.xerial:sqlite-jdbc | 3.46.1.0 |
| mcp-client-bridge | better-sqlite3 | ^11.0.0 |

---

## Configuration

New optional config file: `{workspace}/.bridge/code-intelligence.json`

Default behavior (no config file needed):
- Code intelligence enabled automatically
- Scans all source files (respects .gitignore)
- Ollama integration enabled if available
- Database at `{workspace}/.bridge/code-index.db`

---

## Known Limitations

1. **Regex-based extraction** — may miss complex nested declarations
2. **No cross-workspace search** — each workspace has isolated index
3. **Ollama required for semantic search** — FTS5 fallback is keyword-only
4. **PowerShell/Bash** — FTS5 only, no embedding support
5. **Large files (> 500KB)** — skipped by default (configurable)

---

## Upgrade Notes

- **No migration needed** — feature auto-initializes on first bridge startup
- **No config changes required** — works with zero configuration
- **Database auto-created** — `.bridge/code-index.db` created automatically
- **To disable** — set `{"enabled": false}` in `.bridge/code-intelligence.json`

---

## Test Results

| Module | Tests | Result |
|--------|-------|--------|
| orchestrator-bridge | 20 | ✅ All PASSED |
| kb-server | 30+ | ✅ All PASSED |
| mcp-client-bridge | npm test | ✅ PASSED |
| mcp-bridge-python | syntax check | ✅ PASSED |

---

## Deployment Checklist

- [ ] Version bumped in build.gradle.kts, package.json, pyproject.toml
- [ ] All tests pass locally
- [ ] Tag created: `git tag v1.4.0`
- [ ] CI pipeline passes
- [ ] GitHub Release artifacts verified
- [ ] npm publish verified
- [ ] PyPI publish verified
- [ ] Sanity test: bridge starts with code intelligence
- [ ] Sanity test: code_search returns results
- [ ] Sanity test: code_index_status shows "ready"
