# Deployment Guide (DPG)

## MCP Tool Orchestration — MTO-12: Auto File Proxy (Input + Output)

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-12 |
| Title | Auto File Proxy — Transparent wrapper tools for upstream MCP tools accepting/returning file content |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2026-05-05 |
| Status | Draft |
| Related TDD | TDD-v1.0-MTO-12.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-05 | DevOps Agent | Initial deployment guide — auto-generated from TDD and project context |

---

## Sign-Off

| Name | Role | Signature and date |
|------|------|--------------------|
| Duc Nguyen | Dev Lead | ☐ Approved for deployment |
| | QA Lead | ☐ Testing completed |
| | Ops Lead | ☐ Infrastructure ready |

---

## 1. Overview

### 1.1 Feature Summary

The Auto File Proxy feature adds a transparent interception layer to the MCP Orchestrator Server that automatically handles file I/O operations for AI agents. It detects upstream MCP tools that accept or return file content (base64-encoded), generates wrapper tools that replace complex file parameters with simple `file_path` (STDIO mode) or `file_id` (HTTP/SSE mode), and manages file lifecycle through a PostgreSQL-backed registry with session-scoped cleanup.

Key capabilities:
- **Input Proxy:** AI agents pass `file_path` instead of base64 content — file reading and encoding handled transparently.
- **Output Proxy:** AI agents specify `output_path` — file saving/decoding handled transparently.
- **Lifecycle Management:** Automatic cleanup on startup, shutdown, per-request, and via background TTL job.

### 1.2 Deployment Scope

| Item | Type | Description |
|------|------|-------------|
| MCP Orchestrator Server | Modified | New `fileproxy` package added to existing fat JAR |
| PostgreSQL (`jira_assistant` DB) | Migration | New `file_proxy_registry` table + 3 indexes |
| Application Configuration | Modified | New `file-proxy` section in `application.yml` |
| File System | New | Temp directory for HTTP/SSE file uploads (`/tmp/mcp-file-proxy`) |

### 1.3 Target Environments

| Environment | URL | Deploy Order | Approval Required |
|-------------|-----|-------------|-------------------|
| DEV | localhost:8080 | 1st | No |
| SIT | sit-mcp.internal:8080 | 2nd | No |
| UAT | uat-mcp.internal:8080 | 3rd | QA Sign-off |
| PROD | mcp.internal:8080 | 4th | PM + Dev Lead Sign-off |

---

## 2. Prerequisites

### 2.1 Infrastructure

| Requirement | Status | Notes |
|-------------|--------|-------|
| JVM host with JDK 21 | Ready | Existing infrastructure — no change |
| PostgreSQL 16+ (`jira_assistant` DB) | Ready | Existing database — new table added |
| Disk space for temp directory | Ready | Minimum 2GB free at `/tmp/mcp-file-proxy` (PROD: `/var/mcp/file-proxy`) |
| Network access to upstream MCP servers | Ready | Existing connectivity — no change |

### 2.2 Software Dependencies

| Dependency | Version | Status |
|-----------|---------|--------|
| JDK | 21 | Installed |
| PostgreSQL | 16+ | Available |
| Kotlin Runtime | 2.3.20 | Bundled in fat JAR |
| Ktor (Netty) | 3.4.0 | Bundled in fat JAR |
| HikariCP | Existing | Bundled in fat JAR |
| kotlinx.io | 0.7.0 | Bundled in fat JAR |

### 2.3 Access Requirements

| Access | Type | Who Needs It |
|--------|------|-------------|
| SSH to application server | Key-based | DevOps team |
| PostgreSQL admin (`jira_assistant`) | Credentials | DBA / DevOps |
| File system write access | OS permissions | Application service account |

### 2.4 Backup Requirements

- [ ] Database backup of `jira_assistant` completed before migration
- [ ] Previous version of `mcp-orchestrator-all.jar` saved
- [ ] Current `application.yml` backed up

