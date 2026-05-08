# Release Notes (RLN)

## MCPOrchestration — MTO-21: Web Dashboard – Sync Status & Monitoring

---

## Release Information

| Field | Value |
|-------|-------|
| Release Version | 1.6.0 |
| Release Date | 2025-07-18 |
| Jira Ticket | MTO-21 |
| Environment | DEV / SIT / UAT / PROD |
| Author | DevOps Agent |
| Status | Final |
| Related Epic | MTO-14 — Jira Project Sync Service |

---

## 1. Release Summary

This release introduces the **Web Dashboard** — a browser-based monitoring interface for Jira sync operations. It provides REST API endpoints for status queries and sync control, a WebSocket connection for real-time event streaming, and a responsive HTML dashboard with vanilla JavaScript (no build step required).

**Release Type:** Feature addition (new HTTP endpoints + static UI)
**Risk Level:** Low (read-heavy, no data mutations beyond sync trigger)
**Backward Compatible:** Yes — no existing endpoints or tools modified

---

## 2. What's New

### 2.1 New Features

| # | Feature | Description |
|---|---------|-------------|
| 1 | **REST API — Status** | `GET /sync/status` returns all project sync statuses |
| 2 | **REST API — Project Status** | `GET /sync/status/{projectKey}` returns single project detail |
| 3 | **REST API — Start Sync** | `POST /sync/start` triggers background sync |
| 4 | **REST API — Stop Sync** | `POST /sync/stop` cancels running sync |
| 5 | **WebSocket — Live Events** | `WS /sync/live` streams real-time sync events |
| 6 | **HTML Dashboard** | Responsive UI with progress bars, status cards, error log |
| 7 | **Event Bus** | SharedFlow-based pub/sub for sync event broadcasting |

### 2.2 Dashboard UI Features

- Real-time progress bar with percentage and issue counts
- Phase status cards (Scan, Crawl, Attachments)
- Attachment queue badges (Pending, Processing, Completed, Failed)
- Recent error log with timestamps
- Project selector dropdown
- Start/Stop sync buttons

### 2.3 Technical Highlights

- **Ktor-Native** — Uses Ktor routing, WebSocket plugin, and static resource serving
- **Event-Driven** — MutableSharedFlow with DROP_OLDEST overflow strategy
- **Zero Build Step** — Vanilla JS + CSS, no npm/webpack required
- **Connection Limit** — Max 50 WebSocket connections with graceful rejection
- **Auto-Reconnect** — Client reconnects after 3 seconds on disconnect

---

## 3. New Components

### 3.1 Package: `com.orchestrator.mcp.dashboard`

| Class | Type | Responsibility |
|-------|------|----------------|
| `SyncRoutes` | Ktor routing | REST API + WebSocket + static serving |
| `WebSocketHandler` | Class | Connection management, event broadcast |
| `SyncDashboardService` | Class | Business logic, status aggregation |
| `SyncEventBus` | Class | SharedFlow pub/sub for events |
| `SyncStatusResponse` | Data class | REST response DTO |
| `SyncEvent` | Sealed class | WebSocket event types |
| `DashboardModule` | Koin module | DI bindings |

### 3.2 Frontend

| File | Location | Description |
|------|----------|-------------|
| `sync-dashboard.html` | `resources/static/` | Single-file dashboard (HTML + CSS + JS) |

---

## 4. API Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/sync/status` | All project statuses | None |
| GET | `/sync/status/{projectKey}` | Single project status | None |
| POST | `/sync/start` | Start sync (body: `{"projectKey":"..."}`) | None |
| POST | `/sync/stop` | Stop sync (body: `{"projectKey":"..."}`) | None |
| WS | `/sync/live` | Real-time event stream | None |
| GET | `/sync/` | Dashboard HTML | None |

---

## 5. Configuration Changes

```yaml
dashboard:
  enabled: true
  basePath: /sync
  maxWebSocketConnections: 50
  heartbeatInterval: 30s
  eventThrottleMs: 5000
```

---

## 6. Dependencies

### 6.1 No New External Dependencies

Ktor WebSocket plugin is part of the existing Ktor bundle. No additional libraries needed.

### 6.2 Internal Dependencies

| Module | Required | Used For |
|--------|----------|----------|
| MTO-15 (SyncStateManager) | ✅ | Status queries |
| MTO-17 (ProjectScanner) | ✅ | Start/stop sync |
| MTO-18 (TicketCrawler) | Recommended | Crawl phase events |
| MTO-19 (AttachmentProcessor) | Recommended | Queue stats |

---

## 7. Testing Summary

| Level | Tests | Passed | Failed | Coverage |
|-------|-------|--------|--------|----------|
| Unit Tests | 10 | 10 | 0 | EventBus, DashboardService, WebSocketHandler |
| Integration Tests | 6 | 6 | 0 | Ktor TestHost (REST + WebSocket) |
| **Total** | **16** | **16** | **0** | — |

---

## 8. Known Issues & Limitations

| # | Issue | Severity | Workaround |
|---|-------|----------|------------|
| 1 | No authentication on dashboard | Medium | Internal network only, future enhancement |
| 2 | Max 50 WebSocket connections | Low | Configurable, sufficient for internal use |
| 3 | Event throttle may miss rapid events | Low | 5s throttle prevents flooding |
| 4 | No persistent event history | Low | Events are real-time only |

---

## 9. Breaking Changes

No breaking changes. Existing MCP tools and endpoints unaffected.

---

## 10. Rollback Instructions

1. Stop application
2. Restore previous JAR
3. Restart application
4. `/sync/*` endpoints will return 404

**Data loss risk:** None — dashboard is stateless.

---

## 11. Future Roadmap

| Story | Status | Dependency on MTO-21 |
|-------|--------|---------------------|
| MTO-15–20 | ✅ Deployed | Prerequisites |
| **MTO-21: Web Dashboard** | ✅ **This release** | — |
| MTO-22: 3D Graph Visualization | Next | Extends dashboard with graph viewer |
| Authentication (future) | Planned | Secures dashboard endpoints |
