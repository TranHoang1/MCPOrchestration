# Technical Design Document (TDD)

## MCPOrchestration — MTO-33: KB Refinery — Business Rules Encryption & Access Control

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-33 |
| Title | KB Refinery — Business Rules Encryption & Access Control |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related FSD | FSD-v1-MTO-33.docx |
| Related BRD | BRD-v1-MTO-33.docx |

---

## 1. Architecture Overview

### 1.1 Component Architecture

The BR Access Control system provides sensitivity-level-based access control, session management, DLP enforcement, rate limiting, and KMS key management for encrypted Business Rules. It builds on the existing `BrEncryptionService` (MTO-30) and `RlsConnectionWrapper` (MTO-31).

**New Package:** `com.orchestrator.mcp.security.br`

```
com.orchestrator.mcp.security.br/
├── BrAccessService.kt                (interface — orchestration)
├── BrAccessServiceImpl.kt            (implementation — access pipeline)
├── BrSessionService.kt               (interface — session management)
├── BrSessionServiceImpl.kt           (implementation — token lifecycle)
├── BrRateLimitService.kt             (interface — per-level rate limiting)
├── BrRateLimitServiceImpl.kt         (implementation — sliding window)
├── BrKeyManagementService.kt         (interface — KMS operations)
├── BrKeyManagementServiceImpl.kt     (implementation — file-based KMS)
├── BrDlpService.kt                   (interface — DLP header generation)
├── BrDlpServiceImpl.kt               (implementation — headers + logging guard)
├── model/
│   ├── BrAccessConfig.kt             (configuration data class)
│   ├── BrAccessResult.kt             (sealed class — Success/Denied/RateLimited)
│   ├── BrSession.kt                  (session data class)
│   ├── BrSensitivityLevel.kt         (enum — HIGH/MEDIUM/LOW)
│   ├── DlpHeaders.kt                 (DLP response headers)
│   └── KeyMetadata.kt                (KMS key metadata)
├── repository/
│   ├── BrAccessAuditRepository.kt    (interface)
│   └── BrAccessAuditRepositoryImpl.kt (JDBC implementation)
└── di/
    └── BrAccessModule.kt             (Koin module)
```

### 1.2 Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| KMS implementation | File-based (dev), interface for cloud KMS | Pluggable, testable |
| Session storage | In-memory ConcurrentHashMap | Fast, acceptable loss on restart |
| Rate limit storage | PostgreSQL (br_access_audit) | Survives restart, per-level tracking |
| DLP enforcement | Service layer (not middleware) | MCP tool responses, not HTTP |
| Sensitivity access | Static matrix in config | Simple, auditable |

---

## 2. Detailed Design

### 2.1 BrAccessService Interface

```kotlin
package com.orchestrator.mcp.security.br

interface BrAccessService {
    suspend fun createSession(userId: String, role: KbRole): BrSession
    suspend fun viewBusinessRules(
        sessionToken: String,
        issueKey: String,
        ipAddress: String? = null
    ): BrAccessResult
    suspend fun revokeSession(sessionToken: String): Boolean
    suspend fun getRemainingQuota(userId: String, level: BrSensitivityLevel): Int
}
```

### 2.2 BrAccessResult Sealed Class

```kotlin
package com.orchestrator.mcp.security.br.model

sealed class BrAccessResult {
    data class Success(
        val content: String,
        val sensitivityLevel: BrSensitivityLevel,
        val dlpHeaders: DlpHeaders,
        val remainingQuota: Int
    ) : BrAccessResult()

    data class Denied(
        val reason: BrDenialReason,
        val message: String
    ) : BrAccessResult()

    data class RateLimited(
        val retryAfterSeconds: Long,
        val sensitivityLevel: BrSensitivityLevel
    ) : BrAccessResult()
}

enum class BrDenialReason {
    SESSION_EXPIRED, SESSION_REVOKED,
    INSUFFICIENT_PERMISSION, NOT_FOUND,
    DECRYPTION_ERROR, KMS_UNAVAILABLE,
    SYSTEM_ERROR
}
```

### 2.3 BrSensitivityLevel Enum

```kotlin
package com.orchestrator.mcp.security.br.model

enum class BrSensitivityLevel(val level: Int, val maxPerHour: Int) {
    HIGH(1, 5),
    MEDIUM(2, 15),
    LOW(3, 30);

    companion object {
        fun fromInt(value: Int): BrSensitivityLevel =
            entries.find { it.level == value }
                ?: throw IllegalArgumentException("Unknown sensitivity level: $value")
    }
}
```

### 2.4 BrAccessConfig

