# Functional Specification Document (FSD)

## MTO-26: KB Refinery — KB Entries Schema 4 Columns + Migration

| Field | Value |
|-------|-------|
| **Ticket** | MTO-26 |
| **Epic** | MTO-24 (Knowledge Base Refinery) |
| **Version** | 1.0 |
| **Status** | Draft |
| **Author** | BA Agent + TA Agent |
| **Created** | 2026-05-08 |
| **Related BRD** | BRD-v1-MTO-26.docx |

---

## 1. Overview

This FSD specifies the functional behavior of the KB Entries Schema system, including database tables, domain models, repository operations, encryption logic, and DI configuration.

## 2. Use Cases

### 2.1 UC-1: Store KB Entry with 4 Content Layers

| Field | Value |
|-------|-------|
| **ID** | UC-1 |
| **Actor** | Sync Service |
| **Precondition** | Database initialized, encryption key configured |
| **Postcondition** | KB entry persisted with encrypted business_rules |

#### Main Flow

| Step | Actor | System |
|------|-------|--------|
| 1 | Sync Service sends KB entry data | — |
| 2 | — | Validate issue_key not empty |
| 3 | — | Compute content_hash from all content fields |
| 4 | — | Encrypt business_rules with AES-256-GCM |
| 5 | — | Upsert entry into kb_entries table |
| 6 | — | Return success with entry ID |

#### Alternative Flow

| ID | Condition | Steps |
|----|-----------|-------|
| AF-1.1 | Entry already exists (same issue_key) | Update existing entry, increment version |
| AF-1.2 | content_hash unchanged | Skip update, return existing entry |

#### Exception Flow

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1.1 | Encryption key not configured | Throw ConfigException |
| EF-1.2 | Database connection failed | Throw RepositoryException, rollback |
| EF-1.3 | Invalid br_sensitivity_level | Throw ValidationException |

### 2.2 UC-2: Encrypt/Decrypt Business Rules

| Field | Value |
|-------|-------|
| **ID** | UC-2 |
| **Actor** | System (internal) |
| **Precondition** | Encryption key available in config |
| **Postcondition** | Data encrypted/decrypted successfully |

#### Main Flow

| Step | Actor | System |
|------|-------|--------|
| 1 | Caller provides plaintext | — |
| 2 | — | Generate random 12-byte IV (nonce) |
| 3 | — | Encrypt with AES-256-GCM using key + IV |
| 4 | — | Prepend IV to ciphertext |
| 5 | — | Return IV + ciphertext as ByteArray |

#### Decrypt Flow

| Step | Actor | System |
|------|-------|--------|
| 1 | Caller provides encrypted ByteArray | — |
| 2 | — | Extract first 12 bytes as IV |
| 3 | — | Extract remaining bytes as ciphertext |
| 4 | — | Decrypt with AES-256-GCM using key + IV |
| 5 | — | Return plaintext String |

### 2.3 UC-3: Manage PII Mappings

| Field | Value |
|-------|-------|
| **ID** | UC-3 |
| **Actor** | Sync Service |
| **Precondition** | KB entry exists for the issue_key |
| **Postcondition** | PII mappings stored with encrypted original values |

#### Main Flow

| Step | Actor | System |
|------|-------|--------|
| 1 | Sync Service sends PII mapping list | — |
| 2 | — | Validate each mapping has placeholder + original_value + type |
| 3 | — | Encrypt each original_value with AES-256-GCM |
| 4 | — | Insert all mappings in batch |
| 5 | — | Return count of inserted mappings |

#### Alternative Flow

| ID | Condition | Steps |
|----|-----------|-------|
| AF-3.1 | Mappings already exist for issue_key | Delete existing, insert new (replace strategy) |

### 2.4 UC-4: Detect Content Changes

| Field | Value |
|-------|-------|
| **ID** | UC-4 |
| **Actor** | Sync Service |
| **Precondition** | KB entry may or may not exist |
| **Postcondition** | Change detection result returned |

#### Main Flow

| Step | Actor | System |
|------|-------|--------|
| 1 | Sync Service provides issue_key + new content_hash | — |
| 2 | — | Query existing entry by issue_key |
| 3 | — | Compare stored content_hash with new hash |
| 4 | — | Return changed=true/false |

### 2.5 UC-5: Query KB Entries by Project

| Field | Value |
|-------|-------|
| **ID** | UC-5 |
| **Actor** | Admin / Query Service |
| **Precondition** | Entries exist for project |
| **Postcondition** | List of entries returned (business_rules decrypted if authorized) |

#### Main Flow

| Step | Actor | System |
|------|-------|--------|
| 1 | Caller provides project_key | — |
| 2 | — | Query all entries WHERE project_key = ? |
| 3 | — | Return list (business_rules remains encrypted in DB result) |

### 2.6 UC-6: Batch Upsert Entries

