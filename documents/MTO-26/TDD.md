# Technical Design Document (TDD)

## MTO-26: KB Refinery — KB Entries Schema 4 Columns + Migration

| Field | Value |
|-------|-------|
| **Ticket** | MTO-26 |
| **Epic** | MTO-24 (Knowledge Base Refinery) |
| **Version** | 1.0 |
| **Status** | Draft |
| **Author** | SA Agent |
| **Created** | 2026-05-08 |
| **Related FSD** | FSD-v1-MTO-26.docx |

---

## 1. Architecture Overview

### 1.1 Package Structure

```
com.orchestrator.mcp.kbstore/
├── config/
│   └── KbStoreConfig.kt              # Configuration data class
├── model/
│   ├── KbEntry.kt                    # KB entry domain model
│   ├── PiiMapping.kt                 # PII mapping domain model
│   ├── BrSensitivityLevel.kt         # Sensitivity level enum
│   └── MappingType.kt                # PII mapping type enum
├── repository/
│   ├── KbEntryRepository.kt          # Interface
│   ├── KbEntryRepositoryImpl.kt      # JDBC implementation
│   ├── PiiMappingRepository.kt       # Interface
│   └── PiiMappingRepositoryImpl.kt   # JDBC implementation
├── encryption/
│   ├── EncryptionService.kt          # Interface
│   └── EncryptionServiceImpl.kt      # AES-256-GCM implementation
└── di/
    └── KbStoreModule.kt              # Koin DI module
```

### 1.2 Layer Architecture

| Layer | Components | Responsibility |
|-------|-----------|----------------|
| Config | KbStoreConfig | Load encryption key, batch size from YAML |
| Model | KbEntry, PiiMapping, Enums | Domain data classes (no logic) |
| Repository | KbEntryRepository, PiiMappingRepository | CRUD operations with encryption |
| Encryption | EncryptionService | AES-256-GCM encrypt/decrypt |
| DI | KbStoreModule | Wire all components via Koin |
| Migration | V6, V7 SQL | Database schema creation |

## 2. Detailed Design

### 2.1 Configuration — KbStoreConfig

```kotlin
// File: kbstore/config/KbStoreConfig.kt
package com.orchestrator.mcp.kbstore.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KbStoreConfig(
    @SerialName("encryption_key")
    val encryptionKey: String,          // Base64-encoded 32-byte key
    @SerialName("batch_size")
    val batchSize: Int = 500
)
```

**Integration with existing config:**
- Add `kbstore` section under `orchestrator` in `application.yml`
- `encryption_key` resolves env var `${KB_ENCRYPTION_KEY}`
- Loaded by existing `ConfigurationManager`

### 2.2 Domain Models

#### KbEntry.kt

```kotlin
// File: kbstore/model/KbEntry.kt (< 30 lines)
package com.orchestrator.mcp.kbstore.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

data class KbEntry(
    val id: UUID = UUID.randomUUID(),
    val issueKey: String,
    val projectKey: String,
    val publicContent: String? = null,
    val technicalContent: String? = null,
    val businessRules: String? = null,
    val maskedFull: String? = null,
    val brSensitivityLevel: BrSensitivityLevel = BrSensitivityLevel.INTERNAL,
    val contentHash: String,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val lastSyncedAt: Instant? = null
)
```

#### PiiMapping.kt

```kotlin
// File: kbstore/model/PiiMapping.kt (< 20 lines)
package com.orchestrator.mcp.kbstore.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

data class PiiMapping(
    val id: UUID = UUID.randomUUID(),
    val issueKey: String,
    val placeholder: String,
    val originalValue: String,
    val mappingType: MappingType,
    val createdAt: Instant = Clock.System.now()
)
```

#### BrSensitivityLevel.kt

