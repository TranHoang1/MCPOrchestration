# Functional Specification Document (FSD)

## MCPOrchestration — MTO-32: KB Refinery — PII Mapping Encrypted Table

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-32 |
| Title | KB Refinery — PII Mapping Encrypted Table |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-32.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | BA Agent | Initial FSD from BRD |
| 1.1 | 2026-05-08 | TA Agent | Technical enrichment — API contracts, pseudocode |

---

## 1. Introduction

### 1.1 Purpose

This FSD specifies the functional behavior of the PII Access Control system for MTO-32. It defines how PII unmask operations are authorized, rate-limited, audited, and executed through the `PiiAccessService` interface.

### 1.2 Scope

- PII unmask authorization (role-based, admin-only)
- Rate limiting (sliding window, 10/hour/user configurable)
- PII access audit trail (immutable log)
- Session-based access tokens (30-minute expiry)
- Integration with existing `PiiMappingRepository` and `RlsConnectionWrapper`

### 1.3 Definitions & Acronyms

| Term | Definition |
|------|------------|
| PII | Personally Identifiable Information |
| Unmask | Reveal original PII value from encrypted placeholder |
| Sliding Window | Rate limit window that moves with time |
| RLS | Row-Level Security (PostgreSQL) |

### 1.4 References

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-32.docx |
| MTO-26 TDD | documents/MTO-26/TDD.md |
| MTO-31 TDD | documents/MTO-31/TDD.md |

---

## 2. System Overview

### 2.1 System Context

The PII Access Control system sits between the MCP tool layer and the existing `PiiMappingRepository`. When a user invokes the `unmask_pii` tool, the request flows through:

1. **PiiAccessService** — orchestrates permission check, rate limit, audit
2. **RlsConnectionWrapper** — ensures DB access uses correct PostgreSQL role
3. **PiiMappingRepository** — performs actual decrypt + retrieval
4. **PiiAccessAuditRepository** — logs the access event

### 2.2 Component Interaction

```
User Request → PiiAccessService
                ├── checkPermission(role) → KbRole check
                ├── checkRateLimit(userId) → sliding window check
                ├── PiiMappingRepository.findByIssueKey() → decrypt
                └── PiiAccessAuditRepository.log() → audit trail
```

---

## 3. Functional Requirements

### 3.1 Feature: PII Unmask with Access Control

**Source:** BRD Story 1, 2, 3

#### 3.1.1 Description

Provides controlled access to unmask PII data. Only users with `kb_admin` role can unmask. Each unmask is rate-limited and audited.

#### 3.1.2 Use Case: UC-01 — Unmask PII Value

**Use Case ID:** UC-01
**Actor:** BA/Admin (kb_admin role)
**Preconditions:** User authenticated, valid session token, PII mapping exists
**Postconditions:** Original PII value returned, audit record created, rate counter incremented

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User | | Requests unmask for issue_key + placeholder |
| 2 | | PiiAccessService | Validates session token (not expired/revoked) |
| 3 | | PiiAccessService | Checks user role = kb_admin |
| 4 | | PiiAccessService | Checks rate limit (< max per window) |
| 5 | | PiiMappingRepository | Retrieves and decrypts PII value |
| 6 | | PiiAccessAuditRepository | Logs successful unmask event |
| 7 | | PiiAccessService | Returns UnmaskResult.Success(value) |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-01 | Rate limit not exceeded but close to limit | Return success + warning header X-RateLimit-Remaining |
| AF-02 | Multiple placeholders for same issue_key | Each unmask counts as separate rate limit hit |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-01 | Session token expired (>30 min) | Return UnmaskResult.Denied("SESSION_EXPIRED"), log failure |
| EF-02 | Role != kb_admin | Return UnmaskResult.Denied("INSUFFICIENT_PERMISSION"), log failure |
| EF-03 | Rate limit exceeded | Return UnmaskResult.RateLimited(retryAfterSeconds), log failure |
| EF-04 | Placeholder not found | Return UnmaskResult.Denied("NOT_FOUND"), log failure |
| EF-05 | Decryption failure | Return UnmaskResult.Denied("DECRYPTION_ERROR"), log failure |
| EF-06 | Audit write fails | Deny unmask (fail-closed), return error |

