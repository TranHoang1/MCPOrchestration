# Software Test Cases (STC)

## MTO-26: KB Refinery — KB Entries Schema 4 Columns + Migration

| Field | Value |
|-------|-------|
| **Ticket** | MTO-26 |
| **Version** | 1.0 |
| **Author** | QA Agent |
| **Created** | 2026-05-08 |

---

## 1. Unit Tests (UT)

### UT-01: KbEntry data class creation

| Field | Value |
|-------|-------|
| **ID** | UT-01 |
| **Level** | Unit |
| **Component** | KbEntry |
| **Precondition** | None |

**Steps:**
1. Create KbEntry with all fields populated
2. Verify all fields accessible
3. Verify default values (id=random UUID, brSensitivityLevel=INTERNAL)

**Expected:** KbEntry created with correct defaults

---

### UT-02: PiiMapping data class creation

| Field | Value |
|-------|-------|
| **ID** | UT-02 |
| **Level** | Unit |
| **Component** | PiiMapping |

**Steps:**
1. Create PiiMapping with all fields
2. Verify mappingType enum value correct
3. Verify createdAt defaults to now

**Expected:** PiiMapping created correctly

---

### UT-03: BrSensitivityLevel.fromLevel valid values

| Field | Value |
|-------|-------|
| **ID** | UT-03 |
| **Level** | Unit |
| **Component** | BrSensitivityLevel |

**Steps:**
1. Call fromLevel(1) → CONFIDENTIAL
2. Call fromLevel(2) → INTERNAL
3. Call fromLevel(3) → RESTRICTED
4. Call fromLevel(0) → throws IllegalArgumentException
5. Call fromLevel(4) → throws IllegalArgumentException

**Expected:** Valid levels map correctly, invalid throw exception

---

### UT-04: EncryptionService encrypt produces valid output

| Field | Value |
|-------|-------|
| **ID** | UT-04 |
| **Level** | Unit |
| **Component** | EncryptionServiceImpl |

**Steps:**
1. Create EncryptionServiceImpl with valid 32-byte Base64 key
2. Encrypt "Hello World"
3. Verify output length > 12 (IV) + input length
4. Verify output starts with different bytes each call (random IV)

**Expected:** Encrypted output has IV prefix, non-deterministic

---

### UT-05: EncryptionService roundtrip

| Field | Value |
|-------|-------|
| **ID** | UT-05 |
| **Level** | Unit |
| **Component** | EncryptionServiceImpl |

**Steps:**
1. Encrypt plaintext "Sensitive business rule: interest rate = 5.5%"
2. Decrypt the result
3. Compare with original

**Expected:** decrypt(encrypt(x)) == x

---

### UT-06: EncryptionService rejects empty key

| Field | Value |
|-------|-------|
| **ID** | UT-06 |
| **Level** | Unit |
| **Component** | EncryptionServiceImpl |

**Steps:**
1. Create EncryptionServiceImpl with empty string key
2. Expect ConfigException thrown

**Expected:** ConfigException with message about empty key

---

### UT-07: EncryptionService rejects wrong key size

| Field | Value |
|-------|-------|
| **ID** | UT-07 |
| **Level** | Unit |
| **Component** | EncryptionServiceImpl |

**Steps:**
1. Create EncryptionServiceImpl with 16-byte key (Base64)
2. Expect ConfigException thrown

**Expected:** ConfigException with message about key size

---

### UT-08: EncryptionService decrypt with wrong key fails

| Field | Value |
|-------|-------|
| **ID** | UT-08 |
| **Level** | Unit |
| **Component** | EncryptionServiceImpl |

**Steps:**
1. Encrypt with key A
2. Try decrypt with key B
3. Expect EncryptionException

**Expected:** EncryptionException thrown (GCM tag mismatch)

---

### UT-09: MappingType enum completeness

| Field | Value |
|-------|-------|
| **ID** | UT-09 |
| **Level** | Unit |
| **Component** | MappingType |

**Steps:**
1. Verify MappingType.entries.size == 5
2. Verify contains: NAME, ID_CARD, PHONE, BANK_ACCOUNT, EMAIL

