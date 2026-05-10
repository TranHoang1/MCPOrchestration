# Deployment Guide (DPG)

## MCPOrchestration — MTO-35: KB Refinery — Semantic Entity Linking

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-35 |
| Title | KB Refinery — Semantic Entity Linking |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related TDD | TDD-v1-MTO-35.docx |

---

## 1. Overview

### 1.1 Feature Summary

The Semantic Entity Linking feature adds automatic detection and creation of semantic links between KB entries using embedding-based cosine similarity. It leverages the existing EmbeddingService and VectorDbClient (Qdrant) for vector operations and persists links in a new PostgreSQL table.

### 1.2 Deployment Scope

| Item | Type | Description |
|------|------|-------------|
| MCP Orchestrator Server | Modified | New `linking` package added to fat JAR |
| PostgreSQL (`jira_assistant` DB) | Migration | New `entity_links` table + 3 indexes |
| Qdrant Vector DB | Configuration | New collection `kb_entity_embeddings` |
| Application Configuration | Modified | New `linking` section in `application.yml` |

---

## 2. Prerequisites

### 2.1 Infrastructure

| Requirement | Status | Notes |
|-------------|--------|-------|
| JVM host with JDK 21 | Ready | Existing infrastructure |
| PostgreSQL 16+ | Ready | Existing `jira_assistant` database |
| Qdrant vector DB | Ready | Existing instance (used by tool discovery) |
| OpenAI API access | Ready | Existing EmbeddingService configuration |

### 2.2 Software Dependencies

| Dependency | Version | Status |
|-----------|---------|--------|
| JDK | 21 | Installed |
| PostgreSQL | 16+ | Available |
| Qdrant | 1.9+ | Available |
| Kotlin Runtime | 2.3.20 | Bundled in fat JAR |

### 2.3 Access Requirements

| Access | Type | Who |
|--------|------|-----|
| SSH to app server | Key-based | DevOps |
| PostgreSQL admin | Credentials | DBA / DevOps |
| Qdrant REST API | Network access | Application |

---

## 3. Pre-Deployment Checklist

| # | Check | Command | Expected |
|---|-------|---------|----------|
| 1 | Current version running | `curl localhost:8080/health` | 200 OK |
| 2 | PostgreSQL accessible | `psql -h localhost -d jira_assistant -c "SELECT 1"` | Returns 1 |
| 3 | Qdrant accessible | `curl http://localhost:6333/collections` | 200 OK |
| 4 | Backup current JAR | `cp mcp-orchestrator-all.jar mcp-orchestrator-all.jar.bak` | File copied |
| 5 | Backup database | `pg_dump jira_assistant > backup_pre_mto35.sql` | Dump created |
| 6 | Disk space available | `df -h /opt/mcp/` | > 500MB free |

---

## 4. Deployment Steps

### Step 1: Database Migration

```sql
-- V3__create_entity_links.sql
CREATE TABLE IF NOT EXISTS entity_links (
    id BIGSERIAL PRIMARY KEY,
    source_issue_key VARCHAR(50) NOT NULL,
    target_issue_key VARCHAR(50) NOT NULL,
    similarity_score DOUBLE PRECISION NOT NULL,
    link_type VARCHAR(20) NOT NULL DEFAULT 'SEMANTIC',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(source_issue_key, target_issue_key)
);

CREATE INDEX idx_entity_links_source ON entity_links (source_issue_key);
CREATE INDEX idx_entity_links_target ON entity_links (target_issue_key);
CREATE INDEX idx_entity_links_score ON entity_links (similarity_score DESC);
```

**Execution:**
```bash
psql -h localhost -d jira_assistant -f V3__create_entity_links.sql
```

**Verification:**
```bash
psql -h localhost -d jira_assistant -c "\dt entity_links"
psql -h localhost -d jira_assistant -c "\di idx_entity_links_*"
```

### Step 2: Create Qdrant Collection

```bash
curl -X PUT http://localhost:6333/collections/kb_entity_embeddings \
  -H "Content-Type: application/json" \
  -d '{
    "vectors": {
      "size": 768,
      "distance": "Cosine"
    },
    "hnsw_config": {
      "m": 16,
      "ef_construct": 100
    }
  }'
```

