# Software Test Plan (STP)

## MCP Tool Orchestration — MTO-12: Auto File Proxy (Input + Output)

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-12 |
| Title | Auto File Proxy — Wrapper tool tự động cho upstream MCP tools nhận/trả file |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-05 |
| Status | Draft |
| Related BRD | BRD-v1.0-MTO-12.docx |
| Related FSD | FSD-v1.0-MTO-12.docx |
| Related TDD | TDD-v1.0-MTO-12.docx |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | QA Agent – QA Engineer | Create document |
| Peer Reviewer | Duc Nguyen – Project Lead | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-05 | QA Agent | Initiate document — auto-generated from BRD, FSD, and TDD |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| Duc Nguyen | ☐ I agree and confirm the test plan in this STP |
| | ☐ I agree and confirm the test plan in this STP |

---

## 1. Introduction

### 1.1 Purpose

This test plan defines the testing strategy, scope, schedule, and resources for the **Auto File Proxy** feature (MTO-12) of the MCP Orchestrator Server. The feature provides transparent file I/O proxying for upstream MCP tools — both input (file_path/file_id → base64 encoding) and output (response file content → output_path save) — with a PostgreSQL-backed lifecycle registry and multi-strategy cleanup.

### 1.2 Test Objectives

- Verify all 9 use cases (UC-001 through UC-009) from FSD are implemented correctly
- Validate all 41 business rules (BR-001 through BR-041) are enforced
- Ensure auto-detection heuristics correctly identify file parameters (input) and file responses (output) without false positives
- Verify path security (traversal prevention, absolute path enforcement, symlink resolution)
- Validate lifecycle cleanup strategies (startup, shutdown, per-request, background TTL)
- Confirm graceful degradation when database is unavailable
- Verify non-functional requirements: proxy overhead < 100ms, 50 concurrent operations, zero orphan files
- Ensure wrapper tools transparently replace originals without breaking existing functionality

### 1.3 References

| Document | Location |
|----------|----------|
| BRD | documents/MTO-12/BRD.md |
| FSD | documents/MTO-12/FSD.md |
| TDD | documents/MTO-12/TDD.md |
| MCP Protocol Specification | https://modelcontextprotocol.io/specification |
| MTO-10 BRD (Base Orchestrator) | documents/MTO-10/BRD.md |

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Automation | Tools |
|-------|-------|------------|-------|
| PBT | Correctness properties for detection heuristics, path validation, config parsing | Automated | kotest-property |
| UT | Unit/edge case tests for individual components (detector, validator, handlers) | Automated | kotest + MockK |
| IT | Integration tests with real PostgreSQL (Testcontainers) and file system | Automated | Ktor test engine + Testcontainers |
| E2E-API | Full MCP protocol E2E tests (tool discovery, execution, cleanup) | Automated | Ktor test host + kotest |
| E2E-UI | N/A — this feature has no UI | N/A | N/A |
| SIT | Manual exploratory testing for edge cases, timing, and degraded mode behavior | Manual | Browser / CLI |

### 2.2 Test Types

| Type | Description | Applicable |
|------|-------------|------------|
| Functional Testing | Verify all use cases, main/alternative/exception flows | Yes |
| Regression Testing | Ensure existing tool discovery and execution still work | Yes |
| Performance Testing | Verify proxy overhead < 100ms, 50 concurrent ops, encode time | Yes |
| Security Testing | Path traversal prevention, temp file permissions, symlink attacks | Yes |
| Integration Testing | PostgreSQL registry, file system I/O, upstream MCP communication | Yes |
| Compatibility Testing | STDIO mode vs HTTP/SSE mode behavior | Yes |

### 2.3 Test Approach

**Risk-Based Prioritization:**
- **Critical Path:** Input proxy (STDIO) → most common usage pattern, tested first
- **Security:** Path validation — any bypass is a Critical defect
- **Data Integrity:** Registry lifecycle — orphan files are a reliability concern
- **Graceful Degradation:** DB unavailability — must not crash the server

**Automation Strategy:**
- All unit tests, integration tests, and E2E-API tests are automated (Kotlin/kotest)
- No E2E-UI tests (feature has no user interface)
- Manual SIT limited to: timing-sensitive cleanup behavior, exploratory edge cases, degraded mode observation

### 2.4 Entry Criteria

| Level | Entry Criteria |
|-------|---------------|
| UT/PBT | Code compiles, all dependencies resolved |
| IT | PostgreSQL Testcontainers available, test fixtures prepared |
| E2E-API | Full application boots with test configuration, mock upstream servers configured |
| SIT | Application deployed to test environment, PostgreSQL accessible, test files prepared |

