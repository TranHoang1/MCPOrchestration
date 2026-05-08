# Technical Design Document (TDD)

## MCPOrchestration — MTO-19: Attachment Processor – Background Queue Worker

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-19 |
| Title | Attachment Processor – Background Queue Worker |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-19.docx |
| Related FSD | FSD-v1-MTO-19.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | SA Agent | Initial TDD |

---

## 1. Introduction

### 1.1 Purpose

This TDD specifies the technical implementation of the **AttachmentProcessor** — a background coroutine worker that downloads Jira attachments, extracts text content, and ingests into the Knowledge Base vector database.

### 1.2 Scope

- Background coroutine lifecycle management
- File download with semaphore-based concurrency
- Text extraction (PDF via PDFBox, DOCX via Apache POI, plain text)
- KB ingestion via existing EmbeddingService + VectorDbClient
- Queue state management via Exposed ORM
- Configuration and graceful shutdown

### 1.3 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.3.20 |
| Platform | JVM | 21 |
| Coroutines | kotlinx.coroutines | 1.10.2 |
| PDF Extraction | Apache PDFBox | 3.0.4 |
| DOCX Extraction | Apache POI | 5.3.0 |
| Database ORM | Exposed | 0.61.0 |
| Vector DB | Qdrant | 1.9+ |
| Embeddings | OpenAI text-embedding-3-small | — |

### 1.4 Design Principles

- **Background-first** — runs as daemon coroutine, no blocking
- **Fault-tolerant** — individual item failures don't crash the worker
- **Idempotent** — re-processing a 'pending' item is safe
- **Resource-bounded** — semaphore limits concurrent downloads

### 1.5 Constraints

- Must use existing EmbeddingService and VectorDbClient from the project
- Must use existing JiraRestClient for downloads
- Must integrate with Koin DI
- File content processed in-memory (no temp files on disk)

---

## 2. System Architecture

### 2.1 Architecture Overview

The AttachmentProcessor is a new package within MCPOrchestration that runs as a background coroutine alongside the MCP server.

### 2.2 Component Diagram

| Component | Responsibility | Technology |
|-----------|---------------|------------|
| AttachmentProcessor | Orchestrates polling loop and batch processing | Kotlin Coroutines |
| QueueManager | CRUD operations on jira_attachment_queue | Exposed ORM |
| AttachmentDownloader | Downloads files from Jira with rate limiting | Ktor HttpClient |
| TextExtractor | Extracts text based on MIME type | PDFBox, POI |
| KBIngestor | Generates embeddings and stores in vector DB | EmbeddingService + VectorDbClient |

### 2.3 Communication Patterns

| From | To | Protocol | Pattern |
|------|----|----------|---------|
| AttachmentProcessor | QueueManager | In-process | Suspend function |
| AttachmentProcessor | AttachmentDownloader | In-process | Suspend + Semaphore |
| AttachmentDownloader | Jira API | HTTPS | Streaming download |
| TextExtractor | PDFBox/POI | In-process | Blocking (wrapped in Dispatchers.IO) |
| KBIngestor | OpenAI API | HTTPS | Embedding generation |
| KBIngestor | Qdrant | HTTP | Vector upsert |

---

## 3. API Design

### 3.1 Internal API

```kotlin
interface AttachmentProcessor {
    suspend fun start()       // Start background processing
    suspend fun stop()        // Graceful shutdown
    fun isRunning(): Boolean  // Check if processor is active
    fun getStats(): ProcessorStats  // Queue statistics
}

data class ProcessorStats(
    val pending: Int,
    val processing: Int,
    val completed: Int,
    val failed: Int,
    val skipped: Int
)
```

---

## 4. Database Design

### 4.1 Table: jira_attachment_queue (from MTO-15)

```sql
CREATE TABLE jira_attachment_queue (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_key     VARCHAR(20) NOT NULL,
    filename      VARCHAR(500) NOT NULL,
    mime_type     VARCHAR(100) NOT NULL,
    download_url  TEXT NOT NULL,
    file_size     BIGINT,
    status        VARCHAR(20) NOT NULL DEFAULT 'pending',
    error_message TEXT,
    retry_count   INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at  TIMESTAMPTZ
);

CREATE INDEX idx_attachment_queue_status ON jira_attachment_queue(status, created_at);
CREATE INDEX idx_attachment_queue_issue ON jira_attachment_queue(issue_key);
```

### 4.2 Query Patterns

