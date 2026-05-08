# Release Notes (RLN)

## MCPOrchestration — MTO-17: Project Scanner — Breadth-First Incremental Scan

---

## Release Information

| Field | Value |
|-------|-------|
| Release Version | 1.2.0 |
| Release Date | 2025-07-18 |
| Jira Ticket | MTO-17 |
| Environment | DEV / SIT / UAT / PROD |
| Author | DevOps Agent |
| Status | Final |
| Related Epic | MTO-14 — Jira Project Sync Service |

---

## 1. Release Summary

This release introduces the **Project Scanner** module — a coroutine-based service that performs breadth-first scanning of Jira projects. It fetches lightweight ticket metadata via paginated JQL search and caches results in PostgreSQL using efficient batch upserts. Supports full scans, incremental scans (based on last sync time), and resumable scans (from checkpoint offset).

**Release Type:** Feature addition (non-breaking, additive only)
**Risk Level:** Low
**Backward Compatible:** Yes — no existing APIs or schemas modified

---

## 2. What's New

### 2.1 New Features

| # | Feature | Description |
|---|---------|-------------|
| 1 | **Full Project Scan** | Fetches all issues in a Jira project via paginated JQL, caches metadata in `jira_ticket_cache` |
| 2 | **Incremental Scan** | Only fetches issues updated since last sync (with 1-minute buffer) |
| 3 | **Resumable Scan** | Resumes from last checkpoint offset after interruption |
| 4 | **Concurrent Page Fetching** | Semaphore-based concurrency (configurable 1-20, default 5) |
| 5 | **Batch Upsert** | INSERT ON CONFLICT for efficient bulk metadata caching |
| 6 | **Stale Scan Detection** | Automatically detects and recovers from stale RUNNING states |
| 7 | **Progress Tracking** | Real-time progress via `SyncStateManager` (percentage, synced/total) |

### 2.2 Technical Highlights

- **Structured Concurrency** — SupervisorJob ensures individual page failures don't crash the entire scan
- **Checkpoint-First** — State saved before processing each batch for guaranteed resumability
- **Idempotent Upserts** — Safe to re-process pages (INSERT ON CONFLICT with `updated_at` guard)
- **Rate Limit Handling** — Exponential backoff on Jira 429 responses (1s, 2s, 4s)
- **Configuration-Driven** — All parameters tunable via `application.yml`

---

## 3. New Components

### 3.1 Package: `com.orchestrator.mcp.scanner`

| Class | Type | Responsibility |
|-------|------|----------------|
| `ProjectScanner` | Interface | Scan lifecycle API (scan, getProgress, cancelScan) |
| `ProjectScannerImpl` | Class | Main orchestration with coroutine management |
| `PageFetcher` | Class | Concurrent page fetching via JiraRestClient |
| `BatchUpserter` | Class | Exposed ORM batch INSERT ON CONFLICT |
| `JqlBuilder` | Class | JQL query construction (full/incremental/resumed) |
| `MetadataParser` | Class | Jira JSON response → JiraTicketMetadata mapping |
| `ScannerConfig` | Data class | Configuration parameters |
| `ScannerModule` | Koin module | DI bindings for all scanner services |

### 3.2 API (Internal Service)

| Function | Description |
|----------|-------------|
| `scan(projectKey, options)` | Start or resume a project scan |
| `getProgress(projectKey)` | Query current scan progress (percentage, counts) |
| `cancelScan(projectKey)` | Cancel a running scan gracefully |

---

## 4. Configuration Changes

### 4.1 New Configuration Section

```yaml
scanner:
  concurrency: 5
  pageSize: 50
  staleTimeout: 3600
  syncBufferMinutes: 1
  enabled: true
  autoResume: true
```

No changes to existing configuration sections.

---

## 5. Dependencies

### 5.1 No New Dependencies

This release introduces **zero new external dependencies**. All libraries used are already present:

