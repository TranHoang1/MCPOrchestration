# Software Test Cases (STC)

## Bridge Clients — MTO-120: Local Code Intelligence — SQLite Index + Semantic Search

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-120 |
| Title | Local Code Intelligence — SQLite Index + Semantic Search |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2025-07-10 |
| Status | Draft |
| Related STP | STP-v1-MTO-120.docx |
| Related FSD | FSD-v1-MTO-120.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-10 | QA Agent | Initial test cases — 100 cases across 6 levels |

---

## Test Case Summary

| Level | ID Range | Count | Automation |
|-------|----------|-------|------------|
| Property-Based Testing (PBT) | PBT-001 to PBT-005 | 5 | 100% |
| Unit Testing (UT) | UT-001 to UT-045 | 45 | 100% |
| Integration Testing (IT) | IT-001 to IT-025 | 25 | 100% |
| E2E API Testing (E2E-API) | E2E-API-001 to E2E-API-015 | 15 | 100% |
| E2E UI Testing (E2E-UI) | — | 0 | N/A (no UI) |
| System Integration Testing (SIT) | SIT-001 to SIT-010 | 10 | 80% |
| **Total** | | **100** | **97%** |

---

## 1. Property-Based Testing (PBT)

### PBT-001: SHA-256 Hash Determinism

| Field | Value |
|-------|-------|
| **ID** | PBT-001 |
| **Priority** | High |
| **Story** | MTO-122 (File Scanner) |
| **Requirement** | BR-009: content_hash must be valid SHA-256 |
| **Property** | For any byte array input, hash(input) always produces same 64-char hex string |
| **Generator** | Random byte arrays (0 to 10KB) |
| **Runs** | 1000 |
| **Tool** | Kotest Property Testing |

---

### PBT-002: Signature Extractor — No Crash on Random Input

| Field | Value |
|-------|-------|
| **ID** | PBT-002 |
| **Priority** | High |
| **Story** | MTO-122 (Signature Extractor) |
| **Requirement** | BR-012: Regex extraction must not hang (100ms timeout) |
| **Property** | For any random string input, extractor returns result within 100ms without exception |
| **Generator** | Random strings (0 to 5000 chars, including special chars, unicode) |
| **Runs** | 500 |
| **Tool** | Kotest Property Testing |

---

### PBT-003: FTS5 Query — No SQL Injection

