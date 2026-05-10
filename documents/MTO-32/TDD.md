# Technical Design Document (TDD)

## MCPOrchestration — MTO-32: KB Refinery — PII Mapping Encrypted Table

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-32 |
| Title | KB Refinery — PII Mapping Encrypted Table |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related FSD | FSD-v1-MTO-32.docx |
| Related BRD | BRD-v1-MTO-32.docx |

---

## 1. Architecture Overview

### 1.1 Component Architecture

The PII Access Control system adds a security layer on top of the existing `PiiMappingRepository`. It follows the existing project patterns: interface-first design, Koin DI, coroutine-based async operations.

**New Package:** `com.orchestrator.mcp.security.pii`

```
com.orchestrator.mcp.security.pii/
├── PiiAccessService.kt              (interface — 5 methods)
├── PiiAccessServiceImpl.kt          (implementation — orchestration)
├── PiiSessionService.kt             (interface — session management)
├── PiiSessionServiceImpl.kt         (implementation — token lifecycle)
├── PiiRateLimitService.kt           (interface — rate limiting)
├── PiiRateLimitServiceImpl.kt       (implementation — sliding window)
├── model/
│   ├── PiiAccessConfig.kt           (configuration data class)
│   ├── UnmaskResult.kt              (sealed class — Success/Denied/RateLimited)
│   ├── PiiSession.kt                (session data class)
│   ├── RateLimitResult.kt           (sealed class — Allowed/Exceeded)
│   └── PiiAuditEntry.kt             (audit record data class)
├── repository/
│   ├── PiiAccessAuditRepository.kt  (interface)
│   └── PiiAccessAuditRepositoryImpl.kt (JDBC implementation)
└── di/
    └── PiiAccessModule.kt           (Koin module)
```

### 1.2 Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Rate limit storage | PostgreSQL (pii_access_audit table) | Survives restart, consistent with existing pattern |
| Session storage | In-memory ConcurrentHashMap | Fast lookup, acceptable loss on restart (re-auth) |
| Audit write strategy | Synchronous (fail-closed) | BRD requires unmask denied if audit fails |
| Interface segregation | 3 interfaces (Access, Session, RateLimit) | ISP compliance, testable |

---

## 2. Detailed Design

### 2.1 PiiAccessService Interface

```kotlin
package com.orchestrator.mcp.security.pii

import com.orchestrator.mcp.security.pii.model.UnmaskResult

/**
 * Orchestrates PII unmask operations with access control.
 * Coordinates permission check, rate limiting, audit, and decryption.
 */
interface PiiAccessService {
    
    /** Create a new PII access session for authenticated admin user. */
    suspend fun createSession(userId: String, role: KbRole): PiiSession
    
    /** Unmask a PII value with full access control pipeline. */
    suspend fun unmask(
        sessionToken: String,
        issueKey: String,
        placeholder: String,
        ipAddress: String? = null
    ): UnmaskResult
    
    /** Revoke an active session. */
    suspend fun revokeSession(sessionToken: String): Boolean
    
    /** Get remaining quota for a user in current window. */
    suspend fun getRemainingQuota(userId: String): Int
}
```

### 2.2 UnmaskResult Sealed Class

```kotlin
package com.orchestrator.mcp.security.pii.model

sealed class UnmaskResult {
    data class Success(
        val originalValue: String,
        val remainingQuota: Int
    ) : UnmaskResult()
    
    data class Denied(
        val reason: DenialReason,
        val message: String
    ) : UnmaskResult()
    
    data class RateLimited(
        val retryAfterSeconds: Long,
        val windowResetAt: Instant
    ) : UnmaskResult()
}

enum class DenialReason {
    SESSION_EXPIRED,
    SESSION_REVOKED,
    INSUFFICIENT_PERMISSION,
    NOT_FOUND,
    DECRYPTION_ERROR,
    AUDIT_FAILURE,
    SYSTEM_ERROR
}
```

### 2.3 PiiAccessConfig

```kotlin
package com.orchestrator.mcp.security.pii.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

data class PiiAccessConfig(
    val maxUnmaskPerWindow: Int = 10,
    val windowDuration: Duration = 1.hours,
    val sessionTimeout: Duration = 30.minutes,
    val auditRetentionDays: Int = 90
)
```

### 2.4 PiiAccessServiceImpl (Core Logic)