---

## 3. Pre-Deployment Checklist

| # | Item | Responsible | Status |
|---|------|-------------|--------|
| 1 | Code merged to release branch | Developer | ☐ |
| 2 | All unit tests passed (FileProxy* tests) | Developer | ☐ |
| 3 | All integration tests passed (Testcontainers) | QA | ☐ |
| 4 | E2E tests passed (E2eFileProxy* tests) | QA | ☐ |
| 5 | SIT/UAT sign-off obtained | QA + BA | ☐ |
| 6 | Database backup completed | DBA | ☐ |
| 7 | Configuration files prepared per environment | DevOps | ☐ |
| 8 | Feature flags reviewed (`file-proxy.enabled`) | Developer | ☐ |
| 9 | Temp directory created with correct permissions | DevOps | ☐ |
| 10 | Rollback plan reviewed by team | Team | ☐ |
| 11 | Deployment window confirmed | PM | ☐ |

---

## 4. Database Migration

### 4.1 Migration Scripts

| Order | Script | Description | Estimated Time |
|-------|--------|-------------|----------------|
| 1 | `V2__create_file_proxy_registry.sql` | Create `file_proxy_registry` table + 3 indexes + constraints | < 1 second |

### 4.2 Execution Steps

```bash
# Step 1: Backup database
pg_dump -h ${DB_HOST} -U ${DB_USER} -d jira_assistant -F c -f jira_assistant_backup_$(date +%Y%m%d_%H%M%S).dump

# Step 2: Connect to database
psql -h ${DB_HOST} -U ${DB_USER} -d jira_assistant

# Step 3: Run migration script
\i /path/to/V2__create_file_proxy_registry.sql
```

**Note:** The application also executes this migration automatically on startup via `FileProxyMigration` class using `CREATE TABLE IF NOT EXISTS`. Manual execution is recommended for PROD to verify before application start.

### 4.3 Migration SQL

```sql
-- V2__create_file_proxy_registry.sql
CREATE TABLE IF NOT EXISTS file_proxy_registry (
    file_id         UUID            NOT NULL,
    session_id      UUID            NOT NULL,
    file_path       VARCHAR(500)    NOT NULL,
    file_name       VARCHAR(255),
    file_size       BIGINT,
    real_tool_name  VARCHAR(255),
    upstream_server VARCHAR(255),
    direction       VARCHAR(10)     NOT NULL DEFAULT 'INPUT',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP,

    CONSTRAINT pk_file_proxy_registry PRIMARY KEY (file_id),
    CONSTRAINT chk_direction CHECK (direction IN ('INPUT', 'OUTPUT')),
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED', 'EXPIRED'))
);

COMMENT ON TABLE file_proxy_registry IS 'Tracks file proxy operations for lifecycle management. Records are transient — deleted after processing.';
COMMENT ON COLUMN file_proxy_registry.session_id IS 'Running session UUID — changes on every server restart';
COMMENT ON COLUMN file_proxy_registry.direction IS 'INPUT = file read for upstream, OUTPUT = file save from upstream';

-- Indexes
CREATE INDEX IF NOT EXISTS idx_file_proxy_session ON file_proxy_registry(session_id);
CREATE INDEX IF NOT EXISTS idx_file_proxy_status ON file_proxy_registry(status);
CREATE INDEX IF NOT EXISTS idx_file_proxy_created ON file_proxy_registry(created_at);
```

### 4.4 Verification Queries

```sql
-- Verify table created
SELECT table_name, table_type 
FROM information_schema.tables 
WHERE table_name = 'file_proxy_registry';

-- Verify columns
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns 
WHERE table_name = 'file_proxy_registry'
ORDER BY ordinal_position;

-- Verify indexes
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE tablename = 'file_proxy_registry';

-- Verify constraints
SELECT constraint_name, constraint_type 
FROM information_schema.table_constraints 
WHERE table_name = 'file_proxy_registry';
```

