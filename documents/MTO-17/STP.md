# Software Test Plan (STP)

## MCPOrchestration — MTO-17: Project Scanner — Breadth-First Incremental Scan

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-17 |
| Title | Project Scanner — Breadth-First Incremental Scan |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-17.docx |
| Related FSD | FSD-v1-MTO-17.docx |
| Related TDD | TDD-v1-MTO-17.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | QA Agent | Initial STP — auto-generated from BRD, FSD, and TDD |

---

## 1. Introduction

### 1.1 Purpose

This test plan defines the testing strategy for the **ProjectScanner** component — a coroutine-based service that performs breadth-first scanning of Jira projects, fetching lightweight ticket metadata and caching it in PostgreSQL. Testing covers full scan, incremental scan, resumable scan, concurrent processing, and progress tracking.

### 1.2 Test Objectives

- Verify all 5 use cases (UC-01 through UC-05) from FSD are implemented correctly
- Validate all 18 business rules (BR-01 through BR-18) are enforced
- Ensure non-functional requirements (throughput ≥200 tickets/min, page fetch <5s) are met
- Verify error handling and recovery mechanisms work correctly
- Confirm checkpoint/resume functionality preserves data integrity

### 1.3 References

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-17.docx |
| FSD | FSD-v1-MTO-17.docx |
| TDD | TDD-v1-MTO-17.docx |

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Responsibility | Tools | Automation |
|-------|-------|---------------|-------|------------|
| PBT (Property-Based) | JqlBuilder, MetadataParser input validation | Developer | Kotest Property | 100% automated |
| UT (Unit Test) | Individual classes: JqlBuilder, MetadataParser, BatchUpserter, ProjectScannerImpl | Developer | Kotest + MockK | 100% automated |
| IT (Integration) | DB operations (Exposed + PostgreSQL), JiraRestClient integration | Developer + QA | Testcontainers + Kotest | 100% automated |
| E2E-API | Full scan lifecycle via service API on real server | QA | Ktor testApplication + Testcontainers | 100% automated |
| E2E-UI | N/A — no UI component in this ticket | — | — | — |
| SIT (System Integration) | Full system with real Jira API (sandbox) | QA | Manual + scripted | 80% automated |

### 2.2 Test Types

| Type | Description | Applicable |
|------|-------------|------------|
| Functional Testing | Verify scan, resume, incremental features per FSD | Yes |
| Regression Testing | Ensure MTO-15/MTO-16 integrations not broken | Yes |
| Performance Testing | Verify throughput ≥200 tickets/min | Yes |
| Security Testing | Verify API token not logged, input validation | Yes |
| Reliability Testing | Verify checkpoint/resume after interruption | Yes |
| Concurrency Testing | Verify Semaphore-based throttling works correctly | Yes |

### 2.3 Test Approach

- **Risk-based prioritization**: Focus on data integrity (upsert logic), resumability (checkpoint), and error handling
- **Automation-first**: All levels except visual SIT are fully automated
- **Testcontainers for IT**: Real PostgreSQL in Docker for database tests
- **Mock Jira API for E2E-API**: WireMock or MockK-based HTTP server simulating Jira responses
- **Property-based testing**: Random inputs for JqlBuilder and MetadataParser validation

### 2.4 Entry Criteria

| Level | Entry Criteria |
|-------|---------------|
| UT/PBT | Code compiles, dependencies resolved |
| IT | PostgreSQL Testcontainer starts, Flyway migrations applied |
| E2E-API | Full application context loads, mock Jira server running |
| SIT | Application deployed, real Jira sandbox accessible |

### 2.5 Exit Criteria

| Level | Exit Criteria |
|-------|--------------|
| UT/PBT | 100% test cases pass, ≥90% line coverage on scanner package |
| IT | All DB operations verified, upsert idempotency confirmed |
| E2E-API | Full scan lifecycle completes, resume works, incremental works |
| SIT | Manual verification of real Jira integration, no Critical defects |

---

## 3. Test Scope

### 3.1 Features In Scope

| # | Feature / Story | Priority | FSD Reference | Test Type |
|---|----------------|----------|---------------|-----------|
| 1 | Full Project Scan | High | UC-01, BR-01–BR-06 | UT, IT, E2E-API, SIT |
| 2 | Incremental Scanning | High | UC-02, BR-07–BR-09 | UT, IT, E2E-API |
| 3 | Resumable Scanning | High | UC-03, BR-10–BR-12 | UT, IT, E2E-API |
| 4 | Concurrent Processing | High | UC-04, BR-13–BR-15 | UT, E2E-API |
| 5 | Progress Tracking | Medium | UC-05, BR-16–BR-18 | UT, IT |
| 6 | Error Handling & Retry | High | EF-01–EF-04 | UT, IT, E2E-API |
| 7 | Input Validation | Medium | BR-01, projectKey pattern | PBT, UT |
| 8 | Rate Limit Handling | High | EF-02 | UT, E2E-API |

### 3.2 Features Out of Scope

| # | Feature | Reason |
|---|---------|--------|
| 1 | Deep content fetching | MTO-18 scope |
| 2 | Attachment processing | MTO-19 scope |
| 3 | MCP tool registration | MTO-20 scope |
| 4 | Web dashboard UI | MTO-21 scope |
| 5 | Graph visualization | MTO-22 scope |

---

## 4. Test Environment

### 4.1 Environment Requirements

| Environment | Database | Purpose |
|-------------|----------|---------|
| Local (Testcontainers) | PostgreSQL 16 in Docker | UT, IT, E2E-API |
| SIT | PostgreSQL 16 (dedicated) | System Integration Testing |

### 4.2 Test Data Requirements

