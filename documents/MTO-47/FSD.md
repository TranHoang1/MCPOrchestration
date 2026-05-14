# Functional Specification Document (FSD)

## MCPOrchestration — MTO-47: Unified Sync Pipeline — Multi-Dimensional Jira Indexing

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-47 |
| Title | Unified Sync Pipeline — Multi-Dimensional Jira Indexing |
| Author | TA Agent |
| Version | 1.0 |
| Date | 2026-05-14 |
| Status | Draft |
| Related BRD | documents/MTO-47/BRD.md |

---

## 1. Architecture Overview

### 1.1 Current State (Conflict)

```
orchestrator-server:                    kb-server:
┌─────────────────────┐                ┌──────────────────────┐
│ jira_project_sync   │                │ kb_sync_trigger      │
│   → SyncToolHandler │                │   → KbSyncTriggerHdl │
│   → ProjectScanner  │                │   → Queue            │
│   → TicketCrawler   │                │   → SyncTaskHandler  │
│   → KBIngestor      │                │     (STUB)           │
│   → Vector DB       │                │                      │
└─────────────────────┘                └──────────────────────┘
         ↓                                      ↓
   Data in orchestrator DB              No data produced
```

### 1.2 Target State (Unified)

```
┌─ sync-pipeline (NEW shared module) ──────────────────────────────┐
│                                                                   │
│  SyncOrchestrator                                                │
│    ├── JiraCrawlService (pagination + fetch)                     │
│    ├── DimensionProcessor (config-driven)                        │
│    │     ├── TicketMetadataDimension                             │
│    │     ├── CommentDimension (per-person)                       │
│    │     ├── AttachmentDimension                                 │
│    │     ├── UserRelationDimension                               │
│    │     └── FeatureDetectionDimension (AI-pluggable)            │
│    ├── SourceTracker (provenance)                                │
│    ├── IndexWriter (DB + Vector)                                 │
│    └── SyncStateManager (state machine)                          │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
         ↑                                      ↑
orchestrator-server:                    kb-server:
┌─────────────────────┐                ┌──────────────────────┐
│ jira_project_sync   │                │ kb_sync_trigger      │
│   → SyncToolHandler │                │   → SyncTaskHandler  │
│   → SyncOrchestrator│                │   → SyncOrchestrator │
└─────────────────────┘                └──────────────────────┘
```

### 1.3 Module Structure

```
sync-pipeline/                          ← NEW Gradle module
├── src/main/kotlin/com/orchestrator/mcp/sync/pipeline/
│   ├── SyncOrchestrator.kt            ← Entry point
│   ├── SyncPipelineConfig.kt          ← Configuration
│   ├── crawl/
│   │   ├── JiraCrawlService.kt        ← Jira API pagination
│   │   ├── TicketFetcher.kt           ← Single ticket deep fetch
│   │   └── AdfParser.kt               ← ADF → plain text
│   ├── dimension/
│   │   ├── IndexDimension.kt          ← Interface (Strategy pattern)
│   │   ├── DimensionRegistry.kt       ← Config-driven registry
│   │   ├── DimensionProcessor.kt      ← Orchestrates all dimensions
│   │   ├── TicketMetadataDimension.kt
│   │   ├── CommentDimension.kt
│   │   ├── AttachmentDimension.kt
│   │   ├── UserRelationDimension.kt
│   │   └── FeatureDetectionDimension.kt
│   ├── model/
│   │   ├── CrawledTicket.kt           ← Full ticket data after fetch
│   │   ├── IndexEntry.kt              ← Generic indexed record
│   │   ├── SourceRef.kt               ← Provenance tracking
│   │   └── DimensionConfig.kt         ← Dimension configuration model
│   ├── storage/
│   │   ├── IndexWriter.kt             ← Interface for storage
│   │   ├── PostgresIndexWriter.kt     ← Relational storage
│   │   └── VectorIndexWriter.kt       ← Vector DB storage
│   ├── state/
│   │   └── SyncStateTracker.kt        ← Progress + state machine
│   └── di/
│       └── SyncPipelineModule.kt       ← Koin DI
```

---

## 2. Functional Specifications

### 2.1 UC-01: Unified Sync Execution

**Trigger:** Either `jira_project_sync` or `kb_sync_trigger` invoked

**Flow:**