#### 3.1.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-01 | Only kb_admin role can unmask PII | BRD Story 3 |
| BR-02 | Max 10 unmask operations per hour per user (configurable) | BRD Story 2 |
| BR-03 | Rate limit uses sliding window (not fixed hourly) | BRD Story 2 |
| BR-04 | Audit write failure = unmask denied (fail-closed) | BRD NFR |
| BR-05 | Session token expires after 30 minutes of inactivity | BRD Story 3 |
| BR-06 | Audit records are immutable (no UPDATE/DELETE) | BRD Story 1 |
| BR-07 | Rate limit counter persisted in DB (survives restart) | BRD Story 2 |

#### 3.1.4 Data Specifications

**Input Data:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| userId | String | Yes | Non-blank, max 100 chars | Authenticated user identifier |
| issueKey | String | Yes | Pattern: `[A-Z]+-\d+` | Jira ticket key |
| placeholder | String | Yes | Pattern: `{{PII_.*}}`, max 200 chars | PII placeholder to unmask |
| sessionToken | String | Yes | UUID format, non-expired | Session access token |

**Output Data (Success):**

| Field | Type | Description |
|-------|------|-------------|
| originalValue | String | Decrypted PII value |
| remainingQuota | Int | Remaining unmask operations in current window |

**Output Data (Rate Limited):**

| Field | Type | Description |
|-------|------|-------------|
| retryAfterSeconds | Long | Seconds until next unmask is allowed |
| windowResetAt | Instant | When the current window resets |

#### 3.1.5 API Contract (Functional View)

**Endpoint:** Internal service call (not HTTP — invoked via MCP tool)

**Tool Name:** `unmask_pii`

**Input Parameters:**

| Parameter | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| issue_key | String | Yes | BR-01 | Target ticket |
| placeholder | String | Yes | BR-01 | PII placeholder to reveal |

**Output Data:**

| Field | Type | Description |
|-------|------|-------------|
| status | String | "success" / "denied" / "rate_limited" |
| value | String? | Decrypted value (only on success) |
| reason | String? | Denial reason (only on denied/rate_limited) |
| retry_after_seconds | Long? | Seconds to wait (only on rate_limited) |
| remaining_quota | Int? | Remaining operations in window |

**Business Error Scenarios:**

| Scenario | User Message | Trigger Condition |
|----------|-------------|-------------------|
| Permission denied | "Insufficient permissions. Only admin role can unmask PII." | BR-01 violated |
| Rate limited | "Rate limit exceeded. Retry after {N} seconds." | BR-02 violated |
| Not found | "PII mapping not found for the specified placeholder." | Placeholder doesn't exist |
| Session expired | "Session expired. Please re-authenticate." | BR-05 violated |

---

### 3.2 Feature: Rate Limiting

**Source:** BRD Story 2

#### 3.2.1 Description

Sliding window rate limiting prevents bulk PII exfiltration. Counter is persisted in database.

#### 3.2.2 Use Case: UC-02 — Check Rate Limit

**Use Case ID:** UC-02
**Actor:** System (internal)
**Preconditions:** User identity known
**Postconditions:** Rate limit status determined

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | | RateLimitService | Count unmask events for userId in last windowDuration |
| 2 | | RateLimitService | Compare count against maxPerWindow |
| 3 | | RateLimitService | Return RateLimitResult (allowed/exceeded) |

**Processing Logic (Pseudocode):**

```
fun checkRateLimit(userId: String, config: PiiAccessConfig): RateLimitResult {
    val windowStart = now() - config.windowDuration
    val count = auditRepository.countByUserSince(userId, "UNMASK_PII", windowStart)
    
    if (count >= config.maxUnmaskPerWindow) {
        val oldestInWindow = auditRepository.findOldestInWindow(userId, windowStart)
        val retryAfter = oldestInWindow + config.windowDuration - now()
        return RateLimitResult.Exceeded(retryAfter)
    }
    
    return RateLimitResult.Allowed(remaining = config.maxUnmaskPerWindow - count)
}
```

---

### 3.3 Feature: PII Access Audit Trail

**Source:** BRD Story 1, 5

#### 3.3.1 Description

Every unmask attempt (success or failure) is recorded in an append-only audit table.

#### 3.3.2 Use Case: UC-03 — Log PII Access Event

**Use Case ID:** UC-03
**Actor:** System (internal)
**Preconditions:** Unmask attempt occurred
**Postconditions:** Audit record persisted

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | | PiiAccessAuditRepository | Create audit entry with all fields |
| 2 | | PiiAccessAuditRepository | INSERT into pii_access_audit table |
| 3 | | PiiAccessAuditRepository | Return success/failure |

