# Release Notes

## MCPOrchestration — MTO-15: Database Schema & Sync State Management

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-15 |
| Release Version | 1.1.0 |
| Release Date | 2025-07-17 |
| Author | DevOps Agent |
| Status | Final |
| Related Epic | MTO-14 — Jira Project Sync Service |

---

## 1. Release Summary

This release introduces the **Database Schema & Sync State Management** module — the foundational persistence layer for the Jira Project Sync Service (Epic MTO-14). It provides 4 PostgreSQL tables and a Kotlin service layer for managing synchronization lifecycle, ticket caching, relationship graphing, and attachment queuing.

**Release Type:** Feature addition (non-breaking, additive only)
**Risk Level:** Low
**Backward Compatible:** Yes — no existing APIs or schemas modified

---

## 2. What's New

### 2.1 New Features

| # | Feature | Description |
|---|---------|-------------|
| 1 | **Sync State Machine** | `SyncStateManager` with 5-state lifecycle (IDLE → RUNNING → PAUSED/COMPLETED/FAILED) and optimistic locking for concurrent safety |
| 2 | **Ticket Cache** | `jira_ticket_cache` table with UPSERT support, content hashing for change detection, and KB ingestion tracking |
| 3 | **Ticket Relationship Graph** | `jira_ticket_graph` table storing directed edges (INWARD, OUTWARD, SUBTASK, EPIC) with composite primary key |
| 4 | **Attachment Queue** | `jira_attachment_queue` with FIFO polling, retry counting, and partial indexes for efficient pending item retrieval |
| 5 | **Auto-Migration** | `JiraSyncDatabaseInitializer` executes DDL on startup — idempotent via `IF NOT EXISTS` |
| 6 | **Performance Indexes** | 8 indexes including 2 partial indexes for optimized query patterns |

### 2.2 Technical Highlights

- **State Machine with Optimistic Locking** — Prevents race conditions without pessimistic locks. Uses `WHERE status = :expected` in UPDATE statements.
- **Batch Operations** — `upsertBatch()` and `insertBatch()` for efficient bulk data loading during sync.
- **Partial Indexes** — `idx_ticket_cache_not_ingested` (WHERE kb_ingested = FALSE) and `idx_attachment_queue_pending` (WHERE status = 'PENDING') for targeted query optimization.
- **Resumable Sync** — `last_offset` checkpoint enables sync jobs to resume from interruption point.
- **Content Hash Change Detection** — SHA-256 hash of ticket key fields avoids unnecessary KB re-ingestion.

---

## 3. New Components

### 3.1 Package: `com.orchestrator.mcp.sync`

| Class | Type | Responsibility |
|-------|------|----------------|
| `SyncStateManager` | Interface | Sync lifecycle state machine API |
| `SyncStateManagerImpl` | Class | State machine implementation with optimistic locking |
| `JiraSyncDatabaseInitializer` | Class | DDL migration executor (startup) |
| `TicketCacheRepository` | Interface | Ticket cache CRUD operations |
| `TicketCacheRepositoryImpl` | Class | JDBC implementation with UPSERT |
| `TicketGraphRepository` | Interface | Relationship graph operations |
| `TicketGraphRepositoryImpl` | Class | JDBC implementation with batch insert |
| `AttachmentQueueRepository` | Interface | Attachment queue operations |
| `AttachmentQueueRepositoryImpl` | Class | JDBC implementation with FIFO polling |
| `SyncState` | Data class | Domain model for sync state |
| `SyncStatus` | Enum | IDLE, RUNNING, PAUSED, COMPLETED, FAILED |
| `TicketCache` | Data class | Domain model for cached ticket |
| `TicketRelation` | Data class | Domain model for graph edge |
| `AttachmentQueueItem` | Data class | Domain model for queue item |
| `AttachmentStatus` | Enum | PENDING, DOWNLOADING, PROCESSING, DONE, FAILED |

### 3.2 Database Tables

| Table | Rows (initial) | Growth Rate | Retention |
|-------|---------------|-------------|-----------|
| `jira_sync_state` | 0 | 1 per project synced | Permanent |
| `jira_ticket_cache` | 0 | N per project (all tickets) | Permanent |
| `jira_ticket_graph` | 0 | ~2× ticket count (avg links) | Permanent |
| `jira_attachment_queue` | 0 | Varies (attachments found) | Processed items can be purged |

---

## 4. Configuration Changes

**No new configuration required.** The module uses the existing database connection settings:

```yaml
orchestrator:
  database:
    url: "jdbc:postgresql://localhost:5432/mcp_orchestrator"
    username: "${DB_USERNAME:postgres}"
    password: "${DB_PASSWORD:postgres}"
    pool_size: 10
```

---

## 5. Dependencies

