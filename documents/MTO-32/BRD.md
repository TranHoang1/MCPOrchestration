# Business Requirements Document (BRD)

## MCPOrchestration — MTO-32: KB Refinery — PII Mapping Encrypted Table

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-32 |
| Title | KB Refinery — PII Mapping Encrypted Table |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | BA Agent | Initial document — auto-generated from Jira ticket MTO-32 |

---

## 1. Introduction

### 1.1 Scope

Enhance the existing `pii_mapping` table security by adding access control, audit trail for PII unmask operations, and rate limiting. This ensures that every PII unmask operation is authorized, logged, and rate-limited to prevent data exfiltration.

### 1.2 Out of Scope

- Initial pii_mapping table creation (done in MTO-26)
- RLS policy creation (done in MTO-31)
- Business Rules encryption (covered by MTO-33)
- General audit log service (covered by MTO-34)

### 1.3 Preliminary Requirements

| Dependency | Description |
|------------|-------------|
| MTO-26 | pii_mapping table must exist |
| MTO-31 | RLS policies must be in place |
| PostgreSQL 16+ | Required for RLS integration |

---

## 2. Business Requirements

### 2.1 High Level Process Map

When a user requests to unmask PII data, the system first checks the user's role via RLS, then verifies rate limits (max 10 unmask/hour per user), logs the access attempt in the audit table, and only then returns the decrypted original value.

### 2.2 List of User Stories

| # | Story / Use Case | Priority | Source Ticket |
|---|-----------------|----------|---------------|
| 1 | As a security officer, I want every PII unmask operation to be logged so that I can audit who accessed what PII data | MUST HAVE | MTO-32 |
| 2 | As a system administrator, I want rate limiting on PII unmask so that bulk data exfiltration is prevented | MUST HAVE | MTO-32 |
| 3 | As a BA/Admin, I want to unmask PII when needed for legitimate business purposes with proper authorization | MUST HAVE | MTO-32 |
| 4 | As a developer, I want a clear interface (PiiAccessController) to check permissions before unmask | SHOULD HAVE | MTO-32 |
| 5 | As a compliance officer, I want unmask audit records retained for 90 days so that regulatory requirements are met | SHOULD HAVE | MTO-32 |

---

### 2.3 Details of User Stories

---

#### Business Flow

**Step 1:** User requests PII unmask for a specific issue_key + placeholder

**Step 2:** PiiAccessController checks user role (must be kb_admin)

**Step 3:** PiiAccessController checks rate limit (max 10 unmask/hour for this user)

**Step 4:** If rate limit exceeded → return error with retry-after time

**Step 5:** If authorized → log unmask attempt in pii_access_audit table

**Step 6:** Decrypt and return original PII value

**Step 7:** Update rate limit counter for this user

---

#### STORY 1: PII Unmask Audit Trail

> As a security officer, I want every PII unmask operation to be logged so that I can audit who accessed what PII data

**Requirement Details:**

1. Create `pii_access_audit` table (V9__pii_access_audit.sql)
2. Every unmask operation records: user_id, issue_key, placeholder, timestamp, ip_address, success/failure
3. Audit records are immutable (no UPDATE/DELETE allowed)
4. Audit records retained for minimum 90 days

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| id | BIGSERIAL | Yes | Primary key | 1 |
| user_id | VARCHAR(100) | Yes | Who performed unmask | "admin@company.com" |
| issue_key | VARCHAR(50) | Yes | Which ticket's PII | "PROJ-123" |
| placeholder | VARCHAR(200) | Yes | Which PII placeholder | "{{PII_EMAIL_001}}" |
| action | VARCHAR(20) | Yes | Action type | "UNMASK" |
| success | BOOLEAN | Yes | Whether unmask succeeded | true |
| failure_reason | VARCHAR(200) | No | Reason if failed | "RATE_LIMIT_EXCEEDED" |
| ip_address | INET | No | Client IP | "192.168.1.100" |
| created_at | TIMESTAMPTZ | Yes | When it happened | "2026-05-08T10:00:00Z" |

**Acceptance Criteria:**

1. GIVEN a successful unmask WHEN audit table is queried THEN the unmask event is recorded with all fields
2. GIVEN a failed unmask (rate limit) WHEN audit table is queried THEN the failure is recorded with reason
3. GIVEN audit records WHEN attempting DELETE THEN operation is denied (immutable)
4. GIVEN audit records older than 90 days WHEN retention policy runs THEN records are archived (not deleted)

---

#### STORY 2: Rate Limiting

> As a system administrator, I want rate limiting on PII unmask so that bulk data exfiltration is prevented

**Requirement Details:**

1. Maximum 10 unmask operations per hour per user
2. Rate limit window is sliding (not fixed hourly)
3. Rate limit counter stored in database (survives application restart)
4. When rate limit exceeded, return clear error with retry-after duration
5. Rate limit can be configured per role (admin may have higher limit)

**Acceptance Criteria:**

