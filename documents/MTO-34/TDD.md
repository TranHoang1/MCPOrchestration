# Technical Design Document (TDD)

## MCPOrchestration — MTO-34: KB Refinery — Audit Log & Response Shaping

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-34 |
| Title | KB Refinery — Audit Log & Response Shaping |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |

---

## 1. Architecture Overview

### 1.1 Package Structure

```
com.orchestrator.mcp.audit/
├── AuditService.kt                   (interface)
├── AuditServiceImpl.kt               (async implementation)
├── ResponseShaper.kt                  (interface)
├── ResponseShaperImpl.kt             (role-based filtering)
├── AuditQueryService.kt              (interface — query audit logs)
├── AuditQueryServiceImpl.kt          (implementation)
├── model/
│   ├── AuditEvent.kt                 (event data class)
│   ├── AuditEventType.kt             (enum)
│   ├── AuditQueryFilter.kt           (query filter data class)
│   ├── ShapedResponse.kt             (shaped response wrapper)
│   └── AuditConfig.kt                (configuration)
├── repository/
│   ├── AuditEventRepository.kt       (interface)
│   └── AuditEventRepositoryImpl.kt   (JDBC implementation)
└── di/
    └── AuditModule.kt                (Koin module)
```

### 1.2 Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Async audit writes | Coroutine launch (fire-and-forget) | Non-blocking, < 5ms |
| Response shaping | Strategy pattern per role | Extensible, testable |
| Audit storage | PostgreSQL JSONB for metadata | Flexible, queryable |
| Immutability | REVOKE UPDATE/DELETE | Tamper-proof |

---

## 2. Detailed Design

### 2.1 AuditService Interface

```kotlin
interface AuditService {
    fun log(event: AuditEvent)
    suspend fun logSuspend(event: AuditEvent)
}
```

### 2.2 AuditServiceImpl

```kotlin
class AuditServiceImpl(
    private val repository: AuditEventRepository,
    private val scope: CoroutineScope
) : AuditService {

    override fun log(event: AuditEvent) {
        scope.launch {
            try { repository.save(event) }
            catch (e: Exception) { logger.error("Audit write failed: {}", e.message) }
        }
    }

    override suspend fun logSuspend(event: AuditEvent) {
        repository.save(event)
    }
}
```

### 2.3 ResponseShaper Interface

```kotlin
interface ResponseShaper {
    fun shape(role: KbRole, fieldName: String, value: String?): String?
    fun shapeMap(role: KbRole, data: Map<String, Any?>): Map<String, Any?>
}
```

### 2.4 ResponseShaperImpl

```kotlin
class ResponseShaperImpl : ResponseShaper {
    override fun shape(role: KbRole, fieldName: String, value: String?): String? =
        when {
            fieldName == "business_rules" -> shapeBr(role, value)
            fieldName == "pii_original" -> shapePii(role, value)
            fieldName == "audit_logs" -> if (role == KbRole.BA_ADMIN) value else null
            else -> value
        }

    private fun shapeBr(role: KbRole, value: String?): String? =
        when (role) {
            KbRole.BA_ADMIN -> value
            KbRole.DEVELOPER -> "[BR_MASKED]"
            KbRole.LOW_PRIVILEGE -> null
        }

    private fun shapePii(role: KbRole, value: String?): String? =
        when (role) {
            KbRole.BA_ADMIN -> value
            KbRole.DEVELOPER -> "[PII_MASKED]"
            KbRole.LOW_PRIVILEGE -> null
        }
}
```

---

## 3. Database Design

### 3.1 Migration: V11__audit_events.sql

```sql
CREATE TABLE IF NOT EXISTS audit_events (
    id          BIGSERIAL PRIMARY KEY,
    event_type  VARCHAR(30) NOT NULL,
    user_id     VARCHAR(100) NOT NULL,
    issue_key   VARCHAR(50),
    action      VARCHAR(200) NOT NULL,
    success     BOOLEAN NOT NULL,
    metadata    JSONB,
    ip_address  INET,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user_time ON audit_events (user_id, created_at DESC);
CREATE INDEX idx_audit_type_time ON audit_events (event_type, created_at DESC);
CREATE INDEX idx_audit_issue ON audit_events (issue_key) WHERE issue_key IS NOT NULL;

REVOKE UPDATE, DELETE ON audit_events FROM kb_admin, kb_developer, kb_viewer;
GRANT INSERT, SELECT ON audit_events TO kb_admin;
GRANT SELECT ON audit_events TO kb_developer;
GRANT USAGE, SELECT ON SEQUENCE audit_events_id_seq TO kb_admin;
```

---

## 4. Implementation Checklist

### Files to Create

| # | File | Lines (est.) |
|---|------|-------------|
| 1 | AuditService.kt | ~12 |
| 2 | AuditServiceImpl.kt | ~40 |
| 3 | ResponseShaper.kt | ~12 |
| 4 | ResponseShaperImpl.kt | ~55 |
| 5 | AuditQueryService.kt | ~12 |
| 6 | AuditQueryServiceImpl.kt | ~45 |
| 7 | AuditEvent.kt | ~20 |
| 8 | AuditEventType.kt | ~15 |
| 9 | AuditQueryFilter.kt | ~15 |
| 10 | AuditConfig.kt | ~12 |
| 11 | AuditEventRepository.kt | ~12 |
| 12 | AuditEventRepositoryImpl.kt | ~80 |
| 13 | AuditModule.kt | ~25 |

### Files to Modify

| # | File | Change |
|---|------|--------|
| 1 | AppModule.kt | Import auditModule |

---

## 5. Testing Strategy

| Level | What to Test | Framework |
|-------|-------------|-----------|
| Unit | ResponseShaperImpl role-based filtering | Kotest |
| Unit | AuditServiceImpl async behavior | Kotest + MockK |
| Unit | AuditQueryServiceImpl filter logic | Kotest + MockK |
| Integration | Audit write + query with real DB | Testcontainers |

