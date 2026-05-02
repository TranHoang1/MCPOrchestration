# Software Test Plan (STP)

## MCP Orchestration Server — MTO-5: Create MCP Tool Orchestration

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-5 |
| Title | Create MCP Tool Orchestration |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-02 |
| Status | Draft |
| Related BRD | BRD-v2-MTO-5.docx |
| Related FSD | FSD-v1-MTO-5.docx |
| Related TDD | TDD-v1-MTO-5.docx |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | QA Agent – QA Engineer | Create document |
| Peer Reviewer | Tech Lead – Senior Engineer | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-02 | QA Agent | Initiate document — auto-generated from BRD, FSD, and TDD |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm the test plan in this STP |
| | ☐ I agree and confirm the test plan in this STP |

---

## 1. Introduction

### 1.1 Purpose

This test plan defines the testing strategy, scope, schedule, and resources for the **MCP Orchestration Server** — a Kotlin/Ktor application that acts as an intelligent proxy between the Kiro AI IDE and multiple upstream MCP Servers. The server exposes two MCP tools (`find_tools` and `execute_dynamic_tool`) to minimize AI context window consumption while providing access to an unlimited number of upstream tools via semantic search and dynamic proxying.

This is a **backend-only** system with no UI. Testing focuses on API-level verification, property-based correctness, unit logic, and integration with external services (Vector DB, Embedding API, upstream MCP servers).

### 1.2 Test Objectives

- Verify all 5 Use Cases (UC-01 through UC-05) from FSD are implemented correctly
- Validate all 27 Business Rules (BR-01 through BR-27) are enforced
- Ensure all 9 Error Codes are returned correctly under specified conditions
- Verify fallback mechanisms (keyword search when Vector DB / Embedding service unavailable)
- Validate non-functional requirements: latency (<500ms find_tools, <100ms proxy overhead), scalability (1000+ tools, 50+ servers), and reliability (auto-reconnect, graceful degradation)
- Confirm security requirements: no secrets in logs, environment variable injection, input validation
- Achieve >80% test coverage for core logic

### 1.3 References

| Document | Location |
|----------|----------|
| BRD | BRD-v2-MTO-5.docx |
| FSD | FSD-v1-MTO-5.docx |
| TDD | TDD-v1-MTO-5.docx |
| MCP Specification | https://modelcontextprotocol.io/specification |

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Automation | Tools |
|-------|-------|------------|-------|
| PBT | Correctness properties — random inputs for validation, serialization, config parsing | Automated | kotest-property |
| UT | Unit/edge case tests — individual service methods, error handling, data transformations | Automated | JUnit 5 + kotest assertions |
| IT | Integration tests — Ktor testApplication, Qdrant Testcontainers, mock upstream servers | Automated | Ktor test engine + Testcontainers |
| E2E-API | REST/MCP endpoint E2E — full Orchestrator with real Qdrant, mock upstreams | Automated | Ktor client + JUnit 5 + TestMcpClient |

> **Note:** This is a backend MCP server with no UI. E2E-UI and manual SIT levels are not applicable.

### 2.2 Test Types

| Type | Description | Applicable |
|------|-------------|------------|
| Functional Testing | Verify find_tools and execute_dynamic_tool per FSD use cases | Yes |
| Regression Testing | Ensure existing features are not broken after changes | Yes |
| Performance Testing | Verify response times (find_tools <500ms, proxy <100ms) | Yes |
| Security Testing | Verify no secrets in logs, input validation, env var injection | Yes |
| Reliability Testing | Verify fallback mechanisms, auto-reconnect, graceful degradation | Yes |
| Compatibility Testing | Verify stdio and HTTP transport modes | Yes |

### 2.3 Test Approach

- **Risk-based prioritization**: High-priority tests cover core tool discovery and execution flows, error handling, and fallback mechanisms
- **Property-based testing (PBT)**: Use random input generation to verify invariants — e.g., "find_tools always returns ≤ top_k results", "similarity scores are always in [0.0, 1.0]"
- **Mock-driven integration**: Upstream MCP servers are mocked using `MockUpstreamServer` (from TDD §11.2). Qdrant uses Testcontainers for real vector DB behavior
- **Contract testing**: Verify JSON-RPC request/response formats match MCP specification
- **Automated-first**: All test levels (PBT, UT, IT, E2E-API) are fully automated. No manual testing required for this backend system

### 2.4 Entry Criteria

| Level | Entry Criteria |
|-------|---------------|
| PBT | Core data classes and validation logic implemented |
| UT | Individual service classes implemented with interface contracts |
| IT | Ktor application module configured, Testcontainers available, mock servers ready |
| E2E-API | Full Orchestrator runnable, Qdrant container available, mock upstream servers configured |

### 2.5 Exit Criteria

| Level | Exit Criteria |
|-------|--------------|
| PBT | All property tests pass with 1000+ random inputs each, 0 failures |
| UT | 100% UT test cases executed, 0 Critical/Major defects, >80% code coverage |
| IT | 100% IT test cases executed, 0 Critical defects, all integrations verified |
| E2E-API | 100% E2E scenarios executed, 0 Critical defects, performance targets met |

