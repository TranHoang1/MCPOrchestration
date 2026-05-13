# Software Test Plan (STP)

## MCP Orchestrator — MTO-94: Per-User Credentials + Scalable Process Pool

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-94 |
| Title | Per-User Credentials + Scalable Process Pool for MCP Orchestrator |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-07-06 |
| Status | Draft |
| Related BRD | BRD-v1.0-MTO-94.docx |
| Related FSD | FSD-v1.0-MTO-94.docx |
| Related TDD | TDD-v1.0-MTO-94.docx |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | QA Agent – QA Engineer | Create document |
| Peer Reviewer | SA Agent – Solution Architect | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-07-06 | QA Agent | Initiate document — auto-generated from BRD, FSD, and TDD for MTO-94 |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm the test plan in this STP |
| | ☐ I agree and confirm the test plan in this STP |

---

## 1. Introduction

### 1.1 Purpose

This Software Test Plan defines the testing strategy, scope, schedule, and resources for verifying the **Per-User Credentials + Scalable Process Pool** feature set (Epic MTO-94) in the MCP Orchestrator system. The feature introduces JWT-based authentication, per-user encrypted credential management, runtime credential resolution, a scalable process pool, bridge client token support, and SSO integration across 7 stories (MTO-95 through MTO-101).

### 1.2 Test Objectives

- Verify all 12 use cases (UC-001 through UC-012) from FSD are implemented correctly
- Validate all 47 business rules (BR-001 through BR-047) are enforced
- Ensure all 14 error scenarios from FSD Section 9 are handled with correct error codes
- Verify non-functional requirements: JWT validation < 5ms, credential resolution < 10ms, pool warm acquire < 100ms, cold start < 5s
- Validate security controls: AES-256-GCM encryption, bcrypt hashing, token revocation, PKCE for SSO
- Confirm backward compatibility: X-User-Email header still works, no-schema servers unchanged
- Verify process pool scaling behavior under concurrent load (20+ users)

### 1.3 References

| Document | Location |
|----------|----------|
| BRD | documents/MTO-94/BRD.md |
| FSD | documents/MTO-94/FSD.md |
| TDD | documents/MTO-94/TDD.md |
| Existing Test Patterns | orchestrator-server/src/jvmTest/ |

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Automation | Tools |
|-------|-------|------------|-------|
| PBT | Correctness properties — JWT claims, credential resolution, pool key computation | Automated | kotest-property |
| UT | Unit/edge case tests — services, validators, resolvers | Automated | kotest + mockk |
| IT | API integration — Ktor testApplication with real DB | Automated | Ktor test engine + Testcontainers |
| E2E-API | REST endpoint E2E — full server running | Automated | Ktor HTTP client + JUnit 5 |
| E2E-UI | Browser UI E2E — Cucumber scenarios | Automated | Cucumber + Serenity + WebDriver |
| SIT | Manual exploratory / visual / UX-only tests | Manual | Browser (Chrome) |

### 2.2 Test Types

| Type | Description | Applicable |
|------|-------------|------------|
| Functional Testing | Verify features work per FSD use cases (UC-001 to UC-012) | Yes |
| Security Testing | JWT validation, encryption, auth bypass attempts, CSRF protection | Yes |
| Performance Testing | JWT throughput, credential resolution latency, pool acquire times | Yes |
| Regression Testing | Existing tool execution, X-User-Email backward compat | Yes |
| Integration Testing | Bridge-Orchestrator, Orchestrator-IdP, Orchestrator-Upstream | Yes |
| UI/UX Testing | Login page, profile page, credential forms, admin schema management | Yes |

### 2.3 Test Approach

**Risk-based prioritization:** Security-critical paths (authentication, credential encryption) receive highest test density. Process pool scaling and SSO integration receive medium density due to complexity.

**Automation-first:** Target 93% automation coverage. Only visual/UX tests and complex timing scenarios remain manual.

