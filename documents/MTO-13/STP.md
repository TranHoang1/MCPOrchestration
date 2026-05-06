# Software Test Plan (STP)

## MCPOrchestration â MTO-13: HTTP Streamable Transport & Multi-Module Architecture

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-13 |
| Title | HTTP Streamable Transport & Multi-Module Architecture |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-06 |
| Status | Draft |
| Related BRD | documents/MTO-13/BRD.md |
| Related FSD | documents/MTO-13/FSD.md |
| Related TDD | documents/MTO-13/TDD.md |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | QA Agent â QA Engineer | Create document |
| Peer Reviewer | Duc Nguyen â Project Lead | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-06 | QA Agent | Initiate document â auto-generated from BRD, FSD, and TDD for MTO-13 (9 parts, 56 ACs) |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | â I agree and confirm the test plan in this STP |
| | â I agree and confirm the test plan in this STP |

---

## 1. Introduction

### 1.1 Purpose

This Software Test Plan defines the comprehensive testing strategy for MTO-13, which introduces HTTP Streamable transport mode, hidden utility tools, Gradle multi-module refactoring, MCP Client Bridges (Kotlin + Node.js), Smart Tool Promotion, and three already-implemented features (Stream Write, Embed Images, Large-Text Input Proxy) to the MCP Orchestrator system.

The plan covers 9 parts (AâI) with 56 acceptance criteria, ensuring complete functional and non-functional verification of all new and existing capabilities.

### 1.2 Test Objectives

- Verify all 56 acceptance criteria across 9 parts are correctly implemented
- Validate HTTP Streamable transport conforms to MCP specification 2025-03-26
- Ensure Gradle multi-module refactor preserves all existing functionality (zero regression)
- Verify Smart Tool Promotion achieves 73-80% token reduction
- Validate MCP Client Bridges (Kotlin + Node.js) correctly proxy all MCP operations
- Confirm stream_write_file, embed_images, and large-text proxy tools function correctly
- Ensure non-functional requirements (performance, security, reliability) are met
- Verify backward compatibility with existing stdio and SSE transport modes

### 1.3 References

| Document | Location |
|----------|----------|
| BRD | documents/MTO-13/BRD.md |
| FSD | documents/MTO-13/FSD.md |
| TDD | documents/MTO-13/TDD.md |
| MCP Spec 2025-03-26 | https://modelcontextprotocol.io/specification/2025-03-26/basic/transports#streamable-http |
| Kotlin Code Standards | .antigravity/steering/kotlin-code-standards.md |

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Automation | Tools |
|-------|-------|------------|-------|
| PBT | Correctness properties (random inputs) â path validation, session ID format, JSON-RPC parsing | Automated | kotest-property |
| UT | Unit/edge case tests â individual functions, serialization, config parsing | Automated | kotest 5.9.1 |
| IT | API integration (Ktor testApplication) â HTTP endpoint testing, session management | Automated | Ktor test engine 3.4.0 |
| E2E-API | REST endpoint E2E (real server) â full HTTP Streamable lifecycle, bridge proxy | Automated | Ktor client + JUnit 5 |
| E2E-UI | N/A â this is a backend/infrastructure project with no UI | N/A | N/A |
| SIT | Manual exploratory â complex multi-process scenarios, IDE integration | Manual | IDE + CLI |

> **Note:** E2E-UI is not applicable for MTO-13 as this is a backend infrastructure project with no web UI. All automated E2E testing is done via E2E-API level.

### 2.2 Test Types

| Type | Description | Applicable |
|------|-------------|------------|
| Functional Testing | Verify all 56 ACs work per FSD use cases | Yes |
| Regression Testing | Ensure existing stdio/SSE modes and tools still work after refactor | Yes |
| Performance Testing | Verify HTTP latency < 100ms, token reduction 73-80% | Yes |
| Security Testing | Path traversal prevention, session isolation | Yes |
| Integration Testing | Bridge â Orchestrator, Orchestrator â Upstream servers | Yes |
| Compatibility Testing | Kotlin Bridge + Node.js Bridge produce identical behavior | Yes |

