# Software Test Plan (STP)

## Jira Project Sync Service — MTO-15: Database Schema & Sync State Management

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-15 |
| Title | Database Schema & Sync State Management |
| Author | QA Agent |
| Version | 2.0 |
| Date | 2026-05-07 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-15.docx |
| Related FSD | FSD-v1-MTO-15.docx |
| Related TDD | TDD-v1-MTO-15.docx |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | QA Agent – QA Engineer | Create document |
| Peer Reviewer | SM Agent – Scrum Master | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-14 | QA Agent | Initiate document — auto-generated from BRD, FSD, and TDD |
| 2.0 | 2026-05-07 | QA Agent | Complete rewrite — expanded to 6 test levels (PBT, UT, IT, E2E-API, E2E-UI, SIT), added RTM, enhanced test data strategy |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm the test plan in this STP |
| | ☐ I agree and confirm the test plan in this STP |

---

## 1. Introduction

### 1.1 Purpose

This test plan defines the comprehensive testing strategy for the Database Schema & Sync State Management module (MTO-15). This module is the foundational persistence layer for the Jira Project Sync Service (Epic MTO-14), consisting of:

- 4 PostgreSQL tables: `jira_sync_state`, `jira_ticket_cache`, `jira_ticket_graph`, `jira_attachment_queue`
- `SyncStateManager` class with state machine enforcement and optimistic locking
- `JiraSyncDatabaseInitializer` for idempotent migration execution
- 8 performance indexes including partial indexes
- Repository classes with UPSERT and batch operations

### 1.2 Test Objectives

- Verify all 7 functional requirements (UC-01 through UC-07) from FSD are implemented correctly
- Validate all 35 business rules (BR-01 through BR-35) are enforced
- Ensure state machine transitions follow the defined lifecycle (IDLE → RUNNING → COMPLETED/FAILED/PAUSED)
- Verify database migration scripts are idempotent and create correct schema
- Validate performance indexes improve query execution as specified
- Ensure non-functional requirements (50ms state updates, 100 tickets/sec UPSERT, 10ms queue polling) are met
- Verify optimistic locking prevents race conditions in concurrent scenarios
- Validate input validation rejects malformed data with appropriate error messages
- Confirm regression safety — existing tables (server_config, tool_toggle_state, file_proxy_registry) are unaffected

### 1.3 References

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-15.docx |
| FSD | FSD-v1-MTO-15.docx |
| TDD | TDD-v1-MTO-15.docx |


---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Responsibility | Tools | Automation |
|-------|-------|---------------|-------|------------|
| Property-Based Testing (PBT) | State machine invariants, data model constraints | Developer | Kotest Property Testing (kotlinx-property) | 100% automated |
| Unit Testing (UT) | SyncStateManagerImpl logic, validation, error handling, repository method logic | Developer | Kotest 5.9.1 FunSpec + MockK 1.14.2 | 100% automated |
| Integration Testing (IT) | Full DB operations with real PostgreSQL, migration scripts, UPSERT, indexes, concurrency | Developer + QA | Kotest + Testcontainers 1.21.1 (PostgreSQL 16) | 100% automated |
| E2E-API Testing | Complete sync lifecycle via internal Kotlin API (no HTTP — internal module) | QA | Kotest + Testcontainers + real DI (Koin) | 100% automated |
| E2E-UI Testing | N/A — backend-only module, no UI | — | — | N/A |
| System Integration Testing (SIT) | Performance benchmarks, concurrent access, regression against existing tables | QA Team | Kotest + Testcontainers + custom benchmarks | 95% automated, 5% manual review |

> **Note:** E2E-UI is not applicable for MTO-15 as this is a backend-only persistence module with no HTTP endpoints or UI components.

### 2.2 Test Types

