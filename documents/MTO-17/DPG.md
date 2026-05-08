# Deployment Guide (DPG)

## MCPOrchestration — MTO-17: Project Scanner — Breadth-First Incremental Scan

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-17 |
| Title | Project Scanner — Breadth-First Incremental Scan |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2025-07-18 |
| Status | Final |
| Related TDD | TDD-v1-MTO-17.docx |
| Related FSD | FSD-v1-MTO-17.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-18 | DevOps Agent | Initial deployment guide for Project Scanner module |

---

## 1. Deployment Overview

### 1.1 Summary

This deployment introduces the **ProjectScanner** module — a coroutine-based service that performs breadth-first scanning of Jira projects, fetching lightweight ticket metadata and caching it in PostgreSQL. The change adds:

- `ProjectScanner` service with coroutine-based concurrency (Semaphore + SupervisorJob)
- `PageFetcher` for concurrent Jira API page retrieval
- `BatchUpserter` for efficient INSERT ON CONFLICT operations
- `JqlBuilder` for full/incremental/resumed scan JQL construction
- `MetadataParser` for Jira response → domain model mapping
- `ScannerModule` Koin DI registration
- New `scanner` configuration section in `application.yml`

### 1.2 Deployment Type

| Aspect | Value |
|--------|-------|
| Type | Rolling update (zero-downtime) |
| Risk Level | Low — additive code only, no schema changes |
| Rollback Complexity | Low — remove JAR, restart with previous version |
| Downtime Required | None |
| Data Migration | None (uses existing MTO-15 tables) |

### 1.3 Affected Components

| Component | Change Type | Impact |
|-----------|-------------|--------|
| `orchestrator-server` module | New classes added | New package `com.orchestrator.mcp.scanner` |
| `AppModule.kt` (Koin DI) | Extended | ScannerModule registered |
| `application.yml` | Extended | New `scanner` config section |
| Fat JAR (`mcp-orchestrator-all.jar`) | Rebuilt | Includes scanner classes |
| PostgreSQL database | No change | Uses existing MTO-15 tables |

---

## 2. Prerequisites

### 2.1 Infrastructure Requirements

| Requirement | Minimum | Recommended | Notes |
|-------------|---------|-------------|-------|
| PostgreSQL | 16.0 | 16.x latest | MTO-15 tables must exist |
| JVM | 21 | 21 LTS | GraalVM or OpenJDK |
| RAM (JVM) | 512 MB | 1 GB | Scanner adds ~50MB overhead during scan |
| Network | Outbound HTTPS | — | Access to Jira Cloud API |

### 2.2 Software Dependencies

| Dependency | Version | Already Present | Notes |
|------------|---------|-----------------|-------|
| Kotlin | 2.3.20 | ✅ Yes | No change |
| Ktor | 3.4.0 | ✅ Yes | No change |
| kotlinx.coroutines | 1.10.2 | ✅ Yes | No change |
| kotlinx.serialization | 1.8.1 | ✅ Yes | No change |
| Exposed ORM | 0.61.0 | ✅ Yes | No change |
| Koin | 4.1.1 | ✅ Yes | No change |
| kotlinx.datetime | 0.6.2 | ✅ Yes | No change |

**No new dependencies introduced.** All libraries are already in `build.gradle.kts`.

### 2.3 Pre-requisite Deployments

| Ticket | Component | Required | Status |
|--------|-----------|----------|--------|
| MTO-15 | Database Schema & Sync State | ✅ Must be deployed | Tables: jira_sync_state, jira_ticket_cache |
| MTO-16 | Jira REST Client | ✅ Must be deployed | JiraRestClient service |

### 2.4 Access Requirements

| Access | Purpose | Who |
|--------|---------|-----|
| Jira Cloud API | Fetch project issues | Application (via API token) |
| PostgreSQL | Read/write sync state + ticket cache | Application |
| Server filesystem | Deploy JAR file | DevOps |

---

## 3. Pre-Deployment Checklist

| # | Check | Command / Action | Expected Result |
|---|-------|-----------------|-----------------|
| 1 | MTO-15 tables exist | `SELECT COUNT(*) FROM jira_sync_state;` | Returns 0 or more |
| 2 | MTO-16 JiraRestClient works | Check logs for successful Jira API call | No auth errors |
| 3 | Jira API credentials configured | Verify `JIRA_EMAIL` and `JIRA_API_TOKEN` env vars | Set |
| 4 | Fat JAR built successfully | `./gradlew buildFatJar` | `BUILD SUCCESSFUL` |
| 5 | All tests pass | `./gradlew test` | All tests pass |
| 6 | JAR file exists | `ls build/libs/mcp-orchestrator-all.jar` | File present |
| 7 | Backup current JAR | `cp mcp-orchestrator-all.jar mcp-orchestrator-all.jar.bak` | Backup created |
| 8 | Scanner config added | Check `application.yml` has `scanner:` section | Present |

---

## 4. Configuration Changes

### 4.1 New Configuration Section

Add to `application.yml`:

```yaml
scanner:
  concurrency: 5          # Max concurrent Jira API requests (1-20)
  pageSize: 50            # Fixed at 50 (Jira best practice)
  staleTimeout: 3600      # Seconds before RUNNING state considered stale
  syncBufferMinutes: 1    # Buffer subtracted from last_sync_time for incremental
  enabled: true           # Enable/disable scanner
  autoResume: true        # Auto-resume interrupted scans on startup
```

### 4.2 Existing Configuration (No Changes)

```yaml
orchestrator:
  database:
    url: "jdbc:postgresql://localhost:5432/mcp_orchestrator"
    username: "${DB_USERNAME:postgres}"
    password: "${DB_PASSWORD:postgres}"
    pool_size: 10
```

---

## 5. Deployment Steps

### 5.1 Build Phase

```bash
# Step 1: Pull latest code
git checkout MTO-17
git pull origin MTO-17

# Step 2: Run full test suite
./gradlew clean test

# Step 3: Build fat JAR
./gradlew buildFatJar

# Step 4: Verify JAR contains scanner classes
jar tf build/libs/mcp-orchestrator-all.jar | grep "scanner/"
# Expected: com/orchestrator/mcp/scanner/ProjectScanner.class
#           com/orchestrator/mcp/scanner/ProjectScannerImpl.class
#           com/orchestrator/mcp/scanner/PageFetcher.class
#           com/orchestrator/mcp/scanner/BatchUpserter.class
#           com/orchestrator/mcp/scanner/JqlBuilder.class
#           com/orchestrator/mcp/scanner/MetadataParser.class
```

### 5.2 Application Deployment

```bash
# Step 1: Stop current application
sudo systemctl stop mcp-orchestrator

# Step 2: Deploy new JAR
cp build/libs/mcp-orchestrator-all.jar /opt/mcp-orchestrator/mcp-orchestrator-all.jar

# Step 3: Update application.yml (add scanner section if not present)
# Verify scanner config exists in /opt/mcp-orchestrator/application.yml

# Step 4: Start application
sudo systemctl start mcp-orchestrator

# Step 5: Wait for startup
sleep 10
```

---

## 6. Post-Deployment Verification

### 6.1 Health Checks

| # | Check | Method | Expected |
|---|-------|--------|----------|
| 1 | Application started | Check logs for `Application started` | Present |
| 2 | Scanner module loaded | Check logs for `ScannerModule registered` | Present |
| 3 | No startup errors | `grep -i "error\|exception" app.log` | No critical errors |
| 4 | Existing features work | Send MCP `tools/list` request | Returns tool list |

### 6.2 Smoke Test

```bash
# Test: Verify scanner can be invoked (will start a scan)
# This requires MTO-20 MCP tools to be deployed, or test via internal API

# Verify DB connectivity for scanner
psql -h localhost -U postgres -d mcp_orchestrator -c \
  "SELECT * FROM jira_sync_state LIMIT 1;"
# Expected: Returns rows or empty (no error)
```

---

## 7. Rollback Plan

### 7.1 Rollback Triggers

| Condition | Action |
|-----------|--------|
| Application fails to start | Rollback immediately |
| Scanner causes excessive Jira API calls | Disable via config: `scanner.enabled: false` |
| Performance degradation | Rollback or disable scanner |
| Existing features broken | Rollback immediately |

### 7.2 Rollback Steps

```bash
# Step 1: Stop application
sudo systemctl stop mcp-orchestrator

# Step 2: Restore previous JAR
cp /opt/mcp-orchestrator/mcp-orchestrator-all.jar.bak \
   /opt/mcp-orchestrator/mcp-orchestrator-all.jar

# Step 3: Remove scanner config from application.yml (optional)
# Or set scanner.enabled: false

# Step 4: Restart
sudo systemctl start mcp-orchestrator

# Step 5: Verify
curl -s localhost:8080/health  # or MCP tools/list
```

### 7.3 Quick Disable (No Rollback)

```yaml
# Just disable scanner without rolling back code:
scanner:
  enabled: false
```

Restart application. Scanner won't start.

---

## 8. Monitoring & Alerts

### 8.1 Log Monitoring

| Log Pattern | Meaning | Action |
|-------------|---------|--------|
| `Scan started for project {key}` | Scan initiated | None (expected) |
| `Scan completed: {N} issues synced` | Scan finished | None (expected) |
| `Rate limited by Jira API` | 429 response | Monitor frequency |
| `Scan failed after {N} retries` | Persistent failure | Check Jira API status |
| `ScanAlreadyRunningException` | Duplicate scan attempt | None (expected guard) |

---

## 9. Security Considerations

| Aspect | Implementation |
|--------|---------------|
| Jira credentials | Environment variables (`JIRA_EMAIL`, `JIRA_API_TOKEN`) |
| API rate limiting | Semaphore (max 5 concurrent) + Jira 429 handling |
| Data sensitivity | Only ticket metadata cached (summary, status, type) |
| Network | HTTPS to Jira Cloud, local DB connection |

---

## Appendix: Related Documents

| Document | Reference |
|----------|-----------|
| BRD | BRD-v1-MTO-17.docx |
| FSD | FSD-v1-MTO-17.docx |
| TDD | TDD-v1-MTO-17.docx |
| STP | STP-v1-MTO-17.docx |
| Test Report | STR-v1-MTO-17.docx |