### 2.5 Exit Criteria

| Level | Exit Criteria |
|-------|--------------|
| UT/PBT | 100% test cases pass, code coverage ≥ 80% for fileproxy package |
| IT | 100% test cases pass, registry CRUD verified with real PostgreSQL |
| E2E-API | 100% test cases pass, all MCP protocol flows verified end-to-end |
| SIT | 100% test cases executed, 0 Critical defects, ≤ 2 Major defects open |

### 2.6 Test Cases Summary

| Level | Count | Automated | Manual |
|-------|-------|-----------|--------|
| PBT | 8 | 8 | 0 |
| UT | 24 | 24 | 0 |
| IT | 12 | 12 | 0 |
| E2E-API | 18 | 18 | 0 |
| E2E-UI | 0 | 0 | 0 |
| SIT | 6 | 0 | 6 |
| **Total** | **68** | **62 (91%)** | **6 (9%)** |

---

## 3. Test Scope

### 3.1 Features In Scope

| # | Feature / Story | Priority | FSD Reference | Test Type |
|---|----------------|----------|---------------|-----------|
| 1 | Auto-Detection of Input File Parameters | High | UC-001, BR-001–BR-004 | UT, PBT, IT |
| 2 | Input File Proxy — STDIO Mode (file_path) | High | UC-002, BR-005–BR-009 | UT, IT, E2E-API |
| 3 | Input File Proxy — HTTP/SSE Mode (file_id) | High | UC-003, BR-010–BR-013 | UT, IT, E2E-API |
| 4 | Database Registry for File Lifecycle Tracking | High | UC-004, BR-014–BR-018 | UT, IT, E2E-API |
| 5 | Wrapper Tool Hiding Original Tools | High | UC-005, BR-019–BR-022 | UT, IT, E2E-API |
| 6 | Output File Proxy — Save to output_path | High | UC-006, BR-023–BR-027 | UT, IT, E2E-API |
| 7 | Auto-Detection of Output File Responses | High | UC-007, BR-028–BR-032 | UT, PBT, IT |
| 8 | Configurable Max File Size | Medium | UC-008, BR-033–BR-036 | UT, PBT |
| 9 | Lifecycle Cleanup (Startup, Shutdown, Per-Request) | High | UC-009, BR-037–BR-041 | IT, E2E-API, SIT |

### 3.2 Features Out of Scope

| # | Feature | Reason |
|---|---------|--------|
| 1 | Streaming/chunked file transfer | Explicitly out of scope per BRD §1.2 |
| 2 | File format conversion | Not part of this feature |
| 3 | File encryption at rest | Not part of this feature |
| 4 | UI for file management | Feature is fully transparent to agents |
| 5 | Upstream MCP server changes | Proxy is transparent to upstream |
| 6 | File compression | Not part of this feature |

---

## 4. Test Environment

### 4.1 Environment Requirements

| Environment | Configuration | Database | Purpose |
|-------------|--------------|----------|---------|
| Local Dev | JDK 21, Kotlin 2.3.20, Ktor 3.4.0 | PostgreSQL 16 (Testcontainers) | UT, IT, E2E-API |
| SIT | Fat JAR deployment, real PostgreSQL | jira_assistant DB | Manual SIT testing |

### 4.2 System Requirements

| Component | Version | Required |
|-----------|---------|----------|
| JDK | 21 | Yes |
| Kotlin | 2.3.20 | Yes |
| PostgreSQL | 16+ | Yes |
| Gradle | 8.x | Yes (build) |
| Testcontainers | 1.21.1 | Yes (IT tests) |

### 4.3 Test Data Requirements

| Data Type | Description | Source | Preparation |
|-----------|-------------|--------|-------------|
| Test files (various sizes) | PDF, TXT, binary files from 1KB to 60MB | Generated | Create via test fixtures |
| Mock upstream tool schemas | Tools with base64 params, output artifacts | JSON fixtures | Define in test resources |
| Pre-seeded DB records | Stale registry records for cleanup tests | SQL insert | Test setup scripts |
| Configuration variants | Different max-size, TTL, per-server overrides | YAML | Test resource files |

### 4.4 External Dependencies

| System | Dependency | Mock/Stub Available |
|--------|-----------|---------------------|
| PostgreSQL | Registry table CRUD | Yes — Testcontainers |
| File System | Read/write/delete files | Yes — @TempDir |
| Upstream MCP Servers | Tool schemas, tool execution | Yes — MockK |

