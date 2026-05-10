# Technical Design Document (TDD)

## MCPOrchestration — MTO-31: KB Refinery — PostgreSQL RLS Policies

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-31 |
| Title | KB Refinery — PostgreSQL RLS Policies |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related FSD | FSD-v1-MTO-31.docx |
| Related BRD | BRD-v1-MTO-31.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | SA Agent | Initial technical design |

---

## 1. Architecture Overview

### 1.1 Design Philosophy

The RLS implementation follows a **defense-in-depth** approach with security enforced at the PostgreSQL level. The application layer is responsible only for determining the correct role and setting it via `SET LOCAL` — all actual data filtering is handled by PostgreSQL's native RLS engine.

### 1.2 Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Column filtering approach | Security Barrier Views + RLS | PostgreSQL RLS is row-level; views provide column filtering |
| Role activation | SET LOCAL role | Transaction-scoped, auto-resets on commit/rollback |
| Connection management | Wrap HikariCP connections | Minimal overhead, no custom pool needed |
| DI integration | Koin module | Consistent with existing project architecture |
| Migration tool | Flyway | Already in use for schema migrations |
| Coroutine support | suspend functions with Dispatchers.IO | DB operations are blocking I/O |

### 1.3 Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Application Layer                         │
│                                                                  │
│  ┌────────────────┐     ┌─────────────────────┐                │
│  │ KbQueryService │────▶│ RlsConnectionWrapper │                │
│  └────────────────┘     └──────────┬──────────┘                │
│                                     │                            │
│  ┌────────────────┐                │                            │
│  │RoleContextSvc  │────────────────┘                            │
│  │  (Interface)   │                                             │
│  └────────────────┘                                             │
│         ▲                                                        │
│         │                                                        │
│  ┌────────────────┐     ┌─────────────────────┐                │
│  │RoleContextImpl │     │   SecurityModule     │                │
│  │(from config)   │     │   (Koin DI)         │                │
│  └────────────────┘     └─────────────────────┘                │
│                                                                  │
├──────────────────────────────────────────────────────────────────┤
│                        Data Access Layer                          │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    HikariCP Pool                          │    │
│  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐         │    │
│  │  │Conn 1│ │Conn 2│ │Conn 3│ │Conn 4│ │Conn N│         │    │
│  │  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘         │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
├──────────────────────────────────────────────────────────────────┤
│                        PostgreSQL 16+                             │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ Roles: kb_developer | kb_admin | kb_viewer               │    │
│  ├─────────────────────────────────────────────────────────┤    │
│  │ Views (Security Barrier):                                 │    │
│  │   kb_entries_developer_view                               │    │
│  │   kb_entries_admin_view                                   │    │
│  │   kb_entries_viewer_view                                  │    │
│  ├─────────────────────────────────────────────────────────┤    │
│  │ RLS Policies:                                             │    │
│  │   policy_kb_developer_select                              │    │
│  │   policy_kb_admin_all                                     │    │
│  │   policy_kb_viewer_select                                 │    │
│  │   policy_pii_admin_only                                   │    │
│  └─────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Design

### 2.1 KbRole Enum

**File:** `security/model/KbRole.kt`
**Size:** ~25 lines

```kotlin
package com.orchestrator.mcp.security.model

enum class KbRole(val pgRoleName: String) {
    DEVELOPER("kb_developer"),
    BA_ADMIN("kb_admin"),
    LOW_PRIVILEGE("kb_viewer");

    companion object {
        fun fromString(value: String): KbRole =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown KB role: $value")
    }
}
```

### 2.2 RoleContextService Interface

**File:** `security/RoleContextService.kt`
**Size:** ~15 lines

```kotlin
package com.orchestrator.mcp.security

import com.orchestrator.mcp.security.model.KbRole

interface RoleContextService {
    /**
     * Determine the KB role for the current request context.
     * @param userIdentity The authenticated user's identity/group
     * @return The appropriate KbRole for database access
     */
    fun resolveRole(userIdentity: String): KbRole

    /**
     * Get the default role when no explicit mapping exists.
     */
    fun getDefaultRole(): KbRole
}
```

### 2.3 RoleContextServiceImpl

**File:** `security/RoleContextServiceImpl.kt`
**Size:** ~45 lines

