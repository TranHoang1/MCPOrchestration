# Technical Design Document (TDD)

## KB-Server — MTO-117: Migrate Sync Tools from Orchestrator-Server

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-117 |
| Title | Migrate Sync Tools — Move jira_project_sync, jira_sync_status, jira_ticket_graph from Orchestrator |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2025-07-19 |
| Status | Draft |
| Related BRD | documents/MTO-117/BRD.md |
| Related FSD | documents/MTO-117/FSD.md |
| Parent Epic | MTO-115 — KB-Server Consolidation |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | SA Agent – Solution Architect | Create document |
| Peer Reviewer | TA Agent – Technical Architect | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-19 | SA Agent | Initial TDD — architecture, migration strategy, class design, DI wiring |
| 1.1 | 2026-05-15 | SA Agent | Added §4.5: Content Hash Skip — implementation design for processTicket optimization |

---

## 1. Introduction

### 1.1 Purpose

This TDD specifies the technical implementation plan for migrating all sync-related MCP tools and supporting modules from `orchestrator-server` to `kb-server`. The migration consolidates sync tool ownership under kb-server — the natural home for knowledge base synchronization logic — eliminating tool duplication and establishing a single source of truth.

### 1.2 Scope

- Detailed class design for 4 unified tool handlers on kb-server
- Merge logic: how existing handlers from both servers combine into unified implementations
- DI module changes (additions to kb-server, removals from orchestrator-server)
- Crawler module migration (package relocation + dependency rewiring)
- Database access patterns and connection pool strategy
- Backward compatibility via config update approach
- Phased migration strategy with verification gates

### 1.3 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.3.20 |
| Platform | JVM | 21 |
| Framework | Ktor (Netty) | 3.4.0 |
| DI | Koin | 4.1.1 |
| MCP SDK | io.modelcontextprotocol:kotlin-sdk-server | 0.12.0 |
| Serialization | kotlinx.serialization-json | 1.8.1 |
| Database | PostgreSQL + pgvector | — |
| Connection Pool | HikariCP | — |
| Testing | JUnit 5 + MockK + Testcontainers | — |

### 1.4 Design Principles

- **Single Source of Truth**: After migration, sync logic exists in exactly one place (kb-server)
- **Interface/Impl Pattern**: All services use interface + implementation separation
- **KbToolHandler Contract**: All MCP tools implement the `KbToolHandler` interface for auto-registration
- **Queue-Based Dispatch**: Long-running operations dispatched via dual-priority queue
- **No Schema Changes**: Existing database tables remain as-is (BRD §1.2)

### 1.5 Constraints

- Maximum 200 lines per file, 20 lines per function (project code standards)
- No new external dependencies — use existing libraries only
- Tool names must remain unchanged for backward compatibility
- Database schema must not change (existing tables reused)
- `sync-pipeline` shared module must not be modified

### 1.6 References

| Document | Location |
|----------|----------|
| BRD | documents/MTO-117/BRD.md |
| FSD | documents/MTO-117/FSD.md |
| Project Structure | .analysis/code-intelligence/project-structure.md |
| sync-pipeline module | sync-pipeline/src/main/kotlin/com/orchestrator/mcp/sync/pipeline/ |

---

## 2. Architecture Overview

### 2.1 Before Migration (Current State)

Sync responsibilities are split across two servers, causing tool duplication:

```
┌─────────────────────────────────────────────────────────────────┐
│                        AI Agents                                 │
└──────────┬──────────────────────────────────┬───────────────────┘
           │ MCP calls                        │ MCP calls
           ▼                                  ▼
┌──────────────────────────┐    ┌──────────────────────────────┐
│   orchestrator-server    │    │         kb-server             │
│                          │    │                               │
│  • jira_project_sync     │    │  • kb_sync_trigger            │
│  • jira_sync_status      │    │  • kb_sync_status             │
│  • jira_ticket_graph     │    │  • kb_graph                   │
│                          │    │                               │
│  ┌────────────────────┐  │    │  ┌─────────────────────────┐  │
│  │ synctools/          │  │    │  │ queue/                   │  │
│  │  SyncToolHandler    │  │    │  │  SyncTaskHandler         │  │
│  │  StatusToolHandler  │  │    │  │  QueueWorker             │  │
│  │  GraphToolHandler   │  │    │  └─────────────────────────┘  │
│  └────────────────────┘  │    └──────────────────────────────┘
│  ┌────────────────────┐  │
│  │ crawler/            │  │
│  │  TicketCrawlerImpl  │  │
│  │  KBIngestorImpl     │  │
│  │  ContentFetcher     │  │
│  │  GraphBuilder       │  │
│  │  AttachmentQueuer   │  │
│  └────────────────────┘  │
└──────────────────────────┘
           │
           ▼
┌──────────────────────────┐
│  sync-pipeline (shared)  │
│  SyncOrchestrator        │
│  SyncStateTracker        │
└──────────────────────────┘
```

**Problems:**
1. `jira_project_sync` (orchestrator) vs `kb_sync_trigger` (kb-server) — duplicate functionality
2. `jira_sync_status` (orchestrator) vs `kb_sync_status` (kb-server) — partial data each
3. Crawler lives in orchestrator but ingests into KB — cross-server concern
4. Agents must know which server to call for sync operations

### 2.2 After Migration (Target State)

All sync tools consolidated under kb-server:

```
┌─────────────────────────────────────────────────────────────────┐
│                        AI Agents                                 │
└──────────────────────────────────┬──────────────────────────────┘
                                   │ MCP calls (all sync tools)
                                   ▼
┌──────────────────────────┐    ┌──────────────────────────────────┐
│   orchestrator-server    │    │           kb-server               │
│                          │    │                                   │
│  • find_tools            │    │  • jira_project_sync (unified)    │
│  • execute_dynamic_tool  │    │  • jira_sync_status (unified)     │
│  • agent_log             │    │  • jira_ticket_graph (unified)    │
│  • embed_images          │    │  • jira_ticket_sync (new)         │
│  • stream_write_file     │    │  • kb_ingest, kb_search           │
│                          │    │                                   │
│  (sync tools REMOVED)    │    │  ┌─────────────────────────────┐  │
│                          │    │  │ synctools/ (NEW)             │  │
│                          │    │  │  JiraProjectSyncHandler      │  │
│                          │    │  │  JiraSyncStatusHandler       │  │
│                          │    │  │  JiraTicketGraphHandler      │  │
│                          │    │  │  JiraTicketSyncHandler       │  │
│                          │    │  └─────────────────────────────┘  │
│                          │    │  ┌─────────────────────────────┐  │
│                          │    │  │ crawler/ (MIGRATED)          │  │
│                          │    │  │  TicketCrawlerImpl           │  │
│                          │    │  │  KBIngestorImpl              │  │
│                          │    │  │  ContentFetcher              │  │
│                          │    │  │  GraphBuilder                │  │
│                          │    │  │  AttachmentQueuer            │  │
│                          │    │  └─────────────────────────────┘  │
│                          │    │  ┌─────────────────────────────┐  │
│                          │    │  │ dashboard/ (MIGRATED)        │  │
│                          │    │  │  SyncDashboardRoutes         │  │
│                          │    │  │  SyncEventBus                │  │
│                          │    │  └─────────────────────────────┘  │
└──────────────────────────┘    └──────────────────────────────────┘
                                   │
                                   ▼
                        ┌──────────────────────────┐
                        │  sync-pipeline (shared)  │
                        │  SyncOrchestrator        │
                        │  SyncStateTracker        │
                        └──────────────────────────┘
```