### 4.5 Rollback Scripts

```sql
-- V2__rollback_file_proxy_registry.sql
DROP TABLE IF EXISTS file_proxy_registry;
```

---

## 5. Application Deployment

### 5.1 Build Artifact

| Artifact | Build Command | Output |
|----------|--------------|--------|
| Fat JAR | `./gradlew buildFatJar` | `build/libs/mcp-orchestrator-all.jar` |

### 5.2 Deployment Steps

| Step | Action | Command | Verification |
|------|--------|---------|-------------|
| 1 | Create temp directory | `mkdir -p /tmp/mcp-file-proxy && chmod 700 /tmp/mcp-file-proxy` | `ls -la /tmp/mcp-file-proxy` |
| 2 | Stop existing service | `systemctl stop mcp-orchestrator` or `kill $(pgrep -f mcp-orchestrator-all.jar)` | `pgrep -f mcp-orchestrator` returns empty |
| 3 | Backup current JAR | `cp mcp-orchestrator-all.jar mcp-orchestrator-all.jar.bak` | Backup file exists |
| 4 | Deploy new artifact | `cp build/libs/mcp-orchestrator-all.jar /opt/mcp-orchestrator/` | File size matches build output |
| 5 | Update configuration | Copy updated `application.yml` to deployment directory | Diff confirms changes |
| 6 | Start service | `java -jar /opt/mcp-orchestrator/mcp-orchestrator-all.jar` | Process running |
| 7 | Verify startup | Check logs for `[FileProxy] Startup cleanup completed` | Log entry present within 30s |
| 8 | Health check | Verify application responds to MCP protocol | Successful tool discovery |

### 5.3 Startup Verification

After starting the application, verify these log entries appear:

```
INFO  [main] [FileProxy] File proxy feature enabled. Config: maxSize=50MB, ttl=60min, temp=/tmp/mcp-file-proxy
INFO  [main] [FileProxy] Startup cleanup: records=0, files=0, bytes=0
INFO  [main] [FileProxy] Detected: tool={name}, param={param}, method={method}, confidence={score}
INFO  [main] [FileProxy] Wrapper created: tool={name}, direction={dir}
INFO  [main] [FileProxy] Background cleanup job started: interval=15min, ttl=60min
```

---

## 6. Configuration Changes

### 6.1 New Configuration Section

The following section is added to `application.yml` under the `orchestrator` key:

```yaml
orchestrator:
  file-proxy:
    enabled: true
    max-size-mb: 50
    temp-directory: "/tmp/mcp-file-proxy"
    ttl-minutes: 60
    cleanup-interval-minutes: 15
    shutdown-timeout-seconds: 30
    input-proxy-enabled: true
    output-proxy-enabled: true
    runtime-detection-enabled: true
    servers: {}
```

### 6.2 Environment-Specific Configuration

| Property | DEV | SIT | UAT | PROD |
|----------|-----|-----|-----|------|
| `file-proxy.enabled` | true | true | true | true |
| `file-proxy.max-size-mb` | 50 | 50 | 50 | 50 |
| `file-proxy.temp-directory` | `/tmp/mcp-file-proxy` | `/tmp/mcp-file-proxy` | `/tmp/mcp-file-proxy` | `/var/mcp/file-proxy` |
| `file-proxy.ttl-minutes` | 60 | 60 | 60 | 60 |
| `file-proxy.cleanup-interval-minutes` | 15 | 15 | 15 | 15 |
| `file-proxy.shutdown-timeout-seconds` | 30 | 30 | 30 | 30 |

### 6.3 Per-Server Overrides (Optional)

For upstream servers that handle larger files:

```yaml
orchestrator:
  file-proxy:
    servers:
      pdf-tools:
        max-size-mb: 100
      image-processor:
        max-size-mb: 200
```

### 6.4 Feature Flags

