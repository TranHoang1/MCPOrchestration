# Software Test Cases (STC)

## MCPOrchestration — MTO-31: KB Refinery — PostgreSQL RLS Policies

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-31 |
| Version | 1.0 |
| Date | 2026-05-08 |
| Author | QA Agent |
| Related STP | STP-v1-MTO-31.docx |

---

## 1. Property-Based Tests (PBT)

### PBT-01: KbRole Enum Exhaustiveness

| Field | Value |
|-------|-------|
| ID | PBT-01 |
| Feature | KbRole Enum |
| Technique | Exhaustive enum check |
| Priority | High |

**Property:** Every KbRole enum value maps to a valid PostgreSQL role name matching pattern `kb_[a-z_]+`

```kotlin
checkAll(KbRole.entries.toList()) { role ->
    role.pgRoleName shouldMatch Regex("kb_[a-z_]+")
    role.pgRoleName.length shouldBeInRange 5..30
}
```

### PBT-02: RoleContextService Always Returns Valid Role

| Field | Value |
|-------|-------|
| ID | PBT-02 |
| Feature | RoleContextService |
| Technique | Arbitrary string input |
| Priority | High |

**Property:** For any arbitrary string input, resolveRole() always returns a valid KbRole (never throws)

```kotlin
checkAll(Arb.string(0..100)) { input ->
    val role = roleContextService.resolveRole(input)
    role shouldBeIn KbRole.entries
}
```

### PBT-03: RlsConfig Serialization Roundtrip

| Field | Value |
|-------|-------|
| ID | PBT-03 |
| Feature | RlsConfig |
| Technique | Serialization roundtrip |
| Priority | Medium |

**Property:** RlsConfig serializes to JSON and deserializes back to identical object

---

## 2. Unit Tests (UT)

### UT-01: KbRole.fromString — Valid Input

| Field | Value |
|-------|-------|
| ID | UT-01 |
| Feature | KbRole |
| Precondition | None |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call KbRole.fromString("DEVELOPER") | Returns KbRole.DEVELOPER |
| 2 | Call KbRole.fromString("developer") | Returns KbRole.DEVELOPER (case-insensitive) |
| 3 | Call KbRole.fromString("BA_ADMIN") | Returns KbRole.BA_ADMIN |
| 4 | Call KbRole.fromString("LOW_PRIVILEGE") | Returns KbRole.LOW_PRIVILEGE |

### UT-02: KbRole.fromString — Invalid Input

| Field | Value |
|-------|-------|
| ID | UT-02 |
| Feature | KbRole |
| Precondition | None |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call KbRole.fromString("INVALID") | Throws IllegalArgumentException |
| 2 | Call KbRole.fromString("") | Throws IllegalArgumentException |
| 3 | Call KbRole.fromString("superuser") | Throws IllegalArgumentException |

### UT-03: KbRole.pgRoleName Mapping

| Field | Value |
|-------|-------|
| ID | UT-03 |
| Feature | KbRole |
| Precondition | None |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | KbRole.DEVELOPER.pgRoleName | "kb_developer" |
| 2 | KbRole.BA_ADMIN.pgRoleName | "kb_admin" |
| 3 | KbRole.LOW_PRIVILEGE.pgRoleName | "kb_viewer" |

### UT-04: RoleContextService — Mapped Role

| Field | Value |
|-------|-------|
| ID | UT-04 |
| Feature | RoleContextService |
| Precondition | RlsConfig with default mappings |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | resolveRole("ROLE_DEVELOPER") | KbRole.DEVELOPER |
| 2 | resolveRole("ROLE_BA") | KbRole.BA_ADMIN |
| 3 | resolveRole("ROLE_ADMIN") | KbRole.BA_ADMIN |
| 4 | resolveRole("ROLE_USER") | KbRole.LOW_PRIVILEGE |

### UT-05: RoleContextService — Unmapped Role (Default)