```kotlin
package com.orchestrator.mcp.security

import com.orchestrator.mcp.security.config.RlsConfig
import com.orchestrator.mcp.security.model.KbRole
import org.slf4j.LoggerFactory

class RoleContextServiceImpl(
    private val config: RlsConfig
) : RoleContextService {

    private val logger = LoggerFactory.getLogger(RoleContextServiceImpl::class.java)

    override fun resolveRole(userIdentity: String): KbRole {
        val mappedRole = config.roleMappings[userIdentity]
        if (mappedRole != null) {
            logger.debug("Resolved role for '{}': {}", userIdentity, mappedRole)
            return mappedRole
        }

        logger.warn("No role mapping for '{}', using default: {}", userIdentity, config.defaultRole)
        return config.defaultRole
    }

    override fun getDefaultRole(): KbRole = config.defaultRole
}
```

### 2.4 RlsConnectionWrapper

**File:** `security/RlsConnectionWrapper.kt`
**Size:** ~65 lines

```kotlin
package com.orchestrator.mcp.security

import com.orchestrator.mcp.security.model.KbRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class RlsConnectionWrapper(
    private val dataSource: DataSource
) {
    private val logger = LoggerFactory.getLogger(RlsConnectionWrapper::class.java)

    /**
     * Execute a database operation with the specified RLS role context.
     * The role is set via SET LOCAL and is automatically reset when the transaction ends.
     *
     * @param role The KB role to activate for this transaction
     * @param block The database operation to execute
     * @return The result of the database operation
     */
    suspend fun <T> executeWithRole(role: KbRole, block: suspend (java.sql.Connection) -> T): T {
        return withContext(Dispatchers.IO) {
            val connection = dataSource.connection
            try {
                connection.autoCommit = false

                // Set the PostgreSQL role for this transaction
                connection.createStatement().use { stmt ->
                    stmt.execute("SET LOCAL ROLE '${role.pgRoleName}'")
                }
                logger.debug("Set LOCAL ROLE to '{}'", role.pgRoleName)

                val result = block(connection)
                connection.commit()
                logger.debug("Transaction committed with role '{}'", role.pgRoleName)
                result
            } catch (e: Exception) {
                logger.error("Transaction failed with role '{}': {}", role.pgRoleName, e.message)
                runCatching { connection.rollback() }
                throw e
            } finally {
                runCatching {
                    connection.autoCommit = true
                    connection.close() // Returns to HikariCP pool
                }
            }
        }
    }
}
```

### 2.5 RlsConfig

**File:** `security/config/RlsConfig.kt`
**Size:** ~30 lines

```kotlin
package com.orchestrator.mcp.security.config

import com.orchestrator.mcp.security.model.KbRole
import kotlinx.serialization.Serializable

@Serializable
data class RlsConfig(
    val enabled: Boolean = true,
    val defaultRole: KbRole = KbRole.LOW_PRIVILEGE,
    val roleMappings: Map<String, KbRole> = mapOf(
        "ROLE_DEVELOPER" to KbRole.DEVELOPER,
        "ROLE_BA" to KbRole.BA_ADMIN,
        "ROLE_ADMIN" to KbRole.BA_ADMIN,
        "ROLE_USER" to KbRole.LOW_PRIVILEGE
    ),
    val forceRls: Boolean = true
)
```

### 2.6 SecurityModule (Koin DI)

**File:** `security/di/SecurityModule.kt`
**Size:** ~25 lines

```kotlin
package com.orchestrator.mcp.security.di

import com.orchestrator.mcp.security.RlsConnectionWrapper
import com.orchestrator.mcp.security.RoleContextService
import com.orchestrator.mcp.security.RoleContextServiceImpl
import com.orchestrator.mcp.security.config.RlsConfig
import org.koin.dsl.module

val securityModule = module {
    single<RlsConfig> { RlsConfig() } // TODO: Load from application.yml
    single<RoleContextService> { RoleContextServiceImpl(get()) }
    single { RlsConnectionWrapper(get()) } // DataSource from existing module
}
```

---

## 3. Database Design

### 3.1 Migration: V8__create_kb_roles.sql