| Field | Value |
|-------|-------|
| **ID** | UC-6 |
| **Actor** | Sync Service |
| **Precondition** | Multiple entries ready for persistence |
| **Postcondition** | All entries upserted in single transaction |

#### Main Flow

| Step | Actor | System |
|------|-------|--------|
| 1 | Sync Service provides list of KB entries | — |
| 2 | — | Begin transaction |
| 3 | — | For each entry: encrypt business_rules, compute hash |
| 4 | — | Execute batch upsert (ON CONFLICT DO UPDATE) |
| 5 | — | Commit transaction |
| 6 | — | Return count of affected rows |

#### Exception Flow

| ID | Condition | Steps |
|----|-----------|-------|
| EF-6.1 | Any entry fails validation | Rollback entire batch |
| EF-6.2 | Database error mid-batch | Rollback, throw RepositoryException |

## 3. Data Specifications

### 3.1 Database Schema — kb_entries

```sql
CREATE TABLE kb_entries (
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
    last_synced_at       TIMESTAMP
);
```

**Indexes:**
- `idx_kb_entries_issue_key` — UNIQUE on issue_key (implicit from constraint)
- `idx_kb_entries_project_key` — B-tree on project_key
- `idx_kb_entries_content_hash` — B-tree on (project_key, content_hash)

### 3.2 Database Schema — pii_mapping

```sql
CREATE TABLE pii_mapping (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_key       VARCHAR(50) NOT NULL,
    placeholder     VARCHAR(50) NOT NULL,
    original_value  BYTEA NOT NULL,
    mapping_type    VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_pii_issue_key 
        FOREIGN KEY (issue_key) REFERENCES kb_entries(issue_key) ON DELETE CASCADE,
    CONSTRAINT chk_mapping_type 
        CHECK (mapping_type IN ('NAME', 'ID_CARD', 'PHONE', 'BANK_ACCOUNT', 'EMAIL'))
);
```

**Indexes:**
- `idx_pii_mapping_issue_key` — B-tree on issue_key

### 3.3 Domain Models

#### KbEntry

```kotlin
data class KbEntry(
    val id: UUID = UUID.randomUUID(),
    val issueKey: String,
    val projectKey: String,
    val publicContent: String? = null,
    val technicalContent: String? = null,
    val businessRules: String? = null,       // plaintext (encrypted at repo layer)
    val maskedFull: String? = null,
    val brSensitivityLevel: BrSensitivityLevel = BrSensitivityLevel.INTERNAL,
    val contentHash: String,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val lastSyncedAt: Instant? = null
)
```

#### PiiMapping

```kotlin
data class PiiMapping(
    val id: UUID = UUID.randomUUID(),
    val issueKey: String,
    val placeholder: String,
    val originalValue: String,               // plaintext (encrypted at repo layer)
    val mappingType: MappingType,
    val createdAt: Instant = Clock.System.now()
)
```

#### BrSensitivityLevel (Enum)

```kotlin
enum class BrSensitivityLevel(val level: Int) {
    CONFIDENTIAL(1),
    INTERNAL(2),
    RESTRICTED(3);
    
    companion object {
        fun fromLevel(level: Int): BrSensitivityLevel =
            entries.first { it.level == level }
    }
}
```

#### MappingType (Enum)

```kotlin
enum class MappingType {
    NAME, ID_CARD, PHONE, BANK_ACCOUNT, EMAIL
}
```

### 3.4 API Contracts — Repository Interfaces

#### KbEntryRepository

```kotlin
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

#### PiiMappingRepository

```kotlin
interface PiiMappingRepository {
    suspend fun insertBatch(mappings: List<PiiMapping>): Int
    suspend fun findByIssueKey(issueKey: String): List<PiiMapping>
    suspend fun deleteByIssueKey(issueKey: String): Int
    suspend fun replaceForIssueKey(issueKey: String, mappings: List<PiiMapping>): Int
}
```

#### EncryptionService

```kotlin
interface EncryptionService {
    fun encrypt(plaintext: String): ByteArray
    fun decrypt(ciphertext: ByteArray): String
    fun encryptBytes(data: ByteArray): ByteArray
    fun decryptBytes(data: ByteArray): ByteArray
}
```

### 3.5 API Contracts — Method Signatures (Detailed)

#### KbEntryRepositoryImpl.upsert

```
Input:  KbEntry (domain model, businessRules in plaintext)
Process:
  1. Encrypt entry.businessRules → ByteArray (if not null)
  2. Execute UPSERT SQL with ON CONFLICT (issue_key) DO UPDATE
  3. Set updated_at = NOW() on conflict
Output: Unit (void)
Errors: RepositoryException on DB failure
```

#### KbEntryRepositoryImpl.upsertBatch

```
Input:  List<KbEntry> (max 500 per batch)
Process:
  1. Begin transaction
  2. For each entry: encrypt businessRules, addBatch()
  3. executeBatch()
  4. Commit
