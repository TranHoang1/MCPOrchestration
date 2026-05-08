# User Guide (UG)

## Jira Project Sync Service — MTO-17: Project Scanner — Breadth-First Incremental Scan

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-17 |
| Title | Project Scanner — Breadth-First Incremental Scan |
| Author | DEV Agent |
| Reviewer | BA Agent |
| Version | 1.0 |
| Date | 2025-07-15 |
| Status | Final |
| Related BRD | BRD-v1-MTO-17.docx |
| Related FSD | FSD-v1-MTO-17.docx |
| Related TDD | TDD-v1-MTO-17.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-15 | DEV Agent | Initial document |

---

## 1. Introduction

### 1.1 Purpose

This guide explains how to trigger, monitor, and troubleshoot the **ProjectScanner** — a coroutine-based service that performs breadth-first scanning of Jira projects. It fetches lightweight ticket metadata via JQL pagination and caches it locally in PostgreSQL for downstream processing.

### 1.2 Audience

| Audience | What They Need |
|----------|---------------|
| System Operator | How to trigger scans, monitor progress, and handle failures |
| Developer | How to integrate with ProjectScanner API and configure scan behavior |
| DevOps | How to monitor scan health and tune performance |

### 1.3 Prerequisites

| Prerequisite | Version | Required |
|-------------|---------|----------|
| JDK | 21+ | Yes |
| PostgreSQL | 16+ | Yes |
| Jira REST Client configured (MTO-16) | — | Yes |
| Database schema initialized (MTO-15) | — | Yes |

---

## 2. Getting Started

### 2.1 Quick Start

```bash
# Step 1: Ensure Jira credentials are configured
export JIRA_BASE_URL="https://your-domain.atlassian.net"
export JIRA_EMAIL="your-email@example.com"
export JIRA_API_TOKEN="your-api-token"

# Step 2: Start the application
./gradlew :orchestrator-server:run

# Step 3: Trigger a scan via MCP tool or API
# Via MCP tool (from AI agent):
#   jira_project_sync { "projectKey": "MTO" }
#
# Via REST API (from dashboard):
#   POST http://localhost:8080/sync/start
#   Body: { "projectKey": "MTO" }

# Step 4: Monitor progress
# Via REST API:
#   GET http://localhost:8080/sync/status/MTO
# Via SQL:
#   SELECT * FROM jira_sync_state WHERE project_key = 'MTO';
```

### 2.2 System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Memory | 256 MB | 512 MB |
| Network | Stable connection to Jira | Low-latency connection |
| Jira API rate limit | 5 req/s | 10+ req/s |

---

## 3. Configuration

### 3.1 Scan Configuration

```yaml
sync:
  scanner:
    pageSize: 50              # Issues per API request (fixed by Jira)
    concurrency: 5            # Max concurrent page fetches (1-20)
    staleTimeout: 3600000     # ms before RUNNING state considered stale (1 hour)
    incrementalBuffer: 60000  # ms buffer for incremental JQL (1 minute)
```

### 3.2 Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SYNC_CONCURRENCY` | No | `5` | Max concurrent page fetch coroutines |
| `SYNC_STALE_TIMEOUT_MS` | No | `3600000` | Timeout before stuck RUNNING state is reset |

### 3.3 Configuration Examples

#### Default (Balanced)

```yaml
sync:
  scanner:
    concurrency: 5
```

#### High-Throughput (Large Projects)

```yaml
sync:
  scanner:
    concurrency: 10  # More parallel fetches
```

#### Conservative (Shared Jira Instance)

```yaml
sync:
  scanner:
    concurrency: 2   # Fewer parallel requests
```

---

## 4. Usage

### 4.1 Triggering a Scan

#### Via MCP Tool (AI Agent)

```json
// Tool: jira_project_sync
{
  "projectKey": "MTO",
  "fullSync": false
}
```

Response:
```json
{ "status": "started", "projectKey": "MTO", "estimatedIssues": 67 }
```

