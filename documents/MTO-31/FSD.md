# Functional Specification Document (FSD)

## MCPOrchestration — MTO-31: KB Refinery — PostgreSQL RLS Policies

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-31 |
| Title | KB Refinery — PostgreSQL RLS Policies |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-31.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | BA Agent | Initial document — functional specification from BRD |
| 1.1 | 2026-05-08 | TA Agent | Technical enrichment — API contracts, pseudocode, integration specs |

---

## 1. Introduction

### 1.1 Purpose

This FSD specifies the functional behavior of PostgreSQL Row-Level Security (RLS) policies for the Knowledge Base Refinery module. It defines how the system enforces data access control at the database level, ensuring different user roles can only access data appropriate to their permission level.

### 1.2 Scope

- Flyway migration scripts to create PostgreSQL roles and RLS policies
- Application-layer integration via `SET LOCAL role` within HikariCP transactions
- RoleContextService to determine current user role from request context
- RlsConnectionWrapper to wrap database connections with role context

### 1.3 Definitions & Acronyms

| Term | Definition |
|------|------------|
| RLS | Row-Level Security — PostgreSQL feature restricting row/column access per role |
| SET LOCAL | PostgreSQL command setting a parameter for current transaction only |
| HikariCP | High-performance JDBC connection pool for JVM |
| kb_entries | Main knowledge base table storing content with sensitivity levels |
| pii_mapping | Table mapping PII placeholders to original values |
| FORCE RLS | PostgreSQL option enforcing RLS even for table owner |

### 1.4 References

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-31.docx |
| MTO-26 (kb_entries schema) | documents/MTO-26/ |
| PostgreSQL RLS Docs | https://www.postgresql.org/docs/16/ddl-rowsecurity.html |

---

## 2. System Overview

### 2.1 System Context

The RLS system operates as a security layer between the application and PostgreSQL database. When any service queries `kb_entries` or `pii_mapping`, the RlsConnectionWrapper intercepts the connection, sets the appropriate PostgreSQL role via `SET LOCAL`, and PostgreSQL automatically enforces column/row filtering based on defined policies.

**External Actors:**
- **Application Services** — Request KB data through RlsConnectionWrapper
- **PostgreSQL** — Enforces RLS policies at query execution time
- **HikariCP** — Provides pooled connections that are wrapped with role context