### 2.3 Communication Patterns

| From | To | Protocol | Pattern | Description |
|------|----|----------|---------|-------------|
| AI Agent | kb-server | MCP (HTTP Streamable) | Sync | Tool invocation via MCP protocol |
| JiraProjectSyncHandler | QueueService | In-process | Async | Enqueue sync task for background processing |
| QueueWorker | SyncOrchestrator | In-process | Async | Execute sync pipeline |
| SyncOrchestrator | Jira API | REST/HTTPS | Sync | Fetch ticket data |
| KBIngestorImpl | VectorDbClient | In-process | Sync | Upsert embeddings |
| SyncDashboardRoutes | SyncEventBus | SharedFlow | Async | SSE event delivery |

---

## 3. Migration Strategy

### 3.1 Phased Approach

The migration follows a 4-phase approach to minimize risk:

```
Phase 1: PREPARE        Phase 2: TRANSITION      Phase 3: CLEANUP        Phase 4: VERIFY
─────────────────────   ─────────────────────    ─────────────────────   ─────────────────
• Create new handlers   • Deploy kb-server       • Remove old handlers   • Audit log analysis
• Copy crawler module   • Smoke test all tools   • Remove crawler from   • Performance test
• Wire DI modules       • Update agent configs     orchestrator          • Dashboard check
• Unit tests            • Agent workflow test     • Delete source files   • No duplicate tools
                                                 • Full test suite
```

### 3.2 Phase 1 — Prepare kb-server (No Breaking Changes)

| Step | Action | Files Affected | Verification |
|------|--------|----------------|--------------|
| 1.1 | Create `synctools/` package in kb-server | New package | Compile |
| 1.2 | Migrate sync repositories (interfaces + impls) | New `sync/` package | Unit tests |
| 1.3 | Migrate crawler module | New `crawler/` package | Unit tests |
| 1.4 | Create `JiraProjectSyncHandler` | New file | Unit tests |
| 1.5 | Create `JiraSyncStatusHandler` | New file | Unit tests |
| 1.6 | Create `JiraTicketGraphHandler` | New file | Unit tests |
| 1.7 | Create `JiraTicketSyncHandler` | New file | Unit tests |
| 1.8 | Create DI modules (`kbSyncToolsModule`, `kbCrawlerModule`) | New files | Integration test |
| 1.9 | Add modules to `kbAppModule()` | KbDiModule.kt | Server starts |
| 1.10 | Migrate dashboard routes + static files | New `dashboard/` package | HTTP test |

### 3.3 Phase 2 — Transition Period (Tools on Both Servers)

| Step | Action | Risk | Verification |
|------|--------|------|--------------|
| 2.1 | Deploy kb-server with new unified tools | Low | Smoke test all 4 tools |
| 2.2 | Verify `jira_project_sync` end-to-end | Medium | Full sync test |
| 2.3 | Verify `jira_sync_status` returns combined data | Low | Status check |
| 2.4 | Verify `jira_ticket_graph` returns correct graph | Low | Graph query test |
| 2.5 | Verify dashboard at `/sync/dashboard` | Low | Manual UI test |
| 2.6 | Update agent MCP configs to route sync tools to kb-server | Medium | Agent workflow test |

**Safety during transition:**
- `SyncStateManager` uses the same database — prevents duplicate sync execution
- Queue deduplication: `QueueService.enqueue()` checks for existing pending task
- Audit logging on both servers identifies which path agents are using

### 3.4 Phase 3 — Cleanup (Remove from Orchestrator)

| Step | Action | Verification |
|------|--------|--------------|
| 3.1 | Remove `KbSyncTriggerHandler`, `KbSyncStatusHandler`, `KbGraphHandler` from kb-server `kbHandlersModule()` | Compile + test |
| 3.2 | Remove `SyncToolHandler`, `StatusToolHandler`, `GraphToolHandler` from orchestrator-server | Compile |
| 3.3 | Remove `crawlerModule`, `dashboardModule`, `graphModule` includes from orchestrator AppModule | Compile |
| 3.4 | Remove sync repository bindings from orchestrator AppModule | Compile |
| 3.5 | Delete orchestrator-server source files (see §10 file list) | Compile |
| 3.6 | Run full test suite on both servers | All tests pass |

### 3.5 Phase 4 — Verification

| Step | Action | Pass Criteria |
|------|--------|---------------|
| 4.1 | Confirm no agent calls sync tools on orchestrator-server | Audit log shows zero calls |
| 4.2 | Confirm dashboard accessible only from kb-server | HTTP 404 on orchestrator |
| 4.3 | Performance test: sync 1000 tickets | < 5 min completion |
| 4.4 | Confirm no tool name exists on both servers | Unique tool names |

---

## 4. Detailed Class Design

### 4.1 JiraProjectSyncHandler

| Property | Value |
|----------|-------|
| Class | `JiraProjectSyncHandler` |
| Package | `com.orchestrator.mcp.kb.synctools` |
| File | `kb-server/src/.../kb/synctools/JiraProjectSyncHandler.kt` |
| Implements | `KbToolHandler` |
| Merges | `SyncToolHandler` (orchestrator) + `KbSyncTriggerHandler` (kb-server) |

**Constructor Dependencies:**

| Dependency | Type | Source |
|------------|------|--------|
| `queueService` | `QueueService` | Existing in kb-server (kbQueueModule) |
| `syncStateManager` | `SyncStateManager` | Migrated from orchestrator-server |
| `auditService` | `AuditService` | Existing in kb-server (kbAuditModule) |

**Public Methods:**

```kotlin
class JiraProjectSyncHandler(
    private val queueService: QueueService,
    private val syncStateManager: SyncStateManager,
    private val auditService: AuditService
) : KbToolHandler {

    override val toolName: String = "jira_project_sync"
    override val description: String = "Trigger a Jira project sync..."
    override val inputSchema: ToolSchema = ...

    override suspend fun handle(arguments: JsonObject?): CallToolResult
}
```

**Merge Logic:**

| Aspect | From SyncToolHandler (orchestrator) | From KbSyncTriggerHandler (kb-server) | Unified Behavior |
|--------|-------------------------------------|---------------------------------------|------------------|
| Dispatch | `CoroutineScope.launch` (fire-and-forget) | `QueueService.enqueue()` (queue-based) | **Queue-based** — more reliable, crash-recoverable |
| Duplicate detection | `IllegalStateException` from SyncOrchestrator | None | **SyncStateManager.getStatus()** check before enqueue |
| Priority | Not supported | `Priority.fromString()` | **Supported** — high/normal |
| Audit | Not logged | `AuditService.log()` | **Logged** via AuditService |
| Response | `{status:"started", estimatedIssues}` | `{status:"queued", task_id}` | **`{status:"queued", taskId, projectKey, priority}`** |
| Field naming | `projectKey` (camelCase) | `project_key` (snake_case) | **`projectKey`** (camelCase — BRD canonical) |

**Validation Logic:**
1. `projectKey` must match regex `^[A-Z]{1,10}$`
2. `priority` normalized to lowercase, must be "high" or "normal" (default: "normal")
3. `fullSync` defaults to `false`
4. Check `SyncStateManager` — if status is `SYNCING`, return error

