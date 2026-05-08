# Business Requirements Document (BRD)

## MCPOrchestration — MTO-21: Web Dashboard – Sync Status & Monitoring

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-21 |
| Title | Web Dashboard – Sync Status & Monitoring |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-07 |
| Status | Draft |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | BA Agent – Business Analyst | Create document |
| Peer Reviewer | Duc Nguyen – Product Owner | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-07 | BA Agent | Initiate document — auto-generated from Jira ticket MTO-21 |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |

---

## 1. Introduction

### 1.1 Scope

Implement a Web Dashboard for the MCP Orchestration Sync Service that provides:
- **REST API endpoints** for querying sync status, starting/stopping sync jobs
- **WebSocket real-time updates** for live sync progress streaming
- **HTML Dashboard UI** for visual monitoring of sync operations

The dashboard enables operators and developers to monitor sync progress, view errors, manage sync jobs, and observe attachment processing in real-time.

### 1.2 Out of Scope

- Authentication/authorization for dashboard access (future enhancement)
- Multi-tenant dashboard (single instance only)
- Historical sync analytics/reporting
- Email/Slack notifications for sync events
- Custom dashboard themes or branding
- Database schema changes (handled by MTO Story 1)
- Sync logic implementation (handled by MTO Story 3)

### 1.3 Preliminary Requirement

| # | Prerequisite | Source | Status |
|---|-------------|--------|--------|
| 1 | Database Schema for sync state data | MTO Story 1 (Database Schema) | Blocked by |
| 2 | Project Scanner sync logic | MTO Story 3 (Project Scanner) | Blocked by |
| 3 | Ktor server framework already configured | Existing codebase | Available |
| 4 | kotlinx.serialization for JSON | Existing codebase | Available |

---

## 2. Business Requirements

### 2.1 High Level Process Map

The Web Dashboard provides a monitoring and control interface for the Jira-to-KB sync service. The high-level process:

1. **Operator** opens the HTML dashboard in a browser
2. **Dashboard** connects via WebSocket for real-time updates
3. **Operator** can view sync status for all projects or a specific project
4. **Operator** can start/stop sync jobs via the dashboard
5. **System** streams real-time progress, errors, and completion events
6. **Dashboard** displays progress bars, status cards, error logs, and queue status

![Business Flow](diagrams/business-flow.png)

### 2.2 List of User Stories / Use Cases

| # | Story / Use Case | Priority | Source Ticket |
|---|-----------------|----------|---------------|
| 1 | As an operator, I want to view sync status of all projects so that I can monitor overall sync health | MUST HAVE | MTO-21 |
| 2 | As an operator, I want to view detailed sync status for a specific project so that I can diagnose issues | MUST HAVE | MTO-21 |
| 3 | As an operator, I want to start a sync job so that I can trigger synchronization on demand | MUST HAVE | MTO-21 |
| 4 | As an operator, I want to stop a running sync job so that I can halt problematic syncs | MUST HAVE | MTO-21 |
| 5 | As an operator, I want to receive real-time sync updates so that I can observe progress without refreshing | MUST HAVE | MTO-21 |
| 6 | As an operator, I want to see a visual progress bar so that I can quickly assess sync completion | SHOULD HAVE | MTO-21 |
| 7 | As an operator, I want to view recent errors so that I can troubleshoot sync failures | MUST HAVE | MTO-21 |
| 8 | As an operator, I want to see attachment queue status so that I can monitor attachment processing | SHOULD HAVE | MTO-21 |
| 9 | As an operator, I want the dashboard to be responsive so that I can monitor from mobile devices | SHOULD HAVE | MTO-21 |

---

### 2.3 Details of User Stories

---

#### Business Flow

**Step 1:** Operator navigates to `http://{host}:{port}/sync` in a web browser

**Step 2:** Dashboard HTML page loads with project selector, progress section, status cards, error log, and queue status