**Shift-left:** Property-based tests and unit tests run on every commit. Integration tests run on PR merge. E2E tests run nightly and before release.

### 2.4 Entry Criteria

| Level | Entry Criteria |
|-------|---------------|
| UT/PBT | Code compiles, dependencies resolved |
| IT | Database migrations applied, Testcontainers available |
| E2E-API | Server deployed to test environment, test data seeded |
| E2E-UI | UI deployed, browser automation infrastructure ready |
| SIT | All automated tests pass (>=95%), no Critical defects open |

### 2.5 Exit Criteria

| Level | Exit Criteria |
|-------|--------------|
| UT/PBT | 100% pass rate, >=80% code coverage on new code |
| IT | 100% pass rate, all API contracts verified |
| E2E-API | 100% pass rate, all error scenarios verified |
| E2E-UI | >=95% pass rate, all CRUD flows verified |
| SIT | 100% executed, 0 Critical defects, <=2 Major defects open |

### 2.6 E2E Automation Coverage

| Scenario Type | Classification | Rationale |
|---------------|---------------|-----------|
| Login/auth CRUD operations | E2E-API | API-level verification sufficient |
| Credential schema CRUD | E2E-API + E2E-UI | API for logic, UI for form behavior |
| User credential save/update | E2E-UI | Form interaction + masking verification |
| Token generation/revocation | E2E-API | No UI-specific behavior |
| Pool metrics retrieval | E2E-API | Admin API only |
| Bridge token format validation | UT | CLI argument parsing |
| SSO redirect flow | E2E-UI | Browser redirect required |
| Visual layout/dark theme | SIT (manual) | Requires human judgment |
| Blocking overlay timing | SIT (manual) | Visual timing verification |

---

## 3. Test Scope

### 3.1 Features In Scope

| # | Feature / Story | Priority | FSD Reference | Test Type |
|---|----------------|----------|---------------|-----------|
| 1 | JWT Auth Middleware + Login API (MTO-95) | High | UC-001, UC-002, UC-003, BR-001-BR-007 | Functional, Security, Performance |
| 2 | Credential Schema CRUD (MTO-96) | High | UC-004, UC-005, BR-008-BR-013 | Functional, UI |
| 3 | User Credential CRUD (MTO-97) | High | UC-006, BR-014-BR-020 | Functional, Security, UI |
| 4 | Credential Resolver (MTO-98) | High | UC-007, BR-021-BR-026 | Functional, Integration |
| 5 | Process Pool Manager (MTO-99) | High | UC-008, UC-009, BR-027-BR-035 | Functional, Performance |
| 6 | Bridge Client Update (MTO-100) | High | UC-010, BR-036-BR-040 | Functional, Integration |
| 7 | SSO Integration (MTO-101) | Medium | UC-011, UC-012, BR-041-BR-047 | Functional, Security, Integration |

### 3.2 Features Out of Scope

