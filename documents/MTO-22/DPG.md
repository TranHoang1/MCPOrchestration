# Deployment Guide (DPG)

## MCPOrchestration — MTO-22: 3D Graph Visualization – Force-Directed Graph Views

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-22 |
| Title | 3D Graph Visualization – Force-Directed Graph Views |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2025-07-18 |
| Status | Final |
| Related TDD | TDD-v1-MTO-22.docx |
| Related FSD | FSD-v1-MTO-22.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-18 | DevOps Agent | Initial deployment guide for 3D Graph Visualization |

---

## 1. Deployment Overview

### 1.1 Summary

This deployment introduces the **3D Graph Visualization** module:

- REST API endpoints (`/sync/graph/{projectKey}`, `/sync/graph/{projectKey}/{issueKey}`)
- 7 view mode strategies (Hierarchy, Dependency, Team, Complexity, Functional, Business, Timeline)
- Static HTML viewer (`graph-viewer.html`) using 3d-force-graph.js (CDN)
- `GraphService` for server-side view transformation
- `GraphDataRepository` for BFS traversal queries

### 1.2 Deployment Type

| Aspect | Value |
|--------|-------|
| Type | Rolling update (zero-downtime) |
| Risk Level | Low — read-only API, static frontend |
| Rollback Complexity | Low — remove JAR, endpoints disappear |
| Downtime Required | None |
| Data Migration | None |

### 1.3 Affected Components

| Component | Change Type | Impact |
|-----------|-------------|--------|
| `orchestrator-server` module | New classes added | New package `com.orchestrator.mcp.graph` |
| Ktor Routing | Extended | `/sync/graph/*` routes added |
| Static resources | New file | `graph-viewer.html` |
| `AppModule.kt` (Koin DI) | Extended | GraphModule registered |
| `application.yml` | Extended | New `graph` config section |
| Fat JAR | Rebuilt | Includes graph classes + static HTML |

---

## 2. Prerequisites

### 2.1 Pre-requisite Deployments

| Ticket | Component | Required | Status |
|--------|-----------|----------|--------|
| MTO-15 | Database Schema | ✅ | jira_ticket_cache table |
| MTO-18 | Ticket Crawler | ✅ | jira_ticket_graph table populated |
| MTO-21 | Web Dashboard | Recommended | Shared Ktor routing infrastructure |

### 2.2 Software Dependencies

**No new server-side dependencies.** Frontend uses CDN-hosted 3d-force-graph.js.

### 2.3 Network Requirements

| Requirement | Purpose | Notes |
|-------------|---------|-------|
| Outbound HTTPS (CDN) | Load 3d-force-graph.js from unpkg.com | Client browser needs internet |
| Port 8080 | Serve API + static files | Same as existing |

---

## 3. Pre-Deployment Checklist

| # | Check | Command / Action | Expected Result |
|---|-------|-----------------|-----------------|
| 1 | Graph data exists | `SELECT COUNT(*) FROM jira_ticket_graph;` | > 0 |
| 2 | Ticket cache populated | `SELECT COUNT(*) FROM jira_ticket_cache;` | > 0 |
| 3 | Fat JAR built | `./gradlew buildFatJar` | `BUILD SUCCESSFUL` |
| 4 | All tests pass | `./gradlew test` | All pass |
| 5 | Static HTML in JAR | `jar tf ... | grep "graph-viewer"` | Present |
| 6 | Backup current JAR | Backup | Done |

---

## 4. Configuration Changes

### 4.1 New Configuration Section

```yaml
graph:
  enabled: true
  maxNodes: 1000          # Cap response at 1000 nodes
  defaultDepth: 2         # Default BFS depth for subgraph
  maxDepth: 5             # Maximum allowed depth
```

---

## 5. Deployment Steps

### 5.1 Build Phase

```bash
git checkout MTO-22
git pull origin MTO-22
./gradlew clean test
./gradlew buildFatJar

# Verify graph classes + static resources
jar tf build/libs/mcp-orchestrator-all.jar | grep "graph/"
jar tf build/libs/mcp-orchestrator-all.jar | grep "graph-viewer.html"
```

### 5.2 Deploy

```bash
# Stop application
sudo systemctl stop mcp-orchestrator

# Deploy JAR
cp build/libs/mcp-orchestrator-all.jar /opt/mcp-orchestrator/mcp-orchestrator-all.jar

# Update application.yml with graph config

# Start application
sudo systemctl start mcp-orchestrator
sleep 10
```

---

## 6. Post-Deployment Verification

| # | Check | Method | Expected |
|---|-------|--------|----------|
| 1 | Application started | Check logs | `Application started` |
| 2 | Graph routes registered | Check logs | `Graph routes configured` |
| 3 | Graph API works | `curl http://localhost:8080/sync/graph/MTO` | 200 OK + JSON |
| 4 | Graph viewer served | `curl http://localhost:8080/static/graph-viewer.html` | HTML content |
| 5 | Existing features work | MCP tools/list + /sync/status | Normal responses |

### 6.1 Smoke Test

```bash
# Test full graph API
curl -s "http://localhost:8080/sync/graph/MTO?view=hierarchy" | jq '.metadata'
# Expected: {"totalNodes":N,"totalEdges":N,"projectKey":"MTO",...}

# Test subgraph API
curl -s "http://localhost:8080/sync/graph/MTO/MTO-15?depth=2&view=dependency" | jq '.metadata'
# Expected: {"centerIssue":"MTO-15","depth":2,...}

# Test graph viewer HTML
curl -s http://localhost:8080/static/graph-viewer.html | grep "3d-force-graph"
# Expected: script tag with CDN URL
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

# Remove graph config from application.yml (optional)

# Restart
sudo systemctl start mcp-orchestrator
```

### 7.2 Impact of Rollback

- `/sync/graph/*` endpoints return 404
- Graph viewer HTML no longer served
- No data loss — graph API is read-only

### 7.3 Quick Disable

```yaml
graph:
  enabled: false
```

---

## 8. Monitoring

| Log Pattern | Meaning | Action |
|-------------|---------|--------|
| `Graph routes configured` | Routes active | None |
| `Graph query: {project}, {N} nodes` | API called | None |
| `BFS traversal: {issueKey}, depth {N}` | Subgraph query | None |
| `Graph response capped at 1000 nodes` | Large project | Expected behavior |
| `GraphDataRepository query timeout` | Slow DB | Check indexes, query plan |

---

## 9. Performance Considerations

| Scenario | Expected Response Time | Notes |
|----------|----------------------|-------|
| Full graph (100 nodes) | < 200ms | Direct DB query |
| Full graph (500 nodes) | < 500ms | May need index optimization |
| Subgraph (depth 2) | < 200ms | BFS with CTE |
| Subgraph (depth 5) | < 1s | Deep traversal |
| Graph viewer load | < 2s | CDN dependency for 3d-force-graph.js |

---

## 10. Security Considerations

| Aspect | Implementation |
|--------|---------------|
| Authentication | None (internal tool — future enhancement) |
| Input validation | projectKey regex, depth range 1-5 |
| CDN dependency | 3d-force-graph.js loaded from unpkg.com |
| Data exposure | Only ticket metadata (key, summary, status, type) |

---

## Appendix: Related Documents

| Document | Reference |
|----------|-----------|
| BRD | BRD-v1-MTO-22.docx |
| FSD | FSD-v1-MTO-22.docx |
| TDD | TDD-v1-MTO-22.docx |
| STP | STP-v1-MTO-22.docx |
| Test Report | STR-v1-MTO-22.docx |
