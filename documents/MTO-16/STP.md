# Software Test Plan (STP)

## Jira Project Sync Service — MTO-16: Jira REST Client — Direct API Integration

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-16 |
| Title | Jira REST Client — Direct API Integration |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2025-07-14 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-16.docx |
| Related FSD | FSD-v1-MTO-16.docx |
| Related TDD | TDD-v1-MTO-16.docx |

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

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm the test plan in this STP |
| | ☐ I agree and confirm the test plan in this STP |

---

## 1. Introduction

### 1.1 Purpose

This Software Test Plan defines the testing strategy, scope, resources, and schedule for verifying the Jira REST Client module (MTO-16). The module provides a dedicated HTTP communication layer for the Jira Project Sync Service (Epic MTO-14), implementing direct API integration with Jira Cloud/Server REST API v3.

The test plan covers verification of:
- 4 API endpoint methods: `searchIssues`, `getIssue`, `getAttachments`, `downloadAttachment`
- Token bucket rate limiting with 429 response handling
- Exponential backoff retry logic with jitter
- Custom sealed exception hierarchy (7 exception types)
- Environment-based configuration with fail-fast validation
- Security controls (SSRF prevention, credential protection)

### 1.2 Test Objectives

- Verify all 7 Use Cases (UC-01 through UC-07) function correctly per FSD specification
- Validate all 43 Business Rules (BR-01 through BR-43) are enforced
- Ensure rate limiting correctly throttles requests and handles 429 responses
- Verify retry logic applies correct exponential backoff with jitter
- Confirm exception hierarchy provides accurate error classification
- Validate configuration fail-fast behavior with comprehensive error reporting
- Ensure SSRF protection blocks unauthorized download URLs
- Verify credential protection (API token never logged or exposed)

### 1.3 References

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-16.docx |
| FSD | FSD-v1-MTO-16.docx |
| TDD | TDD-v1-MTO-16.docx |
| Jira REST API v3 | https://developer.atlassian.com/cloud/jira/platform/rest/v3/ |
| Ktor Client Docs | https://ktor.io/docs/client-overview.html |

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Responsibility | Tools | Automation |
|-------|-------|---------------|-------|------------|
| PBT (Property-Based) | Input validation invariants, rate limiter properties | Developer | Kotest Property Testing | 100% Automated |
| UT (Unit Test) | Individual functions: validation, config parsing, exception mapping | Developer | Kotest + MockK | 100% Automated |
| IT (Integration Test) | HTTP client ↔ Jira API interaction, retry + rate limiting end-to-end | Developer + QA | WireMock + Kotest | 100% Automated |
| E2E-API | Full client lifecycle on real/mock Jira server | QA | WireMock Standalone | 100% Automated |
| E2E-UI | N/A — backend module, no UI | — | — | N/A |
| SIT (System Integration) | Verify client works within full MCP Orchestrator application | QA | Manual + Logs | Manual |

### 2.2 Test Types

| Type | Description | Applicable |
|------|-------------|------------|
| Functional Testing | Verify 4 API methods work per FSD use cases | Yes |
| Business Rule Testing | Validate all 43 business rules enforced | Yes |
| Error Handling Testing | Verify exception hierarchy and error classification | Yes |
| Performance Testing | Verify rate limiter throughput and retry latency | Yes |
| Security Testing | SSRF prevention, credential protection | Yes |
| Concurrency Testing | Rate limiter thread-safety under concurrent access | Yes |
| Configuration Testing | Fail-fast validation, default values, boundary values | Yes |
| Regression Testing | Ensure existing MCP Orchestrator features unaffected | Yes |
| Usability Testing | N/A — backend library module | No |
| Compatibility Testing | N/A — JVM-only module | No |

### 2.3 Test Approach

**Automation-First Strategy:** This is a backend library module with no UI. All functional testing is automated using Kotest (BDD-style specs) + MockK (mocking) + WireMock (HTTP stubbing).

**Risk-Based Prioritization:**
- **High Risk:** Rate limiting concurrency, retry logic timing, SSRF prevention → extensive PBT + IT coverage
- **Medium Risk:** API response parsing, pagination handling → UT + IT coverage
- **Low Risk:** Configuration defaults, logging format → UT coverage

**Test Pyramid:**
- PBT: ~15 properties (input validation, rate limiter invariants)
- UT: ~40 test cases (unit-level logic)
- IT: ~25 test cases (WireMock integration)
- E2E-API: ~10 test cases (full lifecycle scenarios)
- SIT: ~5 manual verification scenarios

