# Software Test Cases (STC)

## MCPOrchestration — MTO-17: Project Scanner — Breadth-First Incremental Scan

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-17 |
| Title | Project Scanner — Breadth-First Incremental Scan |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related STP | STP-v1-MTO-17.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | QA Agent | Initial STC — 32 test cases across 5 levels |

---

## 1. PBT — Property-Based Tests

### TC-PBT-001: JqlBuilder produces valid JQL for any valid project key

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | BR-01, BR-02, BR-07 |
| Property | For any string matching `[A-Z][A-Z0-9_]+`, JqlBuilder.build() returns a string containing `project = "{key}"` and `ORDER BY updated DESC` |
| Generator | Arb.string(minSize=2, maxSize=10).filter { it.matches(Regex("[A-Z][A-Z0-9_]+")) } |
| Iterations | 1000 |

### TC-PBT-002: MetadataParser never throws for valid Jira JSON structure

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | EF-04 |
| Property | For any JSON object with required fields (key, fields.summary, fields.status.name, fields.issuetype.name, fields.priority.name, fields.updated), parser returns JiraTicketMetadata without exception |
| Generator | Custom Arb generating valid Jira issue JSON |
| Iterations | 500 |

### TC-PBT-003: Concurrency parameter clamped to 1-20

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | BR-04 |
| Property | For any Int, ScanOptions validation clamps concurrency to range [1, 20] |
| Generator | Arb.int(min=-100, max=100) |
| Iterations | 1000 |

### TC-PBT-004: Project key validation rejects invalid patterns

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | Input validation |
| Property | For any string NOT matching `[A-Z][A-Z0-9_]+`, scan() throws InvalidProjectKeyException |
| Generator | Arb.string().filter { !it.matches(Regex("[A-Z][A-Z0-9_]+")) } |
| Iterations | 1000 |

---

## 2. UT — Unit Tests

### TC-001: Full scan — JQL construction for new project

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-01, BR-01, BR-02 |
| Precondition | No previous sync state exists |
| Input | projectKey = "MTO", forceFullScan = false |
| Steps | 1. Call JqlBuilder.build("MTO", ScanType.FULL, null) |
| Expected | Returns `project = "MTO" ORDER BY updated DESC` |

### TC-002: Full scan — processes all pages sequentially

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-01, BR-01 |
| Precondition | Mock JiraRestClient returns 150 total issues (3 pages) |
| Input | projectKey = "TEST", concurrency = 1 |
| Steps | 1. Call scan("TEST", ScanOptions(concurrency=1)) |
| Expected | 3 page fetches (offset 0, 50, 100), 150 tickets upserted, ScanResult.status = COMPLETED |

### TC-003: Full scan — marks state RUNNING then COMPLETED

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-01 |
| Precondition | Mock SyncStateManager |
| Steps | 1. Call scan("PROJ") 2. Verify markRunning called 3. Verify markCompleted called |
| Expected | State transitions: markRunning(PROJ, totalIssues) → updateProgress (per page) → markCompleted(PROJ) |

### TC-004: Full scan — empty project (0 issues)

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-01 |
| Precondition | Mock returns total=0 |
| Input | projectKey = "EMPTY" |
| Expected | ScanResult(totalIssues=0, syncedIssues=0, status=COMPLETED) |

### TC-005: Full scan — single page project (≤50 issues)

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-01, BR-01 |
| Precondition | Mock returns total=30 |
| Expected | Only 1 page fetch, 30 tickets upserted |

### TC-006: Incremental scan — JQL includes updated filter

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-02, BR-07, BR-08 |
| Precondition | Previous state: COMPLETED, lastSyncTime = 2026-05-01T10:00:00Z |
| Steps | 1. Call JqlBuilder.build("MTO", ScanType.INCREMENTAL, lastSyncTime) |
| Expected | JQL contains `AND updated > "2026-05-01 09:59"` (1-min buffer applied) |

### TC-007: Incremental scan — 1-minute buffer applied

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-08 |
| Precondition | lastSyncTime = 2026-05-08T15:30:00Z |
| Expected | JQL date = "2026-05-08 15:29" (minus 1 minute) |