```
1. Validate project_key format
2. Check sync state (not already RUNNING)
3. Load dimension config (from DB or default)
4. Mark state = RUNNING
5. Build JQL (full or incremental based on lastSyncAt)
6. Paginate through Jira search results
7. For each ticket batch:
   a. Fetch full ticket content (description, comments, links, attachments)
   b. Compute content hash → skip if unchanged
   c. For each enabled dimension:
      - Extract dimension-specific data
      - Build IndexEntry with SourceRef
      - Write to storage (DB + optional vector)
   d. Update sync progress
8. Run post-sync processors (feature detection)
9. Mark state = COMPLETED
```

**Error Handling:**

| Error | Action |
|-------|--------|
| Jira API 401 | Mark FAILED, log auth error |
| Jira API 429 | Backoff + retry (existing rate limiter) |
| Jira API 5xx | Retry 3x, then mark FAILED |
| Single ticket fetch fails | Log warning, skip ticket, continue |
| DB write fails | Retry 2x, then mark FAILED |
| Crash during sync | On restart: detect RUNNING state, resume from last_offset |

---

### 2.2 UC-02: Dimension Processing

**Interface Contract:**

```kotlin
interface IndexDimension {
    /** Unique dimension identifier (matches config) */
    val dimensionId: String
    
    /** Human-readable name */
    val displayName: String
    
    /** Extract index entries from a crawled ticket */
    suspend fun extract(ticket: CrawledTicket, config: DimensionConfig): List<IndexEntry>
    
    /** Post-sync processing (e.g., feature detection across all tickets) */
    suspend fun postProcess(projectKey: String, config: DimensionConfig): List<IndexEntry> = emptyList()
    
    /** Whether this dimension supports vector indexing */
    fun supportsVector(): Boolean
}
```

**Built-in Dimensions:**

| ID | Class | Strategy | Vector |
|----|-------|----------|--------|
| `ticket_metadata` | TicketMetadataDimension | per_ticket | Yes |
| `comments` | CommentDimension | per_comment | Yes |
| `attachments` | AttachmentDimension | per_attachment | No |
| `user_relations` | UserRelationDimension | per_relation | No |
| `feature_grouping` | FeatureDetectionDimension | per_feature (post-sync) | Yes |

---

### 2.3 UC-03: Comment Extraction (Per-Person)

**Input:** Jira issue with expanded comments

**PII Masking Strategy:** Store both original + masked version per entry.

**Output per comment:**

```kotlin
IndexEntry(
    id = UUID (deterministic from ticket_key + comment_id),
    dimensionId = "comments",
    projectKey = "MTO",
    ticketKey = "MTO-14",
    sourceRef = SourceRef(
        type = "jira_comment",
        path = "jira:MTO/MTO-14/comment/12345",
        syncedAt = Instant.now(),
        contentHash = "sha256:..."
    ),
    data = mapOf(
        "jira_comment_id" to "12345",
        "author_account_id" to "5f7c...abc",
        "author_display_name" to "Nguyen Van A",
        "body" to "Original text with PII (restricted access)",
        "body_masked" to "Comment by [NAME_1] about feature...",
        "created_at" to "2026-05-10T10:00:00Z",
        "updated_at" to "2026-05-11T08:00:00Z",
        "visibility" to null
    ),
    vectorText = "Comment by Nguyen Van A on MTO-14: This feature needs..."
)
```

**PII Mapping stored separately:**
```kotlin
// PiiMapping entries created for each detected PII
PiiMapping(issueKey = "MTO-14", placeholder = "[NAME_1]", originalValue = "Nguyen Van A", mappingType = NAME)
PiiMapping(issueKey = "MTO-14", placeholder = "[EMAIL_1]", originalValue = "a@company.com", mappingType = EMAIL)
```

---

### 2.4 UC-04: User Relation Extraction

**Derived from:** assignee, reporter, commenter fields

**Output per relation:**

```kotlin
IndexEntry(
    id = UUID (deterministic from user_id + ticket_key + role),
    dimensionId = "user_relations",
    projectKey = "MTO",
    ticketKey = "MTO-14",
    sourceRef = SourceRef(
        type = "derived",
        path = "jira:MTO/MTO-14/assignee",
        syncedAt = Instant.now()
    ),
    data = mapOf(
        "user_account_id" to "5f7c...abc",
        "user_display_name" to "Nguyen Van A",
        "relation_type" to "assignee",  // assignee | reporter | commenter
        "first_interaction_at" to "2026-05-01T...",
        "interaction_count" to "1"
    ),
    vectorText = null  // No vector for relations
)
```