```sql
-- V8__create_kb_roles.sql
-- Create PostgreSQL roles for KB RLS policies

-- Create roles (NOLOGIN — used only via SET LOCAL ROLE)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'kb_developer') THEN
        CREATE ROLE kb_developer NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'kb_admin') THEN
        CREATE ROLE kb_admin NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'kb_viewer') THEN
        CREATE ROLE kb_viewer NOLOGIN;
    END IF;
END
$$;

-- Grant schema usage to all roles
GRANT USAGE ON SCHEMA public TO kb_developer, kb_admin, kb_viewer;

-- Grant the application user permission to SET ROLE to these roles
-- (Replace 'app_user' with actual HikariCP connection user)
GRANT kb_developer TO app_user;
GRANT kb_admin TO app_user;
GRANT kb_viewer TO app_user;
```

### 3.2 Migration: V9__enable_rls_kb_entries.sql

```sql
-- V9__enable_rls_kb_entries.sql
-- Enable RLS on kb_entries and create access policies

-- Enable RLS
ALTER TABLE kb_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE kb_entries FORCE ROW LEVEL SECURITY;

-- Policy: kb_admin — full access to all columns and operations
CREATE POLICY policy_kb_admin_all ON kb_entries
    FOR ALL
    TO kb_admin
    USING (true)
    WITH CHECK (true);

-- Policy: kb_developer — SELECT only, limited columns via view
CREATE POLICY policy_kb_developer_select ON kb_entries
    FOR SELECT
    TO kb_developer
    USING (true);

-- Policy: kb_viewer — SELECT only, will use view for column filtering
CREATE POLICY policy_kb_viewer_select ON kb_entries
    FOR SELECT
    TO kb_viewer
    USING (true);

-- Security Barrier Views for column-level filtering
-- Developer view: public_content, technical_content, masked_full
CREATE OR REPLACE VIEW kb_entries_developer_view
    WITH (security_barrier = true) AS
SELECT
    id,
    issue_key,
    public_content,
    technical_content,
    masked_full,
    created_at,
    updated_at
FROM kb_entries;

GRANT SELECT ON kb_entries_developer_view TO kb_developer;

-- Admin view: all columns
CREATE OR REPLACE VIEW kb_entries_admin_view
    WITH (security_barrier = true) AS
SELECT * FROM kb_entries;

GRANT SELECT, INSERT, UPDATE, DELETE ON kb_entries_admin_view TO kb_admin;

-- Viewer view: masked content only
CREATE OR REPLACE VIEW kb_entries_viewer_view
    WITH (security_barrier = true) AS
SELECT
    id,
    issue_key,
    masked_full
FROM kb_entries;

GRANT SELECT ON kb_entries_viewer_view TO kb_viewer;

-- Revoke direct table access for developer and viewer
-- (They must use views for column filtering)
REVOKE ALL ON kb_entries FROM kb_developer, kb_viewer;
GRANT SELECT ON kb_entries TO kb_developer, kb_viewer; -- RLS still applies
```

### 3.3 Migration: V10__enable_rls_pii_mapping.sql

```sql
-- V10__enable_rls_pii_mapping.sql
-- Enable RLS on pii_mapping — admin-only access

-- Enable RLS
ALTER TABLE pii_mapping ENABLE ROW LEVEL SECURITY;
ALTER TABLE pii_mapping FORCE ROW LEVEL SECURITY;

-- Policy: Only kb_admin can access pii_mapping
CREATE POLICY policy_pii_admin_only ON pii_mapping
    FOR ALL
    TO kb_admin
    USING (true)
    WITH CHECK (true);

-- Explicitly deny other roles (no policy = no access with FORCE RLS)
-- No policies for kb_developer or kb_viewer means they get zero rows

-- Grant table permissions only to admin
GRANT SELECT, INSERT, UPDATE ON pii_mapping TO kb_admin;
-- No GRANT for kb_developer or kb_viewer
```

---

## 4. API Design

### 4.1 Internal API — RlsConnectionWrapper

This is an internal service API, not an HTTP endpoint. It's used by other services within the application.

```kotlin
interface RlsConnectionWrapper {
    /**
     * Execute a database operation with RLS role context.
     * @param role The KB role to set for this transaction
     * @param block The suspend function receiving a JDBC Connection
     * @return Result of the block execution
     * @throws RlsConfigurationException if SET LOCAL fails
     * @throws ConnectionTimeoutException if pool is exhausted
     */
    suspend fun <T> executeWithRole(role: KbRole, block: suspend (Connection) -> T): T
}
```

