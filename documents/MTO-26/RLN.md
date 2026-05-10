# Release Notes (RLN)

## MTO-26: KB Refinery — KB Entries Schema 4 Columns + Migration

| Field | Value |
|-------|-------|
| **Ticket** | MTO-26 |
| **Version** | 1.0.0 |
| **Release Date** | 2026-05-08 |
| **Type** | Feature |
| **Epic** | MTO-24 (Knowledge Base Refinery) |

---

## Summary

Introduces the foundational database schema and repository layer for the Knowledge Base Refinery system. KB entries are stored with 4-layer content separation (public, technical, business rules, masked) enabling role-based access control. Sensitive data (business rules, PII) is encrypted at the application layer using AES-256-GCM.

## New Features

### KB Entries Table (kb_entries)
- 4-column content layering: `public_content`, `technical_content`, `business_rules` (encrypted), `masked_full`
- Sensitivity level classification (Confidential/Internal/Restricted)
- Content hash for change detection and deduplication
- Unique constraint on `issue_key` (one entry per Jira ticket)
- Optimized indexes for project-level and hash-based queries

### PII Mapping Table (pii_mapping)
- Encrypted storage of PII original values (AES-256-GCM)
- Placeholder-to-original mapping (e.g., [PII_NAME_01] → "Nguyen Van A")
- Support for 5 PII types: NAME, ID_CARD, PHONE, BANK_ACCOUNT, EMAIL
- Foreign key to kb_entries with CASCADE delete

### Encryption Service
- AES-256-GCM authenticated encryption
- 12-byte random IV per operation (non-deterministic)
- 32-byte key from environment variable (never hardcoded)
- Fail-fast on missing/invalid key

### Repository Layer
- `KbEntryRepository` — full CRUD with upsert and batch support
- `PiiMappingRepository` — batch insert, replace, cascade-aware delete
- All operations are suspend functions (coroutine-friendly)
- Transparent encrypt-on-write / decrypt-on-read

### Koin DI Module
- `kbStoreModule` — wires EncryptionService, KbEntryRepository, PiiMappingRepository

## Database Changes

| Migration | Description |
|-----------|-------------|
| V6__create_kb_entries.sql | Creates `kb_entries` table with indexes and constraints |
| V7__create_pii_mapping.sql | Creates `pii_mapping` table with FK and constraints |

## Configuration Changes

| Change | Details |
|--------|---------|
| New YAML section | `orchestrator.kbstore.encryption_key` + `batch_size` |
| New env var | `KB_ENCRYPTION_KEY` (required, Base64 32-byte key) |

## Breaking Changes

None. This is a new module with no impact on existing functionality.

## Dependencies

| Dependency | Version | New? |
|-----------|---------|------|
| PostgreSQL | 16+ | Existing |
| HikariCP | Existing | No |
| Flyway | Existing | No |
| javax.crypto (JDK 21) | Built-in | No |

## Known Limitations

- Row-Level Security (RLS) not yet implemented → MTO-31
- Key rotation requires manual re-encryption → future enhancement
- No REST API exposure yet → MTO-28

## Upgrade Instructions

1. Set environment variable: `KB_ENCRYPTION_KEY=<base64-32-byte-key>`
2. Deploy new JAR (migrations run automatically)
3. Verify tables created (see DPG for verification steps)

## Rollback

Drop tables `pii_mapping` then `kb_entries`, remove Flyway history for V6/V7. See DPG for detailed rollback procedure.

---
