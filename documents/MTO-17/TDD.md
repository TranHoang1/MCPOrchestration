# Technical Design Document (TDD)

## MCPOrchestration — MTO-17: Project Scanner — Breadth-First Incremental Scan

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-17 |
| Title | Project Scanner — Breadth-First Incremental Scan |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-17.docx |
| Related FSD | FSD-v1-MTO-17.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | SA Agent | Initial TDD — auto-generated from FSD v1.0 |

---

## 1. Introduction

### 1.1 Purpose

This TDD specifies the technical implementation of the **ProjectScanner** component — a coroutine-based service that performs breadth-first scanning of Jira projects, fetching lightweight ticket metadata and caching it in PostgreSQL. It covers architecture decisions, class design, database interactions, and deployment considerations.

### 1.2 Scope

- ProjectScanner class implementation with coroutine-based concurrency
- SyncStateManager integration for checkpoint persistence
- JiraRestClient integration for paginated JQL search
- Batch upsert logic for jira_ticket_cache table
- Error handling, retry, and rate-limit strategies
- Configuration and deployment

### 1.3 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.3.20 |
| Platform | JVM | 21 |
| Framework | Ktor (Netty) | 3.4.0 |
| DI | Koin | 4.1.1 |
| Coroutines | kotlinx.coroutines | 1.10.2 |
| Serialization | kotlinx.serialization-json | 1.8.1 |
| Database ORM | Exposed | 0.61.0 |
| Database | PostgreSQL | 16+ |
| Date/Time | kotlinx.datetime | 0.6.2 |
| Logging | Logback Classic | 1.5.18 |
| Testing | Kotest + MockK + Testcontainers | 5.9.1 / 1.14.2 / 1.21.1 |

### 1.4 Design Principles

- **Interface/Impl separation** — all services use interface + implementation pattern
- **Structured concurrency** — SupervisorJob + Semaphore for fault isolation
- **Checkpoint-first** — save state before processing to ensure resumability
- **Idempotent upserts** — INSERT ON CONFLICT ensures safe re-processing
- **Configuration-driven** — all tunable parameters in YAML config

### 1.5 Constraints

- Must integrate with existing Koin DI module (AppModule.kt)
- Must use existing JiraRestClient from MTO-16
- Must use existing database schema from MTO-15 (jira_sync_state, jira_ticket_cache tables)
- Single project per scan job (no multi-project parallel scanning)
- Page size fixed at 50 (Jira API best practice)

### 1.6 References

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-17.docx |
| FSD | FSD-v1-MTO-17.docx |
| MTO-15 TDD | documents/MTO-15/TDD.md |
| MTO-16 TDD | documents/MTO-16/TDD.md |

---

## 2. System Architecture

### 2.1 Architecture Overview

The ProjectScanner is a new package within the existing MCPOrchestration application. It integrates with the JiraRestClient (MTO-16) for API calls and SyncStateManager (MTO-15) for state persistence.

![Architecture Diagram](diagrams/architecture.png)