**Expected:** All 5 types present

---

### UT-10: KbEntryRepository upsert calls encryption

| Field | Value |
|-------|-------|
| **ID** | UT-10 |
| **Level** | Unit |
| **Component** | KbEntryRepositoryImpl |

**Steps:**
1. Mock DataSource and EncryptionService
2. Call upsert with entry having businessRules = "secret"
3. Verify encryptionService.encrypt("secret") was called

**Expected:** Encryption called before DB persist

---

### UT-11: KbEntryRepository upsert with null businessRules

| Field | Value |
|-------|-------|
| **ID** | UT-11 |
| **Level** | Unit |
| **Component** | KbEntryRepositoryImpl |

**Steps:**
1. Mock DataSource and EncryptionService
2. Call upsert with entry having businessRules = null
3. Verify encryptionService.encrypt() NOT called
4. Verify setNull() called for business_rules column

**Expected:** Null business_rules → no encryption, setNull in SQL

---

### UT-12: PiiMappingRepository insertBatch encrypts values

| Field | Value |
|-------|-------|
| **ID** | UT-12 |
| **Level** | Unit |
| **Component** | PiiMappingRepositoryImpl |

**Steps:**
1. Mock DataSource and EncryptionService
2. Call insertBatch with 3 mappings
3. Verify encryptionService.encrypt() called 3 times

**Expected:** Each mapping's originalValue encrypted

---

### UT-13: KbStoreConfig defaults

| Field | Value |
|-------|-------|
| **ID** | UT-13 |
| **Level** | Unit |
| **Component** | KbStoreConfig |

**Steps:**
1. Create KbStoreConfig with only encryptionKey
2. Verify batchSize defaults to 500

**Expected:** Default batch size = 500

---

### UT-14: KbStoreException hierarchy

| Field | Value |
|-------|-------|
| **ID** | UT-14 |
| **Level** | Unit |
| **Component** | KbStoreException |

**Steps:**
1. Create each exception subtype
2. Verify all are instances of KbStoreException
3. Verify message and cause propagation

**Expected:** Sealed hierarchy works correctly

---

### UT-15: KbEntryRepository batch empty list

| Field | Value |
|-------|-------|
| **ID** | UT-15 |
| **Level** | Unit |
| **Component** | KbEntryRepositoryImpl |

**Steps:**
1. Call upsertBatch with empty list
2. Verify returns 0
3. Verify no DB interaction

**Expected:** Early return 0, no DB call

---

### UT-16: PiiMappingRepository insertBatch empty list

| Field | Value |
|-------|-------|
| **ID** | UT-16 |
| **Level** | Unit |
| **Component** | PiiMappingRepositoryImpl |

**Steps:**
1. Call insertBatch with empty list
2. Verify returns 0

**Expected:** Early return 0

---

### UT-17: EncryptionService decrypt too-short ciphertext

| Field | Value |
|-------|-------|
| **ID** | UT-17 |
| **Level** | Unit |
| **Component** | EncryptionServiceImpl |

**Steps:**
1. Call decrypt with ByteArray of size 5 (< 12 IV bytes)
2. Expect EncryptionException

**Expected:** EncryptionException thrown

---

### UT-18: KbStoreModule Koin wiring

| Field | Value |
|-------|-------|
| **ID** | UT-18 |
| **Level** | Unit |
| **Component** | KbStoreModule |

**Steps:**
1. Start Koin with kbStoreModule + mock dependencies
2. Verify EncryptionService resolvable
3. Verify KbEntryRepository resolvable
4. Verify PiiMappingRepository resolvable

**Expected:** All bindings resolve correctly

---

## 2. Integration Tests (IT)

### IT-01: Full KB entry upsert + read roundtrip

| Field | Value |
|-------|-------|
| **ID** | IT-01 |
| **Level** | Integration |
| **Component** | KbEntryRepositoryImpl + PostgreSQL |
| **Precondition** | Testcontainers PostgreSQL running, V6 migration applied |

