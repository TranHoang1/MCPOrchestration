# Functional Specification Document (FSD)

## MCPOrchestration — MTO-21: Web Dashboard – Sync Status & Monitoring

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-21 |
| Title | Web Dashboard – Sync Status & Monitoring |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-21.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | BA Agent | Initial FSD |
| 1.0 | 2026-05-08 | TA Agent | Technical enrichment — API contracts, WebSocket specs |

---

## 1. Introduction

### 1.1 Purpose

This FSD specifies the functional behavior of the Web Dashboard for the Sync Service — REST API endpoints, WebSocket real-time updates, and HTML UI for monitoring sync operations.

### 1.2 Scope

- REST API: GET /sync/status, GET /sync/status/{projectKey}, POST /sync/start, POST /sync/stop
- WebSocket: WS /sync/live for real-time event streaming
- HTML Dashboard: Single-page static HTML with vanilla JS/CSS
- Responsive design (desktop + tablet + mobile)

---

## 2. System Overview

### 2.1 Architecture

```
┌─────────────────────────────────────────────────────┐
│                 MCPOrchestration App                  │
│                                                      │
│  ┌───────────────────────────────────────────────┐  │
│  │            dashboard/ package (NEW)            │  │
│  │                                                │  │
│  │  ┌────────────┐  ┌──────────────┐            │  │
│  │  │ SyncRoutes │  │ WebSocket    │            │  │
│  │  │ (REST API) │  │ Handler      │            │  │
│  │  └────────────┘  └──────────────┘            │  │
│  │        ↕                ↕                     │  │
│  │  ┌──────────────────────────────────────┐    │  │
│  │  │       SyncDashboardService           │    │  │
│  │  └──────────────────────────────────────┘    │  │
│  └───────────────────────────────────────────────┘  │
│        ↕                    ↕                        │
│  ┌──────────────┐   ┌──────────────────┐           │
│  │SyncStateManager│   │ ProjectScanner   │           │
│  │  (MTO-15)    │   │   (MTO-17)       │           │
│  └──────────────┘   └──────────────────┘           │
└─────────────────────────────────────────────────────┘
```

---

## 3. Functional Requirements

### 3.1 Feature: GET /sync/status — All Projects Status

**Use Case ID:** UC-01

**API Contract:**

| Attribute | Value |
|-----------|-------|
| Method | GET |
| Path | /sync/status |
| Auth | None (internal tool) |
| Response | 200 OK — JSON array |

**Response Schema:**

```json
[
  {
    "projectKey": "MTO",
    "status": "syncing",
    "progress": 67.5,
    "syncedIssues": 45,
    "totalIssues": 67,
    "lastSyncTime": "2026-05-07T10:00:00Z",
    "errors": 3
  }
]
```

**Business Rules:**

| Rule ID | Rule |
|---------|------|
| BR-01 | Return empty array if no projects configured |
| BR-02 | Progress = syncedIssues / totalIssues * 100 |
| BR-03 | Status enum: idle, syncing, completed, error, stopped |

---

### 3.2 Feature: GET /sync/status/{projectKey} — Detailed Status

**Use Case ID:** UC-02

**API Contract:**

| Attribute | Value |
|-----------|-------|
| Method | GET |
| Path | /sync/status/{projectKey} |
| Auth | None |
| Response | 200 OK or 404 Not Found |

**Response Schema (200):**

```json
{
  "projectKey": "MTO",
  "status": "syncing",
  "progress": 67.5,
  "phases": {
    "scan": { "status": "completed", "progress": 100, "itemsScanned": 67, "totalItems": 67 },
    "crawl": { "status": "syncing", "progress": 45.0, "issuesCrawled": 30, "totalIssues": 67 },
    "attachments": { "status": "idle", "progress": 0, "processed": 0, "total": 15, "failed": 0 }
  },
  "recentErrors": [
    { "message": "Failed to fetch MTO-99", "timestamp": "2026-05-07T10:15:30Z", "issueKey": "MTO-99" }
  ]
}
```

**Error Response (404):**

```json
{ "error": "Project not found: XYZ" }
```

---

### 3.3 Feature: POST /sync/start — Start Sync Job

**Use Case ID:** UC-03

**Request:**

```json
{ "projectKey": "MTO", "fullSync": false }
```

**Responses:**

