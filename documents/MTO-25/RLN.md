# Release Notes (RLN)

## MCPOrchestration — MTO-25: KB Refinery — Dual-Priority Queue (Kotlin Channels)

---

## Release Information

| Field | Value |
|-------|-------|
| Version | 1.5.0 |
| Release Date | 2026-05-08 |
| Jira Ticket | MTO-25 |
| Epic | MTO-24 (KB Refinery) |
| Type | Feature |
| Priority | High |

---

## 1. Summary

Added a **Dual-Priority Queue** system using Kotlin Channels to the MCPOrchestration platform. This provides prioritized task processing where user-initiated requests (high-priority) preempt batch/system tasks (normal-priority), ensuring responsive user experience while maintaining background processing throughput.

---

## 2. New Features

### 2.1 Dual-Priority Queue (HPQ + NPQ)

- **High-Priority Queue (HPQ)**: Kotlin Channel with capacity 100 for user/UI tasks
- **Normal-Priority Queue (NPQ)**: Kotlin Channel with capacity 1000 for batch/system tasks
- HPQ tasks always processed before NPQ tasks via `select {}` prioritization

### 2.2 Preemption

- Running NPQ tasks are automatically cancelled when HPQ tasks arrive
- Preempted tasks are re-queued without penalty (no retry count increment)
- Preemption latency target: < 500ms

### 2.3 PostgreSQL State Tracking

- New `queue_tasks` table for durable task state persistence
- Manual acknowledgment pattern — tasks only marked complete after explicit confirmation
- Full lifecycle tracking: Pending → Processing → Completed/Failed

### 2.4 Watchdog

- Background coroutine scans for stuck tasks every 60 seconds
- Tasks stuck in Processing > 5 minutes are automatically recovered
- Self-healing: re-queues or marks as Failed based on retry count

### 2.5 Retry with Exponential Backoff

- Failed tasks retried up to 3 times
- Backoff: 2s → 4s → 8s (base 1s * 2^retryCount)
- Permanent failure after max retries exhausted

### 2.6 Crash Recovery

- On startup, detects tasks left in Processing state from previous crash
- Automatically re-queues or fails based on retry count
- Zero task loss guarantee

---

## 3. Technical Changes

### 3.1 New Files (16 total)

| Package | Files | Description |
|---------|-------|-------------|
| `queue/` | 8 | Core services (QueueService, Worker, Watchdog, etc.) |
| `queue/model/` | 3 | Domain models (QueueTask, Priority, TaskStatus) |
| `queue/config/` | 1 | Configuration data class |
| `queue/repository/` | 2 | DB access (interface + JDBC impl) |
| `queue/di/` | 1 | Koin DI module |
| `resources/db/migration/` | 1 | Flyway SQL migration |

### 3.2 Database Changes

- **New table**: `queue_tasks` (11 columns)
- **New indexes**: 4 indexes (status, priority+status, stuck detection, worker)
- **Migration**: `V5__create_queue_tasks.sql`

### 3.3 Configuration Changes

New `queue` section in `application.yml` with capacity, watchdog, and retry settings.

---

## 4. Dependencies

No new external dependencies added. Uses existing:
- kotlinx.coroutines (channels, select, SupervisorJob)
- PostgreSQL (via existing HikariCP pool)
- Koin DI
- kotlinx.serialization-json

---

## 5. Breaking Changes

None. This is a new module with no impact on existing functionality.

---

## 6. Known Limitations

| Limitation | Impact | Future Plan |
|-----------|--------|-------------|
| Single JVM instance only | No distributed coordination | MTO-24 future ticket for multi-instance |
| No UI for queue monitoring | Operators use DB queries/logs | Future monitoring dashboard |
| Channel size not exposed via API | Approximate metrics only | Implement custom channel wrapper |

---

## 7. Upgrade Instructions

1. Deploy new JAR (includes queue module)
2. Flyway auto-creates `queue_tasks` table on startup
3. Add `queue` section to `application.yml` (see DPG)
4. Restart application
5. Verify via logs: "QueueWorker starting", "QueueWatchdog starting"

---

## 8. Rollback Instructions

See DPG §4 for detailed rollback procedures. Quick rollback:
```yaml
queue:
  enabled: false
```