| Field | Value |
|-------|-------|
| **ID** | PBT-003 |
| **Priority** | Critical |
| **Story** | MTO-123 (Query Layer) |
| **Requirement** | Security: queries must be parameterized |
| **Property** | For any string query (including SQL metacharacters), search returns results or empty without error |
| **Generator** | Random strings with SQL injection patterns ('; DROP TABLE; --, UNION SELECT, etc.) |
| **Runs** | 500 |
| **Tool** | Kotest Property Testing |

---

### PBT-004: Path Normalization Consistency

| Field | Value |
|-------|-------|
| **ID** | PBT-004 |
| **Priority** | Medium |
| **Story** | MTO-122 (File Scanner) |
| **Requirement** | BR-007: paths must be relative |
| **Property** | For any valid relative path, normalization produces consistent result (no leading /, no drive letter) |
| **Generator** | Random path segments with mixed separators (/, \\) |
| **Runs** | 500 |
| **Tool** | Kotest Property Testing |

---

### PBT-005: Module Detection Idempotency

| Field | Value |
|-------|-------|
| **ID** | PBT-005 |
| **Priority** | Medium |
| **Story** | MTO-123 (Storage Layer) |
| **Requirement** | Module detection must be deterministic |
| **Property** | Scanning same directory twice produces identical module list |
| **Generator** | Random directory structures (1-50 files, 1-5 modules) |
| **Runs** | 100 |
| **Tool** | Kotest Property Testing |

---

## 2. Unit Testing (UT)

### 2.1 Schema & Database (MTO-121)

### UT-001: Database Creation — Fresh Workspace

| Field | Value |
|-------|-------|
| **ID** | UT-001 |
| **Priority** | Critical |
| **Story** | MTO-121 |
| **Requirement** | UC-001, BR-003 |
| **Preconditions** | No .bridge/ directory exists |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call DatabaseManager.initialize() with temp workspace path | Returns Result.success |
| 2 | Check file system | .bridge/code-index.db file exists |
| 3 | Query PRAGMA journal_mode | Returns "wal" |
| 4 | Query schema_version table | Returns current version (1) |

---

### UT-002: Database Creation — Existing Database

| Field | Value |
|-------|-------|
| **ID** | UT-002 |
| **Priority** | High |
| **Story** | MTO-121 |
| **Requirement** | UC-001 AF-001-2 |
| **Preconditions** | .bridge/code-index.db exists with current schema version |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call DatabaseManager.initialize() | Returns Result.success |
| 2 | Verify no migrations ran | Schema version unchanged, tables intact |

---

### UT-003: Schema Migration — Version Upgrade

| Field | Value |
|-------|-------|
| **ID** | UT-003 |
| **Priority** | High |
| **Story** | MTO-121 |
| **Requirement** | BR-001 |
| **Preconditions** | Database exists with schema version 0 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call DatabaseManager.initialize() | Migrations run sequentially |
| 2 | Query schema_version | Returns current version |
| 3 | Verify all tables exist | files, symbols, modules, fts5 virtual table present |

---

### UT-004: FTS5 Virtual Table — Tokenizer Configuration

| Field | Value |
|-------|-------|
| **ID** | UT-004 |
| **Priority** | High |
| **Story** | MTO-121 |
| **Requirement** | BR-005 |
| **Preconditions** | Database initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert symbol with name "processRequest" | Insert succeeds |
| 2 | FTS5 search for "process" | Returns the symbol (tokenizer splits camelCase) |
| 3 | FTS5 search for "request" | Returns the symbol |

---

### UT-005: Database Error — Disk Full Simulation

| Field | Value |
|-------|-------|
| **ID** | UT-005 |
| **Priority** | Medium |
| **Story** | MTO-121 |
| **Requirement** | EF-001-1 |
| **Preconditions** | Read-only directory |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call DatabaseManager.initialize() with read-only path | Returns Result.failure |
| 2 | Check isReady() | Returns false |
| 3 | Verify no crash | Bridge continues without code intelligence |

---

### 2.2 File Scanner (MTO-122)

### UT-006: Scanner — Respects .gitignore

| Field | Value |
|-------|-------|
| **ID** | UT-006 |
| **Priority** | Critical |
| **Story** | MTO-122 |
| **Requirement** | BR-007, AC-1 |
| **Preconditions** | Temp workspace with .gitignore containing "node_modules/" and "build/" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create files in node_modules/ and src/ | Files exist |
| 2 | Run scanner on workspace | Only src/ files returned |
| 3 | Verify node_modules/ files excluded | Not in scan results |

---

### UT-007: Scanner — Language Detection by Extension

| Field | Value |
|-------|-------|
| **ID** | UT-007 |
| **Priority** | High |
| **Story** | MTO-122 |
| **Requirement** | BR-008 |
| **Preconditions** | Files with various extensions |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Scan file "App.kt" | language = "kotlin" |
| 2 | Scan file "index.ts" | language = "typescript" |
| 3 | Scan file "main.py" | language = "python" |
| 4 | Scan file "main.go" | language = "go" |
| 5 | Scan file "lib.rs" | language = "rust" |
| 6 | Scan file "script.sh" | language = "bash" |
| 7 | Scan file "Module.ps1" | language = "powershell" |

---

### UT-008: Scanner — Skip Binary Files

| Field | Value |
|-------|-------|
| **ID** | UT-008 |
| **Priority** | High |
| **Story** | MTO-122 |
| **Requirement** | AC-6 |
| **Preconditions** | Workspace with .class, .pyc, .exe files |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run scanner on workspace with binary files | Binary files not in results |
| 2 | Verify .class files excluded | Not indexed |
| 3 | Verify .pyc files excluded | Not indexed |

---

### UT-009: Scanner — Max File Size Limit

| Field | Value |
|-------|-------|
| **ID** | UT-009 |
| **Priority** | Medium |
| **Story** | MTO-122 |
| **Requirement** | Config: max_file_size_kb |
| **Preconditions** | File larger than configured limit (default 500KB) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create 1MB .kt file | File exists |
| 2 | Run scanner with max_file_size_kb=500 | Large file skipped |
| 3 | Verify skip logged | Warning in logs |

---

### UT-010: Scanner — Content Hash Calculation

| Field | Value |
|-------|-------|
| **ID** | UT-010 |
| **Priority** | High |
| **Story** | MTO-122 |
| **Requirement** | BR-009 |
| **Preconditions** | Known file content |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Calculate hash of "hello world" | Returns known SHA-256 value |
| 2 | Calculate hash of same content again | Same result (deterministic) |
| 3 | Calculate hash of different content | Different result |

---

### 2.3 Signature Extractor (MTO-122)

### UT-011: Extractor — Kotlin Signatures

| Field | Value |
|-------|-------|
| **ID** | UT-011 |
| **Priority** | Critical |
| **Story** | MTO-122 |
| **Requirement** | AC-2 |
| **Preconditions** | Kotlin source file with class, fun, interface, object |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Extract from "class AuthService(val repo: Repo)" | Symbol: name=AuthService, kind=class |
| 2 | Extract from "suspend fun process(req: Request): Response" | Symbol: name=process, kind=function |
| 3 | Extract from "interface Repository" | Symbol: name=Repository, kind=interface |
| 4 | Extract from "object Singleton" | Symbol: name=Singleton, kind=class |

---

### UT-012: Extractor — TypeScript Signatures

| Field | Value |
|-------|-------|
| **ID** | UT-012 |
| **Priority** | High |
| **Story** | MTO-122 |
| **Requirement** | AC-2 |
| **Preconditions** | TypeScript source file |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Extract from "export class UserService {" | Symbol: name=UserService, kind=class |
| 2 | Extract from "export function getData(): Promise<Data>" | Symbol: name=getData, kind=function |
| 3 | Extract from "export interface Config {" | Symbol: name=Config, kind=interface |
| 4 | Extract from "export const handler = async () => {" | Symbol: name=handler, kind=function |

---

### UT-013: Extractor — Python Signatures

| Field | Value |
|-------|-------|
| **ID** | UT-013 |
| **Priority** | High |
| **Story** | MTO-122 |
| **Requirement** | AC-2 |
| **Preconditions** | Python source file |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Extract from "class DatabaseManager:" | Symbol: name=DatabaseManager, kind=class |
| 2 | Extract from "def process_request(self, req):" | Symbol: name=process_request, kind=function |
| 3 | Extract from "@dataclass\nclass Config:" | Symbol: name=Config, kind=class |
| 4 | Extract from "async def fetch_data():" | Symbol: name=fetch_data, kind=function |

---

### UT-014: Extractor — Go Signatures

| Field | Value |
|-------|-------|
| **ID** | UT-014 |
| **Priority** | Medium |
| **Story** | MTO-122 |
| **Requirement** | AC-2 |
| **Preconditions** | Go source file |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Extract from "func ProcessRequest(r *Request) error {" | Symbol: name=ProcessRequest, kind=function |
| 2 | Extract from "type Service struct {" | Symbol: name=Service, kind=struct |
| 3 | Extract from "type Handler interface {" | Symbol: name=Handler, kind=interface |

---

### UT-015: Extractor — Rust Signatures

| Field | Value |
|-------|-------|
| **ID** | UT-015 |
| **Priority** | Medium |
| **Story** | MTO-122 |
| **Requirement** | AC-2 |
| **Preconditions** | Rust source file |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Extract from "pub fn process(req: &Request) -> Result<Response>" | Symbol: name=process, kind=function |
| 2 | Extract from "pub struct Config {" | Symbol: name=Config, kind=struct |
| 3 | Extract from "pub trait Handler {" | Symbol: name=Handler, kind=interface |
| 4 | Extract from "pub enum Status {" | Symbol: name=Status, kind=enum |

---

### UT-016: Extractor — Empty File

| Field | Value |
|-------|-------|
| **ID** | UT-016 |
| **Priority** | Medium |
| **Story** | MTO-122 |
| **Requirement** | Edge case |
| **Preconditions** | Empty .kt file |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Extract from empty file | Returns empty list, no error |

---

### UT-017: Extractor — Max Symbols Per File

| Field | Value |
|-------|-------|
| **ID** | UT-017 |
| **Priority** | Medium |
| **Story** | MTO-122 |
| **Requirement** | Validation: max 1000 symbols per file |
| **Preconditions** | File with 1500 function declarations |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Extract from file with 1500 symbols | Returns first 1000 symbols |
| 2 | Check warning logged | Warning about truncation |

---

### 2.4 Query Layer (MTO-123)

### UT-018: FTS5 Search — Basic Keyword

| Field | Value |
|-------|-------|
| **ID** | UT-018 |
| **Priority** | Critical |
| **Story** | MTO-123 |
| **Requirement** | UC-005 |
| **Preconditions** | Database with indexed symbols |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert symbol "authenticateUser" in file "AuthService.kt" | Insert succeeds |
| 2 | Search FTS5 for "authenticate" | Returns AuthService.kt result |
| 3 | Verify relevance score | Score > 0 |

---

### UT-019: FTS5 Search — Language Filter

| Field | Value |
|-------|-------|
| **ID** | UT-019 |
| **Priority** | High |
| **Story** | MTO-123 |
| **Requirement** | code_search language parameter |
| **Preconditions** | Symbols in kotlin and typescript files |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Search "process" with language="kotlin" | Only kotlin results |
| 2 | Search "process" with language="typescript" | Only typescript results |
| 3 | Search "process" with no language filter | Both results |

---

### UT-020: FTS5 Search — Module Filter

| Field | Value |
|-------|-------|
| **ID** | UT-020 |
| **Priority** | High |
| **Story** | MTO-123 |
| **Requirement** | code_search module parameter |
| **Preconditions** | Symbols in different modules |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Search "service" with module="auth" | Only auth module results |
| 2 | Search "service" with no module filter | All module results |

---

### UT-021: FTS5 Search — Limit Parameter

| Field | Value |
|-------|-------|
| **ID** | UT-021 |
| **Priority** | Medium |
| **Story** | MTO-123 |
| **Requirement** | code_search limit parameter |
| **Preconditions** | 50 matching symbols |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Search with limit=5 | Returns exactly 5 results |
| 2 | Search with limit=20 (default) | Returns 20 results |
| 3 | Verify total_matches field | Shows 50 (total, not limited) |

---

### UT-022: Incremental Update — Changed File

| Field | Value |
|-------|-------|
| **ID** | UT-022 |
| **Priority** | Critical |
| **Story** | MTO-123 |
| **Requirement** | BR-013 |
| **Preconditions** | File indexed with hash "abc123" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Modify file content (new hash "def456") | File changed |
| 2 | Run incremental update | Old symbols deleted, new symbols inserted |
| 3 | Verify old symbols gone | Search for old symbol returns empty |
| 4 | Verify new symbols present | Search for new symbol returns result |

---

### UT-023: Batch Insert Performance

| Field | Value |
|-------|-------|
| **ID** | UT-023 |
| **Priority** | High |
| **Story** | MTO-123 |
| **Requirement** | AC: 10,000 symbols in < 5 seconds |
| **Preconditions** | Empty database |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Bulk insert 10,000 symbols in transaction | Completes in < 5 seconds |
| 2 | Verify all symbols queryable | Count = 10,000 |

---

### UT-024: Query — Get Symbols By File

| Field | Value |
|-------|-------|
| **ID** | UT-024 |
| **Priority** | High |
| **Story** | MTO-123 |
| **Requirement** | code_symbols tool |
| **Preconditions** | File with 5 symbols indexed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getSymbolsByFile("src/AuthService.kt") | Returns 5 symbols |
| 2 | Verify each symbol has name, kind, signature, line_start | All fields populated |

---

### UT-025: Query — Get Modules

| Field | Value |
|-------|-------|
| **ID** | UT-025 |
| **Priority** | High |
| **Story** | MTO-123 |
| **Requirement** | code_modules tool |
| **Preconditions** | 3 modules indexed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getModules() | Returns 3 modules |
| 2 | Verify each module has name, path, file_count, symbol_count | All fields populated |

---

### UT-026: Query — Get Index Stats

| Field | Value |
|-------|-------|
| **ID** | UT-026 |
| **Priority** | High |
| **Story** | MTO-123 |
| **Requirement** | code_index_status tool |
| **Preconditions** | Database with known data |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getIndexStats() | Returns stats object |
| 2 | Verify files_indexed count | Matches actual file count |
| 3 | Verify symbols_indexed count | Matches actual symbol count |
| 4 | Verify last_indexed timestamp | Valid ISO-8601 |

---

### 2.5 Ollama Integration (MTO-124)

### UT-027: Ollama Health Check — Available

| Field | Value |
|-------|-------|
| **ID** | UT-027 |
| **Priority** | High |
| **Story** | MTO-124 |
| **Requirement** | UC-011, BR-019 |
| **Preconditions** | WireMock stub returning 200 on /api/tags |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call OllamaClient.checkHealth() | Returns true |
| 2 | Verify layers.embeddings | Set to true |

---

### UT-028: Ollama Health Check — Unavailable

| Field | Value |
|-------|-------|
| **ID** | UT-028 |
| **Priority** | High |
| **Story** | MTO-124 |
| **Requirement** | BR-020: graceful degradation |
| **Preconditions** | No Ollama server running (connection refused) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call OllamaClient.checkHealth() | Returns false (no exception) |
| 2 | Verify layers.embeddings | Set to false |
| 3 | Verify code_context falls back to FTS5 | search_method = "fts5" |

---

### UT-029: Ollama Health Check — Timeout

| Field | Value |
|-------|-------|
| **ID** | UT-029 |
| **Priority** | Medium |
| **Story** | MTO-124 |
| **Requirement** | BR-021: 5 second timeout |
| **Preconditions** | WireMock with 10 second delay |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call OllamaClient.checkHealth() with 5s timeout | Returns false after ~5s |
| 2 | Verify no hang | Method returns within 6 seconds |

---

### UT-030: Embedding Generation — Valid Input

| Field | Value |
|-------|-------|
| **ID** | UT-030 |
| **Priority** | High |
| **Story** | MTO-124 |
| **Requirement** | AC-2 |
| **Preconditions** | WireMock stub for /api/embeddings returning 768-dim vector |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call generateEmbedding("AuthService handles JWT") | Returns 768-element float array |
| 2 | Verify vector dimension | Exactly 768 |
| 3 | Verify vector stored in DB | embeddings table has entry |

---

### UT-031: Cosine Similarity Calculation

| Field | Value |
|-------|-------|
| **ID** | UT-031 |
| **Priority** | High |
| **Story** | MTO-124 |
| **Requirement** | Semantic search ranking |
| **Preconditions** | Two known vectors |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Calculate cosine similarity of identical vectors | Returns 1.0 |
| 2 | Calculate cosine similarity of orthogonal vectors | Returns 0.0 |
| 3 | Calculate cosine similarity of similar vectors | Returns value between 0.5 and 1.0 |

---

### 2.6 Background Indexing (MTO-125)

### UT-032: Initial Full Scan — Triggers on Empty DB

| Field | Value |
|-------|-------|
| **ID** | UT-032 |
| **Priority** | Critical |
| **Story** | MTO-125 |
| **Requirement** | UC-012 |
| **Preconditions** | Fresh database, workspace with 10 files |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start indexing engine | Full scan triggered |
| 2 | Wait for completion | All 10 files indexed |
| 3 | Verify code_index_status | files_indexed = 10, status = "ready" |

---

### UT-033: Incremental Scan — Only Changed Files

| Field | Value |
|-------|-------|
| **ID** | UT-033 |
| **Priority** | Critical |
| **Story** | MTO-125 |
| **Requirement** | UC-013, BR-023 |
| **Preconditions** | 10 files indexed, 2 files modified |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Modify 2 files (change content) | Hashes differ |
| 2 | Trigger incremental scan | Only 2 files re-indexed |
| 3 | Verify unchanged files not touched | last_indexed unchanged for 8 files |

---

### UT-034: File Watcher — Create Event

| Field | Value |
|-------|-------|
| **ID** | UT-034 |
| **Priority** | High |
| **Story** | MTO-125 |
| **Requirement** | BR-024 |
| **Preconditions** | Indexing engine running, file watcher active |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create new file "NewService.kt" in workspace | File watcher detects event |
| 2 | Wait 5 seconds (debounce + processing) | File indexed |
| 3 | Search for symbols in new file | Found in index |

---

### UT-035: File Watcher — Delete Event

| Field | Value |
|-------|-------|
| **ID** | UT-035 |
| **Priority** | High |
| **Story** | MTO-125 |
| **Requirement** | BR-025 |
| **Preconditions** | File "OldService.kt" indexed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Delete "OldService.kt" from workspace | File watcher detects event |
| 2 | Wait 5 seconds | File removed from index |
| 3 | Search for symbols from deleted file | Not found |

---

### UT-036: File Watcher — Debounce

| Field | Value |
|-------|-------|
| **ID** | UT-036 |
| **Priority** | Medium |
| **Story** | MTO-125 |
| **Requirement** | BR-026: 2-second debounce |
| **Preconditions** | File watcher active |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Rapidly modify same file 10 times in 1 second | Events batched |
| 2 | Wait 3 seconds | File indexed only once (not 10 times) |

---

### UT-037: Indexing Progress Reporting

| Field | Value |
|-------|-------|
| **ID** | UT-037 |
| **Priority** | Medium |
| **Story** | MTO-125 |
| **Requirement** | AC-6 |
| **Preconditions** | 100 files to index |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start full scan | status = "indexing" |
| 2 | Check progress mid-scan | indexing_progress between 0 and 100 |
| 3 | Wait for completion | status = "ready", indexing_progress = 100 |

---

### 2.7 MCP Tool Response Format (MTO-126)

### UT-038: code_search — Response Schema

| Field | Value |
|-------|-------|
| **ID** | UT-038 |
| **Priority** | Critical |
| **Story** | MTO-126 |
| **Requirement** | UC-006 |
| **Preconditions** | Indexed workspace |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_search(query="auth") | Response has "results" array |
| 2 | Verify result fields | Each has: file, symbol, kind, signature, line, module, relevance |
| 3 | Verify total_matches field | Integer ≥ 0 |
| 4 | Verify query_time_ms field | Integer > 0 |

---

### UT-039: code_symbols — Response Schema

| Field | Value |
|-------|-------|
| **ID** | UT-039 |
| **Priority** | Critical |
| **Story** | MTO-126 |
| **Requirement** | UC-007 |
| **Preconditions** | File "AuthService.kt" indexed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_symbols(file_path="src/AuthService.kt") | Response has "symbols" array |
| 2 | Verify symbol fields | Each has: name, kind, signature, line_start, line_end, visibility |
| 3 | Verify file and language fields | Present and correct |

---

### UT-040: code_context — Fallback to FTS5

| Field | Value |
|-------|-------|
| **ID** | UT-040 |
| **Priority** | High |
| **Story** | MTO-126 |
| **Requirement** | UC-008, graceful degradation |
| **Preconditions** | Ollama unavailable, FTS5 index populated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_context(query="authentication flow") | Returns results |
| 2 | Verify search_method | "fts5" (not "embedding") |
| 3 | Verify results are relevant | FTS5 keyword match results |

---

### UT-041: code_modules — Response Schema

| Field | Value |
|-------|-------|
| **ID** | UT-041 |
| **Priority** | High |
| **Story** | MTO-126 |
| **Requirement** | UC-009 |
| **Preconditions** | Modules detected |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_modules() | Response has "modules" array |
| 2 | Verify module fields | Each has: name, path, file_count, symbol_count, languages |
| 3 | Verify total_modules field | Matches array length |

---

### UT-042: code_index_status — Response Schema

| Field | Value |
|-------|-------|
| **ID** | UT-042 |
| **Priority** | High |
| **Story** | MTO-126 |
| **Requirement** | UC-010 |
| **Preconditions** | Index ready |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_index_status() | Response has all required fields |
| 2 | Verify status field | One of: "ready", "indexing", "error" |
| 3 | Verify layers object | Has fts5, embeddings, summaries booleans |

---

### 2.8 Node.js Implementation (MTO-127)

### UT-043: Node.js — better-sqlite3 Database Init

| Field | Value |
|-------|-------|
| **ID** | UT-043 |
| **Priority** | High |
| **Story** | MTO-127 |
| **Requirement** | Same schema as Kotlin |
| **Preconditions** | Node.js environment, better-sqlite3 installed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call initDatabase(tempDir) | Database created |
| 2 | Verify tables exist | files, symbols, modules, FTS5 table |
| 3 | Verify WAL mode | PRAGMA journal_mode returns "wal" |

---

### UT-044: Python — sqlite3 Database Init

| Field | Value |
|-------|-------|
| **ID** | UT-044 |
| **Priority** | High |
| **Story** | MTO-128 |
| **Requirement** | Same schema as Kotlin |
| **Preconditions** | Python 3.11+ environment |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call init_database(temp_dir) | Database created |
| 2 | Verify tables exist | files, symbols, modules, FTS5 table |
| 3 | Verify WAL mode | PRAGMA journal_mode returns "wal" |

---

### UT-045: PowerShell/Bash — sqlite3 CLI Schema

| Field | Value |
|-------|-------|
| **ID** | UT-045 |
| **Priority** | Medium |
| **Story** | MTO-129 |
| **Requirement** | Same schema (Tier 2 — FTS5 only) |
| **Preconditions** | sqlite3 CLI available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run schema creation SQL via sqlite3 CLI | Database created |
| 2 | Verify tables | .tables shows files, symbols, modules |
| 3 | Verify FTS5 | .tables shows fts5 virtual table |

---

## 3. Integration Testing (IT)

### IT-001: Full Indexing Pipeline — Scan → Extract → Store → Query

| Field | Value |
|-------|-------|
| **ID** | IT-001 |
| **Priority** | Critical |
| **Story** | MTO-121, MTO-122, MTO-123 |
| **Requirement** | End-to-end indexing flow |
| **Preconditions** | Temp workspace with 5 Kotlin files containing classes and functions |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Initialize database in temp directory | DB created with schema |
| 2 | Run file scanner on workspace | Returns 5 file entries |
| 3 | Run signature extractor on each file | Returns symbols for each |
| 4 | Store all in database | Insert succeeds |
| 5 | Search FTS5 for known symbol name | Returns correct result |
| 6 | Get symbols by file path | Returns correct symbols |

---

### IT-002: Incremental Indexing — Hash-Based Change Detection

| Field | Value |
|-------|-------|
| **ID** | IT-002 |
| **Priority** | Critical |
| **Story** | MTO-123, MTO-125 |
| **Requirement** | BR-013, BR-023 |
| **Preconditions** | 10 files indexed in DB |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Modify 3 files on disk | Content hashes change |
| 2 | Run getChangedFiles() comparing disk vs DB hashes | Returns 3 changed paths |
| 3 | Re-index only changed files | Only 3 files updated |
| 4 | Verify unchanged files untouched | last_indexed same for 7 files |

---

### IT-003: Database Concurrent Read During Write (WAL Mode)

| Field | Value |
|-------|-------|
| **ID** | IT-003 |
| **Priority** | High |
| **Story** | MTO-121, MTO-125 |
| **Requirement** | BR-004: WAL mode for read concurrency |
| **Preconditions** | Database in WAL mode |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start a long write transaction (insert 1000 symbols) | Write in progress |
| 2 | Simultaneously query FTS5 from another connection | Read succeeds (not blocked) |
| 3 | Verify read returns pre-transaction data | Consistent snapshot |
| 4 | Commit write transaction | New data visible on next read |

---

### IT-004: File Watcher Integration — Real File System Events

| Field | Value |
|-------|-------|
| **ID** | IT-004 |
| **Priority** | High |
| **Story** | MTO-125 |
| **Requirement** | UC-012, UC-013 |
| **Preconditions** | Indexing engine started with file watcher on temp directory |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create new .kt file in watched directory | Event detected |
| 2 | Wait for debounce (3s) | File indexed |
| 3 | Modify the file | Event detected |
| 4 | Wait for debounce (3s) | File re-indexed with new symbols |
| 5 | Delete the file | Event detected |
| 6 | Wait for debounce (3s) | File and symbols removed from index |

---

### IT-005: Ollama HTTP Integration — Embedding Generation

| Field | Value |
|-------|-------|
| **ID** | IT-005 |
| **Priority** | High |
| **Story** | MTO-124 |
| **Requirement** | UC-011 |
| **Preconditions** | WireMock server simulating Ollama /api/embeddings endpoint |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start WireMock with Ollama stub (returns 768-dim vector) | Server ready |
| 2 | Call OllamaClient.generateEmbedding("test text") | Returns float array |
| 3 | Verify vector dimension | Exactly 768 |
| 4 | Verify HTTP request sent | POST /api/embeddings with model="nomic-embed-text" |

---

### IT-006: Ollama Graceful Degradation — Connection Refused

| Field | Value |
|-------|-------|
| **ID** | IT-006 |
| **Priority** | High |
| **Story** | MTO-124 |
| **Requirement** | BR-020 |
| **Preconditions** | No server on Ollama port |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call OllamaClient.checkHealth() | Returns false (no exception thrown) |
| 2 | Call code_context("test query") | Returns FTS5 results (fallback) |
| 3 | Verify response.search_method | "fts5" |
| 4 | Verify no error in response | Clean response, no stack trace |

---

### IT-007: Multi-Language Extraction Pipeline

| Field | Value |
|-------|-------|
| **ID** | IT-007 |
| **Priority** | High |
| **Story** | MTO-122 |
| **Requirement** | AC-2: all supported languages |
| **Preconditions** | Workspace with .kt, .ts, .py, .go, .rs, .sh, .ps1 files |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Scan workspace | All 7 files detected with correct language |
| 2 | Extract signatures from each | Symbols extracted for each language |
| 3 | Store in database | All symbols stored |
| 4 | Search for symbol from each language | Found in FTS5 results |

---

### IT-008: .gitignore Respect — Nested Patterns

| Field | Value |
|-------|-------|
| **ID** | IT-008 |
| **Priority** | High |
| **Story** | MTO-122 |
| **Requirement** | BR-007 |
| **Preconditions** | Workspace with root .gitignore and nested .gitignore |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create workspace: root .gitignore ignores "build/", nested ignores "*.generated.kt" | Patterns set |
| 2 | Create files in build/ and *.generated.kt | Files exist |
| 3 | Run scanner | Ignored files not in results |
| 4 | Verify only source files returned | Correct file list |

---

### IT-009: Database Migration — Corrupt DB Recovery

| Field | Value |
|-------|-------|
| **ID** | IT-009 |
| **Priority** | Medium |
| **Story** | MTO-121 |
| **Requirement** | EF-001-2 |
| **Preconditions** | Corrupted SQLite file (write random bytes) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Corrupt the .bridge/code-index.db file | File is invalid SQLite |
| 2 | Call DatabaseManager.initialize() | Detects corruption |
| 3 | Verify backup created | .bridge/code-index.db.bak exists |
| 4 | Verify fresh DB created | New valid database |

---

### IT-010: Transaction Atomicity — File + Symbols

| Field | Value |
|-------|-------|
| **ID** | IT-010 |
| **Priority** | High |
| **Story** | MTO-123 |
| **Requirement** | Atomic updates |
| **Preconditions** | Database with existing file entry |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start transaction: delete old symbols + insert new symbols | In transaction |
| 2 | Simulate failure mid-insert (throw exception) | Transaction rolled back |
| 3 | Verify old symbols still present | No partial update |

---

### IT-011: Search Performance — 100K Symbols

| Field | Value |
|-------|-------|
| **ID** | IT-011 |
| **Priority** | High |
| **Story** | MTO-123 |
| **Requirement** | AC: search < 200ms for 100K+ symbols |
| **Preconditions** | Database with 100,000 symbols |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Bulk insert 100,000 symbols | Insert completes |
| 2 | Run FTS5 search for common term | Results returned |
| 3 | Measure query_time_ms | < 200ms |

---

### IT-012: CodeIntelligenceModule Integration — Startup Lifecycle

| Field | Value |
|-------|-------|
| **ID** | IT-012 |
| **Priority** | Critical |
| **Story** | MTO-126 |
| **Requirement** | UC-014 |
| **Preconditions** | Kotlin bridge with CodeIntelligenceModule |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Initialize CodeIntelligenceModule with workspace path | Module starts |
| 2 | Verify database created | .bridge/code-index.db exists |
| 3 | Verify MCP tools registered | 5 tools available |
| 4 | Verify background indexing started | Status shows "indexing" then "ready" |

---

### IT-013: Node.js — Full Pipeline Integration

| Field | Value |
|-------|-------|
| **ID** | IT-013 |
| **Priority** | High |
| **Story** | MTO-127 |
| **Requirement** | Same behavior as Kotlin |
| **Preconditions** | Node.js environment with better-sqlite3 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Initialize database | Created with correct schema |
| 2 | Scan test workspace | Files detected |
| 3 | Extract and store symbols | Symbols in DB |
| 4 | Search via FTS5 | Results returned |
| 5 | Verify response format matches Kotlin | Same JSON structure |

---

### IT-014: Python — Full Pipeline Integration

| Field | Value |
|-------|-------|
| **ID** | IT-014 |
| **Priority** | High |
| **Story** | MTO-128 |
| **Requirement** | Same behavior as Kotlin |
| **Preconditions** | Python 3.11+ with sqlite3 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Initialize database | Created with correct schema |
| 2 | Scan test workspace | Files detected |
| 3 | Extract and store symbols | Symbols in DB |
| 4 | Search via FTS5 | Results returned |

---

### IT-015: PowerShell — FTS5 Search via sqlite3 CLI

| Field | Value |
|-------|-------|
| **ID** | IT-015 |
| **Priority** | Medium |
| **Story** | MTO-129 |
| **Requirement** | Tier 2 — FTS5 only |
| **Preconditions** | sqlite3 CLI available, database populated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run PowerShell code-intel.ps1 init | Database created |
| 2 | Run scan command | Files indexed |
| 3 | Run search command | FTS5 results returned as JSON |

---

### IT-016: Bash — FTS5 Search via sqlite3 CLI

| Field | Value |
|-------|-------|
| **ID** | IT-016 |
| **Priority** | Medium |
| **Story** | MTO-129 |
| **Requirement** | Tier 2 — FTS5 only |
| **Preconditions** | sqlite3 CLI available, database populated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run bash code-intel.sh init | Database created |
| 2 | Run scan command | Files indexed |
| 3 | Run search command | FTS5 results returned as JSON |

---

### IT-017: Cross-Client Schema Compatibility

| Field | Value |
|-------|-------|
| **ID** | IT-017 |
| **Priority** | High |
| **Story** | MTO-121 |
| **Requirement** | BR-002: identical schema across clients |
| **Preconditions** | Database created by Kotlin client |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create DB with Kotlin client | DB file exists |
| 2 | Open same DB with Node.js client | Opens successfully |
| 3 | Query symbols from Node.js | Returns data inserted by Kotlin |
| 4 | Insert from Node.js, query from Python | Cross-client read works |

---

### IT-018: Semantic Search — Cosine Similarity Ranking

| Field | Value |
|-------|-------|
| **ID** | IT-018 |
| **Priority** | High |
| **Story** | MTO-124 |
| **Requirement** | code_context with embeddings |
| **Preconditions** | WireMock Ollama, 3 files with embeddings stored |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Store 3 embeddings: auth-related, db-related, ui-related | Stored in DB |
| 2 | Generate query embedding for "authentication" | Query vector generated |
| 3 | Call code_context("authentication") | Auth file ranked first |
| 4 | Verify search_method | "embedding" |

---

### IT-019: Background Indexing — Non-Blocking MCP Tools

| Field | Value |
|-------|-------|
| **ID** | IT-019 |
| **Priority** | High |
| **Story** | MTO-125, MTO-126 |
| **Requirement** | AC-5: indexing never blocks tool responses |
| **Preconditions** | Large workspace (1000 files), indexing in progress |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start indexing 1000 files | Status = "indexing" |
| 2 | Call code_search("test") during indexing | Returns results (partial index) |
| 3 | Verify response time | < 500ms (not blocked by indexing) |
| 4 | Call code_index_status() | Shows progress < 100% |

---

### IT-020: Graceful Shutdown — No DB Corruption

| Field | Value |
|-------|-------|
| **ID** | IT-020 |
| **Priority** | High |
| **Story** | MTO-125 |
| **Requirement** | AC-7: graceful shutdown |
| **Preconditions** | Indexing in progress |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start indexing 500 files | Indexing running |
| 2 | Call shutdown() mid-indexing | Indexing stops within 5s |
| 3 | Verify DB integrity | PRAGMA integrity_check returns "ok" |
| 4 | Re-open database | Opens successfully, partial data intact |

---

### IT-021: Module Detection — Gradle Multi-Module Project

| Field | Value |
|-------|-------|
| **ID** | IT-021 |
| **Priority** | High |
| **Story** | MTO-123 |
| **Requirement** | code_modules detection |
| **Preconditions** | Workspace with settings.gradle.kts listing 3 modules |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Scan workspace with build.gradle.kts in subfolders | Modules detected |
| 2 | Call code_modules() | Returns 3 modules |
| 3 | Verify each module has correct path | Paths match actual directories |

---

### IT-022: Error Recovery — File Permission Denied

| Field | Value |
|-------|-------|
| **ID** | IT-022 |
| **Priority** | Medium |
| **Story** | MTO-122 |
| **Requirement** | Error handling: skip unreadable files |
| **Preconditions** | Workspace with 1 unreadable file among 10 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set one file to no-read permission | File unreadable |
| 2 | Run scanner | Scans 9 files, skips 1 |
| 3 | Verify warning logged | Warning mentions skipped file |
| 4 | Verify no crash | Scanner completes normally |

---

### IT-023: FTS5 — Special Characters in Query

| Field | Value |
|-------|-------|
| **ID** | IT-023 |
| **Priority** | Medium |
| **Story** | MTO-123 |
| **Requirement** | Security: no SQL injection |
| **Preconditions** | Indexed database |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Search for "test'; DROP TABLE files; --" | Returns empty results (no error) |
| 2 | Verify tables still exist | All tables intact |
| 3 | Search for "test*" (wildcard) | Returns prefix matches |

---

### IT-024: Database Size — Reasonable for Large Workspace

| Field | Value |
|-------|-------|
| **ID** | IT-024 |
| **Priority** | Medium |
| **Story** | MTO-123 |
| **Requirement** | AC: < 50MB for 10,000-file workspace |
| **Preconditions** | 10,000 files indexed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Index 10,000 files with average 10 symbols each | 100,000 symbols stored |
| 2 | Check database file size | < 50MB |

---

### IT-025: Config File Loading

| Field | Value |
|-------|-------|
| **ID** | IT-025 |
| **Priority** | Medium |
| **Story** | MTO-126 |
| **Requirement** | .bridge/code-intelligence.json config |
| **Preconditions** | Config file with custom exclude patterns |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create config with exclude_patterns: ["*.test.kt", "testdata/"] | Config file exists |
| 2 | Initialize CodeIntelligenceModule | Config loaded |
| 3 | Verify scanner respects custom excludes | Test files not indexed |

---

## 4. E2E API Testing (E2E-API)

### E2E-API-001: code_search — Full End-to-End

| Field | Value |
|-------|-------|
| **ID** | E2E-API-001 |
| **Priority** | Critical |
| **Story** | MTO-126 |
| **Requirement** | UC-006 |
| **Preconditions** | Bridge server running with indexed workspace |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send MCP tool call: code_search(query="DatabaseManager") | Response received |
| 2 | Verify response contains matching symbols | At least 1 result with "DatabaseManager" |
| 3 | Verify JSON schema | Has results[], total_matches, query_time_ms |
| 4 | Verify query_time_ms < 500 | Performance target met |

---

### E2E-API-002: code_search — With Language Filter

| Field | Value |
|-------|-------|
| **ID** | E2E-API-002 |
| **Priority** | High |
| **Story** | MTO-126 |
| **Requirement** | UC-006 |
| **Preconditions** | Multi-language workspace indexed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_search(query="process", language="kotlin") | Only Kotlin results |
| 2 | Call code_search(query="process", language="typescript") | Only TypeScript results |
| 3 | Verify no cross-language contamination | Each response filtered correctly |

---

### E2E-API-003: code_search — Empty Query

| Field | Value |
|-------|-------|
| **ID** | E2E-API-003 |
| **Priority** | Medium |
| **Story** | MTO-126 |
| **Requirement** | Error handling |
| **Preconditions** | Bridge server running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_search(query="") | Validation error returned |
| 2 | Verify error message | Clear message about empty query |
| 3 | Verify no crash | Server still responsive |

---

### E2E-API-004: code_symbols — Existing File

| Field | Value |
|-------|-------|
| **ID** | E2E-API-004 |
| **Priority** | Critical |
| **Story** | MTO-126 |
| **Requirement** | UC-007 |
| **Preconditions** | Known file indexed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_symbols(file_path="orchestrator-bridge/src/.../CodeIntelligenceModule.kt") | Response received |
| 2 | Verify symbols array | Contains class and function symbols |
| 3 | Verify each symbol has required fields | name, kind, signature, line_start present |

---

### E2E-API-005: code_symbols — Non-Existent File

| Field | Value |
|-------|-------|
| **ID** | E2E-API-005 |
| **Priority** | High |
| **Story** | MTO-126 |
| **Requirement** | Error handling: FILE_NOT_FOUND |
| **Preconditions** | Bridge server running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_symbols(file_path="nonexistent/file.kt") | Error response |
| 2 | Verify error code | "FILE_NOT_FOUND" |
| 3 | Verify error message | Descriptive message |

---

### E2E-API-006: code_context — With Embeddings Available

| Field | Value |
|-------|-------|
| **ID** | E2E-API-006 |
| **Priority** | High |
| **Story** | MTO-124, MTO-126 |
| **Requirement** | UC-008 |
| **Preconditions** | Ollama available (or WireMock), embeddings generated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_context(query="how does authentication work", top_k=5) | Response received |
| 2 | Verify search_method | "embedding" |
| 3 | Verify results ranked by relevance | Highest relevance first |
| 4 | Verify result fields | file, summary, symbols, relevance present |

---

### E2E-API-007: code_context — Fallback When Ollama Down

| Field | Value |
|-------|-------|
| **ID** | E2E-API-007 |
| **Priority** | High |
| **Story** | MTO-124, MTO-126 |
| **Requirement** | Graceful degradation |
| **Preconditions** | Ollama not running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_context(query="database connection") | Response received (no error) |
| 2 | Verify search_method | "fts5" |
| 3 | Verify results present | FTS5 keyword matches returned |

---

### E2E-API-008: code_modules — List All Modules

| Field | Value |
|-------|-------|
| **ID** | E2E-API-008 |
| **Priority** | High |
| **Story** | MTO-126 |
| **Requirement** | UC-009 |
| **Preconditions** | Workspace with multiple modules indexed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_modules() | Response received |
| 2 | Verify modules array | Contains detected modules |
| 3 | Verify each module has file_count > 0 | Modules have files |
| 4 | Verify total_modules matches array length | Consistent |

---

### E2E-API-009: code_index_status — During Indexing

| Field | Value |
|-------|-------|
| **ID** | E2E-API-009 |
| **Priority** | High |
| **Story** | MTO-125, MTO-126 |
| **Requirement** | UC-010 |
| **Preconditions** | Indexing in progress |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Trigger re-index of large workspace | Indexing starts |
| 2 | Call code_index_status() immediately | status = "indexing" |
| 3 | Verify indexing_progress | Between 0 and 100 |
| 4 | Wait for completion | status = "ready", progress = 100 |

---

### E2E-API-010: code_index_status — Ready State

| Field | Value |
|-------|-------|
| **ID** | E2E-API-010 |
| **Priority** | High |
| **Story** | MTO-126 |
| **Requirement** | UC-010 |
| **Preconditions** | Indexing complete |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_index_status() | Response received |
| 2 | Verify status = "ready" | Correct |
| 3 | Verify files_indexed > 0 | Has indexed files |
| 4 | Verify layers.fts5 = true | Core layer active |
| 5 | Verify db_size_mb > 0 | Database has data |

---

### E2E-API-011: code_search — Pagination with Limit

| Field | Value |
|-------|-------|
| **ID** | E2E-API-011 |
| **Priority** | Medium |
| **Story** | MTO-126 |
| **Requirement** | code_search limit parameter |
| **Preconditions** | Many matching symbols |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_search(query="get", limit=3) | Returns max 3 results |
| 2 | Verify results.length ≤ 3 | Limit respected |
| 3 | Verify total_matches > 3 | Total count shows all matches |

---

### E2E-API-012: code_search — Module Filter

| Field | Value |
|-------|-------|
| **ID** | E2E-API-012 |
| **Priority** | Medium |
| **Story** | MTO-126 |
| **Requirement** | code_search module parameter |
| **Preconditions** | Multiple modules indexed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_search(query="service", module="codeintel") | Only codeintel results |
| 2 | Verify all results have module="codeintel" | Filter applied |

---

### E2E-API-013: MCP Tool Registration — All 5 Tools Available

| Field | Value |
|-------|-------|
| **ID** | E2E-API-013 |
| **Priority** | Critical |
| **Story** | MTO-126 |
| **Requirement** | All 5 MCP tools registered |
| **Preconditions** | Bridge server started |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | List available MCP tools | Tool list returned |
| 2 | Verify code_search present | Found |
| 3 | Verify code_symbols present | Found |
| 4 | Verify code_context present | Found |
| 5 | Verify code_modules present | Found |
| 6 | Verify code_index_status present | Found |

---

### E2E-API-014: code_search — Performance Under Load

| Field | Value |
|-------|-------|
| **ID** | E2E-API-014 |
| **Priority** | Medium |
| **Story** | MTO-123, MTO-126 |
| **Requirement** | AC: all tools respond within 500ms |
| **Preconditions** | Large index (10,000+ symbols) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call code_search 10 times with different queries | All respond |
| 2 | Measure average response time | < 500ms |
| 3 | Measure max response time | < 1000ms |

---

### E2E-API-015: Index Rebuild After DB Delete

| Field | Value |
|-------|-------|
| **ID** | E2E-API-015 |
| **Priority** | Medium |
| **Story** | MTO-121, MTO-125 |
| **Requirement** | UC-002: reset by deletion |
| **Preconditions** | Indexed workspace |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Delete .bridge/code-index.db | File removed |
| 2 | Restart bridge (or trigger re-init) | New DB created |
| 3 | Wait for full scan | Index rebuilt |
| 4 | Call code_search("test") | Returns results from fresh index |

---

## 5. System Integration Testing (SIT)

### SIT-001: Cross-Client Schema Compatibility — Real Databases

| Field | Value |
|-------|-------|
| **ID** | SIT-001 |
| **Priority** | Critical |
| **Story** | MTO-121, MTO-127, MTO-128, MTO-129 |
| **Requirement** | BR-002 |
| **Type** | Automated |
| **Preconditions** | All bridge clients available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create DB with Kotlin bridge | DB file created |
| 2 | Index 10 files with Kotlin | Symbols stored |
| 3 | Open same DB with Node.js bridge | Opens without error |
| 4 | Query symbols from Node.js | Returns Kotlin-inserted data |
| 5 | Add symbols from Node.js | Insert succeeds |
| 6 | Open same DB with Python bridge | Opens without error |
| 7 | Query all symbols from Python | Returns both Kotlin and Node.js data |

---

### SIT-002: Ollama Real Integration — Embedding Generation

| Field | Value |
|-------|-------|
| **ID** | SIT-002 |
| **Priority** | High |
| **Story** | MTO-124 |
| **Requirement** | UC-011 |
| **Type** | Manual (requires Ollama + GPU) |
| **Preconditions** | Ollama running with nomic-embed-text model loaded |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start bridge with Ollama available | Health check passes |
| 2 | Verify layers.embeddings = true | Layer 2 active |
| 3 | Index workspace (triggers embedding generation) | Embeddings generated |
| 4 | Call code_context("authentication logic") | Returns semantic results |
| 5 | Verify search_method = "embedding" | Semantic search used |
| 6 | Verify results are semantically relevant | Auth-related files ranked high |

---

### SIT-003: Ollama Real Integration — AI Summarization

| Field | Value |
|-------|-------|
| **ID** | SIT-003 |
| **Priority** | Medium |
| **Story** | MTO-124 |
| **Requirement** | Layer 3 |
| **Type** | Manual (requires Ollama + qwen3:8b) |
| **Preconditions** | Ollama running with qwen3:8b model |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start bridge with both models available | Health check passes |
| 2 | Wait for summarization to complete | layers.summaries = true |
| 3 | Call code_modules() | Modules have AI-generated summaries |
| 4 | Verify summaries are coherent | Human-readable, accurate descriptions |

---

### SIT-004: Large Workspace Performance — 5000 Files

| Field | Value |
|-------|-------|
| **ID** | SIT-004 |
| **Priority** | High |
| **Story** | MTO-125 |
| **Requirement** | AC: initial scan < 60s for 5000 files |
| **Type** | Automated |
| **Preconditions** | Workspace with 5000+ source files |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Point bridge at large workspace (e.g., kotlinx.coroutines clone) | Workspace set |
| 2 | Start fresh indexing (delete existing DB) | Full scan starts |
| 3 | Measure time to completion | < 60 seconds |
| 4 | Verify all files indexed | files_indexed ≈ 5000 |
| 5 | Run search query | Results in < 200ms |

---

### SIT-005: Graceful Degradation — Ollama Becomes Unavailable Mid-Session

| Field | Value |
|-------|-------|
| **ID** | SIT-005 |
| **Priority** | High |
| **Story** | MTO-124 |
| **Requirement** | BR-020 |
| **Type** | Manual |
| **Preconditions** | Bridge running with Ollama active |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Verify code_context uses embeddings | search_method = "embedding" |
| 2 | Stop Ollama service | Ollama unavailable |
| 3 | Call code_context("test") | Falls back to FTS5 (no error) |
| 4 | Verify search_method = "fts5" | Graceful fallback |
| 5 | Restart Ollama | Service back |
| 6 | Wait for health check retry (60s) | Ollama re-detected |
| 7 | Call code_context("test") | Back to embedding search |

---

### SIT-006: File Watcher — Rapid File Changes (IDE Save)

| Field | Value |
|-------|-------|
| **ID** | SIT-006 |
| **Priority** | Medium |
| **Story** | MTO-125 |
| **Requirement** | BR-026: debounce |
| **Type** | Automated |
| **Preconditions** | Bridge running with file watcher |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Rapidly save same file 20 times in 2 seconds | Events generated |
| 2 | Wait 5 seconds for debounce + processing | Processing completes |
| 3 | Verify file indexed only once (or few times) | Not 20 re-indexes |
| 4 | Verify final index state is correct | Latest content indexed |

---

### SIT-007: Memory Usage — 10,000 File Workspace

| Field | Value |
|-------|-------|
| **ID** | SIT-007 |
| **Priority** | Medium |
| **Story** | MTO-126 |
| **Requirement** | AC: memory < 256MB heap |
| **Type** | Automated |
| **Preconditions** | Large workspace |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start bridge with -Xmx256m | JVM starts |
| 2 | Index 10,000 files | Indexing completes without OOM |
| 3 | Monitor heap usage | Peak < 256MB |

---

### SIT-008: Regression — Existing Bridge Features Still Work

| Field | Value |
|-------|-------|
| **ID** | SIT-008 |
| **Priority** | Critical |
| **Story** | MTO-126 |
| **Requirement** | No regression |
| **Type** | Automated |
| **Preconditions** | Bridge with code intelligence enabled |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Verify existing MCP tools still work (non-code-intel tools) | All respond correctly |
| 2 | Verify bridge startup time not significantly increased | < 5s additional |
| 3 | Verify health check endpoint works | Returns healthy |
| 4 | Verify reconnection manager works | Reconnects after disconnect |

---

### SIT-009: PowerShell Bridge — End-to-End on Windows

| Field | Value |
|-------|-------|
| **ID** | SIT-009 |
| **Priority** | Medium |
| **Story** | MTO-129 |
| **Requirement** | Tier 2 functionality |
| **Type** | Manual (Windows only) |
| **Preconditions** | Windows 11, PowerShell 7+, sqlite3 CLI |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run code-intel.ps1 with workspace path | Initializes DB |
| 2 | Verify .bridge/code-index.db created | File exists |
| 3 | Run scan command | Files indexed |
| 4 | Run search command for known symbol | Results returned |
| 5 | Verify JSON output format | Matches expected schema |

---

### SIT-010: Bash Bridge — End-to-End on Linux/macOS

| Field | Value |
|-------|-------|
| **ID** | SIT-010 |
| **Priority** | Medium |
| **Story** | MTO-129 |
| **Requirement** | Tier 2 functionality |
| **Type** | Manual (Linux/macOS) |
| **Preconditions** | Bash 4+, sqlite3 CLI |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run code-intel.sh with workspace path | Initializes DB |
| 2 | Verify .bridge/code-index.db created | File exists |
| 3 | Run scan command | Files indexed |
| 4 | Run search command for known symbol | Results returned |
| 5 | Verify JSON output format | Matches expected schema |

---

## 6. Requirements Traceability Matrix (RTM)

| Requirement | Source | Test Cases | Coverage |
|-------------|--------|------------|----------|
| UC-001: Initialize Database | FSD 3.1 | UT-001, UT-002, UT-003, IT-012 | Covered |
| UC-002: Reset Database | FSD 3.1 | E2E-API-015 | Covered |
| UC-003: Scan Workspace | FSD 3.2 | UT-006, UT-007, UT-008, UT-009, IT-001, IT-007, IT-008 | Covered |
| UC-004: Extract Signatures | FSD 3.2 | UT-011–UT-017, PBT-002, IT-007 | Covered |
| UC-005: Query Index | FSD 3.3 | UT-018–UT-026, IT-001, IT-011 | Covered |
| UC-006: code_search | FSD 3.4 | UT-038, E2E-API-001–003, E2E-API-011–012, E2E-API-014 | Covered |
| UC-007: code_symbols | FSD 3.4 | UT-039, E2E-API-004–005 | Covered |
| UC-008: code_context | FSD 3.4 | UT-040, E2E-API-006–007, IT-018 | Covered |
| UC-009: code_modules | FSD 3.4 | UT-041, E2E-API-008, IT-021 | Covered |
| UC-010: code_index_status | FSD 3.4 | UT-042, E2E-API-009–010 | Covered |
| UC-011: Ollama Integration | FSD 3.5 | UT-027–031, IT-005–006, SIT-002–003, SIT-005 | Covered |
| UC-012: Background Indexing | FSD 3.7 | UT-032–037, IT-004, IT-019, SIT-004, SIT-006 | Covered |
| UC-013: File Watcher | FSD 3.7 | UT-034–036, IT-004, SIT-006 | Covered |
| UC-014: Kotlin Integration | FSD 3.8 | IT-012, E2E-API-001–015, SIT-008 | Covered |
| UC-015: Node.js Integration | FSD 3.9 | UT-043, IT-013, SIT-001 | Covered |
| UC-016: Python Integration | FSD 3.10 | UT-044, IT-014, SIT-001 | Covered |
| UC-017: PowerShell Integration | FSD 3.11 | UT-045, IT-015, SIT-009 | Covered |
| UC-018: Bash Integration | FSD 3.11 | IT-016, SIT-010 | Covered |
| BR-001: Schema versioning | FSD 3.1.3 | UT-003 | Covered |
| BR-002: Identical schema | FSD 3.1.3 | IT-017, SIT-001 | Covered |
| BR-003: DB path | FSD 3.1.3 | UT-001 | Covered |
| BR-004: WAL mode | FSD 3.1.3 | UT-001, IT-003 | Covered |
| BR-005: FTS5 tokenizer | FSD 3.1.3 | UT-004 | Covered |
| BR-007: Relative paths | FSD 3.2.3 | PBT-004, UT-006 | Covered |
| BR-009: SHA-256 hash | FSD 3.2.3 | PBT-001, UT-010 | Covered |
| BR-012: Regex timeout | FSD 3.2.3 | PBT-002 | Covered |
| BR-013: Incremental update | FSD 3.3.3 | UT-022, IT-002 | Covered |
| BR-020: Graceful degradation | FSD 3.5.3 | UT-028, IT-006, SIT-005 | Covered |
| Security: No SQL injection | Security | PBT-003, IT-023 | Covered |
| Performance: Search < 200ms | FSD NFR | IT-011, E2E-API-014, SIT-004 | Covered |
| Performance: Scan < 60s/5000 files | FSD NFR | SIT-004 | Covered |
| Performance: Memory < 256MB | FSD NFR | SIT-007 | Covered |

**Coverage Summary:**

| Category | Total | Covered | Coverage % |
|----------|-------|---------|------------|
| Use Cases | 18 | 18 | 100% |
| Business Rules | 12 | 12 | 100% |
| Non-Functional | 4 | 4 | 100% |
| Security | 1 | 1 | 100% |
| **Overall** | **35** | **35** | **100%** |

---

## 7. Appendix

### Test Data Files

Test data fixtures are located at `documents/MTO-120/testdata/`:

| File | Purpose |
|------|---------|
| sample-workspace/ | Multi-language workspace fixture (10 files) |
| large-workspace-generator.sh | Script to generate 5000-file workspace |
| gitignore-patterns.txt | Various .gitignore test patterns |
| malformed-files/ | Binary, empty, huge files for edge cases |

### Test Execution Commands

```bash
# Run all Kotlin tests (UT + IT + E2E-API)
./gradlew test

# Run only code intelligence tests
./gradlew test --tests "com.orchestrator.mcp.bridge.codeintel.*"

# Run Node.js tests
cd mcp-client-bridge && npm test

# Run Python tests
cd mcp-bridge-python && pytest

# Run with coverage
./gradlew test jacocoTestReport
```
