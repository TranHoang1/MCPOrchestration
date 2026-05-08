# Functional Specification Document (FSD)

## MCPOrchestration — MTO-19: Attachment Processor – Background Queue Worker

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-19 |
| Title | Attachment Processor – Background Queue Worker |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-19.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | BA Agent | Initial FSD |
| 1.0 | 2026-05-08 | TA Agent | Technical enrichment — API contracts, pseudocode, integration specs |

---

## 1. Introduction

### 1.1 Purpose

This FSD specifies the functional behavior of the **AttachmentProcessor** — a background coroutine worker that processes Jira attachments from a queue, extracts text content, and ingests it into the Knowledge Base vector database for semantic search.

### 1.2 Scope

- Background queue polling with configurable batch size and interval
- File download from Jira with rate limiting
- Text extraction from PDF (PDFBox), DOCX (Apache POI), and plain text files
- Vector embedding generation and KB ingestion
- Error handling with retry logic
- Graceful shutdown

### 1.3 Definitions & Acronyms

| Term | Definition |
|------|------------|
| KB | Knowledge Base — Qdrant vector database |
| Embedding | Vector representation of text (OpenAI text-embedding-3-small) |
| MIME Type | Media type identifier (e.g., application/pdf) |
| Backoff | Progressively increasing wait time between retries |
| Semaphore | Concurrency control limiting parallel operations |

---

## 2. System Overview

### 2.1 System Context

The AttachmentProcessor runs as a background coroutine within the MCPOrchestration application. It reads from the attachment queue (populated by TicketCrawler MTO-18), downloads files from Jira, extracts text, and ingests into Qdrant.

### 2.2 System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    MCPOrchestration App                       │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │           attachment/ package (NEW)                   │    │
│  │                                                      │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────┐  │    │
│  │  │ Attachment   │→ │  Downloader  │→ │  Text    │  │    │
│  │  │ Processor    │  │ (Semaphore)  │  │ Extractor│  │    │
│  │  └──────────────┘  └──────────────┘  └──────────┘  │    │
│  │         ↕                                    ↓       │    │
│  │  ┌──────────────┐                   ┌──────────────┐│    │
│  │  │ Queue Manager│                   │ KB Ingestor  ││    │
│  │  └──────────────┘                   └──────────────┘│    │
│  └─────────────────────────────────────────────────────┘    │
│         ↕                    ↕                ↕              │
│  ┌──────────────┐    ┌──────────────┐  ┌──────────────┐    │
│  │  PostgreSQL   │    │  Jira API    │  │   Qdrant     │    │
│  │  (Queue DB)   │    │  (Download)  │  │  (Vector DB) │    │
│  └──────────────┘    └──────────────┘  └──────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Functional Requirements

### 3.1 Feature: Background Queue Processing

#### 3.1.1 Use Case

**Use Case ID:** UC-01
**Actor:** System (automatic)
**Preconditions:** Application started, processor enabled

**Main Flow:**

| Step | System | Description |
|------|--------|-------------|
| 1 | AttachmentProcessor | Starts background coroutine loop |
| 2 | AttachmentProcessor | Queries queue: SELECT WHERE status='pending' ORDER BY created_at LIMIT batchSize |
| 3 | AttachmentProcessor | If batch empty → exponential backoff, goto Step 2 |
| 4 | AttachmentProcessor | Marks batch items as 'processing' |
| 5 | AttachmentProcessor | For each item: download → extract → ingest (concurrent) |
| 6 | AttachmentProcessor | Marks completed items as 'completed' |
| 7 | AttachmentProcessor | Goto Step 2 |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-01 | Queue empty | Exponential backoff: 30s → 60s → 120s → 240s → 300s max |
| AF-02 | Items found after backoff | Reset backoff to base interval (30s) |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-01 | Download fails (network) | Retry up to maxRetries with exponential backoff |
| EF-02 | Download 404 | Mark 'failed' immediately, no retry |
| EF-03 | Extract fails | Mark 'failed' with error message, no retry |
| EF-04 | KB ingest fails | Retry up to maxRetries |
| EF-05 | All retries exhausted | Mark 'failed' with final error message |

#### 3.1.2 Business Rules

| Rule ID | Rule |
|---------|------|
| BR-01 | Batch size configurable, default 5 |
| BR-02 | Poll interval configurable, default 30s |
| BR-03 | Backoff: 30s → 60s → 120s → 240s → 300s (max) |
| BR-04 | Reset backoff when items found |
| BR-05 | Mark 'processing' before starting work (prevent duplicate processing) |

---

### 3.2 Feature: File Download

#### 3.2.1 Use Case

**Use Case ID:** UC-02
**Actor:** System

**Main Flow:**

