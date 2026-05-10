# Business Requirements Document (BRD)

## MCPOrchestration — MTO-31: KB Refinery — PostgreSQL RLS Policies

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-31 |
| Title | KB Refinery — PostgreSQL RLS Policies |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | BA Agent | Initial document — auto-generated from Jira ticket MTO-31 |

---

## 1. Introduction

### 1.1 Scope

Implement PostgreSQL Row-Level Security (RLS) policies on `kb_entries` and `pii_mapping` tables to enforce data access control at the database level. This ensures that different user roles (developer, admin, viewer) can only access data columns appropriate to their permission level, regardless of application-layer logic.

### 1.2 Out of Scope

- Application-level authentication (handled by upstream auth service)
- UI changes for role management
- Migration of existing data to new security model
- PII unmask audit trail (covered by MTO-32)
- Business Rules encryption (covered by MTO-33)

### 1.3 Preliminary Requirements

| Dependency | Description |
|------------|-------------|
| PostgreSQL 16+ | RLS requires PostgreSQL with RLS support enabled |
| HikariCP connection pool | Existing connection pool must support SET LOCAL role |
| kb_entries table | Must exist (from prior migrations) |
| pii_mapping table | Must exist (from MTO-26) |

---

## 2. Business Requirements

### 2.1 High Level Process Map

The RLS system intercepts every database query at the PostgreSQL level. Before executing any query against protected tables, the application sets the current user role via `SET LOCAL role` within the transaction. PostgreSQL then automatically filters rows and columns based on the active role's RLS policy.

### 2.2 List of User Stories

| # | Story / Use Case | Priority | Source Ticket |
|---|-----------------|----------|---------------|
| 1 | As a system administrator, I want database-level access control so that data security is enforced regardless of application bugs | MUST HAVE | MTO-31 |
| 2 | As a developer, I want to access public and technical content from KB entries so that I can use relevant information for my work | MUST HAVE | MTO-31 |
| 3 | As a BA/Admin, I want to access all content including decrypted business rules so that I can manage knowledge base entries fully | MUST HAVE | MTO-31 |
| 4 | As a viewer, I want to see only masked content so that sensitive information is protected from unauthorized access | MUST HAVE | MTO-31 |
| 5 | As a DevOps engineer, I want RLS to work transparently with HikariCP connection pooling so that performance is not degraded | SHOULD HAVE | MTO-31 |

---

### 2.3 Details of User Stories

---

#### Business Flow

**Step 1:** Application receives a request requiring KB data access

**Step 2:** Application determines the user's role (kb_developer, kb_admin, kb_viewer)

**Step 3:** Application acquires a connection from HikariCP pool

**Step 4:** Within the transaction, application executes `SET LOCAL role = '{role}'`

**Step 5:** Application executes the actual query against kb_entries or pii_mapping

**Step 6:** PostgreSQL RLS policies automatically filter columns/rows based on the SET LOCAL role

**Step 7:** Application receives filtered results and returns to user

**Step 8:** Transaction ends, connection returns to pool with role reset

---

#### STORY 1: Database-Level Access Control via RLS

> As a system administrator, I want database-level access control so that data security is enforced regardless of application bugs

**Requirement Details:**

1. Enable RLS on `kb_entries` table
2. Enable RLS on `pii_mapping` table
3. Create 3 PostgreSQL roles: `kb_developer`, `kb_admin`, `kb_viewer`
4. RLS policies must filter columns (not just rows) based on current role
5. Policies must be enforced even for table owner (FORCE ROW LEVEL SECURITY)

**Acceptance Criteria:**

1. GIVEN RLS is enabled on kb_entries WHEN a query is executed without SET LOCAL role THEN no rows are returned (deny by default)
2. GIVEN role is set to kb_developer WHEN querying kb_entries THEN only public_content and technical_content columns are visible
3. GIVEN role is set to kb_admin WHEN querying kb_entries THEN all columns are visible including decrypted business_rules
4. GIVEN role is set to kb_viewer WHEN querying kb_entries THEN only masked_full column is visible
5. GIVEN RLS is enabled on pii_mapping WHEN role is kb_viewer THEN pii_mapping rows are not accessible

---

#### STORY 2: Developer Role Access

> As a developer, I want to access public and technical content from KB entries so that I can use relevant information for my work

**Requirement Details:**

1. kb_developer role can SELECT from kb_entries: `id`, `issue_key`, `public_content`, `technical_content`, `created_at`, `updated_at`
2. kb_developer role CANNOT access: `business_rules`, `pii_data`, `sensitivity_level` columns with actual values
3. kb_developer role can SELECT from pii_mapping but only sees masked placeholders (not original values)

**Acceptance Criteria:**

1. GIVEN role = kb_developer WHEN SELECT * FROM kb_entries THEN business_rules column returns NULL or is excluded
2. GIVEN role = kb_developer WHEN SELECT * FROM pii_mapping THEN original_value column returns '[MASKED]'
3. GIVEN role = kb_developer WHEN attempting INSERT/UPDATE on kb_entries THEN operation is denied

---

#### STORY 3: Admin Role Full Access

> As a BA/Admin, I want to access all content including decrypted business rules so that I can manage knowledge base entries fully

**Requirement Details:**

1. kb_admin role can SELECT all columns from kb_entries including business_rules
2. kb_admin role can SELECT from pii_mapping with decrypted original_value (requires MTO-33 BR decryption)
3. kb_admin role can INSERT/UPDATE/DELETE on kb_entries
4. kb_admin role access is still audited (MTO-34)

**Acceptance Criteria:**

1. GIVEN role = kb_admin WHEN SELECT * FROM kb_entries THEN all columns are returned with actual values
2. GIVEN role = kb_admin WHEN INSERT INTO kb_entries THEN operation succeeds
3. GIVEN role = kb_admin WHEN accessing pii_mapping THEN original_value is decryptable

