## Mô tả

Expose MCP tools cho Jira Project Sync Service: trigger sync, check status, query graph data.

## Scope

### MCP Tools

1. **jira_project_sync** — Trigger sync cho 1 project
   - Input: projectKey (required), fullSync (optional, default false)
   - fullSync = true: ignore last_sync_time, scan toàn bộ
   - fullSync = false: incremental (chỉ updated tickets)
   - Output: { status: "started", projectKey, estimatedIssues }
   - Async: return immediately, sync chạy background

2. **jira_sync_status** — Check sync progress
   - Input: projectKey (required)
   - Output: { status, progress (%), syncedIssues, totalIssues, lastSyncTime, errors[] }

3. **jira_ticket_graph** — Query graph data
   - Input: projectKey (required), issueKey (optional), depth (optional, default 2), relationshipTypes (optional)
   - Output: { nodes: [...], edges: [...] }
   - Nếu issueKey provided: trả về subgraph xung quanh issue đó
   - Nếu chỉ projectKey: trả về full project graph

### Tool Registration

- Register tools vào MCP Orchestrator tool registry
- Tools available cho tất cả agents (BA, SA, QA, DEV)
- Auto-approve: jira_sync_status, jira_ticket_graph (read-only)
- Require approval: jira_project_sync (write operation)

### Integration Points

- ProjectScanner (Story 3) — trigger scan
- TicketCrawler (Story 4) — trigger deep crawl
- SyncStateManager (Story 1) — read status
- jira_ticket_graph table — query graph

## Acceptance Criteria

- [ ] jira_project_sync triggers background sync
- [ ] jira_sync_status returns accurate progress
- [ ] jira_ticket_graph returns correct graph data
- [ ] Tools registered và discoverable qua MCP
- [ ] Error handling (project not found, sync already running)
- [ ] Unit tests cho tool handlers
- [ ] Integration test: trigger sync → check status → query graph

## Story Points: 5

## Dependencies

- **Blocked by:** Story 1 (Database Schema)
- **Blocked by:** Story 3 (Project Scanner)
- **Blocked by:** Story 4 (Ticket Crawler) — cho graph data
