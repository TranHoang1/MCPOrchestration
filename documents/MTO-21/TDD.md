# Technical Design Document (TDD)

## MCPOrchestration — MTO-21: Web Dashboard – Sync Status & Monitoring

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-21 |
| Title | Web Dashboard – Sync Status & Monitoring |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-21.docx |
| Related FSD | FSD-v1-MTO-21.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | SA Agent | Initial TDD |

---

## 1. Introduction

### 1.1 Purpose

Technical design for the Web Dashboard — REST API endpoints, WebSocket handler, and static HTML dashboard for monitoring Jira sync operations.

### 1.2 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Server | Ktor (Netty) | 3.4.0 |
| WebSocket | Ktor WebSocket plugin | 3.4.0 |
| Serialization | kotlinx.serialization-json | 1.8.1 |
| Frontend | Vanilla JS + CSS | ES2020 |
| Static Serving | Ktor Static Resources | 3.4.0 |

### 1.3 Design Principles

- **Ktor-native** — use Ktor routing, WebSocket, and static resource plugins
- **Event-driven** — WebSocket broadcasts via SharedFlow
- **No frontend framework** — vanilla JS for simplicity and zero build step
- **Responsive-first** — CSS Grid/Flexbox for layout

---

## 2. System Architecture

### 2.1 Component Diagram

| Component | Responsibility | Technology |
|-----------|---------------|------------|
| SyncRoutes | REST API route handlers | Ktor Routing |
| WebSocketHandler | Manage WS connections, broadcast events | Ktor WebSocket + SharedFlow |
| SyncDashboardService | Business logic, aggregate status data | Kotlin |
| SyncEventBus | Publish/subscribe for sync events | MutableSharedFlow |
| sync-dashboard.html | Frontend UI | HTML + CSS + JS |

### 2.2 Communication Patterns

| From | To | Protocol | Pattern |
|------|----|----------|---------|
| Browser | SyncRoutes | HTTP REST | Request/Response |
| Browser | WebSocketHandler | WebSocket | Bidirectional stream |
| ProjectScanner | SyncEventBus | In-process | Emit events |
| SyncEventBus | WebSocketHandler | In-process | SharedFlow collect |

---

## 3. API Design

### 3.1 Route Registration

```kotlin
fun Application.configureDashboardRoutes() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(30)
        timeout = Duration.ofSeconds(15)
    }
    
    routing {
        route("/sync") {
            get("/status") { /* UC-01 */ }
            get("/status/{projectKey}") { /* UC-02 */ }
            post("/start") { /* UC-03 */ }
            post("/stop") { /* UC-04 */ }
            webSocket("/live") { /* UC-05 */ }
            static("/") {
                resources("static")
                defaultResource("static/sync-dashboard.html")
            }
        }
    }
}
```

### 3.2 WebSocket Implementation

```kotlin
class WebSocketHandler(private val eventBus: SyncEventBus) {
    private val connections = ConcurrentHashMap<String, WebSocketSession>()
    
    suspend fun handleConnection(session: WebSocketSession) {
        val id = UUID.randomUUID().toString()
        if (connections.size >= MAX_CONNECTIONS) {
            session.close(CloseReason(4000, "Max connections reached"))
            return
        }
        connections[id] = session
        try {
            eventBus.events.collect { event ->
                session.sendSerialized(event)
            }
        } finally {
            connections.remove(id)
        }
    }
    
    companion object {
        const val MAX_CONNECTIONS = 50
    }
}
```

### 3.3 Event Bus

```kotlin
class SyncEventBus {
    private val _events = MutableSharedFlow<SyncEvent>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()
    
    suspend fun emit(event: SyncEvent) {
        _events.emit(event)
    }
}
```

---

## 4. Database Design

No new tables. Uses existing `jira_sync_state` (MTO-15) for status queries.

**Query Patterns:**

| Operation | Query | Performance |
|-----------|-------|-------------|
| All statuses | `SELECT * FROM jira_sync_state` | < 10ms |
| Single status | `SELECT * FROM jira_sync_state WHERE project_key = ?` | < 1ms |
| Recent errors | `SELECT * FROM jira_sync_errors ORDER BY created_at DESC LIMIT 50` | < 10ms |

---

## 5. Class / Module Design

### 5.1 Package Structure

```
com.orchestrator.mcp/
└── dashboard/
    ├── SyncRoutes.kt                  # Ktor route definitions
    ├── WebSocketHandler.kt            # WebSocket connection management
    ├── SyncDashboardService.kt        # Business logic
    ├── SyncEventBus.kt                # Event pub/sub
    ├── model/
    │   ├── SyncStatusResponse.kt      # REST response DTOs
    │   ├── SyncEvent.kt               # WebSocket event sealed class
    │   └── SyncRequest.kt             # REST request DTOs
    └── di/
        └── DashboardModule.kt         # Koin DI
```