---

## 3. Test Scope

### 3.1 Features In Scope

| # | Feature / Story | Priority | FSD Reference | Test Levels |
|---|----------------|----------|---------------|-------------|
| 1 | Tool Discovery — find_tools (semantic search) | High | UC-01, BR-01..BR-07, STORY-1 | PBT, UT, IT, E2E-API |
| 2 | Tool Execution — execute_dynamic_tool (proxy) | High | UC-02, BR-08..BR-13, STORY-2 | PBT, UT, IT, E2E-API |
| 3 | Tool Registration & Indexing | High | UC-03, BR-14..BR-18, STORY-3 | UT, IT, E2E-API |
| 4 | Server Configuration Management (hot-reload) | Medium | UC-04, BR-19..BR-22, STORY-4 | UT, IT, E2E-API |
| 5 | Health Monitoring & Auto-Reconnect | High | UC-05, BR-23..BR-27, STORY-5 | UT, IT, E2E-API |
| 6 | Schema in Discovery Results | High | STORY-6 | UT, IT |
| 7 | Auto Metadata Extraction | High | STORY-7 | UT, IT |
| 8 | Fallback Mechanisms (keyword search) | High | BR-05, AF-02, AF-03 | UT, IT, E2E-API |
| 9 | Error Handling (9 error codes) | High | FSD §9 | PBT, UT, IT, E2E-API |
| 10 | MCP Protocol Compliance (initialize, tools/list, ping) | High | TDD §3.4 | IT, E2E-API |
| 11 | Security — secrets handling, input validation | Medium | FSD §7, TDD §7 | UT, IT |
| 12 | Performance — latency targets | Medium | FSD §8, BR-01, BR-08 | E2E-API |

### 3.2 Features Out of Scope

| # | Feature | Reason |
|---|---------|--------|
| 1 | UI/Dashboard for tool management | Future phase — not part of MTO-5 |
| 2 | IDE plugin development | Separate project |
| 3 | Production deployment infrastructure (K8s) | DevOps responsibility, not functional testing |
| 4 | HuggingFace local embeddings | Deferred to v1.1 per TDD §12.3 |
| 5 | MCP resources and prompts | Out of scope for v1.0 per TDD §12.3 |
| 6 | Horizontal scaling (HTTP mode multi-instance) | Future phase |

---

## 4. Test Environment

### 4.1 Environment Requirements

| Environment | Configuration | Purpose |
|-------------|--------------|---------|
| Local Dev | JVM 21+, Gradle 8.x, Docker (for Qdrant Testcontainers) | PBT, UT, IT execution |
| CI/CD | GitHub Actions / Jenkins with Docker support | Automated test execution on PR |
| E2E | Full Orchestrator process + Qdrant container + Mock upstream servers | E2E-API test execution |

### 4.2 External Dependencies

| System | Dependency | Mock/Stub Available |
|--------|-----------|---------------------|
| Qdrant Vector DB | Vector storage and search | Yes — Testcontainers (`org.testcontainers:qdrant`) |
| OpenAI Embedding API | Embedding generation | Yes — Mock HTTP server returning fixed embeddings |
| Upstream MCP Servers | Tool providers | Yes — `MockUpstreamServer` (TDD §11.3) |

### 4.3 Test Data Requirements

| Data Type | Description | Source | Preparation |
|-----------|-------------|--------|-------------|
| Tool definitions | Sample MCP tool metadata (name, description, input_schema) | Fixture files (`tool-definitions.json`) | Pre-defined in test fixtures |
| Embedding vectors | Pre-computed 768-dim float arrays | Generated from mock embedding service | Mock returns deterministic vectors |
| Configuration files | Test `application.yml` with mock server configs | Test fixtures (`test-config.yml`) | Pre-defined per test scenario |
| Invalid inputs | Empty strings, oversized queries, malformed JSON | Generated by PBT and test data CSVs | Defined in `testdata/*.csv` |

---

## 5. Test Schedule

| Phase | Start Date | End Date | Duration | Milestone |
|-------|-----------|----------|----------|-----------|
| Test Planning (STP + STC) | 2026-05-02 | 2026-05-02 | 1 day | STP + STC approved |
| Test Data & Fixture Preparation | 2026-05-03 | 2026-05-03 | 1 day | Test data ready |
| PBT + UT Development | 2026-05-04 | 2026-05-06 | 3 days | PBT + UT passing |
| IT Development | 2026-05-07 | 2026-05-09 | 3 days | IT passing |
| E2E-API Development | 2026-05-10 | 2026-05-12 | 3 days | E2E-API passing |
| Defect Fix & Retest | 2026-05-13 | 2026-05-14 | 2 days | All Critical/Major fixed |
| Performance Validation | 2026-05-15 | 2026-05-15 | 1 day | Latency targets met |
| Test Completion Report | 2026-05-16 | 2026-05-16 | 1 day | Final report delivered |

---

## 6. Resources & Responsibilities

