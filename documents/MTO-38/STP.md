# Software Test Plan (STP)

## MCPOrchestration — MTO-38: KB Server — Test Plan

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-38 |
| Title | KB Server — Test Plan |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-38.docx |
| Related FSD | FSD-v1-MTO-38.docx |
| Related TDD | TDD-v1-MTO-38.docx |

---

## 1. Test Strategy

### 1.1 Test Levels

| Level | Scope | Framework | Automation |
|-------|-------|-----------|------------|
| Unit Test (UT) | Individual classes/functions | Kotest + MockK | 100% automated |
| Integration Test (IT) | Component interactions | Kotest + Testcontainers | 100% automated |
| E2E-API | Full MCP tool calls | Kotest + Ktor TestHost | 100% automated |
| System Integration (SIT) | Orchestrator + KB Server | Manual + Script | 80% automated |

### 1.2 Test Scope

| In Scope | Out of Scope |
|----------|-------------|
| 13 MCP tool handlers | Orchestrator routing logic |
| Queue system (DualPriorityQueue) | UI testing (no frontend) |
| PII masking pipeline | Performance/load testing |
| Vector search (pgvector) | Security penetration testing |
| Configuration loading | Multi-node deployment |
| Error handling | OCR integration |
| Audit logging | |

### 1.3 Entry Criteria

- Code compiles without errors (gradlew :kb-server:compileKotlin)
- ShadowJar builds successfully
- PostgreSQL + pgvector available (Testcontainers for IT)
- All dependencies resolved

### 1.4 Exit Criteria

- All UT pass (37+ tests)
- All IT pass
- All E2E-API pass
- No Critical/High severity bugs open
- Test coverage >= 80% for handler layer

---

## 2. Test Coverage Matrix (RTM)

### Requirements Traceability

| BRD Story | FSD Use Case | Test Level | Test Case IDs |
|-----------|-------------|------------|---------------|
| STORY-1: Gradle Module | - | UT, SIT | TC-UT-01, TC-SIT-01 |
| STORY-2: ShadowJar | - | SIT | TC-SIT-02 |
| STORY-3: 13 MCP Tools | UC-01 to UC-13 | UT, IT, E2E | TC-UT-02..14, TC-IT-01..06, TC-E2E-01..13 |
| STORY-4: STDIO+HTTP | - | IT, E2E | TC-IT-07, TC-E2E-14..15 |
| STORY-5: roots/list | UC (workspace) | UT, IT | TC-UT-15, TC-IT-08 |
| STORY-6: Graph UI | UC-12 | E2E | TC-E2E-16 |
| STORY-7: Orchestrator OK | - | SIT | TC-SIT-03 |
| STORY-8: 37+ Tests | - | UT | TC-UT-ALL |

---

## 3. Test Cases Summary

### 3.1 Unit Tests (UT)

| ID | Test Case | Component | Priority |
|----|-----------|-----------|----------|
| TC-UT-01 | Config loading from YAML | KbConfigLoader | High |
| TC-UT-02 | kb_search validates empty query | KbSearchHandler | High |
| TC-UT-03 | kb_search vector + keyword fallback | KbSearchHandler | High |
| TC-UT-04 | kb_ingest validates title/content | KbIngestHandler | High |
| TC-UT-05 | kb_ingest computes content hash | KbIngestHandler | Medium |
| TC-UT-06 | kb_read returns entry by key | KbReadHandler | High |
| TC-UT-07 | kb_delete removes entry + vector | KbDeleteHandler | High |
| TC-UT-08 | DualPriorityQueue HPQ priority | DualPriorityQueue | Critical |
| TC-UT-09 | QueueWorker dispatches tasks | QueueWorker | High |
| TC-UT-10 | QueueWatchdog detects stuck tasks | QueueWatchdog | High |
| TC-UT-11 | CrashRecovery restores tasks | CrashRecoveryService | Critical |
| TC-UT-12 | PiiDetector detects email | PiiDetector | High |
| TC-UT-13 | PiiDetector detects phone | PiiDetector | High |
| TC-UT-14 | PiiMaskingEngine mask + encrypt | PiiMaskingEngine | Critical |
| TC-UT-15 | WorkspaceContext resolves root | WorkspaceContext | Medium |
| TC-UT-16 | Rate limiter enforces limits | RateLimiter | High |
| TC-UT-17 | Audit event logging | AuditService | Medium |

### 3.2 Integration Tests (IT)

