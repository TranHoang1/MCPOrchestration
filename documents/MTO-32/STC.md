# Software Test Cases (STC)

## MCPOrchestration — MTO-32: KB Refinery — PII Mapping Encrypted Table

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-32 |
| Version | 1.0 |
| Date | 2026-05-08 |
| Author | QA Agent |

---

## 1. Property-Based Tests (PBT)

### PBT-01: Rate Limit Sliding Window Math

| Field | Value |
|-------|-------|
| ID | PBT-01 |
| Objective | Verify sliding window calculation is correct for any count/time combination |
| Property | For any count < max, result is Allowed; for count >= max, result is Exceeded with positive retryAfter |
| Generator | Arb.int(0..100) for count, Arb.long(1..7200) for windowSeconds |
| Framework | Kotest Property |

### PBT-02: UnmaskResult Sealed Class Exhaustiveness

| Field | Value |
|-------|-------|
| ID | PBT-02 |
| Objective | Verify all UnmaskResult variants carry required data |
| Property | Success always has non-blank value; Denied always has reason; RateLimited always has positive retryAfter |
| Generator | Exhaustive enum of DenialReason, Arb.string() for values |

---

## 2. Unit Tests (UT)

### UT-01: Audit Entry Created on Successful Unmask

| Field | Value |
|-------|-------|
| ID | UT-01 |
| Requirement | Story 1 |
| Precondition | Valid session, admin role, rate limit not exceeded |
| Steps | 1. Mock session valid, 2. Mock rate limit allowed, 3. Mock PII found, 4. Call unmask() |
| Expected | auditRepository.insert() called with success=true |
| Priority | High |

### UT-02: Audit Entry Created on Failed Unmask

| Field | Value |
|-------|-------|
| ID | UT-02 |
| Requirement | Story 1 |
| Precondition | Valid session, admin role, rate limit exceeded |
| Steps | 1. Mock session valid, 2. Mock rate limit exceeded, 3. Call unmask() |
| Expected | auditRepository.insert() called with success=false, reason="RATE_LIMIT_EXCEEDED" |
| Priority | High |

### UT-03: Rate Limit Allows When Under Threshold

| Field | Value |
|-------|-------|
| ID | UT-03 |
| Requirement | Story 2 |
| Precondition | User has 5 unmask in last hour (max=10) |
| Steps | 1. Mock auditRepository.countSuccessfulUnmaskSince() returns 5, 2. Call check() |
| Expected | RateLimitResult.Allowed(remaining=5) |
| Priority | High |

### UT-04: Rate Limit Denies When At Threshold

| Field | Value |
|-------|-------|
| ID | UT-04 |
| Requirement | Story 2 |
| Precondition | User has 10 unmask in last hour (max=10) |
| Steps | 1. Mock count returns 10, 2. Mock oldest returns 30 min ago, 3. Call check() |
| Expected | RateLimitResult.Exceeded(retryAfterSeconds=1800) |
| Priority | High |

### UT-05: Admin Role Allowed

| Field | Value |
|-------|-------|
| ID | UT-05 |
| Requirement | Story 3 |
| Precondition | Session with role=BA_ADMIN |
| Steps | 1. Create session with BA_ADMIN, 2. Call unmask() |
| Expected | Proceeds past role check (not denied) |
| Priority | High |

### UT-06: Developer Role Denied

| Field | Value |
|-------|-------|
| ID | UT-06 |
| Requirement | Story 3 |
| Precondition | Session with role=DEVELOPER |
| Steps | 1. Create session with DEVELOPER, 2. Call unmask() |
| Expected | UnmaskResult.Denied(INSUFFICIENT_PERMISSION) |
| Priority | High |

### UT-07: Viewer Role Denied

| Field | Value |
|-------|-------|
| ID | UT-07 |
| Requirement | Story 3 |
| Precondition | Session with role=LOW_PRIVILEGE |
| Steps | 1. Create session with LOW_PRIVILEGE, 2. Call unmask() |
| Expected | UnmaskResult.Denied(INSUFFICIENT_PERMISSION) |
| Priority | High |