### 2.4 Entry Criteria

| Level | Entry Criteria |
|-------|---------------|
| UT/PBT | Code compiles, all dependencies resolved |
| IT | WireMock configured, test fixtures prepared |
| E2E-API | WireMock standalone running with full Jira API stubs |
| SIT | MCP Orchestrator deployed with Jira client module, real Jira instance accessible |

### 2.5 Exit Criteria

| Level | Exit Criteria |
|-------|--------------|
| UT/PBT | 100% test cases pass, ≥90% line coverage on target package |
| IT | 100% test cases pass, all retry/rate-limit scenarios verified |
| E2E-API | 100% test cases pass, full lifecycle (search → get → attachments → download) verified |
| SIT | All manual scenarios pass, no Critical/Major defects open |

---

## 3. Test Scope

### 3.1 Features In Scope

| # | Feature / Story | Priority | FSD Reference | Test Type |
|---|----------------|----------|---------------|-----------|
| 1 | Search Jira Issues by JQL | High | UC-01, BR-01..BR-08 | PBT + UT + IT + E2E-API |
| 2 | Fetch Single Jira Issue | High | UC-02, BR-09..BR-11 | UT + IT + E2E-API |
| 3 | Retrieve Attachment Metadata | High | UC-03, BR-12..BR-14 | UT + IT + E2E-API |
| 4 | Download Attachment Binary | High | UC-04, BR-15..BR-18 | UT + IT + E2E-API |
| 5 | Token Bucket Rate Limiting | High | UC-05, BR-19..BR-24 | PBT + UT + IT |
| 6 | Exponential Backoff Retry | High | UC-06, BR-25..BR-33 | PBT + UT + IT |
| 7 | Configuration & Validation | High | UC-07, BR-34..BR-43 | PBT + UT |

### 3.2 Features Out of Scope

| # | Feature | Reason |
|---|---------|--------|
| 1 | Jira write operations (create/update/delete) | Not part of MTO-16 scope |
| 2 | OAuth 2.0 authentication | Only Basic Auth implemented |
| 3 | Webhook integration | Separate story under MTO-14 |
| 4 | Response caching | Handled by MTO-15 cache layer |
| 5 | Background job scheduling | Separate story under MTO-14 |
| 6 | KB ingestion pipeline | Separate story under MTO-14 |

---

## 4. Test Environment

### 4.1 Environment Requirements

| Environment | URL | Database | Purpose |
|-------------|-----|----------|---------|
| DEV (Local) | localhost (WireMock on port 8089) | N/A | Unit + Integration Testing |
| SIT | WireMock Standalone (Docker) | N/A | E2E-API Testing |
| UAT | Real Jira Cloud instance | N/A | System Integration Testing |

### 4.2 Technology Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Kotlin | 1.9+ | Implementation language |
| Kotest | 5.x | Test framework (BDD specs) |
| MockK | 1.13+ | Mocking framework |
| WireMock | 3.x | HTTP API stubbing |
| Ktor Client CIO | 2.x | HTTP client under test |
| JVM | 17+ | Runtime |
| Gradle | 8.x | Build tool |

### 4.3 Test Data Requirements

| Data Type | Description | Source | Preparation |
|-----------|-------------|--------|-------------|
| JQL queries | Valid/invalid JQL strings | Test fixtures | Hardcoded in test classes |
| Issue keys | Valid format (MTO-16), invalid format | Test fixtures | Hardcoded |
| Jira API responses | JSON response bodies for all endpoints | WireMock stubs | JSON fixture files |
| Attachment URLs | Valid (same domain) and invalid (different domain) | Test fixtures | Hardcoded |
| Environment variables | Valid/invalid/missing config combinations | Test setup | Set via System properties |

### 4.4 External Dependencies

| System | Dependency | Mock/Stub Available |
|--------|-----------|---------------------|
| Jira REST API v3 | HTTP endpoints for search, issue, attachment | Yes — WireMock stubs |
| Network | HTTP/HTTPS connectivity | Yes — localhost WireMock |
| Environment | OS environment variables | Yes — System.setProperty in tests |

---

## 5. Test Schedule

| Phase | Start Date | End Date | Duration | Milestone |
|-------|-----------|----------|----------|-----------|
| Test Planning | 2025-07-14 | 2025-07-14 | 1 day | STP + STC approved |
| Test Data Preparation | 2025-07-15 | 2025-07-15 | 1 day | WireMock stubs + fixtures ready |
| UT/PBT Development | 2025-07-15 | 2025-07-17 | 3 days | Unit tests pass |
| IT Development | 2025-07-16 | 2025-07-18 | 3 days | Integration tests pass |
| E2E-API Execution | 2025-07-18 | 2025-07-19 | 2 days | E2E scenarios pass |
| SIT Execution | 2025-07-19 | 2025-07-20 | 2 days | SIT sign-off |
| Defect Fix & Retest | 2025-07-20 | 2025-07-21 | 2 days | All Critical/Major fixed |