---

#### STORY 4: Viewer Role Restricted Access

> As a viewer, I want to see only masked content so that sensitive information is protected from unauthorized access

**Requirement Details:**

1. kb_viewer role can only SELECT `id`, `issue_key`, `masked_full` from kb_entries
2. kb_viewer role CANNOT access pii_mapping table at all
3. kb_viewer role CANNOT perform INSERT/UPDATE/DELETE on any protected table

**Acceptance Criteria:**

1. GIVEN role = kb_viewer WHEN SELECT * FROM kb_entries THEN only id, issue_key, masked_full are returned
2. GIVEN role = kb_viewer WHEN SELECT FROM pii_mapping THEN permission denied error
3. GIVEN role = kb_viewer WHEN attempting any write operation THEN permission denied error

---

#### STORY 5: HikariCP Integration

> As a DevOps engineer, I want RLS to work transparently with HikariCP connection pooling so that performance is not degraded

**Requirement Details:**

1. `SET LOCAL role` is scoped to the current transaction only — does not affect other connections
2. After transaction ends, the connection role resets automatically (SET LOCAL behavior)
3. RlsContextManager interface wraps query execution with proper role setting
4. Connection pool size and performance are not affected by RLS overhead

**Acceptance Criteria:**

1. GIVEN HikariCP pool with 10 connections WHEN 10 concurrent requests with different roles THEN each request sees only its role's data
2. GIVEN a transaction with SET LOCAL role WHEN transaction commits/rollbacks THEN connection role resets to default
3. GIVEN RLS enabled WHEN running performance benchmark THEN query latency increase is < 5ms per query

---

## 3. Dependencies

| Dependency | Type | Related Ticket | Description |
|------------|------|----------------|-------------|
| PostgreSQL 16+ | Infrastructure | — | RLS support required |
| HikariCP | System | — | Connection pool must support SET LOCAL |
| kb_entries table | System | MTO-26 | Table must exist with required columns |
| pii_mapping table | System | MTO-26 | Table must exist |
| MTO-32 | Feature | MTO-32 | PII access control builds on RLS |
| MTO-33 | Feature | MTO-33 | BR encryption uses RLS for access control |
| MTO-34 | Feature | MTO-34 | Audit log integrates with RLS role context |

---

## 4. Stakeholders

| Role | Name / Team | Responsibility |
|------|-------------|----------------|
| Product Owner | Duc Nguyen | Approve requirements, UAT |
| Developer | Dev Team | Implement RLS policies and integration |
| DBA | Dev Team | Review SQL migrations |
| QA | QA Team | Test RLS enforcement |

---

## 5. Risks and Assumptions

### 5.1 Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| RLS performance overhead on large tables | Medium | Low | Benchmark with production-like data volumes |
| Connection pool role leakage between requests | High | Low | Use SET LOCAL (transaction-scoped), add integration tests |
| Role not set before query execution | High | Medium | RlsContextManager enforces role setting in wrapper |
| PostgreSQL version incompatibility | High | Low | Require PostgreSQL 16+ in deployment guide |

### 5.2 Assumptions

- PostgreSQL 16+ is available in all environments (dev, staging, production)
- HikariCP supports SET LOCAL within transactions without issues
- Application already has user role information available at request time
- Column-level security can be achieved via views + RLS combination

---

## 6. Non-Functional Requirements

| Category | Requirement | Details |
|----------|-------------|---------|
| Performance | Query latency overhead < 5ms | RLS policy evaluation must not significantly impact query performance |
| Security | Deny-by-default | If no role is set, no data is accessible |
| Security | No role escalation | Application cannot bypass RLS without superuser |
| Reliability | Connection pool safety | Role must reset after each transaction |
| Scalability | Support 100+ concurrent connections | RLS must work with high connection pool sizes |

---

## 7. Related Tickets

| Ticket Key | Summary | Status | Type | Relationship |
|------------|---------|--------|------|--------------|
| MTO-31 | KB Refinery — PostgreSQL RLS Policies | Docs Review | Story | Main ticket |
| MTO-26 | KB Refinery — PII Mapping Table | Done | Story | Prerequisite (table exists) |
| MTO-32 | KB Refinery — PII Mapping Encrypted Table | To Do | Story | Depends on MTO-31 |
| MTO-33 | KB Refinery — BR Encryption & Access Control | To Do | Story | Depends on MTO-31 |
| MTO-34 | KB Refinery — Audit Log & Response Shaping | To Do | Story | Depends on MTO-31 |

---

## 8. Appendix

### Glossary

| Term | Definition |
|------|------------|
| RLS | Row-Level Security — PostgreSQL feature that restricts row access per user/role |
| SET LOCAL | PostgreSQL command that sets a parameter for the current transaction only |
| HikariCP | High-performance JDBC connection pool for Java/Kotlin |
| kb_entries | Main knowledge base table storing content with sensitivity levels |
| pii_mapping | Table mapping PII placeholders to original values |

### SQL Migration Reference

Migration file: `V8__create_rls_policies.sql`

Key operations:
1. CREATE ROLE kb_developer, kb_admin, kb_viewer
2. ALTER TABLE kb_entries ENABLE ROW LEVEL SECURITY
3. ALTER TABLE kb_entries FORCE ROW LEVEL SECURITY
4. CREATE POLICY for each role on each table
5. GRANT appropriate permissions to each role

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Business Flow | [business-flow.png](diagrams/business-flow.png) | [business-flow.drawio](diagrams/business-flow.drawio) |
| 2 | Use Case Diagram | [use-case.png](diagrams/use-case.png) | [use-case.drawio](diagrams/use-case.drawio) |