**Steps:**
1. Create KbEntry with all fields populated
2. Call upsert(entry)
3. Call findByIssueKey(entry.issueKey)
4. Verify all fields match (including decrypted businessRules)

**Expected:** Full roundtrip preserves all data, businessRules decrypted correctly

---

### IT-02: KB entry update via upsert (ON CONFLICT)

| Field | Value |
|-------|-------|
| **ID** | IT-02 |
| **Level** | Integration |
| **Component** | KbEntryRepositoryImpl |

**Steps:**
1. Upsert entry with issueKey="TEST-1"
2. Upsert again with same issueKey but different publicContent
3. FindByIssueKey("TEST-1")
4. Verify publicContent updated
5. Verify updated_at changed

**Expected:** ON CONFLICT updates existing row

---

### IT-03: Business rules encryption verified in DB

| Field | Value |
|-------|-------|
| **ID** | IT-03 |
| **Level** | Integration |
| **Component** | KbEntryRepositoryImpl + EncryptionService |

**Steps:**
1. Upsert entry with businessRules = "Interest rate formula: base + 2.5%"
2. Query DB directly (raw JDBC): SELECT business_rules FROM kb_entries
3. Verify raw bytes are NOT plaintext (cannot decode as UTF-8 to original)
4. Call findByIssueKey → verify businessRules decrypted correctly

**Expected:** DB stores encrypted bytes, repository returns decrypted string

---

### IT-04: Unique constraint on issue_key

| Field | Value |
|-------|-------|
| **ID** | IT-04 |
| **Level** | Integration |
| **Component** | kb_entries table |

**Steps:**
1. Insert entry with issueKey="UNIQUE-1" via raw SQL (not upsert)
2. Try INSERT again with same issueKey
3. Expect constraint violation

**Expected:** UNIQUE constraint enforced

---

### IT-05: br_sensitivity_level CHECK constraint

| Field | Value |
|-------|-------|
| **ID** | IT-05 |
| **Level** | Integration |
| **Component** | kb_entries table |

**Steps:**
1. Try INSERT with br_sensitivity_level = 0 via raw SQL
2. Expect CHECK constraint violation
3. Try INSERT with br_sensitivity_level = 4
4. Expect CHECK constraint violation
5. INSERT with br_sensitivity_level = 1, 2, 3 → all succeed

**Expected:** Only values 1, 2, 3 accepted

---

### IT-06: PII mapping linked to KB entry

| Field | Value |
|-------|-------|
| **ID** | IT-06 |
| **Level** | Integration |
| **Component** | PiiMappingRepositoryImpl |

**Steps:**
1. Create KB entry with issueKey="PII-TEST-1"
2. Create PiiMapping with same issueKey
3. Call insertBatch
4. Call findByIssueKey("PII-TEST-1")
5. Verify mapping returned with decrypted originalValue

**Expected:** PII mapping persisted and retrievable

---

### IT-07: PII original_value encrypted in DB

| Field | Value |
|-------|-------|
| **ID** | IT-07 |
| **Level** | Integration |
| **Component** | PiiMappingRepositoryImpl + EncryptionService |

**Steps:**
1. Insert PII mapping with originalValue = "Nguyen Van A"
2. Query DB directly: SELECT original_value FROM pii_mapping
3. Verify raw bytes are NOT "Nguyen Van A" in UTF-8
4. Call findByIssueKey → verify originalValue = "Nguyen Van A"

**Expected:** DB stores encrypted, repository returns decrypted

---

### IT-08: Query all PII mappings for issue_key

| Field | Value |
|-------|-------|
| **ID** | IT-08 |
| **Level** | Integration |
| **Component** | PiiMappingRepositoryImpl |

**Steps:**
1. Create KB entry
2. Insert 5 PII mappings for same issueKey (different types)
3. Call findByIssueKey
4. Verify 5 mappings returned
5. Verify each has correct mappingType

**Expected:** All 5 mappings returned with correct types

---

### IT-09: Content hash lookup