### TC-008: Incremental scan — 0 results still updates last_sync_time

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-09 |
| Precondition | Mock returns total=0 for incremental query |
| Expected | markCompleted called (updates last_sync_time), ScanResult(totalIssues=0, status=COMPLETED) |

### TC-009: Incremental scan — forceFullScan overrides

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-02 AF-02 |
| Precondition | Previous state exists with lastSyncTime |
| Input | forceFullScan = true |
| Expected | JQL does NOT contain `AND updated >`, uses full scan JQL |

### TC-010: Resume scan — starts from last_offset

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-03, BR-10 |
| Precondition | State: RUNNING, lastOffset=100, totalIssues=200 |
| Steps | 1. Call scan("PROJ") |
| Expected | First page fetch starts at offset=100, not 0 |

### TC-011: Resume scan — uses same JQL as original

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-03, BR-10 |
| Precondition | State: RUNNING, lastSyncTime=null (was full scan) |
| Expected | JQL = full scan JQL (no updated filter) |

### TC-012: Resume scan — idempotent upsert handles duplicates

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-11 |
| Precondition | Ticket "MTO-1" already in cache with updatedAt=T1 |
| Input | Upsert same ticket with updatedAt=T1 |
| Expected | No update (WHERE clause: existing.updatedAt < new.updatedAt fails) |

### TC-013: Stale RUNNING state (>1 hour) treated as restartable

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-12 |
| Precondition | State: RUNNING, startedAt = 2 hours ago |
| Steps | 1. Call scan("PROJ") |
| Expected | Does NOT throw ScanAlreadyRunningException, starts new scan |

### TC-014: Concurrent processing — semaphore limits active requests

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-04, BR-13 |
| Precondition | Mock with 200 issues (4 pages), concurrency=2 |
| Steps | 1. Track max concurrent fetchPage calls |
| Expected | Never more than 2 concurrent fetches active |

### TC-015: SupervisorJob — one page failure doesn't cancel others

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-04, BR-14 |
| Precondition | Page at offset=50 throws exception, pages at 0, 100, 150 succeed |
| Expected | 3 pages processed successfully, 1 failed, scan continues |

### TC-016: Scope cancellation — cancelScan stops all children

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-04, BR-15 |
| Precondition | Scan running with slow mock (delay per page) |
| Steps | 1. Start scan 2. Call cancelScan("PROJ") |
| Expected | All coroutines cancelled, scan returns CANCELLED status |

### TC-017: Progress — total_issues set from first response

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-16 |
| Precondition | First page response has total=500 |
| Expected | markRunning called with totalIssues=500 |

### TC-018: Progress — synced_issues incremented per batch

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-17 |
| Precondition | 3 pages of 50, 50, 30 issues |
| Expected | updateProgress called with syncedIssues: 50, 100, 130 |

### TC-019: Progress — getProgress returns correct percentage

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-18 |
| Precondition | State: totalIssues=200, syncedIssues=100 |
| Expected | getProgress returns percentage=50 |

---

## 3. IT — Integration Tests

### TC-020: BatchUpserter — INSERT new tickets into PostgreSQL

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Requirement | UC-01, BR-01 |
| Precondition | Empty jira_ticket_cache table, Testcontainers PostgreSQL |
| Input | Batch of 50 JiraTicketMetadata objects |
| Steps | 1. Call upsertBatch(batch) 2. Query jira_ticket_cache |
| Expected | 50 rows inserted, all fields match input |

### TC-021: BatchUpserter — UPSERT updates only newer tickets (BR-03)

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Requirement | BR-03 |
| Precondition | Existing row: MTO-1, updatedAt=2026-05-01T10:00:00Z |
| Input | Upsert MTO-1 with updatedAt=2026-05-02T10:00:00Z (newer) |
| Expected | Row updated with new data |
| Input 2 | Upsert MTO-1 with updatedAt=2026-04-30T10:00:00Z (older) |
| Expected 2 | Row NOT updated (WHERE clause prevents) |

### TC-022: SyncStateManager — full lifecycle (IDLE → RUNNING → COMPLETED)

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Requirement | UC-01 |
| Precondition | Testcontainers PostgreSQL with Flyway migrations |
| Steps | 1. markRunning("PROJ", 100) 2. updateProgress("PROJ", 50, 50) 3. markCompleted("PROJ") 4. getState("PROJ") |
| Expected | Final state: status=COMPLETED, lastSyncTime set, syncedIssues=50 |

