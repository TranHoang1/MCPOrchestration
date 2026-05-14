# Technical Design Document (TDD)

## MCPOrchestration — MTO-47: Unified Sync Pipeline — Multi-Dimensional Jira Indexing

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-47 |
| Title | Unified Sync Pipeline — Multi-Dimensional Jira Indexing |
| Author | SA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-14 |
| Status | Draft |
| Related BRD | documents/MTO-47/BRD.md |
| Related FSD | documents/MTO-47/FSD.md |

---

## 1. Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.1.20 |
| Coroutines | kotlinx.coroutines | 1.10.2 |
| Serialization | kotlinx.serialization | 1.8.1 |
| HTTP Client | Ktor Client (CIO) | 3.1.3 |
| Database | PostgreSQL + JDBC | 16+ |
| Vector DB | pgvector (via existing) | — |
| AI/LLM | LangChain4j | 1.0.0-beta4 |
| Embedding | Ollama / LMStudio / OpenAI | Multi-provider |
| DI | Koin | 4.1.0-Beta5 |
| Hashing | java.security.MessageDigest | JDK 21 |
| DateTime | kotlinx-datetime | 0.6.2 |

---

## 2. Module Structure

### 2.1 New Gradle Module: `sync-pipeline`

```
sync-pipeline/
├── build.gradle.kts
└── src/main/kotlin/com/orchestrator/mcp/sync/pipeline/
    ├── SyncOrchestrator.kt                 ← Interface
    ├── SyncOrchestratorImpl.kt             ← Main orchestration
    ├── SyncPipelineConfig.kt               ← Configuration model
    ├── crawl/
    │   ├── JiraCrawlService.kt             ← Pagination + concurrent fetch
    │   ├── TicketFetcher.kt                ← Single ticket deep fetch
    │   ├── TicketFetcherImpl.kt
    │   ├── AdfParser.kt                    ← ADF → plain text (reuse)
    │   └── ContentHasher.kt                ← SHA-256 dedup
    ├── dimension/
    │   ├── IndexDimension.kt               ← Strategy interface
    │   ├── DimensionRegistry.kt            ← Config-driven registry
    │   ├── DimensionProcessor.kt           ← Orchestrates all dimensions
    │   ├── builtin/
    │   │   ├── TicketMetadataDimension.kt
    │   │   ├── CommentDimension.kt
    │   │   ├── AttachmentDimension.kt
    │   │   ├── UserRelationDimension.kt
    │   │   └── FeatureDetectionDimension.kt
    │   └── generic/
    │       └── GenericFieldDimension.kt    ← For UI-configured dimensions
    ├── ai/
    │   ├── AiAnalysisService.kt            ← Interface
    │   ├── AiAnalysisServiceImpl.kt        ← LangChain4j impl
    │   ├── AiProviderFactory.kt            ← Multi-provider factory
    │   └── model/
    │       ├── FeatureGroup.kt
    │       └── TicketSummary.kt
    ├── model/
    │   ├── CrawledTicket.kt                ← Full ticket after fetch
    │   ├── IndexEntry.kt                   ← Generic indexed record
    │   ├── SourceRef.kt                    ← Provenance
    │   ├── DimensionConfig.kt              ← Config model
    │   ├── SyncOptions.kt
    │   ├── SyncResult.kt
    │   └── SyncProgress.kt
    ├── storage/
    │   ├── IndexWriter.kt                  ← Interface
    │   ├── BatchIndexWriter.kt             ← Buffered batch writes
    │   ├── PostgresIndexWriter.kt          ← Relational storage
    │   └── VectorIndexWriter.kt            ← Embedding + vector upsert
    ├── state/
    │   ├── SyncStateTracker.kt             ← Interface
    │   └── PostgresSyncStateTracker.kt     ← DB-backed state machine
    └── di/
        └── SyncPipelineModule.kt           ← Koin DI registration
```

### 2.2 Dependencies Between Modules

```
sync-pipeline
├── depends on: orchestrator-client (EmbeddingService interface)
├── depends on: kotlinx-coroutines
├── depends on: kotlinx-serialization
├── depends on: ktor-client-cio
├── depends on: langchain4j-core
├── depends on: langchain4j-ollama
├── depends on: langchain4j-open-ai
├── depends on: HikariCP (DB connection pool)
└── depends on: slf4j-api

orchestrator-server
├── depends on: sync-pipeline (NEW)
└── existing deps unchanged

kb-server
├── depends on: sync-pipeline (NEW)
└── existing deps unchanged
```


---

## 3. Database Schema

### 3.1 New Tables (schema: `sync`)

