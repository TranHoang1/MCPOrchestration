# Release Notes (RLN)

## MCPOrchestration — MTO-19: Attachment Processor – Background Queue Worker

---

## Release Information

| Field | Value |
|-------|-------|
| Release Version | 1.4.0 |
| Release Date | 2025-07-18 |
| Jira Ticket | MTO-19 |
| Environment | DEV / SIT / UAT / PROD |
| Author | DevOps Agent |
| Status | Final |
| Related Epic | MTO-14 — Jira Project Sync Service |

---

## 1. Release Summary

This release introduces the **Attachment Processor** — a background coroutine worker that downloads Jira attachments from the queue (populated by MTO-18 Ticket Crawler), extracts text content using Apache PDFBox and Apache POI, and ingests into the Knowledge Base vector database for semantic search.

**Release Type:** Feature addition (new dependencies: PDFBox, POI)
**Risk Level:** Medium (new external libraries, background worker)
**Backward Compatible:** Yes — no existing behavior changed

---

## 2. What's New

### 2.1 New Features

| # | Feature | Description |
|---|---------|-------------|
| 1 | **Background Queue Worker** | Daemon coroutine polls `jira_attachment_queue` with adaptive backoff |
| 2 | **PDF Text Extraction** | Apache PDFBox 3.0.4 extracts text from PDF attachments |
| 3 | **DOCX Text Extraction** | Apache POI 5.3.0 extracts text from Word documents |
| 4 | **Plain Text Support** | Direct read for .txt and .md files |
| 5 | **Semaphore Concurrency** | Configurable max concurrent downloads (default 3) |
| 6 | **Retry Logic** | Failed items retried up to 3 times with backoff |
| 7 | **Graceful Shutdown** | Processing items reset to pending on shutdown |
| 8 | **KB Vector Ingestion** | Extracted text embedded and stored in Qdrant |

### 2.2 Technical Highlights

- **FOR UPDATE SKIP LOCKED** — Prevents duplicate processing in concurrent scenarios
- **Strategy Pattern** — MIME-type based routing to appropriate extractor
- **Adaptive Backoff** — Doubles poll interval when queue empty, resets on work found
- **Memory-Safe** — 50MB file size limit, in-memory processing only (no temp files)
- **Fault Isolation** — SupervisorJob ensures single item failure doesn't crash worker

---

## 3. New Components

### 3.1 Package: `com.orchestrator.mcp.attachment`

| Class | Type | Responsibility |
|-------|------|----------------|
| `AttachmentProcessor` | Interface | Worker lifecycle (start, stop, stats) |
| `AttachmentProcessorImpl` | Class | Main polling loop + batch processing |
| `QueueManager` | Class | Queue CRUD (dequeue, mark status) |
| `AttachmentDownloader` | Class | HTTP download with semaphore |
| `TextExtractor` | Class | Strategy-based MIME routing |
| `PdfTextExtractor` | Class | PDFBox implementation |
| `DocxTextExtractor` | Class | Apache POI implementation |
| `PlainTextExtractor` | Class | Direct byte-to-string |
| `KBIngestor` | Class | Embedding + Qdrant upsert |
| `AttachmentProcessorConfig` | Data class | Configuration |
| `AttachmentModule` | Koin module | DI bindings |

---

## 4. Configuration Changes

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

---

## 5. Dependencies

### 5.1 New External Dependencies

| Library | Version | Purpose | Size Impact |
|---------|---------|---------|-------------|
| Apache PDFBox | 3.0.4 | PDF text extraction | ~15MB |
| Apache POI | 5.3.0 | DOCX text extraction | ~15MB |

### 5.2 Internal Dependencies

| Module | Required | Relationship |
|--------|----------|-------------|
| MTO-15 (DB Schema) | ✅ | jira_attachment_queue table |
| MTO-16 (Jira Client) | ✅ | Authenticated downloads |
| MTO-18 (Ticket Crawler) | ✅ | Populates attachment queue |

---

## 6. Testing Summary

| Level | Tests | Passed | Failed | Coverage |
|-------|-------|--------|--------|----------|
| Unit Tests | 16 | 16 | 0 | Extractors, QueueManager, ProcessorImpl |
| Integration Tests | 6 | 6 | 0 | Full pipeline with Testcontainers |
| **Total** | **22** | **22** | **0** | — |

---

## 7. Known Issues & Limitations

| # | Issue | Severity | Workaround |
|---|-------|----------|------------|
| 1 | Scanned PDFs (image-only) produce empty text | Low | OCR not supported |
| 2 | Password-protected files skipped | Low | Marked as failed with error message |
| 3 | Large files (>50MB) rejected | Low | Configurable maxFileSize |
| 4 | Only 4 MIME types supported | Low | Extensible via Strategy pattern |

---

## 8. Breaking Changes

No breaking changes. Fully backward compatible.

---

## 9. Rollback Instructions

1. Stop application
2. Restore previous JAR
3. Reset processing items: `UPDATE jira_attachment_queue SET status='pending' WHERE status='processing';`
4. Restart application

**Data loss risk:** Processed items remain in 'completed' status. Vector DB points from this session remain (manual cleanup if needed).

---

## 10. Future Roadmap

| Story | Status | Dependency on MTO-19 |
|-------|--------|---------------------|
| MTO-15–18 | ✅ Deployed | Prerequisites |
| **MTO-19: Attachment Processor** | ✅ **This release** | — |
| MTO-20: MCP Tool Integration | Next | May expose processor stats |
| MTO-21: Web Dashboard | Planned | Shows attachment queue status |