| # | Feature | Reason |
|---|---------|--------|
| 1 | Credential rotation automation | Deferred to future iteration (BRD 1.2) |
| 2 | Multi-factor authentication (MFA) | Not in scope for MTO-94 |
| 3 | Rate limiting per user | Deferred (FSD Open Issue #2) |
| 4 | User self-registration | Admin creates users (BRD 1.2) |
| 5 | Mobile client support | Not in scope |
| 6 | JWT key rotation | Open issue (FSD 11.5 #1) |

---

## 4. Test Environment

### 4.1 Environment Requirements

| Environment | URL | Database | Purpose |
|-------------|-----|----------|---------|
| Local Dev | localhost:8080 | PostgreSQL 16 (Docker) | Unit + Integration tests |
| SIT | sit.orchestrator.local:8080 | PostgreSQL 16 (dedicated) | System Integration Testing |
| UAT | uat.orchestrator.local:8080 | PostgreSQL 16 (dedicated) | User Acceptance Testing |

### 4.2 Browser / Device Requirements

| Browser | Version | OS | Required |
|---------|---------|-----|----------|
| Chrome | 120+ | Windows 11 / macOS | Yes |
| Firefox | 115+ | Windows 11 / macOS | Yes (regression only) |
| Edge | 120+ | Windows 11 | No (nice to have) |

### 4.3 Test Data Requirements

| Data Type | Description | Source | Preparation |
|-----------|-------------|--------|-------------|
| Admin user | System owner with full permissions | DB seed | Pre-seeded via migration |
| Developer users | 5+ users with developer role | DB seed | Test data script |
| Upstream servers | 3+ servers with credential schemas | DB seed | atlassian, github, custom |
| Credential schemas | Field definitions for test servers | API/seed | Created via admin API |
| User credentials | Encrypted credential values | API | Saved via user API |
| SSO IdP | Mock OIDC provider | WireMock/Keycloak | Docker container |

### 4.4 External Dependencies

| System | Dependency | Mock/Stub Available |
|--------|-----------|---------------------|
| Identity Provider (OIDC) | OAuth2 token exchange, OIDC discovery | Yes — WireMock or Keycloak Docker |
| Upstream MCP Servers | Process spawn for pool testing | Yes — mock stdio server script |
| PostgreSQL | Database for all CRUD operations | Yes — Testcontainers |

---

## 5. Test Schedule

| Phase | Start Date | End Date | Duration | Milestone |
|-------|-----------|----------|----------|-----------|
| Test Planning | Sprint Day 1 | Sprint Day 2 | 2 days | STP + STC approved |
| Test Data Preparation | Sprint Day 2 | Sprint Day 3 | 1 day | Test data + mocks ready |
| UT/PBT Development | Sprint Day 3 | Sprint Day 7 | 5 days | Unit tests pass |
| IT Development | Sprint Day 5 | Sprint Day 8 | 4 days | Integration tests pass |
| E2E-API Development | Sprint Day 7 | Sprint Day 9 | 3 days | API E2E tests pass |
| E2E-UI Development | Sprint Day 8 | Sprint Day 10 | 3 days | UI E2E tests pass |
| SIT Execution | Sprint Day 10 | Sprint Day 12 | 3 days | SIT sign-off |
| Defect Fix & Retest | Sprint Day 12 | Sprint Day 13 | 2 days | All Critical/Major fixed |
| UAT Support | Sprint Day 13 | Sprint Day 14 | 2 days | UAT sign-off |

---

## 6. Resources & Responsibilities

| Role | Name | Responsibility |
|------|------|---------------|
| Test Lead | QA Agent | Test planning, coordination, reporting, SIT execution |
| QA Engineer | QA Agent | Test case design, automation, defect reporting |
| BA | BA Agent | UAT support, acceptance criteria clarification |
| Developer | DEV Agent | Bug fixing, unit test coverage, IT implementation |
| DevOps | DevOps Agent | Environment setup, CI/CD pipeline, Docker |
| SA | SA Agent | Technical review, architecture validation |

---

## 7. Risk & Mitigation

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | SSO IdP mock not accurately simulating real provider | High | Medium | Use Keycloak Docker for realistic OIDC testing |
| 2 | Process pool timing tests flaky in CI | Medium | High | Use deterministic time control, generous timeouts |
| 3 | Credential encryption key not available in test env | High | Low | Use dedicated test encryption key in test config |
| 4 | Concurrent pool tests causing resource exhaustion | Medium | Medium | Limit maxTotalInstances to 10 in test config |
| 5 | JWT secret rotation during test execution | Low | Low | Use fixed test secret, test rotation separately |
| 6 | Database migration conflicts with existing data | High | Low | Use clean database per test suite (Testcontainers) |
| 7 | Bridge client tests require real IDE connection | Medium | Medium | Mock stdio transport for bridge tests |

---

## 8. Defect Management

### 8.1 Severity Levels

| Severity | Definition | Example |
|----------|-----------|---------|
| Critical | Security breach, data loss, system crash | JWT bypass allows unauthenticated access; credentials stored unencrypted |
| Major | Feature not working, no workaround | Login always returns 500; pool never scales up |
| Minor | Feature works with workaround | Bridge token copy button does not work; wrong error message text |
| Trivial | Cosmetic issue | Typo in UI label; minor alignment issue |

### 8.2 Priority Levels

| Priority | Definition | SLA (Fix Time) |
|----------|-----------|----------------|
| P1 | Security vulnerability or data loss | 4 hours |
| P2 | Feature blocker, must fix before release | 1 business day |
| P3 | Should fix if time permits | 3 business days |
| P4 | Nice to fix, can defer | Next sprint |

### 8.3 Defect Lifecycle

```
New -> Open -> In Progress -> Fixed -> Ready for Retest -> Verified -> Closed
                                                        -> Reopened -> In Progress
```

---

## 9. Test Metrics & Reporting

### 9.1 Metrics

| Metric | Formula | Target |
|--------|---------|--------|
| Test Execution Rate | Executed / Total x 100% | 100% |
| Pass Rate | Passed / Executed x 100% | >= 95% |
| Automation Rate | Automated / Total x 100% | >= 80% |
| Defect Density | Defects / Test Cases | <= 0.1 |
| Critical Defect Count | Count of Critical severity | 0 |
| Security Test Pass Rate | Security tests passed / total | 100% |

### 9.2 Reporting Schedule

| Report | Frequency | Audience |
|--------|-----------|----------|
| Daily Test Status | Daily during SIT | Project team |
| Defect Summary | Daily | Dev team + PM |
| Test Completion Report | End of SIT / End of UAT | All stakeholders |
| Security Test Report | End of security testing phase | Security team + PM |

---

## 10. Test Cases Summary

### 10.1 Test Levels Distribution

| Level | Count | Automated | Manual |
|-------|-------|-----------|--------|
| PBT | 8 | 8 | 0 |
| UT | 35 | 35 | 0 |
| IT | 25 | 25 | 0 |
| E2E-API | 30 | 30 | 0 |
| E2E-UI | 18 | 18 | 0 |
| SIT | 9 | 0 | 9 |
| **Total** | **125** | **116 (93%)** | **9 (7%)** |

### 10.2 Test Cases by Story

| Story | Feature | PBT | UT | IT | E2E-API | E2E-UI | SIT | Total |
|-------|---------|-----|----|----|---------|--------|-----|-------|
| MTO-95 | JWT Auth + Login | 3 | 10 | 6 | 10 | 4 | 2 | 35 |
| MTO-96 | Credential Schema CRUD | 1 | 5 | 4 | 6 | 4 | 1 | 21 |
| MTO-97 | User Credential CRUD | 1 | 5 | 4 | 5 | 4 | 2 | 21 |
| MTO-98 | Credential Resolver | 2 | 6 | 4 | 3 | 0 | 0 | 15 |
| MTO-99 | Process Pool Manager | 1 | 5 | 4 | 3 | 0 | 2 | 15 |
| MTO-100 | Bridge Client Update | 0 | 3 | 2 | 2 | 0 | 1 | 8 |
| MTO-101 | SSO Integration | 0 | 1 | 1 | 1 | 6 | 1 | 10 |

---

## 11. Appendix

### Glossary

| Term | Definition |
|------|------------|
| SIT | System Integration Testing |
| UAT | User Acceptance Testing |
| STP | Software Test Plan |
| STC | Software Test Cases |
| JWT | JSON Web Token |
| PBT | Property-Based Testing |
| OIDC | OpenID Connect |
| PKCE | Proof Key for Code Exchange |

### Assumptions

- PostgreSQL 16+ is available in all test environments
- Docker is available for Testcontainers and mock IdP
- Test encryption key is separate from production key
- Bridge client binary is available for integration testing
- Upstream MCP server mock scripts are provided by DEV team
- CI/CD pipeline supports parallel test execution
