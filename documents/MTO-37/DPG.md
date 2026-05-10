# Deployment Guide (DPG)

## MCPOrchestration — MTO-37: KB Refinery — Feedback & Correction UI

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-37 |
| Title | KB Refinery — Feedback & Correction UI |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related TDD | TDD-v1-MTO-37.docx |

---

## 1. Overview

### 1.1 Feature Summary

The Feedback & Correction feature adds API endpoints for users to submit feedback on KB entries and an approval workflow for administrators to review and apply corrections. All actions are audit-logged via MTO-34 AuditService.

### 1.2 Deployment Scope

| Item | Type | Description |
|------|------|-------------|
| MCP Orchestrator Server | Modified | New `feedback` package added to fat JAR |
| PostgreSQL (`jira_assistant` DB) | Migration | New `kb_feedback` table + 3 indexes |
| Application Configuration | Modified | New `feedback` section in `application.yml` |

---

## 2. Prerequisites

### 2.1 Infrastructure

| Requirement | Status | Notes |
|-------------|--------|-------|
| JVM host with JDK 21 | Ready | Existing infrastructure |
| PostgreSQL 16+ | Ready | Existing `jira_assistant` database |
| MTO-26 deployed (KB entries) | Required | Feedback references KB entries |
| MTO-34 deployed (Audit service) | Required | Audit logging dependency |

### 2.2 Pre-Deployment Checklist

| # | Check | Command | Expected |
|---|-------|---------|----------|
| 1 | Current version running | `curl localhost:8080/health` | 200 OK |
| 2 | PostgreSQL accessible | `psql -h localhost -d jira_assistant -c "SELECT 1"` | Returns 1 |
| 3 | MTO-34 AuditService available | Check logs for "AuditModule" | Present |
| 4 | Backup current JAR | `cp mcp-orchestrator-all.jar mcp-orchestrator-all.jar.bak` | Done |
| 5 | Backup database | `pg_dump jira_assistant > backup_pre_mto37.sql` | Done |

---

## 3. Deployment Steps

### Step 1: Database Migration

```sql
-- V4__create_kb_feedback.sql
CREATE TABLE IF NOT EXISTS kb_feedback (
    id BIGSERIAL PRIMARY KEY,
    issue_key VARCHAR(50) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    feedback_type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    suggested_correction TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_id VARCHAR(100),
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_feedback_issue ON kb_feedback (issue_key);
CREATE INDEX idx_feedback_status ON kb_feedback (status);
CREATE INDEX idx_feedback_user ON kb_feedback (user_id);
```

**Execution:**
```bash
psql -h localhost -d jira_assistant -f V4__create_kb_feedback.sql
```

**Verification:**
```bash
psql -h localhost -d jira_assistant -c "\dt kb_feedback"
psql -h localhost -d jira_assistant -c "\di idx_feedback_*"
```

### Step 2: Update Configuration

Add to `application.yml`:

```yaml
orchestrator:
  feedback:
    enabled: true
    max-content-length: 2000
    max-correction-length: 5000
    query-limit-default: 50
    query-limit-max: 100
```

### Step 3: Deploy New JAR

```bash
systemctl stop mcp-orchestrator
cp build/libs/mcp-orchestrator-all.jar /opt/mcp/mcp-orchestrator-all.jar
systemctl start mcp-orchestrator
```

### Step 4: Verify Startup

```bash
journalctl -u mcp-orchestrator --since "1 minute ago" | grep -i "feedback"
# Expected: "FeedbackModule registered"

curl localhost:8080/health
# Expected: 200 OK
```

---

## 4. Post-Deployment Verification

| # | Check | Command | Expected |
|---|-------|---------|----------|
| 1 | Service started | `systemctl status mcp-orchestrator` | active (running) |
| 2 | Feedback module loaded | Check logs for "FeedbackModule" | Present |
| 3 | DB table accessible | App can query kb_feedback | No errors |
| 4 | Submit test feedback | Call submit_kb_feedback tool | Feedback created |
| 5 | Query feedback | Call getByStatus(PENDING) | Returns test feedback |

### Smoke Test

```bash
# Submit test feedback via MCP tool
# Then verify in DB:
psql -h localhost -d jira_assistant -c "SELECT id, issue_key, status FROM kb_feedback ORDER BY id DESC LIMIT 5"
```

---

## 5. Rollback Plan

### 5.1 Decision Criteria

| Condition | Action |
|-----------|--------|
| Application fails to start | Rollback JAR |
| Feedback operations fail | Disable in config |
| Audit logging broken | Investigate MTO-34, disable feedback if critical |
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
  feedback:
    enabled: false
```

### 5.3 Database Rollback (if needed)

```sql
DROP TABLE IF EXISTS kb_feedback;
```

---

## 6. Monitoring

| Metric | Target | Alert |
|--------|--------|-------|
| submit() latency | < 100ms | > 300ms |
| approve/reject latency | < 200ms | > 500ms |
| getStats() latency | < 300ms | > 1000ms |
| Error rate | 0% | > 5% |
| Pending feedback count | Monitored | > 100 unresolved |

---

## 7. Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `orchestrator.feedback.enabled` | true | Master toggle |
| `orchestrator.feedback.max-content-length` | 2000 | Max feedback content chars |
| `orchestrator.feedback.max-correction-length` | 5000 | Max correction text chars |
| `orchestrator.feedback.query-limit-default` | 50 | Default query limit |
| `orchestrator.feedback.query-limit-max` | 100 | Maximum query limit |
