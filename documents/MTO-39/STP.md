# Software Test Plan (STP)

## MCPOrchestration — MTO-39: User Management & Document Approval

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-39 |
| Title | User Management & Document Approval — Test Plan |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-10 |
| Related BRD | BRD-v1-MTO-39.docx |
| Related FSD | FSD-v1-MTO-39.docx |
| Related TDD | TDD-v1-MTO-39.docx |

---

## 1. Test Strategy

### 1.1 Test Levels

| Level | Scope | Framework | Automation |
|-------|-------|-----------|------------|
| Unit Test (UT) | Individual service/repository methods | Kotest + MockK | 100% automated |
| Integration Test (IT) | DB operations, service interactions | Kotest + Testcontainers PostgreSQL | 100% automated |
| E2E-API | Full HTTP endpoint flows | Kotest + Java HttpClient | 100% automated |
| E2E-MCP | MCP tool invocations | Kotest + MCP SDK test client | 100% automated |

### 1.2 Test Coverage Targets

| Category | Target | Metric |
|----------|--------|--------|
| Line coverage | ≥ 80% | JaCoCo |
| Branch coverage | ≥ 70% | JaCoCo |
| Business rules | 100% | All BR-01 to BR-24 tested |
| Error paths | 100% | All exception types tested |
| Permission matrix | 100% | All 7 roles × 6 doc types |

### 1.3 Test Environment

| Component | Configuration |
|-----------|--------------|
| Database | Testcontainers PostgreSQL 16 |
| Encryption Key | Test key (base64 of 32 zero bytes) |
| Jira API | MockK (no real Jira calls in tests) |
| HTTP Server | Java HttpServer on random port |

---

## 2. Test Scope

### 2.1 In Scope

| Feature | Test Focus |
|---------|-----------|
| User CRUD | Create, read, update, deactivate, reactivate |
| Project Assignment | Assign, list, revoke, duplicate prevention |
| Permission Matrix | Default seeding, CRUD, validation |
| Approval Workflow | Approve, reject, permission checks, audit logging |
| Admin Auth | Middleware validation, role checks, 403 responses |
| Token Encryption | Encrypt/decrypt roundtrip, invalid key handling |
| MCP Tools | approve_document, get_approval_status tool handlers |
| DB Migration | Table creation, idempotency |

### 2.2 Out of Scope

| Item | Reason |
|------|--------|
| Jira API integration | Mocked — tested separately in Jira module |
| Admin UI rendering | No frontend in this ticket |
| Performance/load testing | Team-scale only (< 100 users) |
| Security penetration testing | Separate security review |

---

## 3. Requirements Traceability Matrix (RTM)

| Requirement | Business Rule | Test Cases |
|-------------|---------------|------------|
| BRD Story 1: User CRUD | BR-01 to BR-06 | UT-01 to UT-08, IT-01 to IT-04, E2E-01 to E2E-05 |
| BRD Story 2: Project Assignment | BR-07 to BR-10 | UT-09 to UT-12, IT-05 to IT-06, E2E-06 to E2E-08 |
| BRD Story 3: Permission Matrix | BR-11 to BR-14 | UT-13 to UT-16, IT-07, E2E-09 to E2E-10 |
| BRD Story 4: Authentication | BR-15 to BR-18 | UT-17 to UT-19 |
| BRD Story 5: Document Approval | BR-19 to BR-24 | UT-20 to UT-26, IT-08 to IT-10, E2E-11 to E2E-14 |
| BRD Story 6: List Pending | — | UT-27, E2E-15 |
| BRD Story 7: Approval Status | — | UT-28, E2E-16 |
| BRD Story 8: Re-attach | BR-21, BR-22 | UT-29 to UT-30 |

---

## 4. Test Schedule

| Phase | Duration | Dependencies |
|-------|----------|-------------|
| Unit Tests | 1 day | Implementation complete |
| Integration Tests | 1 day | Unit tests pass |
| E2E API Tests | 0.5 day | Integration tests pass |
| MCP Tool Tests | 0.5 day | E2E API tests pass |

---

## 5. Entry/Exit Criteria

### Entry Criteria
- Implementation code compiles without errors
- Database migration runs successfully
- All dependencies available (Testcontainers, MockK)

### Exit Criteria
- All test cases pass (0 failures)
- Coverage targets met (≥ 80% line, ≥ 70% branch)
- All business rules verified
- No Critical/High severity bugs open

---

## 6. Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Testcontainers slow on CI | Medium | Use connection pooling, parallel test execution |
| Encryption key not set in test env | High | Use fixed test key in test configuration |
| Flaky tests from DB state | Medium | Each test class uses fresh schema |