---

### 4.2 JiraSyncStatusHandler

| Property | Value |
|----------|-------|
| Class | `JiraSyncStatusHandler` |
| Package | `com.orchestrator.mcp.kb.synctools` |
| File | `kb-server/src/.../kb/synctools/JiraSyncStatusHandler.kt` |
| Implements | `KbToolHandler` |
| Merges | `StatusToolHandler` (orchestrator) + `KbSyncStatusHandler` (kb-server) |

**Constructor Dependencies:**

| Dependency | Type | Source |
|------------|------|--------|
| `syncStateManager` | `SyncStateManager` | Migrated from orchestrator-server |
| `queueService` | `QueueService` | Existing in kb-server (kbQueueModule) |

**Public Methods:**

```kotlin
class JiraSyncStatusHandler(
    private val syncStateManager: SyncStateManager,
    private val queueService: QueueService
) : KbToolHandler {

    override val toolName: String = "jira_sync_status"
    override val description: String = "Get sync status and queue metrics..."
    override val inputSchema: ToolSchema = ...

    override suspend fun handle(arguments: JsonObject?): CallToolResult
}
```

**Merge Logic:**

| Aspect | From StatusToolHandler (orchestrator) | From KbSyncStatusHandler (kb-server) | Unified Behavior |
|--------|---------------------------------------|--------------------------------------|------------------|
| Project progress | `syncStateManager.getOrCreate()` → status, synced, total, lastSync | Not available | **Included** when projectKey provided |
| Queue metrics | Not available | `queueService.getMetrics()` → HPQ/NPQ depth, processing, completed/failed | **Always included** |
| projectKey | Required | Not used | **Optional** — if provided: progress + queue; if omitted: queue only |
| Progress % | `(synced * 100) / total` | Not computed | **Computed** in unified handler |

---

### 4.3 JiraTicketGraphHandler

| Property | Value |
|----------|-------|
| Class | `JiraTicketGraphHandler` |
| Package | `com.orchestrator.mcp.kb.synctools` |
| File | `kb-server/src/.../kb/synctools/JiraTicketGraphHandler.kt` |
| Implements | `KbToolHandler` |
| Migrated from | `GraphToolHandler` (orchestrator-server) — direct port |

**Constructor Dependencies:**

| Dependency | Type | Source |
|------------|------|--------|
| `graphRepository` | `TicketGraphRepository` | Migrated from orchestrator-server |
| `ticketCacheRepository` | `TicketCacheRepository` | Migrated from orchestrator-server |

**Public Methods:**

```kotlin
class JiraTicketGraphHandler(
    private val graphRepository: TicketGraphRepository,
    private val ticketCacheRepository: TicketCacheRepository
) : KbToolHandler {

    override val toolName: String = "jira_ticket_graph"
    override val description: String = "Query ticket relationship graph..."
    override val inputSchema: ToolSchema = ...

    override suspend fun handle(arguments: JsonObject?): CallToolResult
}
```

**Migration Notes:**
- This is a **direct port** of `GraphToolHandler` — no merge required
- BFS traversal logic preserved exactly (same algorithm)
- Node limit of 1000 preserved
- Replaces both `GraphToolHandler` (orchestrator) and `KbGraphHandler` (kb-server)
- The existing `GraphService` in kb-server's `graph/` package continues to serve HTTP routes

---

### 4.4 JiraTicketSyncHandler (New Tool)

| Property | Value |
|----------|-------|
| Class | `JiraTicketSyncHandler` |
| Package | `com.orchestrator.mcp.kb.synctools` |
| File | `kb-server/src/.../kb/synctools/JiraTicketSyncHandler.kt` |
| Implements | `KbToolHandler` |
| New | Yes — uses existing `TicketCrawler.crawlSingle()` |

**Constructor Dependencies:**

| Dependency | Type | Source |
|------------|------|--------|
| `ticketCrawler` | `TicketCrawler` | Migrated from orchestrator-server |

**Public Methods:**

```kotlin
class JiraTicketSyncHandler(
    private val ticketCrawler: TicketCrawler
) : KbToolHandler {

    override val toolName: String = "jira_ticket_sync"
    override val description: String = "Sync a single Jira ticket on-demand..."
    override val inputSchema: ToolSchema = ...

    override suspend fun handle(arguments: JsonObject?): CallToolResult
}
```

**Design Notes:**
- Synchronous execution — does NOT go through queue
- Uses `TicketCrawler.crawlSingle(issueKey)` for immediate processing
- When `includeLinked=true`, iterates linked tickets and calls `crawlSingle()` for each
- Priority: COULD HAVE (nice-to-have per BRD)

---

### 4.5 Content Hash Skip Optimization (SyncOrchestratorImpl Modification)

| Property | Value |
|----------|-------|
| Class | `SyncOrchestratorImpl` |
| Module | `sync-pipeline` |
| File | `sync-pipeline/src/.../pipeline/SyncOrchestratorImpl.kt` |
| Change Type | MODIFY — add hash check in `processTicket()` |

**Modified Method:**

```kotlin
private suspend fun processTicket(
    ticket: CrawledTicket,
    options: SyncOptions,
    stats: SyncStats
) {
    // NEW: Skip unchanged tickets (unless fullSync)
    if (!options.fullSync && isUnchanged(ticket)) {
        stats.skipped++
        return
    }
    val entries = dimensionProcessor.process(ticket, options.dimensions)
    batchWriter.writeBatch(entries)
    queueVectorEntries(entries)
    stats.processed++
    stats.addEntries(entries)
}

private suspend fun isUnchanged(ticket: CrawledTicket): Boolean {
    return try {
        val existingHash = batchWriter.getContentHash(ticket.key, "ticket_metadata")
        existingHash != null && existingHash == ticket.contentHash
    } catch (e: Exception) {
        logger.warn("Hash check failed for {}, processing anyway: {}", ticket.key, e.message)
        false  // Fail-safe: process if check fails
    }
}
```

**New Method on IndexWriter:**

```kotlin
// IndexWriter.kt — add method
suspend fun getContentHash(ticketKey: String, dimensionId: String): String?
```

**Implementation in PostgresIndexWriter:**

```kotlin
override suspend fun getContentHash(ticketKey: String, dimensionId: String): String? {
    return withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(HASH_SQL).use { stmt ->
                stmt.setString(1, ticketKey)
                stmt.setString(2, dimensionId)
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getString("content_hash") else null
            }
        }
    }
}

companion object {
    private const val HASH_SQL = """
        SELECT content_hash FROM sync.index_entries
        WHERE ticket_key = ? AND dimension_id = ?
        LIMIT 1
    """
}
```

**SyncOptions Consideration:**

`fullSync` flag already exists in `SyncOptions`. When `true`, hash check is bypassed — all tickets are re-processed regardless of content change.

**Test Scenarios:**

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Ticket unchanged (same hash) | Skipped, stats.skipped++ |
| 2 | Ticket changed (different hash) | Processed normally |
| 3 | New ticket (no existing hash) | Processed normally |
| 4 | fullSync=true, unchanged ticket | Processed (hash check bypassed) |
| 5 | getContentHash throws exception | Processed (fail-safe) |

---

## 5. DI Module Changes

### 5.1 New Modules in kb-server

#### 5.1.1 `kbSyncToolsModule()` — Unified Tool Handlers + Repositories