**Step 3:** Dashboard establishes WebSocket connection to `/sync/live` for real-time updates

**Step 4:** Operator selects a project from the dropdown (or views all projects)

**Step 5:** Dashboard calls `GET /sync/status` (or `GET /sync/status/{projectKey}`) to fetch current state

**Step 6:** Dashboard displays current sync status with progress bar, phase cards, and error log

**Step 7:** (Optional) Operator clicks "Start Sync" → Dashboard calls `POST /sync/start`

**Step 8:** System begins sync and streams events via WebSocket (progress, errors, completion)

**Step 9:** Dashboard updates progress bar, error log, and queue status in real-time

**Step 10:** (Optional) Operator clicks "Stop Sync" → Dashboard calls `POST /sync/stop`

**Step 11:** System gracefully stops sync and sends completion event

> **Note:** The dashboard is a single-page application served as static HTML from Ktor resources. No server-side rendering is required.

---

#### STORY 1: View All Projects Sync Status

> As an operator, I want to view sync status of all projects so that I can monitor overall sync health

**Requirement Details:**

1. REST endpoint `GET /sync/status` returns an array of project sync statuses
2. Each project status includes: projectKey, status, progress percentage, synced/total issues, last sync time, error count
3. Response is JSON format with proper HTTP status codes
4. If no projects are syncing, return empty array

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| projectKey | String | Yes | Jira project key | "MTO" |
| status | String (enum) | Yes | Current sync status | "syncing" / "idle" / "error" / "stopped" |
| progress | Float | Yes | Sync progress percentage (0-100) | 67.5 |
| syncedIssues | Int | Yes | Number of issues synced | 45 |
| totalIssues | Int | Yes | Total issues to sync | 67 |
| lastSyncTime | String (ISO8601) | No | Last successful sync timestamp | "2026-05-07T10:00:00Z" |
| errors | Int | Yes | Number of errors in current/last sync | 3 |

**Acceptance Criteria:**

1. GET /sync/status returns HTTP 200 with JSON array of project statuses
2. Response includes all fields defined in Data Fields table
3. Empty array returned when no projects configured
4. Response time < 200ms for up to 50 projects

---

#### STORY 2: View Detailed Project Sync Status

> As an operator, I want to view detailed sync status for a specific project so that I can diagnose issues

**Requirement Details:**

1. REST endpoint `GET /sync/status/{projectKey}` returns detailed status for one project
2. Includes phase-level breakdown: scan phase, crawl phase, attachment phase
3. Includes recent errors list with timestamps and messages
4. Returns 404 if project key not found

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| projectKey | String | Yes | Jira project key | "MTO" |
| status | String (enum) | Yes | Current sync status | "syncing" |
| progress | Float | Yes | Overall progress percentage | 67.5 |
| phases.scan | Object | Yes | Scan phase details | { status, progress, itemsScanned, totalItems } |
| phases.crawl | Object | Yes | Crawl phase details | { status, progress, issuesCrawled, totalIssues } |
| phases.attachments | Object | Yes | Attachment phase details | { status, progress, processed, total, failed } |
| recentErrors | Array | Yes | Last N errors | [{ message, timestamp, issueKey }] |

**Acceptance Criteria:**

1. GET /sync/status/{projectKey} returns HTTP 200 with detailed JSON
2. Returns HTTP 404 with error message for unknown project key
3. Phase breakdown shows individual progress for scan, crawl, attachments
4. Recent errors limited to last 50 entries
5. Response time < 100ms

---

#### STORY 3: Start Sync Job

> As an operator, I want to start a sync job so that I can trigger synchronization on demand

**Requirement Details:**

1. REST endpoint `POST /sync/start` accepts project key and sync type
2. Supports full sync (re-sync everything) and incremental sync (only changes)
3. Returns error if sync already running for the specified project
4. Triggers the actual sync process asynchronously