| Field | Value |
|-------|-------|
| ID | UT-05 |
| Feature | RoleContextService |
| Precondition | RlsConfig with defaultRole = LOW_PRIVILEGE |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | resolveRole("UNKNOWN_ROLE") | KbRole.LOW_PRIVILEGE (default) |
| 2 | resolveRole("") | KbRole.LOW_PRIVILEGE (default) |

### UT-06: RoleContextService — Custom Config

| Field | Value |
|-------|-------|
| ID | UT-06 |
| Feature | RoleContextService |
| Precondition | Custom RlsConfig with different mappings |
| Priority | Medium |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create config with custom mapping "CUSTOM" → BA_ADMIN | — |
| 2 | resolveRole("CUSTOM") | KbRole.BA_ADMIN |

### UT-07: RoleContextService — getDefaultRole

| Field | Value |
|-------|-------|
| ID | UT-07 |
| Feature | RoleContextService |
| Precondition | RlsConfig with defaultRole = LOW_PRIVILEGE |
| Priority | Medium |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | getDefaultRole() | KbRole.LOW_PRIVILEGE |

### UT-08: RlsConfig — Default Values

| Field | Value |
|-------|-------|
| ID | UT-08 |
| Feature | RlsConfig |
| Precondition | None |
| Priority | Medium |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create RlsConfig() with no args | enabled = true |
| 2 | Check defaultRole | KbRole.LOW_PRIVILEGE |
| 3 | Check forceRls | true |
| 4 | Check roleMappings | Contains 4 default mappings |

### UT-09: RlsConfig — Custom Values

| Field | Value |
|-------|-------|
| ID | UT-09 |
| Feature | RlsConfig |
| Precondition | None |
| Priority | Medium |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create RlsConfig(enabled=false, defaultRole=DEVELOPER) | enabled = false, defaultRole = DEVELOPER |

### UT-10: RlsConnectionWrapper — executeWithRole Sets Role

| Field | Value |
|-------|-------|
| ID | UT-10 |
| Feature | RlsConnectionWrapper |
| Precondition | Mocked DataSource |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call executeWithRole(DEVELOPER) { ... } | SET LOCAL ROLE 'kb_developer' executed |
| 2 | Verify statement execution order | SET LOCAL before block, commit after |

### UT-11: RlsConnectionWrapper — Rollback on Exception

| Field | Value |
|-------|-------|
| ID | UT-11 |
| Feature | RlsConnectionWrapper |
| Precondition | Mocked DataSource |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call executeWithRole(DEVELOPER) { throw RuntimeException() } | Connection.rollback() called |
| 2 | Verify connection closed | connection.close() called in finally |

### UT-12: RlsConnectionWrapper — AutoCommit Reset

| Field | Value |
|-------|-------|
| ID | UT-12 |
| Feature | RlsConnectionWrapper |
| Precondition | Mocked DataSource |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call executeWithRole(any role) | autoCommit set to false before, true after |
| 2 | Even on exception | autoCommit reset to true in finally |

---

## 3. Integration Tests (IT)

### IT-01: Flyway Migration V8 — Role Creation

| Field | Value |
|-------|-------|
| ID | IT-01 |
| Feature | Flyway Migrations |
| Precondition | Fresh PostgreSQL 16 container |
| Technique | Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run Flyway migrate | V8 migration succeeds |
| 2 | Query pg_roles for kb_developer | Role exists, NOLOGIN |
| 3 | Query pg_roles for kb_admin | Role exists, NOLOGIN |
| 4 | Query pg_roles for kb_viewer | Role exists, NOLOGIN |

### IT-02: Flyway Migration V9 — RLS on kb_entries

| Field | Value |
|-------|-------|
| ID | IT-02 |
| Feature | Flyway Migrations |
| Precondition | V8 migration complete, kb_entries table exists |
| Technique | Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run Flyway migrate | V9 migration succeeds |
| 2 | Check RLS enabled on kb_entries | relrowsecurity = true |
| 3 | Check FORCE RLS on kb_entries | relforcerowsecurity = true |
| 4 | Query pg_policies for kb_entries | 3 policies exist |

