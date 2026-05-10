# Software Test Plan (STP)

## MCPOrchestration — MTO-31: KB Refinery — PostgreSQL RLS Policies

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-31 |
| Title | KB Refinery — PostgreSQL RLS Policies |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-31.docx |
| Related FSD | FSD-v1-MTO-31.docx |
| Related TDD | TDD-v1-MTO-31.docx |

---

## 1. Test Scope

### 1.1 In Scope

- PostgreSQL role creation (V8 migration)
- RLS policy enforcement on kb_entries (V9 migration)
- RLS policy enforcement on pii_mapping (V10 migration)
- RoleContextService role resolution logic
- RlsConnectionWrapper transaction management
- Column-level filtering via security barrier views
- Deny-by-default behavior
- Connection pool role isolation
- Concurrent access with different roles

### 1.2 Out of Scope

- Application-level authentication (upstream service)
- UI testing (no UI in this ticket)
- PII encryption/decryption (MTO-33)
- Audit logging (MTO-34)

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Framework | Automation |
|-------|-------|-----------|------------|
| Unit Test (UT) | KbRole, RoleContextService, RlsConfig | Kotest + MockK | 100% automated |
| Integration Test (IT) | RlsConnectionWrapper + PostgreSQL, Flyway migrations | Kotest + Testcontainers | 100% automated |
| E2E-API | Full flow: role resolution → SET LOCAL → query → filtered results | Kotest + Testcontainers | 100% automated |
| Property-Based Test (PBT) | Role enum exhaustiveness, config validation | Kotest Property | 100% automated |

### 2.2 Test Environment

| Component | Specification |
|-----------|--------------|
| Database | PostgreSQL 16 (Testcontainers) |
| JVM | JDK 21 |
| Build | Gradle + Kotest |
| CI | GitHub Actions |

### 2.3 Entry Criteria

- TDD approved
- Source code implemented
- Flyway migrations written
- Testcontainers available in CI

### 2.4 Exit Criteria

- All UT pass (100%)
- All IT pass (100%)
- All E2E-API pass (100%)
- Code coverage ≥ 90% for security package
- No Critical/High severity bugs open

---

## 3. Test Case Summary

### 3.1 By Level

| Level | Total Cases | Automated | Manual |
|-------|-------------|-----------|--------|
| PBT | 3 | 3 | 0 |
| UT | 12 | 12 | 0 |
| IT | 15 | 15 | 0 |
| E2E-API | 10 | 10 | 0 |
| **Total** | **40** | **40** | **0** |

### 3.2 By Feature

| Feature | PBT | UT | IT | E2E-API | Total |
|---------|-----|----|----|---------|-------|
| KbRole Enum | 1 | 3 | 0 | 0 | 4 |
| RoleContextService | 1 | 4 | 0 | 0 | 5 |
| RlsConfig | 1 | 2 | 0 | 0 | 3 |
| RlsConnectionWrapper | 0 | 3 | 5 | 0 | 8 |
| Flyway Migrations | 0 | 0 | 4 | 0 | 4 |
| RLS Policy — kb_entries | 0 | 0 | 4 | 5 | 9 |
| RLS Policy — pii_mapping | 0 | 0 | 2 | 3 | 5 |
| Concurrent Access | 0 | 0 | 0 | 2 | 2 |

---

## 4. Requirements Traceability Matrix (RTM)

| BRD Requirement | FSD Use Case | Test Cases |
|-----------------|-------------|------------|
| Story 1: DB-level access control | UC-01, UC-02 | IT-01..04, E2E-01 |
| Story 2: Developer role access | UC-02 (AF-1) | UT-04, IT-05..07, E2E-02..03 |
| Story 3: Admin role full access | UC-02, UC-03 | UT-05, IT-08..09, E2E-04..05 |
| Story 4: Viewer restricted access | UC-02 (AF-2) | UT-06, IT-10..11, E2E-06..07 |
| Story 5: HikariCP integration | UC-05 | IT-12..15, E2E-08..10 |
| NFR: Performance < 5ms | — | E2E-09 |
| NFR: Deny-by-default | — | IT-04, E2E-01 |
| NFR: No role escalation | — | IT-13, E2E-10 |

---

## 5. Risk Assessment

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Testcontainers PostgreSQL version mismatch | Medium | Low | Pin to postgres:16-alpine |
| Flyway migration order dependency | High | Medium | Test migrations in sequence |
| Connection pool state leakage in tests | High | Medium | Fresh container per test class |
| Role not properly reset between tests | High | Medium | Verify role reset in afterEach |

---

## 6. Test Schedule

| Phase | Duration | Dependencies |
|-------|----------|-------------|
| UT implementation | 1 day | Source code complete |
| IT implementation | 2 days | Migrations written |
| E2E-API implementation | 1 day | IT passing |
| Test execution & bug fix | 1 day | All tests written |

---

## 7. Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Test Coverage Overview | [test-coverage.png](diagrams/test-coverage.png) | [test-coverage.drawio](diagrams/test-coverage.drawio) |
| 2 | Test Execution Flow | [test-execution-flow.png](diagrams/test-execution-flow.png) | [test-execution-flow.drawio](diagrams/test-execution-flow.drawio) |