---

### 2.5 UC-05: Feature Auto-Detection

**Trigger:** Post-sync processor (runs after all tickets indexed)

**Detection Strategy (pluggable):**

```
1. Epic-based grouping (baseline):
   - All tickets under same Epic = same feature
   - Feature name = Epic summary
   
2. Label-based enrichment:
   - Tickets with shared "feature:*" labels grouped together
   
3. Component-based enrichment:
   - Tickets in same Jira component = related feature
   
4. (Future) Semantic clustering:
   - Tickets with similar descriptions clustered by AI
```

**Output per feature:**

```kotlin
IndexEntry(
    id = UUID (deterministic from feature_id),
    dimensionId = "feature_grouping",
    projectKey = "MTO",
    ticketKey = null,  // Feature spans multiple tickets
    sourceRef = SourceRef(
        type = "ai_derived",
        path = "derived:feature/auth-system",
        syncedAt = Instant.now(),
        derivedFrom = listOf("jira:MTO/MTO-14", "jira:MTO/MTO-15", "jira:MTO/MTO-16")
    ),
    data = mapOf(
        "feature_id" to "auth-system",
        "feature_name" to "Authentication System",
        "detection_method" to "epic_hierarchy",
        "confidence" to "0.95",
        "ticket_keys" to "[MTO-14, MTO-15, MTO-16]",
        "epic_key" to "MTO-14"
    ),
    vectorText = "Feature: Authentication System. Tickets: MTO-14 (Epic), MTO-15 (JWT impl), MTO-16 (OAuth2)"
)
```

---

### 2.6 UC-06: Dimension Configuration API

**REST Endpoint (kb-server HTTP mode):**

```
GET  /api/sync/dimensions              → List all dimension configs
GET  /api/sync/dimensions/{id}         → Get single dimension config
PUT  /api/sync/dimensions/{id}         → Update dimension config
POST /api/sync/dimensions              → Add custom dimension
DELETE /api/sync/dimensions/{id}        → Disable dimension (soft delete)
```

**MCP Tool (both servers):**

```
Tool: sync_dimension_config
Input: { action: "list" | "get" | "update", dimension_id?: string, config?: object }
Output: Current dimension configuration
```

**Configuration stored in:** `sync_dimension_config` table (PostgreSQL)

---

### 2.7 UC-07: Source Provenance Tracking

**SourceRef Model:**

```kotlin
data class SourceRef(
    val type: String,           // jira_ticket, jira_comment, jira_attachment, derived, ai_derived
    val path: String,           // Hierarchical path: jira:{project}/{ticket}/comment/{id}
    val syncedAt: Instant,      // When this data was fetched
    val contentHash: String?,   // For change detection
    val derivedFrom: List<String>? = null  // For AI-derived data: list of source paths
)
```

**Path Format:**

| Source Type | Path Pattern | Example |
|-------------|-------------|---------|
| Ticket | `jira:{project}/{ticket}` | `jira:MTO/MTO-14` |
| Comment | `jira:{project}/{ticket}/comment/{id}` | `jira:MTO/MTO-14/comment/12345` |
| Attachment | `jira:{project}/{ticket}/attachment/{id}` | `jira:MTO/MTO-14/attachment/67890` |
| Link | `jira:{project}/{ticket}/link/{target}` | `jira:MTO/MTO-14/link/MTO-15` |
| User relation | `jira:{project}/{ticket}/{role}` | `jira:MTO/MTO-14/assignee` |
| Feature | `derived:feature/{id}` | `derived:feature/auth-system` |

---

## 3. Data Model

### 3.1 New Tables (sync-pipeline schema)