```kotlin
// File: kb-server/src/.../kb/synctools/di/SyncToolsModule.kt
package com.orchestrator.mcp.kb.synctools.di

import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.sync.*
import com.orchestrator.mcp.kb.synctools.*
import org.koin.dsl.bind
import org.koin.dsl.module

fun kbSyncToolsModule() = module {
    // Sync state management (migrated from orchestrator-server)
    single<SyncStateManager> { SyncStateManagerImpl(get()) }
    single<TicketCacheRepository> { TicketCacheRepositoryImpl(get()) }
    single<TicketGraphRepository> { TicketGraphRepositoryImpl(get()) }
    single<AttachmentQueueRepository> { AttachmentQueueRepositoryImpl(get()) }

    // Unified tool handlers
    single { JiraProjectSyncHandler(get(), get(), get()) } bind KbToolHandler::class
    single { JiraSyncStatusHandler(get(), get()) } bind KbToolHandler::class
    single { JiraTicketGraphHandler(get(), get()) } bind KbToolHandler::class
    single { JiraTicketSyncHandler(get()) } bind KbToolHandler::class
}
```

#### 5.1.2 `kbCrawlerModule()` — Migrated Crawler Pipeline

```kotlin
// File: kb-server/src/.../kb/crawler/di/CrawlerModule.kt
package com.orchestrator.mcp.kb.crawler.di

import com.orchestrator.mcp.kb.crawler.*
import org.koin.dsl.module

fun kbCrawlerModule() = module {
    single<ContentFetcher> { ContentFetcherImpl(get()) }
    single<ContentHasher> { ContentHasherImpl() }
    single<GraphBuilder> { GraphBuilderImpl(get()) }
    single<KBIngestor> { KBIngestorImpl(get(), get(), "kb_tickets") }
    single<AttachmentQueuer> { AttachmentQueuerImpl(get()) }
    single<TicketCrawler> {
        TicketCrawlerImpl(get(), get(), get(), get(), get(), get(), get())
    }
}
```

#### 5.1.3 `kbDashboardModule()` — Migrated Dashboard

```kotlin
// File: kb-server/src/.../kb/dashboard/di/DashboardModule.kt
package com.orchestrator.mcp.kb.dashboard.di

import com.orchestrator.mcp.kb.dashboard.*
import org.koin.dsl.module

fun kbDashboardModule() = module {
    single { SyncEventBus() }
    single { SyncDashboardRoutes(get(), get(), get()) }
}
```

### 5.2 Modifications to `kbAppModule()`

```kotlin
// File: kb-server/src/.../kb/di/KbDiModule.kt
// MODIFIED: Add 3 new modules to composition

fun kbAppModule(config: KbConfig): List<Module> = listOf(
    kbConfigModule(config),
    kbInfraModule(),
    kbSyncPipelineModule(config),
    kbStoreModule(),
    kbAuditModule(),
    kbMaskingModule(),
    kbQueueModule(),
    kbGraphModule,
    kbNetworkModule,
    kbSyncToolsModule(),      // ← NEW
    kbCrawlerModule(),        // ← NEW
    kbDashboardModule(),      // ← NEW
    kbHandlersModule(),       // ← MODIFIED (3 handlers removed)
    kbProtocolModule(),
    syncPipelineModule
)
```

### 5.3 Modifications to `kbHandlersModule()`

**Remove these bindings** (merged into unified tools in `kbSyncToolsModule`):

```kotlin
// REMOVE from kbHandlersModule():
single { KbSyncTriggerHandler(get(), get()) } bind KbToolHandler::class  // → JiraProjectSyncHandler
single { KbSyncStatusHandler(get(), get()) } bind KbToolHandler::class   // → JiraSyncStatusHandler
single { KbGraphHandler(get()) } bind KbToolHandler::class               // → JiraTicketGraphHandler
```

### 5.4 Removals from orchestrator-server `AppModule.kt`

**Remove these bindings:**

```kotlin
// REMOVE tool handlers:
single { SyncToolHandler(get()) }
single { StatusToolHandler(get()) }
// Note: GraphToolHandler is registered via SyncToolRegistrar, not directly in AppModule

// REMOVE module includes:
includes(crawlerModule)      // Crawler moved to kb-server
includes(dashboardModule)    // Dashboard moved to kb-server
includes(graphModule)        // Graph moved to kb-server

// REMOVE sync repositories (moved to kb-server):
single<SyncStateManager> { SyncStateManagerImpl(get()) }
single<TicketCacheRepository> { TicketCacheRepositoryImpl(get()) }
single<TicketGraphRepository> { TicketGraphRepositoryImpl(get()) }
single<AttachmentQueueRepository> { AttachmentQueueRepositoryImpl(get()) }
```

**Keep in orchestrator-server** (still needed for scanner module):

| Binding | Reason |
|---------|--------|
| `SyncJiraClient` / `SyncJiraClientAdapter` | Used by scanner module |
| `SyncPipelineConfig` | Used by scanner module |
| `syncPipelineModule` | Shared library, still needed |
| `scannerModule` | Project scanner remains in orchestrator |

---

## 6. Database Access

### 6.1 Tables Accessed After Migration

kb-server already connects to the same PostgreSQL instance. No new database connections needed — only additional table access from kb-server's existing connection pool.

| Table | Current Access | After Migration | Operations |
|-------|---------------|-----------------|------------|
| `sync_state` | orchestrator-server | **kb-server** | READ/WRITE — sync status tracking |
| `ticket_cache` | orchestrator-server | **kb-server** | READ/WRITE — cached ticket data |
| `ticket_graph` | orchestrator-server | **kb-server** | READ/WRITE — ticket relationships |
| `attachment_queue` | orchestrator-server | **kb-server** | READ/WRITE — attachment processing |
| `kb_entries` | kb-server | kb-server (unchanged) | READ/WRITE — KB vector entries |
| `kb_segments` | kb-server | kb-server (unchanged) | READ/WRITE — content segments |
| `kb_queue` | kb-server | kb-server (unchanged) | READ/WRITE — async task queue |
| `kb_audit` | kb-server | kb-server (unchanged) | WRITE — audit trail |

### 6.2 Connection Pool Strategy

**Shared Database — Single PostgreSQL Instance:**

Both servers connect to the same PostgreSQL instance. The sync tables are already in the same database as KB tables. No schema changes required (BRD §1.2).

**Connection Pool Adjustment:**

| Parameter | Current (kb-server) | After Migration | Rationale |
|-----------|--------------------|-----------------|-----------| 
| `maximumPoolSize` | 10 | **15** | Additional queries from sync tools + crawler |
| `minimumIdle` | 5 | **7** | Maintain warm connections for sync operations |
| `connectionTimeout` | 30000ms | 30000ms (unchanged) | — |
| `idleTimeout` | 600000ms | 600000ms (unchanged) | — |

**Configuration change in `kb-config.yaml`:**

```yaml
kb:
  database:
    maximumPoolSize: 15    # was 10
    minimumIdle: 7         # was 5
```

### 6.3 Repository Access Patterns

| Repository | Table | Key Queries | Estimated Load |
|------------|-------|-------------|----------------|
| `SyncStateManager` | `sync_state` | `getOrCreate(projectKey)`, `updateStatus()`, `updateProgress()` | Low (per-sync) |
| `TicketCacheRepository` | `ticket_cache` | `findByProject(projectKey)`, `findByHash()`, `markIngested()` | Medium (per-ticket) |
| `TicketGraphRepository` | `ticket_graph` | `findAllForProject()`, `findOutgoing()`, `findIncoming()`, `upsertEdge()` | Medium (BFS traversal) |
| `AttachmentQueueRepository` | `attachment_queue` | `enqueue()`, `dequeue(batchSize)` | Low (batch) |

