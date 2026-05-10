# Deployment Guide (DPG)

## MTO-26: KB Refinery — KB Entries Schema 4 Columns + Migration

| Field | Value |
|-------|-------|
| **Ticket** | MTO-26 |
| **Version** | 1.0 |
| **Author** | DevOps Agent |
| **Created** | 2026-05-08 |

---

## 1. Deployment Overview

This deployment introduces:
- 2 new database tables (`kb_entries`, `pii_mapping`) via Flyway migrations V6, V7
- New `kbstore` package with encryption capabilities
- New environment variable requirement: `KB_ENCRYPTION_KEY`

**Deployment Type:** Database schema + application code (no infrastructure changes)

## 2. Pre-Deployment Checklist

| # | Check | Command/Action | Expected |
|---|-------|---------------|----------|
| 1 | PostgreSQL 16+ running | `psql --version` | 16.x |
| 2 | Database accessible | `psql -h localhost -U orchestrator -d mcp_orchestrator -c '\dt'` | Connection OK |
| 3 | Flyway baseline exists | Check V5 migration applied | V5 present |
| 4 | Generate encryption key | `openssl rand -base64 32` | 44-char Base64 string |
| 5 | Set env var | `export KB_ENCRYPTION_KEY=<generated_key>` | Env var set |
| 6 | Backup database | `pg_dump mcp_orchestrator > backup_pre_mto26.sql` | Backup file created |
| 7 | Build fat JAR | `./gradlew buildFatJar` | Build successful |

## 3. Deployment Steps

### Step 1: Generate Encryption Key (First-time only)

```bash
# Generate a 32-byte (256-bit) random key, Base64-encoded
openssl rand -base64 32
# Example output: dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcw==

# Store securely (e.g., in secrets manager or .env file)
export KB_ENCRYPTION_KEY="<your-generated-key>"
```

⚠️ **CRITICAL:** Store this key securely. If lost, all encrypted data becomes unrecoverable.

### Step 2: Backup Database

```bash
pg_dump -h localhost -U orchestrator mcp_orchestrator > backup_pre_mto26_$(date +%Y%m%d).sql
```

### Step 3: Apply Flyway Migrations

Migrations run automatically on application startup. Alternatively, run manually:

```bash
# Option A: Let application handle it (recommended)
java -jar build/libs/mcp-orchestrator-all.jar

# Option B: Manual Flyway CLI
flyway -url=jdbc:postgresql://localhost:5432/mcp_orchestrator \
       -user=orchestrator -password=<password> \
       migrate
```

### Step 4: Verify Migrations Applied

```sql
-- Check Flyway history
SELECT version, description, success FROM flyway_schema_history 
ORDER BY installed_rank DESC LIMIT 5;

-- Verify tables created
\dt kb_entries
\dt pii_mapping

-- Verify indexes
\di idx_kb_entries_project_key
\di idx_kb_entries_content_hash
\di idx_pii_mapping_issue_key

-- Verify constraints
SELECT conname FROM pg_constraint WHERE conrelid = 'kb_entries'::regclass;
SELECT conname FROM pg_constraint WHERE conrelid = 'pii_mapping'::regclass;
```

### Step 5: Verify Application Startup

```bash
# Start application
java -jar build/libs/mcp-orchestrator-all.jar

# Check logs for:
# - "Flyway migration V6 applied"
# - "Flyway migration V7 applied"
# - No EncryptionService initialization errors
```

### Step 6: Smoke Test

```bash
# Verify encryption service works (application log should show no errors)
# The kbstore module initializes EncryptionService on startup
# If KB_ENCRYPTION_KEY is missing/invalid → application fails to start (fail-fast)
```

## 4. Configuration Changes

### application.yml additions

```yaml
orchestrator:
  kbstore:
    encryption_key: ${KB_ENCRYPTION_KEY}
    batch_size: 500
```

### Environment Variables

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `KB_ENCRYPTION_KEY` | Yes | Base64-encoded 32-byte AES key | `dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcw==` |

## 5. Rollback Plan

### Rollback Steps

1. **Stop application**
2. **Rollback migrations:**
   ```sql
   -- Drop pii_mapping first (FK dependency)
   DROP TABLE IF EXISTS pii_mapping;
   DROP TABLE IF EXISTS kb_entries;
   
   -- Remove Flyway history entries
   DELETE FROM flyway_schema_history WHERE version IN ('6', '7');
   ```
3. **Deploy previous JAR version**
4. **Remove KB_ENCRYPTION_KEY env var** (optional)

### Rollback Conditions

| Condition | Action |
|-----------|--------|
| Migration V6 fails | Flyway auto-rollback, investigate SQL |
| Migration V7 fails | Drop V7 table, fix FK, retry |
| Application fails to start (key issue) | Fix KB_ENCRYPTION_KEY, restart |
| Data corruption detected | Restore from backup |

## 6. Post-Deployment Verification

| # | Check | Command | Expected |
|---|-------|---------|----------|
| 1 | Tables exist | `\dt kb_entries; \dt pii_mapping` | Both tables listed |
| 2 | Indexes exist | `\di *kb_entries*; \di *pii_mapping*` | 3 indexes total |
| 3 | FK constraint | `\d pii_mapping` | FK to kb_entries.issue_key |
| 4 | CHECK constraint | Insert with level=0 | Rejected |
| 5 | App healthy | Check application logs | No errors |
| 6 | Encryption works | App starts without ConfigException | Startup clean |

## 7. Monitoring

| Metric | Alert Threshold | Action |
|--------|----------------|--------|
| Migration failure | Any | Page on-call, check Flyway logs |
| Encryption errors | > 0 in 5min | Check key configuration |
| DB connection errors | > 5 in 1min | Check HikariCP pool, PostgreSQL |
| Table size growth | > 1GB | Plan partitioning strategy |

---