```sql
-- Unified index entries (all dimensions write here)
CREATE TABLE sync_index_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dimension_id VARCHAR(50) NOT NULL,
    project_key VARCHAR(20) NOT NULL,
    ticket_key VARCHAR(50),              -- NULL for feature-level entries
    entry_key VARCHAR(200) NOT NULL,     -- Deterministic key for upsert
    
    -- Source provenance
    source_type VARCHAR(50) NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    content_hash VARCHAR(64),
    derived_from JSONB,                  -- For AI-derived entries
    
    -- Flexible data storage
    data JSONB NOT NULL,                 -- Dimension-specific structured data
    
    -- Vector indexing
    vector_text TEXT,                    -- Text used for embedding (NULL = no vector)
    vector_indexed BOOLEAN DEFAULT false,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uq_index_entry UNIQUE (dimension_id, entry_key)
);

CREATE INDEX idx_sie_project ON sync_index_entries(project_key);
CREATE INDEX idx_sie_dimension ON sync_index_entries(dimension_id);
CREATE INDEX idx_sie_ticket ON sync_index_entries(ticket_key);
CREATE INDEX idx_sie_source_path ON sync_index_entries(source_path);
CREATE INDEX idx_sie_data_gin ON sync_index_entries USING GIN (data);

-- Dimension configuration (UI-configurable)
CREATE TABLE sync_dimension_config (
    id VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    source_type VARCHAR(50) NOT NULL,    -- jira_fields, jira_comments, jira_attachments, derived, ai_derived
    fields JSONB,                        -- Which fields to extract
    index_strategy VARCHAR(50) NOT NULL, -- per_ticket, per_comment, per_attachment, per_relation, per_feature
    vector_enabled BOOLEAN DEFAULT false,
    processor_class VARCHAR(200),        -- FQCN of IndexDimension implementation (for custom)
    config_json JSONB,                   -- Additional processor-specific config
    sort_order INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Sync state (unified, replaces jira_sync_state)
CREATE TABLE sync_state (
    project_key VARCHAR(20) PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    last_sync_at TIMESTAMPTZ,
    last_offset INT DEFAULT 0,
    total_issues INT DEFAULT 0,
    synced_issues INT DEFAULT 0,
    error_message TEXT,
    dimensions_processed JSONB,          -- Which dimensions completed
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- User registry (derived from sync data)
CREATE TABLE sync_users (
    account_id VARCHAR(100) PRIMARY KEY,
    display_name VARCHAR(200),
    email VARCHAR(200),
    avatar_url TEXT,
    first_seen_at TIMESTAMPTZ DEFAULT NOW(),
    last_active_at TIMESTAMPTZ,
    projects JSONB,                      -- ["MTO", "PROJ2"]
    total_tickets INT DEFAULT 0,
    total_comments INT DEFAULT 0
);
```

### 3.2 Default Dimension Config (Seed Data)

```sql
INSERT INTO sync_dimension_config (id, display_name, enabled, source_type, index_strategy, vector_enabled, sort_order) VALUES
('ticket_metadata', 'Ticket Metadata', true, 'jira_fields', 'per_ticket', true, 1),
('comments', 'Comments Per Person', true, 'jira_comments', 'per_comment', true, 2),
('attachments', 'Attachment Metadata', true, 'jira_attachments', 'per_attachment', false, 3),
('user_relations', 'User Relationships', true, 'derived', 'per_relation', false, 4),
('feature_grouping', 'Feature Auto-Detection', true, 'ai_derived', 'per_feature', true, 5);
```

---

## 4. Integration Contracts

### 4.1 Jira API Fields Required

```kotlin
val SYNC_FIELDS = listOf(
    "summary", "description", "issuetype", "status", "priority",
    "assignee", "reporter", "parent", "labels", "components",
    "fixVersions", "created", "updated", "resolutiondate",
    "comment", "issuelinks", "attachment",
    "customfield_10014"  // Epic Link (varies by instance)
)

val SYNC_EXPAND = listOf("names")  // For custom field name resolution
```

### 4.2 SyncOrchestrator Interface

```kotlin
interface SyncOrchestrator {
    /** Start or resume a project sync */
    suspend fun sync(projectKey: String, options: SyncOptions): SyncResult
    
    /** Get current sync progress */
    suspend fun getProgress(projectKey: String): SyncProgress?
    
    /** Cancel a running sync */
    suspend fun cancel(projectKey: String): Boolean
}

data class SyncOptions(
    val fullSync: Boolean = false,
    val batchSize: Int = 50,
    val dimensions: List<String>? = null  // NULL = all enabled
)

data class SyncResult(
    val projectKey: String,
    val totalTickets: Int,
    val processedTickets: Int,
    val skippedTickets: Int,
    val entriesCreated: Map<String, Int>,  // dimension_id → count
    val duration: Duration,
    val status: SyncStatus
)
```

### 4.3 How Both Tools Use SyncOrchestrator

