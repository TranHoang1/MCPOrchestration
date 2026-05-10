# Software Test Plan (STP)

## MCPOrchestration — MTO-25: KB Refinery — Dual-Priority Queue (Kotlin Channels)

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-25 |
| Title | KB Refinery — Dual-Priority Queue (Kotlin Channels) |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-25.docx |
| Related FSD | FSD-v1-MTO-25.docx |
| Related TDD | TDD-v1-MTO-25.docx |

---

## 1. Test Strategy

### 1.1 Test Levels

| Level | Scope | Framework | Automation |
|-------|-------|-----------|------------|
| Unit Test (UT) | Individual classes/functions | Kotest + MockK | 100% automated |
| Integration Test (IT) | Component interactions + DB | Kotest + Testcontainers (PostgreSQL) | 100% automated |
| E2E-API | Full queue lifecycle via service API | Kotest + Testcontainers | 100% automated |
| Property-Based Test (PBT) | Invariant verification | Kotest Property Testing | 100% automated |

### 1.2 Test Scope

| Feature | UT | IT | E2E-API | PBT |
|---------|----|----|---------|-----|
| Task Enqueue (HPQ/NPQ) | ✅ | ✅ | ✅ | ✅ |
| Task Processing (Worker) | ✅ | ✅ | ✅ | — |
| Preemption | ✅ | ✅ | ✅ | — |
| Re-queue | ✅ | ✅ | ✅ | — |
| State Tracking (DB) | — | ✅ | ✅ | — |
| Watchdog | ✅ | ✅ | ✅ | — |
| Retry/Backoff | ✅ | ✅ | ✅ | ✅ |
| Crash Recovery | ✅ | ✅ | ✅ | — |
| Validation | ✅ | — | ✅ | ✅ |

### 1.3 Test Environment

| Component | Technology | Configuration |
|-----------|-----------|---------------|
| Test Runner | Kotest 5.9.1 | JUnit Platform |
| Mocking | MockK 1.14.2 | Coroutine support |
| Database | Testcontainers PostgreSQL | Ephemeral container per test class |
| Assertions | Kotest matchers | shouldBe, shouldThrow, eventually |
| Coroutines | kotlinx-coroutines-test | TestDispatcher, advanceTimeBy |

---

## 2. Requirements Traceability Matrix (RTM)

| Requirement (BRD) | Test Cases | Coverage |
|-------------------|-----------|----------|
| STORY 1: HPQ immediate processing | UT-01, UT-02, IT-01, E2E-01 | 100% |
| STORY 2: NPQ batch processing | UT-03, UT-04, IT-02, E2E-02 | 100% |
| STORY 3: Preemption | UT-05, UT-06, IT-03, E2E-03 | 100% |
| STORY 4: Re-queue preempted tasks | UT-07, UT-08, IT-04, E2E-03 | 100% |
| STORY 5: PostgreSQL state tracking | IT-05, IT-06, IT-07, E2E-04 | 100% |
| STORY 6: Watchdog | UT-09, UT-10, IT-08, E2E-05 | 100% |
| STORY 7: Retry with backoff | UT-11, UT-12, IT-09, E2E-06, PBT-01 | 100% |
| STORY 8: Crash recovery | UT-13, UT-14, IT-10, E2E-07 | 100% |
| BR-01: DB-first persistence | IT-05, E2E-04 | 100% |
| BR-05: HPQ priority in select | UT-02, IT-01, E2E-01 | 100% |
| BR-07: Preemption no retry increment | UT-08, IT-04, E2E-03 | 100% |
| BR-14: Max 3 retries | UT-12, IT-09, E2E-06, PBT-01 | 100% |
| NFR: HPQ pickup < 100ms | E2E-01 (timing assertion) | 100% |
| NFR: Preemption < 500ms | E2E-03 (timing assertion) | 100% |

---

## 3. Test Execution Plan

### 3.1 Execution Order

1. **PBT** — Property-based tests (invariant verification)
2. **UT** — Unit tests (fast, no external dependencies)
3. **IT** — Integration tests (requires Testcontainers/PostgreSQL)
4. **E2E-API** — End-to-end lifecycle tests

### 3.2 Pass/Fail Criteria

| Level | Pass Criteria |
|-------|--------------|
| PBT | All properties hold for 1000 iterations |
| UT | 100% pass, 0 failures |
| IT | 100% pass, 0 failures |
| E2E-API | 100% pass, timing assertions within tolerance |

### 3.3 Test Data

Test data files located at `documents/MTO-25/testdata/`:
- `enqueue-tasks.csv` — Sample tasks for enqueue tests
- `stuck-tasks.csv` — Pre-seeded stuck tasks for watchdog tests
- `crash-recovery.csv` — Pre-seeded Processing tasks for recovery tests

---

## 4. Risk-Based Testing

| Risk | Test Focus | Priority |
|------|-----------|----------|
| Preemption causes data loss | Verify re-queue after cancel | Critical |
| Watchdog false positives | Verify threshold timing | High |
| Retry infinite loop | Verify max retry cap | High |
| Channel backpressure deadlock | Verify suspension behavior | Medium |
| DB connection failure during enqueue | Verify exception propagation | Medium |

---

## 5. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Test Coverage | [test-coverage.png](diagrams/test-coverage.png) | [test-coverage.drawio](diagrams/test-coverage.drawio) |