| Operation | Query | Performance |
|-----------|-------|-------------|
| Dequeue batch | `SELECT ... WHERE status='pending' ORDER BY created_at LIMIT 5 FOR UPDATE SKIP LOCKED` | < 5ms |
| Mark processing | `UPDATE ... SET status='processing' WHERE id IN (...)` | < 5ms |
| Mark completed | `UPDATE ... SET status='completed', processed_at=NOW() WHERE id=?` | < 1ms |
| Mark failed | `UPDATE ... SET status='failed', error_message=?, retry_count=retry_count+1 WHERE id=?` | < 1ms |
| Reset on shutdown | `UPDATE ... SET status='pending' WHERE status='processing'` | < 5ms |

---

## 5. Class / Module Design

### 5.1 Package Structure

```
com.orchestrator.mcp/
└── attachment/
    ├── AttachmentProcessor.kt          # Interface
    ├── AttachmentProcessorImpl.kt      # Main loop implementation
    ├── QueueManager.kt                 # Queue CRUD operations
    ├── AttachmentDownloader.kt         # Download with semaphore
    ├── TextExtractor.kt                # Strategy-based extraction
    ├── extractors/
    │   ├── PdfTextExtractor.kt         # PDFBox implementation
    │   ├── DocxTextExtractor.kt        # Apache POI implementation
    │   └── PlainTextExtractor.kt       # Direct read
    ├── KBIngestor.kt                   # Embedding + vector store
    ├── config/
    │   └── AttachmentProcessorConfig.kt # Configuration data class
    ├── model/
    │   ├── AttachmentQueueItem.kt      # Domain model
    │   ├── ProcessorStats.kt           # Statistics DTO
    │   └── AttachmentStatus.kt         # Status enum
    └── di/
        └── AttachmentModule.kt         # Koin DI module
```

### 5.2 Key Implementation

```kotlin
class AttachmentProcessorImpl(
    private val queueManager: QueueManager,
    private val downloader: AttachmentDownloader,
    private val textExtractor: TextExtractor,
    private val kbIngestor: KBIngestor,
    private val config: AttachmentProcessorConfig
) : AttachmentProcessor {

    private var job: Job? = null
    private val downloadSemaphore = Semaphore(config.maxConcurrentDownloads)

    override suspend fun start() {
        if (!config.enabled) return
        job = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runProcessingLoop()
        }
    }

    private suspend fun runProcessingLoop() {
        var backoff = config.pollInterval
        while (isActive) {
            val batch = queueManager.dequeueBatch(config.batchSize)
            if (batch.isEmpty()) {
                delay(backoff)
                backoff = minOf(backoff * 2, config.maxBackoff)
                continue
            }
            backoff = config.pollInterval
            processBatch(batch)
        }
    }

    override suspend fun stop() {
        job?.let { j ->
            j.cancel()
            withTimeoutOrNull(config.shutdownTimeout) { j.join() }
            queueManager.resetProcessingItems()
        }
    }
}
```

### 5.3 TextExtractor (Strategy Pattern)

```kotlin
class TextExtractor(private val extractors: Map<String, ContentExtractor>) {
    suspend fun extract(bytes: ByteArray, mimeType: String): String {
        val extractor = extractors[mimeType]
            ?: throw UnsupportedMimeTypeException(mimeType)
        return withContext(Dispatchers.IO) {
            extractor.extract(bytes)
        }
    }
}

interface ContentExtractor {
    fun extract(bytes: ByteArray): String
}
```

### 5.4 Design Patterns

| Pattern | Where Used | Rationale |
|---------|-----------|-----------|
| Strategy | TextExtractor (MIME-based routing) | Clean extension for new formats |
| Supervisor | Processing loop (SupervisorJob) | Item failure isolation |
| Semaphore | Download concurrency | Rate limit protection |
| FOR UPDATE SKIP LOCKED | Queue dequeue | Prevent duplicate processing |

---

## 6. Integration Design

### 6.1 Jira REST API (Attachment Download)

| Attribute | Value |
|-----------|-------|
| Method | GET |
| URL | From attachment metadata (content URL) |
| Auth | Basic Auth (email + API token) |
| Timeout | 60 seconds |
| Retry | 3 times, exponential backoff (1s, 2s, 4s) |
| Max Size | 50MB (reject larger) |

### 6.2 Qdrant Vector DB