```kotlin
// File: kbstore/model/BrSensitivityLevel.kt
package com.orchestrator.mcp.kbstore.model

enum class BrSensitivityLevel(val level: Int) {
    CONFIDENTIAL(1),
    INTERNAL(2),
    RESTRICTED(3);

    companion object {
        fun fromLevel(level: Int): BrSensitivityLevel =
            entries.firstOrNull { it.level == level }
                ?: throw IllegalArgumentException("Invalid sensitivity level: $level")
    }
}
```

#### MappingType.kt

```kotlin
// File: kbstore/model/MappingType.kt
package com.orchestrator.mcp.kbstore.model

enum class MappingType {
    NAME, ID_CARD, PHONE, BANK_ACCOUNT, EMAIL
}
```

### 2.3 Encryption Service

#### Interface

```kotlin
// File: kbstore/encryption/EncryptionService.kt
package com.orchestrator.mcp.kbstore.encryption

interface EncryptionService {
    fun encrypt(plaintext: String): ByteArray
    fun decrypt(ciphertext: ByteArray): String
}
```

#### Implementation

```kotlin
// File: kbstore/encryption/EncryptionServiceImpl.kt
package com.orchestrator.mcp.kbstore.encryption

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptionServiceImpl(
    base64Key: String
) : EncryptionService {

    private val secretKey: SecretKeySpec
    private val secureRandom = SecureRandom()

    init {
        val keyBytes = Base64.getDecoder().decode(base64Key)
        require(keyBytes.size == 32) { "Encryption key must be 32 bytes (256 bits)" }
        secretKey = SecretKeySpec(keyBytes, ALGORITHM)
    }

    override fun encrypt(plaintext: String): ByteArray {
        val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = createCipher(Cipher.ENCRYPT_MODE, iv)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return iv + ciphertext
    }

    override fun decrypt(ciphertext: ByteArray): String {
        require(ciphertext.size > IV_LENGTH) { "Ciphertext too short" }
        val iv = ciphertext.copyOfRange(0, IV_LENGTH)
        val encrypted = ciphertext.copyOfRange(IV_LENGTH, ciphertext.size)
        val cipher = createCipher(Cipher.DECRYPT_MODE, iv)
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun createCipher(mode: Int, iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
    }
}
```

### 2.4 Repository — KbEntryRepository

#### Interface

```kotlin
// File: kbstore/repository/KbEntryRepository.kt
package com.orchestrator.mcp.kbstore.repository

import com.orchestrator.mcp.kbstore.model.KbEntry
import kotlinx.datetime.Instant

interface KbEntryRepository {
    suspend fun upsert(entry: KbEntry)
    suspend fun upsertBatch(entries: List<KbEntry>): Int
    suspend fun findByIssueKey(issueKey: String): KbEntry?
    suspend fun findByProjectKey(projectKey: String): List<KbEntry>
    suspend fun findByContentHash(projectKey: String, hash: String): KbEntry?
    suspend fun updateLastSyncedAt(issueKey: String, syncedAt: Instant)
    suspend fun delete(issueKey: String)
}
```

#### Implementation (split into logical sections)

```kotlin
// File: kbstore/repository/KbEntryRepositoryImpl.kt
package com.orchestrator.mcp.kbstore.repository

import com.orchestrator.mcp.kbstore.encryption.EncryptionService
import com.orchestrator.mcp.kbstore.model.BrSensitivityLevel
import com.orchestrator.mcp.kbstore.model.KbEntry
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

class KbEntryRepositoryImpl(
    private val dataSource: HikariDataSource,
    private val encryptionService: EncryptionService
) : KbEntryRepository {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun upsert(entry: KbEntry): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(UPSERT_SQL).use { stmt ->
                setEntryParams(stmt, entry)
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun upsertBatch(entries: List<KbEntry>): Int =
        withContext(Dispatchers.IO) {
            if (entries.isEmpty()) return@withContext 0
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val count = conn.prepareStatement(UPSERT_SQL).use { stmt ->
                        entries.forEach { entry ->
                            setEntryParams(stmt, entry)
                            stmt.addBatch()
                        }
                        stmt.executeBatch().sum()
                    }
                    conn.commit()
                    log.debug("Batch upsert: {} entries", count)
                    count
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }

    override suspend fun findByIssueKey(issueKey: String): KbEntry? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(FIND_BY_ISSUE_KEY_SQL).use { stmt ->
                    stmt.setString(1, issueKey)
                    val rs = stmt.executeQuery()
                    if (rs.next()) mapRow(rs) else null
                }
            }
        }

    // ... additional methods follow same pattern
}
```