**orchestrator-server (jira_project_sync):**
```kotlin
class SyncToolHandler(private val syncOrchestrator: SyncOrchestrator) {
    suspend fun handle(arguments: JsonObject?): CallToolResult {
        val projectKey = arguments?.get("projectKey")?.jsonPrimitive?.content ?: ...
        val fullSync = arguments?.get("fullSync")?.jsonPrimitive?.boolean ?: false
        
        // Launch async, return immediately
        CoroutineScope(Dispatchers.Default).launch {
            syncOrchestrator.sync(projectKey, SyncOptions(fullSync = fullSync))
        }
        
        return successResult(...)
    }
}
```

**kb-server (SyncTaskHandler):**
```kotlin
class SyncTaskHandler(private val syncOrchestrator: SyncOrchestrator) : TaskHandler {
    override fun taskType(): String = "sync"
    
    override suspend fun handle(task: QueueTask) {
        val projectKey = task.payload["project_key"]?.jsonPrimitive?.content ?: ...
        val fullSync = task.payload["full_sync"]?.jsonPrimitive?.content?.toBoolean() ?: false
        
        // Execute synchronously (queue worker manages lifecycle)
        syncOrchestrator.sync(projectKey, SyncOptions(fullSync = fullSync))
    }
}
```

---

## 5. Extensibility Design

### 5.1 Adding a New Dimension (No Code Change)

**Step 1:** Admin adds config via API/UI:
```json
POST /api/sync/dimensions
{
    "id": "sprint_tracking",
    "display_name": "Sprint Tracking",
    "enabled": true,
    "source_type": "jira_fields",
    "fields": ["sprint", "story_points"],
    "index_strategy": "per_ticket",
    "vector_enabled": false
}
```

**Step 2:** Built-in `GenericFieldDimension` handles `source_type: jira_fields` automatically — extracts specified fields from ticket data.

### 5.2 Adding a Custom Dimension (Code Required)

For complex logic (e.g., sentiment analysis):

1. Implement `IndexDimension` interface
2. Register class in `DimensionRegistry`
3. Add config with `processor_class` pointing to implementation
4. Deploy new version

### 5.3 Dimension Lifecycle

```
Config Created → Enabled → Next Sync Processes It → Entries Written
Config Disabled → Next Sync Skips It → Existing Entries Preserved
Config Deleted → Entries Cleaned Up (async)
```

---

## 6. Migration Strategy

### 6.1 From Current State

1. Create `sync-pipeline` module with shared code
2. Migrate orchestrator-server to use `SyncOrchestrator` (replace direct TicketCrawler calls)
3. Implement `SyncTaskHandler` in kb-server using same `SyncOrchestrator`
4. Run full re-sync to populate new schema
5. Deprecate old `jira_ticket_cache` direct writes

### 6.2 Backward Compatibility

- Existing `jira_ticket_cache` table preserved (read by graph service during migration)
- New `sync_index_entries` table is additive
- Old vector DB entries preserved until re-indexed
- Both tools work during migration (old behavior until switched)

### 6.3 kb_search Migration (Option C — Single Source of Truth)

**Why Option C is optimal:**
- No dual-write overhead (Option B writes to 2 tables = 2x latency)
- No cross-table query complexity (Option A queries 2 tables = slower)
- Single source of truth = no data inconsistency risk

**Migration Steps:**
1. Deploy sync-pipeline with new `sync.index_entries` table
2. Run full re-sync → populates new table
3. Update `kb_search` handler to query `sync.index_entries` (with role-based filtering)
4. Keep old `kb.kb_entries` as read-only archive (no new writes)
5. After verification period → deprecate old table

**kb_search Query Migration:**
```kotlin
// BEFORE: query kb.kb_entries
val entries = kbEntryRepository.search(query, projectKey)

// AFTER: query sync.index_entries with role-based content filtering
val entries = syncIndexRepository.search(
    query = query,
    projectKey = projectKey,
    dimensions = listOf("ticket_metadata", "comments"),
    roleFilter = callerRole  // Determines which data fields are visible
)
```

### 6.4 GraphService Upgrade

**Current:** Reads from `kb.ticket_cache` + `kb.ticket_graph` (separate tables)
**Target:** Reads from `sync.index_entries` (dimensions: ticket_metadata, user_relations)

