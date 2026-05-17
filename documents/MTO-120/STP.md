# Software Test Plan (STP)

## Bridge Clients — MTO-120: Local Code Intelligence — SQLite Index + Semantic Search

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-120 |
| Title | Local Code Intelligence — SQLite Index + Semantic Search Across All Bridge Clients |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2025-07-10 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-120.docx |
| Related FSD | FSD-v1-MTO-120.docx |
| Related TDD | TDD-v1-MTO-121.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-10 | QA Agent | Initial test plan — covers all 9 stories (MTO-121 to MTO-129) |

---

## 1. Introduction

### 1.1 Purpose

This test plan defines the testing strategy for the **Local Code Intelligence** system — a multi-language implementation providing SQLite-based code indexing with FTS5 full-text search, optional Ollama semantic search, and background file watching across 6 bridge clients (Kotlin, Node.js, Python, PowerShell, Bash, and server-side VCS index).

### 1.2 Test Objectives

- Verify SQLite schema creation, FTS5 indexing, and migration logic across all bridge clients
- Validate file scanning respects .gitignore and extracts correct signatures for all supported languages
- Ensure all 5 MCP tools (code_search, code_symbols, code_context, code_modules, code_index_status) return correct JSON responses
- Validate Ollama integration gracefully degrades when unavailable
- Verify background indexing and file watcher maintain index consistency
- Confirm cross-client schema compatibility (same DB works across Tier 1 clients)
- Validate performance targets (initial scan < 60s for 5000 files, search < 200ms)

### 1.3 References

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-120.docx |
| FSD | FSD-v1-MTO-120.docx |
| TDD | TDD-v1-MTO-121.docx |

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | ID Prefix | Scope | Responsibility | Tools | Automation |
|-------|-----------|-------|---------------|-------|------------|
| Property-Based Testing (PBT) | PBT- | Invariant verification for parsers, hash functions | Developer | Kotest (property), fast-check (JS) | 100% automated |
| Unit Testing (UT) | UT- | Individual functions: extractors, parsers, query builders | Developer | JUnit 5 + Kotest (Kotlin), Jest (Node.js), pytest (Python) | 100% automated |
| Integration Testing (IT) | IT- | SQLite operations, Ollama HTTP calls, file system interactions | Developer + QA | Testcontainers (SQLite), temp directories, WireMock (Ollama) | 100% automated |
| E2E API Testing (E2E-API) | E2E-API- | MCP tool invocations end-to-end on real server | QA | Ktor test client, real SQLite DB, real file system | 100% automated |
| E2E UI Testing (E2E-UI) | E2E-UI- | N/A — no UI in this feature | — | — | N/A |
| System Integration Testing (SIT) | SIT- | Cross-client compatibility, Ollama real integration, large workspace | QA | Manual + scripted | 80% automated, 20% manual |

### 2.2 Test Types

| Type | Description | Applicable |
|------|-------------|------------|
| Functional Testing | Verify MCP tools, scanner, extractor, query layer per FSD | Yes |
| Regression Testing | Ensure existing bridge functionality not broken | Yes |
| Performance Testing | Verify indexing speed, search latency, memory usage | Yes |
| Security Testing | Verify no path traversal, SQL injection in queries | Yes |
| Compatibility Testing | Verify same schema across Kotlin/Node.js/Python/PowerShell/Bash | Yes |
| Usability Testing | N/A — no UI | No |

### 2.3 Test Approach

**Risk-based prioritization:**
1. **Critical path:** Schema creation → File scanning → FTS5 search → MCP tools (must work in all clients)
2. **High risk:** Incremental indexing (hash comparison), concurrent access (WAL mode), graceful degradation
3. **Medium risk:** Ollama integration, AI summarization, large workspace performance
4. **Low risk:** Edge cases in regex extraction, unusual file encodings

**Automation strategy:**
- All unit and integration tests automated in Gradle test suite (`./gradlew test`)
- E2E-API tests use Ktor `testApplication` for Kotlin bridge
- Node.js tests use Jest with real SQLite (better-sqlite3)
- Python tests use pytest with sqlite3 stdlib
- SIT tests scripted where possible, manual for Ollama real-model verification

### 2.4 Entry Criteria

| Level | Entry Criteria |
|-------|---------------|
| UT/IT | Code compiles, dependencies resolved, test fixtures available |
| E2E-API | All UT/IT pass, MCP tools registered, test workspace prepared |
| SIT | All E2E-API pass, Ollama installed with models, multi-client environment ready |

### 2.5 Exit Criteria

| Level | Exit Criteria |
|-------|--------------|
| UT/IT | 100% test cases executed, 0 Critical defects, code coverage ≥ 80% |
| E2E-API | 100% MCP tool scenarios pass, response format validated |
| SIT | All cross-client scenarios pass, performance targets met, Ollama graceful degradation confirmed |

---

## 3. Test Scope

### 3.1 Features In Scope

| # | Feature / Story | Priority | FSD Reference | Test Level |
|---|----------------|----------|---------------|------------|
| 1 | SQLite Schema + FTS5 Setup (MTO-121) | Critical | UC-001, UC-002, BR-001–BR-006 | UT, IT |
| 2 | File Scanner + Signature Extractor (MTO-122) | Critical | UC-003, UC-004, BR-007–BR-012 | PBT, UT, IT |
| 3 | SQLite Storage + Query Layer (MTO-123) | Critical | UC-005, BR-013–BR-018 | UT, IT, E2E-API |
| 4 | MCP Tools (MTO-123/126) | Critical | UC-006–UC-010 | UT, IT, E2E-API |
| 5 | Ollama Embedding Integration (MTO-124) | High | UC-011, BR-019–BR-022 | UT, IT, SIT |
| 6 | Background Indexing + File Watcher (MTO-125) | Critical | UC-012, UC-013, BR-023–BR-026 | UT, IT, E2E-API |
| 7 | Kotlin Bridge Integration (MTO-126) | Critical | UC-014 | IT, E2E-API |
| 8 | Node.js Bridge Implementation (MTO-127) | High | UC-015 | UT, IT |
| 9 | Python Bridge Implementation (MTO-128) | High | UC-016 | UT, IT |
| 10 | PowerShell + Bash Implementation (MTO-129) | Medium | UC-017, UC-018 | UT, IT |