| Step | System | Description |
|------|--------|-------------|
| 1 | Downloader | Acquires semaphore permit |
| 2 | Downloader | Calls JiraRestClient.downloadAttachment(url) |
| 3 | Downloader | Streams response to byte array |
| 4 | Downloader | Releases semaphore permit |
| 5 | Downloader | Returns downloaded bytes |

#### 3.2.2 Business Rules

| Rule ID | Rule |
|---------|------|
| BR-06 | Max concurrent downloads: configurable, default 3 (Semaphore) |
| BR-07 | Download timeout: 60 seconds |
| BR-08 | Max file size: 50MB (reject larger files) |
| BR-09 | Retry on network error: max 3 times, backoff 1s/2s/4s |
| BR-10 | No retry on 404 (file doesn't exist) |

---

### 3.3 Feature: Text Extraction

#### 3.3.1 Use Case

**Use Case ID:** UC-03
**Actor:** System

**Main Flow:**

| Step | System | Description |
|------|--------|-------------|
| 1 | TextExtractor | Determines extraction strategy from MIME type |
| 2 | TextExtractor | Invokes appropriate extractor |
| 3 | TextExtractor | Returns extracted text |

#### 3.3.2 Extraction Strategies

| MIME Type | Strategy | Library | Notes |
|-----------|----------|---------|-------|
| application/pdf | PDFBox | Apache PDFBox 3.x | All pages, handle encrypted |
| application/vnd.openxmlformats-officedocument.wordprocessingml.document | POI | Apache POI 5.x | Paragraphs + tables |
| text/plain | Direct read | Kotlin stdlib | UTF-8, fallback ISO-8859-1 |
| text/markdown | Direct read | Kotlin stdlib | UTF-8, fallback ISO-8859-1 |
| image/* | Skip | N/A | Mark 'skipped': "OCR not supported" |
| Other | Skip | N/A | Mark 'skipped': "Unsupported MIME type" |

#### 3.3.3 Business Rules

| Rule ID | Rule |
|---------|------|
| BR-11 | PDF: extract all pages, concatenate with page separators |
| BR-12 | PDF encrypted: mark 'failed' with "Password-protected PDF" |
| BR-13 | DOCX: extract paragraphs + table cells |
| BR-14 | Text: UTF-8 default, fallback ISO-8859-1 |
| BR-15 | Empty extraction result: mark 'skipped' |
| BR-16 | Max extracted text: 100KB (truncate with warning) |

---

### 3.4 Feature: KB Ingestion

#### 3.4.1 Use Case

**Use Case ID:** UC-04
**Actor:** System

**Main Flow:**

| Step | System | Description |
|------|--------|-------------|
| 1 | KBIngestor | Formats content: title = "{issue_key} - {filename}" |
| 2 | KBIngestor | Sets tags: [attachment, {issue_key}, {mime_type}] |
| 3 | KBIngestor | Generates embedding via EmbeddingService |
| 4 | KBIngestor | Stores in Qdrant via VectorDbClient |

#### 3.4.2 Business Rules

| Rule ID | Rule |
|---------|------|
| BR-17 | Title format: "{issue_key} - {filename}" |
| BR-18 | Tags: attachment, issue_key, mime_type |
| BR-19 | Metadata: project_key, issue_type, priority |
| BR-20 | Chunk large text (>8000 tokens) into overlapping segments |

---

### 3.5 Feature: Graceful Shutdown

#### 3.5.1 Use Case

**Use Case ID:** UC-05
**Actor:** System (on application shutdown)

**Main Flow:**

| Step | System | Description |
|------|--------|-------------|
| 1 | AttachmentProcessor | Receives shutdown signal |
| 2 | AttachmentProcessor | Stops accepting new batches |
| 3 | AttachmentProcessor | Waits for current batch (timeout: 60s) |
| 4 | AttachmentProcessor | Resets incomplete 'processing' items to 'pending' |
| 5 | AttachmentProcessor | Exits |

#### 3.5.2 Business Rules

| Rule ID | Rule |
|---------|------|
| BR-21 | Shutdown timeout: 60 seconds |
| BR-22 | Force stop after timeout |
| BR-23 | Reset 'processing' items to 'pending' on incomplete shutdown |

---

## 4. Data Model

### 4.1 Entity: AttachmentQueueItem (jira_attachment_queue)

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| id | UUID (PK) | Yes | Unique queue item ID |
| issue_key | String | Yes | Jira issue key |
| filename | String | Yes | Attachment filename |
| mime_type | String | Yes | MIME type |
| download_url | String | Yes | Jira download URL |
| file_size | Long | No | File size in bytes |
| status | Enum | Yes | pending/processing/completed/failed/skipped |
| error_message | String? | No | Error details if failed |
| retry_count | Int | Yes | Current retry count (default 0) |
| created_at | Instant | Yes | When queued |
| processed_at | Instant? | No | When processing completed |

### 4.2 State Transitions

| From | To | Trigger |
|------|-----|---------|
| pending | processing | Batch dequeued |
| processing | completed | Successfully ingested |
| processing | failed | Max retries exhausted |
| processing | skipped | Unsupported MIME type or empty content |
| processing | pending | Shutdown reset |
| failed | pending | Manual reset |

---

## 5. Integration Specifications

### 5.1 Jira REST API (Download)

| Attribute | Value |
|-----------|-------|
| Endpoint | `{attachment.content_url}` (from issue metadata) |
| Method | GET |
| Auth | Basic Auth (same as search API) |
| Response | Binary stream |
| Timeout | 60 seconds |

### 5.2 Qdrant Vector DB

| Attribute | Value |
|-----------|-------|
| Collection | mcp_tools (existing) or mcp_attachments (new) |
| Vector Size | 768 (text-embedding-3-small) |
| Payload | title, content (truncated), tags, metadata |

### 5.3 OpenAI Embedding API

| Attribute | Value |
|-----------|-------|
| Model | text-embedding-3-small |
| Dimensions | 768 |
| Max Input | 8191 tokens |

---

## 6. Processing Logic (Pseudocode)

```kotlin
class AttachmentProcessorImpl {
    suspend fun run() {
        var backoff = config.pollInterval
        
        while (isActive) {
            val batch = queueManager.dequeueBatch(config.batchSize)
            
            if (batch.isEmpty()) {
                delay(backoff)
                backoff = minOf(backoff * 2, config.maxBackoff)
                continue
            }
            
            backoff = config.pollInterval // Reset backoff
            
            coroutineScope {
                batch.forEach { item ->
                    launch {
                        processItem(item)
                    }
                }
            }
        }
    }
    
    private suspend fun processItem(item: AttachmentQueueItem) {
        try {
            // Check MIME type support
            if (!isSupportedMimeType(item.mimeType)) {
                queueManager.markSkipped(item.id, "Unsupported: ${item.mimeType}")
                return
            }
            
            // Check file size
            if (item.fileSize != null && item.fileSize > config.maxFileSize) {
                queueManager.markFailed(item.id, "File too large: ${item.fileSize} bytes")
                return
            }
            
            // Download with semaphore
            val bytes = downloadSemaphore.withPermit {
                downloader.download(item.downloadUrl)
            }
            
            // Extract text
            val text = textExtractor.extract(bytes, item.mimeType)
            if (text.isBlank()) {
                queueManager.markSkipped(item.id, "Empty content after extraction")
                return
            }
            
            // Ingest into KB
            kbIngestor.ingest(
                title = "${item.issueKey} - ${item.filename}",
                content = text.take(MAX_CONTENT_LENGTH),
                tags = listOf("attachment", item.issueKey, item.mimeType)
            )
            
            queueManager.markCompleted(item.id)
        } catch (e: Exception) {
            handleError(item, e)
        }
    }
}
```

---

## 7. Security Requirements

| Data Type | Classification | Protection |
|-----------|---------------|------------|
| Download URLs | Internal | Not logged (contain auth tokens) |
| Attachment content | Internal | Processed in memory, not persisted to disk |
| API credentials | Restricted | Environment variables only |

---

## 8. Non-Functional Requirements

| Category | Requirement | Target |
|----------|-------------|--------|
| Performance | Batch processing time | < 60s for 5 items |
| Performance | Download throughput | 3 concurrent, 60s timeout each |
| Reliability | Retry on transient failure | Max 3 retries |
| Reliability | No data loss on shutdown | Graceful reset to 'pending' |
| Scalability | Configurable concurrency | Batch size + download parallelism |
| Monitoring | Log all state transitions | INFO/WARN/ERROR levels |

---

## 9. Configuration

```yaml
attachmentProcessor:
  enabled: true
  batchSize: 5
  pollInterval: 30s
  maxBackoff: 300s
  maxConcurrentDownloads: 3
  maxRetries: 3
  maxFileSize: 52428800  # 50MB
  shutdownTimeout: 60s
  supportedMimeTypes:
    - application/pdf
    - application/vnd.openxmlformats-officedocument.wordprocessingml.document
    - text/plain
    - text/markdown
```

---

## 10. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | System Context | [system-context.png](diagrams/system-context.png) | [system-context.drawio](diagrams/system-context.drawio) |
| 2 | State Diagram | [state-attachment.png](diagrams/state-attachment.png) | [state-attachment.drawio](diagrams/state-attachment.drawio) |
| 3 | Sequence - Process Batch | [sequence-process.png](diagrams/sequence-process.png) | [sequence-process.drawio](diagrams/sequence-process.drawio) |
