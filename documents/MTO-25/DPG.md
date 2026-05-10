# Deployment Guide (DPG)

## MCPOrchestration — MTO-25: KB Refinery — Dual-Priority Queue (Kotlin Channels)

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-25 |
| Title | KB Refinery — Dual-Priority Queue Deployment |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |

---

## 1. Prerequisites

### 1.1 Infrastructure Requirements

| Component | Requirement | Notes |
|-----------|-------------|-------|
| JVM | Java 21+ | Required for Kotlin 2.3.20 |
| PostgreSQL | 16+ | For queue_tasks table |
| Disk Space | 50MB additional | For fat JAR + logs |
| Memory | 256MB additional heap | For channel buffers + coroutines |

### 1.2 Database Prerequisites

- PostgreSQL instance accessible from application server
- Database `orchestrator` exists
- User with CREATE TABLE, INSERT, UPDATE, SELECT, DELETE permissions
- Flyway migration will auto-create `queue_tasks` table on first startup

### 1.3 Configuration Prerequisites

Ensure `application.yml` has the queue section:

```yaml
queue:
  hpq_capacity: 100
  npq_capacity: 1000
  worker_id: "worker-${HOSTNAME}"
  watchdog:
    scan_interval_seconds: 60
    stuck_threshold_minutes: 5
    enabled: true
  retry:
    max_retries: 3
    base_delay_ms: 1000
  enabled: true
```

---

## 2. Deployment Steps

### Step 1: Database Migration

```bash
# Flyway runs automatically on application startup
# To run manually:
./gradlew flywayMigrate -Dflyway.url=jdbc:postgresql://localhost:5432/orchestrator
```

Verify migration:
```sql
SELECT * FROM flyway_schema_history WHERE script LIKE '%queue%';
-- Should show V5__create_queue_tasks.sql as SUCCESS
```

### Step 2: Build Application

```bash
./gradlew buildFatJar
# Output: build/libs/mcp-orchestrator-all.jar
```

### Step 3: Deploy JAR

```bash
# Stop existing instance
systemctl stop mcp-orchestrator

# Replace JAR
cp build/libs/mcp-orchestrator-all.jar /opt/mcp-orchestrator/

# Start with queue enabled
systemctl start mcp-orchestrator
```

### Step 4: Verify Deployment

```bash
# Check logs for queue startup
grep -i "QueueWorker starting" /var/log/mcp-orchestrator/app.log
grep -i "QueueWatchdog starting" /var/log/mcp-orchestrator/app.log
grep -i "Crash recovery" /var/log/mcp-orchestrator/app.log

# Verify queue_tasks table exists
psql -d orchestrator -c "SELECT COUNT(*) FROM queue_tasks;"
```

---

## 3. Post-Deployment Verification

| Check | Command | Expected |
|-------|---------|----------|
| Queue worker running | Log: "QueueWorker starting" | Present |
| Watchdog running | Log: "QueueWatchdog starting" | Present |
| DB table exists | `\dt queue_tasks` | Table listed |
| Indexes created | `\di *queue*` | 4 indexes |
| No crash recovery needed | Log: "no interrupted tasks found" | Present (fresh deploy) |

---

## 4. Rollback Plan

### 4.1 Quick Rollback (Feature Flag)

```yaml
# In application.yml — disable queue without redeployment
queue:
  enabled: false
```

Restart application. Queue system will not start.

### 4.2 Full Rollback

```bash
# 1. Stop application
systemctl stop mcp-orchestrator

# 2. Rollback database
psql -d orchestrator -c "DROP TABLE IF EXISTS queue_tasks;"
psql -d orchestrator -c "DELETE FROM flyway_schema_history WHERE script = 'V5__create_queue_tasks.sql';"

# 3. Deploy previous JAR version
cp /opt/mcp-orchestrator/backup/mcp-orchestrator-all.jar.bak /opt/mcp-orchestrator/mcp-orchestrator-all.jar

# 4. Restart
systemctl start mcp-orchestrator
```

### 4.3 Rollback Verification

```bash
# Verify queue table removed
psql -d orchestrator -c "\dt queue_tasks" 2>&1 | grep "Did not find"

# Verify application starts without queue
grep -i "queue" /var/log/mcp-orchestrator/app.log | grep -v "disabled"
```

---

## 5. Monitoring

### 5.1 Key Log Patterns to Monitor

| Pattern | Severity | Action |
|---------|----------|--------|
| "Task .* failed permanently" | WARN | Investigate failed task |
| "Watchdog found .* stuck task" | WARN | Check worker health |
| "QueuePersistenceException" | ERROR | Check PostgreSQL connectivity |
| "Crash recovery: found .* interrupted" | WARN | Normal after restart, investigate if frequent |

### 5.2 Database Monitoring Queries

```sql
-- Queue health overview
SELECT status, priority, COUNT(*) FROM queue_tasks GROUP BY status, priority;

-- Stuck tasks (should be 0 if watchdog is working)
SELECT * FROM queue_tasks WHERE status = 'Processing' AND started_at < NOW() - INTERVAL '5 minutes';

-- Failed tasks (investigate root cause)
SELECT task_id, task_type, error_message, retry_count FROM queue_tasks WHERE status = 'Failed' ORDER BY completed_at DESC LIMIT 10;
```

---

## 6. Appendix

### Environment-Specific Configuration

| Property | DEV | STAGING | PROD |
|----------|-----|---------|------|
| queue.hpq_capacity | 10 | 50 | 100 |
| queue.npq_capacity | 100 | 500 | 1000 |
| queue.watchdog.scan_interval_seconds | 30 | 60 | 60 |
| queue.watchdog.stuck_threshold_minutes | 2 | 5 | 5 |
| queue.retry.max_retries | 3 | 3 | 3 |