| Flag | DEV | SIT | UAT | PROD (Initial) | PROD (After Verification) |
|------|-----|-----|-----|----------------|---------------------------|
| `file-proxy.enabled` | true | true | true | true | true |
| `file-proxy.input-proxy-enabled` | true | true | true | true | true |
| `file-proxy.output-proxy-enabled` | true | true | true | true | true |
| `file-proxy.runtime-detection-enabled` | true | true | true | false | true |

**Note:** For PROD initial deployment, consider disabling `runtime-detection-enabled` until static detection is verified working correctly. Enable after first deployment verification.

### 6.5 Existing Environment Variables (Unchanged)

| Variable | Description | Required |
|----------|-------------|----------|
| `OPENAI_API_KEY` | OpenAI API key for embeddings | Yes |
| `DB_USER` | PostgreSQL username | Yes |
| `DB_PASSWORD` | PostgreSQL password | Yes |
| `DB_HOST` | PostgreSQL host | Yes (default: localhost) |
| `DB_PORT` | PostgreSQL port | Yes (default: 5432) |

---

## 7. Post-Deployment Verification

### 7.1 Health Checks

| Check | Method | Expected Result | Timeout |
|-------|--------|-----------------|---------|
| Application startup | Check process running | PID exists | 60s |
| Database connectivity | `SELECT 1` via HikariCP | Success within 100ms | 10s |
| Temp directory writable | `Files.isWritable(tempDir)` | true | 5s |
| File proxy initialized | Log entry `[FileProxy] Startup cleanup completed` | Present | 30s |
| Tool discovery | MCP `find_tools` call | Returns tool list (including wrappers) | 30s |

### 7.2 Smoke Tests

| # | Scenario | Steps | Expected Result |
|---|----------|-------|-----------------|
| 1 | Input proxy — STDIO mode | Call wrapper tool with `file_path` pointing to a test file | Upstream tool receives base64 content, response returned successfully |
| 2 | Input proxy — file not found | Call wrapper tool with non-existent `file_path` | `FILE_NOT_FOUND` error returned |
| 3 | Input proxy — file too large | Call wrapper tool with file > 50MB | `FILE_TOO_LARGE` error returned |
| 4 | Output proxy — save to path | Call output-proxied tool with `output_path` | File saved to specified path |
| 5 | Tool discovery — wrappers visible | Call `find_tools` | Wrapper tools shown, original tools hidden |
| 6 | Feature disabled | Set `file-proxy.enabled: false`, restart | Original tools visible, no wrappers |

### 7.3 Log Verification

| Log Entry | Level | Expected | Location |
|-----------|-------|----------|----------|
| `[FileProxy] File proxy feature enabled` | INFO | Within 10s of start | stdout |
| `[FileProxy] Startup cleanup completed` | INFO | Within 30s of start | stdout |
| `[FileProxy] Wrapper created: tool=*` | INFO | After tool discovery | stdout |
| `[FileProxy] Background cleanup job started` | INFO | After startup | stdout |
| No `ERROR` or `FATAL` entries | — | No errors in first 5 minutes | stdout |

### 7.4 Monitoring Dashboard

- [ ] `file_proxy_operations_total` counter incrementing on proxy calls
- [ ] `file_proxy_duration_ms` histogram showing p95 < 1000ms
- [ ] `file_proxy_registry_size` gauge < 100 (steady state)
- [ ] `file_proxy_errors_total` counter = 0 (no errors)
- [ ] `file_proxy_temp_dir_size_bytes` gauge < 500MB
- [ ] No unexpected alerts triggered

---

## 8. Rollback Plan

### 8.1 Rollback Decision Criteria

