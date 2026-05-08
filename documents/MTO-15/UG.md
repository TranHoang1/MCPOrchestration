# User Guide (UG)

## Jira Project Sync Service — MTO-15: Database Schema & Sync State Management

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-15 |
| Title | Database Schema & Sync State Management |
| Author | DEV Agent |
| Reviewer | BA Agent |
| Version | 1.0 |
| Date | 2025-07-15 |
| Status | Final |
| Related BRD | BRD-v1-MTO-15.docx |
| Related FSD | FSD-v1-MTO-15.docx |
| Related TDD | TDD-v1-MTO-15.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-15 | DEV Agent | Initial document |

---

## 1. Introduction

### 1.1 Purpose

This guide explains how to configure, use, and troubleshoot the Database Schema & Sync State Management module — the foundational persistence layer for the Jira Project Sync Service (Epic MTO-14). It provides 4 PostgreSQL tables and a `SyncStateManager` Kotlin API for managing synchronization lifecycle.

### 1.2 Audience

| Audience | What They Need |
|----------|---------------|
| Developer | How to integrate with SyncStateManager API, repository interfaces, and Koin DI |
| DevOps / DBA | How to run migrations, monitor tables, and troubleshoot database issues |
| System Operator | How to verify schema is initialized and check sync state |

### 1.3 Prerequisites

| Prerequisite | Version | Required |
|-------------|---------|----------|
| JDK | 21+ | Yes |
| PostgreSQL | 16+ | Yes |
| Gradle | 8.x | Yes |
| HikariCP (bundled) | 6.2.1 | Yes (auto-included) |

---

## 2. Getting Started

### 2.1 Quick Start

The database schema is automatically initialized when the application starts. No manual migration is needed.

```bash
# Step 1: Ensure PostgreSQL is running
pg_isready -h localhost -p 5432

# Step 2: Configure database connection in application.yml
# (see Section 3 for full configuration reference)

# Step 3: Start the application
./gradlew :orchestrator-server:run

# Step 4: Verify schema created (check logs)
# Expected log output:
# INFO  JiraSyncDatabaseInitializer - Initializing Jira sync database schema...
# INFO  JiraSyncDatabaseInitializer - Jira sync database schema initialized successfully.
```

### 2.2 System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| PostgreSQL | 16 | 16+ |
| Disk (DB) | 100 MB | 1 GB (for large projects) |
| Memory (App) | 256 MB | 512 MB |
| Network | Local or LAN to PostgreSQL | Same host |

### 2.3 Schema Overview

The module creates 4 tables:

| Table | Purpose | Estimated Rows |
|-------|---------|----------------|
| `jira_sync_state` | One row per project — sync lifecycle tracking | 10–50 |
| `jira_ticket_cache` | Cached Jira ticket metadata | 10,000–100,000 |
| `jira_ticket_graph` | Directed relationship edges between tickets | 50,000–500,000 |
| `jira_attachment_queue` | Work queue for attachment processing | 1,000–50,000 |

---

## 3. Configuration

### 3.1 Database Connection

Configure in `application.yml` (or via environment variables):

```yaml
database:
  url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/mcp_orchestrator}
  username: ${DATABASE_USERNAME:postgres}
  password: ${DATABASE_PASSWORD:postgres}
  hikari:
    maximumPoolSize: 10
    minimumIdle: 2
    connectionTimeout: 30000
    idleTimeout: 600000
```

### 3.2 Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DATABASE_URL` | Yes | `jdbc:postgresql://localhost:5432/mcp_orchestrator` | JDBC connection URL |
| `DATABASE_USERNAME` | Yes | `postgres` | Database username |
| `DATABASE_PASSWORD` | Yes | `postgres` | Database password |

### 3.3 Configuration Examples

#### Minimal (Local Development)

```yaml
database:
  url: jdbc:postgresql://localhost:5432/mcp_orchestrator
  username: postgres
  password: postgres
```