```sql
-- ============================================================
-- Table: sync.index_entries (unified storage for all dimensions)
-- ============================================================
CREATE SCHEMA IF NOT EXISTS sync;

CREATE TABLE sync.index_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dimension_id VARCHAR(50) NOT NULL,
    project_key VARCHAR(20) NOT NULL,
    ticket_key VARCHAR(50),
    entry_key VARCHAR(200) NOT NULL,     -- Deterministic key for upsert
    
    -- Source provenance
    source_type VARCHAR(50) NOT NULL,    -- jira_ticket, jira_comment, jira_attachment, derived
    source_path VARCHAR(500) NOT NULL,   -- jira:MTO/MTO-14/comment/12345
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    content_hash VARCHAR(64),
    derived_from JSONB,
    
    -- Flexible data
    data JSONB NOT NULL,
    
    -- Vector indexing
    vector_text TEXT,
    vector_indexed BOOLEAN DEFAULT false,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uq_sync_entry UNIQUE (dimension_id, entry_key)
);

-- Performance indexes
CREATE INDEX idx_sie_project ON sync.index_entries(project_key);
CREATE INDEX idx_sie_dimension ON sync.index_entries(dimension_id);
CREATE INDEX idx_sie_ticket ON sync.index_entries(ticket_key) WHERE ticket_key IS NOT NULL;
CREATE INDEX idx_sie_source ON sync.index_entries(source_path);
CREATE INDEX idx_sie_vector_pending ON sync.index_entries(vector_indexed) WHERE vector_text IS NOT NULL AND vector_indexed = false;
CREATE INDEX idx_sie_data_gin ON sync.index_entries USING GIN (data);
CREATE INDEX idx_sie_updated ON sync.index_entries(updated_at);

-- ============================================================
-- Table: sync.dimension_config (UI-configurable dimensions)
-- ============================================================
CREATE TABLE sync.dimension_config (
    id VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    source_type VARCHAR(50) NOT NULL,
    fields JSONB,
    index_strategy VARCHAR(50) NOT NULL,
    vector_enabled BOOLEAN DEFAULT false,
    processor_class VARCHAR(200),
    config_json JSONB,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- Table: sync.state (per-project sync state machine)
-- ============================================================
CREATE TABLE sync.state (
    project_key VARCHAR(20) PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    last_sync_at TIMESTAMPTZ,
    last_offset INT DEFAULT 0,
    total_issues INT DEFAULT 0,
    synced_issues INT DEFAULT 0,
    error_message TEXT,
    dimensions_processed JSONB,
    started_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Table: sync.users (user registry derived from sync)
-- ============================================================
CREATE TABLE sync.users (
    account_id VARCHAR(100) PRIMARY KEY,
    display_name VARCHAR(200),
    email VARCHAR(200),
    avatar_url TEXT,
    first_seen_at TIMESTAMPTZ DEFAULT NOW(),
    last_active_at TIMESTAMPTZ,
    projects JSONB DEFAULT '[]',
    total_tickets INT DEFAULT 0,
    total_comments INT DEFAULT 0
);

CREATE INDEX idx_su_name ON sync.users(display_name);
CREATE INDEX idx_su_projects ON sync.users USING GIN (projects);

-- ============================================================
-- Table: sync.features (AI-detected feature groupings)
-- ============================================================
CREATE TABLE sync.features (
    feature_id VARCHAR(100) PRIMARY KEY,
    project_key VARCHAR(20) NOT NULL,
    feature_name VARCHAR(500) NOT NULL,
    detection_method VARCHAR(50) NOT NULL,
    confidence DECIMAL(3,2) NOT NULL,
    epic_key VARCHAR(50),
    ticket_keys JSONB NOT NULL DEFAULT '[]',
    description TEXT,
    synced_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_sf_project ON sync.features(project_key);
CREATE INDEX idx_sf_tickets ON sync.features USING GIN (ticket_keys);

-- ============================================================
-- Seed: Default dimension configurations
-- ============================================================
INSERT INTO sync.dimension_config (id, display_name, enabled, source_type, index_strategy, vector_enabled, sort_order) VALUES
('ticket_metadata', 'Ticket Metadata', true, 'jira_fields', 'per_ticket', true, 1),
('comments', 'Comments Per Person', true, 'jira_comments', 'per_comment', true, 2),
('attachments', 'Attachment Metadata', true, 'jira_attachments', 'per_attachment', false, 3),
('user_relations', 'User Relationships', true, 'derived', 'per_relation', false, 4),
('feature_grouping', 'Feature Auto-Detection', true, 'ai_derived', 'per_feature', true, 5);
```

### 3.2 Performance Considerations