```kotlin
package com.orchestrator.mcp.security.pii

class PiiAccessServiceImpl(
    private val sessionService: PiiSessionService,
    private val rateLimitService: PiiRateLimitService,
    private val auditRepository: PiiAccessAuditRepository,
    private val piiMappingRepository: PiiMappingRepository,
    private val rlsWrapper: RlsConnectionWrapper,
    private val config: PiiAccessConfig
) : PiiAccessService {

    override suspend fun unmask(
        sessionToken: String,
        issueKey: String,
        placeholder: String,
        ipAddress: String?
    ): UnmaskResult {
        // Step 1: Validate session
        val session = sessionService.validate(sessionToken)
            ?: return denied(DenialReason.SESSION_EXPIRED, "Session expired or invalid")
        
        if (session.revoked) {
            return denied(DenialReason.SESSION_REVOKED, "Session has been revoked")
        }
        
        // Step 2: Check role
        if (session.role != KbRole.BA_ADMIN) {
            return denied(DenialReason.INSUFFICIENT_PERMISSION, "Admin role required")
        }
        
        // Step 3: Check rate limit
        val rateLimitResult = rateLimitService.check(session.userId, config)
        if (rateLimitResult is RateLimitResult.Exceeded) {
            logAuditFailure(session, issueKey, placeholder, "RATE_LIMIT_EXCEEDED", ipAddress)
            return UnmaskResult.RateLimited(rateLimitResult.retryAfterSeconds, rateLimitResult.windowResetAt)
        }
        
        // Step 4: Retrieve PII mapping
        val mappings = piiMappingRepository.findByIssueKey(issueKey)
        val mapping = mappings.find { it.placeholder == placeholder }
            ?: return denied(DenialReason.NOT_FOUND, "Placeholder not found")
        
        // Step 5: Log audit (fail-closed)
        val auditSuccess = logAuditSuccess(session, issueKey, placeholder, ipAddress)
        if (!auditSuccess) {
            return denied(DenialReason.AUDIT_FAILURE, "Audit write failed")
        }
        
        // Step 6: Return decrypted value
        val remaining = (rateLimitResult as RateLimitResult.Allowed).remaining
        return UnmaskResult.Success(mapping.originalValue, remaining - 1)
    }
}
```

### 2.5 PiiRateLimitServiceImpl

```kotlin
package com.orchestrator.mcp.security.pii

class PiiRateLimitServiceImpl(
    private val auditRepository: PiiAccessAuditRepository
) : PiiRateLimitService {

    override suspend fun check(userId: String, config: PiiAccessConfig): RateLimitResult {
        val windowStart = Clock.System.now() - config.windowDuration
        val count = auditRepository.countSuccessfulUnmaskSince(userId, windowStart)
        
        if (count >= config.maxUnmaskPerWindow) {
            val oldest = auditRepository.findOldestSuccessfulInWindow(userId, windowStart)
            val retryAfter = if (oldest != null) {
                (oldest + config.windowDuration - Clock.System.now()).inWholeSeconds
            } else {
                config.windowDuration.inWholeSeconds
            }
            return RateLimitResult.Exceeded(
                retryAfterSeconds = maxOf(retryAfter, 1),
                windowResetAt = oldest?.plus(config.windowDuration) ?: (Clock.System.now() + config.windowDuration)
            )
        }
        
        return RateLimitResult.Allowed(remaining = config.maxUnmaskPerWindow - count)
    }
}
```

### 2.6 PiiSessionServiceImpl

```kotlin
package com.orchestrator.mcp.security.pii

import java.util.concurrent.ConcurrentHashMap

class PiiSessionServiceImpl(
    private val config: PiiAccessConfig
) : PiiSessionService {

    private val sessions = ConcurrentHashMap<String, PiiSession>()

    override suspend fun create(userId: String, role: KbRole): PiiSession {
        val now = Clock.System.now()
        val session = PiiSession(
            token = UUID.randomUUID().toString(),
            userId = userId,
            role = role,
            createdAt = now,
            expiresAt = now + config.sessionTimeout,
            revoked = false
        )
        sessions[session.token] = session
        return session
    }

    override suspend fun validate(token: String): PiiSession? {
        val session = sessions[token] ?: return null
        if (session.expiresAt < Clock.System.now()) {
            sessions.remove(token)
            return null
        }
        return session
    }

    override suspend fun revoke(token: String): Boolean {
        val session = sessions[token] ?: return false
        sessions[token] = session.copy(revoked = true)
        return true
    }
}
```

---

## 3. Database Design

### 3.1 Migration: V9__pii_access_audit.sql

```sql
-- PII Access Audit Table (append-only)
CREATE TABLE IF NOT EXISTS pii_access_audit (
    id          BIGSERIAL PRIMARY KEY,
    user_id     VARCHAR(100) NOT NULL,
    issue_key   VARCHAR(50) NOT NULL,
    placeholder VARCHAR(200) NOT NULL,
    action      VARCHAR(20) NOT NULL DEFAULT 'UNMASK_PII',
    success     BOOLEAN NOT NULL,
    failure_reason VARCHAR(200),
    ip_address  INET,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for rate limit queries and audit lookups
CREATE INDEX idx_pii_audit_user_time ON pii_access_audit (user_id, created_at DESC);
CREATE INDEX idx_pii_audit_issue ON pii_access_audit (issue_key);
CREATE INDEX idx_pii_audit_success_time ON pii_access_audit (user_id, success, created_at DESC)
    WHERE success = true;

-- Prevent UPDATE/DELETE (append-only)
REVOKE UPDATE, DELETE ON pii_access_audit FROM kb_admin, kb_developer, kb_viewer;

-- Only the application role can INSERT
GRANT INSERT, SELECT ON pii_access_audit TO kb_admin;
GRANT SELECT ON pii_access_audit TO kb_developer, kb_viewer;
GRANT USAGE, SELECT ON SEQUENCE pii_access_audit_id_seq TO kb_admin;
```