**SQL Constants (companion object):**

```kotlin
companion object {
    private val UPSERT_SQL = """
        INSERT INTO kb_entries 
            (id, issue_key, project_key, public_content, technical_content,
             business_rules, masked_full, br_sensitivity_level, content_hash)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (issue_key) DO UPDATE SET
            project_key = EXCLUDED.project_key,
            public_content = EXCLUDED.public_content,
            technical_content = EXCLUDED.technical_content,
            business_rules = EXCLUDED.business_rules,
            masked_full = EXCLUDED.masked_full,
            br_sensitivity_level = EXCLUDED.br_sensitivity_level,
            content_hash = EXCLUDED.content_hash,
            updated_at = NOW()
    """.trimIndent()

    private const val FIND_BY_ISSUE_KEY_SQL =
        "SELECT * FROM kb_entries WHERE issue_key = ?"
}
```

### 2.5 Repository — PiiMappingRepository

#### Interface

```kotlin
// File: kbstore/repository/PiiMappingRepository.kt
package com.orchestrator.mcp.kbstore.repository

import com.orchestrator.mcp.kbstore.model.PiiMapping

interface PiiMappingRepository {
    suspend fun insertBatch(mappings: List<PiiMapping>): Int
    suspend fun findByIssueKey(issueKey: String): List<PiiMapping>
    suspend fun deleteByIssueKey(issueKey: String): Int
    suspend fun replaceForIssueKey(issueKey: String, mappings: List<PiiMapping>): Int
}
```

#### Implementation

```kotlin
// File: kbstore/repository/PiiMappingRepositoryImpl.kt
// Follows same pattern as KbEntryRepositoryImpl
// - encrypt originalValue before INSERT
// - decrypt originalValue after SELECT
// - replaceForIssueKey = DELETE + INSERT in transaction
```

### 2.6 DI Module

```kotlin
// File: kbstore/di/KbStoreModule.kt
package com.orchestrator.mcp.kbstore.di

import com.orchestrator.mcp.kbstore.config.KbStoreConfig
import com.orchestrator.mcp.kbstore.encryption.EncryptionService
import com.orchestrator.mcp.kbstore.encryption.EncryptionServiceImpl
import com.orchestrator.mcp.kbstore.repository.*
import org.koin.dsl.module

val kbStoreModule = module {
    single<EncryptionService> {
        EncryptionServiceImpl(get<KbStoreConfig>().encryptionKey)
    }
    single<KbEntryRepository> {
        KbEntryRepositoryImpl(get(), get())
    }
    single<PiiMappingRepository> {
        PiiMappingRepositoryImpl(get(), get())
    }
}
```

### 2.7 Migration Scripts

#### V6__create_kb_entries.sql

