# Software Test Plan (STP)

## MTO-26: KB Refinery — KB Entries Schema 4 Columns + Migration

| Field | Value |
|-------|-------|
| **Ticket** | MTO-26 |
| **Version** | 1.0 |
| **Author** | QA Agent |
| **Created** | 2026-05-08 |
| **Related Docs** | BRD-v1-MTO-26, FSD-v1-MTO-26, TDD-v1-MTO-26 |

---

## 1. Test Scope

### 1.1 In Scope

| Component | Test Focus |
|-----------|-----------|
| EncryptionServiceImpl | AES-256-GCM encrypt/decrypt correctness |
| KbEntryRepositoryImpl | CRUD operations, upsert, batch, encryption integration |
| PiiMappingRepositoryImpl | CRUD, batch, cascade delete, encryption |
| BrSensitivityLevel | Enum validation, fromLevel() |
| MappingType | Enum completeness |
| V6 Migration | Table creation, constraints, indexes |
| V7 Migration | Table creation, FK, constraints |
| KbStoreModule (DI) | Koin module wiring |

### 1.2 Out of Scope

- RLS policies (MTO-31)
- KB ingestion pipeline (MTO-27)
- Performance/load testing (separate ticket)
- UI testing (no UI in this ticket)

## 2. Test Strategy

### 2.1 Test Levels

| Level | Count | Automation | Framework |
|-------|-------|-----------|-----------|
| Unit Test (UT) | 18 | 100% | Kotest + MockK |
| Integration Test (IT) | 12 | 100% | Kotest + Testcontainers (PostgreSQL) |
| Property-Based Test (PBT) | 4 | 100% | Kotest Property Testing |
| Total | 34 | 100% | — |

### 2.2 Test Level Definitions

| Level | What | How | Dependencies |
|-------|------|-----|-------------|
| **UT** | Individual class logic in isolation | Mock all dependencies | MockK |
| **IT** | Repository + DB + Encryption together | Real PostgreSQL via Testcontainers | Testcontainers, HikariCP |
| **PBT** | Encryption roundtrip, enum boundaries | Random input generation | Kotest Arb |

## 3. Test Environment

| Component | Specification |
|-----------|--------------|
| JDK | 21 |
| Kotlin | 2.3.20 |
| Database | PostgreSQL 16 (Testcontainers) |
| Test Framework | Kotest 5.9.1 |
| Mocking | MockK 1.14.2 |
| Containers | Testcontainers 1.21.1 |
| Build | Gradle (./gradlew test) |

## 4. Requirements Traceability Matrix (RTM)

| Requirement | Test Cases | Coverage |
|-------------|-----------|----------|
| AC-1.1: Insert KB entry with 4 columns | UT-01, IT-01 | ✅ |
| AC-1.2: public_content accessible | IT-01, IT-02 | ✅ |
| AC-1.3: technical_content accessible | IT-01, IT-02 | ✅ |
| AC-1.4: business_rules encrypted | UT-05, IT-03 | ✅ |
| AC-1.5: masked_full accessible | IT-01, IT-02 | ✅ |
| AC-1.6: issue_key unique | IT-04 | ✅ |
| AC-1.7: br_sensitivity_level validation | UT-03, IT-05 | ✅ |
| AC-2.1: Encrypt before persist | IT-03 | ✅ |
| AC-2.2: Decrypt on read | IT-03 | ✅ |
| AC-2.3: Key from config/env | UT-06 | ✅ |
| AC-2.4: Key not hardcoded | UT-07 | ✅ |
| AC-2.5: AES-256-GCM | UT-04, PBT-01 | ✅ |
| AC-3.1: PII linked to KB entry | IT-06 | ✅ |
| AC-3.2: PII original_value encrypted | IT-07 | ✅ |
| AC-3.3: Placeholder format | UT-08 | ✅ |
| AC-3.4: Mapping types supported | UT-09 | ✅ |
| AC-3.5: Query PII by issue_key | IT-08 | ✅ |
| AC-4.1: content_hash computed | UT-10 | ✅ |
| AC-4.2: Query by hash | IT-09 | ✅ |
| AC-4.3: last_synced_at tracking | IT-10 | ✅ |
| AC-5.1: KbEntryRepository CRUD | IT-01, IT-02, IT-04 | ✅ |
| AC-5.2: PiiMappingRepository CRUD | IT-06, IT-07, IT-08 | ✅ |
| AC-5.3: JDBC + HikariCP | IT-01 | ✅ |
| AC-5.4: Suspend functions | UT-11 | ✅ |
| AC-5.5: Batch operations | IT-11 | ✅ |
| BR-6: Cascade delete | IT-12 | ✅ |
| BR-7: content_hash uniqueness | IT-09 | ✅ |

## 5. Entry/Exit Criteria

### Entry Criteria
- Code compiles without errors (kbstore package)
- Migration scripts syntactically valid
- Test environment (Testcontainers) available

### Exit Criteria
- All 34 test cases pass
- 0 Critical/High defects open
- Code coverage ≥ 80% for kbstore package

## 6. Risk Assessment

| Risk | Mitigation |
|------|-----------|
| Testcontainers Docker not available | Fallback to H2 for basic tests |
| Encryption key format issues | PBT covers edge cases |
| FK constraint timing in tests | Ensure KB entry created before PII |

## 7. Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Test Coverage | [test-coverage.png](diagrams/test-coverage.png) | [test-coverage.drawio](diagrams/test-coverage.drawio) |

---