| Library | Version | Usage in MTO-17 |
|---------|---------|-----------------|
| kotlinx.coroutines | 1.10.2 | Structured concurrency, Semaphore |
| Exposed ORM | 0.61.0 | Batch upsert operations |
| Ktor Client | 3.4.0 | Via JiraRestClient (MTO-16) |
| Koin | 4.1.1 | DI bindings |

### 5.2 Internal Dependencies

| Module | Required | Relationship |
|--------|----------|-------------|
| MTO-15 (DB Schema) | ✅ | Uses jira_sync_state + jira_ticket_cache tables |
| MTO-16 (Jira Client) | ✅ | Uses JiraRestClient for API calls |

---

## 6. Testing Summary

| Level | Tests | Passed | Failed | Coverage |
|-------|-------|--------|--------|----------|
| Unit Tests | 15 | 15 | 0 | JqlBuilder, MetadataParser, BatchUpserter, ProjectScannerImpl |
| Integration Tests | 8 | 8 | 0 | Full scan with Testcontainers PostgreSQL |
| **Total** | **23** | **23** | **0** | — |

### 6.1 Test Highlights

- **JqlBuilder** — Full, incremental, and resumed JQL generation verified
- **MetadataParser** — All Jira field mappings tested including null handling
- **BatchUpserter** — Upsert behavior verified (insert new, update existing, skip unchanged)
- **ProjectScannerImpl** — Scan lifecycle, cancellation, stale detection, error handling
- **Integration** — End-to-end scan with real PostgreSQL (Testcontainers)

---

## 7. Known Issues & Limitations

| # | Issue | Severity | Workaround | Planned Fix |
|---|-------|----------|------------|-------------|
| 1 | Single project per scan (no parallel multi-project) | Low | Run scans sequentially | Future enhancement |
| 2 | Page size fixed at 50 | Low | N/A (Jira best practice) | Not planned |
| 3 | No automatic scheduling | Low | Trigger manually via MCP tool (MTO-20) | Future scheduler story |

---

## 8. Migration Notes

### 8.1 From Previous Version

- **No breaking changes** — existing functionality untouched
- **No schema changes** — uses existing MTO-15 tables
- **New config section** — add `scanner:` to `application.yml`
- **Automatic startup** — scanner registers on boot but doesn't scan until triggered

### 8.2 Verification After Upgrade

```bash
# Verify scanner module loaded
grep "ScannerModule" app.log
# Expected: "ScannerModule registered successfully"
```

---

## 9. Rollback Instructions

1. Stop application
2. Restore previous JAR (`mcp-orchestrator-all.jar.bak`)
3. Remove `scanner:` section from `application.yml` (optional)
4. Restart application
5. Verify existing features work

**Data loss risk:** None — scanner only writes to existing tables, no destructive operations.

---

## 10. Future Roadmap (Epic MTO-14)

| Story | Status | Dependency on MTO-17 |
|-------|--------|---------------------|
| MTO-15: DB Schema & Sync State | ✅ Deployed | Prerequisite |
| MTO-16: Jira REST Client | ✅ Deployed | Prerequisite |
| **MTO-17: Project Scanner** | ✅ **This release** | — |
| MTO-18: Ticket Crawler | Next | Uses scanner's cached metadata |
| MTO-19: Attachment Processor | Planned | Uses scanner's ticket cache |
| MTO-20: MCP Tool Integration | Planned | Invokes ProjectScanner.scan() |
| MTO-21: Web Dashboard | Planned | Reads scanner progress |
| MTO-22: 3D Graph Visualization | Planned | Reads cached ticket data |

---

## 11. Contact & Support

| Role | Contact | Responsibility |
|------|---------|----------------|
| Developer | DEV Agent | Implementation questions |
| Architect | SA Agent | Design decisions |
| QA | QA Agent | Test failures |
| DevOps | DevOps Agent | Deployment issues |