### TC-023: SyncStateManager — markFailed preserves checkpoint

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Requirement | EF-01 |
| Precondition | State: RUNNING, lastOffset=100, syncedIssues=100 |
| Steps | 1. markFailed("PROJ", "Network error") 2. getState("PROJ") |
| Expected | status=FAILED, lastOffset=100 preserved, errorMessage set |

### TC-024: Full scan with real DB — 150 tickets across 3 pages

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Requirement | UC-01 |
| Precondition | Testcontainers PostgreSQL, Mock JiraRestClient returning 150 issues |
| Steps | 1. Call scan("TEST") 2. Query jira_ticket_cache WHERE project_key='TEST' |
| Expected | 150 rows in cache, sync state = COMPLETED |

### TC-025: Checkpoint persistence — verify offset saved after each page

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Requirement | BR-05 |
| Precondition | Testcontainers PostgreSQL, 3-page scan |
| Steps | 1. After each page, query jira_sync_state.last_offset |
| Expected | Offsets: 50, 100, 150 saved sequentially |

---

## 4. E2E-API — End-to-End API Tests

### TC-026: Full scan lifecycle — start to completion

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Requirement | UC-01 |
| Precondition | Ktor testApplication with Testcontainers PostgreSQL + WireMock Jira |
| Steps | 1. Configure WireMock to return 100 issues (2 pages) 2. Call projectScanner.scan("DEMO") 3. Verify result |
| Expected | ScanResult(totalIssues=100, syncedIssues=100, status=COMPLETED), DB has 100 rows |

### TC-027: Incremental scan — only fetches updated tickets

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Requirement | UC-02 |
| Precondition | Previous scan completed (lastSyncTime set), WireMock returns 10 updated issues |
| Steps | 1. Call scan("DEMO") 2. Verify WireMock received JQL with `updated >` filter |
| Expected | Only 10 tickets upserted, WireMock verifies correct JQL |

### TC-028: Resume after interruption — continues from checkpoint

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Requirement | UC-03 |
| Precondition | Manually set state: RUNNING, lastOffset=50, totalIssues=150 |
| Steps | 1. Call scan("DEMO") 2. Verify first fetch starts at offset=50 |
| Expected | Only pages 50, 100 fetched (not 0), total synced = 100 new + previous 50 |

### TC-029: Error recovery — network failure mid-scan saves checkpoint

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Requirement | EF-01 |
| Precondition | WireMock: page 0 OK, page 50 returns 500 (3 times) |
| Steps | 1. Call scan("DEMO") expecting failure |
| Expected | State: FAILED, lastOffset=50, syncedIssues=50, first page data preserved in DB |

### TC-030: Rate limit handling — pauses and resumes

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Requirement | EF-02 |
| Precondition | WireMock: page 0 returns 429 with Retry-After: 1, then 200 on retry |
| Steps | 1. Call scan("DEMO") 2. Measure timing |
| Expected | Scan completes successfully after ~1s delay, no data loss |

---

## 5. SIT — System Integration Tests (Manual)

### TC-031: Real Jira sandbox — full project scan

| Attribute | Value |
|-----------|-------|
| Level | SIT (Manual) |
| Requirement | UC-01, NFR-01 |
| Precondition | Real Jira sandbox with ≥100 tickets, application deployed |
| Steps | 1. Trigger scan for sandbox project 2. Monitor progress 3. Verify all tickets cached 4. Measure throughput |
| Expected | All tickets synced, throughput ≥200 tickets/min, no errors |
| Pass Criteria | All tickets in Jira appear in jira_ticket_cache with correct metadata |

### TC-032: Real Jira — incremental scan after ticket update

| Attribute | Value |
|-----------|-------|
| Level | SIT (Manual) |
| Requirement | UC-02, NFR-02 |
| Precondition | Previous full scan completed, then update 3 tickets in Jira |
| Steps | 1. Wait 2 minutes 2. Trigger incremental scan 3. Verify only 3 tickets fetched 4. Verify page fetch <5s |
| Expected | Only updated tickets re-synced, cache reflects new data, latency within target |