#### 3.3.3 Data Specifications

**Audit Entry Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| id | Long (BIGSERIAL) | Auto | Primary key |
| userId | String | Yes | Who performed unmask |
| issueKey | String | Yes | Which ticket's PII |
| placeholder | String | Yes | Which PII placeholder |
| action | AuditAction enum | Yes | UNMASK_PII |
| success | Boolean | Yes | Whether unmask succeeded |
| failureReason | String? | No | Reason if failed |
| ipAddress | String? | No | Client IP address |
| createdAt | Instant | Yes | Event timestamp (DB server time) |

---

### 3.4 Feature: Session-Based Access

**Source:** BRD Story 3

#### 3.4.1 Description

PII unmask requires a valid session token that expires after 30 minutes of inactivity.

#### 3.4.2 Use Case: UC-04 — Create PII Access Session

**Use Case ID:** UC-04
**Actor:** BA/Admin
**Preconditions:** User authenticated with kb_admin role
**Postconditions:** Session token issued, valid for 30 minutes

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User | | Requests PII access session |
| 2 | | PiiSessionService | Validates user role = kb_admin |
| 3 | | PiiSessionService | Generates UUID session token |
| 4 | | PiiSessionService | Stores token with expiry (now + 30 min) |
| 5 | | PiiSessionService | Returns session token |

**Session Data:**

| Field | Type | Description |
|-------|------|-------------|
| token | UUID | Unique session identifier |
| userId | String | Bound user identity |
| role | KbRole | Role at creation time |
| createdAt | Instant | Token creation time |
| expiresAt | Instant | Token expiry (createdAt + 30 min) |
| revoked | Boolean | Whether explicitly revoked |

---

## 4. Data Model

### 4.1 Logical Entities

#### Entity: pii_access_audit

| Attribute | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| id | BIGSERIAL | Yes | — | Auto-increment PK |
| user_id | VARCHAR(100) | Yes | BR-01 | Authenticated user |
| issue_key | VARCHAR(50) | Yes | — | Target ticket |
| placeholder | VARCHAR(200) | Yes | — | PII placeholder |
| action | VARCHAR(20) | Yes | — | UNMASK_PII |
| success | BOOLEAN | Yes | — | Operation result |
| failure_reason | VARCHAR(200) | No | — | Denial reason |
| ip_address | INET | No | — | Client IP |
| created_at | TIMESTAMPTZ | Yes | BR-06 | Server timestamp |

**Constraints:**
- No UPDATE/DELETE allowed (append-only via REVOKE)
- Index on (user_id, created_at) for rate limit queries
- Index on (issue_key) for audit queries

#### Entity: pii_access_session

| Attribute | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| token | UUID | Yes | BR-05 | Session token PK |
| user_id | VARCHAR(100) | Yes | — | Bound user |
| role | VARCHAR(20) | Yes | BR-01 | Role at creation |
| created_at | TIMESTAMPTZ | Yes | — | Creation time |
| expires_at | TIMESTAMPTZ | Yes | BR-05 | Expiry time |
| revoked | BOOLEAN | Yes | — | Revocation flag |

**Relationships:**

| From Entity | To Entity | Cardinality | Description |
|-------------|-----------|-------------|-------------|
| pii_access_audit | pii_mapping | N:1 | Audit references PII mapping by placeholder |
| pii_access_session | pii_access_audit | 1:N | Session can have multiple audit entries |

---

## 5. Integration Specifications

### 5.1 External System: PiiMappingRepository (MTO-26)

| Attribute | Value |
|-----------|-------|
| Purpose | Retrieve and decrypt PII values |
| Direction | Outbound (read) |
| Data Format | Kotlin objects (in-process) |
| Frequency | On-demand (per unmask request) |

### 5.2 External System: RlsConnectionWrapper (MTO-31)

| Attribute | Value |
|-----------|-------|
| Purpose | Enforce PostgreSQL RLS for DB access |
| Direction | Outbound (wrap DB calls) |
| Data Format | JDBC Connection with SET LOCAL ROLE |
| Frequency | Every DB operation |

### 5.3 External System: AuditService (MTO-34)

| Attribute | Value |
|-----------|-------|
| Purpose | Centralized audit logging |
| Direction | Outbound (write) |
| Data Format | AuditEntry objects |
| Frequency | Every unmask attempt |

---

## 6. Processing Logic

### 6.1 Unmask PII Process