| Table | Expected Rows (1000 tickets) | Key Indexes |
|-------|------------------------------|-------------|
| index_entries | ~5000-10000 (multi-dimension) | GIN on data, B-tree on project+dimension |
| users | ~50-200 | B-tree on account_id |
| features | ~20-50 | GIN on ticket_keys |
| state | 1 per project | Primary key |

### 3.3 Query Patterns

```sql
-- "What did user X say?" (per-person comments)
SELECT data->>'body', data->>'created_at', ticket_key
FROM sync.index_entries
WHERE dimension_id = 'comments'
  AND data->>'author_account_id' = ?
  AND project_key = ?
ORDER BY (data->>'created_at')::timestamptz DESC;

-- "All Bugs in project MTO"
SELECT data, ticket_key
FROM sync.index_entries
WHERE dimension_id = 'ticket_metadata'
  AND project_key = 'MTO'
  AND data->>'issue_type' = 'Bug';

-- "Tickets related to feature X"
SELECT ticket_keys FROM sync.features WHERE feature_id = ?;

-- "User's involvement across tickets"
SELECT ticket_key, data->>'relation_type'
FROM sync.index_entries
WHERE dimension_id = 'user_relations'
  AND data->>'user_account_id' = ?;
```


---

## 4. Class Design

### 4.1 Core Interfaces

```kotlin
// ═══════════════════════════════════════════════════════════
// SyncOrchestrator — Entry point for both tools
// ═══════════════════════════════════════════════════════════
interface SyncOrchestrator {
    suspend fun sync(projectKey: String, options: SyncOptions = SyncOptions()): SyncResult
    suspend fun getProgress(projectKey: String): SyncProgress?
    suspend fun cancel(projectKey: String): Boolean
}

data class SyncOptions(
    val fullSync: Boolean = false,
    val batchSize: Int = 50,
    val dimensions: List<String>? = null,  // null = all enabled
    val maxConcurrentFetches: Int = 5
)

data class SyncResult(
    val projectKey: String,
    val totalTickets: Int,
    val processedTickets: Int,
    val skippedTickets: Int,
    val entriesCreated: Map<String, Int>,  // dimension_id → count
    val duration: kotlin.time.Duration,
    val status: SyncStatus
)

// ═══════════════════════════════════════════════════════════
// IndexDimension — Strategy pattern for extensible indexing
// ═══════════════════════════════════════════════════════════
interface IndexDimension {
    val dimensionId: String
    val displayName: String
    
    /** Extract entries from a single crawled ticket */
    suspend fun extract(ticket: CrawledTicket, config: DimensionConfig): List<IndexEntry>
    
    /** Post-sync processing (feature detection, aggregation) */
    suspend fun postProcess(
        projectKey: String, 
        config: DimensionConfig
    ): List<IndexEntry> = emptyList()
    
    fun supportsVector(): Boolean
}

// ═══════════════════════════════════════════════════════════
// IndexEntry — Universal indexed record
// ═══════════════════════════════════════════════════════════
data class IndexEntry(
    val id: String,                    // Deterministic UUID
    val dimensionId: String,
    val projectKey: String,
    val ticketKey: String?,
    val entryKey: String,              // Unique within dimension
    val sourceRef: SourceRef,
    val data: Map<String, String?>,    // Flexible key-value
    val vectorText: String? = null     // Text for embedding (null = skip)
)

data class SourceRef(
    val type: String,                  // jira_ticket, jira_comment, etc.
    val path: String,                  // jira:MTO/MTO-14/comment/12345
    val syncedAt: kotlinx.datetime.Instant,
    val contentHash: String? = null,
    val derivedFrom: List<String>? = null
)

// ═══════════════════════════════════════════════════════════
// CrawledTicket — Full ticket data after Jira fetch
// ═══════════════════════════════════════════════════════════
data class CrawledTicket(
    val key: String,
    val projectKey: String,
    val summary: String,
    val description: String,
    val issueType: String,
    val status: String,
    val priority: String?,
    val assignee: JiraUser?,
    val reporter: JiraUser?,
    val parentKey: String?,
    val epicKey: String?,
    val labels: List<String>,
    val components: List<String>,
    val fixVersions: List<String>,
    val storyPoints: Double?,
    val sprint: String?,
    val createdAt: kotlinx.datetime.Instant,
    val updatedAt: kotlinx.datetime.Instant,
    val resolvedAt: kotlinx.datetime.Instant?,
    val comments: List<CrawledComment>,
    val links: List<CrawledLink>,
    val attachments: List<CrawledAttachment>,
    val contentHash: String              // For change detection
)

data class JiraUser(
    val accountId: String,
    val displayName: String,
    val email: String? = null
)

data class CrawledComment(
    val commentId: String,
    val author: JiraUser,
    val body: String,
    val createdAt: kotlinx.datetime.Instant,
    val updatedAt: kotlinx.datetime.Instant?
)

data class CrawledLink(
    val type: String,
    val direction: String,             // inward | outward
    val targetKey: String
)

data class CrawledAttachment(
    val attachmentId: String,
    val filename: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val author: JiraUser?,
    val createdAt: kotlinx.datetime.Instant,
    val downloadUrl: String            // Internal use only
)
```