#### Via REST API

```bash
curl -X POST http://localhost:8080/sync/start \
  -H "Content-Type: application/json" \
  -d '{"projectKey": "MTO", "fullSync": false}'
```

#### Via Kotlin Code

```kotlin
val scanner: ProjectScanner by inject()

val result = scanner.scan("MTO", ScanOptions(
    concurrency = 5,
    forceFullScan = false
))
println("Synced ${result.syncedIssues}/${result.totalIssues} in ${result.duration}")
```

### 4.2 Scan Types

| Type | When Used | JQL Pattern |
|------|-----------|-------------|
| **Full Scan** | First sync or `forceFullScan=true` | `project = "MTO" ORDER BY updated DESC` |
| **Incremental** | Previous successful sync exists | `project = "MTO" AND updated > "2025-07-14 10:00" ORDER BY updated DESC` |
| **Resume** | Previous scan interrupted (RUNNING state) | Same JQL, starts from `last_offset` |

### 4.3 Monitoring Progress

#### Via REST API

```bash
curl http://localhost:8080/sync/status/MTO
```

Response:
```json
{
  "projectKey": "MTO",
  "status": "syncing",
  "progress": 67.5,
  "syncedIssues": 45,
  "totalIssues": 67,
  "phases": {
    "scan": { "status": "syncing", "progress": 67.5 }
  }
}
```

#### Via SQL

```sql
SELECT project_key, status, synced_issues, total_issues,
       ROUND(synced_issues::numeric / NULLIF(total_issues, 0) * 100, 1) AS progress_pct,
       updated_at
FROM jira_sync_state 
WHERE project_key = 'MTO';
```

### 4.4 Stopping a Scan

```bash
curl -X POST http://localhost:8080/sync/stop \
  -H "Content-Type: application/json" \
  -d '{"projectKey": "MTO"}'
```

The scan will complete its current page, save checkpoint, and transition to PAUSED.

### 4.5 Force Full Re-Scan

```json
// Ignores last_sync_time, scans all tickets from scratch
{
  "projectKey": "MTO",
  "fullSync": true
}
```

---

## 5. Administration

### 5.1 Viewing Scan History

```sql
-- Last sync time per project
SELECT project_key, status, last_sync_at, synced_issues, total_issues
FROM jira_sync_state
ORDER BY last_sync_at DESC;
```

### 5.2 Checking Cached Tickets

```sql
-- Count cached tickets per project
SELECT project_key, COUNT(*) as ticket_count
FROM jira_ticket_cache
GROUP BY project_key;

-- Recently synced tickets
SELECT issue_key, summary, status, synced_at
FROM jira_ticket_cache
WHERE project_key = 'MTO'
ORDER BY synced_at DESC
LIMIT 20;
```

### 5.3 Resetting a Scan

```sql
-- Reset to allow fresh full scan
UPDATE jira_sync_state 
SET status = 'IDLE', last_offset = 0, synced_issues = 0, 
    total_issues = 0, error_message = NULL
WHERE project_key = 'MTO';
```

### 5.4 Performance Tuning

| Scenario | Adjustment |
|----------|-----------|
| Jira rate limiting (429 errors) | Reduce `concurrency` to 2-3 |
| Slow scan for large project (10K+ tickets) | Increase `concurrency` to 10 |
| Network instability | Increase `JIRA_MAX_RETRIES` to 5 |
| Scan stuck in RUNNING | Check `staleTimeout`, manually reset if needed |

---

## 6. Troubleshooting

### 6.1 Common Issues