---

## 6. Resources & Responsibilities

| Role | Name | Responsibility |
|------|------|---------------|
| Test Lead | QA Agent | Test planning, coordination, reporting |
| QA Engineer | QA Agent | Test case design, execution, defect reporting |
| Developer | DEV Agent | Bug fixing, unit test implementation |
| Scrum Master | SM Agent | Quality gate review, coordination |

---

## 7. Risk & Mitigation

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | WireMock stubs don't match real Jira API responses | High | Medium | Use actual Jira API response samples as WireMock fixtures |
| 2 | Rate limiter timing tests flaky due to coroutine scheduling | Medium | High | Use virtual time (Kotest coroutine test) for deterministic timing |
| 3 | Retry backoff timing assertions brittle | Medium | High | Assert delay ranges (±30%) rather than exact values |
| 4 | Real Jira instance unavailable for SIT | Medium | Low | Maintain WireMock standalone as fallback |
| 5 | Concurrent test execution interferes with rate limiter state | High | Medium | Isolate rate limiter instance per test, use TestScope |
| 6 | Environment variable tests pollute other tests | Medium | Medium | Use dedicated test config, restore env after each test |

---

## 8. Defect Management

### 8.1 Severity Levels

| Severity | Definition | Example |
|----------|-----------|---------|
| Critical | Core functionality broken, no workaround | Rate limiter allows unlimited requests, SSRF bypass |
| Major | Feature not working correctly, workaround exists | Retry logic doesn't respect max retries, wrong exception type |
| Minor | Non-critical issue, cosmetic | Log message format incorrect, correlation ID missing |
| Trivial | Documentation, naming | Typo in error message, inconsistent naming |

### 8.2 Priority Levels

| Priority | Definition | SLA (Fix Time) |
|----------|-----------|----------------|
| P1 | Must fix immediately (security, data loss) | 4 hours |
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
| Code Coverage (target package) | Lines covered / Total lines × 100% | ≥ 90% |
| Business Rule Coverage | BRs with ≥1 test / Total BRs × 100% | 100% |
| Use Case Coverage | UCs with ≥1 test / Total UCs × 100% | 100% |

### 9.2 Reporting Schedule

| Report | Frequency | Audience |
|--------|-----------|----------|
| Daily Test Status | Daily during testing | Project team |
| Defect Summary | Daily | Dev team + SM |
| Test Completion Report | End of each test level | All stakeholders |

---

## 10. Test Case Distribution by Level

| Level | Count | Coverage Focus |
|-------|-------|----------------|
| PBT (Property-Based) | 8 | Input validation invariants, rate limiter mathematical properties |
| UT (Unit Test) | 52 | Individual method logic, exception mapping, config parsing |
| IT (Integration Test) | 22 | HTTP interaction, retry sequences, rate limiting behavior |
| E2E-API | 6 | Full lifecycle scenarios, multi-step workflows |
| SIT (Manual) | 5 | Real Jira connectivity, end-to-end sync verification |
| **Total** | **93** | **100% UC + BR coverage** |

---

## 11. Appendix

### Glossary

| Term | Definition |
|------|------------|
| PBT | Property-Based Testing — generates random inputs to verify invariants |
| UT | Unit Testing — tests individual functions in isolation |
| IT | Integration Testing — tests component interactions with real/stubbed dependencies |
| E2E-API | End-to-End API Testing — tests full API lifecycle on running server |
| SIT | System Integration Testing — tests within full application context |
| WireMock | HTTP mock server for stubbing external API responses |
| Kotest | Kotlin test framework supporting BDD-style specs |
| MockK | Kotlin mocking library for creating test doubles |
| JQL | Jira Query Language |
| SSRF | Server-Side Request Forgery |
| RTM | Requirements Traceability Matrix |

### Assumptions

- WireMock 3.x is available as test dependency in build.gradle.kts
- Kotest 5.x with property testing module is available
- MockK 1.13+ is available for mocking
- JVM 17+ runtime for test execution
- Gradle 8.x for build and test execution
- No real Jira instance required for UT/IT/E2E-API levels (WireMock sufficient)
- Real Jira instance available for SIT level only