### 6.4 No Schema Migration Required

Per BRD §1.2: "Database schema changes (existing tables remain as-is)" is explicitly out of scope. All tables already exist with correct schemas. The migration is purely a code-level change — which server accesses which tables.

---

## 7. Backward Compatibility

### 7.1 Strategy: Config Update (Option B)

The FSD recommends Config Update over Tool Forwarding for these reasons:

| Criteria | Tool Forwarding (rejected) | Config Update (chosen) |
|----------|---------------------------|------------------------|
| Complexity | High — proxy handlers, HTTP forwarding | Low — update JSON config |
| Latency | Added network hop | Direct call |
| Maintenance | Must maintain stubs | One-time change |
| Duplication risk | Possible | Zero |
| Rollback | Complex | Simple — revert config |

### 7.2 Config Update Implementation

**Step 1: Update `mcp-servers.json` (agent configurations)**

```json
{
  "orchestrator": {
    "command": "java",
    "args": ["-jar", "mcp-orchestrator-all.jar"],
    "tools": ["find_tools", "execute_dynamic_tool", "agent_log",
              "embed_images", "stream_write_file", "export_drawio"]
  },
  "kb-server": {
    "command": "java",
    "args": ["-jar", "kb-server-all.jar"],
    "tools": ["kb_ingest", "kb_search", "kb_read", "kb_delete",
              "kb_link", "kb_feedback", "kb_audit",
              "jira_project_sync", "jira_sync_status",
              "jira_ticket_graph", "jira_ticket_sync"]
  }
}
```

**Step 2: Update `.kiro/settings/mcp.json` (IDE-level config)**

Same pattern — move sync tool names from orchestrator server entry to kb-server entry.

### 7.3 Transition Period Safety Mechanisms

| Mechanism | How It Works |
|-----------|-------------|
| Database-level dedup | `SyncStateManager` checks `sync_state` table — same DB for both servers |
| Queue dedup | `QueueService.enqueue()` checks for existing pending task with same project key |
| Audit trail | Both servers log tool invocations — identifies which path agents use |
| Tool name uniqueness | After Phase 3, tool names exist on exactly one server |

### 7.4 Rollback Plan

If issues are found after config update:
1. Revert `mcp-servers.json` to previous version (sync tools back on orchestrator)
2. Restart agent processes to pick up config change
3. kb-server tools remain deployed but unused (no harm)
4. Investigate and fix before re-attempting migration

---

## 8. Testing Strategy

### 8.1 Unit Tests (Per Handler)

| Test Class | Key Test Cases | Priority |
|------------|---------------|----------|
| `JiraProjectSyncHandlerTest` | Valid sync trigger, missing projectKey, invalid format (`^[A-Z]{1,10}$`), duplicate detection (SYNCING state), priority handling (high/normal), fullSync flag | High |
| `JiraSyncStatusHandlerTest` | With projectKey (progress + queue), without projectKey (queue only), unknown project returns "idle", progress % calculation | High |
| `JiraTicketGraphHandlerTest` | Full project graph, subgraph BFS traversal, depth limits (1-5), node limit 1000, empty graph (no data), invalid issueKey format | High |
| `JiraTicketSyncHandlerTest` | Single ticket sync, with linked tickets, without linked, invalid issueKey, crawl failure handling | Medium |

**Mocking Strategy:**
- Use MockK for all dependencies
- Mock `QueueService`, `SyncStateManager`, `AuditService`, `TicketCrawler`
- Mock `TicketGraphRepository`, `TicketCacheRepository` for graph handler

### 8.2 Integration Tests

| Test Class | Description | Dependencies |
|------------|-------------|--------------|
| `SyncToolsIntegrationTest` | Start kb-server, call all 4 tools via MCP protocol | Testcontainers (PostgreSQL) |
| `CrawlerIntegrationTest` | Trigger sync → verify tickets crawled → KB entries created | Testcontainers + Mock Jira |
| `DashboardIntegrationTest` | Verify `/sync/*` routes respond correctly | Ktor Test Host |
| `SSEIntegrationTest` | Connect to `/sync/live`, trigger sync, verify events | Ktor Test Host |
| `GraphQueryIntegrationTest` | Seed graph data, query via tool, verify BFS | Testcontainers |

### 8.3 Migration Verification Tests

These tests specifically verify the migration was successful:

| Test | Description | Pass Criteria |
|------|-------------|---------------|
| `OrchestratorNoSyncToolsTest` | After cleanup, orchestrator does NOT register sync tools | `find_tools("jira_project_sync")` returns empty |
| `KbServerSyncToolsTest` | kb-server registers all 4 sync tools | Tool listing includes all 4 |
| `NoDuplicateToolsTest` | No tool name exists on both servers | Unique names across servers |
| `BackwardCompatConfigTest` | With updated config, agent calls sync tools on kb-server | Successful invocation |
| `DashboardMigrationTest` | Dashboard loads from kb-server, NOT orchestrator | HTTP 200 from kb, 404 from orchestrator |

### 8.4 Performance Tests

| Test | Target | Measurement Method |
|------|--------|-------------------|
| Sync tool response time | < 2 seconds | Time from MCP call to response (queue dispatch) |
| Graph query (1000 tickets) | < 5 seconds | Time for full project graph query |
| Concurrent syncs (3 projects) | No interference | All 3 complete without errors |
| SSE event latency | < 1 second | Time from state change to SSE delivery |

### 8.5 Test Data Strategy

- **Testcontainers**: PostgreSQL container for integration tests
- **Seed data**: Pre-populate `ticket_cache` and `ticket_graph` tables
- **Mock Jira**: Mock `SyncJiraClient` for unit tests (no real Jira calls)
- **MockK**: Mock all DI dependencies in handler unit tests

---

## 9. Package Structure After Migration

### 9.1 kb-server (New Packages)