| Type | Description | Applicable | Level |
|------|-------------|------------|-------|
| Functional Testing | Verify state machine transitions, CRUD operations, business rules | Yes | UT, IT, E2E-API |
| Property-Based Testing | Verify state machine invariants hold for arbitrary input sequences | Yes | PBT |
| Regression Testing | Ensure existing DB tables are unaffected by migration | Yes | IT, SIT |
| Performance Testing | Verify 50ms state updates, 100 tickets/sec UPSERT, 10ms queue polling | Yes | SIT |
| Security Testing | Verify SQL injection prevention, input validation | Yes | UT, IT |
| Concurrency Testing | Verify optimistic locking, race condition handling | Yes | IT, SIT |
| Boundary Testing | Verify field length limits, null handling, edge values | Yes | UT, IT |
| Idempotency Testing | Verify migration and UPSERT operations are safely repeatable | Yes | IT |

### 2.3 Test Approach

**Automation-First Strategy:**
- All tests are fully automated using Kotest + Testcontainers — no manual test execution required
- Property-based tests generate random state transition sequences to find edge cases
- Integration tests use real PostgreSQL via Testcontainers (no H2 or in-memory substitutes)
- Performance tests use timing assertions with statistical confidence (p95 < threshold)
- Each test class gets its own Testcontainers instance — no shared state between tests

**Risk-Based Prioritization:**
1. **Critical:** State machine transitions (BR-02, BR-21–BR-26) — incorrect transitions corrupt sync state
2. **Critical:** Optimistic locking (EF-03) — race conditions cause data inconsistency
3. **High:** Database migration idempotency (BR-28–BR-31) — failed migration blocks startup
4. **High:** UPSERT operations (BR-06, BR-09, BR-15) — data integrity depends on correct conflict handling
5. **Medium:** Performance indexes (BR-32–BR-35) — affects query speed but not correctness
6. **Low:** Edge cases (self-referencing graph, max retry count) — unlikely in normal operation

### 2.4 Entry Criteria

| Level | Entry Criteria |
|-------|---------------|
| PBT | Kotest property testing dependency available, SyncStatus enum defined |
| Unit Testing | Code compiles, MockK dependencies available, SyncStateManagerImpl class exists |
| Integration Testing | Testcontainers available, Docker running, PostgreSQL 16 image pulled |
| E2E-API | All UT + IT pass, Koin module configured with all sync components |
| SIT | All UT + IT + E2E-API pass, full module deployed to test environment |

### 2.5 Exit Criteria

| Level | Exit Criteria |
|-------|--------------|
| PBT | 1000 random sequences pass without invariant violation |
| Unit Testing | 100% test cases executed, 0 failures, all state transitions verified |
| Integration Testing | 100% test cases executed, 0 failures, migration idempotency confirmed |
| E2E-API | Full sync lifecycle (create → run → progress → complete) passes end-to-end |
| SIT | All performance targets met, concurrency tests pass, regression suite green |


---

## 3. Test Scope

### 3.1 Features In Scope

| # | Feature / Story | Priority | FSD Reference | Test Levels |
|---|----------------|----------|---------------|-------------|
| 1 | Sync State Table (jira_sync_state) | Critical | UC-01, BR-01–BR-05 | PBT + UT + IT + E2E-API |
| 2 | Ticket Cache Table (jira_ticket_cache) | High | UC-02, BR-06–BR-10 | UT + IT + E2E-API |
| 3 | Ticket Graph Table (jira_ticket_graph) | High | UC-03, BR-11–BR-14 | UT + IT |
| 4 | Attachment Queue Table (jira_attachment_queue) | High | UC-04, BR-15–BR-20 | UT + IT + E2E-API |
| 5 | SyncStateManager Class (state machine) | Critical | UC-05, BR-21–BR-27 | PBT + UT + IT + E2E-API |
| 6 | Database Migration Scripts | High | UC-06, BR-28–BR-31 | IT + SIT |
| 7 | Performance Indexes | Medium | UC-07, BR-32–BR-35 | IT + SIT |

### 3.2 Features Out of Scope

