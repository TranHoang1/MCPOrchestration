# Software Test Plan (STP)

## MCPOrchestration — MTO-47: Unified Sync Pipeline — Multi-Dimensional Jira Indexing

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-47 |
| Title | Unified Sync Pipeline — Multi-Dimensional Jira Indexing |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-14 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-47.docx |
| Related FSD | FSD-v1-MTO-47.docx |
| Related TDD | TDD-v1-MTO-47.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-14 | QA Agent | Initiate document — auto-generated from BRD, FSD, and TDD |

---

## 1. Introduction

### 1.1 Purpose

This test plan covers the **Unified Sync Pipeline** feature (MTO-47) which consolidates two separate Jira sync tools (`jira_project_sync` and `kb_sync_trigger`) into a single shared `sync-pipeline` Gradle module with multi-dimensional indexing capabilities.

### 1.2 Test Objectives

- Verify both sync tools produce identical data through the shared pipeline (AC-01)
- Validate per-project ticket indexing with full metadata and type classification (AC-02)
- Verify per-person comment storage with author identity (AC-03)
- Validate multi-dimensional graph traversal (User→Ticket, Ticket→Ticket, Feature→Ticket) (AC-04)
- Verify AI-powered feature auto-detection produces correct groupings (AC-05)
- Validate dimension configuration API accepts new dimensions (AC-06)
- Verify all data has source provenance (source_ref) (AC-07)
- Validate attachment metadata indexing (AC-08)
- Verify incremental sync only processes changed tickets (AC-09)
- Validate crash recovery and resume from checkpoint (AC-10)
- Verify PII masking and role-based content access (BR-34 to BR-39)
- Validate streaming pipeline performance targets (§7.4 FSD)

### 1.3 References

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-47.docx |
| FSD | FSD-v1-MTO-47.docx |
| TDD | TDD-v1-MTO-47.docx |

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | ID | Scope | Responsibility | Tools |
|-------|-----|-------|---------------|-------|
| Property-Based Testing | PBT | Data model invariants, hash determinism, dimension extraction purity | Developer | Kotest Property |
| Unit Testing | UT | Individual classes: dimensions, hashers, parsers, config loaders | Developer | JUnit 5 + MockK |
| Integration Testing | IT | DB writes, Jira API interaction, vector indexing, DI wiring | Developer + QA | Testcontainers (PostgreSQL), Ktor testApplication |
| E2E API Testing | E2E-API | Full sync pipeline via MCP tool invocation on real server | QA | Ktor Test Host, real PostgreSQL |
| E2E UI Testing | E2E-UI | Sync Dashboard SSE events, Graph Viewer data refresh | QA | Playwright |
| System Integration Testing | SIT | Cross-module: orchestrator-server + kb-server produce same data | QA | Manual + automated comparison |

### 2.2 Test Types

| Type | Description | Applicable |
|------|-------------|------------|
| Functional Testing | Verify all UC-01 to UC-07 from FSD | Yes |
| Regression Testing | Ensure existing jira_project_sync still works during migration | Yes |
| Performance Testing | Verify sync targets (100 tickets < 3min, incremental < 20s) | Yes |
| Security Testing | PII masking, role-based access, download URL not exposed | Yes |
| Reliability Testing | Crash recovery, checkpoint resume, stale state detection | Yes |
| Compatibility Testing | Multi-provider AI (Ollama, LMStudio, OpenAI) graceful degradation | Yes |

### 2.3 Test Approach

**Risk-based prioritization:**
- **Critical path:** Sync execution → dimension processing → storage → state management
- **High risk:** Concurrent fetch (race conditions), batch writes (data integrity), crash recovery
- **Medium risk:** AI feature detection (non-deterministic), vector indexing (external dependency)

**Automation strategy:**
- PBT + UT + IT: 100% automated (Gradle test suite)
- E2E-API: 100% automated (Ktor test host with real DB)
- E2E-UI: 80% automated (Playwright), 20% manual (visual verification)
- SIT: 70% automated (comparison scripts), 30% manual (cross-server verification)

### 2.4 Entry Criteria

| Level | Entry Criteria |
|-------|---------------|
| UT/IT | Code compiles, dependencies resolved, Testcontainers available |
| E2E-API | sync-pipeline module built, PostgreSQL running, test Jira project available |
| E2E-UI | Server deployed to test environment, dashboard accessible |
| SIT | Both orchestrator-server and kb-server deployed, same DB, test project synced |

### 2.5 Exit Criteria

| Level | Exit Criteria |
|-------|--------------|
| UT/IT | 100% test cases executed, 0 Critical defects, code coverage ≥ 80% |
| E2E-API | All sync scenarios pass, performance targets met |
| E2E-UI | Dashboard shows correct real-time data, graph refreshes after sync |
| SIT | Both tools produce byte-identical data in DB (AC-01 verified) |

