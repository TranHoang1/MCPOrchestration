# Release Notes (RLN)

## MCPOrchestration — MTO-20: MCP Tool Integration – Sync & Graph Tools

---

## Release Information

| Field | Value |
|-------|-------|
| Release Version | 1.5.0 |
| Release Date | 2025-07-18 |
| Jira Ticket | MTO-20 |
| Environment | DEV / SIT / UAT / PROD |
| Author | DevOps Agent |
| Status | Final |
| Related Epic | MTO-14 — Jira Project Sync Service |

---

## 1. Release Summary

This release exposes the Jira sync infrastructure (MTO-15 through MTO-18) to AI agents via three new MCP protocol tools. Agents can now trigger project syncs, check sync progress, and query the ticket relationship graph — all through the standard MCP `tools/call` interface.

**Release Type:** Feature addition (new MCP tools)
**Risk Level:** Low (thin handler layer over existing services)
**Backward Compatible:** Yes — no existing tools modified

---

## 2. What's New

### 2.1 New MCP Tools

| # | Tool Name | Description | Auto-Approve |
|---|-----------|-------------|--------------|
| 1 | `jira_project_sync` | Trigger background Jira project sync (full or incremental) | ❌ Requires approval |
| 2 | `jira_sync_status` | Check sync progress (status, percentage, counts) | ✅ Auto-approved |
| 3 | `jira_ticket_graph` | Query ticket relationship graph (BFS traversal or full) | ✅ Auto-approved |

### 2.2 Tool Schemas

**jira_project_sync:**
```json
{
  "projectKey": "string (required)",
  "fullSync": "boolean (default: false)"
}
```

**jira_sync_status:**
```json
{
  "projectKey": "string (required)"
}
```

**jira_ticket_graph:**
```json
{
  "projectKey": "string (required)",
  "issueKey": "string (optional — center node for subgraph)",
  "depth": "integer (1-5, default: 2)",
  "relationshipTypes": "array (optional filter)"
}
```

### 2.3 Technical Highlights

- **Async Sync Trigger** — `jira_project_sync` launches scan in background, returns immediately
- **BFS Graph Traversal** — `jira_ticket_graph` supports depth-limited subgraph queries
- **1000-Node Cap** — Graph responses capped at 1000 nodes for performance
- **Consistent Error Handling** — Uses existing McpOrchestratorException hierarchy

---

## 3. New Components

### 3.1 Package: `com.orchestrator.mcp.synctools`

| Class | Type | Responsibility |
|-------|------|----------------|
| `SyncToolRegistrar` | Class | Register 3 tools with MCP server |
| `SyncToolHandler` | Class | Handle jira_project_sync invocations |
| `StatusToolHandler` | Class | Handle jira_sync_status invocations |
| `GraphToolHandler` | Class | Handle jira_ticket_graph invocations |
| `SyncToolsModule` | Koin module | DI bindings |

---

## 4. Configuration Changes

```yaml
orchestrator:
  tools:
    autoApprove:
      - jira_sync_status
      - jira_ticket_graph
```

---

## 5. Dependencies

### 5.1 No New External Dependencies

All existing libraries. Tools are thin handlers delegating to MTO-15/17/18 services.

### 5.2 Internal Dependencies

| Module | Required | Used By |
|--------|----------|---------|
| MTO-15 (SyncStateManager) | ✅ | StatusToolHandler |
| MTO-17 (ProjectScanner) | ✅ | SyncToolHandler |
| MTO-18 (GraphDataRepository) | ✅ | GraphToolHandler |

---

## 6. Testing Summary

| Level | Tests | Passed | Failed | Coverage |
|-------|-------|--------|--------|----------|
| Unit Tests | 12 | 12 | 0 | All 3 handlers + registrar |
| Integration Tests | 5 | 5 | 0 | MCP protocol end-to-end |
| **Total** | **17** | **17** | **0** | — |

---

## 7. Known Issues & Limitations

| # | Issue | Severity | Workaround |
|---|-------|----------|------------|
| 1 | Graph capped at 1000 nodes | Low | Use issueKey + depth for focused queries |
| 2 | No pagination for large graphs | Low | Filter by relationshipTypes |
| 3 | jira_project_sync is fire-and-forget | Low | Use jira_sync_status to poll progress |

---

## 8. Breaking Changes

No breaking changes. Existing MCP tools unaffected.

---

## 9. Rollback Instructions

1. Stop application
2. Restore previous JAR
3. Restart application
4. 3 tools will no longer appear in `tools/list`

**Data loss risk:** None — tools are stateless handlers.

---

## 10. Future Roadmap

| Story | Status | Dependency on MTO-20 |
|-------|--------|---------------------|
| MTO-15–19 | ✅ Deployed | Prerequisites |
| **MTO-20: MCP Tool Integration** | ✅ **This release** | — |
| MTO-21: Web Dashboard | Next | Uses same services, different interface |
| MTO-22: 3D Graph Visualization | Planned | Uses GraphDataRepository (same as MTO-20) |