### 4.2 SyncOrchestratorImpl (Main Flow)

```kotlin
class SyncOrchestratorImpl(
    private val crawlService: JiraCrawlService,
    private val dimensionProcessor: DimensionProcessor,
    private val indexWriter: BatchIndexWriter,
    private val vectorWriter: VectorIndexWriter,
    private val stateTracker: SyncStateTracker,
    private val config: SyncPipelineConfig
) : SyncOrchestrator {

    override suspend fun sync(projectKey: String, options: SyncOptions): SyncResult {
        validateProjectKey(projectKey)
        stateTracker.markRunning(projectKey)
        
        val mark = TimeSource.Monotonic.markNow()
        val stats = SyncStats()
        
        try {
            val lastSyncAt = if (options.fullSync) null 
                             else stateTracker.getLastSyncAt(projectKey)
            
            // Streaming pipeline: fetch → process → write
            crawlService.crawlProject(projectKey, lastSyncAt, options)
                .chunked(config.pipeline.writeBufferSize)
                .collect { batch ->
                    processBatch(batch, options, stats)
                    stateTracker.updateProgress(projectKey, stats.processed, stats.total)
                }
            
            // Post-sync: feature detection (AI-powered)
            runPostProcessors(projectKey, options, stats)
            
            // Flush remaining vector entries
            vectorWriter.flush()
            
            stateTracker.markCompleted(projectKey)
            return stats.toResult(projectKey, mark.elapsedNow())
        } catch (e: CancellationException) {
            stateTracker.markFailed(projectKey, "Cancelled")
            throw e
        } catch (e: Exception) {
            stateTracker.markFailed(projectKey, e.message ?: "Unknown")
            throw e
        }
    }
    
    private suspend fun processBatch(
        tickets: List<CrawledTicket>, 
        options: SyncOptions, 
        stats: SyncStats
    ) {
        for (ticket in tickets) {
            val entries = dimensionProcessor.process(ticket, options.dimensions)
            indexWriter.writeBatch(entries)
            
            // Queue vector entries for batch embedding
            entries.filter { it.vectorText != null }
                .forEach { vectorWriter.queue(it) }
            
            stats.processed++
            stats.entriesByDimension.merge(entries)
        }
    }
}
```

### 4.3 JiraCrawlService (Streaming with Concurrency)

```kotlin
class JiraCrawlService(
    private val jiraClient: JiraRestClient,
    private val ticketFetcher: TicketFetcher,
    private val contentHasher: ContentHasher,
    private val config: SyncPipelineConfig
) {
    /**
     * Returns a Flow of CrawledTickets.
     * Handles pagination internally, fetches details concurrently.
     */
    fun crawlProject(
        projectKey: String,
        lastSyncAt: Instant?,
        options: SyncOptions
    ): Flow<CrawledTicket> = flow {
        val jql = buildJql(projectKey, lastSyncAt)
        var startAt = 0
        val semaphore = Semaphore(options.maxConcurrentFetches)
        
        do {
            val page = jiraClient.searchIssues(jql, SEARCH_FIELDS, startAt, options.batchSize)
            
            // Concurrent deep fetch within page
            val tickets = coroutineScope {
                page.issues.map { issue ->
                    async {
                        semaphore.withPermit {
                            ticketFetcher.fetchFull(issue)
                        }
                    }
                }.awaitAll()
            }
            
            // Emit only changed tickets (hash check)
            for (ticket in tickets) {
                if (shouldProcess(ticket)) emit(ticket)
            }
            
            startAt += options.batchSize
        } while (startAt < page.total)
    }
    
    private fun buildJql(projectKey: String, lastSyncAt: Instant?): String {
        val base = "project = $projectKey ORDER BY updated DESC"
        return if (lastSyncAt != null) {
            "project = $projectKey AND updated > '${formatJiraDate(lastSyncAt)}' ORDER BY updated DESC"
        } else base
    }
}
```

### 4.4 DimensionProcessor