#### Production

```yaml
database:
  url: jdbc:postgresql://db-host:5432/mcp_prod
  username: ${DB_USER}
  password: ${DB_PASS}
  hikari:
    maximumPoolSize: 20
    minimumIdle: 5
    connectionTimeout: 10000
```

---

## 4. Usage

### 4.1 SyncStateManager API

The `SyncStateManager` is the primary interface for managing sync lifecycle. Inject it via Koin DI.

#### Injection

```kotlin
import org.koin.core.component.inject

class MySyncJob : KoinComponent {
    private val syncStateManager: SyncStateManager by inject()
}
```

#### Get or Create Sync State

```kotlin
val state = syncStateManager.getOrCreate("MTO")
// Returns SyncState with status=IDLE if new, or current state if exists
println("Project: ${state.projectKey}, Status: ${state.status}")
```

#### Start a Sync Run

```kotlin
// Transition: IDLE/PAUSED/FAILED → RUNNING
syncStateManager.markRunning("MTO")
```

#### Update Progress

```kotlin
// Update checkpoint (only when RUNNING)
syncStateManager.updateProgress(
    projectKey = "MTO",
    offset = 150,    // last processed offset
    synced = 150     // total synced issues so far
)
```

#### Complete or Fail

```kotlin
// On success:
syncStateManager.markCompleted("MTO")

// On failure:
syncStateManager.markFailed("MTO", "Network timeout after 3 retries")

// Pause (operator-initiated):
syncStateManager.markPaused("MTO")
```

### 4.2 State Machine

Valid transitions:

```
IDLE ──────→ RUNNING
PAUSED ────→ RUNNING
FAILED ────→ RUNNING
RUNNING ───→ COMPLETED
RUNNING ───→ FAILED
RUNNING ───→ PAUSED
```

Invalid transitions throw `IllegalStateException`. Example:
- `COMPLETED → PAUSED` ❌
- `IDLE → COMPLETED` ❌

### 4.3 TicketCacheRepository

```kotlin
private val ticketCacheRepo: TicketCacheRepository by inject()

// Upsert a single ticket
ticketCacheRepo.upsert(ticketCache)

// Batch upsert (returns count of affected rows)
val count = ticketCacheRepo.upsertBatch(ticketList)

// Find tickets not yet ingested into KB
val pending = ticketCacheRepo.findNotIngested("MTO")

// Mark as ingested after KB processing
ticketCacheRepo.markIngested("MTO-15")
```

### 4.4 TicketGraphRepository

```kotlin
private val graphRepo: TicketGraphRepository by inject()

// Insert a relationship edge
graphRepo.insertRelation(TicketRelation(
    sourceKey = "MTO-14",
    targetKey = "MTO-15",
    linkType = "parent",
    category = RelationCategory.SUBTASK
))

// Query outgoing edges
val children = graphRepo.findOutgoing("MTO-14")

// Query incoming edges
val parents = graphRepo.findIncoming("MTO-15")
```

### 4.5 AttachmentQueueRepository

```kotlin
private val attachmentRepo: AttachmentQueueRepository by inject()

// Enqueue an attachment for processing
attachmentRepo.enqueue(AttachmentQueueItem(
    ticketKey = "MTO-15",
    attachmentId = "12345",
    filename = "design.pdf",
    mimeType = "application/pdf",
    sizeBytes = 1024000,
    downloadUrl = "https://jira.example.com/attachment/12345"
))

// Poll pending items (for processor)
val pending = attachmentRepo.pollPending(limit = 5)

// Mark as done after processing
attachmentRepo.markDone(itemId)

// Increment retry on failure
attachmentRepo.incrementRetry(itemId, "Download timeout")
```

---

## 5. Administration

### 5.1 Verify Schema Initialization

```sql
-- Check all 4 tables exist
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' 
AND table_name LIKE 'jira_%';

-- Expected output:
-- jira_sync_state
-- jira_ticket_cache
-- jira_ticket_graph
-- jira_attachment_queue
```