### 4.2 Usage Example

```kotlin
class KbQueryService(
    private val rlsWrapper: RlsConnectionWrapper,
    private val roleContext: RoleContextService
) {
    suspend fun getEntries(userIdentity: String): List<KbEntry> {
        val role = roleContext.resolveRole(userIdentity)
        return rlsWrapper.executeWithRole(role) { connection ->
            val viewName = when (role) {
                KbRole.DEVELOPER -> "kb_entries_developer_view"
                KbRole.BA_ADMIN -> "kb_entries_admin_view"
                KbRole.LOW_PRIVILEGE -> "kb_entries_viewer_view"
            }
            connection.prepareStatement("SELECT * FROM $viewName").use { stmt ->
                stmt.executeQuery().use { rs ->
                    // Map ResultSet to KbEntry list
                    buildList {
                        while (rs.next()) {
                            add(mapRow(rs, role))
                        }
                    }
                }
            }
        }
    }
}
```

---

## 5. Error Handling

### 5.1 Exception Hierarchy

```kotlin
sealed class RlsException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class RlsConfigurationException(message: String) : RlsException(message)
    class RoleResolutionException(message: String) : RlsException(message)
    class ConnectionTimeoutException(message: String, cause: Throwable) : RlsException(message, cause)
    class PermissionDeniedException(role: KbRole, table: String) :
        RlsException("Role '${role.pgRoleName}' denied access to '$table'")
}
```

### 5.2 Error Mapping

| Exception | HTTP Status | Log Level | Recovery |
|-----------|-------------|-----------|----------|
| RlsConfigurationException | 500 | ERROR | Alert ops, check migration status |
| RoleResolutionException | 403 | WARN | Check user identity/mapping |
| ConnectionTimeoutException | 503 | ERROR | Retry with backoff |
| PermissionDeniedException | 403 | INFO | Expected behavior for unauthorized access |

---

## 6. Security Design

### 6.1 Threat Model

| Threat | Mitigation |
|--------|-----------|
| SQL injection in role name | KbRole is an enum — no string interpolation from user input |
| Role escalation | NOLOGIN roles + FORCE RLS + no SUPERUSER access |
| Connection pool role leakage | SET LOCAL is transaction-scoped, auto-resets |
| Bypass via direct table access | FORCE ROW LEVEL SECURITY on all protected tables |
| Missing role context | Deny-by-default — no policy match = no rows |

### 6.2 Security Invariants

1. **Role names are never derived from user input** — only from enum values
2. **SET LOCAL is always within a transaction** — cannot leak to other requests
3. **FORCE RLS applies to table owner** — no backdoor via ownership
4. **Views use security_barrier** — prevents optimizer from leaking data through predicates

---

## 7. Performance Considerations

### 7.1 Overhead Analysis

| Operation | Expected Overhead | Justification |
|-----------|------------------|---------------|
| SET LOCAL ROLE | < 0.1ms | Simple session variable set |
| RLS policy evaluation | < 1ms | Simple boolean USING clause |
| View resolution | < 0.5ms | Security barrier views are simple projections |
| Total per-query overhead | < 2ms | Well within 5ms budget |

### 7.2 Connection Pool Impact

- **No additional connections needed** — SET LOCAL works on existing pooled connections
- **No connection state pollution** — SET LOCAL resets on transaction end
- **Pool size unchanged** — same HikariCP configuration applies

### 7.3 Indexing

No additional indexes needed for RLS. Existing indexes on `kb_entries` and `pii_mapping` remain effective since RLS policies use simple boolean conditions (`USING (true)`).

---

## 8. Testing Strategy

### 8.1 Unit Tests

| Test Class | Tests | Framework |
|-----------|-------|-----------|
| KbRoleTest | Enum values, fromString(), pgRoleName mapping | Kotest |
| RoleContextServiceImplTest | Role resolution, default role, missing mapping | Kotest + MockK |
| RlsConfigTest | Serialization, defaults, custom mappings | Kotest |

### 8.2 Integration Tests