```kotlin
class DimensionProcessor(
    private val registry: DimensionRegistry
) {
    /**
     * Process a ticket through all enabled dimensions concurrently.
     */
    suspend fun process(
        ticket: CrawledTicket, 
        dimensionFilter: List<String>?
    ): List<IndexEntry> = coroutineScope {
        val dimensions = registry.getEnabled(dimensionFilter)
        
        dimensions.map { (dimension, config) ->
            async {
                try {
                    dimension.extract(ticket, config)
                } catch (e: Exception) {
                    logger.warn("Dimension {} failed for {}: {}", 
                        dimension.dimensionId, ticket.key, e.message)
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }
}
```

### 4.5 Built-in Dimensions

```kotlin
// ─── CommentDimension (per-person) ───────────────────────
class CommentDimension : IndexDimension {
    override val dimensionId = "comments"
    override val displayName = "Comments Per Person"
    override fun supportsVector() = true
    
    override suspend fun extract(
        ticket: CrawledTicket, config: DimensionConfig
    ): List<IndexEntry> {
        return ticket.comments.map { comment ->
            IndexEntry(
                id = deterministicId("${ticket.key}:comment:${comment.commentId}"),
                dimensionId = dimensionId,
                projectKey = ticket.projectKey,
                ticketKey = ticket.key,
                entryKey = "${ticket.key}:${comment.commentId}",
                sourceRef = SourceRef(
                    type = "jira_comment",
                    path = "jira:${ticket.projectKey}/${ticket.key}/comment/${comment.commentId}",
                    syncedAt = Clock.System.now(),
                    contentHash = sha256(comment.body)
                ),
                data = mapOf(
                    "jira_comment_id" to comment.commentId,
                    "author_account_id" to comment.author.accountId,
                    "author_display_name" to comment.author.displayName,
                    "body" to comment.body,
                    "created_at" to comment.createdAt.toString(),
                    "updated_at" to comment.updatedAt?.toString()
                ),
                vectorText = "Comment by ${comment.author.displayName} on ${ticket.key}: ${comment.body.take(500)}"
            )
        }
    }
}

// ─── UserRelationDimension ───────────────────────────────
class UserRelationDimension : IndexDimension {
    override val dimensionId = "user_relations"
    override val displayName = "User Relationships"
    override fun supportsVector() = false
    
    override suspend fun extract(
        ticket: CrawledTicket, config: DimensionConfig
    ): List<IndexEntry> {
        val relations = mutableListOf<IndexEntry>()
        
        ticket.assignee?.let { user ->
            relations.add(buildRelation(ticket, user, "assignee"))
        }
        ticket.reporter?.let { user ->
            relations.add(buildRelation(ticket, user, "reporter"))
        }
        ticket.comments.map { it.author }.distinctBy { it.accountId }.forEach { user ->
            relations.add(buildRelation(ticket, user, "commenter"))
        }
        
        return relations
    }
    
    private fun buildRelation(
        ticket: CrawledTicket, user: JiraUser, role: String
    ): IndexEntry = IndexEntry(
        id = deterministicId("${user.accountId}:${ticket.key}:$role"),
        dimensionId = dimensionId,
        projectKey = ticket.projectKey,
        ticketKey = ticket.key,
        entryKey = "${user.accountId}:${ticket.key}:$role",
        sourceRef = SourceRef(
            type = "derived",
            path = "jira:${ticket.projectKey}/${ticket.key}/$role",
            syncedAt = Clock.System.now()
        ),
        data = mapOf(
            "user_account_id" to user.accountId,
            "user_display_name" to user.displayName,
            "relation_type" to role,
            "ticket_key" to ticket.key,
            "ticket_summary" to ticket.summary
        ),
        vectorText = null
    )
}

// ─── FeatureDetectionDimension (AI-powered, post-sync) ───
class FeatureDetectionDimension(
    private val aiService: AiAnalysisService,
    private val indexWriter: IndexWriter
) : IndexDimension {
    override val dimensionId = "feature_grouping"
    override val displayName = "Feature Auto-Detection"
    override fun supportsVector() = true
    
    override suspend fun extract(
        ticket: CrawledTicket, config: DimensionConfig
    ): List<IndexEntry> = emptyList()  // No per-ticket extraction
    
    override suspend fun postProcess(
        projectKey: String, config: DimensionConfig
    ): List<IndexEntry> {
        // Load all ticket summaries for this project
        val tickets = indexWriter.getTicketSummaries(projectKey)
        
        // AI analysis
        val features = aiService.detectFeatures(tickets)
        
        return features.map { feature ->
            IndexEntry(
                id = deterministicId("feature:${feature.featureId}"),
                dimensionId = dimensionId,
                projectKey = projectKey,
                ticketKey = null,
                entryKey = "feature:${feature.featureId}",
                sourceRef = SourceRef(
                    type = "ai_derived",
                    path = "derived:feature/${feature.featureId}",
                    syncedAt = Clock.System.now(),
                    derivedFrom = feature.ticketKeys.map { "jira:$projectKey/$it" }
                ),
                data = mapOf(
                    "feature_id" to feature.featureId,
                    "feature_name" to feature.featureName,
                    "detection_method" to feature.detectionMethod,
                    "confidence" to feature.confidence.toString(),
                    "ticket_keys" to feature.ticketKeys.joinToString(","),
                    "epic_key" to feature.epicKey
                ),
                vectorText = "Feature: ${feature.featureName}. " +
                    "Tickets: ${feature.ticketKeys.joinToString(", ")}"
            )
        }
    }
}
```