### 3.2 Indexes

| Index | Columns | Purpose |
|-------|---------|---------|
| idx_pii_audit_user_time | (user_id, created_at DESC) | Rate limit sliding window query |
| idx_pii_audit_issue | (issue_key) | Audit lookup by ticket |
| idx_pii_audit_success_time | (user_id, success, created_at DESC) WHERE success=true | Efficient rate limit count |

---

## 4. DI Module

### 4.1 PiiAccessModule.kt

```kotlin
package com.orchestrator.mcp.security.pii.di

import com.orchestrator.mcp.security.pii.*
import com.orchestrator.mcp.security.pii.model.PiiAccessConfig
import com.orchestrator.mcp.security.pii.repository.*
import org.koin.dsl.module

val piiAccessModule = module {
    single { PiiAccessConfig() }
    
    single<PiiAccessAuditRepository> {
        PiiAccessAuditRepositoryImpl(get()) // HikariDataSource
    }
    
    single<PiiSessionService> {
        PiiSessionServiceImpl(get()) // PiiAccessConfig
    }
    
    single<PiiRateLimitService> {
        PiiRateLimitServiceImpl(get()) // PiiAccessAuditRepository
    }
    
    single<PiiAccessService> {
        PiiAccessServiceImpl(
            sessionService = get(),
            rateLimitService = get(),
            auditRepository = get(),
            piiMappingRepository = get(),
            rlsWrapper = get(),
            config = get()
        )
    }
}
```

---

## 5. Error Handling

| Error Type | Handling | Recovery |
|------------|----------|----------|
| DB connection failure | Log error, return SYSTEM_ERROR | Retry via HikariCP pool |
| Decryption failure | Log error, return DECRYPTION_ERROR | Alert ops, check key |
| Audit write failure | Deny unmask (fail-closed) | Alert ops, check DB |
| Session not found | Return SESSION_EXPIRED | User re-authenticates |
| Rate limit exceeded | Return retry-after duration | User waits |

---

## 6. Security Design

### 6.1 Threat Model

| Threat | Mitigation |
|--------|-----------|
| Bulk PII exfiltration | Rate limiting (10/hour/user) |
| Unauthorized access | Role check (admin-only) + RLS |
| Session hijacking | 30-min expiry, UUID tokens |
| Audit tampering | REVOKE UPDATE/DELETE on audit table |
| Replay attacks | Each unmask logged with timestamp |

### 6.2 Encryption

- PII values encrypted at rest via `EncryptionService` (AES-256-GCM)
- Decryption only in application layer, never exposed in logs
- Session tokens are UUIDs (not JWTs) — no sensitive data in token

---

## 7. Implementation Checklist

### Files to Create

| # | File | Package | Lines (est.) |
|---|------|---------|-------------|
| 1 | PiiAccessService.kt | security.pii | ~25 |
| 2 | PiiAccessServiceImpl.kt | security.pii | ~120 |
| 3 | PiiSessionService.kt | security.pii | ~15 |
| 4 | PiiSessionServiceImpl.kt | security.pii | ~60 |
| 5 | PiiRateLimitService.kt | security.pii | ~12 |
| 6 | PiiRateLimitServiceImpl.kt | security.pii | ~45 |
| 7 | PiiAccessConfig.kt | security.pii.model | ~15 |
| 8 | UnmaskResult.kt | security.pii.model | ~30 |
| 9 | PiiSession.kt | security.pii.model | ~15 |
| 10 | RateLimitResult.kt | security.pii.model | ~15 |
| 11 | PiiAuditEntry.kt | security.pii.model | ~15 |
| 12 | PiiAccessAuditRepository.kt | security.pii.repository | ~15 |
| 13 | PiiAccessAuditRepositoryImpl.kt | security.pii.repository | ~90 |
| 14 | PiiAccessModule.kt | security.pii.di | ~30 |
| 15 | V9__pii_access_audit.sql | db/migration | ~20 |

### Files to Modify

| # | File | Change |
|---|------|--------|
| 1 | AppModule.kt or SecurityModule | Import piiAccessModule |
| 2 | McpToolRegistrar.kt | Register unmask_pii tool |

---

## 8. Testing Strategy

| Level | What to Test | Framework |
|-------|-------------|-----------|
| Unit | PiiAccessServiceImpl logic | Kotest + MockK |
| Unit | PiiRateLimitServiceImpl sliding window | Kotest + MockK |
| Unit | PiiSessionServiceImpl expiry/revoke | Kotest |
| Integration | Full unmask flow with real DB | Testcontainers + PostgreSQL |
| Integration | Rate limit persistence across restart | Testcontainers |
| Integration | Audit immutability (REVOKE) | Testcontainers |

---

## 9. Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Architecture | [architecture.png](diagrams/architecture.png) | [architecture.drawio](diagrams/architecture.drawio) |
| 2 | Component | [component.png](diagrams/component.png) | [component.drawio](diagrams/component.drawio) |
| 3 | Class Diagram | [class-diagram.png](diagrams/class-diagram.png) | [class-diagram.drawio](diagrams/class-diagram.drawio) |