```
com.orchestrator.mcp.kb/
├── KbApplication.kt
├── KbMain.kt
├── config/
├── di/KbDiModule.kt                    ← MODIFIED (add new modules)
├── protocol/
│   ├── KbToolHandler.kt
│   ├── KbMcpServerFactory.kt
│   └── handlers/                       ← MODIFIED (3 handlers removed)
│       ├── KbSearchHandler.kt
│       ├── KbIngestHandler.kt
│       ├── KbReadHandler.kt
│       ├── KbDeleteHandler.kt
│       ├── KbLinkHandler.kt
│       ├── KbFeedbackHandler.kt
│       ├── KbAuditHandler.kt
│       ├── KbUnmaskPiiHandler.kt
│       ├── KbUnmaskBrHandler.kt
│       ├── KbNetworkHandler.kt
│       └── HandlerUtils.kt
├── synctools/                          ← NEW PACKAGE
│   ├── JiraProjectSyncHandler.kt
│   ├── JiraSyncStatusHandler.kt
│   ├── JiraTicketGraphHandler.kt
│   ├── JiraTicketSyncHandler.kt
│   └── di/SyncToolsModule.kt
├── crawler/                            ← NEW PACKAGE (migrated)
│   ├── TicketCrawler.kt               (interface)
│   ├── TicketCrawlerImpl.kt
│   ├── ContentFetcher.kt             (interface + impl)
│   ├── ContentHasher.kt              (interface + impl)
│   ├── GraphBuilder.kt               (interface + impl)
│   ├── KBIngestor.kt                 (interface + impl)
│   ├── AttachmentQueuer.kt           (interface + impl)
│   ├── config/CrawlerConfig.kt
│   ├── model/CrawlerModels.kt
│   └── di/CrawlerModule.kt
├── sync/                               ← NEW PACKAGE (migrated repositories)
│   ├── SyncStateManager.kt           (interface + impl)
│   ├── TicketCacheRepository.kt      (interface + impl)
│   ├── TicketGraphRepository.kt      (interface + impl)
│   ├── AttachmentQueueRepository.kt  (interface + impl)
│   └── model/SyncModels.kt
├── dashboard/                          ← NEW PACKAGE (migrated)
│   ├── SyncDashboardRoutes.kt
│   ├── SyncEventBus.kt
│   └── di/DashboardModule.kt
├── graph/                              ← EXISTING (unchanged)
├── store/
├── queue/
├── masking/
├── audit/
├── network/
└── transport/KbHttpTransport.kt        ← MODIFIED (add /sync/* routes)
```

### 9.2 orchestrator-server (Packages Removed)

After Phase 3 cleanup, these packages are deleted:

```
DELETED:
├── synctools/                  ← ENTIRE PACKAGE DELETED
│   ├── SyncToolHandler.kt
│   ├── StatusToolHandler.kt
│   ├── GraphToolHandler.kt
│   └── SyncToolRegistrar.kt
├── crawler/                    ← ENTIRE PACKAGE DELETED
│   ├── TicketCrawlerImpl.kt
│   ├── KBIngestor.kt
│   ├── ContentFetcher.kt
│   ├── GraphBuilder.kt
│   ├── AttachmentQueuer.kt
│   ├── ContentHasher.kt
│   ├── config/CrawlerConfig.kt
│   ├── model/*.kt
│   └── di/crawlerModule.kt
├── dashboard/                  ← ENTIRE PACKAGE DELETED
│   ├── SyncDashboardHandler.kt
│   ├── SyncEventBus.kt
│   └── di/dashboardModule.kt
└── graph/                      ← ENTIRE PACKAGE DELETED
    ├── GraphService.kt
    ├── GraphDataRepository.kt
    ├── GraphRoutes.kt
    ├── model/*.kt
    ├── views/*.kt
    └── di/graphModule.kt
```

---

## 10. Implementation Tasks

### 10.1 Ordered Task List

| # | Task | Phase | Depends On | Estimate | Owner |
|---|------|-------|------------|----------|-------|
| 1 | Create `kb-server/.../kb/sync/` package — migrate `SyncStateManager`, `TicketCacheRepository`, `TicketGraphRepository`, `AttachmentQueueRepository` (interfaces + impls) | P1 | — | 2h | DEV |
| 2 | Create `kb-server/.../kb/sync/model/SyncModels.kt` — migrate domain models | P1 | — | 1h | DEV |
| 3 | Create `kb-server/.../kb/crawler/` package — migrate `TicketCrawler`, `TicketCrawlerImpl`, `ContentFetcher`, `ContentHasher`, `GraphBuilder`, `KBIngestor`, `AttachmentQueuer` | P1 | #1 | 3h | DEV |
| 4 | Create `kb-server/.../kb/crawler/model/CrawlerModels.kt` — migrate crawler data models | P1 | — | 1h | DEV |
| 5 | Create `kb-server/.../kb/crawler/config/CrawlerConfig.kt` — migrate config | P1 | — | 0.5h | DEV |
| 6 | Create `JiraProjectSyncHandler.kt` — unified project sync tool | P1 | #1, #3 | 2h | DEV |
| 7 | Create `JiraSyncStatusHandler.kt` — unified sync status tool | P1 | #1 | 1.5h | DEV |
| 8 | Create `JiraTicketGraphHandler.kt` — unified graph tool | P1 | #1 | 2h | DEV |
| 9 | Create `JiraTicketSyncHandler.kt` — new single-ticket sync | P1 | #3 | 1.5h | DEV |
| 10 | Create `kbSyncToolsModule()` DI module | P1 | #6, #7, #8, #9 | 1h | DEV |
| 11 | Create `kbCrawlerModule()` DI module | P1 | #3 | 0.5h | DEV |
| 12 | Create `kbDashboardModule()` DI module | P1 | — | 0.5h | DEV |
| 13 | Migrate `SyncDashboardRoutes` + `SyncEventBus` to kb-server | P1 | #12 | 2h | DEV |
| 14 | Migrate `sync-dashboard.html` static file to kb-server resources | P1 | #13 | 0.5h | DEV |
| 15 | Modify `KbDiModule.kt` — add new modules to `kbAppModule()` | P1 | #10, #11, #12 | 0.5h | DEV |
| 16 | Modify `kbHandlersModule()` — remove 3 old handlers | P1 | #15 | 0.5h | DEV |
| 17 | Modify `KbHttpTransport.kt` — add `/sync/*` dashboard routes | P1 | #13 | 1h | DEV |
| 18 | Update `kb-config.yaml` — increase connection pool (10→15) | P1 | — | 0.5h | DEV |
| 19 | Write unit tests for all 4 handlers | P1 | #6-#9 | 4h | DEV |
| 20 | Write integration tests (Testcontainers) | P1 | #15 | 3h | DEV |
| 21 | Deploy kb-server with new tools | P2 | #20 | 1h | DevOps |
| 22 | Smoke test all 4 tools on kb-server | P2 | #21 | 1h | QA |
| 23 | End-to-end sync test (trigger → crawl → ingest) | P2 | #22 | 2h | QA |
| 24 | Update agent MCP configs (`mcp-servers.json`) | P2 | #23 | 0.5h | DevOps |
| 25 | Verify agent workflows with new config | P2 | #24 | 1h | QA |
| 26 | Remove old handlers from orchestrator-server AppModule | P3 | #25 | 1h | DEV |
| 27 | Delete orchestrator-server source files (synctools/, crawler/, dashboard/, graph/) | P3 | #26 | 0.5h | DEV |
| 28 | Run full test suite on both servers | P3 | #27 | 1h | QA |
| 29 | Write migration verification tests | P4 | #28 | 2h | DEV |
| 30 | Performance test (1000 tickets, concurrent syncs) | P4 | #28 | 2h | QA |

**Total Estimate: ~36 hours (4.5 dev-days)**

### 10.2 Critical Path

```
#1 (repos) → #3 (crawler) → #6 (sync handler) → #10 (DI) → #15 (wire) → #20 (tests) → #21 (deploy) → #25 (verify) → #26 (cleanup)
```

---

## 11. Risk Mitigation