### 4.6 AI Analysis Service

```kotlin
class AiAnalysisServiceImpl(
    private val providerFactory: AiProviderFactory,
    private val config: SyncPipelineConfig
) : AiAnalysisService {

    override suspend fun detectFeatures(tickets: List<TicketSummary>): List<FeatureGroup> {
        if (tickets.isEmpty()) return emptyList()
        
        // Step 1: Epic-based grouping (always, no AI needed)
        val epicGroups = groupByEpic(tickets)
        
        // Step 2: AI enrichment (if enabled and provider available)
        return if (config.featureDetection.strategy != "epic_only" && isHealthy()) {
            enrichWithAi(epicGroups, tickets)
        } else {
            epicGroups
        }
    }
    
    private fun groupByEpic(tickets: List<TicketSummary>): List<FeatureGroup> {
        return tickets.filter { it.epicKey != null }
            .groupBy { it.epicKey!! }
            .map { (epicKey, members) ->
                val epicTicket = tickets.find { it.key == epicKey }
                FeatureGroup(
                    featureId = "epic-$epicKey",
                    featureName = epicTicket?.summary ?: "Feature $epicKey",
                    ticketKeys = members.map { it.key },
                    detectionMethod = "epic_hierarchy",
                    confidence = 1.0,
                    epicKey = epicKey
                )
            }
    }
    
    private suspend fun enrichWithAi(
        epicGroups: List<FeatureGroup>, 
        tickets: List<TicketSummary>
    ): List<FeatureGroup> {
        val model = providerFactory.createChatModel(config.ai)
        val prompt = buildFeatureDetectionPrompt(tickets, epicGroups)
        
        return try {
            val response = withTimeout(config.ai.timeoutSeconds * 1000L) {
                model.generate(prompt)
            }
            parseFeatureResponse(response.content().text(), epicGroups)
        } catch (e: Exception) {
            logger.warn("AI feature detection failed, using epic-only: {}", e.message)
            epicGroups  // Graceful degradation
        }
    }
    
    override suspend fun isHealthy(): Boolean {
        return try {
            providerFactory.createChatModel(config.ai)
            true
        } catch (e: Exception) {
            false
        }
    }
}
```

### 4.7 VectorIndexWriter (Batch Embedding)

```kotlin
class VectorIndexWriter(
    private val embeddingService: EmbeddingService,
    private val vectorClient: VectorDbClient,
    private val config: SyncPipelineConfig
) {
    private val buffer = mutableListOf<IndexEntry>()
    private val mutex = Mutex()
    
    suspend fun queue(entry: IndexEntry) {
        mutex.withLock {
            buffer.add(entry)
            if (buffer.size >= config.embedding.batchSize) {
                flushInternal()
            }
        }
    }
    
    suspend fun flush() {
        mutex.withLock { flushInternal() }
    }
    
    private suspend fun flushInternal() {
        if (buffer.isEmpty()) return
        val batch = buffer.toList()
        buffer.clear()
        
        try {
            // Batch embedding generation
            val texts = batch.map { it.vectorText!! }
            val embeddings = embeddingService.generateEmbeddings(texts)
            
            // Batch vector upsert
            val points = batch.zip(embeddings).map { (entry, embedding) ->
                VectorPoint(
                    id = entry.id,
                    vector = embedding,
                    payload = mapOf(
                        "dimension_id" to entry.dimensionId,
                        "project_key" to entry.projectKey,
                        "ticket_key" to (entry.ticketKey ?: ""),
                        "source_path" to entry.sourceRef.path,
                        "text_preview" to (entry.vectorText?.take(200) ?: "")
                    )
                )
            }
            vectorClient.upsert(config.vector.collection, points)
            
            logger.debug("Flushed {} vector entries", batch.size)
        } catch (e: Exception) {
            logger.error("Vector flush failed for {} entries: {}", batch.size, e.message)
            // Mark entries as not indexed (will retry on next sync)
        }
    }
}
```


---

## 5. Integration Points

### 5.1 orchestrator-server Integration