### IT-03: Flyway Migration V10 — RLS on pii_mapping

| Field | Value |
|-------|-------|
| ID | IT-03 |
| Feature | Flyway Migrations |
| Precondition | V8 migration complete, pii_mapping table exists |
| Technique | Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run Flyway migrate | V10 migration succeeds |
| 2 | Check RLS enabled on pii_mapping | relrowsecurity = true |
| 3 | Check FORCE RLS on pii_mapping | relforcerowsecurity = true |
| 4 | Query pg_policies for pii_mapping | 1 policy exists (admin only) |

### IT-04: Flyway Migration Idempotency

| Field | Value |
|-------|-------|
| ID | IT-04 |
| Feature | Flyway Migrations |
| Precondition | All migrations already applied |
| Technique | Testcontainers |
| Priority | Medium |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run Flyway migrate again | No errors, no new migrations applied |

### IT-05: RLS — Developer SELECT kb_entries

| Field | Value |
|-------|-------|
| ID | IT-05 |
| Feature | RLS Policy — kb_entries |
| Precondition | Migrations applied, test data inserted |
| Technique | Testcontainers + SET LOCAL |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert test row into kb_entries (as superuser) | Row inserted |
| 2 | SET LOCAL ROLE 'kb_developer' | Role set |
| 3 | SELECT * FROM kb_entries_developer_view | Returns: id, issue_key, public_content, technical_content, masked_full, created_at, updated_at |
| 4 | Verify business_rules NOT in result | Column not present in view |

### IT-06: RLS — Admin SELECT kb_entries

| Field | Value |
|-------|-------|
| ID | IT-06 |
| Feature | RLS Policy — kb_entries |
| Precondition | Migrations applied, test data inserted |
| Technique | Testcontainers + SET LOCAL |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | SET LOCAL ROLE 'kb_admin' | Role set |
| 2 | SELECT * FROM kb_entries_admin_view | Returns ALL columns including business_rules |
| 3 | Verify business_rules has actual value | Not NULL, not masked |

### IT-07: RLS — Viewer SELECT kb_entries

| Field | Value |
|-------|-------|
| ID | IT-07 |
| Feature | RLS Policy — kb_entries |
| Precondition | Migrations applied, test data inserted |
| Technique | Testcontainers + SET LOCAL |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | SET LOCAL ROLE 'kb_viewer' | Role set |
| 2 | SELECT * FROM kb_entries_viewer_view | Returns ONLY: id, issue_key, masked_full |
| 3 | Verify no other columns | Only 3 columns in result set |

### IT-08: RLS — Deny-by-Default (No Role Set)

| Field | Value |
|-------|-------|
| ID | IT-08 |
| Feature | RLS Policy — kb_entries |
| Precondition | Migrations applied, test data inserted, NO SET LOCAL |
| Technique | Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Connect as app_user (no SET LOCAL) | Connected |
| 2 | SELECT * FROM kb_entries | Empty result set (RLS denies) |

### IT-09: RLS — Developer NO ACCESS to pii_mapping

| Field | Value |
|-------|-------|
| ID | IT-09 |
| Feature | RLS Policy — pii_mapping |
| Precondition | Migrations applied, pii_mapping has data |
| Technique | Testcontainers + SET LOCAL |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | SET LOCAL ROLE 'kb_developer' | Role set |
| 2 | SELECT * FROM pii_mapping | Empty result (no policy grants access) |

### IT-10: RLS — Admin ACCESS to pii_mapping

| Field | Value |
|-------|-------|
| ID | IT-10 |
| Feature | RLS Policy — pii_mapping |
| Precondition | Migrations applied, pii_mapping has data |
| Technique | Testcontainers + SET LOCAL |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | SET LOCAL ROLE 'kb_admin' | Role set |
| 2 | SELECT * FROM pii_mapping | Returns all rows with original_value visible |

