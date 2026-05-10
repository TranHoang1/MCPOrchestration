# Deployment Guide (DPG)

## MCPOrchestration — MTO-36: KB Refinery — Feature Network Mapping

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-36 |
| Title | KB Refinery — Feature Network Mapping |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related TDD | TDD-v1-MTO-36.docx |

---

## 1. Overview

### 1.1 Feature Summary

The Feature Network Mapping service builds relationship graphs from semantic entity links (MTO-35) and provides D3.js-ready JSON output. It adds a `NetworkService` that performs BFS traversal for neighborhood queries and full-network graph construction.

### 1.2 Deployment Scope

| Item | Type | Description |
|------|------|-------------|
| MCP Orchestrator Server | Modified | New `network` package added to fat JAR |
| Application Configuration | Modified | New `network` section in `application.yml` |
| No DB migration | — | Uses existing `entity_links` table from MTO-35 |
| No new infrastructure | — | No additional services required |

---

## 2. Prerequisites

### 2.1 Dependencies

| Requirement | Status | Notes |
|-------------|--------|-------|
| MTO-35 deployed | Required | entity_links table must exist |
| JVM host with JDK 21 | Ready | Existing infrastructure |
| PostgreSQL with entity_links | Ready | Created by MTO-35 migration |

### 2.2 Pre-Deployment Checklist

| # | Check | Command | Expected |
|---|-------|---------|----------|
| 1 | MTO-35 is deployed | `psql -c "SELECT COUNT(*) FROM entity_links"` | No error |
| 2 | Current version running | `curl localhost:8080/health` | 200 OK |
| 3 | Backup current JAR | `cp mcp-orchestrator-all.jar mcp-orchestrator-all.jar.bak` | Done |

---

## 3. Deployment Steps

### Step 1: Update Configuration

Add to `application.yml`:

```yaml
orchestrator:
  network:
    enabled: true
    max-hops: 5
    max-nodes: 1000
    truncation-threshold: 500
```

### Step 2: Deploy New JAR

```bash
systemctl stop mcp-orchestrator
cp build/libs/mcp-orchestrator-all.jar /opt/mcp/mcp-orchestrator-all.jar
systemctl start mcp-orchestrator
```

### Step 3: Verify Startup

```bash
journalctl -u mcp-orchestrator --since "1 minute ago" | grep -i "network"
# Expected: "NetworkModule registered"

curl localhost:8080/health
# Expected: 200 OK
```

---

## 4. Post-Deployment Verification

| # | Check | Command | Expected |
|---|-------|---------|----------|
| 1 | Service started | `systemctl status mcp-orchestrator` | active (running) |
| 2 | Network module loaded | Check logs for "NetworkModule" | Present |
| 3 | Graph query works | Call getNetwork via MCP tool | Returns graph JSON |

### Smoke Test

If entity_links has data (from MTO-35):
- Call `get_feature_network` with a known issue key
- Verify JSON response has `nodes`, `edges`, `metadata` fields
- Verify nodes contain the queried issue key

---

## 5. Rollback Plan

### 5.1 Decision Criteria

| Condition | Action |
|-----------|--------|
| Application fails to start | Rollback JAR |
| Network queries cause errors | Disable in config |
| Performance degradation | Disable, investigate |

### 5.2 Rollback Steps

```bash
systemctl stop mcp-orchestrator
cp mcp-orchestrator-all.jar.bak /opt/mcp/mcp-orchestrator-all.jar
systemctl start mcp-orchestrator
```

Or quick disable:
```yaml
orchestrator:
  network:
    enabled: false
```

---

## 6. Monitoring

| Metric | Target | Alert |
|--------|--------|-------|
| getNetwork latency | < 200ms | > 500ms |
| getFullNetwork latency | < 500ms | > 1000ms |
| Error rate | 0% | > 5% |
| Memory usage during graph build | Stable | Spike > 200MB |

---

## 7. Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `orchestrator.network.enabled` | true | Master toggle |
| `orchestrator.network.max-hops` | 5 | Maximum BFS depth |
| `orchestrator.network.max-nodes` | 1000 | Max nodes in response |
| `orchestrator.network.truncation-threshold` | 500 | Warn/truncate threshold for getNetwork |
