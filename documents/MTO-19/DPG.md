# Deployment Guide (DPG)

## MCPOrchestration — MTO-19: Attachment Processor – Background Queue Worker

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-19 |
| Title | Attachment Processor – Background Queue Worker |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2025-07-18 |
| Status | Final |
| Related TDD | TDD-v1-MTO-19.docx |
| Related FSD | FSD-v1-MTO-19.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-18 | DevOps Agent | Initial deployment guide for Attachment Processor module |

---

## 1. Deployment Overview

### 1.1 Summary

This deployment introduces the **AttachmentProcessor** — a background coroutine worker that:

- Polls `jira_attachment_queue` for pending items (populated by MTO-18 Ticket Crawler)
- Downloads Jira attachments with semaphore-based concurrency
- Extracts text content (PDF via PDFBox, DOCX via Apache POI, plain text)
- Generates embeddings and ingests into Qdrant vector DB
- Manages queue state with retry logic and graceful shutdown

### 1.2 Deployment Type

| Aspect | Value |
|--------|-------|
| Type | Rolling update |
| Risk Level | Medium — new external dependencies (PDFBox, POI) |
| Rollback Complexity | Low — disable via config or revert JAR |
| Downtime Required | None |
| Data Migration | None (uses existing MTO-15 queue table) |

### 1.3 Affected Components

| Component | Change Type | Impact |
|-----------|-------------|--------|
| `orchestrator-server` module | New classes added | New package `com.orchestrator.mcp.attachment` |
| `build.gradle.kts` | New dependencies | PDFBox 3.0.4, Apache POI 5.3.0 |
| `AppModule.kt` (Koin DI) | Extended | AttachmentModule registered |
| `application.yml` | Extended | New `attachmentProcessor` config section |
| Fat JAR | Rebuilt + larger | ~30MB increase from PDFBox + POI |
| Qdrant vector DB | New points | Attachment content embeddings |

---

## 2. Prerequisites

### 2.1 Infrastructure Requirements

| Requirement | Minimum | Recommended | Notes |
|-------------|---------|-------------|-------|
| PostgreSQL | 16.0 | 16.x latest | MTO-15 queue table must exist |
| JVM | 21 | 21 LTS | GraalVM or OpenJDK |
| Qdrant | 1.9+ | Latest | Vector DB for KB ingestion |
| OpenAI API | — | — | For text-embedding-3-small |
| RAM (JVM) | 1 GB | 2 GB | PDF processing can be memory-intensive |
| Disk (temp) | 500 MB | 1 GB | In-memory processing, but JVM heap needed |

### 2.2 Software Dependencies

| Dependency | Version | Already Present | Notes |
|------------|---------|-----------------|-------|
| Apache PDFBox | 3.0.4 | ❌ **NEW** | PDF text extraction |
| Apache POI | 5.3.0 | ❌ **NEW** | DOCX text extraction |
| Kotlin | 2.3.20 | ✅ Yes | No change |
| kotlinx.coroutines | 1.10.2 | ✅ Yes | No change |
| Exposed ORM | 0.61.0 | ✅ Yes | No change |

### 2.3 Pre-requisite Deployments

| Ticket | Component | Required | Status |
|--------|-----------|----------|--------|
| MTO-15 | Database Schema | ✅ | jira_attachment_queue table |
| MTO-16 | Jira REST Client | ✅ | Download via authenticated HTTP |
| MTO-18 | Ticket Crawler | ✅ | Populates attachment queue |

### 2.4 Access Requirements

| Access | Purpose | Who |
|--------|---------|-----|
| Jira Cloud API | Download attachments | Application |
| OpenAI API | Generate embeddings | Application |
| Qdrant | Store vectors | Application |
| PostgreSQL | Queue management | Application |

---

## 3. Pre-Deployment Checklist

| # | Check | Command / Action | Expected Result |
|---|-------|-----------------|-----------------|
| 1 | Attachment queue table exists | `SELECT COUNT(*) FROM jira_attachment_queue;` | Returns count |
| 2 | Qdrant accessible | `curl http://localhost:6333/collections` | 200 OK |
| 3 | OpenAI API key set | Verify `OPENAI_API_KEY` env var | Set |
| 4 | Fat JAR built | `./gradlew buildFatJar` | `BUILD SUCCESSFUL` |
| 5 | All tests pass | `./gradlew test` | All pass |
| 6 | Backup current JAR | `cp mcp-orchestrator-all.jar mcp-orchestrator-all.jar.bak` | Done |
| 7 | Verify JAR size increase | `ls -la build/libs/mcp-orchestrator-all.jar` | ~30MB larger |

---

## 4. Configuration Changes

### 4.1 New Configuration Section