| Field | Value |
|-------|-------|
| **ID** | IT-09 |
| **Level** | Integration |
| **Component** | KbEntryRepositoryImpl |

**Steps:**
1. Upsert entry with contentHash = "abc123" and projectKey = "PROJ"
2. Call findByContentHash("PROJ", "abc123")
3. Verify entry found
4. Call findByContentHash("PROJ", "xyz789")
5. Verify null returned

**Expected:** Hash lookup works for change detection

---

### IT-10: Update last_synced_at

| Field | Value |
|-------|-------|
| **ID** | IT-10 |
| **Level** | Integration |
| **Component** | KbEntryRepositoryImpl |

**Steps:**
1. Upsert entry (lastSyncedAt = null initially)
2. Call updateLastSyncedAt with current time
3. FindByIssueKey → verify lastSyncedAt updated

**Expected:** last_synced_at field updated correctly

---

### IT-11: Batch upsert with transaction

| Field | Value |
|-------|-------|
| **ID** | IT-11 |
| **Level** | Integration |
| **Component** | KbEntryRepositoryImpl |

**Steps:**
1. Create 10 KbEntry objects with different issueKeys
2. Call upsertBatch(entries)
3. Verify return value = 10
4. Query each by issueKey → all exist

**Expected:** All 10 entries persisted in single transaction

---

### IT-12: Cascade delete (KB entry → PII mappings)

| Field | Value |
|-------|-------|
| **ID** | IT-12 |
| **Level** | Integration |
| **Component** | kb_entries + pii_mapping FK |

**Steps:**
1. Create KB entry with issueKey="CASCADE-1"
2. Insert 3 PII mappings for "CASCADE-1"
3. Delete KB entry: repository.delete("CASCADE-1")
4. Query pii_mapping for "CASCADE-1"
5. Verify 0 mappings (cascaded)

**Expected:** FK ON DELETE CASCADE removes PII mappings

---

## 3. Property-Based Tests (PBT)

### PBT-01: Encryption roundtrip for arbitrary strings

| Field | Value |
|-------|-------|
| **ID** | PBT-01 |
| **Level** | PBT |
| **Component** | EncryptionServiceImpl |

**Property:** For any non-empty string s: decrypt(encrypt(s)) == s

**Generator:** Arb.string(1..10000)

**Iterations:** 1000

---

### PBT-02: Encryption produces unique ciphertexts

| Field | Value |
|-------|-------|
| **ID** | PBT-02 |
| **Level** | PBT |
| **Component** | EncryptionServiceImpl |

**Property:** encrypt(s) != encrypt(s) for same input (random IV)

**Generator:** Arb.string(1..100)

**Iterations:** 100

---

### PBT-03: BrSensitivityLevel roundtrip

| Field | Value |
|-------|-------|
| **ID** | PBT-03 |
| **Level** | PBT |
| **Component** | BrSensitivityLevel |

**Property:** For any level in entries: fromLevel(level.level) == level

**Generator:** Arb.enum<BrSensitivityLevel>()

**Iterations:** 100

---

### PBT-04: MappingType valueOf roundtrip

| Field | Value |
|-------|-------|
| **ID** | PBT-04 |
| **Level** | PBT |
| **Component** | MappingType |

**Property:** For any type in entries: MappingType.valueOf(type.name) == type

**Generator:** Arb.enum<MappingType>()

**Iterations:** 100

---

## 4. Test Execution Order

1. UT-01 → UT-18 (unit tests, no external deps)
2. PBT-01 → PBT-04 (property tests, no external deps)
3. IT-01 → IT-12 (integration tests, require Testcontainers)

## 5. Test Data

| Data | Value | Used In |
|------|-------|---------|
| Valid encryption key | `dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcw==` (32 bytes Base64) | All encryption tests |
| Sample issue_key | "MTO-26-TEST" | IT-01 to IT-12 |
| Sample project_key | "MTO" | IT-01 to IT-12 |
| Sample PII name | "Nguyen Van A" | IT-06, IT-07 |
| Sample business rule | "Interest rate = base + 2.5%" | IT-03 |

---
