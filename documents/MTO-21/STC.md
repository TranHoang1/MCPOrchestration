# Software Test Cases (STC)

## MCPOrchestration — MTO-21: Web Dashboard – Sync Status & Monitoring

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-21 |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Related STP | STP-v1-MTO-21.docx |

---

## 1. PBT — Property-Based Tests

### TC-PBT-001: ProjectSyncStatus serialization roundtrip

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Property | For any valid ProjectSyncStatus, serialize → deserialize produces identical object |
| Generator | Custom Arb for ProjectSyncStatus (all enum values, 0-100 progress) |
| Iterations | 500 |

### TC-PBT-002: Progress calculation always 0-100

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | BR-02 |
| Property | For any (synced, total) where total > 0, progress = synced/total*100 ∈ [0, 100] |
| Generator | Arb.pair(Arb.int(0..10000), Arb.int(1..10000)) |
| Iterations | 1000 |

---

## 2. UT — Unit Tests

### TC-001: GET /sync/status — returns all project statuses

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-01, BR-02, BR-03 |
| Precondition | Mock service returns 2 projects |
| Expected | 200 OK, JSON array with 2 items, correct progress calculation |

### TC-002: GET /sync/status — empty array when no projects

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-01 |
| Precondition | Mock service returns empty list |
| Expected | 200 OK, `[]` |

### TC-003: GET /sync/status/{key} — detailed status with phases

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-02 |
| Input | GET /sync/status/MTO |
| Expected | 200 OK, response includes phases.scan, phases.crawl, phases.attachments |

### TC-004: GET /sync/status/{key} — 404 for unknown project

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-02 |
| Input | GET /sync/status/UNKNOWN |
| Expected | 404, `{ "error": "Project not found: UNKNOWN" }` |

### TC-005: POST /sync/start — starts sync successfully

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-03 |
| Input | `{ "projectKey": "MTO" }` |
| Expected | 200, `{ "status": "started" }`, ProjectScanner.scan() called |

### TC-006: POST /sync/start — 400 for missing projectKey

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-03 |
| Input | `{}` |
| Expected | 400, `{ "error": "projectKey is required" }` |

### TC-007: POST /sync/start — 409 when already running

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-03 |
| Precondition | Sync already running for MTO |
| Expected | 409, `{ "error": "Sync already running for MTO" }` |

### TC-008: POST /sync/stop — stops running sync

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-04 |
| Input | `{ "projectKey": "MTO" }` |
| Expected | 200, `{ "status": "stopped" }`, cancelScan() called |

### TC-009: POST /sync/stop — 409 when not running

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-04 |
| Precondition | No sync running for MTO |
| Expected | 409, `{ "error": "No sync running for MTO" }` |

---

## 3. IT — Integration Tests

### TC-IT-001: REST endpoints with real DB state

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Precondition | Testcontainers PostgreSQL with sync state data |
| Steps | 1. GET /sync/status 2. Verify matches DB |
| Expected | Response reflects actual jira_sync_state table |

### TC-IT-002: WebSocket connection and event reception

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Steps | 1. Connect WS /sync/live 2. Trigger sync 3. Receive events |
| Expected | Progress events received, correct JSON format |

### TC-IT-003: WebSocket heartbeat every 30s

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Requirement | BR-05 |
| Steps | 1. Connect WS 2. Wait 35s 3. Check heartbeat received |
| Expected | At least 1 heartbeat event within 35s |

### TC-IT-004: POST /sync/start triggers real scanner

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Precondition | Testcontainers PostgreSQL + mock Jira |
| Steps | 1. POST /sync/start 2. Poll GET /sync/status/MTO |
| Expected | Status transitions: idle → syncing → completed |

### TC-IT-005: Static HTML dashboard served correctly

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Steps | 1. GET /sync 2. Verify HTML response |
| Expected | 200 OK, Content-Type: text/html, contains dashboard elements |

---

## 4. E2E-API — End-to-End Tests

### TC-E2E-001: Full sync lifecycle via REST + WebSocket

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Steps | 1. Connect WS 2. POST /sync/start 3. Receive progress events 4. Receive completed event |
| Expected | Events match actual sync progress, completed event has correct totals |

### TC-E2E-002: Multiple WebSocket clients receive same events

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Steps | 1. Connect 3 WS clients 2. Trigger sync 3. Verify all receive events |
| Expected | All 3 clients receive identical event stream |

### TC-E2E-003: WebSocket progress throttling (max 1 per 5s)

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Requirement | BR-06 |
| Steps | 1. Connect WS 2. Trigger fast sync (many pages) 3. Count progress events |
| Expected | Progress events spaced ≥ 5s apart |

### TC-E2E-004: Max 50 WebSocket connections enforced

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Requirement | BR-04 |
| Steps | 1. Open 50 WS connections 2. Try 51st |
| Expected | 51st connection rejected with appropriate error |

---

## 5. E2E-UI — Browser Tests

### TC-UI-001: Dashboard loads and shows project list

| Attribute | Value |
|-----------|-------|
| Level | E2E-UI |
| Tool | Playwright |
| Steps | 1. Navigate to /sync 2. Verify project selector visible 3. Verify status cards |
| Expected | Dashboard renders, project dropdown populated |

### TC-UI-002: Progress bar updates in real-time via WebSocket

| Attribute | Value |
|-----------|-------|
| Level | E2E-UI |
| Steps | 1. Open dashboard 2. Start sync 3. Watch progress bar |
| Expected | Progress bar animates from 0% to 100% |

### TC-UI-003: Error log displays errors from WebSocket

| Attribute | Value |
|-----------|-------|
| Level | E2E-UI |
| Steps | 1. Open dashboard 2. Trigger error during sync 3. Check error log |
| Expected | Error appears in scrollable error log section |

### TC-UI-004: Responsive layout — mobile viewport

| Attribute | Value |
|-----------|-------|
| Level | E2E-UI |
| Steps | 1. Set viewport 375px 2. Navigate to /sync 3. Verify single-column layout |
| Expected | All sections stacked vertically, no horizontal overflow |

---

## 6. SIT — System Integration Tests (Manual)

### TC-SIT-001: Cross-browser visual verification

| Attribute | Value |
|-----------|-------|
| Level | SIT |
| Steps | 1. Open dashboard in Chrome, Firefox, Safari 2. Compare layouts |
| Expected | Consistent rendering across browsers |

### TC-SIT-002: Real sync monitoring end-to-end

| Attribute | Value |
|-----------|-------|
| Level | SIT |
| Steps | 1. Open dashboard 2. Start real Jira sync 3. Monitor progress 4. Verify completion |
| Expected | Dashboard accurately reflects real sync progress |