**Data Fields (Request):**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| projectKey | String | Yes | Jira project key to sync | "MTO" |
| fullSync | Boolean | No (default: false) | Whether to perform full re-sync | true |

**Data Fields (Response):**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| status | String | Yes | Result of start request | "started" |
| message | String | No | Additional info | "Sync started for MTO" |

**Acceptance Criteria:**

1. POST /sync/start with valid body returns HTTP 200 with { status: "started" }
2. POST /sync/start when sync already running returns HTTP 409 with { error: "already running" }
3. POST /sync/start with missing projectKey returns HTTP 400
4. Sync job starts asynchronously (response returns immediately)
5. WebSocket clients receive progress events after sync starts

**Error Handling:**

- Missing projectKey: HTTP 400 `{ "error": "projectKey is required" }`
- Unknown projectKey: HTTP 404 `{ "error": "Project not found: {key}" }`
- Already running: HTTP 409 `{ "error": "Sync already running for {key}" }`
- Internal error: HTTP 500 `{ "error": "Failed to start sync: {message}" }`

---

#### STORY 4: Stop Sync Job

> As an operator, I want to stop a running sync job so that I can halt problematic syncs

**Requirement Details:**

1. REST endpoint `POST /sync/stop` accepts project key
2. Gracefully stops the running sync (completes current item, then stops)
3. Returns error if no sync running for the specified project
4. WebSocket clients receive "stopped" event

**Data Fields (Request):**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| projectKey | String | Yes | Jira project key to stop | "MTO" |

**Data Fields (Response):**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| status | String | Yes | Result of stop request | "stopped" |

**Acceptance Criteria:**

1. POST /sync/stop with valid body returns HTTP 200 with { status: "stopped" }
2. POST /sync/stop when no sync running returns HTTP 409 with { error: "no sync running" }
3. Sync stops gracefully (current item completes before stopping)
4. WebSocket clients receive completion event with status "stopped"

**Error Handling:**

- Missing projectKey: HTTP 400 `{ "error": "projectKey is required" }`
- No sync running: HTTP 409 `{ "error": "No sync running for {key}" }`
- Internal error: HTTP 500 `{ "error": "Failed to stop sync: {message}" }`

---

#### STORY 5: Real-time WebSocket Updates

> As an operator, I want to receive real-time sync updates so that I can observe progress without refreshing

**Requirement Details:**

1. WebSocket endpoint `WS /sync/live` streams real-time sync events
2. Events are JSON-formatted with type discriminator
3. Multiple clients can connect simultaneously
4. Connection auto-reconnects on disconnect (client-side)
5. Server sends heartbeat every 30 seconds to keep connection alive

**Data Fields (Events):**

| Event Type | Fields | Description |
|-----------|--------|-------------|
| progress | projectKey, synced, total, percentage | Periodic progress update |
| error | projectKey, message, timestamp | Error occurred during sync |
| completed | projectKey, duration, totalSynced | Sync completed successfully |
| attachment_processed | issueKey, filename | Single attachment processed |
| heartbeat | timestamp | Keep-alive signal |

**Acceptance Criteria:**

1. WebSocket connection established at /sync/live
2. Progress events sent at least every 5 seconds during active sync
3. Error events sent immediately when errors occur
4. Completed event sent when sync finishes (success or stopped)
5. Heartbeat sent every 30 seconds when no other events
6. Multiple simultaneous WebSocket connections supported
7. Connection gracefully closed when server shuts down

---

#### STORY 6: Visual Progress Bar

> As an operator, I want to see a visual progress bar so that I can quickly assess sync completion

**Requirement Details:**

1. Animated progress bar showing sync completion percentage
2. Shows synced/total count next to progress bar
3. Color changes based on status: blue (syncing), green (complete), red (error)
4. Updates in real-time via WebSocket events

**Acceptance Criteria:**

1. Progress bar displays current percentage (0-100%)
2. Progress bar animates smoothly between updates
3. Synced/total count displayed (e.g., "45/67 issues")
4. Color coding: blue=syncing, green=complete, red=error, gray=idle