| Role | Name | Responsibility |
|------|------|---------------|
| Test Lead | QA Agent | Test planning, coordination, reporting |
| QA Engineer | QA Agent | Test case design, PBT/UT/IT/E2E-API development, execution |
| Developer | DEV Agent | Bug fixing, unit test coverage, mock server implementation |
| BA | BA Agent | Acceptance criteria clarification |
| DevOps | DevOps Agent | CI/CD pipeline, Testcontainers setup |

### Tools

| Tool | Purpose |
|------|---------|
| JUnit 5 | Test framework for UT, IT, E2E-API |
| kotest-property | Property-based testing |
| Ktor Test Host | Integration testing with Ktor application |
| Testcontainers | Qdrant container for IT/E2E |
| MockUpstreamServer | Mock MCP servers for testing |
| Gradle | Build and test execution |
| Jira (MTO project) | Defect tracking |

---

## 7. Risk & Mitigation

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | Qdrant Testcontainers slow to start | Medium | Medium | Use shared container across test classes; cache container image |
| 2 | OpenAI API mock doesn't match real behavior | Medium | Low | Use recorded responses from real API; validate embedding dimensions |
| 3 | Mock upstream servers don't cover all MCP edge cases | High | Medium | Test with real MCP servers in E2E; comprehensive mock scenarios |
| 4 | Flaky tests due to timing (health checks, reconnect) | Medium | High | Use deterministic timers in tests; increase timeouts; use `advanceTimeBy` |
| 5 | Coroutine-based code hard to test deterministically | Medium | Medium | Use `runTest` with `TestCoroutineScheduler`; inject dispatchers |
| 6 | Vector similarity scores vary with embedding model changes | Low | Low | Pin embedding model version; use threshold-based assertions |

---

## 8. Defect Management

### 8.1 Severity Levels

| Severity | Definition | Example |
|----------|-----------|---------|
| Critical | Core tool discovery/execution completely broken, data loss | find_tools returns no results for any query; execute_dynamic_tool crashes server |
| Major | Feature not working correctly, workaround exists | Keyword fallback not triggered when Vector DB down; wrong error code returned |
| Minor | Non-critical issue, cosmetic or edge case | Log message format incorrect; metric name typo |
| Trivial | Documentation typo, minor code style issue | Comment typo; unused import |

### 8.2 Priority Levels

| Priority | Definition | SLA (Fix Time) |
|----------|-----------|----------------|
| P1 | Must fix immediately — blocks testing | 4 hours |
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
| Defect Fix Rate | Fixed / Total Defects × 100% | ≥ 90% |
| Code Coverage (UT) | Lines covered / Total lines × 100% | ≥ 80% |
| PBT Property Coverage | Properties verified / Total properties | 100% |

### 9.2 Reporting Schedule

| Report | Frequency | Audience |
|--------|-----------|----------|
| Daily Test Status | Daily during test execution | Project team |
| Defect Summary | Daily | Dev team + PM |
| Test Completion Report | End of each test level | All stakeholders |

---

## 10. Test Cases Summary

### 10.1 Test Levels Summary

| Level | Count | Automated | Manual |
|-------|-------|-----------|--------|
| PBT | 12 | 12 | 0 |
| UT | 35 | 35 | 0 |
| IT | 25 | 25 | 0 |
| E2E-API | 15 | 15 | 0 |
| **Total** | **87** | **87 (100%)** | **0 (0%)** |

### 10.2 Test Cases by Feature

| Feature | PBT | UT | IT | E2E-API | Total |
|---------|-----|----|----|---------|-------|
| find_tools (UC-01) | 4 | 8 | 6 | 4 | 22 |
| execute_dynamic_tool (UC-02) | 3 | 8 | 5 | 4 | 20 |
| Tool Registration & Indexing (UC-03) | 2 | 5 | 4 | 2 | 13 |
| Configuration Management (UC-04) | 2 | 5 | 4 | 2 | 13 |
| Health Monitoring (UC-05) | 1 | 5 | 4 | 2 | 12 |
| MCP Protocol Compliance | 0 | 2 | 1 | 1 | 4 |
| Security & Input Validation | 0 | 2 | 1 | 0 | 3 |
| **Total** | **12** | **35** | **25** | **15** | **87** |

---

## 11. Appendix

### Glossary

| Term | Definition |
|------|------------|
| PBT | Property-Based Testing — tests that verify invariants with random inputs |
| UT | Unit Testing — tests for individual functions/methods in isolation |
| IT | Integration Testing — tests for component interactions with real/mocked dependencies |
| E2E-API | End-to-End API Testing — tests against a fully running server instance |
| MCP | Model Context Protocol — open standard for AI tool communication |
| JSON-RPC | JSON Remote Procedure Call — the wire protocol used by MCP |
| Vector DB | Vector Database for semantic similarity search |
| ANN | Approximate Nearest Neighbor — fast vector search algorithm |

### Assumptions

- Docker is available in the test environment for Testcontainers (Qdrant)
- OpenAI API is mocked in all test levels (no real API calls during testing)
- Upstream MCP servers are mocked using `MockUpstreamServer` from TDD §11.3
- JVM 21+ is available for running Kotlin coroutines and virtual threads
- Test execution is sequential per test class but parallel across classes