| Condition | Action |
|-----------|--------|
| Application fails to start after deployment | Immediate rollback |
| Database migration fails | Rollback migration, deploy previous JAR |
| Health check fails after 60s | Immediate rollback |
| Error rate > 5% on proxy operations | Immediate rollback |
| Performance degradation > 50% (p95 > 2000ms) | Immediate rollback |
| File proxy errors affecting non-proxy tool calls | Immediate rollback |
| Minor issue with specific upstream server proxy | Disable per-server proxy via config, no full rollback |

### 8.2 Rollback Steps

| Step | Action | Command | Verification |
|------|--------|---------|-------------|
| 1 | Stop application | `systemctl stop mcp-orchestrator` | Process stopped |
| 2 | Rollback database | `psql -h ${DB_HOST} -U ${DB_USER} -d jira_assistant -f V2__rollback_file_proxy_registry.sql` | Table dropped |
| 3 | Restore previous JAR | `cp mcp-orchestrator-all.jar.bak mcp-orchestrator-all.jar` | File restored |
| 4 | Restore previous config | `cp application.yml.bak application.yml` | Config restored |
| 5 | Start previous version | `systemctl start mcp-orchestrator` | Process running |
| 6 | Verify rollback | Check logs, run tool discovery | Original tools visible, no proxy |

### 8.3 Quick Rollback (Config-Only)

If the issue is isolated to the file proxy feature and does not affect core functionality:

```yaml
# Set in application.yml and restart:
orchestrator:
  file-proxy:
    enabled: false
```

This disables the entire file proxy without code rollback:
- No detection runs
- No wrappers generated
- Original tools remain visible
- Zero overhead on existing functionality

### 8.4 Rollback Time Estimate

| Action | Estimated Time |
|--------|---------------|
| Stop application | 30 seconds |
| Database rollback | < 5 seconds |
| Restore JAR + config | 30 seconds |
| Start application | 60 seconds |
| Verification | 2 minutes |
| **Total** | **~4 minutes** |

---

## 9. Environment-Specific Notes

### 9.1 DEV

- Auto-deployed on merge to `develop` branch
- Temp directory: `/tmp/mcp-file-proxy` (auto-created by application)
- Database migration runs automatically on startup
- Feature flags: all enabled
- No approval required

### 9.2 SIT

- Deployed after DEV verification passes
- Same configuration as DEV
- Integration tests run against real upstream MCP servers
- Verify proxy works with actual upstream tools (pdf-tools, image-processor)

### 9.3 UAT

- Deployed after SIT sign-off
- QA team performs manual verification of smoke tests
- Business stakeholders verify tool behavior unchanged from agent perspective
- Feature flags: all enabled

### 9.4 PROD

- **Deployment Window:** Weekday, 10:00–12:00 (low traffic period)
- **Approval Required From:** Duc Nguyen (Dev Lead), QA Lead
- **Communication Plan:** Notify team in Slack #mcp-releases 30 min before deployment
- **On-Call Contact:** DevOps team
- **Initial Config:** `runtime-detection-enabled: false` (enable after 24h monitoring)
- **Temp Directory:** `/var/mcp/file-proxy` (dedicated partition with 10GB space)
- **Monitoring:** Watch `file_proxy_errors_total` for first 24 hours

---

## 10. Appendix

### Contacts

| Role | Name | Contact |
|------|------|---------|
| Dev Lead | Duc Nguyen | Project Lead |
| DevOps | DevOps Agent | Automated |
| DBA | DBA Team | Internal |

### Related Tickets

| Ticket | Summary | Relationship |
|--------|---------|-------------|
| MTO-12 | Auto File Proxy (Input + Output) | Main ticket |
| MTO-10 | Base MCP Orchestrator | Prerequisite (must be deployed) |

### File Locations

| File | Path |
|------|------|
| Fat JAR | `build/libs/mcp-orchestrator-all.jar` |
| Application config | `src/main/resources/application.yml` |
| Migration script | `src/main/resources/db/V2__create_file_proxy_registry.sql` |
| Rollback script | `src/main/resources/db/V2__rollback_file_proxy_registry.sql` |
