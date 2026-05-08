# User Guide (UG)

## Jira Project Sync Service — MTO-19: Attachment Processor — Background Queue Worker

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-19 |
| Title | Attachment Processor — Background Queue Worker |
| Author | DEV Agent |
| Reviewer | BA Agent |
| Version | 1.0 |
| Date | 2025-07-15 |
| Status | Final |
| Related BRD | BRD-v1-MTO-19.docx |
| Related FSD | FSD-v1-MTO-19.docx |
| Related TDD | TDD-v1-MTO-19.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-15 | DEV Agent | Initial document |

---

## 1. Introduction

### 1.1 Purpose

This guide explains how to configure, monitor, and troubleshoot the **Attachment Processor** — a background coroutine worker that processes the Jira attachment queue. It downloads files from Jira, extracts text content based on MIME type, and ingests the extracted text into the Knowledge Base for semantic search.

### 1.2 Audience

| Audience | What They Need |
|----------|---------------|
| System Operator | How to enable/disable, monitor queue depth, and handle failures |
| DevOps | How to tune performance, manage disk space, and troubleshoot |
| Developer | How to extend with new MIME type extractors |

### 1.3 Prerequisites

| Prerequisite | Version | Required |
|-------------|---------|----------|
| JDK | 21+ | Yes |
| PostgreSQL | 16+ | Yes |
| Qdrant Vector DB | Running | Yes |
| OpenAI API key | — | Yes (for embeddings) |
| MTO-15 (DB Schema) | Initialized | Yes |
| MTO-16 (Jira Client) | Configured | Yes |
| Apache PDFBox (bundled) | — | Yes (auto-included) |
| Apache POI (bundled) | — | Yes (auto-included) |

---

## 2. Getting Started

### 2.1 Quick Start

The Attachment Processor starts automatically with the application (if enabled). It continuously polls the queue for pending attachments.

```bash
# Step 1: Ensure configuration
export JIRA_BASE_URL="https://your-domain.atlassian.net"
export JIRA_EMAIL="your-email@example.com"
export JIRA_API_TOKEN="your-api-token"
export OPENAI_API_KEY="sk-..."
export QDRANT_URL="http://localhost:6333"

# Step 2: Start the application
./gradlew :orchestrator-server:run

# Step 3: Verify processor is running (check logs)
# Expected: INFO AttachmentProcessor - Attachment processor started (batchSize=5, concurrency=3)

# Step 4: Monitor queue
# GET http://localhost:8080/sync/status/MTO
# Or SQL: SELECT status, COUNT(*) FROM jira_attachment_queue GROUP BY status;
```

### 2.2 Processing Pipeline

```
Queue (PENDING) → Download → Extract Text → Generate Embedding → Ingest KB → DONE
                     ↓            ↓
                  FAILED       SKIPPED
               (retry 3x)   (unsupported type)
```

---

## 3. Configuration

### 3.1 Processor Configuration

```yaml
sync:
  attachments:
    enabled: true                    # Enable/disable processor
    batchSize: 5                     # Items per poll cycle
    pollInterval: 30s                # Time between polls
    maxConcurrentDownloads: 3        # Parallel download limit
    maxRetries: 3                    # Retry attempts per item
    maxFileSize: 52428800            # 50MB max file size
    shutdownTimeout: 60s             # Graceful shutdown timeout
    supportedMimeTypes:
      - application/pdf
      - application/vnd.openxmlformats-officedocument.wordprocessingml.document
      - text/plain
      - text/markdown
      - text/csv
```

### 3.2 Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `ATTACHMENT_PROCESSOR_ENABLED` | No | `true` | Enable/disable the processor |
| `ATTACHMENT_BATCH_SIZE` | No | `5` | Items per poll cycle |
| `ATTACHMENT_POLL_INTERVAL_MS` | No | `30000` | Poll interval in ms |
| `ATTACHMENT_MAX_CONCURRENT` | No | `3` | Max parallel downloads |
| `ATTACHMENT_MAX_RETRIES` | No | `3` | Max retry attempts |
| `ATTACHMENT_MAX_FILE_SIZE` | No | `52428800` | Max file size (bytes) |

### 3.3 Configuration Examples

#### Default (Balanced)