### UT-08: Session Expired Returns Denied

| Field | Value |
|-------|-------|
| ID | UT-08 |
| Requirement | Story 4 (FSD UC-04) |
| Precondition | Session created 31 minutes ago |
| Steps | 1. Create session, 2. Advance clock 31 min, 3. Call unmask() |
| Expected | UnmaskResult.Denied(SESSION_EXPIRED) |
| Priority | High |

### UT-09: Audit Write Failure Denies Unmask (Fail-Closed)

| Field | Value |
|-------|-------|
| ID | UT-09 |
| Requirement | NFR: Fail-closed |
| Precondition | All checks pass but audit write throws exception |
| Steps | 1. Mock all checks pass, 2. Mock auditRepository.insert() throws, 3. Call unmask() |
| Expected | UnmaskResult.Denied(AUDIT_FAILURE) |
| Priority | High |

### UT-10: Session Revocation

| Field | Value |
|-------|-------|
| ID | UT-10 |
| Requirement | FSD UC-04 |
| Precondition | Valid session exists |
| Steps | 1. Create session, 2. Revoke session, 3. Call unmask() with revoked token |
| Expected | UnmaskResult.Denied(SESSION_REVOKED) |
| Priority | Medium |

---

## 3. Integration Tests (IT)

### IT-01: Full Unmask Roundtrip with Real DB

| Field | Value |
|-------|-------|
| ID | IT-01 |
| Requirement | Story 3 |
| Precondition | PostgreSQL container running, PII mappings seeded |
| Steps | 1. Insert PII mapping (encrypted), 2. Create admin session, 3. Call unmask(), 4. Verify decrypted value matches original |
| Expected | Original PII value returned correctly |
| Technique | Testcontainers PostgreSQL |
| Priority | High |

### IT-02: RLS Enforcement — Non-Admin Cannot Access pii_mapping

| Field | Value |
|-------|-------|
| ID | IT-02 |
| Requirement | Story 3 + MTO-31 |
| Precondition | PostgreSQL with RLS policies active |
| Steps | 1. SET ROLE kb_developer, 2. SELECT from pii_mapping, 3. Verify empty result |
| Expected | No rows returned (RLS blocks) |
| Technique | Testcontainers PostgreSQL |
| Priority | High |

### IT-03: Audit Record Persisted After Unmask

| Field | Value |
|-------|-------|
| ID | IT-03 |
| Requirement | Story 1 |
| Precondition | Successful unmask completed |
| Steps | 1. Perform unmask, 2. Query pii_access_audit table, 3. Verify record exists |
| Expected | Audit record with correct user_id, issue_key, placeholder, success=true |
| Technique | Testcontainers PostgreSQL |
| Priority | High |

### IT-04: Audit Table Immutability

| Field | Value |
|-------|-------|
| ID | IT-04 |
| Requirement | Story 1 (BR-06) |
| Precondition | Audit records exist |
| Steps | 1. INSERT audit record, 2. Attempt UPDATE, 3. Attempt DELETE |
| Expected | UPDATE and DELETE throw permission error |
| Technique | Testcontainers PostgreSQL |
| Priority | High |

### IT-05: Rate Limit Persists Across Connection

| Field | Value |
|-------|-------|
| ID | IT-05 |
| Requirement | Story 2 (BR-07) |
| Precondition | User has performed 9 unmask operations |
| Steps | 1. Perform 9 unmask, 2. Close all connections, 3. Reconnect, 4. Perform 10th unmask (success), 5. Perform 11th unmask |
| Expected | 11th unmask returns RateLimited |
| Technique | Testcontainers PostgreSQL |
| Priority | High |

### IT-06: Sliding Window Expiry

| Field | Value |
|-------|-------|
| ID | IT-06 |
| Requirement | Story 2 (BR-03) |
| Precondition | User hit rate limit |
| Steps | 1. Insert 10 audit records with created_at = 61 min ago, 2. Call rate limit check |
| Expected | RateLimitResult.Allowed (window expired) |
| Technique | Testcontainers PostgreSQL + manual timestamp |
| Priority | Medium |