```
┌─────────────────────────────────────────────────────────────┐
│                    MCPOrchestration App                       │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              scanner/ package (NEW)                   │    │
│  │                                                      │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────┐  │    │
│  │  │ProjectScanner│→ │ PageFetcher  │→ │  Batch   │  │    │
│  │  │  (Service)   │  │ (Coroutine)  │  │ Upserter │  │    │
│  │  └──────────────┘  └──────────────┘  └──────────┘  │    │
│  │         ↕                                    ↕       │    │
│  │  ┌──────────────────────────────────────────────┐   │    │
│  │  │         SyncStateManager (MTO-15)            │   │    │
│  │  └──────────────────────────────────────────────┘   │    │
│  └─────────────────────────────────────────────────────┘    │
│         ↕                              ↕                     │
│  ┌──────────────┐              ┌──────────────┐             │
│  │JiraRestClient│              │  PostgreSQL   │             │
│  │  (MTO-16)    │              │  (Exposed)    │             │
│  └──────────────┘              └──────────────┘             │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Component Diagram

![Component Diagram](diagrams/component.png)

| Component | Responsibility | Technology |
|-----------|---------------|------------|
| ProjectScanner | Orchestrates scan lifecycle (start, resume, complete) | Kotlin Coroutines |
| PageFetcher | Fetches individual pages from Jira API concurrently | Ktor HttpClient via JiraRestClient |
| BatchUpserter | Upserts parsed metadata into jira_ticket_cache | Exposed ORM |
| SyncStateManager | CRUD for jira_sync_state (checkpoint, progress) | Exposed ORM (from MTO-15) |
| JiraRestClient | HTTP calls to Jira REST API v3 | Ktor Client (from MTO-16) |
| ScannerConfig | Configuration data class for scanner parameters | kaml YAML |

### 2.3 Deployment Architecture

The ProjectScanner runs within the same JVM process as the MCP Orchestrator. No separate deployment is needed.

![Deployment Diagram](diagrams/deployment.png)

### 2.4 Communication Patterns

| From | To | Protocol | Pattern | Description |
|------|----|----------|---------|-------------|
| ProjectScanner | JiraRestClient | In-process | Sync (suspend) | Fetch pages via searchIssues() |
| ProjectScanner | SyncStateManager | In-process | Sync (suspend) | Read/write sync state |
| ProjectScanner | BatchUpserter | In-process | Sync (suspend) | Upsert ticket metadata |
| JiraRestClient | Jira Cloud API | HTTPS | Async (Ktor Client) | REST API calls with retry |
| BatchUpserter | PostgreSQL | JDBC | Sync (transaction) | Batch INSERT ON CONFLICT |

---

## 3. API Design

### 3.1 API Overview

The ProjectScanner exposes an internal service API (not HTTP endpoints). It will be invoked by MCP tools (MTO-20) and the Web Dashboard (MTO-21).

| # | Function | Description | Source |
|---|----------|-------------|--------|
| 1 | scan(projectKey, options) | Start or resume a project scan | UC-01, UC-02, UC-03 |
| 2 | getProgress(projectKey) | Query current scan progress | UC-05 |
| 3 | cancelScan(projectKey) | Cancel a running scan | UC-04 |

### 3.2 Internal API: scan()

**Implements:** UC-01, UC-02, UC-03

| Attribute | Value |
|-----------|-------|
| Function | `suspend fun scan(projectKey: String, options: ScanOptions = ScanOptions()): ScanResult` |
| Concurrency | Coroutine-safe, one scan per project at a time |
| Timeout | None (runs until completion or cancellation) |

**Input Parameters:**

```kotlin
data class ScanOptions(
    val concurrency: Int = 5,        // 1-20, Semaphore permits
    val forceFullScan: Boolean = false, // Ignore last_sync_time
    val pageSize: Int = 50           // Fixed at 50 per Jira best practice
)
```

**Output:**

```kotlin
data class ScanResult(
    val totalIssues: Int,
    val syncedIssues: Int,
    val skippedIssues: Int,
    val duration: Duration,
    val scanType: ScanType,  // FULL, INCREMENTAL, RESUMED
    val status: ScanStatus   // COMPLETED, FAILED, CANCELLED
)

enum class ScanType { FULL, INCREMENTAL, RESUMED }
enum class ScanStatus { COMPLETED, FAILED, CANCELLED }
```

**Error Scenarios:**

| Exception | When Thrown |
|-----------|------------|
| ScanAlreadyRunningException | Another scan for same project is RUNNING and not stale |
| InvalidProjectKeyException | Project key doesn't match `[A-Z][A-Z0-9_]+` pattern |
| ScanFailedException | All retries exhausted, scan cannot continue |

### 3.3 Internal API: getProgress()

```kotlin
suspend fun getProgress(projectKey: String): ScanProgress?