### 2.2 System Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Application Layer                      │
│                                                          │
│  ┌──────────────┐    ┌───────────────────┐              │
│  │ Request      │───▶│ RoleContextService │              │
│  │ Handler      │    │ (determine role)   │              │
│  └──────────────┘    └───────────────────┘              │
│         │                      │                         │
│         ▼                      ▼                         │
│  ┌──────────────────────────────────────┐               │
│  │       RlsConnectionWrapper           │               │
│  │  (SET LOCAL role before each query)  │               │
│  └──────────────────────────────────────┘               │
│         │                                                │
│         ▼                                                │
│  ┌──────────────┐                                       │
│  │   HikariCP   │                                       │
│  │ (conn pool)  │                                       │
│  └──────────────┘                                       │
└─────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│                   PostgreSQL 16+                          │
│                                                          │
│  ┌─────────────────────────────────────────────────┐    │
│  │              RLS Policies                         │    │
│  │  ┌─────────────┐  ┌──────────────┐             │    │
│  │  │ kb_entries  │  │ pii_mapping  │             │    │
│  │  │ policies    │  │ policies     │             │    │
│  │  └─────────────┘  └──────────────┘             │    │
│  └─────────────────────────────────────────────────┘    │
│                                                          │
│  Roles: kb_developer | kb_admin | kb_viewer             │
└─────────────────────────────────────────────────────────┘
```

---

## 3. Functional Requirements

### 3.1 Feature: PostgreSQL Role Creation

**Source:** BRD Story 1

#### 3.1.1 Description

Create three PostgreSQL database roles that represent the application's access levels. These roles are used with `SET LOCAL role` to activate RLS policies per transaction.

#### 3.1.2 Use Case

**Use Case ID:** UC-01
**Actor:** System (Flyway migration)
**Preconditions:** PostgreSQL 16+ database exists, Flyway is configured
**Postconditions:** Three roles exist in PostgreSQL with appropriate base permissions

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | | Flyway | Execute V8__create_kb_roles.sql migration |
| 2 | | PostgreSQL | CREATE ROLE kb_developer NOLOGIN |
| 3 | | PostgreSQL | CREATE ROLE kb_admin NOLOGIN |
| 4 | | PostgreSQL | CREATE ROLE kb_viewer NOLOGIN |
| 5 | | PostgreSQL | GRANT USAGE ON SCHEMA public TO all roles |
| 6 | | PostgreSQL | GRANT SELECT on kb_entries TO kb_developer, kb_viewer |
| 7 | | PostgreSQL | GRANT ALL on kb_entries TO kb_admin |
| 8 | | PostgreSQL | GRANT SELECT on pii_mapping TO kb_admin |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | Role already exists | Use IF NOT EXISTS clause, migration is idempotent |
| EF-2 | Insufficient privileges | Migration fails, Flyway marks as failed, requires DBA intervention |

#### 3.1.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-01 | Roles are NOLOGIN — cannot be used for direct database connections | Security requirement |
| BR-02 | Application database user must be granted SET ROLE permission for all 3 roles | HikariCP integration |
| BR-03 | Role creation is idempotent — re-running migration does not fail | Flyway best practice |

---

### 3.2 Feature: RLS Policy on kb_entries

**Source:** BRD Stories 1, 2, 3, 4

#### 3.2.1 Description

Enable Row-Level Security on `kb_entries` table and create policies that control which columns each role can access. Since PostgreSQL RLS operates at row level, column-level filtering is achieved through security barrier views combined with RLS.

#### 3.2.2 Use Case

**Use Case ID:** UC-02
**Actor:** Application Service (via RlsConnectionWrapper)
**Preconditions:** Roles exist (UC-01 complete), kb_entries table exists (MTO-26)
**Postconditions:** Query results are filtered based on active role

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | App Service | | Requests KB data |
| 2 | | RlsConnectionWrapper | Gets connection from HikariCP |
| 3 | | RlsConnectionWrapper | Executes `SET LOCAL role = 'kb_developer'` |
| 4 | | PostgreSQL | Activates RLS policies for kb_developer |
| 5 | App Service | | Executes `SELECT * FROM kb_entries_view` |
| 6 | | PostgreSQL | Returns only permitted columns based on active role |
| 7 | | RlsConnectionWrapper | Transaction commits, role resets automatically |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-1 | Role is kb_admin | Step 6 returns ALL columns including business_rules |
| AF-2 | Role is kb_viewer | Step 6 returns only id, issue_key, masked_full |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | No role set (SET LOCAL not called) | PostgreSQL denies access — returns empty result set |
| EF-2 | Invalid role name | SET LOCAL fails, transaction aborted, connection returned to pool |
| EF-3 | Connection pool exhausted | HikariCP throws timeout exception, request fails with 503 |

#### 3.2.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-04 | kb_developer sees: id, issue_key, public_content, technical_content, created_at, updated_at | BRD Story 2 |
| BR-05 | kb_admin sees: ALL columns (full access) | BRD Story 3 |
| BR-06 | kb_viewer sees: id, issue_key, masked_full ONLY | BRD Story 4 |
| BR-07 | Deny-by-default: no role set = no data returned | BRD NFR |
| BR-08 | FORCE ROW LEVEL SECURITY enabled — even table owner is subject to RLS | BRD Story 1 |

#### 3.2.4 Data Specifications

**Column Access Matrix:**

| Column | kb_developer | kb_admin | kb_viewer |
|--------|:---:|:---:|:---:|
| id | ✅ | ✅ | ✅ |
| issue_key | ✅ | ✅ | ✅ |
| public_content | ✅ | ✅ | ❌ |
| technical_content | ✅ | ✅ | ❌ |
| business_rules | ❌ | ✅ | ❌ |
| pii_data | ❌ | ✅ | ❌ |
| sensitivity_level | ❌ | ✅ | ❌ |
| masked_full | ✅ | ✅ | ✅ |
| created_at | ✅ | ✅ | ❌ |
| updated_at | ✅ | ✅ | ❌ |

#### 3.2.5 API Contract (Functional View)

**Internal Service Interface:** `RlsConnectionWrapper.executeWithRole()`

**Input Parameters:**

| Parameter | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| role | KbRole (enum) | Yes | BR-04/05/06 | The role to activate for this transaction |
| block | (Connection) -> T | Yes | — | The database operation to execute |

**Output Data:**

| Field | Type | Description |
|-------|------|-------------|
| result | T | Query result filtered by RLS policies |

**Business Error Scenarios:**

| Scenario | User Message | Trigger Condition |
|----------|-------------|-------------------|
| Invalid role | "Access denied — invalid security context" | Role enum value has no matching PostgreSQL role |
| Connection timeout | "Service temporarily unavailable" | HikariCP pool exhausted |
| Role not permitted | "Insufficient permissions" | Attempting write with read-only role |

---

### 3.3 Feature: RLS Policy on pii_mapping

**Source:** BRD Stories 2, 3, 4

#### 3.3.1 Description

Enable RLS on `pii_mapping` table with stricter access control. Only `kb_admin` can access this table. Developers and viewers are completely denied access.

#### 3.3.2 Use Case

**Use Case ID:** UC-03
**Actor:** Application Service (BA Admin user)
**Preconditions:** Roles exist, pii_mapping table exists (MTO-26), user has kb_admin role
**Postconditions:** PII mapping data returned only to admin role

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | BA Admin | | Requests PII unmask operation |
| 2 | | RoleContextService | Determines user role = kb_admin |
| 3 | | RlsConnectionWrapper | SET LOCAL role = 'kb_admin' |
| 4 | | PostgreSQL | Allows SELECT on pii_mapping |
| 5 | | App | Returns PII mapping data |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-1 | User is developer | Step 4: PostgreSQL denies access, returns permission error |
| AF-2 | User is viewer | Step 4: PostgreSQL denies access, returns permission error |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | kb_developer attempts access | Permission denied error returned |
| EF-2 | kb_viewer attempts access | Permission denied error returned |

#### 3.3.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-09 | ONLY kb_admin can access pii_mapping table | BRD Story 3 |
| BR-10 | kb_developer has NO ACCESS to pii_mapping | BRD Story 2 |
| BR-11 | kb_viewer has NO ACCESS to pii_mapping | BRD Story 4 |
| BR-12 | Access denial returns PostgreSQL permission error (not empty result) | Security best practice |

#### 3.3.4 Data Specifications

**Access Matrix — pii_mapping:**

| Operation | kb_developer | kb_admin | kb_viewer |
|-----------|:---:|:---:|:---:|
| SELECT | ❌ | ✅ | ❌ |
| INSERT | ❌ | ✅ | ❌ |
| UPDATE | ❌ | ✅ | ❌ |
| DELETE | ❌ | ❌ | ❌ |

---

### 3.4 Feature: RoleContextService

**Source:** BRD Story 5

#### 3.4.1 Description

Service that determines the current user's KB role from the request context. Maps application-level user identity to one of the three PostgreSQL roles.

#### 3.4.2 Use Case

**Use Case ID:** UC-04
**Actor:** Application (internal)
**Preconditions:** User is authenticated, request context contains user identity
**Postconditions:** KbRole enum value determined for current request

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | | Request Handler | Extracts user identity from request |
| 2 | | RoleContextService | Looks up user's KB role from configuration/mapping |
| 3 | | RoleContextService | Returns KbRole enum value |
| 4 | | RlsConnectionWrapper | Uses KbRole to SET LOCAL role |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-1 | User has no explicit KB role mapping | Default to kb_viewer (least privilege) |
| AF-2 | User has multiple roles | Use highest privilege role applicable to current operation |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | No user context available | Throw SecurityException — deny access |
| EF-2 | Role mapping configuration missing | Log error, default to kb_viewer |

#### 3.4.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-13 | Default role is kb_viewer (least privilege principle) | Security best practice |
| BR-14 | Role determination must happen BEFORE any KB query | BRD Story 5 |
| BR-15 | Role mapping is configurable via application.yml | Operational requirement |

#### 3.4.4 Data Specifications

**Role Mapping Configuration:**

| Application Role/Group | KB Role | Description |
|----------------------|---------|-------------|
| ROLE_DEVELOPER | kb_developer | Technical staff accessing KB for development |
| ROLE_BA, ROLE_ADMIN | kb_admin | Business analysts and administrators |
| ROLE_USER, default | kb_viewer | General users with read-only masked access |

---

### 3.5 Feature: RlsConnectionWrapper

**Source:** BRD Story 5

#### 3.5.1 Description

Wrapper around HikariCP connections that automatically sets the PostgreSQL role before executing any query against RLS-protected tables. Ensures role is always set within a transaction boundary.

#### 3.5.2 Use Case

**Use Case ID:** UC-05
**Actor:** Application Service
**Preconditions:** HikariCP pool configured, roles exist in PostgreSQL
**Postconditions:** Query executed with correct role context, connection returned to pool clean

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Service | | Calls executeWithRole(role, block) |
| 2 | | Wrapper | Acquires connection from HikariCP |
| 3 | | Wrapper | Begins transaction (if not already in one) |
| 4 | | Wrapper | Executes: `SET LOCAL role = '{role.pgRoleName}'` |
| 5 | | Wrapper | Executes the provided block with the connection |
| 6 | | Wrapper | Commits transaction |
| 7 | | Wrapper | Returns connection to pool (role auto-resets) |
| 8 | | Wrapper | Returns result to caller |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | Block throws exception | Rollback transaction, return connection to pool, propagate exception |
| EF-2 | SET LOCAL fails | Rollback, return connection, throw RlsConfigurationException |
| EF-3 | Connection acquisition timeout | Throw ConnectionTimeoutException |

#### 3.5.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-16 | SET LOCAL is transaction-scoped — role resets on commit/rollback | PostgreSQL behavior |
| BR-17 | Connection MUST be returned to pool in clean state | HikariCP requirement |
| BR-18 | Role MUST be set BEFORE any query on protected tables | Security requirement |
| BR-19 | Wrapper must be thread-safe for concurrent access | Performance requirement |

#### 3.5.4 Pseudocode

```kotlin
suspend fun <T> executeWithRole(role: KbRole, block: suspend (Connection) -> T): T {
    val connection = dataSource.connection
    try {
        connection.autoCommit = false
        connection.createStatement().execute("SET LOCAL ROLE '${role.pgRoleName}'")
        val result = block(connection)
        connection.commit()
        return result
    } catch (e: Exception) {
        connection.rollback()
        throw e
    } finally {
        connection.autoCommit = true
        connection.close() // returns to pool
    }
}
```

---

## 4. Data Model

### 4.1 Logical Entities

#### Entity: kb_entries (existing — from MTO-26)

| Attribute | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| id | UUID | Yes | — | Primary key |
| issue_key | VARCHAR(20) | Yes | — | Jira ticket reference |
| public_content | TEXT | No | BR-04 | Publicly accessible content |
| technical_content | TEXT | No | BR-04 | Technical documentation |
| business_rules | TEXT | No | BR-05 | Sensitive business rules (admin only) |
| pii_data | JSONB | No | BR-05 | PII references (admin only) |
| sensitivity_level | VARCHAR(20) | Yes | BR-05 | Data classification level |
| masked_full | TEXT | No | BR-06 | Fully masked version for viewers |
| created_at | TIMESTAMP | Yes | BR-04 | Record creation time |
| updated_at | TIMESTAMP | Yes | BR-04 | Last modification time |

#### Entity: pii_mapping (existing — from MTO-26)

| Attribute | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| id | UUID | Yes | — | Primary key |
| kb_entry_id | UUID | Yes | — | FK to kb_entries |
| placeholder | VARCHAR(100) | Yes | — | Masked placeholder text |
| original_value | TEXT | Yes | BR-09 | Original PII value (admin only) |
| pii_type | VARCHAR(50) | Yes | — | Type of PII (email, phone, etc.) |
| created_at | TIMESTAMP | Yes | — | Record creation time |

#### Entity: KbRole (application enum)

| Value | PostgreSQL Role | Permissions |
|-------|----------------|-------------|
| DEVELOPER | kb_developer | SELECT limited columns on kb_entries |
| BA_ADMIN | kb_admin | ALL on kb_entries, SELECT on pii_mapping |
| LOW_PRIVILEGE | kb_viewer | SELECT masked only on kb_entries |

---

## 5. Integration Specifications

### 5.1 External System: HikariCP Connection Pool

| Attribute | Value |
|-----------|-------|
| Purpose | Provide pooled database connections for RLS-wrapped queries |
| Direction | Bidirectional |
| Data Format | JDBC Connection objects |
| Frequency | Real-time (per request) |

**Data Exchange:**

| Our Data | External Data | Direction | Business Rule |
|----------|--------------|-----------|---------------|
| Role context (KbRole) | SET LOCAL statement | Send | BR-16, BR-18 |
| Query results | Filtered rows/columns | Receive | BR-04/05/06 |

### 5.2 External System: Flyway Migration Engine

| Attribute | Value |
|-----------|-------|
| Purpose | Execute DDL scripts to create roles and policies |
| Direction | Outbound (SQL scripts → PostgreSQL) |
| Data Format | SQL migration files |
| Frequency | On application startup / deployment |

---

## 6. Processing Logic

### 6.1 Role Resolution Process

**Trigger:** Incoming request that requires KB data access
**Input:** User identity from request context
**Output:** KbRole enum value

**Processing Steps:**

| Step | Description | Error Handling |
|------|-------------|----------------|
| 1 | Extract user identity from request headers/token | If missing → throw SecurityException |
| 2 | Look up role mapping in RlsConfig | If no mapping → default to kb_viewer |
| 3 | Validate role is active/enabled | If disabled → throw RoleDisabledException |
| 4 | Return KbRole enum value | — |

### 6.2 Connection Wrapping Process

**Trigger:** Service calls executeWithRole()
**Input:** KbRole + database operation block
**Output:** Query result (type T)

**Processing Steps:**

| Step | Description | Error Handling |
|------|-------------|----------------|
| 1 | Acquire connection from HikariCP pool | Timeout → throw ConnectionTimeoutException |
| 2 | Disable autoCommit (begin transaction) | SQL error → close connection, rethrow |
| 3 | Execute SET LOCAL ROLE = '{role}' | Invalid role → rollback, throw RlsConfigException |
| 4 | Execute provided block (actual query) | Any exception → rollback, rethrow |
| 5 | Commit transaction | Commit failure → rollback, rethrow |
| 6 | Reset autoCommit, return connection to pool | Always execute in finally block |

---

## 7. Security Requirements

### 7.1 Authentication & Authorization

| Role | Permissions | Tables/Operations |
|------|-------------|-------------------|
| kb_developer | Read limited columns | kb_entries (SELECT limited), pii_mapping (NO ACCESS) |
| kb_admin | Full access | kb_entries (ALL), pii_mapping (SELECT, INSERT, UPDATE) |
| kb_viewer | Read masked only | kb_entries (SELECT masked), pii_mapping (NO ACCESS) |

### 7.2 Data Sensitivity Classification

| Data Type | Classification | Business Requirement |
|-----------|---------------|---------------------|
| public_content | Public | Accessible to all authenticated roles |
| technical_content | Internal | Accessible to developers and admins |
| business_rules | Confidential | Admin-only, contains sensitive business logic |
| pii_data | Restricted | Admin-only, contains personal information |
| original_value (pii_mapping) | Restricted | Admin-only, actual PII values |

### 7.3 Security Design Principles

| Principle | Implementation |
|-----------|---------------|
| Deny-by-default | No role set = no data returned |
| Least privilege | Default role is kb_viewer |
| Defense in depth | RLS at DB level + application-level checks |
| No role escalation | Application cannot bypass RLS without superuser |
| Transaction isolation | SET LOCAL scoped to transaction only |

---

## 8. Non-Functional Requirements

| Category | Business Requirement | Acceptance Criteria |
|----------|---------------------|---------------------|
| Performance | RLS overhead minimal | Query latency increase < 5ms per query |
| Performance | Connection pool efficiency | No additional connections needed for RLS |
| Reliability | Connection safety | Role always resets after transaction (SET LOCAL behavior) |
| Scalability | High concurrency | Support 100+ concurrent connections with different roles |
| Security | Deny-by-default | Zero data leakage when role not set |
| Maintainability | Idempotent migrations | Re-running migrations does not break existing policies |

---

## 9. Error Handling (User-Facing)

### 9.1 Error Scenarios

| Scenario | Severity | User Message | Expected Behavior |
|----------|----------|-------------|-------------------|
| No role context | Critical | "Access denied — security context not established" | Request rejected with 403 |
| Invalid role | Critical | "Access denied — invalid security context" | Request rejected with 403 |
| Permission denied by RLS | Warning | "Insufficient permissions for requested data" | Return 403 with role info |
| Connection pool exhausted | Critical | "Service temporarily unavailable" | Return 503, retry after backoff |
| Migration failure | Critical | "System configuration error" | Application fails to start, alert ops |

---

## 10. Testing Considerations

### 10.1 Test Scenarios

| ID | Scenario | Input | Expected Output | Priority |
|----|----------|-------|-----------------|----------|
| TC-01 | Developer queries kb_entries | role=kb_developer, SELECT * | Only public_content, technical_content visible | High |
| TC-02 | Admin queries kb_entries | role=kb_admin, SELECT * | All columns visible | High |
| TC-03 | Viewer queries kb_entries | role=kb_viewer, SELECT * | Only masked_full visible | High |
| TC-04 | Developer queries pii_mapping | role=kb_developer, SELECT * | Permission denied | High |
| TC-05 | No role set, query kb_entries | No SET LOCAL, SELECT * | Empty result or denied | High |
| TC-06 | Concurrent requests different roles | 10 parallel requests | Each sees only its role's data | High |
| TC-07 | Transaction rollback resets role | SET LOCAL + rollback | Next query has no role | Medium |
| TC-08 | Connection pool role isolation | Sequential requests on same connection | Each request independent | High |
| TC-09 | Admin writes to kb_entries | role=kb_admin, INSERT | Success | Medium |
| TC-10 | Developer writes to kb_entries | role=kb_developer, INSERT | Permission denied | High |

---

## 11. Appendix

### Flyway Migration Plan

| Migration | File | Description |
|-----------|------|-------------|
| V8 | V8__create_kb_roles.sql | Create 3 PostgreSQL roles + grant base permissions |
| V9 | V9__enable_rls_kb_entries.sql | Enable RLS on kb_entries + create column-filtering policies |
| V10 | V10__enable_rls_pii_mapping.sql | Enable RLS on pii_mapping + admin-only policy |

### Package Structure

```
com.orchestrator.mcp.security/
├── RoleContextService.kt          (interface)
├── RoleContextServiceImpl.kt      (implementation)
├── RlsConnectionWrapper.kt        (connection wrapper)
├── model/
│   └── KbRole.kt                  (enum: DEVELOPER, BA_ADMIN, LOW_PRIVILEGE)
├── config/
│   └── RlsConfig.kt              (configuration data class)
└── di/
    └── SecurityModule.kt          (Koin DI module)
```

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | System Context | [system-context.png](diagrams/system-context.png) | [system-context.drawio](diagrams/system-context.drawio) |
| 2 | RLS Sequence Flow | [sequence-rls-flow.png](diagrams/sequence-rls-flow.png) | [sequence-rls-flow.drawio](diagrams/sequence-rls-flow.drawio) |
| 3 | Role State Diagram | [state-role.png](diagrams/state-role.png) | [state-role.drawio](diagrams/state-role.drawio) |
