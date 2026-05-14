# Module: sync-pipeline

**Last Updated:** 2026-07-06
**Ticket:** MTO-47 — Unified Sync Pipeline — Multi-Dimensional Jira Indexing

## Purpose

Shared library module providing the foundation for multi-dimensional Jira indexing.
Used by both `orchestrator-server` and `kb-server` to unify sync behavior.

## Dependencies

- `orchestrator-client` (for `EmbeddingService`, `VectorDbClient`)
- `kotlinx-coroutines`
- `kotlinx-serialization-json`
- `kotlinx-datetime`
- `ktor-client-cio`
- `koin-core`
- `hikaricp` + `postgresql`

## Package Structure

| Package | Purpose |
|---------|---------|
| `com.orchestrator.mcp.sync.pipeline` | Core interface (`SyncOrchestrator`) |
| `com.orchestrator.mcp.sync.pipeline.model` | Data classes (CrawledTicket, IndexEntry, etc.) |
| `com.orchestrator.mcp.sync.pipeline.dimension` | Strategy interface (`IndexDimension`) |
| `com.orchestrator.mcp.sync.pipeline.storage` | Storage interfaces (IndexWriter, VectorIndexWriter) |
| `com.orchestrator.mcp.sync.pipeline.state` | State machine interface (SyncStateTracker) |
| `com.orchestrator.mcp.sync.pipeline.ai` | AI analysis interface + models |
| `com.orchestrator.mcp.sync.pipeline.config` | Configuration data classes |

## Key Interfaces

### SyncOrchestrator
- `sync(projectKey, options)` → `SyncResult`
- `getProgress(projectKey)` → `SyncProgress?`
- `cancel(projectKey)` → `Boolean`

### IndexDimension (Strategy Pattern)
- `dimensionId: String`
- `displayName: String`
- `extract(ticket, config)` → `List<IndexEntry>`
- `postProcess(projectKey, config)` → `List<IndexEntry>`
- `supportsVector()` → `Boolean`

### IndexWriter
- `writeBatch(entries)`
- `deleteByDimension(dimensionId, projectKey)`
- `getTicketSummaries(projectKey)`

### VectorIndexWriter
- `queue(entry)`
- `flush()`
- `pendingCount()`

### SyncStateTracker
- `markRunning/markCompleted/markFailed/markCancelled`
- `updateProgress(projectKey, synced, total)`
- `getLastSyncAt(projectKey)`
- `getProgress(projectKey)`
- `isRunning(projectKey)`

### AiAnalysisService
- `detectFeatures(tickets)` → `List<FeatureGroup>`
- `summarizeFeature(feature)` → `String`
- `isHealthy()` → `Boolean`

## Key Models

| Model | Description |
|-------|-------------|
| `CrawledTicket` | Full ticket data after Jira fetch (key, summary, description, comments, links, etc.) |
| `IndexEntry` | Universal indexed record with flexible `data: Map<String, String?>` |
| `SourceRef` | Provenance tracking (type, path, syncedAt, contentHash, derivedFrom) |
| `DimensionConfig` | Per-dimension configuration (loaded from DB) |
| `SyncOptions` | Sync behavior options (fullSync, batchSize, dimensions filter) |
| `SyncResult` | Sync completion result (counts, duration, status) |
| `SyncProgress` | Real-time progress snapshot |
| `SyncStatus` | Enum: IDLE, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED |
| `FeatureGroup` | AI-detected feature grouping |
| `TicketSummary` | Lightweight ticket data for AI analysis |

## Implementation Status

- [x] Module structure created (Phase 5, Step 1)
- [ ] Implementations (SyncOrchestratorImpl, dimensions, etc.) — Step 2
- [ ] Database migration SQL — Step 2
- [ ] DI module (Koin) — Step 2
- [ ] Tests — Step 3