**Verification:**
```bash
curl http://localhost:6333/collections/kb_entity_embeddings | jq .result.status
# Expected: "green"
```

### Step 3: Update Configuration

Add to `application.yml`:

```yaml
orchestrator:
  linking:
    enabled: true
    similarity-threshold: 0.75
    top-k: 10
    batch-chunk-size: 50
    collection-name: kb_entity_embeddings
    auto-link-on-ingest: true
```

### Step 4: Deploy New JAR

```bash
# Stop current instance
systemctl stop mcp-orchestrator

# Deploy new JAR
cp build/libs/mcp-orchestrator-all.jar /opt/mcp/mcp-orchestrator-all.jar

# Start new instance
systemctl start mcp-orchestrator
```

### Step 5: Verify Startup

```bash
# Check logs for successful startup
journalctl -u mcp-orchestrator --since "1 minute ago" | grep -i "linking"
# Expected: "EntityLinkingService initialized"

# Health check
curl localhost:8080/health
# Expected: 200 OK
```

---

## 5. Post-Deployment Verification

| # | Check | Command | Expected |
|---|-------|---------|----------|
| 1 | Service started | `systemctl status mcp-orchestrator` | active (running) |
| 2 | Linking module loaded | Check logs for "LinkingModule registered" | Log entry present |
| 3 | Qdrant collection accessible | Application logs show no VectorDB errors | No errors |
| 4 | DB table accessible | Application can query entity_links | No connection errors |
| 5 | Test link creation | Trigger KB ingest for test entry | Link created in DB |

### Smoke Test

```bash
# Ingest a test KB entry (triggers auto-linking)
# Verify via DB query:
psql -h localhost -d jira_assistant -c "SELECT COUNT(*) FROM entity_links"
# Should show new links if other entries exist
```

---

## 6. Rollback Plan

### 6.1 Decision Criteria

| Condition | Action |
|-----------|--------|
| Application fails to start | Rollback immediately |
| entity_links queries fail | Check DB connectivity, rollback if persistent |
| Qdrant collection errors | Disable linking in config, restart |
| Performance degradation > 50% | Disable linking, investigate |

### 6.2 Rollback Steps

```bash
# Step 1: Stop application
systemctl stop mcp-orchestrator

# Step 2: Restore previous JAR
cp mcp-orchestrator-all.jar.bak /opt/mcp/mcp-orchestrator-all.jar

# Step 3: Remove config changes (or set enabled: false)
# Edit application.yml: orchestrator.linking.enabled: false

# Step 4: Start application
systemctl start mcp-orchestrator

# Step 5: Verify rollback
curl localhost:8080/health
```

### 6.3 Database Rollback (if needed)

```sql
DROP TABLE IF EXISTS entity_links;
```

### 6.4 Qdrant Rollback (if needed)

```bash
curl -X DELETE http://localhost:6333/collections/kb_entity_embeddings
```

---

## 7. Monitoring

### 7.1 Key Metrics

| Metric | Target | Alert Threshold |
|--------|--------|-----------------|
| linkEntry latency | < 500ms | > 1000ms |
| findSimilar latency | < 100ms | > 200ms |
| entity_links row count | Growing | Sudden drop |
| Qdrant collection size | Growing | Sudden drop |
| Error rate | 0% | > 5% |

### 7.2 Log Monitoring

```bash
# Watch for errors
journalctl -u mcp-orchestrator -f | grep -i "ERROR.*linking\|ERROR.*entity"

# Watch for performance issues
journalctl -u mcp-orchestrator -f | grep -i "WARN.*slow\|WARN.*timeout"
```

---

## 8. Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `orchestrator.linking.enabled` | true | Master toggle |
| `orchestrator.linking.similarity-threshold` | 0.75 | Min score for link creation |
| `orchestrator.linking.top-k` | 10 | Max similar entries to find |
| `orchestrator.linking.batch-chunk-size` | 50 | Entries per batch chunk |
| `orchestrator.linking.collection-name` | kb_entity_embeddings | Qdrant collection |
| `orchestrator.linking.auto-link-on-ingest` | true | Auto-link on KB ingest |