### 5.2 Monitor Sync State

```sql
-- View all project sync states
SELECT project_key, status, synced_issues, total_issues, 
       last_sync_at, updated_at 
FROM jira_sync_state;

-- Check progress percentage
SELECT project_key, status,
       CASE WHEN total_issues > 0 
            THEN ROUND(synced_issues::numeric / total_issues * 100, 1)
            ELSE 0 END AS progress_pct
FROM jira_sync_state;
```

### 5.3 Monitor Attachment Queue

```sql
-- Queue depth by status
SELECT status, COUNT(*) as count 
FROM jira_attachment_queue 
GROUP BY status;

-- Failed items needing attention
SELECT id, ticket_key, filename, error_message, retry_count
FROM jira_attachment_queue 
WHERE status = 'FAILED';

-- Reset failed items for re-processing
UPDATE jira_attachment_queue 
SET status = 'PENDING', retry_count = 0, error_message = NULL
WHERE status = 'FAILED' AND retry_count >= 3;
```

### 5.4 Reset Stuck Sync State

If a sync is stuck in RUNNING (e.g., after a crash):

```sql
-- Check if stuck (RUNNING for > 1 hour)
SELECT * FROM jira_sync_state 
WHERE status = 'RUNNING' 
AND updated_at < NOW() - INTERVAL '1 hour';

-- Reset to FAILED (allows retry)
UPDATE jira_sync_state 
SET status = 'FAILED', error_message = 'Manual reset: stuck in RUNNING'
WHERE project_key = 'MTO' AND status = 'RUNNING';
```

### 5.5 Database Maintenance

```sql
-- Check table sizes
SELECT relname, pg_size_pretty(pg_total_relation_size(relid))
FROM pg_catalog.pg_statio_user_tables
WHERE relname LIKE 'jira_%'
ORDER BY pg_total_relation_size(relid) DESC;

-- Vacuum and analyze (run periodically)
VACUUM ANALYZE jira_ticket_cache;
VACUUM ANALYZE jira_ticket_graph;
VACUUM ANALYZE jira_attachment_queue;
```

---

## 6. Troubleshooting

### 6.1 Common Issues

| # | Symptom | Cause | Solution |
|---|---------|-------|----------|
| 1 | `IllegalStateException: Cannot transition to RUNNING from COMPLETED` | Trying to start sync on already-completed project | Call `getOrCreate()` first — it returns current state. For re-sync, the state machine allows IDLE/PAUSED/FAILED → RUNNING. |
| 2 | `IllegalStateException: Concurrent modification detected` | Two processes trying to modify same project state | Only one sync job per project should run. Check for duplicate schedulers. |
| 3 | Schema not created on startup | Database connection failed | Check `DATABASE_URL`, ensure PostgreSQL is running and accessible |
| 4 | `PSQLException: relation "jira_sync_state" does not exist` | Migration didn't run | Check startup logs for `JiraSyncDatabaseInitializer` errors. Verify DB credentials. |
| 5 | Slow queries on `jira_ticket_cache` | Missing indexes | Verify indexes exist: `SELECT indexname FROM pg_indexes WHERE tablename = 'jira_ticket_cache';` |
| 6 | Attachment queue growing unbounded | Processor not running or failing | Check MTO-19 Attachment Processor status. Review failed items. |

### 6.2 Error Codes

| Exception | Description | Action |
|-----------|-------------|--------|
| `IllegalArgumentException` | Invalid input (blank project key, negative offset) | Fix caller code — programming error |
| `IllegalStateException` | Invalid state transition or concurrent modification | Check current state before transitioning; ensure single-writer |
| `SQLException` | Database connection or constraint error | Check DB connectivity, review HikariCP pool settings |
| `DataIntegrityViolationException` | Invalid JSONB data in labels field | Validate JSON before inserting into ticket cache |

### 6.3 Logs