---

## 3. Test Scope

### 3.1 Features In Scope

| # | Feature / Story | Priority | FSD Reference | Test Level |
|---|----------------|----------|---------------|------------|
| 1 | Unified Pipeline Execution (both tools → same result) | Critical | UC-01, BR-01 to BR-04 | IT, E2E-API, SIT |
| 2 | Per-Project Ticket Indexing with Type Classification | High | UC-01, BR-05 to BR-07 | UT, IT, E2E-API |
| 3 | Per-Person Comment Storage | High | UC-03, BR-08 to BR-12 | UT, IT, E2E-API |
| 4 | Multi-Dimensional Relationship Graph | High | UC-04, BR-13 to BR-16 | UT, IT, E2E-API |
| 5 | AI Feature Auto-Detection | Medium | UC-05, BR-17 to BR-20 | UT, IT |
| 6 | Config-Driven Dimension API | Medium | UC-06, BR-21 to BR-25 | UT, IT, E2E-API |
| 7 | Source Provenance Tracking | High | UC-07, BR-26 to BR-29 | UT, IT |
| 8 | Attachment Metadata Indexing | Medium | UC-08, BR-30 to BR-33 | UT, IT |
| 9 | Content Hash Deduplication | High | FSD §7.2 | PBT, UT |
| 10 | Streaming Pipeline (Flow + Channels) | High | FSD §7.3 | IT, E2E-API |
| 11 | Batch DB Writes | High | TDD §4.7 | IT |
| 12 | Batch Vector Embedding | Medium | TDD §4.7 | IT |
| 13 | Crash Recovery & Checkpoint Resume | High | FSD UC-01 Error Handling | IT, E2E-API |
| 14 | PII Masking & Role-Based Access | High | BRD §3.5, BR-34 to BR-39 | UT, IT, E2E-API |
| 15 | Incremental Sync (hash-based skip) | High | AC-09 | IT, E2E-API |
| 16 | AI Provider Factory (multi-provider) | Medium | TDD §6 | UT, IT |
| 17 | Graceful Degradation (AI/Vector unavailable) | Medium | FSD §8.4 | IT |
| 18 | Sync State Machine (IDLE→RUNNING→COMPLETED/FAILED) | High | TDD §3.1 | UT, IT |
| 19 | GenericFieldDimension (UI-configured) | Medium | FSD §5.1 | UT, IT |
| 20 | Dimension Config REST API | Medium | FSD §2.6 | E2E-API |

### 3.2 Features Out of Scope

| # | Feature | Reason |
|---|---------|--------|
| 1 | UI implementation for dimension configuration | Out of scope per BRD §1.3 |
| 2 | Attachment content extraction (OCR, PDF parsing) | Out of scope per BRD §1.3 |
| 3 | Migration of old data | Full re-sync approach, no data migration testing |
| 4 | Semantic clustering for feature detection | Future enhancement, only epic-based + AI hybrid tested |

---

## 4. Test Environment

### 4.1 Environment Requirements

| Environment | Setup | Database | Purpose |
|-------------|-------|----------|---------|
| Local Dev | localhost | PostgreSQL 16 + pgvector (Testcontainers) | UT, IT |
| Test Server | localhost:8080 (orchestrator) + localhost:8081 (kb-server) | Shared PostgreSQL | E2E-API, SIT |
| Staging | TBD | Dedicated PostgreSQL | E2E-UI, Performance |

### 4.2 External Dependencies

| System | Dependency | Mock/Stub Available |
|--------|-----------|---------------------|
| Jira Cloud API | REST API for ticket data | Yes — MockK + recorded responses |
| Ollama | Local LLM for embeddings + AI analysis | Yes — mock EmbeddingService |
| PostgreSQL + pgvector | Primary storage | Yes — Testcontainers |
| Qdrant | Vector DB (optional) | Yes — mock VectorDbClient |

### 4.3 Test Data Requirements

| Data Type | Description | Source | Preparation |
|-----------|-------------|--------|-------------|
| Jira Project (small) | 10 tickets, 3 types, 20 comments | Mock JSON fixtures | Pre-built test fixtures |
| Jira Project (medium) | 100 tickets, 5 types, 200 comments | Mock JSON fixtures | Generated from template |
| Jira Project (large) | 1000 tickets | Mock JSON fixtures | Performance test only |
| Dimension configs | 5 built-in + 2 custom | SQL seed | Migration script |
| AI responses | Feature detection results | Mock responses | Pre-recorded |

---

## 5. Test Schedule

