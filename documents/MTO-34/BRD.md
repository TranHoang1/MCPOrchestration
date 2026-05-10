# Business Requirements Document (BRD)

## MCPOrchestration — MTO-34: KB Refinery — Audit Log & Response Shaping

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-34 |
| Title | KB Refinery — Audit Log & Response Shaping |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |

---

## 1. Introduction

### 1.1 Scope

Implement a centralized audit logging service that records all access to sensitive data (BR content, PII unmask operations) and a role-based response shaping layer that filters API responses based on the caller's role (Developer sees masked data, BA/Admin sees full data, Viewer sees summaries only).

### 1.2 Out of Scope

- PII masking logic (MTO-27)
- BR masking logic (MTO-30)
- RLS policies (MTO-31)
- Encryption at rest (MTO-33)

### 1.3 Dependencies

| Dependency | Description |
|------------|-------------|
| MTO-31 | RLS roles (KbRole enum) |
| MTO-32 | PII access audit pattern |
| MTO-33 | BR access audit pattern |

---

## 2. Business Requirements

### 2.1 User Stories

| # | Story | Priority |
|---|-------|----------|
| 1 | As a security officer, I want all BR/PII access logged with user, timestamp, action, and result so that I can audit data access | MUST HAVE |
| 2 | As a system admin, I want role-based response shaping so that each role sees only appropriate data | MUST HAVE |
| 3 | As a compliance officer, I want audit logs to be immutable (append-only) so that they cannot be tampered with | MUST HAVE |
| 4 | As a BA/Admin, I want to query audit logs by user, date range, and action type | SHOULD HAVE |
| 5 | As a developer, I want audit events emitted asynchronously so they don't impact response latency | SHOULD HAVE |

### 2.2 Response Shaping Rules

| Role | BR Content | PII Data | Audit Logs | KB Entries |
|------|-----------|----------|------------|------------|
| BA_ADMIN | Full (decrypted) | Full (unmasked) | Full access | Full |
| DEVELOPER | Masked placeholders | Masked | Own actions only | Filtered (no BR) |
| LOW_PRIVILEGE (Viewer) | Hidden | Hidden | No access | Summary only |

### 2.3 Audit Event Types

| Event | Data Captured |
|-------|--------------|
| VIEW_BR | user, issue_key, sensitivity_level, success, ip |
| UNMASK_PII | user, issue_key, placeholder, success, ip |
| QUERY_KB | user, query_text (truncated), results_count |
| EXPORT_DATA | user, format, record_count |
| SESSION_CREATE | user, role, token_hash |
| SESSION_REVOKE | user, token_hash, reason |

---

## 3. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| Performance | Audit write < 5ms (async) |
| Storage | Audit retention: 90 days default |
| Immutability | No UPDATE/DELETE on audit table |
| Availability | Audit failure must NOT block main operation |