### IT-07: Audit Retention Query

| Field | Value |
|-------|-------|
| ID | IT-07 |
| Requirement | Story 5 |
| Precondition | Audit records with various ages |
| Steps | 1. Insert records at 30, 60, 91 days ago, 2. Query records older than 90 days |
| Expected | Only 91-day record returned for archival |
| Technique | Testcontainers PostgreSQL |
| Priority | Medium |

### IT-08: Fail-Closed When Audit DB Unavailable

| Field | Value |
|-------|-------|
| ID | IT-08 |
| Requirement | NFR: Fail-closed |
| Precondition | Audit table connection fails |
| Steps | 1. Configure audit to use invalid datasource, 2. Attempt unmask |
| Expected | Unmask denied with AUDIT_FAILURE reason |
| Technique | Testcontainers + connection kill |
| Priority | High |

---

## 4. E2E-API Tests

### E2E-01: MCP Tool unmask_pii Success

| Field | Value |
|-------|-------|
| ID | E2E-01 |
| Requirement | Story 3, Story 4 |
| Precondition | Server running, admin session active, PII seeded |
| Steps | 1. Call tools/call with unmask_pii tool, 2. Provide issue_key + placeholder |
| Expected | Response contains original PII value, status=success |
| Technique | Ktor TestHost |
| Priority | High |

### E2E-02: Performance — Unmask < 50ms

| Field | Value |
|-------|-------|
| ID | E2E-02 |
| Requirement | NFR: Performance |
| Precondition | Server running, warm connection pool |
| Steps | 1. Perform 10 unmask operations, 2. Measure average latency |
| Expected | Average latency < 50ms |
| Technique | Ktor TestHost + timing |
| Priority | Medium |

### E2E-03: MCP Tool unmask_pii Rate Limited

| Field | Value |
|-------|-------|
| ID | E2E-03 |
| Requirement | Story 2 |
| Precondition | Server running, admin session active |
| Steps | 1. Call unmask_pii 10 times, 2. Call 11th time |
| Expected | 11th call returns rate_limited with retry_after_seconds |
| Technique | Ktor TestHost |
| Priority | High |

---

## 5. SIT (System Integration Tests)

### SIT-01: Rate Limit Survives Application Restart

| Field | Value |
|-------|-------|
| ID | SIT-01 |
| Requirement | Story 2 (BR-07) |
| Steps | 1. Start app, 2. Perform 8 unmask, 3. Restart app, 4. Perform 3 more unmask |
| Expected | 11th total unmask is rate-limited (counter preserved in DB) |
| Type | Automated (Testcontainers) |
| Priority | High |

### SIT-02: RLS + PiiAccessService Integration

| Field | Value |
|-------|-------|
| ID | SIT-02 |
| Requirement | Story 3 + MTO-31 |
| Steps | 1. Start app with full RLS, 2. Attempt unmask as developer via MCP tool |
| Expected | Permission denied at service layer (before DB access) |
| Type | Automated |
| Priority | High |

---

## 6. Test Data

### CSV: test-pii-mappings.csv

```csv
id,issue_key,placeholder,original_value,mapping_type
uuid-1,TEST-001,{{PII_EMAIL_001}},john.doe@example.com,EMAIL
uuid-2,TEST-001,{{PII_PHONE_001}},+84-123-456-789,PHONE
uuid-3,TEST-002,{{PII_NAME_001}},Nguyen Van A,FULL_NAME
```

### CSV: test-audit-entries.csv

```csv
user_id,issue_key,placeholder,action,success,failure_reason,created_at
admin@test.com,TEST-001,{{PII_EMAIL_001}},UNMASK_PII,true,,2026-05-08T09:00:00Z
admin@test.com,TEST-001,{{PII_PHONE_001}},UNMASK_PII,true,,2026-05-08T09:05:00Z
dev@test.com,TEST-001,{{PII_EMAIL_001}},UNMASK_PII,false,INSUFFICIENT_PERMISSION,2026-05-08T09:10:00Z
```