| # | Feature | Reason |
|---|---------|--------|
| 1 | Jira API integration | Separate story under MTO-14 |
| 2 | Knowledge Base ingestion pipeline | Separate story under MTO-14 |
| 3 | Background job scheduling | Separate story under MTO-14 |
| 4 | UI/dashboard monitoring | Not part of MTO-15 |
| 5 | Data archival/purging | Future iteration |
| 6 | HTTP endpoints | MTO-15 is internal Kotlin API only |

---

## 4. Test Environment

### 4.1 Environment Requirements

| Environment | Configuration | Database | Purpose |
|-------------|--------------|----------|---------|
| PBT | JVM + Kotest Property | No DB (pure logic) | State machine invariant verification |
| Unit Test | JVM + MockK | Mocked (no real DB) | State machine logic, validation |
| Integration Test | JVM + Testcontainers | PostgreSQL 16 (Docker) | Full DB operations, migration, UPSERT |
| E2E-API | JVM + Koin + Testcontainers | PostgreSQL 16 (Docker) | Full lifecycle with real DI |
| Performance Test | JVM + Testcontainers | PostgreSQL 16 (Docker) | Benchmark timing assertions |

### 4.2 Technology Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Kotlin | 2.3.20 | Implementation language |
| Kotest | 5.9.1 | Test framework (FunSpec style) |
| Kotest Property | 5.9.1 | Property-based testing |
| MockK | 1.14.2 | Mocking library for unit tests |
| Testcontainers | 1.21.1 | Docker-based PostgreSQL for integration tests |
| PostgreSQL | 16-alpine | Database (Docker image) |
| HikariCP | 6.2.1 | Connection pool |
| Koin Test | 4.1.1 | DI testing support |
| Gradle | 8.x | Build tool (`./gradlew test`) |

### 4.3 Test Data Requirements

| Data Type | Description | Source | Preparation |
|-----------|-------------|--------|-------------|
| Project keys | Valid Jira project keys (e.g., "MTO", "PROJ", "TEST") | Inline fixtures | TestFixtures.kt factory methods |
| Ticket keys | Valid ticket keys (e.g., "MTO-15", "PROJ-100") | Inline fixtures | TestFixtures.kt factory methods |
| Sync states | All 5 status values (IDLE, RUNNING, PAUSED, COMPLETED, FAILED) | Enum | SyncStatus enum |
| Content hashes | Valid SHA-256 hex strings (64 chars) | Generated | SHA-256 of test data |
| Attachment metadata | Filenames, MIME types, URLs, sizes | Inline fixtures | TestFixtures.kt factory methods |
| Graph relationships | Source/target keys with link types and categories | Inline fixtures | TestFixtures.kt factory methods |
| Invalid inputs | Empty strings, null values, oversized strings, SQL injection attempts | Inline | Boundary test data |
| Batch data | Lists of 1, 10, 100, 1000 items | Generated | Loop-generated test data |

### 4.4 External Dependencies

| System | Dependency | Mock/Stub Available |
|--------|-----------|---------------------|
| PostgreSQL | Real database instance via Testcontainers | Yes — Testcontainers PostgreSQL 16 |
| Docker | Required for Testcontainers | Must be running on CI/local machine |
| HikariCP | Connection pool | Real instance in IT/E2E, mocked in UT |
| Koin | DI framework | Real in E2E-API, not used in UT/IT |


---

## 5. Test Schedule

| Phase | Start Date | End Date | Duration | Milestone |
|-------|-----------|----------|----------|-----------|
| Test Planning | 2026-05-07 | 2026-05-07 | 1 day | STP + STC approved |
| Test Implementation | 2026-05-08 | 2026-05-09 | 2 days | Test code written |
| PBT + Unit Test Execution | 2026-05-09 | 2026-05-09 | 0.5 day | All PBT + UT pass |
| Integration Test Execution | 2026-05-09 | 2026-05-10 | 1 day | All IT pass |
| E2E-API Test Execution | 2026-05-10 | 2026-05-10 | 0.5 day | Full lifecycle verified |
| Performance/SIT Execution | 2026-05-10 | 2026-05-11 | 1 day | NFR targets met |
| Defect Fix & Retest | 2026-05-11 | 2026-05-12 | 1 day | All Critical/Major fixed |
| Sign-off | 2026-05-12 | 2026-05-12 | 0.5 day | QA sign-off |

