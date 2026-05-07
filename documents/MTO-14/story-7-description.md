## Mô tả

Implement Web Dashboard cho Sync Service: REST API endpoints, WebSocket live updates, và HTML UI hiển thị sync status.

## Scope

### Ktor Routes (REST API)

1. **GET /sync/status** — Trả về status của tất cả projects đang sync
   - Response: [{ projectKey, status, progress, syncedIssues, totalIssues, lastSyncTime, errors }]

2. **GET /sync/status/{projectKey}** — Status chi tiết cho 1 project
   - Response: { projectKey, status, progress, phases: { scan: {...}, crawl: {...}, attachments: {...} }, recentErrors }

3. **POST /sync/start** — Start sync job
   - Body: { projectKey, fullSync: boolean }
   - Response: { status: "started" } hoặc { error: "already running" }

4. **POST /sync/stop** — Stop sync job
   - Body: { projectKey }
   - Response: { status: "stopped" }

### WebSocket (Real-time Updates)

- **WS /sync/live** — Stream real-time sync events
  - Events: { type: "progress", projectKey, synced, total, percentage }
  - Events: { type: "error", projectKey, message, timestamp }
  - Events: { type: "completed", projectKey, duration, totalSynced }
  - Events: { type: "attachment_processed", issueKey, filename }

### HTML Dashboard (Static)

- Single-page HTML served từ Ktor (resources/static/sync-dashboard.html)
- Sections:
  - **Header**: Project selector dropdown
  - **Progress**: Progress bar (animated), synced/total count
  - **Status Cards**: Scan phase, Crawl phase, Attachment phase
  - **Error Log**: Scrollable list of recent errors
  - **Queue Status**: Attachment queue (pending/processing/completed/failed counts)
- Tech: Vanilla JS + CSS (no framework), WebSocket connection for live updates
- Responsive design (mobile-friendly)

### Configuration

```yaml
dashboard:
  enabled: true
  port: 8081  # hoặc cùng port với MCP server
  basePath: /sync
```

## Acceptance Criteria

- [ ] GET /sync/status trả về đúng data
- [ ] POST /sync/start triggers sync
- [ ] POST /sync/stop stops sync gracefully
- [ ] WebSocket streams real-time updates
- [ ] HTML dashboard hiển thị progress bar
- [ ] Error log hiển thị recent errors
- [ ] Queue status hiển thị attachment counts
- [ ] Responsive trên mobile
- [ ] Unit tests cho route handlers
- [ ] Manual test: start sync → observe live updates

## Story Points: 5

## Dependencies

- **Blocked by:** Story 1 (Database Schema) — cần sync state data
- **Blocked by:** Story 3 (Project Scanner) — cần sync logic để trigger