### 3.2 Features Out of Scope

| # | Feature | Reason |
|---|---------|--------|
| 1 | CMD bridge | Explicitly out of scope per BRD |
| 2 | Cross-workspace search | Not part of this epic |
| 3 | IDE plugin UI | Separate feature |
| 4 | AI summarization quality assessment | Subjective, requires human evaluation |

---

## 4. Test Environment

### 4.1 Environment Requirements

| Environment | Configuration | Database | Purpose |
|-------------|--------------|----------|---------|
| Local Dev | JDK 21, Node.js 20+, Python 3.11+ | SQLite (file-based) | Unit + Integration tests |
| CI (GitHub Actions) | Ubuntu latest, JDK 21 | SQLite (temp directory) | Automated test suite |
| SIT | Windows 11, RTX 4060 8GB, Ollama installed | SQLite (real workspace) | Full integration with Ollama |

### 4.2 Test Data Requirements

| Data Type | Description | Source | Preparation |
|-----------|-------------|--------|-------------|
| Sample workspace | Multi-language project with 100+ files | Generated fixture | Create temp directory with .kt, .ts, .py, .go files |
| Large workspace | 5000+ files for performance testing | Cloned open-source repo | Use kotlin/kotlinx.coroutines or similar |
| .gitignore patterns | Various ignore patterns | Fixture file | Include node_modules, build, .git patterns |
| Malformed files | Binary, empty, huge files | Generated | Create edge-case test files |

### 4.3 External Dependencies

| System | Dependency | Mock/Stub Available |
|--------|-----------|---------------------|
| Ollama (localhost:11434) | Embedding generation, summarization | Yes — WireMock stub for HTTP API |
| File system | Read/write workspace files | Yes — temp directories |
| SQLite | Database operations | Yes — in-memory or temp file |

---

## 5. Test Schedule

| Phase | Duration | Milestone |
|-------|----------|-----------|
| Test Planning | 1 day | STP + STC approved |
| Test Implementation | 3 days | All automated tests written |
| Test Execution | 1 day | All tests pass |
| Defect Fix & Retest | 2 days | All Critical/Major fixed |
| SIT Execution | 1 day | Cross-client + Ollama verified |

---

## 6. Resources & Responsibilities

| Role | Responsibility |
|------|---------------|
| QA Agent | Test planning, test case design, E2E-API + SIT execution |
| Developer | Unit tests, integration tests, bug fixing |
| SA Agent | Architecture review of test approach |
| DevOps | CI pipeline configuration for test execution |

---

## 7. Risk & Mitigation

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | Ollama not available in CI | High | High | Mock Ollama HTTP API with WireMock; SIT tests run locally only |
| 2 | SQLite version differences across platforms | Medium | Low | Pin sqlite-jdbc version; test on CI (Ubuntu) + local (Windows) |
| 3 | File watcher behavior differs across OS | Medium | Medium | Use Java NIO WatchService (cross-platform); test on both OS |
| 4 | Large workspace performance regression | High | Low | Benchmark tests with threshold assertions; run in CI |
| 5 | Regex extraction hangs on pathological input | Medium | Low | Property-based tests with random input; 100ms timeout per file |

---

## 8. Defect Management

### 8.1 Severity Levels

| Severity | Definition | Example |
|----------|-----------|---------|
| Critical | Index corruption, data loss, crash | SQLite DB corrupted after concurrent writes |
| Major | Feature not working, no workaround | code_search returns empty for indexed files |
| Minor | Feature works but degraded | Slow search (> 500ms) for small workspace |
| Trivial | Cosmetic, logging issues | Typo in log message |

### 8.2 Priority Levels

| Priority | Definition | SLA |
|----------|-----------|-----|
| P1 | Blocks all testing | Fix within 4 hours |
| P2 | Blocks specific test area | Fix within 1 day |
| P3 | Non-blocking, should fix | Fix within 3 days |
| P4 | Nice to have | Next sprint |

---

## 9. Test Metrics & Reporting

### 9.1 Metrics

| Metric | Target |
|--------|--------|
| Test Execution Rate | 100% |
| Pass Rate | ≥ 95% |
| Code Coverage (Kotlin) | ≥ 80% |
| Critical Defect Count | 0 |
| Performance Tests Pass | 100% |

### 9.2 Test Case Count by Level

| Level | Estimated Count | Automation |
|-------|----------------|------------|
| PBT | 5 | 100% |
| UT | 45 | 100% |
| IT | 25 | 100% |
| E2E-API | 15 | 100% |
| SIT | 10 | 80% |
| **Total** | **100** | **97%** |

---

## 10. Appendix

### Glossary

| Term | Definition |
|------|------------|
| FTS5 | Full-Text Search 5 — SQLite extension |
| PBT | Property-Based Testing |
| WAL | Write-Ahead Logging |
| MCP | Model Context Protocol |
| SIT | System Integration Testing |

### Assumptions

- SQLite FTS5 extension is available in all target environments
- Ollama is NOT required for core functionality (graceful degradation)
- Test workspace fixtures are deterministic (same files produce same index)
- CI environment has sufficient disk space for SQLite test databases
