# Deployment Guide (DPG)

## MCPOrchestration — MTO-20: MCP Tool Integration – Sync & Graph Tools

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-20 |
| Title | MCP Tool Integration – Sync & Graph Tools |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2025-07-18 |
| Status | Final |
| Related TDD | TDD-v1-MTO-20.docx |
| Related FSD | FSD-v1-MTO-20.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-18 | DevOps Agent | Initial deployment guide for MCP Sync & Graph Tools |

---

## 1. Deployment Overview

### 1.1 Summary

This deployment registers three new MCP tools into the existing MCP Orchestrator tool registry:

- `jira_project_sync` — Trigger background Jira project sync
- `jira_sync_status` — Check sync progress for a project
- `jira_ticket_graph` — Query ticket relationship graph (nodes + edges)

These tools provide the MCP protocol interface for AI agents to interact with the Jira sync infrastructure built in MTO-15 through MTO-18.

### 1.2 Deployment Type

| Aspect | Value |
|--------|-------|
| Type | Rolling update (zero-downtime) |
| Risk Level | Low — thin handler layer, delegates to existing services |
| Rollback Complexity | Low — remove JAR, tools disappear from registry |
| Downtime Required | None |
| Data Migration | None |

### 1.3 Affected Components

| Component | Change Type | Impact |
|-----------|-------------|--------|
| `orchestrator-server` module | New classes added | New package `com.orchestrator.mcp.synctools` |
| `McpServerFactory` | Extended | SyncToolRegistrar called during server setup |
| `AppModule.kt` (Koin DI) | Extended | SyncToolsModule registered |
| `application.yml` | Extended | autoApprove list updated |
| Fat JAR | Rebuilt | Includes synctools classes |
| MCP tool registry | 3 new tools | Visible via `tools/list` |

---

## 2. Prerequisites

### 2.1 Pre-requisite Deployments

| Ticket | Component | Required | Status |
|--------|-----------|----------|--------|
| MTO-15 | Database Schema | ✅ | SyncStateManager, tables |
| MTO-16 | Jira REST Client | ✅ | JiraRestClient |
| MTO-17 | Project Scanner | ✅ | ProjectScanner service |
| MTO-18 | Ticket Crawler | ✅ | GraphDataRepository |

### 2.2 Software Dependencies

**No new external dependencies.** Uses existing MCP SDK and kotlinx.serialization.

### 2.3 Access Requirements

| Access | Purpose | Who |
|--------|---------|-----|
| MCP protocol | Tool invocation | AI agents (Claude, etc.) |
| PostgreSQL | Query sync state + graph | Application |

---

## 3. Pre-Deployment Checklist

| # | Check | Command / Action | Expected Result |
|---|-------|-----------------|-----------------|
| 1 | MTO-17 scanner deployed | Check ProjectScanner in DI | Available |
| 2 | MTO-18 graph data exists | `SELECT COUNT(*) FROM jira_ticket_graph;` | ≥ 0 |
| 3 | Fat JAR built | `./gradlew buildFatJar` | `BUILD SUCCESSFUL` |
| 4 | All tests pass | `./gradlew test` | All pass |
| 5 | Backup current JAR | Backup | Done |

---

## 4. Configuration Changes

### 4.1 Auto-Approve Configuration

```yaml
orchestrator:
  tools:
    autoApprove:
      - jira_sync_status      # Read-only, safe
      - jira_ticket_graph     # Read-only, safe
      # jira_project_sync NOT auto-approved — triggers background job
```

---

## 5. Deployment Steps

### 5.1 Build Phase

```bash
git checkout MTO-20
git pull origin MTO-20
./gradlew clean test
./gradlew buildFatJar

# Verify synctools classes
jar tf build/libs/mcp-orchestrator-all.jar | grep "synctools/"
```

### 5.2 Deploy

```bash
# Stop application
sudo systemctl stop mcp-orchestrator

# Deploy JAR
cp build/libs/mcp-orchestrator-all.jar /opt/mcp-orchestrator/mcp-orchestrator-all.jar

# Update application.yml (add autoApprove entries)

# Start application
sudo systemctl start mcp-orchestrator
sleep 10
```

---

## 6. Post-Deployment Verification

| # | Check | Method | Expected |
|---|-------|--------|----------|
| 1 | Application started | Check logs | `Application started` |
| 2 | Tools registered | MCP `tools/list` | 3 new tools visible |
| 3 | jira_sync_status works | Invoke tool | Returns status JSON |
| 4 | jira_ticket_graph works | Invoke tool | Returns nodes + edges |
| 5 | Existing tools work | Invoke existing tool | Normal response |

### 6.1 Smoke Test

```json
// Test jira_sync_status
{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"jira_sync_status","arguments":{"projectKey":"MTO"}}}
// Expected: {"status":"never_synced"} or actual status

// Test jira_ticket_graph
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"jira_ticket_graph","arguments":{"projectKey":"MTO"}}}
// Expected: {"nodes":[],"edges":[],"metadata":{...}} or actual graph data
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

# Remove autoApprove entries from application.yml (optional)

# Restart
sudo systemctl start mcp-orchestrator
```

### 7.2 Impact of Rollback

- 3 MCP tools disappear from `tools/list`
- AI agents calling these tools will get "tool not found" errors
- No data loss — tools are read-only (except jira_project_sync which triggers existing scanner)

---

## 8. Monitoring

| Log Pattern | Meaning | Action |
|-------------|---------|--------|
| `SyncToolRegistrar: 3 tools registered` | Tools available | None |
| `jira_project_sync invoked for {project}` | Sync triggered | None |
| `jira_ticket_graph: {N} nodes returned` | Graph queried | None |
| `Tool invocation error: {tool}` | Handler error | Check logs |

---

## 9. Security Considerations

| Tool | Approval Required | Rationale |
|------|-------------------|-----------|
| `jira_project_sync` | ✅ Yes | Write operation — triggers background job |
| `jira_sync_status` | ❌ Auto-approve | Read-only, no side effects |
| `jira_ticket_graph` | ❌ Auto-approve | Read-only, no side effects |

---

## Appendix: Related Documents

| Document | Reference |
|----------|-----------|
| BRD | BRD-v1-MTO-20.docx |
| FSD | FSD-v1-MTO-20.docx |
| TDD | TDD-v1-MTO-20.docx |
| STP | STP-v1-MTO-20.docx |
| Test Report | STR-v1-MTO-20.docx |