**Migration:**
1. Add new `SyncGraphDataRepository` that queries `sync.index_entries`
2. Implement `ViewModeStrategy` adapters for new data format
3. Update `GraphModule` DI to use new repository
4. Deprecate old `TicketCacheRepository` + `TicketGraphRepository`

**New Graph Capabilities (from multi-dimensional data):**
- User→Ticket traversal (dimension = user_relations)
- Feature→Ticket traversal (dimension = feature_grouping)
- Ticket→Attachment listing (dimension = attachments)
- All existing Ticket→Ticket traversal preserved

---

## 7. Performance Design

### 7.1 Concurrent Pipeline Architecture

```
┌─ JiraCrawlService ─────────────────────────────────────────────┐
│                                                                  │
│  Pagination (sequential)                                        │
│    Page 1 → Page 2 → Page 3 → ...                              │
│                                                                  │
│  Per-page: Concurrent ticket fetch (Semaphore-bounded)          │
│    ┌─ Ticket 1 ─┐  ┌─ Ticket 2 ─┐  ┌─ Ticket 3 ─┐           │
│    │ fetch()     │  │ fetch()     │  │ fetch()     │           │
│    └─────────────┘  └─────────────┘  └─────────────┘           │
│         ↓                 ↓                 ↓                   │
│    ┌─ DimensionProcessor (parallel per dimension) ─────────┐   │
│    │  TicketMeta │ Comments │ Attachments │ UserRel │       │   │
│    └────────────────────────────────────────────────────────┘   │
│         ↓                                                       │
│    ┌─ BatchWriter (buffered, flush every N entries) ────────┐   │
│    │  PostgreSQL batch INSERT ... ON CONFLICT DO UPDATE      │   │
│    │  Vector batch (collect → embed batch → upsert batch)    │   │
│    └────────────────────────────────────────────────────────┘   │
│                                                                  │
│  Post-sync: FeatureDetection (AI-powered, async)                │
│    All tickets loaded → AI analysis → Feature entries written   │
└──────────────────────────────────────────────────────────────────┘
```

### 7.2 Performance Optimizations

| Technique | Where | Impact |
|-----------|-------|--------|
| **Content hash skip** | Per ticket | Skip unchanged tickets (60-90% on incremental) |
| **Concurrent fetch** | Jira API | 5 parallel fetches (configurable, rate-limit aware) |
| **Batch DB writes** | PostgreSQL | 50-100 entries per INSERT (vs 1-by-1) |
| **Batch embeddings** | Vector indexing | Embed 10-20 texts per API call |
| **Streaming pipeline** | Overall | Fetch → Process → Write overlapped via channels |
| **Dimension parallelism** | Per ticket | All dimensions extract concurrently |
| **Checkpoint resume** | Crash recovery | Resume from last_offset, not restart |
| **Connection pooling** | DB + HTTP | Reuse connections across batches |

### 7.3 Kotlin Coroutines Flow Design

```kotlin
// Streaming pipeline using Kotlin Flow + Channels
suspend fun syncProject(projectKey: String) {
    val ticketChannel = Channel<CrawledTicket>(capacity = 100)
    val entryChannel = Channel<List<IndexEntry>>(capacity = 200)
    
    coroutineScope {
        // Producer: Fetch tickets from Jira (bounded concurrency)
        launch { fetchTickets(projectKey, ticketChannel) }
        
        // Transformer: Process dimensions (parallel per ticket)
        launch { processTickets(ticketChannel, entryChannel) }
        
        // Consumer: Batch write to storage
        launch { batchWrite(entryChannel) }
    }
}
```

### 7.4 Performance Targets

| Metric | Target | Technique |
|--------|--------|-----------|
| Full sync 100 tickets | < 3 minutes | Concurrent fetch + batch write |
| Full sync 1000 tickets | < 20 minutes | Streaming pipeline + checkpoints |
| Incremental sync (10 changed) | < 20 seconds | Hash skip + targeted fetch |
| Single ticket deep fetch | < 2 seconds | Direct API call |
| Embedding batch (20 texts) | < 5 seconds | Batch API call |
| DB batch write (100 entries) | < 500ms | Single INSERT statement |
| Memory per 100 tickets | < 200MB | Streaming (not load-all) |

---

## 8. AI Provider Integration

### 8.1 Multi-Provider Support

Sync pipeline sử dụng AI cho 2 mục đích:
1. **Embedding generation** — vector indexing (existing `EmbeddingService`)
2. **Feature detection** — AI analysis to group tickets (new `AiAnalysisService`)