| Data Type | Description | Source | Preparation |
|-----------|-------------|--------|-------------|
| Jira API responses | Mock JSON responses (50 issues/page) | Generated fixtures | Create JSON files with realistic data |
| Sync state records | Pre-populated jira_sync_state rows | SQL seed scripts | Insert IDLE/RUNNING/COMPLETED states |
| Ticket cache records | Pre-populated jira_ticket_cache rows | SQL seed scripts | Insert for upsert verification |

### 4.3 External Dependencies

| System | Dependency | Mock/Stub Available |
|--------|-----------|---------------------|
| Jira REST API v3 | Search endpoint responses | Yes — WireMock/MockK HTTP server |
| PostgreSQL 16 | Database for state/cache | Yes — Testcontainers |
| SyncStateManager (MTO-15) | State CRUD operations | Yes — real implementation with Testcontainers |
| JiraRestClient (MTO-16) | HTTP client for Jira | Yes — MockK for UT, real for IT |

---

## 5. Test Schedule

| Phase | Duration | Milestone |
|-------|----------|-----------|
| Test Planning | 1 day | STP + STC approved |
| Test Implementation | 3 days | All automated tests written |
| Test Execution | 1 day | All tests pass |
| Defect Fix & Retest | 1 day | All Critical/Major fixed |

---

## 6. Risk & Mitigation

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | Jira API rate limits during SIT | High | Medium | Use sandbox project with few tickets |
| 2 | Testcontainers startup slow in CI | Medium | Medium | Cache Docker images, parallel test execution |
| 3 | Flaky tests due to concurrency | High | Medium | Use deterministic semaphore permits, fixed seeds |
| 4 | MTO-15/16 not yet merged | High | Low | Use interface mocks, verify integration separately |

---

## 7. Requirements Traceability Matrix (RTM)

| Requirement ID | Requirement Description | Test Case IDs | Coverage |
|----------------|------------------------|---------------|----------|
| UC-01 | Full Project Scan | TC-001, TC-002, TC-003, TC-004, TC-005 | ✅ |
| UC-02 | Incremental Scanning | TC-006, TC-007, TC-008, TC-009 | ✅ |
| UC-03 | Resumable Scanning | TC-010, TC-011, TC-012, TC-013 | ✅ |
| UC-04 | Concurrent Processing | TC-014, TC-015, TC-016 | ✅ |
| UC-05 | Progress Tracking | TC-017, TC-018, TC-019 | ✅ |
| BR-01 | Page size fixed at 50 | TC-001, TC-020 | ✅ |
| BR-02 | JQL ORDER BY updated DESC | TC-020, TC-021 | ✅ |
| BR-03 | Upsert only if newer | TC-022, TC-023 | ✅ |
| BR-04 | Concurrency 1-20, default 5 | TC-014, TC-024 | ✅ |
| BR-05 | Checkpoint after each page | TC-010, TC-025 | ✅ |
| BR-06 | SupervisorJob fault isolation | TC-015, TC-016 | ✅ |
| BR-07 | Incremental JQL with updated > | TC-006, TC-021 | ✅ |
| BR-08 | 1-minute buffer on last_sync_time | TC-007, TC-021 | ✅ |
| BR-09 | 0 results still updates last_sync_time | TC-008 | ✅ |
| BR-10 | Resume uses same JQL | TC-011 | ✅ |
| BR-11 | Upsert is idempotent | TC-022 | ✅ |
| BR-12 | Stale RUNNING > 1 hour → restart | TC-013 | ✅ |
| BR-13 | Semaphore permits = concurrency | TC-014 | ✅ |
| BR-14 | SupervisorJob child isolation | TC-015 | ✅ |
| BR-15 | Scope cancel → children cancel | TC-016 | ✅ |
| BR-16 | total_issues from first response | TC-017 | ✅ |
| BR-17 | synced_issues incremented per batch | TC-018 | ✅ |
| BR-18 | Progress = synced/total * 100 | TC-019 | ✅ |
| EF-01 | Network error → retry → checkpoint | TC-026, TC-027 | ✅ |
| EF-02 | Rate limit → pause → resume | TC-028 | ✅ |
| EF-03 | DB write error → retry → fail | TC-029 | ✅ |
| EF-04 | Parse error → skip ticket | TC-030 | ✅ |
| NFR-01 | Throughput ≥200 tickets/min | TC-031 | ✅ |
| NFR-02 | Page fetch <5s | TC-032 | ✅ |

---

## 8. Test Metrics & Reporting

| Metric | Formula | Target |
|--------|---------|--------|
| Test Execution Rate | Executed / Total × 100% | 100% |
| Pass Rate | Passed / Executed × 100% | ≥ 95% |
| Defect Density | Defects / Test Cases | ≤ 0.1 |
| Critical Defect Count | Count of Critical severity | 0 |
| Code Coverage (scanner package) | Lines covered / Total lines | ≥ 90% |

---

## 9. Test Summary

| Level | Test Cases | Automated | Manual |
|-------|-----------|-----------|--------|
| PBT | 4 | 4 | 0 |
| UT | 15 | 15 | 0 |
| IT | 6 | 6 | 0 |
| E2E-API | 5 | 5 | 0 |
| E2E-UI | 0 | 0 | 0 |
| SIT | 2 | 0 | 2 |
| **Total** | **32** | **30** | **2** |

---

## 10. Appendix

### Glossary

| Term | Definition |
|------|------------|
| PBT | Property-Based Testing |
| UT | Unit Testing |
| IT | Integration Testing |
| E2E-API | End-to-End API Testing |
| SIT | System Integration Testing |
| RTM | Requirements Traceability Matrix |