### 5.2 Frontend Structure

```
src/main/resources/static/
└── sync-dashboard.html    # Single HTML file with embedded CSS + JS
```

---

## 6. Frontend Design

### 6.1 HTML Structure

```html
<div class="dashboard">
  <header class="header">
    <select id="project-selector"></select>
    <button id="btn-start">Start Sync</button>
    <button id="btn-stop">Stop Sync</button>
  </header>
  <main class="content">
    <section class="progress-section">
      <div class="progress-bar"><div class="progress-fill"></div></div>
      <span class="progress-text">0/0 issues</span>
    </section>
    <section class="status-cards">
      <div class="card" id="card-scan">Scan Phase</div>
      <div class="card" id="card-crawl">Crawl Phase</div>
      <div class="card" id="card-attachments">Attachments</div>
    </section>
    <section class="error-log">
      <h3>Recent Errors</h3>
      <ul id="error-list"></ul>
    </section>
    <section class="queue-status">
      <span class="badge pending">Pending: 0</span>
      <span class="badge processing">Processing: 0</span>
      <span class="badge completed">Completed: 0</span>
      <span class="badge failed">Failed: 0</span>
    </section>
  </main>
</div>
```

### 6.2 WebSocket Client

```javascript
class SyncWebSocket {
    constructor() {
        this.connect();
    }
    connect() {
        this.ws = new WebSocket(`ws://${location.host}/sync/live`);
        this.ws.onmessage = (e) => this.handleEvent(JSON.parse(e.data));
        this.ws.onclose = () => setTimeout(() => this.connect(), 3000);
    }
    handleEvent(event) {
        switch(event.type) {
            case 'progress': updateProgress(event); break;
            case 'error': addError(event); break;
            case 'completed': showCompleted(event); break;
            case 'attachment_processed': updateQueue(event); break;
        }
    }
}
```

---

## 7. Security Design

| Aspect | Implementation |
|--------|---------------|
| Authentication | None (internal tool, future enhancement) |
| CORS | Same-origin (no CORS needed) |
| WebSocket | No auth token required |
| Input validation | projectKey regex validation |

---

## 8. Performance & Scalability

| Metric | Target | Implementation |
|--------|--------|----------------|
| API response | < 200ms | Direct DB query, no joins |
| WebSocket broadcast | < 100ms | SharedFlow, non-blocking |
| Max connections | 50 | Connection counter + reject |
| Event throttle | 1 per 5s | Debounce in SyncEventBus |
| Dashboard load | < 2s | Single HTML file, no external deps |

---

## 9. Deployment Considerations

### 9.1 Configuration

```yaml
dashboard:
  enabled: true
  basePath: /sync
  maxWebSocketConnections: 50
  heartbeatInterval: 30s
  eventThrottleMs: 5000
```

### 9.2 Ktor Plugin Installation

```kotlin
// In Application.kt or module configuration
install(WebSockets) { ... }
install(ContentNegotiation) { json() }  // already installed
configureDashboardRoutes()
```

---

## 10. Implementation Checklist

| # | Task | File | Priority |
|---|------|------|----------|
| 1 | SyncEventBus | dashboard/SyncEventBus.kt | High |
| 2 | SyncDashboardService | dashboard/SyncDashboardService.kt | High |
| 3 | SyncRoutes (REST) | dashboard/SyncRoutes.kt | High |
| 4 | WebSocketHandler | dashboard/WebSocketHandler.kt | High |
| 5 | DTOs | dashboard/model/*.kt | High |
| 6 | DashboardModule (Koin) | dashboard/di/DashboardModule.kt | High |
| 7 | sync-dashboard.html | resources/static/sync-dashboard.html | High |
| 8 | Unit tests | test/.../dashboard/*.kt | High |
| 9 | Integration test (Ktor TestHost) | test/.../dashboard/it/*.kt | Medium |

---

## 11. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Architecture | [architecture.png](diagrams/architecture.png) | [architecture.drawio](diagrams/architecture.drawio) |
| 2 | Component | [component.png](diagrams/component.png) | [component.drawio](diagrams/component.drawio) |
| 3 | Sequence - WebSocket | [api-sequence-websocket.png](diagrams/api-sequence-websocket.png) | [api-sequence-websocket.drawio](diagrams/api-sequence-websocket.drawio) |
| 4 | Class Diagram | [class-diagram.png](diagrams/class-diagram.png) | [class-diagram.drawio](diagrams/class-diagram.drawio) |