data class ScanProgress(
    val projectKey: String,
    val status: SyncStatus,      // IDLE, RUNNING, COMPLETED, FAILED
    val totalIssues: Int,
    val syncedIssues: Int,
    val percentage: Int,          // 0-100
    val startedAt: Instant?,
    val lastSyncTime: Instant?
)
```

### 3.4 Internal API: cancelScan()

```kotlin
suspend fun cancelScan(projectKey: String): Boolean
```

Cancels the CoroutineScope for the running scan. Returns true if a scan was running and cancelled.

---

## 4. Database Design

### 4.1 Schema Overview

The ProjectScanner uses tables defined in MTO-15. No new tables are created.

![Database Schema](diagrams/db-schema.png)

### 4.2 Tables Used

#### Table: jira_sync_state (from MTO-15)

```sql
CREATE TABLE jira_sync_state (
    project_key    VARCHAR(20) PRIMARY KEY,
    status         VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    last_sync_time TIMESTAMPTZ,
    last_offset    INTEGER NOT NULL DEFAULT 0,
    total_issues   INTEGER DEFAULT 0,
    synced_issues  INTEGER DEFAULT 0,
    error_message  TEXT,
    started_at     TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

#### Table: jira_ticket_cache (from MTO-15)

```sql
CREATE TABLE jira_ticket_cache (
    issue_key     VARCHAR(20) PRIMARY KEY,
    project_key   VARCHAR(20) NOT NULL,
    summary       TEXT NOT NULL,
    status        VARCHAR(50) NOT NULL,
    issue_type    VARCHAR(50) NOT NULL,
    priority      VARCHAR(20) NOT NULL,
    assignee      VARCHAR(100),
    updated_at    TIMESTAMPTZ NOT NULL,
    content_hash  VARCHAR(64),
    metadata_json JSONB,
    synced_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ticket_cache_project ON jira_ticket_cache(project_key);
CREATE INDEX idx_ticket_cache_updated ON jira_ticket_cache(updated_at DESC);
```

### 4.3 Upsert Query Pattern

```sql
INSERT INTO jira_ticket_cache (issue_key, project_key, summary, status, issue_type, priority, assignee, updated_at, metadata_json, synced_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW())
ON CONFLICT (issue_key) DO UPDATE SET
    summary = EXCLUDED.summary,
    status = EXCLUDED.status,
    issue_type = EXCLUDED.issue_type,
    priority = EXCLUDED.priority,
    assignee = EXCLUDED.assignee,
    updated_at = EXCLUDED.updated_at,
    metadata_json = EXCLUDED.metadata_json,
    synced_at = NOW()
WHERE jira_ticket_cache.updated_at < EXCLUDED.updated_at;
```

### 4.4 Query Patterns

| Operation | Query Pattern | Expected Performance |
|-----------|--------------|---------------------|
| Read sync state | `SELECT * FROM jira_sync_state WHERE project_key = ?` | < 1ms (PK lookup) |
| Update checkpoint | `UPDATE jira_sync_state SET last_offset = ?, synced_issues = ? WHERE project_key = ?` | < 1ms |
| Batch upsert (50 rows) | INSERT ON CONFLICT (see above) | < 50ms |
| Mark completed | `UPDATE jira_sync_state SET status = 'COMPLETED', last_sync_time = NOW()` | < 1ms |

---

## 5. Class / Module Design

### 5.1 Package Structure

```
com.orchestrator.mcp/
└── scanner/
    ├── ProjectScanner.kt              # Interface
    ├── ProjectScannerImpl.kt          # Implementation (main orchestration)
    ├── PageFetcher.kt                 # Concurrent page fetching logic
    ├── BatchUpserter.kt               # Database upsert operations
    ├── JqlBuilder.kt                  # JQL query construction
    ├── MetadataParser.kt              # Parse Jira response → JiraTicketMetadata
    ├── config/
    │   └── ScannerConfig.kt           # Configuration data class
    ├── model/
    │   ├── JiraTicketMetadata.kt      # Domain model
    │   ├── ScanOptions.kt             # Input options
    │   ├── ScanResult.kt              # Output result
    │   ├── ScanProgress.kt            # Progress query result
    │   └── ScanExceptions.kt          # Custom exceptions
    └── di/
        └── ScannerModule.kt           # Koin module for scanner DI
```

### 5.2 Key Interfaces

```kotlin
interface ProjectScanner {
    suspend fun scan(projectKey: String, options: ScanOptions = ScanOptions()): ScanResult
    suspend fun getProgress(projectKey: String): ScanProgress?
    suspend fun cancelScan(projectKey: String): Boolean
}
```

```kotlin
interface BatchUpserter {
    suspend fun upsertBatch(tickets: List<JiraTicketMetadata>)
}
```

```kotlin
interface PageFetcher {
    suspend fun fetchPage(jql: String, startAt: Int, maxResults: Int): JiraSearchResponse
}
```

### 5.3 Class Diagram

![Class Diagram](diagrams/class-diagram.png)

### 5.4 Implementation Details

#### ProjectScannerImpl

```kotlin
class ProjectScannerImpl(
    private val syncStateManager: SyncStateManager,
    private val pageFetcher: PageFetcher,
    private val batchUpserter: BatchUpserter,
    private val jqlBuilder: JqlBuilder,
    private val config: ScannerConfig
) : ProjectScanner {

    private val runningScans = ConcurrentHashMap<String, Job>()

    override suspend fun scan(projectKey: String, options: ScanOptions): ScanResult {
        validateProjectKey(projectKey)
        checkNotAlreadyRunning(projectKey)

        val state = syncStateManager.getState(projectKey)
        val scanType = determineScanType(state, options)
        val jql = jqlBuilder.build(projectKey, scanType, state?.lastSyncTime)
        val startOffset = if (scanType == ScanType.RESUMED) state!!.lastOffset else 0

        return coroutineScope {
            val job = coroutineContext.job
            runningScans[projectKey] = job

            try {
                executeScan(projectKey, jql, startOffset, options, scanType)
            } finally {
                runningScans.remove(projectKey)
            }
        }
    }
}
```

#### JqlBuilder

```kotlin
class JqlBuilder {
    fun build(projectKey: String, scanType: ScanType, lastSyncTime: Instant?): String {
        return when (scanType) {
            ScanType.FULL, ScanType.RESUMED ->
                """project = "$projectKey" ORDER BY updated DESC"""
            ScanType.INCREMENTAL -> {
                val buffered = lastSyncTime!!.minus(1.minutes)
                val formatted = buffered.toJiraDateFormat()
                """project = "$projectKey" AND updated > "$formatted" ORDER BY updated DESC"""
            }
        }
    }
}
```

### 5.5 Design Patterns

| Pattern | Where Used | Rationale |
|---------|-----------|-----------|
| Interface/Impl | All scanner services | Testability, DI |
| Strategy | JqlBuilder (full vs incremental) | Clean separation of JQL logic |
| Supervisor | ProjectScannerImpl (SupervisorJob) | Fault isolation between page fetches |
| Checkpoint | SyncStateManager updates | Resumability after failure |
| Semaphore | PageFetcher concurrency | Rate limiting API calls |

### 5.6 Error Handling

| Exception | HTTP Status | Error Code | When Thrown |
|-----------|-------------|------------|------------|
| ScanAlreadyRunningException | N/A (internal) | SCAN_ALREADY_RUNNING | Scan triggered while another is active |
| InvalidProjectKeyException | N/A (internal) | INVALID_PROJECT_KEY | Key doesn't match pattern |
| ScanFailedException | N/A (internal) | SCAN_FAILED | All retries exhausted |

---

## 6. Integration Design

### 6.1 External System: Jira REST API v3

| Attribute | Value |
|-----------|-------|
| Protocol | HTTPS (REST) |
| Endpoint | `{jiraBaseUrl}/rest/api/3/search` |
| Authentication | Basic Auth (email + API token) |
| Timeout | 30 seconds per request |
| Retry Policy | Max 3 retries, exponential backoff (1s, 2s, 4s) |
| Circuit Breaker | Not needed (retry handles transient failures) |

**Request:**

```http
GET /rest/api/3/search?jql={jql}&startAt={offset}&maxResults=50&fields=summary,status,issuetype,priority,assignee,issuelinks,parent,updated
```

**Response Mapping:**

| Jira Field | Domain Field | Transformation |
|------------|-------------|----------------|
| `key` | issueKey | Direct |
| `fields.summary` | summary | Direct |
| `fields.status.name` | status | Direct |
| `fields.issuetype.name` | issueType | Direct |
| `fields.priority.name` | priority | Direct, default "Medium" if null |
| `fields.assignee.displayName` | assignee | Null if unassigned |
| `fields.issuelinks[]` | links (JSONB) | Map to TicketLink list |
| `fields.parent.key` | parent (JSONB) | Null if no parent |
| `fields.updated` | updatedAt | Parse ISO-8601 → Instant |

**Sequence Diagram:**

![API Sequence - Scan](diagrams/api-sequence-scan.png)

### 6.2 Internal System: SyncStateManager (MTO-15)

| Attribute | Value |
|-----------|-------|
| Protocol | In-process (Kotlin function calls) |
| Interface | SyncStateManager interface |
| Operations | getState, markRunning, updateProgress, markCompleted, markFailed |

---

## 7. Security Design

### 7.1 Authentication

The ProjectScanner does not expose HTTP endpoints directly. Authentication is handled by:
- JiraRestClient (MTO-16) manages Jira API credentials
- MCP tools (MTO-20) that invoke the scanner are authenticated via MCP protocol

### 7.2 Authorization

| Role | Permissions | Features |
|------|-------------|----------|
| System (internal) | Full access | All scanner operations |
| MCP Tool (jira_project_sync) | Trigger scan, read progress | Via MTO-20 tool handler |

### 7.3 Data Protection

| Data Type | At Rest | In Transit | In Logs |
|-----------|---------|------------|---------|
| Jira API Token | Env var (not in config file) | TLS 1.2+ | Never logged |
| Ticket metadata | PostgreSQL (unencrypted) | N/A (local) | Summary only |
| Sync state | PostgreSQL (unencrypted) | N/A (local) | Full (no PII) |

### 7.4 Input Validation

| Field | Validation | Sanitization |
|-------|-----------|--------------|
| projectKey | Regex `^[A-Z][A-Z0-9_]+$` | None (reject invalid) |
| concurrency | Range 1-20 | Clamp to range |
| forceFullScan | Boolean | Type-safe (Kotlin) |

---

## 8. Performance & Scalability

### 8.1 Caching Strategy

No application-level caching needed. The jira_ticket_cache table IS the cache.

### 8.2 Connection Pooling

| Resource | Min | Max | Timeout | Idle Timeout |
|----------|-----|-----|---------|-------------|
| PostgreSQL (HikariCP) | 2 | 10 | 30000ms | 600000ms |
| Ktor HttpClient | N/A | Configurable | 30000ms | N/A |

### 8.3 Performance Targets

| Operation | Target | Measurement |
|-----------|--------|-------------|
| Full scan throughput | ≥ 200 tickets/min | End-to-end with concurrency=5 |
| Page fetch latency | < 5s per page | Jira API response time |
| Batch upsert (50 rows) | < 50ms | PostgreSQL transaction time |
| Checkpoint update | < 5ms | Single row UPDATE |

### 8.4 Concurrency Model

```
Semaphore(permits = config.concurrency)  // default 5

for each page offset in [startOffset, totalIssues, step=50]:
    semaphore.acquire()
    launch(SupervisorJob) {
        try {
            val response = pageFetcher.fetchPage(jql, offset, 50)
            val metadata = metadataParser.parse(response)
            batchUpserter.upsertBatch(metadata)
            syncStateManager.updateProgress(projectKey, offset + 50, syncedCount.addAndGet(metadata.size))
        } finally {
            semaphore.release()
        }
    }
```

---

## 9. Monitoring & Observability

### 9.1 Logging

| Log Event | Level | Fields | Destination |
|-----------|-------|--------|-------------|
| Scan started | INFO | projectKey, scanType, totalIssues | stdout (Logback) |
| Page fetched | DEBUG | projectKey, offset, count | stdout |
| Batch upserted | DEBUG | projectKey, batchSize | stdout |
| Checkpoint saved | DEBUG | projectKey, offset, syncedCount | stdout |
| Scan completed | INFO | projectKey, duration, totalSynced | stdout |
| Rate limited | WARN | projectKey, retryAfter | stdout |
| Fetch failed (retrying) | WARN | projectKey, offset, attempt, error | stdout |
| Scan failed | ERROR | projectKey, error, lastOffset | stdout |

### 9.2 Metrics

| Metric | Type | Description | Alert Threshold |
|--------|------|-------------|-----------------|
| scanner_scan_duration_seconds | Histogram | Total scan duration | > 600s |
| scanner_pages_fetched_total | Counter | Pages fetched per scan | N/A |
| scanner_tickets_synced_total | Counter | Tickets synced per scan | N/A |
| scanner_errors_total | Counter | Errors encountered | > 10/scan |
| scanner_active_scans | Gauge | Currently running scans | > 3 |

### 9.3 Health Checks

| Check | Method | Expected |
|-------|--------|----------|
| Scanner idle | getProgress() returns null or COMPLETED | No stuck scans |
| DB connectivity | SELECT 1 from jira_sync_state | Success |

---

## 10. Deployment Considerations

### 10.1 Environment Configuration

```yaml
scanner:
  concurrency: 5          # Max concurrent Jira API requests
  pageSize: 50            # Fixed (Jira best practice)
  staleTimeout: 3600      # Seconds before RUNNING state considered stale
  syncBufferMinutes: 1    # Buffer subtracted from last_sync_time for incremental
```

### 10.2 Feature Flags

| Flag | Default | Description |
|------|---------|-------------|
| scanner.enabled | true | Enable/disable scanner functionality |
| scanner.autoResume | true | Auto-resume interrupted scans on startup |

### 10.3 Rollback Strategy

- Scanner is stateless (state in DB). Rolling back code doesn't affect cached data.
- If schema changes are needed, use Flyway migrations with rollback scripts.
- Feature flag `scanner.enabled = false` disables scanner without code rollback.

---

## 11. Implementation Checklist

| # | Task | Package/File | Priority | Estimated |
|---|------|-------------|----------|-----------|
| 1 | Create ScannerConfig data class | scanner/config/ScannerConfig.kt | High | 0.5h |
| 2 | Create domain models (JiraTicketMetadata, ScanOptions, ScanResult, etc.) | scanner/model/*.kt | High | 1h |
| 3 | Create custom exceptions | scanner/model/ScanExceptions.kt | High | 0.5h |
| 4 | Implement JqlBuilder | scanner/JqlBuilder.kt | High | 0.5h |
| 5 | Implement MetadataParser | scanner/MetadataParser.kt | High | 1h |
| 6 | Implement BatchUpserter (Exposed ORM) | scanner/BatchUpserter.kt | High | 1.5h |
| 7 | Implement PageFetcher (wraps JiraRestClient) | scanner/PageFetcher.kt | High | 1h |
| 8 | Implement ProjectScannerImpl (main orchestration) | scanner/ProjectScannerImpl.kt | High | 3h |
| 9 | Create ScannerModule (Koin DI) | scanner/di/ScannerModule.kt | High | 0.5h |
| 10 | Register ScannerModule in AppModule | di/AppModule.kt | High | 0.25h |
| 11 | Add scanner config section to application.yml | resources/application.yml | Medium | 0.25h |
| 12 | Unit tests for JqlBuilder | test/.../scanner/JqlBuilderTest.kt | High | 0.5h |
| 13 | Unit tests for MetadataParser | test/.../scanner/MetadataParserTest.kt | High | 1h |
| 14 | Unit tests for BatchUpserter | test/.../scanner/BatchUpserterTest.kt | High | 1h |
| 15 | Unit tests for ProjectScannerImpl | test/.../scanner/ProjectScannerImplTest.kt | High | 2h |
| 16 | Integration test with Testcontainers (PostgreSQL) | test/.../scanner/it/ScannerIntegrationTest.kt | Medium | 2h |

**Total Estimated:** ~16.5 hours

---

## 12. Appendix

### Glossary

| Term | Definition |
|------|------------|
| Checkpoint | Saved offset + synced count for resumability |
| Upsert | INSERT ON CONFLICT DO UPDATE |
| Semaphore | Concurrency-limiting coroutine primitive |
| SupervisorJob | Coroutine job where child failure doesn't cancel siblings |
| Stale scan | RUNNING state older than staleTimeout (default 1 hour) |

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Architecture Overview | [architecture.png](diagrams/architecture.png) | [architecture.drawio](diagrams/architecture.drawio) |
| 2 | Component Diagram | [component.png](diagrams/component.png) | [component.drawio](diagrams/component.drawio) |
| 3 | Deployment Diagram | [deployment.png](diagrams/deployment.png) | [deployment.drawio](diagrams/deployment.drawio) |
| 4 | Database Schema | [db-schema.png](diagrams/db-schema.png) | [db-schema.drawio](diagrams/db-schema.drawio) |
| 5 | Class Diagram | [class-diagram.png](diagrams/class-diagram.png) | [class-diagram.drawio](diagrams/class-diagram.drawio) |
| 6 | API Sequence - Scan | [api-sequence-scan.png](diagrams/api-sequence-scan.png) | [api-sequence-scan.drawio](diagrams/api-sequence-scan.drawio) |