```kotlin
package com.orchestrator.mcp.security.br.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

data class BrAccessConfig(
    val sessionTimeout: Duration = 30.minutes,
    val rateLimitWindow: Duration = 1.hours,
    val kmsKeyPath: String = "config/br-keys",
    val activeKeyId: String = "br-key-2026-05"
)
```

### 2.5 BrAccessServiceImpl (Core Logic)

```kotlin
class BrAccessServiceImpl(
    private val sessionService: BrSessionService,
    private val rateLimitService: BrRateLimitService,
    private val kmsService: BrKeyManagementService,
    private val dlpService: BrDlpService,
    private val auditRepository: BrAccessAuditRepository,
    private val kbEntryRepository: KbEntryRepository,
    private val config: BrAccessConfig
) : BrAccessService {

    override suspend fun viewBusinessRules(
        sessionToken: String,
        issueKey: String,
        ipAddress: String?
    ): BrAccessResult {
        // Step 1: Validate session
        val session = sessionService.validate(sessionToken)
            ?: return denied(SESSION_EXPIRED, "Session expired or invalid")

        if (session.revoked) return denied(SESSION_REVOKED, "Session revoked")

        // Step 2: Retrieve BR entry
        val entry = kbEntryRepository.findByIssueKey(issueKey)
            ?: return denied(NOT_FOUND, "BR not found for issue")

        val level = BrSensitivityLevel.fromInt(entry.brSensitivityLevel)

        // Step 3: Check role against sensitivity level
        if (!hasAccess(session.role, level))
            return denied(INSUFFICIENT_PERMISSION, "Insufficient permissions")

        // Step 4: Check rate limit
        val rateResult = rateLimitService.check(session.userId, level, config)
        if (rateResult is BrRateLimitResult.Exceeded)
            return BrAccessResult.RateLimited(rateResult.retryAfterSeconds, level)

        // Step 5: Decrypt with KMS key
        val decrypted = kmsService.decrypt(entry.businessRulesEncrypted, entry.brKeyId)
            ?: return denied(DECRYPTION_ERROR, "Decryption failed")

        // Step 6: Audit (async — not fail-closed for BR)
        auditRepository.logAccess(session.userId, issueKey, level, true, ipAddress)

        // Step 7: Return with DLP headers
        val remaining = (rateResult as BrRateLimitResult.Allowed).remaining
        return BrAccessResult.Success(decrypted, level, dlpService.generateHeaders(), remaining)
    }

    private fun hasAccess(role: KbRole, level: BrSensitivityLevel): Boolean =
        when (level) {
            BrSensitivityLevel.HIGH -> role == KbRole.BA_ADMIN
            BrSensitivityLevel.MEDIUM -> role == KbRole.BA_ADMIN
            BrSensitivityLevel.LOW -> role in listOf(KbRole.BA_ADMIN, KbRole.DEVELOPER)
        }
}
```

### 2.6 BrKeyManagementService

```kotlin
interface BrKeyManagementService {
    fun getActiveKeyId(): String
    fun encrypt(plaintext: String): EncryptedPayload
    fun decrypt(encryptedPayload: String, keyId: String): String?
    fun rotateKey(newKeyId: String, newKeyBase64: String): Boolean
    fun getKeyMetadata(keyId: String): KeyMetadata?
}
```

### 2.7 BrDlpService

```kotlin
interface BrDlpService {
    fun generateHeaders(): DlpHeaders
    fun sanitizeForLogging(content: String): String
}

data class DlpHeaders(
    val cacheControl: String = "no-store, no-cache, must-revalidate",
    val pragma: String = "no-cache",
    val contentTypeOptions: String = "nosniff",
    val dlpFlag: String = "enforced"
)
```

### 2.8 BrSessionServiceImpl