| ID | Test Case | Components | Priority |
|----|-----------|-----------|----------|
| TC-IT-01 | Search with real pgvector | SearchHandler + VectorClient + DB | High |
| TC-IT-02 | Ingest + embedding + vector index | IngestHandler + Embedding + Vector | High |
| TC-IT-03 | Delete cascades to vector | DeleteHandler + Repository + Vector | High |
| TC-IT-04 | Queue enqueue + worker process | QueueService + Worker + DB | High |
| TC-IT-05 | Audit events persisted | AuditService + Repository + DB | Medium |
| TC-IT-06 | PII mask + encrypt + unmask cycle | Masking + Encryption + DB | Critical |
| TC-IT-07 | HTTP transport serves MCP | KbHttpTransport + Ktor | High |
| TC-IT-08 | roots/list resolves workspace | MCP Server + Client | Medium |

### 3.3 E2E-API Tests

| ID | Test Case | Tool | Priority |
|----|-----------|------|----------|
| TC-E2E-01 | kb_search returns ranked results | kb_search | High |
| TC-E2E-02 | kb_search with project filter | kb_search | Medium |
| TC-E2E-03 | kb_ingest stores and indexes | kb_ingest | High |
| TC-E2E-04 | kb_read returns full entry | kb_read | High |
| TC-E2E-05 | kb_delete removes entry | kb_delete | High |
| TC-E2E-06 | kb_link finds similar | kb_link | Medium |
| TC-E2E-07 | kb_feedback stores rating | kb_feedback | Medium |
| TC-E2E-08 | kb_audit_query returns events | kb_audit_query | Medium |
| TC-E2E-09 | kb_sync_trigger enqueues job | kb_sync_trigger | Medium |
| TC-E2E-10 | kb_sync_status returns progress | kb_sync_status | Low |
| TC-E2E-11 | kb_unmask_pii with rate limit | kb_unmask_pii | High |
| TC-E2E-12 | kb_graph returns nodes/edges | kb_graph | Medium |
| TC-E2E-13 | kb_network BFS traversal | kb_network | Medium |
| TC-E2E-14 | STDIO transport full cycle | All tools | High |
| TC-E2E-15 | HTTP transport full cycle | All tools | High |
| TC-E2E-16 | Graph UI serves HTML | /graph | Low |

### 3.4 System Integration Tests (SIT)

| ID | Test Case | Priority |
|----|-----------|----------|
| TC-SIT-01 | gradlew :kb-server:compileKotlin succeeds | Critical |
| TC-SIT-02 | shadowJar builds kb-server-all.jar | Critical |
| TC-SIT-03 | orchestrator-server still compiles | Critical |
| TC-SIT-04 | Orchestrator discovers kb-server tools | High |
| TC-SIT-05 | End-to-end: Agent -> Orchestrator -> KB Server | High |

---

## 4. Test Environment

### 4.1 Required Infrastructure

| Component | Version | Purpose |
|-----------|---------|---------|
| JDK | 21 | Runtime |
| PostgreSQL | 16+ | Database (Testcontainers) |
| pgvector | 0.7+ | Vector extension |
| Ollama | latest | Embedding (mock for UT/IT) |
| Gradle | 9.0 | Build tool |

### 4.2 Test Data

- Sample KB entries (5-10 entries with varied content)
- PII test data (emails, phones, bank accounts)
- Vector embeddings (pre-computed for deterministic tests)
- Queue tasks (various priorities and states)

---

## 5. Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| pgvector not available in CI | IT tests fail | Use Testcontainers with pgvector image |
| Ollama not available | Embedding tests fail | Mock EmbeddingService in UT/IT |
| Flaky queue tests (timing) | False failures | Use deterministic delays, increase timeouts |
| Large test data | Slow tests | Use minimal datasets, parallel execution |

---

## 6. Test Schedule

| Phase | Duration | Dependencies |
|-------|----------|-------------|
| UT execution | Immediate | Code compiled |
| IT setup + execution | 1 hour | Testcontainers ready |
| E2E-API execution | 30 min | Server startable |
| SIT execution | 1 hour | Full build + orchestrator |

---

## 7. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Test Coverage | [test-coverage.png](diagrams/test-coverage.png) | [test-coverage.drawio](diagrams/test-coverage.drawio) |
| 2 | Test Execution Flow | [test-execution-flow.png](diagrams/test-execution-flow.png) | [test-execution-flow.drawio](diagrams/test-execution-flow.drawio) |