---

#### STORY 7: Error Log Display

> As an operator, I want to view recent errors so that I can troubleshoot sync failures

**Requirement Details:**

1. Scrollable list of recent sync errors
2. Each error shows: timestamp, project key, error message
3. New errors appear at top (most recent first)
4. Maximum 100 errors displayed (older ones removed)
5. Updates in real-time via WebSocket error events

**Acceptance Criteria:**

1. Error log displays recent errors in reverse chronological order
2. Each entry shows timestamp, project key, and message
3. New errors from WebSocket appear immediately at top
4. List scrollable with max 100 entries
5. Clear button to dismiss all errors from view

---

#### STORY 8: Attachment Queue Status

> As an operator, I want to see attachment queue status so that I can monitor attachment processing

**Requirement Details:**

1. Display attachment processing queue metrics
2. Shows: pending, processing, completed, failed counts
3. Updates in real-time via WebSocket attachment_processed events

**UI Specifications:**

| No. | Name | Type | Required | Description | Note |
|-----|------|------|----------|-------------|------|
| 1 | Pending Count | Label | Yes | Number of attachments waiting | Badge style |
| 2 | Processing Count | Label | Yes | Number currently being processed | Animated indicator |
| 3 | Completed Count | Label | Yes | Number successfully processed | Green text |
| 4 | Failed Count | Label | Yes | Number that failed processing | Red text, clickable for details |

**Acceptance Criteria:**

1. Queue status shows 4 counters: pending, processing, completed, failed
2. Counters update in real-time as attachments are processed
3. Failed count is clickable to show failed attachment details
4. Counters reset when new sync starts

---

#### STORY 9: Responsive Mobile Design

> As an operator, I want the dashboard to be responsive so that I can monitor from mobile devices

**Requirement Details:**

1. Dashboard layout adapts to screen sizes (desktop, tablet, mobile)
2. Single-column layout on mobile (< 768px)
3. Two-column layout on tablet (768px - 1024px)
4. Full layout on desktop (> 1024px)
5. Touch-friendly buttons and controls

**Acceptance Criteria:**

1. Dashboard renders correctly on mobile (320px - 767px width)
2. Dashboard renders correctly on tablet (768px - 1024px width)
3. Dashboard renders correctly on desktop (> 1024px width)
4. All interactive elements have minimum 44px touch target
5. No horizontal scrolling on any viewport size

---

## 3. Dependencies

| Dependency | Type | Related Ticket | Description |
|------------|------|----------------|-------------|
| Database Schema | System | MTO Story 1 | Sync state data storage — needed for status queries |
| Project Scanner | System | MTO Story 3 | Sync logic to trigger/stop — needed for start/stop endpoints |
| Ktor Framework | Infrastructure | N/A | Already available in codebase (v3.4.0) |
| kotlinx.serialization | Infrastructure | N/A | Already available for JSON serialization |
| Ktor WebSocket plugin | Infrastructure | N/A | Needs to be added to Ktor configuration |

---

## 4. Stakeholders

| Role | Name / Team | Responsibility | Source |
|------|-------------|----------------|--------|
| Product Owner | Duc Nguyen | Define requirements, accept deliverables | Jira reporter |
| Developer | Unassigned | Implement dashboard | Jira assignee |
| Operator | DevOps Team | Primary user of dashboard | End user |
| QA | QA Team | Test dashboard functionality | Testing |

---

## 5. Risks and Assumptions

### 5.1 Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| WebSocket connection instability | Medium | Medium | Implement client-side auto-reconnect with exponential backoff |
| High frequency events overwhelming browser | Medium | Low | Throttle UI updates to max 10fps, batch events |
| Sync state data not available (Story 1 not done) | High | Medium | Use mock data layer until Story 1 complete |
| Large number of concurrent WebSocket connections | Low | Low | Implement connection limit (max 50) |
| Browser compatibility issues with WebSocket | Low | Low | Use standard WebSocket API, provide fallback polling |