| Status | Body | Condition |
|--------|------|-----------|
| 200 | `{ "status": "started", "message": "Sync started for MTO" }` | Success |
| 400 | `{ "error": "projectKey is required" }` | Missing field |
| 404 | `{ "error": "Project not found: XYZ" }` | Unknown project |
| 409 | `{ "error": "Sync already running for MTO" }` | Already running |

---

### 3.4 Feature: POST /sync/stop — Stop Sync Job

**Use Case ID:** UC-04

**Request:**

```json
{ "projectKey": "MTO" }
```

**Responses:**

| Status | Body | Condition |
|--------|------|-----------|
| 200 | `{ "status": "stopped" }` | Success |
| 400 | `{ "error": "projectKey is required" }` | Missing field |
| 409 | `{ "error": "No sync running for MTO" }` | Not running |

---

### 3.5 Feature: WS /sync/live — Real-time WebSocket

**Use Case ID:** UC-05

**Connection:** `ws://{host}:{port}/sync/live`

**Event Types:**

| Type | Fields | Frequency |
|------|--------|-----------|
| progress | projectKey, synced, total, percentage | Every 5s during sync |
| error | projectKey, message, timestamp | Immediately on error |
| completed | projectKey, duration, totalSynced | On sync completion |
| attachment_processed | issueKey, filename | Per attachment |
| heartbeat | timestamp | Every 30s (keep-alive) |

**Business Rules:**

| Rule ID | Rule |
|---------|------|
| BR-04 | Max 50 concurrent WebSocket connections |
| BR-05 | Heartbeat every 30s to keep connection alive |
| BR-06 | Progress events throttled to max 1 per 5 seconds |
| BR-07 | Graceful close on server shutdown |

---

### 3.6 Feature: HTML Dashboard UI

**Use Case ID:** UC-06

**Served at:** GET /sync (static HTML from resources/static/sync-dashboard.html)

**UI Sections:**

| Section | Content | Update Method |
|---------|---------|---------------|
| Header | Project selector dropdown, Start/Stop buttons | User interaction |
| Progress | Animated progress bar, synced/total count | WebSocket events |
| Status Cards | Scan/Crawl/Attachment phase status | WebSocket events |
| Error Log | Scrollable list, max 100 entries | WebSocket error events |
| Queue Status | Pending/Processing/Completed/Failed counts | WebSocket events |

**Responsive Breakpoints:**

| Viewport | Layout |
|----------|--------|
| > 1024px | Full layout (2 columns) |
| 768-1024px | Two columns, compact |
| < 768px | Single column, stacked |

---

## 4. Data Model

### 4.1 DTOs

```kotlin
@Serializable
data class ProjectSyncStatus(
    val projectKey: String,
    val status: SyncStatusEnum,
    val progress: Float,
    val syncedIssues: Int,
    val totalIssues: Int,
    val lastSyncTime: Instant?,
    val errors: Int
)

@Serializable
enum class SyncStatusEnum { idle, syncing, completed, error, stopped }

@Serializable
sealed class SyncEvent {
    abstract val type: String
}

@Serializable
data class ProgressEvent(val projectKey: String, val synced: Int, val total: Int, val percentage: Float) : SyncEvent()

@Serializable
data class ErrorEvent(val projectKey: String, val message: String, val timestamp: Instant) : SyncEvent()

@Serializable
data class CompletedEvent(val projectKey: String, val duration: Long, val totalSynced: Int) : SyncEvent()
```

---

## 5. Non-Functional Requirements

| Category | Target |
|----------|--------|
| API response time | GET < 200ms, POST < 500ms |
| WebSocket latency | Events within 1s of occurrence |
| Dashboard load time | < 2 seconds |
| Concurrent WebSocket | Up to 50 connections |
| Browser support | Chrome, Firefox, Safari, Edge (latest 2) |

---

## 6. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | System Context | [system-context.png](diagrams/system-context.png) | [system-context.drawio](diagrams/system-context.drawio) |
| 2 | Sequence - Start Sync | [sequence-start-sync.png](diagrams/sequence-start-sync.png) | [sequence-start-sync.drawio](diagrams/sequence-start-sync.drawio) |
| 3 | State - Sync Status | [state-sync.png](diagrams/state-sync.png) | [state-sync.drawio](diagrams/state-sync.drawio) |