**Trigger:** User invokes unmask_pii MCP tool
**Input:** userId, issueKey, placeholder, sessionToken
**Output:** UnmaskResult (Success/Denied/RateLimited)

**Processing Steps:**

| Step | Description | Error Handling |
|------|-------------|----------------|
| 1 | Validate session token exists and not expired/revoked | Return Denied("SESSION_EXPIRED") |
| 2 | Verify session.role == BA_ADMIN | Return Denied("INSUFFICIENT_PERMISSION") |
| 3 | Count recent unmask events for userId in sliding window | Return error if DB unavailable |
| 4 | If count >= maxPerWindow, calculate retryAfter | Return RateLimited(retryAfter) |
| 5 | Execute PiiMappingRepository.findByIssueKey via RLS | Return Denied("NOT_FOUND") if missing |
| 6 | Find specific placeholder in results | Return Denied("NOT_FOUND") if not found |
| 7 | Log audit event (success=true) | If audit fails → deny unmask (fail-closed) |
| 8 | Return decrypted original value | — |

---

## 7. Security Requirements

### 7.1 Authentication & Authorization

| Role | Permissions | Features |
|------|-------------|----------|
| kb_admin | Unmask PII, view audit | Full PII access |
| kb_developer | None | Cannot unmask |
| kb_viewer | None | Cannot unmask |

### 7.2 Data Sensitivity Classification

| Data Type | Classification | Business Requirement |
|-----------|---------------|---------------------|
| PII original values | Restricted | Encrypted at rest, admin-only access |
| Audit records | Internal | Immutable, 90-day retention |
| Session tokens | Confidential | 30-min expiry, revocable |

### 7.3 Audit Trail

| Event | Logged Fields | Retention | Business Reason |
|-------|--------------|-----------|-----------------|
| UNMASK_PII (success) | user, issue, placeholder, timestamp | 90 days | Compliance |
| UNMASK_PII (denied) | user, issue, placeholder, reason, timestamp | 90 days | Security monitoring |

---

## 8. Non-Functional Requirements

| Category | Business Requirement | Acceptance Criteria |
|----------|---------------------|---------------------|
| Performance | Unmask latency < 50ms | Including all checks + decrypt |
| Security | Fail-closed on audit failure | Unmask denied if audit write fails |
| Reliability | Rate limit survives restart | Counter in DB, not memory |
| Data Retention | 90-day audit retention | Archived, not deleted |
| Scalability | Support 100 concurrent unmask requests | Connection pool handles load |

---

## 9. Error Handling (User-Facing)

### 9.1 Error Scenarios

| Scenario | Severity | User Message | Expected Behavior |
|----------|----------|-------------|-------------------|
| Session expired | Warning | "Session expired. Please re-authenticate." | User re-authenticates |
| Permission denied | Warning | "Insufficient permissions." | User contacts admin |
| Rate limited | Warning | "Rate limit exceeded. Retry after {N}s." | User waits |
| Not found | Info | "PII mapping not found." | User verifies input |
| System error | Critical | "Internal error. Please try again." | Ops team alerted |

---

## 10. Testing Considerations

### 10.1 Test Scenarios

| ID | Scenario | Input | Expected Output | Priority |
|----|----------|-------|-----------------|----------|
| TC-01 | Admin unmask success | valid session, admin role, valid placeholder | Original value returned | High |
| TC-02 | Developer denied | developer role | Permission denied | High |
| TC-03 | Rate limit exceeded | 11th request in 1 hour | 429 with retry-after | High |
| TC-04 | Expired session | token > 30 min old | Session expired error | High |
| TC-05 | Audit write failure | DB connection error on audit | Unmask denied | High |
| TC-06 | Sliding window reset | 10 requests, wait 61 min, request again | Success | Medium |
| TC-07 | Revoked session | explicitly revoked token | Session revoked error | Medium |
| TC-08 | Non-existent placeholder | valid session, invalid placeholder | Not found error | Medium |

---

## 11. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | System Context | [system-context.png](diagrams/system-context.png) | [system-context.drawio](diagrams/system-context.drawio) |
| 2 | Sequence — Unmask Flow | [sequence-unmask.png](diagrams/sequence-unmask.png) | [sequence-unmask.drawio](diagrams/sequence-unmask.drawio) |
| 3 | State — Session Lifecycle | [state-session.png](diagrams/state-session.png) | [state-session.drawio](diagrams/state-session.drawio) |