**Supported Providers (reuse existing infrastructure):**

| Provider | Embedding | AI Analysis | Config Key |
|----------|-----------|-------------|------------|
| Ollama | ✅ `OllamaEmbeddingService` | ✅ via LangChain4j | `ollama` |
| LM Studio | ✅ `LmStudioEmbeddingService` | ✅ via OpenAI-compatible API | `lmstudio` |
| OpenAI | ✅ `OpenAiEmbeddingService` | ✅ via LangChain4j | `openai` |
| Azure OpenAI | ❌ (add later) | ✅ via LangChain4j | `azure` |

### 8.2 AI Analysis Service Interface

```kotlin
/**
 * AI service for intelligent analysis during sync.
 * Used by FeatureDetectionDimension and future AI-powered dimensions.
 */
interface AiAnalysisService {
    /** Analyze tickets and detect feature groupings */
    suspend fun detectFeatures(tickets: List<TicketSummary>): List<FeatureGroup>
    
    /** Generate enriched description for a feature */
    suspend fun summarizeFeature(feature: FeatureGroup): String
    
    /** Check if AI provider is available */
    suspend fun isHealthy(): Boolean
}

data class TicketSummary(
    val key: String,
    val summary: String,
    val issueType: String,
    val epicKey: String?,
    val labels: List<String>,
    val components: List<String>
)

data class FeatureGroup(
    val featureId: String,
    val featureName: String,
    val ticketKeys: List<String>,
    val detectionMethod: String,
    val confidence: Double,
    val epicKey: String?
)
```

### 8.3 Provider Configuration

```yaml
sync:
  ai:
    # Primary provider for feature detection
    provider: "ollama"              # ollama | lmstudio | openai | azure
    model: "llama3"                 # Model name
    base_url: "http://localhost:11434"
    api_key: null                   # Required for openai/azure
    temperature: 0.1
    timeout_seconds: 30
    max_tokens: 4000
    
    # Fallback when primary unavailable
    fallback_provider: null         # Optional fallback
    fallback_model: null
    
  embedding:
    # Reuse existing EmbeddingService config
    provider: "ollama"              # ollama | lmstudio | openai
    model: "nomic-embed-text"
    base_url: "http://localhost:11434"
    dimensions: 768
    batch_size: 20                  # Texts per embedding API call
    
  feature_detection:
    enabled: true
    strategy: "ai_hybrid"           # epic_only | ai_hybrid | full_ai
    confidence_threshold: 0.7
    max_tickets_per_analysis: 200   # Chunk large projects
```

### 8.4 Graceful Degradation

| Scenario | Behavior |
|----------|----------|
| AI provider unavailable | Feature detection skipped, other dimensions proceed |
| Embedding provider unavailable | Vector indexing skipped, relational data still written |
| Both unavailable | Only relational data indexed (metadata, comments, relations) |
| Timeout on AI call | Retry 2x, then skip with warning |
| Rate limit hit | Backoff + retry (existing rate limiter pattern) |

---

## 9. Configuration

### 9.1 sync-pipeline.yaml (Full)

```yaml
sync:
  jira:
    base_url: ${JIRA_URL}
    email: ${JIRA_EMAIL}
    api_token: ${JIRA_TOKEN}
    rate_limit: 10                  # requests/second
    
  pipeline:
    batch_size: 50                  # Tickets per page
    batch_delay_ms: 500             # Delay between pages
    max_concurrent_fetches: 5       # Parallel ticket fetches
    max_comments_per_ticket: 100
    content_max_length: 50000       # Chars per field
    write_buffer_size: 100          # Entries before flush
    
  state:
    stale_timeout_minutes: 30
    checkpoint_interval: 10         # Save progress every N tickets
    
  ai:
    provider: "ollama"
    model: "llama3"
    base_url: "http://localhost:11434"
    api_key: null
    temperature: 0.1
    timeout_seconds: 30
    max_tokens: 4000
    fallback_provider: null
    
  embedding:
    provider: "ollama"
    model: "nomic-embed-text"
    base_url: "http://localhost:11434"
    dimensions: 768
    batch_size: 20
    
  vector:
    enabled: true
    collection: "sync_entries"
    
  feature_detection:
    enabled: true
    strategy: "ai_hybrid"
    confidence_threshold: 0.7
    max_tickets_per_analysis: 200
```