```sql
-- KB Entries: 4-column content layering with encryption
-- MTO-26: KB Refinery — KB Entries Schema

CREATE TABLE IF NOT EXISTS kb_entries (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_key            VARCHAR(50) NOT NULL UNIQUE,
    project_key          VARCHAR(20) NOT NULL,
    public_content       TEXT,
    technical_content    TEXT,
    business_rules       BYTEA,
    masked_full          TEXT,
    br_sensitivity_level INT NOT NULL DEFAULT 2,
    content_hash         VARCHAR(64) NOT NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    last_synced_at       TIMESTAMP,

    CONSTRAINT chk_br_sensitivity_level 
        CHECK (br_sensitivity_level IN (1, 2, 3))
);

CREATE INDEX idx_kb_entries_project_key 
    ON kb_entries (project_key);

CREATE INDEX idx_kb_entries_content_hash 
    ON kb_entries (project_key, content_hash);

COMMENT ON TABLE kb_entries IS 'KB entries with 4-layer content separation (MTO-26)';
COMMENT ON COLUMN kb_entries.public_content IS 'Public metadata visible to all roles';
COMMENT ON COLUMN kb_entries.technical_content IS 'Technical content for Developer+ roles';
COMMENT ON COLUMN kb_entries.business_rules IS 'Encrypted business rules (AES-256-GCM) for BA/Admin';
COMMENT ON COLUMN kb_entries.masked_full IS 'PII+BR masked version for low-privilege users';
COMMENT ON COLUMN kb_entries.br_sensitivity_level IS '1=Confidential, 2=Internal, 3=Restricted';
COMMENT ON COLUMN kb_entries.content_hash IS 'SHA-256 hash for change detection';
```

#### V7__create_pii_mapping.sql

```sql
-- PII Mapping: Encrypted PII placeholder-to-original mapping
-- MTO-26: KB Refinery — KB Entries Schema

CREATE TABLE IF NOT EXISTS pii_mapping (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_key       VARCHAR(50) NOT NULL,
    placeholder     VARCHAR(50) NOT NULL,
    original_value  BYTEA NOT NULL,
    mapping_type    VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_pii_issue_key 
        FOREIGN KEY (issue_key) REFERENCES kb_entries(issue_key) ON DELETE CASCADE,
    CONSTRAINT chk_pii_mapping_type 
        CHECK (mapping_type IN ('NAME', 'ID_CARD', 'PHONE', 'BANK_ACCOUNT', 'EMAIL'))
);

CREATE INDEX idx_pii_mapping_issue_key 
    ON pii_mapping (issue_key);

COMMENT ON TABLE pii_mapping IS 'PII placeholder-to-encrypted-original mapping (MTO-26)';
COMMENT ON COLUMN pii_mapping.placeholder IS 'Placeholder token e.g. [PII_NAME_01]';
COMMENT ON COLUMN pii_mapping.original_value IS 'AES-256-GCM encrypted original PII value';
COMMENT ON COLUMN pii_mapping.mapping_type IS 'PII category: NAME, ID_CARD, PHONE, BANK_ACCOUNT, EMAIL';
```

## 3. Security Design

### 3.1 Encryption Strategy

| Aspect | Decision |
|--------|----------|
| Algorithm | AES-256-GCM (authenticated encryption) |
| Key Size | 256 bits (32 bytes) |
| IV/Nonce | 12 bytes, random per operation |
| Tag Length | 128 bits |
| Key Storage | Environment variable `KB_ENCRYPTION_KEY` |
| Key Format | Base64-encoded 32-byte key |
| Encryption Layer | Application (not database) |

### 3.2 Key Management

- Key loaded once at startup via `KbStoreConfig`
- Key NEVER logged or serialized
- Key rotation: future MTO-31 (re-encrypt all data with new key)
- If key missing → application fails to start (fail-fast)

### 3.3 Data at Rest

| Column | Encryption | Format in DB |
|--------|-----------|--------------|
| public_content | None | TEXT (plaintext) |
| technical_content | None | TEXT (plaintext) |
| business_rules | AES-256-GCM | BYTEA (iv + ciphertext) |
| masked_full | None | TEXT (already masked) |
| pii_mapping.original_value | AES-256-GCM | BYTEA (iv + ciphertext) |

## 4. Error Handling

### 4.1 Exception Hierarchy