### 11.1 Technical Risks

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | Agent workflows break during config update | High | Medium | Phase 2 transition period — tools on both servers; rollback = revert config |
| 2 | Connection pool exhaustion on kb-server | Medium | Low | Increase pool from 10→15; monitor connection usage; add HikariCP metrics |
| 3 | Merge conflicts with MTO-116 (parallel kb-server work) | Medium | Medium | Work in separate packages (`synctools/`, `crawler/`, `dashboard/`); coordinate via daily sync |
| 4 | Scanner module breaks after orchestrator cleanup | High | Low | Scanner uses `sync-pipeline` module directly, NOT crawler; verify scanner still compiles after removal |
| 5 | Duplicate sync execution during transition | High | Low | `SyncStateManager` uses same DB; queue dedup prevents double-enqueue |
| 6 | SSE event bus not available in kb-server | Medium | Low | kb-server already has SharedFlow infrastructure (queue notifications); reuse pattern |
| 7 | `KBIngestor` dependency on `EmbeddingService` + `VectorDbClient` | Low | Low | Both already available in kb-server via `kbInfraModule()` and `kbSyncPipelineModule()` |
| 8 | Tool name collision during transition | Medium | Low | KbToolHandler auto-registration uses `toolName` as unique key; same name = override |

### 11.2 Mitigation Actions

| Risk # | Action | When | Owner |
|--------|--------|------|-------|
| 1 | Test config rollback procedure before migration | Before Phase 2 | DevOps |
| 2 | Add connection pool monitoring (HikariCP MXBean) | Phase 1 | DEV |
| 3 | Create separate Git branch for MTO-117 work | Immediately | DEV |
| 4 | Run `./gradlew :orchestrator-server:compileKotlin` after each removal | Phase 3 | DEV |
| 5 | Add integration test: trigger sync on both servers → verify only 1 execution | Phase 2 | QA |
| 6 | Verify `SharedFlow` pattern in existing `QueueWorker` notifications | Phase 1 | DEV |

---

## 12. Performance & Scalability

### 12.1 Performance Targets

| Operation | Target | Current | After Migration |
|-----------|--------|---------|-----------------|
| `jira_project_sync` response | < 2s | ~500ms (orchestrator) | ~500ms (kb-server, queue dispatch) |
| `jira_sync_status` response | < 1s | ~200ms (orchestrator) | ~300ms (kb-server, combined query) |
| `jira_ticket_graph` (1000 nodes) | < 5s | ~3s (orchestrator) | ~3s (kb-server, same algorithm) |
| `jira_ticket_sync` (single ticket) | < 10s | N/A (new) | ~5s (fetch + hash + ingest) |
| SSE event delivery | < 1s | ~200ms (orchestrator) | ~200ms (kb-server, SharedFlow) |

### 12.2 Connection Pooling

| Resource | Min | Max | Timeout | Idle Timeout |
|----------|-----|-----|---------|-------------|
| PostgreSQL (kb-server) | 7 | 15 | 30000ms | 600000ms |

### 12.3 Concurrency

| Concern | Solution |
|---------|----------|
| Concurrent project syncs | `ConcurrentHashMap.newKeySet()` in `TicketCrawlerImpl` — max 3 concurrent |
| Queue worker threads | Existing `QueueWorker` with configurable concurrency |
| Graph BFS traversal | Single-threaded per request (no shared state) |

---

## 13. Monitoring & Observability

### 13.1 Logging

| Log Event | Level | Fields | When |
|-----------|-------|--------|------|
| Sync tool invoked | INFO | projectKey, priority, fullSync, caller | Every `jira_project_sync` call |
| Sync status queried | DEBUG | projectKey | Every `jira_sync_status` call |
| Graph queried | DEBUG | projectKey, issueKey, depth | Every `jira_ticket_graph` call |
| Sync task enqueued | INFO | taskId, projectKey, priority | After successful enqueue |
| Duplicate sync rejected | WARN | projectKey, currentStatus | When sync already running |
| Crawl completed | INFO | projectKey, processed, skipped, ingested | After crawl finishes |
| Crawl failed | ERROR | projectKey, error message | On crawl exception |

### 13.2 Audit Trail

All tool invocations logged via `AuditService`:

| Event Type | Action | Metadata |
|------------|--------|----------|
| `INGEST` | `jira_project_sync` | projectKey, fullSync, taskId, priority |
| `QUERY` | `jira_sync_status` | projectKey |
| `QUERY` | `jira_ticket_graph` | projectKey, issueKey, depth, nodeCount |
| `INGEST` | `jira_ticket_sync` | issueKey, changed, ingested, linkedSynced |

---

## 14. Files to Create / Modify / Delete

### 14.1 Files to CREATE (in kb-server)

| # | File Path | Purpose |
|---|-----------|---------|
| 1 | `kb-server/src/.../kb/synctools/JiraProjectSyncHandler.kt` | Unified project sync tool |
| 2 | `kb-server/src/.../kb/synctools/JiraSyncStatusHandler.kt` | Unified sync status tool |
| 3 | `kb-server/src/.../kb/synctools/JiraTicketGraphHandler.kt` | Unified ticket graph tool |
| 4 | `kb-server/src/.../kb/synctools/JiraTicketSyncHandler.kt` | New single-ticket sync tool |
| 5 | `kb-server/src/.../kb/synctools/di/SyncToolsModule.kt` | DI module for sync tools |
| 6 | `kb-server/src/.../kb/crawler/TicketCrawler.kt` | Crawler interface |
| 7 | `kb-server/src/.../kb/crawler/TicketCrawlerImpl.kt` | Crawler implementation |
| 8 | `kb-server/src/.../kb/crawler/ContentFetcher.kt` | Content fetcher (interface + impl) |
| 9 | `kb-server/src/.../kb/crawler/ContentHasher.kt` | Content hasher (interface + impl) |
| 10 | `kb-server/src/.../kb/crawler/GraphBuilder.kt` | Graph edge builder (interface + impl) |
| 11 | `kb-server/src/.../kb/crawler/KBIngestor.kt` | KB ingestion (interface + impl) |
| 12 | `kb-server/src/.../kb/crawler/AttachmentQueuer.kt` | Attachment queuer (interface + impl) |
| 13 | `kb-server/src/.../kb/crawler/model/CrawlerModels.kt` | Crawler data models |
| 14 | `kb-server/src/.../kb/crawler/config/CrawlerConfig.kt` | Crawler configuration |
| 15 | `kb-server/src/.../kb/crawler/di/CrawlerModule.kt` | DI module for crawler |
| 16 | `kb-server/src/.../kb/sync/SyncStateManager.kt` | Sync state interface + impl |
| 17 | `kb-server/src/.../kb/sync/TicketCacheRepository.kt` | Ticket cache repository |
| 18 | `kb-server/src/.../kb/sync/TicketGraphRepository.kt` | Ticket graph repository |
| 19 | `kb-server/src/.../kb/sync/AttachmentQueueRepository.kt` | Attachment queue repository |
| 20 | `kb-server/src/.../kb/sync/model/SyncModels.kt` | Sync domain models |
| 21 | `kb-server/src/.../kb/dashboard/SyncDashboardRoutes.kt` | Dashboard HTTP routes |
| 22 | `kb-server/src/.../kb/dashboard/SyncEventBus.kt` | SSE event bus |
| 23 | `kb-server/src/.../kb/dashboard/di/DashboardModule.kt` | DI module for dashboard |
| 24 | `kb-server/src/main/resources/static/sync-dashboard.html` | Dashboard HTML |

### 14.2 Files to MODIFY