```kotlin
// SyncToolHandler.kt — MODIFIED to use SyncOrchestrator
class SyncToolHandler(private val syncOrchestrator: SyncOrchestrator) {
    suspend fun handle(arguments: JsonObject?): CallToolResult {
        val projectKey = arguments?.get("projectKey")?.jsonPrimitive?.content
            ?: return errorResult("projectKey is required")
        val fullSync = arguments["fullSync"]?.jsonPrimitive?.boolean ?: false
        
        // Launch async (same as before), but now uses shared pipeline
        CoroutineScope(Dispatchers.Default).launch {
            syncOrchestrator.sync(projectKey, SyncOptions(fullSync = fullSync))
        }
        
        val progress = syncOrchestrator.getProgress(projectKey)
        return successResult(buildResponse(projectKey, fullSync, progress))
    }
}
```

### 5.2 kb-server Integration

```kotlin
// SyncTaskHandler.kt — REPLACED stub with real implementation
class SyncTaskHandler(
    private val syncOrchestrator: SyncOrchestrator
) : TaskHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun taskType(): String = "sync"

    override suspend fun handle(task: QueueTask) {
        val projectKey = task.payload["project_key"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("project_key required")
        val fullSync = task.payload["full_sync"]?.jsonPrimitive?.content?.toBoolean() ?: false

        logger.info("Sync task started for project={} fullSync={}", projectKey, fullSync)
        
        val result = syncOrchestrator.sync(projectKey, SyncOptions(fullSync = fullSync))
        
        logger.info("Sync completed for {}: {} processed, {} entries created",
            projectKey, result.processedTickets, result.entriesCreated.values.sum())
    }
}
```

### 5.3 DI Module Registration

```kotlin
// SyncPipelineModule.kt — Koin module for shared pipeline
val syncPipelineModule = module {
    // Config
    single { SyncPipelineConfigLoader.load() }
    
    // Crawl layer
    single<TicketFetcher> { TicketFetcherImpl(get(), get()) }
    single { JiraCrawlService(get(), get(), get(), get()) }
    single { ContentHasher() }
    single { AdfParser() }
    
    // Dimension layer
    single { DimensionRegistry(get()) }
    single { DimensionProcessor(get()) }
    single<IndexDimension>(named("ticket_metadata")) { TicketMetadataDimension() }
    single<IndexDimension>(named("comments")) { CommentDimension() }
    single<IndexDimension>(named("attachments")) { AttachmentDimension() }
    single<IndexDimension>(named("user_relations")) { UserRelationDimension() }
    single<IndexDimension>(named("feature_grouping")) { FeatureDetectionDimension(get(), get()) }
    
    // AI layer
    single { AiProviderFactory() }
    single<AiAnalysisService> { AiAnalysisServiceImpl(get(), get()) }
    
    // Storage layer
    single<IndexWriter> { PostgresIndexWriter(get()) }
    single { BatchIndexWriter(get(), get()) }
    single { VectorIndexWriter(get(), get(), get()) }
    
    // State layer
    single<SyncStateTracker> { PostgresSyncStateTracker(get()) }
    
    // Orchestrator
    single<SyncOrchestrator> { SyncOrchestratorImpl(get(), get(), get(), get(), get(), get()) }
}
```

---

## 6. AI Provider Factory

```kotlin
class AiProviderFactory {
    
    fun createChatModel(config: AiConfig): ChatLanguageModel {
        return when (config.provider.lowercase()) {
            "ollama" -> OllamaChatModel.builder()
                .baseUrl(config.baseUrl)
                .modelName(config.model)
                .temperature(config.temperature)
                .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
                .build()
                
            "lmstudio" -> OpenAiChatModel.builder()
                .baseUrl(config.baseUrl)  // LMStudio uses OpenAI-compatible API
                .modelName(config.model)
                .apiKey("lm-studio")      // LMStudio doesn't need real key
                .temperature(config.temperature)
                .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
                .build()
                
            "openai" -> OpenAiChatModel.builder()
                .apiKey(config.apiKey ?: System.getenv("OPENAI_API_KEY") ?: "")
                .modelName(config.model)
                .temperature(config.temperature)
                .maxTokens(config.maxTokens)
                .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
                .build()
                
            "azure" -> AzureOpenAiChatModel.builder()
                .apiKey(config.apiKey ?: System.getenv("AZURE_OPENAI_KEY") ?: "")
                .deploymentName(config.model)
                .temperature(config.temperature)
                .maxTokens(config.maxTokens)
                .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
                .apply { config.baseUrl?.let { endpoint(it) } }
                .build()
                
            else -> throw IllegalArgumentException(
                "Unsupported AI provider: ${config.provider}. " +
                "Supported: ollama, lmstudio, openai, azure"
            )
        }
    }
    
    fun createEmbeddingService(config: EmbeddingConfig, httpClient: HttpClient): EmbeddingService {
        return when (config.provider.lowercase()) {
            "ollama" -> OllamaEmbeddingService(httpClient, config.baseUrl, config.model, config.dimensions)
            "lmstudio" -> LmStudioEmbeddingService(httpClient, config.baseUrl, config.model, config.dimensions)
            "openai" -> OpenAiEmbeddingService(httpClient, config.apiKey ?: "", config.model, config.dimensions)
            else -> throw IllegalArgumentException("Unsupported embedding provider: ${config.provider}")
        }
    }
}
```