```kotlin
sealed class KbStoreException(message: String, cause: Throwable? = null) 
    : Exception(message, cause) {
    
    class ConfigException(message: String) : KbStoreException(message)
    class EncryptionException(message: String, cause: Throwable? = null) 
        : KbStoreException(message, cause)
    class RepositoryException(message: String, cause: Throwable? = null) 
        : KbStoreException(message, cause)
    class ValidationException(message: String) : KbStoreException(message)
}
```

### 4.2 Error Handling Strategy

| Scenario | Action |
|----------|--------|
| Encryption key missing | Throw ConfigException at startup |
| Encryption fails | Throw EncryptionException, do NOT persist |
| Decryption fails (wrong key) | Throw EncryptionException, log warning |
| DB connection timeout | Throw RepositoryException, caller retries |
| Constraint violation (duplicate) | ON CONFLICT handles gracefully |
| Invalid sensitivity level | Throw ValidationException |

## 5. Implementation Checklist

### Files to Create

| # | File | Package | Lines (est.) |
|---|------|---------|-------------|
| 1 | `KbStoreConfig.kt` | kbstore/config | ~15 |
| 2 | `KbEntry.kt` | kbstore/model | ~20 |
| 3 | `PiiMapping.kt` | kbstore/model | ~15 |
| 4 | `BrSensitivityLevel.kt` | kbstore/model | ~15 |
| 5 | `MappingType.kt` | kbstore/model | ~8 |
| 6 | `KbStoreException.kt` | kbstore/model | ~15 |
| 7 | `EncryptionService.kt` | kbstore/encryption | ~8 |
| 8 | `EncryptionServiceImpl.kt` | kbstore/encryption | ~50 |
| 9 | `KbEntryRepository.kt` | kbstore/repository | ~15 |
| 10 | `KbEntryRepositoryImpl.kt` | kbstore/repository | ~180 |
| 11 | `PiiMappingRepository.kt` | kbstore/repository | ~10 |
| 12 | `PiiMappingRepositoryImpl.kt` | kbstore/repository | ~150 |
| 13 | `KbStoreModule.kt` | kbstore/di | ~20 |
| 14 | `V6__create_kb_entries.sql` | resources/db/migration | ~35 |
| 15 | `V7__create_pii_mapping.sql` | resources/db/migration | ~25 |

### Files to Modify

| # | File | Change |
|---|------|--------|
| 1 | `OrchestratorConfig.kt` | Add `kbstore: KbStoreConfig` field |
| 2 | `AppModule.kt` | Include `kbStoreModule` |
| 3 | `application.yml` | Add `kbstore` config section |

## 6. Testing Strategy

| Level | What to Test | Framework |
|-------|-------------|-----------|
| Unit | EncryptionServiceImpl (encrypt/decrypt roundtrip) | Kotest + JUnit 5 |
| Unit | BrSensitivityLevel.fromLevel() | Kotest |
| Unit | Repository SQL generation (mock DataSource) | Kotest + MockK |
| Integration | Full CRUD with real PostgreSQL | Testcontainers + Kotest |
| Integration | Batch upsert with transaction rollback | Testcontainers |
| Integration | FK cascade delete (KB entry → PII mappings) | Testcontainers |

## 7. Performance Considerations

| Operation | Expected Latency | Optimization |
|-----------|-----------------|--------------|
| Single upsert | < 10ms | Prepared statement reuse |
| Batch 100 entries | < 500ms | executeBatch() + single commit |
| Encrypt single field | < 1ms | In-memory crypto |
| Index lookup by issue_key | < 1ms | UNIQUE index |
| Index lookup by project_key | < 5ms | B-tree index |

## 8. Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Architecture | [architecture.png](diagrams/architecture.png) | [architecture.drawio](diagrams/architecture.drawio) |
| 2 | Component | [component.png](diagrams/component.png) | [component.drawio](diagrams/component.drawio) |
| 3 | Class Diagram | [class-diagram.png](diagrams/class-diagram.png) | [class-diagram.drawio](diagrams/class-diagram.drawio) |

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Tech Lead | | | Pending |
| Security | | | Pending |