### IT-11: RlsConnectionWrapper — SET LOCAL Executed

| Field | Value |
|-------|-------|
| ID | IT-11 |
| Feature | RlsConnectionWrapper |
| Precondition | Real PostgreSQL container, HikariCP DataSource |
| Technique | Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call executeWithRole(DEVELOPER) { query current_user } | Returns 'kb_developer' |
| 2 | Call executeWithRole(BA_ADMIN) { query current_user } | Returns 'kb_admin' |

### IT-12: RlsConnectionWrapper — Transaction Rollback

| Field | Value |
|-------|-------|
| ID | IT-12 |
| Feature | RlsConnectionWrapper |
| Precondition | Real PostgreSQL container |
| Technique | Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call executeWithRole(BA_ADMIN) { INSERT + throw } | Exception propagated |
| 2 | Verify INSERT was rolled back | Row does not exist |

### IT-13: RlsConnectionWrapper — Role Reset After Transaction

| Field | Value |
|-------|-------|
| ID | IT-13 |
| Feature | RlsConnectionWrapper |
| Precondition | Real PostgreSQL container |
| Technique | Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call executeWithRole(BA_ADMIN) { ... } | Succeeds |
| 2 | Get same connection from pool | Connection available |
| 3 | Query current_user without SET LOCAL | Returns app_user (not kb_admin) |

### IT-14: Developer Cannot INSERT into kb_entries

| Field | Value |
|-------|-------|
| ID | IT-14 |
| Feature | RLS Policy — kb_entries |
| Precondition | Migrations applied |
| Technique | Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | SET LOCAL ROLE 'kb_developer' | Role set |
| 2 | INSERT INTO kb_entries (...) VALUES (...) | Permission denied error |

### IT-15: Viewer Cannot Access pii_mapping

| Field | Value |
|-------|-------|
| ID | IT-15 |
| Feature | RLS Policy — pii_mapping |
| Precondition | Migrations applied |
| Technique | Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | SET LOCAL ROLE 'kb_viewer' | Role set |
| 2 | SELECT * FROM pii_mapping | Empty result (no policy) |

---

## 4. E2E-API Tests

### E2E-01: Full Flow — Deny-by-Default

| Field | Value |
|-------|-------|
| ID | E2E-01 |
| Feature | End-to-end RLS enforcement |
| Precondition | Full application stack with Testcontainers PostgreSQL |
| Technique | Kotest + Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start application with Testcontainers PostgreSQL | App starts, migrations run |
| 2 | Call KbQueryService without user context | SecurityException thrown |
| 3 | Verify no data returned | Empty/error response |

### E2E-02: Full Flow — Developer Access

| Field | Value |
|-------|-------|
| ID | E2E-02 |
| Feature | End-to-end developer access |
| Precondition | Full stack, test data seeded |
| Technique | Kotest + Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call KbQueryService with userIdentity="ROLE_DEVELOPER" | Role resolved to DEVELOPER |
| 2 | Query kb_entries | Returns public_content, technical_content |
| 3 | Verify business_rules NOT in response | Field is null/absent |

### E2E-03: Full Flow — Developer Denied pii_mapping

| Field | Value |
|-------|-------|
| ID | E2E-03 |
| Feature | End-to-end developer PII denial |
| Precondition | Full stack, pii_mapping has data |
| Technique | Kotest + Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call PiiService with userIdentity="ROLE_DEVELOPER" | Role resolved to DEVELOPER |
| 2 | Query pii_mapping | Empty result (access denied by RLS) |

### E2E-04: Full Flow — Admin Full Access kb_entries

| Field | Value |
|-------|-------|
| ID | E2E-04 |
| Feature | End-to-end admin access |
| Precondition | Full stack, test data seeded |
| Technique | Kotest + Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call KbQueryService with userIdentity="ROLE_ADMIN" | Role resolved to BA_ADMIN |
| 2 | Query kb_entries | Returns ALL columns |
| 3 | Verify business_rules has value | Actual content returned |