### 2.3 Test Approach

**Risk-Based Prioritization:**
- **High Priority:** Parts A (HTTP Streamable), C (Multi-Module Refactor), F (Smart Tool Promotion) â core architectural changes
- **Medium Priority:** Parts D (Kotlin Bridge), E (Node.js Bridge) â new components
- **Lower Priority:** Parts G, H, I (already implemented) â verification/regression only

**Automation Strategy:**
- PBT for input validation properties (path validation, UUID format, JSON-RPC structure)
- UT for all business logic functions (session management, promotion cache, compact schema generation)
- IT for HTTP endpoint testing via Ktor testApplication
- E2E-API for full lifecycle testing with real server instances
- SIT (manual) only for multi-process IDE integration scenarios

### 2.4 Entry Criteria

| Level | Entry Criteria |
|-------|---------------|
| UT/PBT | Code compiles, dependencies resolved |
| IT | Ktor testApplication configured, mock upstream servers available |
| E2E-API | Server JAR built, can start on localhost:8080 |
| SIT | All automated tests pass, server + bridge JARs available, IDE configured |

### 2.5 Exit Criteria

| Level | Exit Criteria |
|-------|--------------|
| UT/PBT | 100% test cases pass, code coverage â¥ 80% for new code |
| IT | All integration scenarios pass, no Critical defects |
| E2E-API | Full lifecycle tests pass for all 9 parts |
| SIT | All manual scenarios executed, 0 Critical/Major defects open |

### 2.6 E2E Automation Coverage

| Scenario Type | Classify As | Rationale |
|---------------|-------------|-----------|
| HTTP Streamable CRUD (init, call, resume) | E2E-API | Deterministic HTTP request/response |
| Session management (create, validate, expire) | E2E-API | API-level verification sufficient |
| Smart Tool Promotion lifecycle | E2E-API | JSON-RPC request/response verification |
| Bridge proxy (stdio â HTTP â response) | E2E-API | Multi-process but automatable via process spawn |
| Stream write file operations | E2E-API | File I/O verification |
| Embed images tool | E2E-API | File transformation verification |
| Hidden tool discovery | E2E-API | find_tools + tools/list verification |
| Gradle build verification | IT | Build system testing |
| IDE integration (Kiro/Cursor) | SIT (manual) | Requires real IDE, human judgment |
| Multi-client concurrent sessions | SIT (manual) | Complex timing, visual monitoring |

---

## 3. Test Scope

### 3.1 Features In Scope

| # | Feature / Part | Priority | AC Range | Test Type |
|---|---------------|----------|----------|-----------|
| 1 | HTTP Streamable Transport (Part A) | High | AC #1â7 | IT, E2E-API, PBT |
| 2 | Hidden Utility Tools (Part B) | Medium | AC #8â9 | UT, IT, E2E-API |
| 3 | Gradle Multi-Module Refactor (Part C) | High | AC #10â14 | IT, Regression |
| 4 | MCP Client Bridge â Kotlin (Part D) | High | AC #15â22 | UT, IT, E2E-API |
| 5 | MCP Client Bridge â Node.js (Part E) | Medium | AC #23â30 | UT, IT, E2E-API |
| 6 | Smart Tool Promotion (Part F) | High | AC #31â41 | PBT, UT, IT, E2E-API |
| 7 | Stream Write Tool (Part G) | Medium | AC #42â50 | PBT, UT, IT |
| 8 | Embed Images Tool (Part H) | Low | AC #51â53 | UT, IT |
| 9 | Large-Text Input Proxy (Part I) | Low | AC #54â56 | UT, IT |

### 3.2 Features Out of Scope