---

## 5. Test Schedule

| Phase | Start Date | End Date | Duration | Milestone |
|-------|-----------|----------|----------|-----------|
| Test Planning | 2026-05-05 | 2026-05-05 | 1 day | STP + STC approved |
| Test Data Preparation | 2026-05-06 | 2026-05-06 | 1 day | Test fixtures ready |
| UT + PBT Execution | 2026-05-07 | 2026-05-08 | 2 days | Unit tests pass |
| IT Execution | 2026-05-09 | 2026-05-10 | 2 days | Integration tests pass |
| E2E-API Execution | 2026-05-11 | 2026-05-12 | 2 days | E2E tests pass |
| SIT Execution | 2026-05-13 | 2026-05-14 | 2 days | SIT sign-off |
| Defect Fix & Retest | 2026-05-15 | 2026-05-16 | 2 days | All Critical/Major fixed |

---

## 6. Resources & Responsibilities

| Role | Name | Responsibility |
|------|------|---------------|
| Test Lead | QA Agent | Test planning, coordination, reporting |
| QA Engineer | QA Agent | Test case design, execution, defect reporting |
| BA | BA Agent | Acceptance criteria clarification |
| Developer | Dev Team | Bug fixing, unit test coverage |
| DevOps | DevOps Team | Environment setup, deployment |
| Project Lead | Duc Nguyen | Review and approve test results |

---

## 7. Risk & Mitigation

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | False positive detection — non-file params flagged | High | Medium | Comprehensive PBT with random schemas; BR-002 enforcement |
| 2 | Path traversal bypass | Critical | Low | Security-focused test cases (ST-001–ST-005); multiple validation layers |
| 3 | Orphan file accumulation | High | Low | Test all 4 cleanup strategies; verify zero orphans after 24h simulation |
| 4 | Large file OOM during base64 encoding | Medium | Medium | Test with files at/near max size; verify streaming for >10MB |
| 5 | Testcontainers PostgreSQL instability | Medium | Low | Pin container version; retry on flaky tests |
| 6 | Concurrent proxy operations race conditions | High | Medium | Load test with 50 parallel requests; verify thread safety |
| 7 | Upstream tool schema changes breaking detection | Medium | Low | Test re-detection on reconnect; verify graceful degradation |

---

## 8. Defect Management

### 8.1 Severity Levels

| Severity | Definition | Example |
|----------|-----------|---------|
| Critical | Security breach, data loss, system crash | Path traversal allows reading /etc/passwd |
| Major | Feature not working, no workaround | Input proxy fails to encode file correctly |
| Minor | Feature works with workaround | Cleanup log message has wrong format |
| Trivial | Cosmetic, no functional impact | Typo in error message |

### 8.2 Priority Levels

| Priority | Definition | SLA (Fix Time) |
|----------|-----------|----------------|
| P1 | Must fix immediately (security, crash) | 4 hours |
| P2 | Must fix before release | 1 business day |
| P3 | Should fix if time permits | 3 business days |
| P4 | Nice to fix, can defer | Next release |

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
| Pass Rate | Passed / Executed × 100% | ≥ 95% |
| Defect Density | Defects / Test Cases | ≤ 0.1 |
| Critical Defect Count | Count of Critical severity | 0 |
| Automation Coverage | Automated / Total × 100% | ≥ 90% |
| Code Coverage (fileproxy package) | Lines covered / Total lines × 100% | ≥ 80% |

### 9.2 Reporting Schedule

| Report | Frequency | Audience |
|--------|-----------|----------|
| Daily Test Status | Daily during execution | Project team |
| Defect Summary | Daily | Dev team + PM |
| Test Completion Report | End of each phase | All stakeholders |

---

## 10. Appendix

### Glossary

| Term | Definition |
|------|------------|
| SIT | System Integration Testing |
| STP | Software Test Plan |
| STC | Software Test Cases |
| PBT | Property-Based Testing |
| UT | Unit Testing |
| IT | Integration Testing |
| E2E-API | End-to-End API Testing |
| MCP | Model Context Protocol |
| STDIO | Standard Input/Output transport mode |
| SSE | Server-Sent Events transport mode |
| TTL | Time-To-Live |

### Assumptions

- PostgreSQL 16+ is available and accessible for integration tests
- Testcontainers Docker environment is available on CI/CD
- Upstream MCP server behavior can be fully mocked for testing
- File system has sufficient space for test file generation (up to 60MB per test)
- Base64 encoding overhead is acceptable for test execution time