1. GIVEN user has performed 10 unmask in last hour WHEN requesting 11th unmask THEN return 429 with retry-after
2. GIVEN user performed 10 unmask 61 minutes ago WHEN requesting unmask now THEN operation succeeds (window expired)
3. GIVEN rate limit is configured as 10/hour WHEN admin changes to 20/hour THEN new limit applies immediately
4. GIVEN application restarts WHEN user had 8 unmask in current window THEN counter is preserved (not reset)

---

#### STORY 3: Authorized PII Unmask

> As a BA/Admin, I want to unmask PII when needed for legitimate business purposes with proper authorization

**Requirement Details:**

1. Only kb_admin role can perform unmask operations
2. kb_developer and kb_viewer roles are denied unmask
3. Unmask requires specifying issue_key + placeholder (no bulk unmask)
4. Each unmask returns exactly one original value

**Acceptance Criteria:**

1. GIVEN role = kb_admin WHEN requesting unmask for valid placeholder THEN original value is returned
2. GIVEN role = kb_developer WHEN requesting unmask THEN permission denied error
3. GIVEN role = kb_viewer WHEN requesting unmask THEN permission denied error
4. GIVEN valid role WHEN requesting unmask for non-existent placeholder THEN not found error

---

#### STORY 4: PiiAccessController Interface

> As a developer, I want a clear interface (PiiAccessController) to check permissions before unmask

**Requirement Details:**

1. Interface: `PiiAccessController` in package `com.orchestrator.mcp.piicontrol`
2. Methods: `checkPermission(userId, role, action)`, `checkRateLimit(userId)`, `unmask(userId, issueKey, placeholder)`
3. Returns sealed result type: `UnmaskResult.Success(value)` | `UnmaskResult.Denied(reason)` | `UnmaskResult.RateLimited(retryAfter)`

**Acceptance Criteria:**

1. GIVEN PiiAccessController interface WHEN used by other services THEN compile-time type safety is enforced
2. GIVEN checkPermission returns denied WHEN unmask is called THEN operation is not attempted
3. GIVEN checkRateLimit returns limited WHEN unmask is called THEN operation is not attempted and retryAfter is provided

---

## 3. Dependencies

| Dependency | Type | Related Ticket | Description |
|------------|------|----------------|-------------|
| MTO-26 | System | MTO-26 | pii_mapping table exists |
| MTO-31 | System | MTO-31 | RLS policies for role-based access |
| PostgreSQL 16+ | Infrastructure | — | Database with RLS support |
| MTO-34 | Feature | MTO-34 | Centralized audit service integration |

---

## 4. Stakeholders

| Role | Name / Team | Responsibility |
|------|-------------|----------------|
| Product Owner | Duc Nguyen | Approve requirements, UAT |
| Security Officer | Dev Team | Review audit trail design |
| Developer | Dev Team | Implement PiiAccessController |
| QA | QA Team | Test rate limiting and audit |

---

## 5. Risks and Assumptions

### 5.1 Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Rate limit bypass via multiple sessions | High | Medium | Rate limit by user_id, not session |
| Audit table grows unbounded | Medium | High | Implement 90-day retention policy with archival |
| Clock skew affects sliding window | Low | Low | Use database server time (NOW()) not application time |

### 5.2 Assumptions

- User identity is reliably available at request time
- Database clock is synchronized (NTP)
- Audit table performance is acceptable with index on (user_id, created_at)

---

## 6. Non-Functional Requirements

| Category | Requirement | Details |
|----------|-------------|---------|
| Performance | Unmask latency < 50ms | Including permission check + rate limit check + audit write |
| Security | Audit immutability | No UPDATE/DELETE on audit table |
| Security | Rate limit: 10/hour/user | Configurable per role |
| Reliability | Audit write must succeed | If audit write fails, unmask is denied (fail-closed) |
| Data Retention | 90 days minimum | Audit records archived after 90 days |

---

## 7. Related Tickets

| Ticket Key | Summary | Status | Type | Relationship |
|------------|---------|--------|------|--------------|
| MTO-32 | KB Refinery — PII Mapping Encrypted Table | Docs Review | Story | Main ticket |
| MTO-26 | KB Refinery — PII Mapping Table | Done | Story | Prerequisite |
| MTO-31 | KB Refinery — PostgreSQL RLS Policies | Docs Review | Story | Prerequisite (RLS) |
| MTO-33 | KB Refinery — BR Encryption & Access Control | Docs Review | Story | Related |
| MTO-34 | KB Refinery — Audit Log & Response Shaping | Docs Review | Story | Integration |

---

## 8. Appendix

### Glossary

| Term | Definition |
|------|------------|
| PII | Personally Identifiable Information |
| Unmask | Operation to reveal original PII value from placeholder |
| Rate Limiting | Restricting number of operations per time window |
| Sliding Window | Rate limit window that moves with time (not fixed intervals) |

### SQL Migration Reference

Migration file: `V9__pii_access_audit.sql`

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Business Flow | [business-flow.png](diagrams/business-flow.png) | [business-flow.drawio](diagrams/business-flow.drawio) |
| 2 | Use Case Diagram | [use-case.png](diagrams/use-case.png) | [use-case.drawio](diagrams/use-case.drawio) |