| # | Feature | Reason |
|---|---------|--------|
| 1 | Authentication/authorization for HTTP endpoints | Explicitly out of scope per BRD 1.2 |
| 2 | Load balancing / horizontal scaling | Future enhancement |
| 3 | UI/frontend IDE plugin changes | Not part of MTO-13 |
| 4 | Migration of existing upstream server configs | Not part of MTO-13 |
| 5 | Qdrant vector DB changes | Existing infrastructure, unchanged |

---

## 4. Test Environment

### 4.1 Environment Requirements

| Environment | Configuration | Purpose |
|-------------|--------------|---------|
| Local Dev | JDK 21, Kotlin 2.3.20, Ktor 3.4.0, Node.js 20+ | UT, PBT, IT |
| Integration | localhost:8080 (Orchestrator), localhost:3000 (Bridge) | E2E-API |
| SIT | Full stack: Orchestrator + Bridge + Upstream mocks + IDE | Manual testing |

### 4.2 Runtime Requirements

| Component | Version | Required For |
|-----------|---------|-------------|
| JDK | 21 | Orchestrator, Kotlin Bridge |
| Kotlin | 2.3.20 | All Kotlin modules |
| Ktor | 3.4.0 | HTTP server/client |
| Node.js | 20+ | Node.js Bridge |
| Gradle | 8.x (wrapper) | Build system |
| draw.io Desktop | Latest | Part B hidden tools |
| Qdrant | 1.9+ | Vector search (existing) |

### 4.3 Test Data Requirements

| Data Type | Description | Source |
|-----------|-------------|--------|
| JSON-RPC requests | Valid/invalid MCP protocol messages | Generated in test fixtures |
| Session IDs | UUID v4 format strings | Generated dynamically |
| Tool definitions | Mock upstream tool schemas | Test fixtures |
| File content | Test files for stream_write and embed_images | Created in test setup |
| Markdown with images | Files with image references for embed_images | Test fixtures |

### 4.4 External Dependencies

| System | Dependency | Mock/Stub Available |
|--------|-----------|---------------------|
| Qdrant Vector DB | Tool embedding storage | Yes â in-memory mock |
| OpenAI API | Text embeddings | Yes â mock embedding service |
| Upstream MCP Servers | Tool execution | Yes â mock stdio servers |
| draw.io CLI | Diagram export | Conditional â skip if not installed |
| File System | stream_write_file target | Yes â temp directories |

---

## 5. Test Schedule

| Phase | Start Date | End Date | Duration | Milestone |
|-------|-----------|----------|----------|-----------|
| Test Planning | 2026-05-06 | 2026-05-07 | 2 days | STP + STC approved |
| Test Data Preparation | 2026-05-07 | 2026-05-08 | 1 day | Test fixtures ready |
| UT/PBT Execution | 2026-05-08 | 2026-05-10 | 3 days | Unit tests pass |
| IT Execution | 2026-05-10 | 2026-05-13 | 3 days | Integration tests pass |
| E2E-API Execution | 2026-05-13 | 2026-05-15 | 2 days | E2E scenarios pass |
| SIT Execution | 2026-05-15 | 2026-05-16 | 2 days | Manual scenarios verified |
| Defect Fix & Retest | 2026-05-16 | 2026-05-18 | 2 days | All Critical/Major fixed |
| Sign-Off | 2026-05-18 | 2026-05-18 | 1 day | Test completion report |

---

## 6. Resources & Responsibilities

| Role | Name | Responsibility |
|------|------|---------------|
| Test Lead | QA Agent | Test planning, coordination, reporting |
| QA Engineer | QA Agent | Test case design, execution, defect reporting |
| BA | BA Agent | Acceptance criteria clarification |
| Developer | Dev Team | Bug fixing, unit test coverage |
| DevOps | DevOps Agent | CI/CD pipeline, environment setup |
| Project Lead | Duc Nguyen | Review, sign-off |

---