| Phase | Duration | Milestone |
|-------|----------|-----------|
| Test Planning (STP + STC) | 1 day | STP + STC approved |
| Test Data Preparation | 1 day | Fixtures + seeds ready |
| PBT + UT Development | 3 days | Unit tests pass |
| IT Development | 3 days | Integration tests pass |
| E2E-API Development | 2 days | API tests pass |
| E2E-UI Development | 1 day | UI tests pass |
| SIT Execution | 1 day | Cross-server verification |
| Performance Testing | 1 day | Targets met |
| Defect Fix & Retest | 2 days | All Critical/Major fixed |

---

## 6. Resources & Responsibilities

| Role | Responsibility |
|------|---------------|
| QA Engineer | Test case design, IT/E2E development, execution, defect reporting |
| Developer | PBT/UT development, bug fixing, Testcontainers setup |
| SA | Architecture review of test approach, performance test design |
| SM | Test plan approval, defect triage, schedule coordination |

---

## 7. Risk & Mitigation

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | Jira API rate limiting during E2E tests | Medium | High | Use mock fixtures for most tests, real API only for SIT |
| 2 | AI feature detection non-deterministic | Low | High | Test structure (features exist), not exact content |
| 3 | Testcontainers slow on CI | Medium | Medium | Cache Docker images, parallel test execution |
| 4 | Concurrent pipeline race conditions | High | Medium | Property-based testing for invariants, stress tests |
| 5 | Vector embedding service unavailable | Low | Medium | Graceful degradation tests verify fallback behavior |
| 6 | Large project sync timeout | Medium | Low | Performance tests with 1000-ticket fixture |

---

## 8. Defect Management

### 8.1 Severity Levels

| Severity | Definition | Example |
|----------|-----------|---------|
| Critical | Data loss, sync produces wrong data, crash without recovery | Duplicate entries on re-sync, state stuck in RUNNING |
| Major | Feature not working, dimension skipped silently | Comments not indexed, hash skip fails |
| Minor | Non-critical behavior issue | Log message unclear, progress percentage off by 1 |
| Trivial | Cosmetic | Config field name inconsistency |

### 8.2 Priority & SLA

| Priority | Definition | SLA |
|----------|-----------|-----|
| P1 | Data integrity issue | Fix within 4 hours |
| P2 | Feature broken, no workaround | Fix within 1 day |
| P3 | Feature degraded, workaround exists | Fix within 3 days |
| P4 | Nice to fix | Next sprint |

---

## 9. Test Metrics

| Metric | Formula | Target |
|--------|---------|--------|
| Test Execution Rate | Executed / Total × 100% | 100% |
| Pass Rate | Passed / Executed × 100% | ≥ 95% |
| Code Coverage (UT+IT) | Lines covered / Total lines | ≥ 80% |
| Critical Defect Count | Count of Critical severity | 0 |
| Performance Pass Rate | Metrics within target / Total metrics | 100% |
| Automation Rate | Automated / Total test cases | ≥ 85% |

---

## 10. Test Case Summary by Level

| Level | Count | Automated | Manual |
|-------|-------|-----------|--------|
| PBT (Property-Based) | 12 | 12 | 0 |
| UT (Unit) | 45 | 45 | 0 |
| IT (Integration) | 28 | 28 | 0 |
| E2E-API | 18 | 18 | 0 |
| E2E-UI | 8 | 6 | 2 |
| SIT | 10 | 7 | 3 |
| **Total** | **121** | **116** | **5** |

### Automation Breakdown

- **Automated:** 116/121 = 95.9%
- **Manual (SIT visual verification + E2E-UI visual):** 5/121 = 4.1%

---

## 11. Requirements Traceability Summary

| Category | Total Requirements | Test Cases Covering | Coverage |
|----------|-------------------|--------------------|---------| 
| Use Cases (UC-01 to UC-07) | 7 | 78 | 100% |
| Business Rules (BR-01 to BR-45) | 45 | 95 | 100% |
| Acceptance Criteria (AC-01 to AC-10) | 10 | 45 | 100% |
| Non-Functional Requirements | 9 | 18 | 100% |
| Error Handling Scenarios | 7 | 14 | 100% |

---

## 12. Appendix

### Glossary

| Term | Definition |
|------|------------|
| PBT | Property-Based Testing — generates random inputs to verify invariants |
| SIT | System Integration Testing — cross-module end-to-end verification |
| Dimension | A configurable axis of indexing (metadata, comments, users, features) |
| Source Ref | Provenance identifier linking data to its Jira origin |
| Content Hash | SHA-256 hash for change detection (skip unchanged tickets) |

### Assumptions

- Testcontainers Docker is available on CI/CD environment
- Test Jira project with known data exists for SIT
- Ollama is available locally for AI integration tests (or graceful degradation tested)
- PostgreSQL 16+ with pgvector extension available