### 5.2 Assumptions

- Ktor server is already running and accessible on configured port
- Database schema (Story 1) will be available before implementation starts
- Project Scanner (Story 3) provides an interface to start/stop sync
- Single server deployment (no load balancer WebSocket routing needed)
- Modern browsers only (Chrome, Firefox, Safari, Edge — latest 2 versions)
- Dashboard served from same origin as API (no CORS issues)

---

## 6. Non-Functional Requirements

| Category | Requirement | Details |
|----------|-------------|---------|
| Performance | API response time | GET endpoints < 200ms, POST endpoints < 500ms |
| Performance | WebSocket latency | Events delivered within 1 second of occurrence |
| Performance | Dashboard load time | Initial page load < 2 seconds |
| Scalability | Concurrent connections | Support up to 50 simultaneous WebSocket connections |
| Scalability | Projects monitored | Support up to 100 projects in status view |
| Availability | Dashboard uptime | Same as Ktor server uptime (no separate deployment) |
| Security | Access control | No authentication required (internal tool, future enhancement) |
| Usability | Browser support | Chrome, Firefox, Safari, Edge (latest 2 versions) |
| Usability | Responsive design | Mobile-friendly (320px minimum width) |
| Maintainability | No external JS frameworks | Vanilla JS + CSS only |
| Maintainability | Static serving | HTML/CSS/JS served from Ktor resources |

---

## 7. Related Tickets

| Ticket Key | Summary | Status | Type | Relationship |
|------------|---------|--------|------|--------------|
| MTO-21 | Web Dashboard – Sync Status & Monitoring | Docs Review | Story | Main ticket |
| MTO Story 1 | Database Schema | Unknown | Story | Blocks MTO-21 (sync state data) |
| MTO Story 3 | Project Scanner | Unknown | Story | Blocks MTO-21 (sync logic) |

---

## 8. Appendix

### Configuration

```yaml
dashboard:
  enabled: true
  port: 8081  # or same port as MCP server
  basePath: /sync
```

### API Endpoints Summary

| Method | Path | Description |
|--------|------|-------------|
| GET | /sync/status | All projects sync status |
| GET | /sync/status/{projectKey} | Detailed project sync status |
| POST | /sync/start | Start sync job |
| POST | /sync/stop | Stop sync job |
| WS | /sync/live | Real-time WebSocket events |
| GET | /sync (static) | HTML Dashboard page |

### WebSocket Event Schema

```json
// Progress event
{ "type": "progress", "projectKey": "MTO", "synced": 45, "total": 67, "percentage": 67.16 }

// Error event
{ "type": "error", "projectKey": "MTO", "message": "Failed to fetch issue MTO-99", "timestamp": "2026-05-07T10:15:30Z" }

// Completed event
{ "type": "completed", "projectKey": "MTO", "duration": 120, "totalSynced": 67 }

// Attachment processed event
{ "type": "attachment_processed", "issueKey": "MTO-15", "filename": "screenshot.png" }

// Heartbeat event
{ "type": "heartbeat", "timestamp": "2026-05-07T10:16:00Z" }
```

### Glossary

| Term | Definition |
|------|------------|
| Sync | Process of synchronizing Jira issues and attachments to the knowledge base |
| Full Sync | Complete re-synchronization of all issues (ignores previous state) |
| Incremental Sync | Synchronize only changes since last sync |
| Phase | A stage in the sync process (scan, crawl, attachments) |
| WebSocket | Full-duplex communication protocol for real-time updates |

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Business Flow | [business-flow.png](diagrams/business-flow.png) | [business-flow.drawio](diagrams/business-flow.drawio) |
| 2 | Use Case Diagram | [use-case.png](diagrams/use-case.png) | [use-case.drawio](diagrams/use-case.drawio) |