## 7. Risk & Mitigation

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | Multi-module refactor breaks existing tests | High | Medium | Run full test suite after each module extraction; incremental approach |
| 2 | HTTP Streamable session management race conditions | High | Medium | PBT with concurrent session creation; stress testing |
| 3 | Bridge auto-reconnect timing issues | Medium | Medium | Configurable timeouts; retry with exponential backoff testing |
| 4 | Smart Tool Promotion cache stale data | Medium | Medium | TTL verification; cache invalidation testing |
| 5 | Node.js bridge behavior diverges from Kotlin bridge | Medium | Medium | Shared acceptance criteria; cross-bridge compatibility tests |
| 6 | draw.io CLI not available in CI environment | Low | High | Conditional test execution; skip export tests if CLI absent |
| 7 | Path traversal vulnerability in stream_write_file | High | Low | PBT with malicious path inputs; security-focused test cases |

---

## 8. Defect Management

### 8.1 Severity Levels

| Severity | Definition | Example |
|----------|-----------|---------|
| Critical | System crash, data loss, security breach | Path traversal allows arbitrary file write; session data leaked |
| Major | Feature not working, no workaround | HTTP Streamable returns wrong content-type; promotion fails silently |
| Minor | Feature works with workaround | Auto-reconnect takes longer than 15s; compact schema slightly over 100 chars |
| Trivial | Cosmetic, logging issue | Log message typo; unnecessary debug output |

### 8.2 Priority Levels

| Priority | Definition | SLA (Fix Time) |
|----------|-----------|----------------|
| P1 | Must fix immediately â blocks testing | 4 hours |
| P2 | Must fix before release | 1 business day |
| P3 | Should fix if time permits | 3 business days |
| P4 | Nice to fix, can defer | Next release |

### 8.3 Defect Lifecycle

```
New â Open â In Progress â Fixed â Ready for Retest â Verified â Closed
                                                     â Reopened â In Progress
```

---

## 9. Test Metrics & Reporting

### 9.1 Metrics

| Metric | Formula | Target |
|--------|---------|--------|
| Test Execution Rate | Executed / Total Ã 100% | 100% |
| Pass Rate | Passed / Executed Ã 100% | â¥ 95% |
| Defect Density | Defects / Test Cases | â¤ 0.1 |
| Critical Defect Count | Count of Critical severity | 0 |
| AC Coverage | ACs with â¥1 test case / Total ACs Ã 100% | 100% (56/56) |
| Automation Rate | Automated / Total Ã 100% | â¥ 85% |

### 9.2 Reporting Schedule

| Report | Frequency | Audience |
|--------|-----------|----------|
| Daily Test Status | Daily during execution | Project team |
| Defect Summary | Daily | Dev team + PM |
| Test Completion Report | End of each phase | All stakeholders |

---

## 10. Test Cases Summary

| Level | Count | Automated | Manual |
|-------|-------|-----------|--------|
| PBT | 12 | 12 | 0 |
| UT | 35 | 35 | 0 |
| IT | 28 | 28 | 0 |
| E2E-API | 20 | 20 | 0 |
| E2E-UI | 0 | 0 | 0 |
| SIT | 8 | 0 | 8 |
| **Total** | **103** | **95 (92%)** | **8 (8%)** |

---

## 11. Appendix

### Glossary

| Term | Definition |
|------|------------|
| MCP | Model Context Protocol |
| HTTP Streamable | Transport mode per MCP spec 2025-03-26 |
| SSE | Server-Sent Events |
| stdio | Standard input/output transport |
| PBT | Property-Based Testing |
| UT | Unit Testing |
| IT | Integration Testing |
| E2E-API | End-to-End API Testing |
| SIT | System Integration Testing (Manual) |
| Smart Tool Promotion | Progressive tool exposure mechanism |
| Bridge | Protocol translator (stdio â HTTP Streamable) |

### Assumptions

- MCP specification 2025-03-26 is stable during testing
- JDK 21 and Node.js 20+ are available in all test environments
- Qdrant and OpenAI services are available (or mocked) during testing
- draw.io CLI may not be available in CI â tests are conditional
- IDE integration testing requires manual execution with real Kiro/Cursor
