# Software Test Plan (STP)

## MCPOrchestration — MTO-32: KB Refinery — PII Mapping Encrypted Table

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-32 |
| Version | 1.0 |
| Date | 2026-05-08 |
| Author | QA Agent |
| Related BRD | BRD-v1-MTO-32.docx |
| Related FSD | FSD-v1-MTO-32.docx |
| Related TDD | TDD-v1-MTO-32.docx |

---

## 1. Test Strategy

### 1.1 Test Levels

| Level | Scope | Framework | Automation |
|-------|-------|-----------|------------|
| PBT (Property-Based) | UnmaskResult sealed class invariants, rate limit math | Kotest Property | 100% automated |
| UT (Unit Test) | PiiAccessServiceImpl, PiiRateLimitServiceImpl, PiiSessionServiceImpl | Kotest + MockK | 100% automated |
| IT (Integration Test) | Full unmask flow with PostgreSQL, audit immutability | Testcontainers | 100% automated |
| E2E-API | MCP tool invocation unmask_pii end-to-end | Ktor TestHost | 100% automated |
| E2E-UI | N/A (no UI for this feature) | — | — |
| SIT (System Integration) | Rate limit persistence across restart, RLS enforcement | Manual + Testcontainers | 80% automated |

### 1.2 Test Coverage Targets

| Level | Target Coverage | Rationale |
|-------|----------------|-----------|
| PBT | 100% sealed class variants | Ensure type safety |
| UT | 95% line coverage | Core business logic |
| IT | 90% integration paths | DB + encryption + RLS |
| E2E-API | 100% API scenarios | All tool invocations |

### 1.3 Requirements Traceability Matrix (RTM)

| BRD Requirement | FSD Use Case | Test Cases |
|-----------------|-------------|------------|
| Story 1: Audit Trail | UC-03 | UT-01, UT-02, IT-03, IT-04 |
| Story 2: Rate Limiting | UC-02 | PBT-01, UT-03, UT-04, IT-05, IT-06 |
| Story 3: Authorized Unmask | UC-01 | UT-05, UT-06, UT-07, IT-01, IT-02, E2E-01 |
| Story 4: PiiAccessService Interface | UC-01 | UT-08, PBT-02 |
| Story 5: Retention Policy | UC-03 | IT-07 |
| NFR: Performance < 50ms | — | E2E-02 |
| NFR: Fail-closed | — | UT-09, IT-08 |

---

## 2. Test Environment

### 2.1 Infrastructure

| Component | Technology | Version |
|-----------|-----------|---------|
| Database | PostgreSQL (Testcontainers) | 16 |
| Application | Ktor TestHost | 3.4.0 |
| Test Framework | Kotest | 5.9.1 |
| Mocking | MockK | 1.14.2 |
| Containers | Testcontainers | 1.21.1 |

### 2.2 Test Data

- Pre-seeded PII mappings (3 entries for test issue key)
- Pre-configured encryption key (test-only, 32 bytes)
- Test user identities: admin@test.com, dev@test.com, viewer@test.com

---

## 3. Test Schedule

| Phase | Duration | Dependencies |
|-------|----------|-------------|
| PBT + UT | 1 day | TDD complete |
| IT | 1 day | Code implemented |
| E2E-API | 0.5 day | IT passing |
| SIT | 0.5 day | E2E passing |

---

## 4. Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Testcontainers PostgreSQL slow startup | Medium | Use shared container across tests |
| Rate limit timing sensitivity | Medium | Use Clock injection for deterministic tests |
| Encryption key management in tests | Low | Use fixed test key, never production key |

---

## 5. Entry/Exit Criteria

**Entry:** TDD approved, code implemented, build passes

**Exit:** All Critical/High tests pass, no open Critical bugs, coverage targets met
