# Deployment Guide (DPG)

## MCPOrchestration — MTO-21: Web Dashboard – Sync Status & Monitoring

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-21 |
| Title | Web Dashboard – Sync Status & Monitoring |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2025-07-18 |
| Status | Final |
| Related TDD | TDD-v1-MTO-21.docx |
| Related FSD | FSD-v1-MTO-21.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-18 | DevOps Agent | Initial deployment guide for Web Dashboard |

---

## 1. Deployment Overview

### 1.1 Summary

This deployment introduces the **Web Dashboard** for monitoring Jira sync operations:

- REST API endpoints (`/sync/status`, `/sync/start`, `/sync/stop`)
- WebSocket handler (`/sync/live`) for real-time event streaming
- Static HTML dashboard (`sync-dashboard.html`) with vanilla JS
- `SyncEventBus` for publish/subscribe event broadcasting

### 1.2 Deployment Type

| Aspect | Value |
|--------|-------|
| Type | Rolling update (zero-downtime) |
| Risk Level | Low — read-heavy UI, no data mutations beyond sync trigger |
| Rollback Complexity | Low — remove JAR, endpoints disappear |
| Downtime Required | None |
| Data Migration | None |

### 1.3 Affected Components

| Component | Change Type | Impact |
|-----------|-------------|--------|
| `orchestrator-server` module | New classes added | New package `com.orchestrator.mcp.dashboard` |
| Ktor Application | New plugins | WebSocket plugin installed |
| Ktor Routing | Extended | `/sync/*` routes added |
| Static resources | New file | `sync-dashboard.html` |
| `AppModule.kt` (Koin DI) | Extended | DashboardModule registered |
| `application.yml` | Extended | New `dashboard` config section |
| Fat JAR | Rebuilt | Includes dashboard classes + static HTML |

---

## 2. Prerequisites

### 2.1 Pre-requisite Deployments

| Ticket | Component | Required | Status |
|--------|-----------|----------|--------|
| MTO-15 | Database Schema | ✅ | jira_sync_state table |
| MTO-17 | Project Scanner | ✅ | ProjectScanner for start/stop |
| MTO-18 | Ticket Crawler | Recommended | For crawl phase status |
| MTO-19 | Attachment Processor | Recommended | For attachment queue stats |

### 2.2 Software Dependencies

**No new external dependencies.** Uses existing Ktor WebSocket plugin (already in Ktor bundle).

### 2.3 Network Requirements

| Port | Protocol | Purpose |
|------|----------|---------|
| 8080 (default) | HTTP | REST API + static files |
| 8080 (default) | WebSocket | Real-time events (`/sync/live`) |

---

## 3. Pre-Deployment Checklist

| # | Check | Command / Action | Expected Result |
|---|-------|-----------------|-----------------|
| 1 | MTO-15 tables exist | `SELECT COUNT(*) FROM jira_sync_state;` | Returns count |
| 2 | Port 8080 available | `netstat -tlnp | grep 8080` | Only MCP orchestrator |
| 3 | Fat JAR built | `./gradlew buildFatJar` | `BUILD SUCCESSFUL` |
| 4 | All tests pass | `./gradlew test` | All pass |
| 5 | Static HTML in JAR | `jar tf build/libs/mcp-orchestrator-all.jar | grep "sync-dashboard"` | Present |
| 6 | Backup current JAR | Backup | Done |

---

## 4. Configuration Changes

### 4.1 New Configuration Section

```yaml
dashboard:
  enabled: true
  basePath: /sync
  maxWebSocketConnections: 50
  heartbeatInterval: 30s
  eventThrottleMs: 5000
```

### 4.2 Ktor Plugin (Auto-configured)

WebSocket plugin is installed automatically when dashboard module loads. No manual Ktor config needed.

---

## 5. Deployment Steps

### 5.1 Build Phase

```bash
git checkout MTO-21
git pull origin MTO-21
./gradlew clean test
./gradlew buildFatJar

# Verify dashboard classes + static resources
jar tf build/libs/mcp-orchestrator-all.jar | grep "dashboard/"
jar tf build/libs/mcp-orchestrator-all.jar | grep "sync-dashboard.html"
```

### 5.2 Deploy

```bash
# Stop application
sudo systemctl stop mcp-orchestrator

# Deploy JAR
cp build/libs/mcp-orchestrator-all.jar /opt/mcp-orchestrator/mcp-orchestrator-all.jar

# Update application.yml with dashboard config

# Start application
sudo systemctl start mcp-orchestrator
sleep 10
```

---

## 6. Post-Deployment Verification

| # | Check | Method | Expected |
|---|-------|--------|----------|
| 1 | Application started | Check logs | `Application started` |
| 2 | Dashboard routes registered | Check logs | `Dashboard routes configured at /sync` |
| 3 | REST API works | `curl http://localhost:8080/sync/status` | 200 OK + JSON |
| 4 | Static HTML served | `curl http://localhost:8080/sync/` | HTML content |
| 5 | WebSocket connects | Browser dev tools | WS connection established |
| 6 | Existing MCP tools work | MCP tools/list | Normal response |

### 6.1 Smoke Test

```bash
# Test REST API
curl -s http://localhost:8080/sync/status | jq .
# Expected: JSON array of project sync statuses

# Test static dashboard
curl -s http://localhost:8080/sync/ | head -5
# Expected: <!DOCTYPE html>...

# Test WebSocket (using wscat)
wscat -c ws://localhost:8080/sync/live
# Expected: Connection established, receives heartbeat
```

---

## 7. Rollback Plan

### 7.1 Rollback Steps

```bash
# Stop application
sudo systemctl stop mcp-orchestrator

# Restore JAR
cp /opt/mcp-orchestrator/mcp-orchestrator-all.jar.bak \
   /opt/mcp-orchestrator/mcp-orchestrator-all.jar

# Remove dashboard config from application.yml (optional)

# Restart
sudo systemctl start mcp-orchestrator
```

### 7.2 Impact of Rollback

- `/sync/*` endpoints return 404
- Dashboard HTML no longer served
- WebSocket connections close
- No data loss — dashboard is read-only

### 7.3 Quick Disable

```yaml
dashboard:
  enabled: false
```

---

## 8. Monitoring

| Log Pattern | Meaning | Action |
|-------------|---------|--------|
| `Dashboard routes configured at /sync` | Routes active | None |
| `WebSocket connection opened (total: {N})` | Client connected | None |
| `WebSocket connection closed (total: {N})` | Client disconnected | None |
| `Max WebSocket connections reached (50)` | Connection limit | Increase config or investigate |
| `SyncEvent broadcast to {N} clients` | Event sent | None |

---

## 9. Security Considerations

| Aspect | Implementation |
|--------|---------------|
| Authentication | None (internal tool — future enhancement) |
| CORS | Same-origin only |
| WebSocket auth | None (connection limit as guard) |
| Input validation | projectKey regex validation on all endpoints |
| Rate limiting | Event throttle (5s between broadcasts) |

---

## Appendix: Related Documents

| Document | Reference |
|----------|-----------|
| BRD | BRD-v1-MTO-21.docx |
| FSD | FSD-v1-MTO-21.docx |
| TDD | TDD-v1-MTO-21.docx |
| STP | STP-v1-MTO-21.docx |
| Test Report | STR-v1-MTO-21.docx |