---

## 6. Resources & Responsibilities

| Role | Name | Responsibility |
|------|------|---------------|
| Test Lead | QA Agent | Test planning, STP/STC creation, test execution coordination |
| QA Engineer | QA Agent | Test case design, automated test execution, defect reporting |
| Developer | Dev Agent | Unit test implementation, bug fixing, code coverage |
| Solution Architect | SA Agent | Technical review of test approach, performance targets validation |
| Scrum Master | SM Agent | Pipeline coordination, quality gate enforcement |

---

## 7. Risk & Mitigation

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | Docker not available on CI | High | Low | Ensure Docker is installed on CI runners; document Docker requirement |
| 2 | Testcontainers startup timeout | Medium | Medium | Set container startup timeout to 60s; use reusable containers in dev |
| 3 | PostgreSQL version mismatch | Medium | Low | Pin Docker image to postgres:16-alpine; match production version |
| 4 | Concurrent test interference | High | Medium | Each test class gets its own Testcontainers instance; no shared state |
| 5 | Performance test flakiness | Medium | Medium | Use statistical assertions (p95 < threshold); run 100+ iterations |
| 6 | Migration script breaks existing tables | High | Low | Integration test verifies existing tables are untouched after migration |
| 7 | Property-based test finds edge case | Medium | Medium | Fix implementation, add regression test for the specific case |
| 8 | Optimistic locking race in tests | Medium | Low | Use coroutine-based concurrency simulation with controlled timing |

---

## 8. Defect Management

### 8.1 Severity Levels

| Severity | Definition | Example |
|----------|-----------|---------|
| Critical | Data corruption, state machine deadlock, migration destroys existing data | Invalid state transition allows COMPLETED → RUNNING without reset |
| Major | Feature not working but workaround exists | UPSERT fails for tickets with special characters in labels |
| Minor | Non-critical behavior deviation | Error message not descriptive enough |
| Trivial | Code style, naming convention | Variable name doesn't follow project convention |

### 8.2 Priority Levels

| Priority | Definition | SLA (Fix Time) |
|----------|-----------|----------------|
| P1 | Must fix immediately — blocks other MTO-14 stories | 4 hours |
| P2 | Must fix before integration with other stories | 1 business day |
| P3 | Should fix if time permits | 3 business days |
| P4 | Nice to fix, can defer to next sprint | Next sprint |

### 8.3 Defect Lifecycle

```
New → Open → In Progress → Fixed → Ready for Retest → Verified → Closed
                                                     → Reopened → In Progress
```

---

## 9. Test Metrics & Reporting

### 9.1 Metrics

| Metric | Formula | Target |
|--------|---------|--------|
| Test Execution Rate | Executed / Total × 100% | 100% |
| Pass Rate | Passed / Executed × 100% | ≥ 98% |
| Defect Density | Defects / Test Cases | ≤ 0.05 |
| Critical Defect Count | Count of Critical severity | 0 |
| Code Coverage (Unit) | Lines covered / Total lines × 100% | ≥ 90% |
| Business Rule Coverage | BRs with ≥1 test case / Total BRs × 100% | 100% |
| Performance Target Met | NFRs meeting target / Total NFRs × 100% | 100% |
| PBT Iterations | Random sequences tested | ≥ 1000 |

### 9.2 Reporting Schedule

| Report | Frequency | Audience |
|--------|-----------|----------|
| Test Execution Summary | After each test run | Dev team + SM |
| Defect Report | On defect discovery | Dev team |
| Final Test Report | End of testing phase | All stakeholders |


---

## 10. Test Case Summary

### 10.1 Test Case Distribution by Level