### 5.1 No New Dependencies

This release introduces **zero new external dependencies**. All libraries used are already present in the project:

| Library | Version | Usage in MTO-15 |
|---------|---------|-----------------|
| HikariCP | 6.2.1 | Connection pool (existing) |
| PostgreSQL JDBC | 42.7.x | Database driver (existing) |
| kotlinx.coroutines | 1.10.2 | Async DB operations (existing) |
| kotlinx.datetime | 0.6.2 | Timestamp handling (existing) |
| Koin | 4.1.1 | DI bindings (existing) |

### 5.2 Infrastructure Dependencies

| Service | Version | Required | Notes |
|---------|---------|----------|-------|
| PostgreSQL | 16+ | ✅ | Must support JSONB, partial indexes |
| JVM | 21 | ✅ | Existing requirement |

---

## 6. Testing Summary

| Level | Tests | Passed | Failed | Coverage |
|-------|-------|--------|--------|----------|
| Unit Tests | 12 | 12 | 0 | State machine logic, validation, error handling |
| Integration Tests | 6 | 6 | 0 | Full DB operations with Testcontainers PostgreSQL |
| **Total** | **18** | **18** | **0** | — |

### 6.1 Test Highlights

- **State Machine Tests** — All valid transitions verified, invalid transitions throw `IllegalStateException`
- **Optimistic Locking** — Concurrent modification detected and rejected
- **UPSERT Behavior** — Insert-or-update verified for ticket cache
- **Batch Operations** — Bulk insert performance validated
- **Partial Index Usage** — Query plans verified to use partial indexes
- **Testcontainers** — Real PostgreSQL 16 container used for integration tests

---

## 7. Known Issues & Limitations

| # | Issue | Severity | Workaround | Planned Fix |
|---|-------|----------|------------|-------------|
| 1 | No data purging strategy | Low | Manual DELETE for old records | Future MTO-14 story |
| 2 | No connection pool size auto-tuning | Low | Manual `pool_size` config | Monitor and adjust |
| 3 | Single-node only (no distributed locking) | Low | N/A (single JVM deployment) | Not planned |

---

## 8. Migration Notes

### 8.1 From Previous Version

- **No breaking changes** — existing tables (`server_config`, `tool_toggle_state`, `file_proxy_registry`) are untouched
- **No config changes** — `application.yml` format unchanged
- **No API changes** — existing MCP tools unaffected
- **Automatic migration** — tables created on first startup with new JAR

### 8.2 Verification After Upgrade

```sql
-- Quick verification query
SELECT 
  (SELECT COUNT(*) FROM pg_tables WHERE tablename = 'jira_sync_state') AS sync_state_exists,
  (SELECT COUNT(*) FROM pg_tables WHERE tablename = 'jira_ticket_cache') AS ticket_cache_exists,
  (SELECT COUNT(*) FROM pg_tables WHERE tablename = 'jira_ticket_graph') AS ticket_graph_exists,
  (SELECT COUNT(*) FROM pg_tables WHERE tablename = 'jira_attachment_queue') AS attachment_queue_exists;
-- Expected: all values = 1
```

---

## 9. Rollback Instructions

If rollback is needed:

1. Stop application
2. Restore previous JAR (`mcp-orchestrator-all.jar.bak`)
3. Drop new tables:
   ```sql
   DROP TABLE IF EXISTS jira_attachment_queue CASCADE;
   DROP TABLE IF EXISTS jira_ticket_graph CASCADE;
   DROP TABLE IF EXISTS jira_ticket_cache CASCADE;
   DROP TABLE IF EXISTS jira_sync_state CASCADE;
   ```
4. Restart application
5. Verify existing features work

**Data loss risk:** None (tables are new, no pre-existing data affected)

---

## 10. Future Roadmap (Epic MTO-14)

This release is **Story 1 of N** in the Jira Project Sync Service epic:

| Story | Status | Dependency on MTO-15 |
|-------|--------|---------------------|
| MTO-15: DB Schema & Sync State | ✅ **This release** | — |
| Jira API Client & Data Fetcher | Planned | Uses `TicketCacheRepository`, `SyncStateManager` |
| KB Ingestion Pipeline | Planned | Uses `TicketCacheRepository.findNotIngested()` |
| Background Job Scheduler | Planned | Uses `SyncStateManager` lifecycle |
| Attachment Processor | Planned | Uses `AttachmentQueueRepository` |
| Monitoring Dashboard | Planned | Reads `jira_sync_state` table |

---

## 11. Contact & Support

| Role | Contact | Responsibility |
|------|---------|----------------|
| Developer | DEV Agent | Implementation questions |
| Architect | SA Agent | Design decisions |
| QA | QA Agent | Test failures |
| DevOps | DevOps Agent | Deployment issues |