```yaml
sync:
  attachments:
    enabled: true
    batchSize: 5
    maxConcurrentDownloads: 3
```

#### High-Throughput

```yaml
sync:
  attachments:
    enabled: true
    batchSize: 10
    maxConcurrentDownloads: 5
    pollInterval: 10s
```

#### Disabled (Manual Processing Only)

```yaml
sync:
  attachments:
    enabled: false
```

---

## 4. Usage

### 4.1 Supported File Types

| MIME Type | Extension | Extraction Method |
|-----------|-----------|-------------------|
| `application/pdf` | .pdf | Apache PDFBox (all pages) |
| `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | .docx | Apache POI (paragraphs, tables, headers) |
| `text/plain` | .txt | Direct UTF-8 read |
| `text/markdown` | .md | Direct UTF-8 read |
| `text/csv` | .csv | Direct UTF-8 read |
| `image/*` | .png, .jpg, etc. | Skipped (OCR not yet supported) |
| Other | — | Skipped with reason |

### 4.2 Processing Lifecycle

Each attachment goes through these states:

```
PENDING → DOWNLOADING → PROCESSING → DONE
    ↓          ↓            ↓
    └──────────┴────────────┴──→ FAILED (after max retries)
                                     or
                                  SKIPPED (unsupported type)
```

### 4.3 Monitoring Queue Status

#### Via REST API

```bash
curl http://localhost:8080/sync/status/MTO
```

Response includes attachment phase:
```json
{
  "phases": {
    "attachments": {
      "status": "syncing",
      "progress": 60.0,
      "processed": 9,
      "total": 15,
      "failed": 1
    }
  }
}
```

#### Via SQL

```sql
-- Queue depth by status
SELECT status, COUNT(*) as count 
FROM jira_attachment_queue 
GROUP BY status
ORDER BY count DESC;

-- Recent processing activity
SELECT id, ticket_key, filename, status, processed_at
FROM jira_attachment_queue
ORDER BY processed_at DESC NULLS LAST
LIMIT 20;
```

### 4.4 Backoff Behavior

When the queue is empty, the processor uses exponential backoff:

| Poll # | Wait Time | Behavior |
|--------|-----------|----------|
| 1 | 30s | Base interval |
| 2 | 60s | Queue still empty |
| 3 | 120s | Doubling |
| 4 | 240s | Doubling |
| 5+ | 300s | Max cap (5 minutes) |

Backoff resets to 30s immediately when new items appear.

---

## 5. Administration

### 5.1 View Failed Attachments

```sql
SELECT id, ticket_key, filename, mime_type, error_message, retry_count
FROM jira_attachment_queue
WHERE status = 'FAILED'
ORDER BY created_at DESC;
```

### 5.2 Retry Failed Attachments

```sql
-- Reset specific failed items for re-processing
UPDATE jira_attachment_queue
SET status = 'PENDING', retry_count = 0, error_message = NULL
WHERE id IN (101, 102, 103);

-- Reset ALL failed items
UPDATE jira_attachment_queue
SET status = 'PENDING', retry_count = 0, error_message = NULL
WHERE status = 'FAILED';
```

### 5.3 View Skipped Attachments

```sql
SELECT ticket_key, filename, mime_type, error_message
FROM jira_attachment_queue
WHERE status = 'SKIPPED';
```

### 5.4 Queue Cleanup

```sql
-- Remove completed items older than 30 days
DELETE FROM jira_attachment_queue
WHERE status = 'DONE' AND processed_at < NOW() - INTERVAL '30 days';

-- Count queue size
SELECT 
    COUNT(*) FILTER (WHERE status = 'PENDING') as pending,
    COUNT(*) FILTER (WHERE status = 'PROCESSING') as processing,
    COUNT(*) FILTER (WHERE status = 'DONE') as done,
    COUNT(*) FILTER (WHERE status = 'FAILED') as failed,
    COUNT(*) FILTER (WHERE status = 'SKIPPED') as skipped
FROM jira_attachment_queue;
```

### 5.5 Disable/Enable Processor at Runtime

To disable without restarting:
```yaml
# Set in application.yml and hot-reload (if supported)
sync:
  attachments:
    enabled: false
```

Or stop the application, change config, restart.

---

## 6. Troubleshooting

### 6.1 Common Issues

| # | Symptom | Cause | Solution |
|---|---------|-------|----------|
| 1 | Queue growing, nothing processing | Processor disabled | Set `enabled: true` in config |
| 2 | All items FAILED with "Download timeout" | Jira API slow or rate limited | Increase timeout, reduce concurrency |
| 3 | PDF extraction empty | Scanned PDF (image-only) | Expected — mark as limitation. OCR not supported. |
| 4 | "Password-protected PDF" error | Encrypted PDF | Cannot process — manual intervention needed |
| 5 | "File too large" error | Attachment exceeds `maxFileSize` | Increase limit or accept as limitation |
| 6 | DOCX extraction garbled | Corrupted file | Mark as FAILED, skip |
| 7 | KB ingestion failing | Qdrant down or OpenAI quota exceeded | Check Qdrant status, check OpenAI billing |
| 8 | Items stuck in PROCESSING | App crashed during processing | Items auto-reset to PENDING on next startup |

### 6.2 Retry Behavior

| Error Type | Retried? | Max Attempts | Backoff |
|-----------|----------|--------------|---------|
| Download timeout | Yes | 3 | 1s, 2s, 4s |
| Download 404 | No | — | Immediate FAILED |
| Download 429 | Yes | 3 | Retry-After header |
| Extract exception | No | — | Immediate FAILED |
| KB unavailable | Yes | 3 | 1s, 2s, 4s |
| Out of memory | No | — | Immediate FAILED |

### 6.3 Logs

| Log Pattern | Meaning |
|-------------|---------|
| `INFO AttachmentProcessor - Attachment processor started` | Processor running |
| `INFO AttachmentProcessor - Processing batch: 5 items` | Batch picked up |
| `INFO AttachmentProcessor - MTO-15/design.pdf: extracted 2,345 chars` | Successful extraction |
| `INFO AttachmentProcessor - MTO-15/design.pdf: ingested into KB` | Successful ingestion |
| `WARN AttachmentProcessor - MTO-20/image.png: skipped (OCR not supported)` | Unsupported type |
| `ERROR AttachmentProcessor - MTO-99/report.pdf: retry 2/3 (timeout)` | Retry in progress |
| `ERROR AttachmentProcessor - MTO-99/report.pdf: FAILED after 3 retries` | Max retries exceeded |
| `INFO AttachmentProcessor - Queue empty, backing off (60s)` | Idle backoff |

### 6.4 Graceful Shutdown

On application shutdown:
1. Processor stops accepting new batches
2. Current batch completes (up to `shutdownTimeout`)
3. If timeout exceeded → force stop
4. Items in PROCESSING state → reset to PENDING on next startup

### 6.5 FAQ

**Q: What happens to unsupported file types?**
A: They are marked as SKIPPED with a reason (e.g., "OCR not yet supported" for images). They don't block the queue.

**Q: Can I add support for new file types?**
A: Yes. Implement a new `TextExtractor` for the MIME type and register it in the extractor factory. Add the MIME type to `supportedMimeTypes` config.

**Q: How much disk space does processing use?**
A: Attachments are streamed and processed in memory — no persistent disk storage. Temporary memory usage equals the file size during processing.

**Q: What if the same attachment is queued twice?**
A: The unique constraint on `(ticket_key, attachment_id)` prevents duplicates. The second insert is silently ignored.

**Q: How do I know if KB ingestion is working?**
A: Search KB for a recently processed attachment: `kb_search { "query": "filename content" }`. If results appear, ingestion is working.

---

## 7. Appendix

### 7.1 Glossary

| Term | Definition |
|------|------------|
| Queue Worker | Background coroutine that continuously polls and processes items |
| MIME Type | Media type identifier determining extraction strategy |
| Backoff | Progressively increasing wait time when queue is empty |
| Semaphore | Concurrency control limiting parallel downloads |
| Graceful Shutdown | Completing current work before stopping |

### 7.2 Related Documents

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-19.docx |
| FSD | FSD-v1-MTO-19.docx |
| TDD | TDD-v1-MTO-19.docx |
| DPG | DPG-v1-MTO-19.docx |