| # | File Path | Change |
|---|-----------|--------|
| 1 | `kb-server/src/.../kb/di/KbDiModule.kt` | Add 3 new modules, remove 3 old handler bindings |
| 2 | `kb-server/src/.../kb/transport/KbHttpTransport.kt` | Add `/sync/*` dashboard routes |
| 3 | `kb-server/src/main/resources/kb-config.yaml` | Increase connection pool size |
| 4 | `orchestrator-server/src/.../di/AppModule.kt` | Remove sync handlers, crawler, dashboard, graph modules |
| 5 | `orchestrator-server/src/.../protocol/McpServerFactory.kt` | Remove SyncToolRegistrar usage |
| 6 | `orchestrator-server/src/.../HttpStreamableServer.kt` | Remove `/sync/*` routes |
| 7 | Agent configs (`mcp-servers.json`, `.kiro/settings/mcp.json`) | Route sync tools to kb-server |

### 14.3 Files to DELETE (from orchestrator-server)

| # | File Path | Reason |
|---|-----------|--------|
| 1 | `.../synctools/SyncToolHandler.kt` | Replaced by JiraProjectSyncHandler |
| 2 | `.../synctools/StatusToolHandler.kt` | Replaced by JiraSyncStatusHandler |
| 3 | `.../synctools/GraphToolHandler.kt` | Replaced by JiraTicketGraphHandler |
| 4 | `.../synctools/SyncToolRegistrar.kt` | kb-server uses KbToolHandler auto-registration |
| 5 | `.../crawler/TicketCrawlerImpl.kt` | Moved to kb-server |
| 6 | `.../crawler/KBIngestor.kt` | Moved to kb-server |
| 7 | `.../crawler/ContentFetcher.kt` | Moved to kb-server |
| 8 | `.../crawler/GraphBuilder.kt` | Moved to kb-server |
| 9 | `.../crawler/AttachmentQueuer.kt` | Moved to kb-server |
| 10 | `.../crawler/ContentHasher.kt` | Moved to kb-server |
| 11 | `.../crawler/model/*.kt` | Moved to kb-server |
| 12 | `.../crawler/config/CrawlerConfig.kt` | Moved to kb-server |
| 13 | `.../crawler/di/crawlerModule.kt` | Moved to kb-server |
| 14 | `.../dashboard/SyncDashboardHandler.kt` | Moved to kb-server |
| 15 | `.../dashboard/SyncEventBus.kt` | Moved to kb-server |
| 16 | `.../dashboard/di/dashboardModule.kt` | Moved to kb-server |
| 17 | `.../graph/GraphService.kt` | Already exists in kb-server |
| 18 | `.../graph/GraphDataRepository.kt` | Already exists in kb-server |
| 19 | `.../graph/GraphRoutes.kt` | Already exists in kb-server |
| 20 | `.../graph/model/*.kt` | Already exists in kb-server |
| 21 | `.../graph/views/*.kt` | Already exists in kb-server |
| 22 | `.../graph/di/graphModule.kt` | Already exists in kb-server |
| 23 | `.../resources/static/sync-dashboard.html` | Moved to kb-server |

### 14.4 Files to DELETE (from kb-server — merged into unified tools)

| # | File Path | Reason |
|---|-----------|--------|
| 1 | `.../protocol/handlers/KbSyncTriggerHandler.kt` | Merged into JiraProjectSyncHandler |
| 2 | `.../protocol/handlers/KbSyncStatusHandler.kt` | Merged into JiraSyncStatusHandler |
| 3 | `.../protocol/handlers/KbGraphHandler.kt` | Merged into JiraTicketGraphHandler |

---

## 15. Appendix

### 15.1 Tool Registration Summary (After Migration)

**kb-server tools (final):**

| Tool Name | Handler Class | Source |
|-----------|--------------|--------|
| `kb_ingest` | KbIngestHandler | Existing |
| `kb_search` | KbSearchHandler | Existing |
| `kb_read` | KbReadHandler | Existing |
| `kb_delete` | KbDeleteHandler | Existing |
| `kb_link` | KbLinkHandler | Existing |
| `kb_feedback` | KbFeedbackHandler | Existing |
| `kb_audit` | KbAuditHandler | Existing |
| `kb_unmask_pii` | KbUnmaskPiiHandler | Existing |
| `kb_unmask_br` | KbUnmaskBrHandler | Existing |
| `kb_network` | KbNetworkHandler | Existing |
| `jira_project_sync` | JiraProjectSyncHandler | **Migrated + Merged** |
| `jira_sync_status` | JiraSyncStatusHandler | **Migrated + Merged** |
| `jira_ticket_graph` | JiraTicketGraphHandler | **Migrated** |
| `jira_ticket_sync` | JiraTicketSyncHandler | **New** |

**orchestrator-server tools (final):**

| Tool Name | Handler | Status |
|-----------|---------|--------|
| `find_tools` | ToolDiscoveryHandler | Unchanged |
| `execute_dynamic_tool` | ToolExecutionHandler | Unchanged |
| `agent_log` | AgentLogHandler | Unchanged |
| `embed_images` | (via bridge) | Unchanged |
| `stream_write_file` | (via bridge) | Unchanged |
| `export_drawio` | DrawioExportHandler | Unchanged |

**Removed tools:**

| Tool Name | Was On | Reason |
|-----------|--------|--------|
| `kb_sync_trigger` | kb-server | Merged into `jira_project_sync` |
| `kb_sync_status` | kb-server | Merged into `jira_sync_status` |
| `kb_graph` | kb-server | Merged into `jira_ticket_graph` |

### 15.2 Field Naming Resolution

The BRD uses `projectKey` (camelCase) while the existing `KbSyncTriggerHandler` uses `project_key` (snake_case). The unified handlers will use **camelCase** (`projectKey`, `fullSync`, `issueKey`) as the canonical field names, matching the BRD specification and existing orchestrator-server conventions.

| Field | BRD Name | KbSyncTriggerHandler Name | Unified Name |
|-------|----------|---------------------------|--------------|
| Project key | `projectKey` | `project_key` | **`projectKey`** |
| Full sync flag | `fullSync` | `full_sync` | **`fullSync`** |
| Issue key | `issueKey` | N/A | **`issueKey`** |
| Include linked | `includeLinked` | N/A | **`includeLinked`** |

### 15.3 Glossary

| Term | Definition |
|------|------------|
| MCP | Model Context Protocol — communication protocol between AI agents and tool servers |
| SSE | Server-Sent Events — HTTP-based unidirectional real-time event streaming |
| BFS | Breadth-First Search — graph traversal algorithm for subgraph extraction |
| HPQ | High Priority Queue — queue for urgent sync tasks |
| NPQ | Normal Priority Queue — queue for standard sync tasks |
| KbToolHandler | Interface in kb-server for registering MCP tools with standardized error handling |
| SyncOrchestrator | Core interface in sync-pipeline module that coordinates the sync process |
| SharedFlow | Kotlin coroutines hot flow — used for SSE event broadcasting |

### 15.4 Open Questions

| # | Question | Status | Answer |
|---|----------|--------|--------|
| 1 | Should `jira_ticket_sync` be included in Phase 1 or deferred? | Resolved | Include in Phase 1 — low effort, uses existing `crawlSingle()` |
| 2 | Should orchestrator-server keep `sync-pipeline` dependency after cleanup? | Resolved | Yes — scanner module still uses it |
| 3 | Connection pool size 15 sufficient for concurrent syncs? | Open | Monitor during Phase 2; adjust if needed |
| 4 | Should `SyncTaskHandler` in queue module be updated to use new crawler? | Resolved | Yes — it already delegates to `SyncOrchestrator` which uses the crawler |

---

*End of Document*