| Attribute | Value |
|-----------|-------|
| Collection | mcp_tools (reuse existing) |
| Operation | Upsert point with payload |
| Vector | 768 dimensions (text-embedding-3-small) |
| Payload | title, content_preview, tags, issue_key, mime_type |

---

## 7. Security Design

| Data | Protection |
|------|------------|
| Download URLs | Not logged (may contain auth tokens) |
| File content | In-memory only, not persisted to disk |
| API credentials | Environment variables |
| Extracted text | Stored in Qdrant (internal network only) |

---

## 8. Performance & Scalability

| Metric | Target |
|--------|--------|
| Batch processing | < 60s for 5 items |
| Download concurrency | 3 parallel (configurable) |
| Extraction (PDF 10 pages) | < 5s |
| Extraction (DOCX) | < 2s |
| KB ingestion per item | < 3s (embedding + upsert) |
| Memory per item | < 100MB (50MB file + processing overhead) |

---

## 9. Monitoring & Observability

| Log Event | Level | Fields |
|-----------|-------|--------|
| Processor started | INFO | enabled, batchSize, concurrency |
| Batch dequeued | DEBUG | batchSize, items |
| Download started | DEBUG | issueKey, filename, size |
| Download completed | DEBUG | issueKey, filename, duration |
| Extraction completed | DEBUG | issueKey, mimeType, textLength |
| Ingestion completed | INFO | issueKey, filename |
| Item skipped | WARN | issueKey, filename, reason |
| Item failed | ERROR | issueKey, filename, error, retryCount |
| Processor shutdown | INFO | pendingReset count |

---

## 10. Deployment Considerations

### 10.1 Dependencies to Add (build.gradle.kts)

```kotlin
implementation("org.apache.pdfbox:pdfbox:3.0.4")
implementation("org.apache.poi:poi-ooxml:5.3.0")
```

### 10.2 Configuration

```yaml
attachmentProcessor:
  enabled: true
  batchSize: 5
  pollInterval: 30s
  maxBackoff: 300s
  maxConcurrentDownloads: 3
  maxRetries: 3
  maxFileSize: 52428800
  shutdownTimeout: 60s
  supportedMimeTypes:
    - application/pdf
    - application/vnd.openxmlformats-officedocument.wordprocessingml.document
    - text/plain
    - text/markdown
```

### 10.3 Feature Flag

| Flag | Default | Description |
|------|---------|-------------|
| attachmentProcessor.enabled | true | Enable/disable processor |

---

## 11. Implementation Checklist

| # | Task | File | Priority |
|---|------|------|----------|
| 1 | AttachmentProcessorConfig | attachment/config/AttachmentProcessorConfig.kt | High |
| 2 | Domain models | attachment/model/*.kt | High |
| 3 | QueueManager (Exposed) | attachment/QueueManager.kt | High |
| 4 | PdfTextExtractor | attachment/extractors/PdfTextExtractor.kt | High |
| 5 | DocxTextExtractor | attachment/extractors/DocxTextExtractor.kt | High |
| 6 | PlainTextExtractor | attachment/extractors/PlainTextExtractor.kt | High |
| 7 | TextExtractor (strategy) | attachment/TextExtractor.kt | High |
| 8 | AttachmentDownloader | attachment/AttachmentDownloader.kt | High |
| 9 | KBIngestor | attachment/KBIngestor.kt | High |
| 10 | AttachmentProcessorImpl | attachment/AttachmentProcessorImpl.kt | High |
| 11 | AttachmentModule (Koin) | attachment/di/AttachmentModule.kt | High |
| 12 | Add PDFBox + POI deps | build.gradle.kts | High |
| 13 | Unit tests | test/.../attachment/*.kt | High |
| 14 | Integration test | test/.../attachment/it/*.kt | Medium |

---

## 12. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Architecture | [architecture.png](diagrams/architecture.png) | [architecture.drawio](diagrams/architecture.drawio) |
| 2 | Component | [component.png](diagrams/component.png) | [component.drawio](diagrams/component.drawio) |
| 3 | Class Diagram | [class-diagram.png](diagrams/class-diagram.png) | [class-diagram.drawio](diagrams/class-diagram.drawio) |
| 4 | Sequence - Process | [api-sequence-process.png](diagrams/api-sequence-process.png) | [api-sequence-process.drawio](diagrams/api-sequence-process.drawio) |
| 5 | State - Queue Item | [state-queue-item.png](diagrams/state-queue-item.png) | [state-queue-item.drawio](diagrams/state-queue-item.drawio) |