Output: Int (number of affected rows)
Errors: RepositoryException (rollback on any failure)
```

#### PiiMappingRepositoryImpl.replaceForIssueKey

```
Input:  issueKey: String, mappings: List<PiiMapping>
Process:
  1. Begin transaction
  2. DELETE FROM pii_mapping WHERE issue_key = ?
  3. For each mapping: encrypt originalValue, INSERT
  4. Commit
Output: Int (number of inserted rows)
Errors: RepositoryException (rollback on any failure)
```

#### EncryptionServiceImpl.encrypt

```
Input:  plaintext: String (UTF-8)
Process:
  1. Generate 12-byte random IV (SecureRandom)
  2. Create GCM spec: GCMParameterSpec(128, iv)
  3. Init Cipher with AES/GCM/NoPadding, ENCRYPT_MODE
  4. cipher.doFinal(plaintext.toByteArray(UTF_8))
  5. Concatenate: iv (12 bytes) + ciphertext
Output: ByteArray (iv + ciphertext)
Errors: EncryptionException on crypto failure
```

## 4. Business Rules

| ID | Rule | Implementation |
|----|------|----------------|
| BR-1 | issue_key unique per entry | UNIQUE constraint + ON CONFLICT |
| BR-2 | business_rules encrypted before persist | EncryptionService.encrypt() in repo impl |
| BR-3 | pii original_value encrypted before persist | EncryptionService.encrypt() in repo impl |
| BR-4 | Encryption key from env/config | KbStoreConfig.encryptionKey from YAML/env |
| BR-5 | br_sensitivity_level ∈ {1, 2, 3} | CHECK constraint + enum validation |
| BR-6 | Cascade delete PII on KB entry delete | FK ON DELETE CASCADE |
| BR-7 | content_hash = SHA-256(public + technical + business_rules) | Computed before persist |
| BR-8 | Batch size max 500 | Validated in repository |

## 5. Integration Requirements

### 5.1 Database Integration

| Property | Value |
|----------|-------|
| Database | PostgreSQL 16+ |
| Connection Pool | HikariCP (existing) |
| Migration | Flyway V6, V7 |
| Schema | Default (public) |

### 5.2 Configuration Integration

```yaml
orchestrator:
  kbstore:
    encryption_key: ${KB_ENCRYPTION_KEY}  # 32-byte base64-encoded key
    batch_size: 500
    connection_timeout_ms: 5000
```

### 5.3 DI Integration (Koin)

```kotlin
val kbStoreModule = module {
    single<EncryptionService> { EncryptionServiceImpl(get<KbStoreConfig>().encryptionKey) }
    single<KbEntryRepository> { KbEntryRepositoryImpl(get(), get()) }
    single<PiiMappingRepository> { PiiMappingRepositoryImpl(get(), get()) }
}
```

## 6. Error Handling

| Error Code | Exception | Cause | Recovery |
|------------|-----------|-------|----------|
| KB-001 | ConfigException | Encryption key missing/invalid | Check env var KB_ENCRYPTION_KEY |
| KB-002 | RepositoryException | DB connection failed | Retry with backoff |
| KB-003 | RepositoryException | Constraint violation | Log + skip duplicate |
| KB-004 | EncryptionException | Decrypt failed (wrong key) | Alert admin, check key rotation |
| KB-005 | ValidationException | Invalid sensitivity level | Reject input |

## 7. Non-Functional Requirements

| ID | Requirement | Target | Measurement |
|----|-------------|--------|-------------|
| NFR-1 | Single CRUD latency | < 50ms p99 | Benchmark test |
| NFR-2 | Batch 100 entries | < 2s p99 | Benchmark test |
| NFR-3 | Encryption overhead | < 5ms per operation | Micro-benchmark |
| NFR-4 | Connection pool size | 5-10 connections | HikariCP config |
| NFR-5 | Memory per entry | < 10KB | Profiling |

## 8. Open Issues

| ID | Issue | Owner | Status |
|----|-------|-------|--------|
| OI-1 | Key rotation strategy (how to re-encrypt existing data) | Security Team | Deferred to MTO-31 |
| OI-2 | content_hash algorithm (SHA-256 vs xxHash for speed) | Dev Team | Decided: SHA-256 |
| OI-3 | RLS policy integration points | DBA | Deferred to MTO-31 |

## 9. Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | System Context | [system-context.png](diagrams/system-context.png) | [system-context.drawio](diagrams/system-context.drawio) |
| 2 | Sequence — Store Entry | [sequence-store-entry.png](diagrams/sequence-store-entry.png) | [sequence-store-entry.drawio](diagrams/sequence-store-entry.drawio) |
| 3 | State — KB Entry Lifecycle | [state-kb-entry.png](diagrams/state-kb-entry.png) | [state-kb-entry.drawio](diagrams/state-kb-entry.drawio) |

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product Owner | | | Pending |
| Tech Lead | | | Pending |