| # | Symptom | Cause | Solution |
|---|---------|-------|----------|
| 1 | `ScanAlreadyRunningException` | Another scan is active for same project | Wait for current scan to finish, or stop it first |
| 2 | Scan stuck in RUNNING | App crashed mid-scan | Wait for stale timeout (1 hour) or manually reset state to FAILED |
| 3 | `RetryExhaustedException` | Jira API consistently failing | Check Jira status, verify credentials, check network |
| 4 | 0 issues synced | Invalid project key or empty project | Verify project key exists in Jira |
| 5 | Incremental scan misses tickets | Clock skew between app and Jira | The 1-minute buffer (BR-08) handles most cases. Use `fullSync=true` if needed. |
| 6 | High memory usage during scan | Too many concurrent pages in memory | Reduce `concurrency` |

### 6.2 Error Scenarios

| Error | Severity | Auto-Recovery | Manual Action |
|-------|----------|---------------|---------------|
| Network timeout | Warning | Retry 3x via JiraRestClient | None needed unless persistent |
| HTTP 429 Rate Limit | Warning | Pause all coroutines, wait Retry-After | Reduce concurrency |
| HTTP 5xx Server Error | Warning | Retry 3x | Check Jira server status |
| JSON parse error (single ticket) | Info | Skip ticket, continue | Review skipped ticket manually |
| Database write error | Critical | Retry once, then checkpoint + FAILED | Check DB connectivity |

### 6.3 Logs

| Log Pattern | Meaning |
|-------------|---------|
| `INFO ProjectScanner - Starting full scan for MTO (concurrency=5)` | Scan initiated |
| `INFO ProjectScanner - Page 3/14 processed (150/670 issues)` | Progress update |
| `WARN ProjectScanner - Skipping MTO-99: parse error` | Single ticket skipped |
| `INFO ProjectScanner - Scan completed: 670 issues in 3m 24s` | Scan finished |
| `ERROR ProjectScanner - Scan failed for MTO: Network timeout` | Scan failed |

### 6.4 FAQ

**Q: How often should I run incremental scans?**
A: Every 5-15 minutes for active projects. The incremental scan only fetches tickets updated since last sync, so it's lightweight.

**Q: What happens if Jira is down during a scan?**
A: The JiraRestClient retries 3 times with exponential backoff. If all retries fail, the scan saves its checkpoint and marks as FAILED. Next scan attempt will resume from the checkpoint.

**Q: Does the scanner handle deleted tickets?**
A: Not currently. Deleted tickets remain in the cache. A future cleanup job will detect orphaned entries.

**Q: Can I scan multiple projects simultaneously?**
A: Yes. Each project has its own sync state. Multiple scans can run in parallel (one per project).

**Q: How much API quota does a full scan use?**
A: For a project with N tickets: `ceil(N / 50)` API calls. A 1000-ticket project uses ~20 API calls.

---

## 7. API Reference

### 7.1 ProjectScanner.scan()

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| projectKey | String | Yes | — | Jira project key (e.g., "MTO") |
| options.concurrency | Int | No | 5 | Max concurrent page fetches (1-20) |
| options.forceFullScan | Boolean | No | false | Skip incremental, do full scan |

**Returns:** `ScanResult`

| Field | Type | Description |
|-------|------|-------------|
| totalIssues | Int | Total tickets found |
| syncedIssues | Int | Tickets successfully synced |
| skippedIssues | Int | Tickets skipped (parse errors) |
| duration | Duration | Total scan time |
| scanType | ScanType | FULL or INCREMENTAL |
| status | ScanStatus | COMPLETED, FAILED, INTERRUPTED |

---

## 8. Appendix

### 8.1 Glossary

| Term | Definition |
|------|------------|
| Breadth-First Scan | Scan all tickets at metadata level before deep-fetching content |
| Checkpoint | Saved pagination offset for resumability |
| Incremental Scan | Only fetch tickets updated since last successful sync |
| JQL | Jira Query Language — used to filter and sort issues |
| Semaphore | Concurrency-limiting primitive controlling parallel requests |

### 8.2 Related Documents

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-17.docx |
| FSD | FSD-v1-MTO-17.docx |
| TDD | TDD-v1-MTO-17.docx |
| DPG | DPG-v1-MTO-17.docx |