| Log Source | Content | Useful For |
|-----------|---------|------------|
| `JiraSyncDatabaseInitializer` | Schema creation success/failure | Startup verification |
| `SyncStateManagerImpl` | State transitions, progress updates | Monitoring sync lifecycle |
| HikariCP | Connection pool metrics | Connection issues |

### 6.4 FAQ

**Q: Can I run the migration manually?**
A: Yes. Execute `V3__create_jira_sync_tables.sql` directly against your PostgreSQL database. All statements use `IF NOT EXISTS` so it's safe to re-run.

**Q: What happens if the app crashes mid-sync?**
A: The sync state remains as RUNNING with the last saved checkpoint (`last_offset`). On restart, the scanner (MTO-17) detects this and resumes from the checkpoint.

**Q: Can multiple instances share the same database?**
A: The optimistic locking pattern (`WHERE status = :expected`) prevents race conditions. However, only one instance should run sync for a given project at a time.

**Q: How do I add a new project for syncing?**
A: Simply call `syncStateManager.getOrCreate("NEW_PROJECT")`. It creates a new record with IDLE status automatically.

**Q: How do I completely reset sync data for a project?**
A: 
```sql
DELETE FROM jira_attachment_queue WHERE ticket_key LIKE 'MTO-%';
DELETE FROM jira_ticket_graph WHERE source_key LIKE 'MTO-%' OR target_key LIKE 'MTO-%';
DELETE FROM jira_ticket_cache WHERE project_key = 'MTO';
DELETE FROM jira_sync_state WHERE project_key = 'MTO';
```

---

## 7. API Reference

### 7.1 SyncStateManager Interface

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `getOrCreate` | `projectKey: String` | `SyncState` | Get existing or create new (IDLE) |
| `markRunning` | `projectKey: String` | `Unit` | Transition to RUNNING |
| `markPaused` | `projectKey: String` | `Unit` | Transition to PAUSED |
| `markCompleted` | `projectKey: String` | `Unit` | Transition to COMPLETED |
| `markFailed` | `projectKey: String, error: String` | `Unit` | Transition to FAILED |
| `updateProgress` | `projectKey: String, offset: Int, synced: Int` | `Unit` | Update checkpoint |
| `getStatus` | `projectKey: String` | `SyncStatus?` | Query current status |

### 7.2 Data Models

#### SyncState

| Field | Type | Description |
|-------|------|-------------|
| projectKey | String | Jira project key (PK) |
| lastSyncAt | Instant? | Last successful sync completion |
| lastOffset | Int | Resumable checkpoint |
| totalIssues | Int | Total issues in project |
| syncedIssues | Int | Issues synced so far |
| status | SyncStatus | IDLE, RUNNING, PAUSED, COMPLETED, FAILED |
| errorMessage | String? | Error details when FAILED |
| updatedAt | Instant | Last modification timestamp |

#### SyncStatus Enum

| Value | Description |
|-------|-------------|
| IDLE | No sync in progress, ready to start |
| RUNNING | Sync actively processing |
| PAUSED | Sync paused by operator |
| COMPLETED | Last sync finished successfully |
| FAILED | Last sync failed with error |

---

## 8. Appendix

### 8.1 Glossary

| Term | Definition |
|------|------------|
| Sync State | Record tracking progress of a Jira project synchronization |
| Content Hash | SHA-256 hash for change detection |
| Checkpoint | Saved offset for resumable sync |
| Optimistic Locking | Concurrency control via WHERE clause on expected state |
| UPSERT | INSERT with ON CONFLICT UPDATE |

### 8.2 Related Documents

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-15.docx |
| FSD | FSD-v1-MTO-15.docx |
| TDD | TDD-v1-MTO-15.docx |
| DPG | DPG-v1-MTO-15.docx |

### 8.3 Database Schema DDL

Full migration script location: `orchestrator-server/src/main/resources/db/V3__create_jira_sync_tables.sql`