### E2E-05: Full Flow — Admin Access pii_mapping

| Field | Value |
|-------|-------|
| ID | E2E-05 |
| Feature | End-to-end admin PII access |
| Precondition | Full stack, pii_mapping has data |
| Technique | Kotest + Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call PiiService with userIdentity="ROLE_ADMIN" | Role resolved to BA_ADMIN |
| 2 | Query pii_mapping | Returns all rows with original_value |

### E2E-06: Full Flow — Viewer Masked Only

| Field | Value |
|-------|-------|
| ID | E2E-06 |
| Feature | End-to-end viewer access |
| Precondition | Full stack, test data seeded |
| Technique | Kotest + Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call KbQueryService with userIdentity="ROLE_USER" | Role resolved to LOW_PRIVILEGE |
| 2 | Query kb_entries | Returns ONLY id, issue_key, masked_full |
| 3 | Verify no other columns | Only 3 fields in response |

### E2E-07: Full Flow — Viewer Denied pii_mapping

| Field | Value |
|-------|-------|
| ID | E2E-07 |
| Feature | End-to-end viewer PII denial |
| Precondition | Full stack |
| Technique | Kotest + Testcontainers |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call PiiService with userIdentity="ROLE_USER" | Role resolved to LOW_PRIVILEGE |
| 2 | Query pii_mapping | Empty result (access denied) |

### E2E-08: Concurrent Access — Role Isolation

| Field | Value |
|-------|-------|
| ID | E2E-08 |
| Feature | Concurrent role isolation |
| Precondition | Full stack, test data seeded |
| Technique | Kotest + Testcontainers + coroutines |
| Priority | High |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Launch 10 concurrent coroutines: 3 developer, 4 admin, 3 viewer | All launched |
| 2 | Each queries kb_entries simultaneously | No blocking |
| 3 | Verify developer results | Only public_content, technical_content |
| 4 | Verify admin results | All columns |
| 5 | Verify viewer results | Only masked_full |
| 6 | No cross-contamination | Each role sees only its data |

### E2E-09: Performance — RLS Overhead < 5ms

| Field | Value |
|-------|-------|
| ID | E2E-09 |
| Feature | Performance |
| Precondition | Full stack, 1000 rows in kb_entries |
| Technique | Kotest + Testcontainers + timing |
| Priority | Medium |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Measure query time WITHOUT RLS (direct superuser) | Baseline time recorded |
| 2 | Measure query time WITH RLS (via executeWithRole) | RLS time recorded |
| 3 | Calculate overhead | Overhead < 5ms |

### E2E-10: Admin Write — INSERT kb_entries

| Field | Value |
|-------|-------|
| ID | E2E-10 |
| Feature | Admin write access |
| Precondition | Full stack |
| Technique | Kotest + Testcontainers |
| Priority | Medium |

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call KbWriteService with userIdentity="ROLE_ADMIN" | Role resolved to BA_ADMIN |
| 2 | INSERT new kb_entry | Success |
| 3 | Verify row exists | Row persisted |

---

## 5. Test Data

### 5.1 Seed Data for kb_entries

| id | issue_key | public_content | technical_content | business_rules | masked_full | sensitivity_level |
|----|-----------|---------------|-------------------|----------------|-------------|-------------------|
| uuid-1 | TEST-1 | "Public info" | "Technical details" | "Secret BR" | "[MASKED] content" | confidential |
| uuid-2 | TEST-2 | "Another public" | "More tech" | "Another BR" | "[MASKED] more" | internal |

### 5.2 Seed Data for pii_mapping

| id | kb_entry_id | placeholder | original_value | pii_type |
|----|-------------|-------------|----------------|----------|
| uuid-p1 | uuid-1 | [EMAIL_1] | john@example.com | email |
| uuid-p2 | uuid-1 | [PHONE_1] | +1-555-0123 | phone |