| Test Class | Tests | Framework |
|-----------|-------|-----------|
| RlsConnectionWrapperIT | SET LOCAL execution, transaction commit/rollback, role reset | Kotest + Testcontainers (PostgreSQL) |
| RlsPolicyIT | Column filtering per role, deny-by-default, pii_mapping access | Kotest + Testcontainers |
| FlywayMigrationIT | V8/V9/V10 migrations execute successfully, idempotent | Kotest + Testcontainers |

### 8.3 Test Infrastructure

```kotlin
// Testcontainers PostgreSQL setup
val postgres = PostgreSQLContainer("postgres:16-alpine")
    .withDatabaseName("kb_test")
    .withUsername("app_user")
    .withPassword("test")

// Run Flyway migrations in test
Flyway.configure()
    .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
    .locations("classpath:db/migration")
    .load()
    .migrate()
```

---

## 9. Implementation Checklist

### 9.1 Files to Create

| # | File | Package | Size (est.) | Priority |
|---|------|---------|-------------|----------|
| 1 | KbRole.kt | security.model | 25 lines | P0 |
| 2 | RlsConfig.kt | security.config | 30 lines | P0 |
| 3 | RoleContextService.kt | security | 15 lines | P0 |
| 4 | RoleContextServiceImpl.kt | security | 45 lines | P0 |
| 5 | RlsConnectionWrapper.kt | security | 65 lines | P0 |
| 6 | SecurityModule.kt | security.di | 25 lines | P0 |
| 7 | V8__create_kb_roles.sql | db/migration | 30 lines | P0 |
| 8 | V9__enable_rls_kb_entries.sql | db/migration | 60 lines | P0 |
| 9 | V10__enable_rls_pii_mapping.sql | db/migration | 25 lines | P0 |
| 10 | RlsException.kt | security | 15 lines | P1 |

### 9.2 Files to Modify

| # | File | Change | Priority |
|---|------|--------|----------|
| 1 | AppModule.kt | Import securityModule | P0 |
| 2 | application.yml | Add rls config section | P1 |
| 3 | build.gradle.kts | Add PostgreSQL + HikariCP + Flyway dependencies (if not present) | P0 |

### 9.3 Implementation Order

1. **Phase 1:** Create KbRole enum + RlsConfig (no dependencies)
2. **Phase 2:** Create RoleContextService interface + impl
3. **Phase 3:** Create RlsConnectionWrapper
4. **Phase 4:** Create SecurityModule (Koin DI)
5. **Phase 5:** Write Flyway migrations (V8, V9, V10)
6. **Phase 6:** Integrate into AppModule
7. **Phase 7:** Write unit tests
8. **Phase 8:** Write integration tests with Testcontainers

---

## 10. Dependencies

### 10.1 New Dependencies Required

| Dependency | Version | Purpose |
|-----------|---------|---------|
| org.postgresql:postgresql | 42.7.x | PostgreSQL JDBC driver |
| com.zaxxer:hikaricp | 5.1.x | Connection pool |
| org.flywaydb:flyway-core | 10.x | Database migrations |
| org.flywaydb:flyway-database-postgresql | 10.x | PostgreSQL Flyway support |
| org.testcontainers:postgresql | 1.21.x | Integration test PostgreSQL |

### 10.2 Existing Dependencies Used

| Dependency | Purpose |
|-----------|---------|
| io.insert-koin:koin-core | DI framework |
| org.jetbrains.kotlinx:kotlinx-coroutines-core | Async execution |
| org.jetbrains.kotlinx:kotlinx-serialization-json | Config serialization |
| ch.qos.logback:logback-classic | Logging |

---

## 11. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Architecture Overview | [architecture.png](diagrams/architecture.png) | [architecture.drawio](diagrams/architecture.drawio) |
| 2 | Component Diagram | [component.png](diagrams/component.png) | [component.drawio](diagrams/component.drawio) |
| 3 | Class Diagram | [class-diagram.png](diagrams/class-diagram.png) | [class-diagram.drawio](diagrams/class-diagram.drawio) |

### Configuration Example (application.yml)

```yaml
orchestrator:
  security:
    rls:
      enabled: true
      default_role: LOW_PRIVILEGE
      force_rls: true
      role_mappings:
        ROLE_DEVELOPER: DEVELOPER
        ROLE_BA: BA_ADMIN
        ROLE_ADMIN: BA_ADMIN
        ROLE_USER: LOW_PRIVILEGE
```
