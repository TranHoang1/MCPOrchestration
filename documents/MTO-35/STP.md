# Software Test Plan (STP)

## MCPOrchestration — MTO-35: KB Refinery — Semantic Entity Linking

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-35 |
| Title | KB Refinery — Semantic Entity Linking |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-35.docx |
| Related FSD | FSD-v1-MTO-35.docx |
| Related TDD | TDD-v1-MTO-35.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | QA Agent | Initial test plan |

---

## 1. Introduction

### 1.1 Purpose

This test plan defines the testing strategy, scope, and approach for the **Semantic Entity Linking** feature (MTO-35). The feature provides automatic detection and creation of semantic links between KB entries using embedding-based cosine similarity with HNSW index.

### 1.2 Test Objectives

- Verify all 5 use cases (UC-01 through UC-05) from FSD are implemented correctly
- Validate all 10 business rules (BR-01 through BR-10) are enforced
- Ensure cosine similarity calculations produce correct results
- Verify HNSW index queries return accurate nearest neighbors
- Validate batch processing handles large datasets correctly
- Confirm graceful degradation when external services are unavailable
- Verify performance targets: findSimilar < 100ms, linkEntry < 500ms

### 1.3 References

| Document | Location |
|----------|----------|
| BRD | documents/MTO-35/BRD.md |
| FSD | documents/MTO-35/FSD.md |
| TDD | documents/MTO-35/TDD.md |

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Automation | Tools |
|-------|-------|------------|-------|
| PBT | Property-based tests for similarity scoring, threshold filtering, batch chunking | Automated | kotest-property |
| UT | Unit tests for EntityLinkingServiceImpl, EntityLinkRepositoryImpl, config validation | Automated | kotest + MockK |
| IT | Integration tests with real PostgreSQL (Testcontainers) + mocked vector DB | Automated | kotest + Testcontainers |
| E2E-API | Full flow tests: ingest → embed → search → link → query | Automated | Ktor test host + kotest |

### 2.2 Test Types

| Type | Description | Applicable |
|------|-------------|------------|
| Functional Testing | Verify all use cases and business rules | Yes |
| Performance Testing | Verify query latency < 100ms, throughput ≥ 50/sec | Yes |
| Integration Testing | PostgreSQL + VectorDB interaction | Yes |
| Error Handling Testing | Graceful degradation on service failures | Yes |
| Boundary Testing | Threshold edge cases, max content length, batch limits | Yes |

### 2.3 Test Approach

**Risk-Based Prioritization:**
- **Critical Path:** linkEntry flow (embed → index → search → persist) — most common operation
- **Accuracy:** Cosine similarity threshold filtering — incorrect filtering = bad links
- **Resilience:** External service failures — must not crash the system
- **Performance:** Query latency — user-facing operation

### 2.4 Entry Criteria

| Level | Entry Criteria |
|-------|---------------|
| UT/PBT | Code compiles, all dependencies resolved |
| IT | PostgreSQL Testcontainers available, test fixtures prepared |
| E2E-API | All unit tests pass, integration environment ready |

### 2.5 Exit Criteria

| Level | Exit Criteria |
|-------|---------------|
| UT/PBT | 100% pass rate, ≥ 90% line coverage on service classes |
| IT | All DB operations verified with real PostgreSQL |
| E2E-API | All use cases verified end-to-end |

---

## 3. Requirements Traceability Matrix (RTM)

| Requirement | Source | Test Cases | Priority |
|-------------|--------|------------|----------|
| UC-01: Find Similar | FSD §3.1 | UT-01, UT-02, E2E-01 | High |
| UC-02: Link Entry | FSD §3.2 | UT-03, UT-04, IT-01, E2E-02 | High |
| UC-03: Batch Link | FSD §3.3 | UT-05, UT-06, IT-02, E2E-03 | High |
| UC-04: Get Links | FSD §3.4 | UT-07, IT-03, E2E-04 | High |
| UC-05: Configure Threshold | FSD §3.5 | UT-08, PBT-01 | Medium |
| BR-01: Threshold range 0.0–1.0 | FSD §4 | PBT-01, UT-09 | High |
| BR-02: Default threshold 0.75 | FSD §4 | UT-10 | Medium |
| BR-03: Bidirectional links | FSD §4 | IT-04, UT-11 | High |
| BR-04: No self-links | FSD §4 | PBT-02, UT-12 | High |
| BR-05: Unique constraint | FSD §4 | IT-05 | High |
| BR-06: Default type SEMANTIC | FSD §4 | UT-13 | Low |
| BR-07: Default topK = 10 | FSD §4 | UT-14 | Low |
| BR-08: Batch chunk size 50 | FSD §4 | UT-15, PBT-03 | Medium |
| BR-09: Persist across restarts | FSD §4 | IT-06 | High |
| BR-10: Embedding dimension match | FSD §4 | UT-16 | High |
| NFR: findSimilar < 100ms | FSD §10 | E2E-05 | High |
| NFR: linkEntry < 500ms | FSD §10 | E2E-06 | Medium |
| NFR: Graceful degradation | FSD §10 | UT-17, UT-18 | High |
| Story #1: Auto-linked tickets | BRD §2 | E2E-02, E2E-04 | High |
| Story #2: Similarity scores | BRD §2 | UT-01, E2E-01 | High |
| Story #3: Configurable threshold | BRD §2 | UT-08, PBT-01 | Medium |
| Story #4: Batch on ingest | BRD §2 | UT-05, E2E-03 | High |

---

## 4. Test Environment

### 4.1 Infrastructure

| Component | Specification |
|-----------|--------------|
| JDK | 21 |
| Kotlin | 2.3.20 |
| Test Framework | Kotest 5.9.1 |
| Mocking | MockK 1.14.2 |
| Containers | Testcontainers 1.21.1 (PostgreSQL 16) |
| Build Tool | Gradle (./gradlew test) |

### 4.2 Test Data

| Dataset | Description | Size |
|---------|-------------|------|
| Small corpus | 10 KB entries with known similarity | 10 entries |
| Medium corpus | 100 KB entries for batch testing | 100 entries |
| Edge cases | Empty content, max-length content, special chars | 5 entries |

---

## 5. Pass/Fail Criteria

### 5.1 Overall

| Criteria | Threshold |
|----------|-----------|
| UT pass rate | 100% |
| IT pass rate | 100% |
| E2E pass rate | 100% |
| PBT pass rate | 100% (1000 iterations each) |
| Performance targets met | ≥ 95% of runs within target |

### 5.2 Defect Severity

| Severity | Definition | Action |
|----------|------------|--------|
| Critical | Links created with wrong scores, data corruption | Block release |
| Major | Performance target missed by >50%, batch fails silently | Must fix before release |
| Minor | Log messages incorrect, non-critical edge case | Fix in next iteration |

---

## 6. Risks & Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Vector DB flaky in CI | Tests fail intermittently | Medium | Use MockK for UT, Testcontainers only for IT |
| Embedding API rate limits in tests | Slow test execution | Low | Mock EmbeddingService in all tests |
| Large batch tests slow | CI timeout | Medium | Limit batch test size to 100 entries |
| Floating point comparison issues | False test failures | Low | Use tolerance (±0.0001) for score comparisons |