| Level | Count | Automation | Priority |
|-------|-------|------------|----------|
| Property-Based Testing (PBT) | 5 | 100% automated | High |
| Unit Testing (UT) | 20 | 100% automated | High |
| Integration Testing (IT) | 22 | 100% automated | High |
| E2E-API Testing | 6 | 100% automated | High |
| E2E-UI Testing | 0 | N/A (backend-only) | — |
| System Integration Testing (SIT) | 8 | 95% automated | Medium |
| **Total** | **61** | **99% automated** | — |

### 10.2 Test Case Distribution by Feature

| Feature | PBT | UT | IT | E2E-API | SIT | Total |
|---------|-----|----|----|---------|-----|-------|
| Sync State Table (UC-01) | 2 | 2 | 3 | 1 | 1 | 9 |
| Ticket Cache Table (UC-02) | — | 3 | 4 | 1 | 1 | 9 |
| Ticket Graph Table (UC-03) | — | 3 | 3 | — | — | 6 |
| Attachment Queue Table (UC-04) | — | 3 | 4 | 1 | 1 | 9 |
| SyncStateManager (UC-05) | 3 | 9 | 4 | 3 | 3 | 22 |
| Migration Scripts (UC-06) | — | — | 2 | — | 1 | 3 |
| Performance Indexes (UC-07) | — | — | 2 | — | 1 | 3 |
| **Total** | **5** | **20** | **22** | **6** | **8** | **61** |

### 10.3 Business Rule Coverage Summary (RTM)

| BR Range | Feature | Test Cases | Coverage |
|----------|---------|------------|----------|
| BR-01 to BR-05 | Sync State Table | TC-PBT-01..02, TC-UT-01..02, TC-IT-01..03 | 100% |
| BR-06 to BR-10 | Ticket Cache Table | TC-UT-03..05, TC-IT-04..07 | 100% |
| BR-11 to BR-14 | Ticket Graph Table | TC-UT-06..08, TC-IT-08..10 | 100% |
| BR-15 to BR-20 | Attachment Queue Table | TC-UT-09..11, TC-IT-11..14 | 100% |
| BR-21 to BR-27 | SyncStateManager | TC-PBT-03..05, TC-UT-12..20, TC-IT-15..18 | 100% |
| BR-28 to BR-31 | Migration Scripts | TC-IT-19..20, TC-SIT-07 | 100% |
| BR-32 to BR-35 | Performance Indexes | TC-IT-21..22, TC-SIT-08 | 100% |

**Overall Coverage:**

| Category | Total | Covered | Coverage % |
|----------|-------|---------|------------|
| Use Cases (UC-01..UC-07) | 7 | 7 | 100% |
| Business Rules (BR-01..BR-35) | 35 | 35 | 100% |
| FSD Test Scenarios (TC-01..TC-20) | 20 | 20 | 100% |
| Exception Flows | 9 | 9 | 100% |
| Alternative Flows | 11 | 11 | 100% |
| **Overall** | **82** | **82** | **100%** |

---

## 11. Appendix

### Glossary

| Term | Definition |
|------|------------|
| PBT | Property-Based Testing |
| UT | Unit Testing |
| IT | Integration Testing |
| E2E-API | End-to-End API Testing |
| SIT | System Integration Testing |
| STP | Software Test Plan |
| STC | Software Test Cases |
| RTM | Requirements Traceability Matrix |
| BR | Business Rule |
| UC | Use Case |
| NFR | Non-Functional Requirement |

### Assumptions

- Docker is available on all development and CI machines for Testcontainers
- PostgreSQL 16 Docker image is accessible (no network restrictions)
- Kotest + MockK + Testcontainers + Kotest Property dependencies are in build.gradle.kts
- No UI testing required — this is a backend-only module
- Test data is ephemeral — each test creates and destroys its own data within the container lifecycle
- E2E-API tests use real Koin DI with Testcontainers PostgreSQL (no mocks)
- Performance targets are measured on developer machines (not production hardware)

---

*End of Document*