```yaml
attachmentProcessor:
  enabled: true
  batchSize: 5
  pollInterval: 30s
  maxBackoff: 300s
  maxConcurrentDownloads: 3
  maxRetries: 3
  maxFileSize: 52428800      # 50MB
  shutdownTimeout: 60s
  supportedMimeTypes:
    - application/pdf
    - application/vnd.openxmlformats-officedocument.wordprocessingml.document
    - text/plain
    - text/markdown
```

### 4.2 build.gradle.kts Changes

```kotlin
// New dependencies added
implementation("org.apache.pdfbox:pdfbox:3.0.4")
implementation("org.apache.poi:poi-ooxml:5.3.0")
```

---

## 5. Deployment Steps

### 5.1 Build Phase

```bash
git checkout MTO-19
git pull origin MTO-19
./gradlew clean test
./gradlew buildFatJar

# Verify attachment classes + new deps
jar tf build/libs/mcp-orchestrator-all.jar | grep "attachment/"
jar tf build/libs/mcp-orchestrator-all.jar | grep "pdfbox"
jar tf build/libs/mcp-orchestrator-all.jar | grep "poi"
```

### 5.2 Deploy

```bash
# Stop application
sudo systemctl stop mcp-orchestrator

# Deploy JAR
cp build/libs/mcp-orchestrator-all.jar /opt/mcp-orchestrator/mcp-orchestrator-all.jar

# Update application.yml with attachmentProcessor config

# Start application
sudo systemctl start mcp-orchestrator
sleep 10
```

---

## 6. Post-Deployment Verification

| # | Check | Method | Expected |
|---|-------|--------|----------|
| 1 | Application started | Check logs | `Application started` |
| 2 | Processor started | Check logs | `AttachmentProcessor started` |
| 3 | No dependency errors | Check logs | No ClassNotFoundException |
| 4 | Queue polling active | Check logs | `Polling attachment queue...` or idle backoff |
| 5 | Existing features work | MCP tools/list | Returns tool list |

### 6.1 Smoke Test

```sql
-- Insert a test queue item (if queue is empty)
INSERT INTO jira_attachment_queue (issue_key, filename, mime_type, download_url, status)
VALUES ('TEST-1', 'test.txt', 'text/plain', 'https://example.com/test.txt', 'pending');

-- Check if processor picks it up (within pollInterval)
SELECT status FROM jira_attachment_queue WHERE issue_key = 'TEST-1';
-- Expected: 'processing' or 'completed' or 'failed' (not 'pending' after pollInterval)

-- Cleanup
DELETE FROM jira_attachment_queue WHERE issue_key = 'TEST-1';
```

---

## 7. Rollback Plan

### 7.1 Rollback Steps

```bash
# Stop application
sudo systemctl stop mcp-orchestrator

# Restore JAR
cp /opt/mcp-orchestrator/mcp-orchestrator-all.jar.bak \
   /opt/mcp-orchestrator/mcp-orchestrator-all.jar

# Reset any 'processing' items back to 'pending'
psql -h localhost -U postgres -d mcp_orchestrator -c \
  "UPDATE jira_attachment_queue SET status = 'pending' WHERE status = 'processing';"

# Restart
sudo systemctl start mcp-orchestrator
```

### 7.2 Quick Disable

```yaml
attachmentProcessor:
  enabled: false
```

---

## 8. Monitoring

| Log Pattern | Meaning | Action |
|-------------|---------|--------|
| `AttachmentProcessor started` | Worker running | None |
| `Processing batch of {N} items` | Active processing | None |
| `Download completed: {filename}` | File downloaded | None |
| `Extraction completed: {mimeType}` | Text extracted | None |
| `Ingestion completed: {filename}` | Vector stored | None |
| `Download failed: {error}` | Download error | Check Jira API |
| `Unsupported MIME type: {type}` | Skipped file | Expected for images, etc. |
| `Max retries exceeded for {id}` | Permanent failure | Manual investigation |
| `OutOfMemoryError` | Large file | Increase JVM heap or reduce maxFileSize |

---

## 9. Security Considerations

| Aspect | Implementation |
|--------|---------------|
| Download URLs | Not logged (may contain auth tokens) |
| File content | In-memory only, never written to disk |
| Extracted text | Stored in Qdrant (internal network) |
| API credentials | Environment variables |
| File size limit | 50MB max (configurable) |

---

## Appendix: Related Documents

| Document | Reference |
|----------|-----------|
| BRD | BRD-v1-MTO-19.docx |
| FSD | FSD-v1-MTO-19.docx |
| TDD | TDD-v1-MTO-19.docx |
| STP | STP-v1-MTO-19.docx |
| Test Report | STR-v1-MTO-19.docx |