```kotlin
class BrSessionServiceImpl(
    private val config: BrAccessConfig
) : BrSessionService {

    private val sessions = ConcurrentHashMap<String, BrSession>()

    override suspend fun create(userId: String, role: KbRole): BrSession {
        val now = Clock.System.now()
        val session = BrSession(
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

    override suspend fun validate(token: String): BrSession? {
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

### 3.1 Migration: V10__br_access_audit.sql

```sql
CREATE TABLE IF NOT EXISTS br_access_audit (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(100) NOT NULL,
    issue_key       VARCHAR(50) NOT NULL,
    sensitivity_level INTEGER NOT NULL,
    action          VARCHAR(30) NOT NULL DEFAULT 'VIEW_BR',
    success         BOOLEAN NOT NULL,
    failure_reason  VARCHAR(200),
    ip_address      INET,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_br_audit_user_level_time
    ON br_access_audit (user_id, sensitivity_level, created_at DESC);
CREATE INDEX idx_br_audit_issue
    ON br_access_audit (issue_key);
CREATE INDEX idx_br_audit_success
    ON br_access_audit (user_id, sensitivity_level, success, created_at DESC)
    WHERE success = true;

REVOKE UPDATE, DELETE ON br_access_audit FROM kb_admin, kb_developer, kb_viewer;
GRANT INSERT, SELECT ON br_access_audit TO kb_admin;
GRANT SELECT ON br_access_audit TO kb_developer, kb_viewer;
GRANT USAGE, SELECT ON SEQUENCE br_access_audit_id_seq TO kb_admin;
```

---

## 4. DI Module

```kotlin
val brAccessModule = module {
    single { BrAccessConfig() }
    single<BrAccessAuditRepository> { BrAccessAuditRepositoryImpl(get()) }
    single<BrSessionService> { BrSessionServiceImpl(get()) }
    single<BrRateLimitService> { BrRateLimitServiceImpl(get()) }
    single<BrKeyManagementService> { BrKeyManagementServiceImpl(get()) }
    single<BrDlpService> { BrDlpServiceImpl() }
    single<BrAccessService> {
        BrAccessServiceImpl(get(), get(), get(), get(), get(), get(), get())
    }
}
```

---

## 5. Error Handling

| Error Type | Handling | Recovery |
|------------|----------|----------|
| KMS unavailable | Fail-closed, deny all BR access | Alert ops |
| Decryption failure | Return DECRYPTION_ERROR | Log, check key |
| Session not found | Return SESSION_EXPIRED | Re-authenticate |
| Rate limit exceeded | Return retry-after | Wait |
| DB connection failure | Return SYSTEM_ERROR | HikariCP retry |

---

## 6. Security Design

| Threat | Mitigation |
|--------|-----------|
| Bulk BR exfiltration | Per-level rate limiting |
| Unauthorized access | Sensitivity-level matrix + session |
| Key compromise | Key rotation, per-entry key_id |
| Response caching | DLP headers (no-store) |
| Log leakage | DLP service sanitizes all logging |
| Session hijacking | 30-min expiry, UUID tokens |

---

## 7. Implementation Checklist

### Files to Create

| # | File | Package | Lines (est.) |
|---|------|---------|-------------|
| 1 | BrAccessService.kt | security.br | ~20 |
| 2 | BrAccessServiceImpl.kt | security.br | ~100 |
| 3 | BrSessionService.kt | security.br | ~15 |
| 4 | BrSessionServiceImpl.kt | security.br | ~55 |
| 5 | BrRateLimitService.kt | security.br | ~12 |
| 6 | BrRateLimitServiceImpl.kt | security.br | ~50 |
| 7 | BrKeyManagementService.kt | security.br | ~18 |
| 8 | BrKeyManagementServiceImpl.kt | security.br | ~90 |
| 9 | BrDlpService.kt | security.br | ~12 |
| 10 | BrDlpServiceImpl.kt | security.br | ~30 |
| 11 | BrAccessConfig.kt | security.br.model | ~15 |
| 12 | BrAccessResult.kt | security.br.model | ~30 |
| 13 | BrSession.kt | security.br.model | ~15 |
| 14 | BrSensitivityLevel.kt | security.br.model | ~18 |
| 15 | DlpHeaders.kt | security.br.model | ~12 |
| 16 | KeyMetadata.kt | security.br.model | ~15 |
| 17 | BrRateLimitResult.kt | security.br.model | ~15 |
| 18 | BrAccessAuditRepository.kt | security.br.repository | ~15 |
| 19 | BrAccessAuditRepositoryImpl.kt | security.br.repository | ~80 |
| 20 | BrAccessModule.kt | security.br.di | ~25 |
| 21 | V10__br_access_audit.sql | db/migration | ~20 |

### Files to Modify

| # | File | Change |
|---|------|--------|
| 1 | AppModule.kt | Import brAccessModule |
| 2 | McpToolRegistrar.kt | Register view_business_rules tool |

---

## 8. Testing Strategy

| Level | What to Test | Framework |
|-------|-------------|-----------|
| Unit | BrAccessServiceImpl access pipeline | Kotest + MockK |
| Unit | BrRateLimitServiceImpl per-level limits | Kotest + MockK |
| Unit | BrSessionServiceImpl expiry/revoke | Kotest |
| Unit | BrKeyManagementServiceImpl encrypt/decrypt | Kotest |
| Unit | BrDlpServiceImpl header generation | Kotest |
| Integration | Full BR access flow with real DB | Testcontainers |
| Integration | Key rotation with multiple entries | Testcontainers |
| Integration | Audit immutability | Testcontainers |

