# Functional Specification Document (FSD)

## MCPOrchestration — MTO-34: KB Refinery — Audit Log & Response Shaping

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-34 |
| Title | KB Refinery — Audit Log & Response Shaping |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |

---

## 1. System Overview

### 1.1 Components

1. **AuditService** — Centralized audit event recording (async, non-blocking)
2. **ResponseShaper** — Filters/transforms API responses based on caller role
3. **AuditRepository** — Append-only PostgreSQL storage
4. **AuditQueryService** — Query audit logs with filters

### 1.2 Component Interaction

```
MCP Tool Request → ResponseShaper.shape(role, rawResponse)
                     ├── If BR content → mask/hide based on role
                     ├── If PII data → mask/hide based on role
                     └── Return shaped response

Any sensitive operation → AuditService.log(event) [async, fire-and-forget]
```

---

## 2. Functional Requirements

### 2.1 UC-01: Log Audit Event

**Actor:** System (internal)
**Trigger:** Any sensitive data access

**Main Flow:**
1. Service calls `auditService.log(event)`
2. AuditService validates event data
3. AuditService writes to audit table asynchronously
4. Returns immediately (non-blocking)

**Exception:** If DB write fails → log error, do NOT fail the main operation

### 2.2 UC-02: Shape Response by Role

**Actor:** MCP Tool handler
**Trigger:** Before returning response to caller

**Main Flow:**
1. Handler calls `responseShaper.shape(role, response)`
2. ResponseShaper checks role against field visibility rules
3. ResponseShaper masks/removes fields not visible to role
4. Returns shaped response

### 2.3 UC-03: Query Audit Logs

**Actor:** BA/Admin
**Trigger:** Admin requests audit history

**Main Flow:**
1. Admin calls query with filters (user, dateRange, action)
2. System validates admin role
3. System queries audit table with filters
4. Returns paginated results

---

## 3. API Contracts

### 3.1 AuditEvent Data Model

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| eventType | AuditEventType | Yes | Type of event |
| userId | String | Yes | Who performed the action |
| issueKey | String? | No | Related ticket |
| action | String | Yes | Specific action description |
| success | Boolean | Yes | Whether action succeeded |
| metadata | Map<String, String> | No | Additional context |
| ipAddress | String? | No | Client IP |
| timestamp | Instant | Yes | When event occurred |

### 3.2 Response Shaping Rules

| Field Category | BA_ADMIN | DEVELOPER | LOW_PRIVILEGE |
|---------------|----------|-----------|---------------|
| business_rules | Full text | "[BR_MASKED]" | null (hidden) |
| pii_original | Full text | "[PII_MASKED]" | null (hidden) |
| sensitivity_level | Visible | Visible | Hidden |
| audit_logs | Full access | Own only | No access |
| kb_entry.content | Full | Full | Summary (first 200 chars) |

---

## 4. Data Model

### 4.1 audit_events Table

```sql
CREATE TABLE audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(30) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    issue_key VARCHAR(50),
    action VARCHAR(200) NOT NULL,
    success BOOLEAN NOT NULL,
    metadata JSONB,
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 5. Non-Functional Requirements

| Requirement | Target | Verification |
|-------------|--------|--------------|
| Audit write latency | < 5ms (async) | Performance test |
| Response shaping latency | < 2ms | Unit test timing |
| Audit immutability | No UPDATE/DELETE | DB permissions |
| Retention | 90 days | Scheduled cleanup job |