---

## 7. Gradle Configuration

### 7.1 sync-pipeline/build.gradle.kts

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // Project modules
    implementation(project(":orchestrator-client"))  // EmbeddingService, VectorDbClient
    
    // Kotlin
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    
    // HTTP Client (for Jira API)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    
    // AI/LLM
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j.ollama)
    implementation(libs.langchain4j.open.ai)
    
    // Database
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    
    // DI
    implementation(libs.koin.core)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}
```

---

## 8. Migration Plan

### 8.1 Phase 1: Create Module + Schema (This PR)

1. Create `sync-pipeline` Gradle module
2. Add DB migration for `sync` schema
3. Implement core interfaces + models
4. Implement `SyncOrchestratorImpl` with streaming pipeline
5. Implement 5 built-in dimensions
6. Implement `AiProviderFactory` + `AiAnalysisServiceImpl`
7. Implement storage layer (Postgres + Vector batch writer)

### 8.2 Phase 2: Wire Into Existing Tools

1. Update `orchestrator-server` DI to include `syncPipelineModule`
2. Modify `SyncToolHandler` to use `SyncOrchestrator`
3. Update `kb-server` `SyncTaskHandler` to use `SyncOrchestrator`
4. Update `kb-server` DI module

### 8.3 Phase 3: Deprecate Old Pipeline

1. Mark old `TicketCrawlerImpl` as `@Deprecated`
2. Mark old `KBIngestorImpl` as `@Deprecated`
3. Run full re-sync to populate new schema
4. Verify data consistency

---

## 9. Implementation Checklist

| # | Task | File | Priority |
|---|------|------|----------|
| 1 | Create sync-pipeline module | build.gradle.kts, settings.gradle.kts | High |
| 2 | Core models | model/*.kt | High |
| 3 | SyncPipelineConfig | SyncPipelineConfig.kt | High |
| 4 | ContentHasher | crawl/ContentHasher.kt | High |
| 5 | AdfParser (copy+adapt) | crawl/AdfParser.kt | High |
| 6 | TicketFetcher | crawl/TicketFetcher*.kt | High |
| 7 | JiraCrawlService | crawl/JiraCrawlService.kt | High |
| 8 | IndexDimension interface | dimension/IndexDimension.kt | High |
| 9 | DimensionRegistry | dimension/DimensionRegistry.kt | High |
| 10 | DimensionProcessor | dimension/DimensionProcessor.kt | High |
| 11 | TicketMetadataDimension | dimension/builtin/*.kt | High |
| 12 | CommentDimension | dimension/builtin/*.kt | High |
| 13 | AttachmentDimension | dimension/builtin/*.kt | High |
| 14 | UserRelationDimension | dimension/builtin/*.kt | High |
| 15 | FeatureDetectionDimension | dimension/builtin/*.kt | Medium |
| 16 | AiProviderFactory | ai/AiProviderFactory.kt | Medium |
| 17 | AiAnalysisServiceImpl | ai/AiAnalysisServiceImpl.kt | Medium |
| 18 | PostgresIndexWriter | storage/PostgresIndexWriter.kt | High |
| 19 | VectorIndexWriter | storage/VectorIndexWriter.kt | High |
| 20 | BatchIndexWriter | storage/BatchIndexWriter.kt | High |
| 21 | PostgresSyncStateTracker | state/PostgresSyncStateTracker.kt | High |
| 22 | SyncOrchestratorImpl | SyncOrchestratorImpl.kt | High |
| 23 | SyncPipelineModule (Koin) | di/SyncPipelineModule.kt | High |
| 24 | DB Migration SQL | resources/db/migration/ | High |
| 25 | Wire orchestrator-server | SyncToolHandler modification | High |
| 26 | Wire kb-server | SyncTaskHandler replacement | High |
| 27 | GenericFieldDimension | dimension/generic/*.kt | Medium |
| 28 | Dimension Config REST API | (kb-server HTTP routes) | Medium |
| 29 | Unit tests | test/**/*.kt | High |
| 30 | Integration tests | test/**/*IntegrationTest.kt | Medium |
