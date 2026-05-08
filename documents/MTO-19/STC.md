# Software Test Cases (STC)

## MCPOrchestration — MTO-19: Attachment Processor – Background Queue Worker

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-19 |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Related STP | STP-v1-MTO-19.docx |

---

## 1. PBT — Property-Based Tests

### TC-PBT-001: MIME type routing always returns valid strategy or UNSUPPORTED

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | UC-03 |
| Property | For any string MIME type, getStrategy() returns a valid ExtractionStrategy or UNSUPPORTED |
| Generator | Arb.string(5..50) |
| Iterations | 1000 |

### TC-PBT-002: Text extraction output never exceeds 100KB

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | BR-16 |
| Property | For any input bytes, extracted text length ≤ 102400 bytes |
| Generator | Arb.byteArray(Arb.int(0..200000)) |
| Iterations | 100 |

### TC-PBT-003: Backoff sequence follows exponential pattern

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | BR-03 |
| Property | For N consecutive empty polls, backoff = min(baseInterval * 2^N, maxBackoff) |
| Generator | Arb.int(0..20) |
| Iterations | 100 |

---

## 2. UT — Unit Tests

### TC-001: Queue polling — batch size configurable

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-01, BR-05 |
| Input | batchSize=3, 10 pending items in queue |
| Expected | Dequeue returns 3 items, all marked 'processing' |

### TC-002: Queue polling — poll interval respected

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-02 |
| Steps | 1. Process batch 2. Measure delay before next poll |
| Expected | Delay ≥ pollInterval (30s) between polls |

### TC-003: Queue polling — exponential backoff on empty queue

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-03 |
| Steps | 1. Return empty batch 5 times 2. Track delays |
| Expected | Delays: 30s, 60s, 120s, 240s, 300s (capped) |

### TC-004: Queue polling — backoff resets when items found

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-04 |
| Steps | 1. Empty 3 times (backoff=120s) 2. Return items 3. Next empty |
| Expected | After items found, next backoff = 30s (reset) |

### TC-005: Downloader — semaphore limits concurrent downloads

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-06 |
| Input | 10 download requests, maxConcurrent=3 |
| Expected | Never more than 3 active downloads simultaneously |

### TC-006: Downloader — timeout after 60 seconds

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-07 |
| Input | Mock server with 90s delay |
| Expected | Download throws TimeoutException after 60s |

### TC-007: Downloader — reject files > 50MB

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-08 |
| Input | Queue item with fileSize = 60MB |
| Expected | Item marked 'failed' with "File too large" message, no download attempted |

### TC-008: Downloader — retry logic (3x backoff, no retry on 404)

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-09, BR-10 |
| Input A | Server returns 500 three times then 200 |
| Expected A | Download succeeds after 3 retries |
| Input B | Server returns 404 |
| Expected B | Immediately marked 'failed', no retry |

### TC-009: TextExtractor — PDF extracts all pages

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-11 |
| Input | 3-page PDF with known text |
| Expected | Extracted text contains content from all 3 pages |

### TC-010: TextExtractor — encrypted PDF marked failed

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-12 |
| Input | Password-protected PDF bytes |
| Expected | Throws EncryptedPdfException, item marked 'failed' |

### TC-011: TextExtractor — DOCX extracts paragraphs and tables

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-13 |
| Input | DOCX with 2 paragraphs + 1 table (3x3) |
| Expected | Text contains paragraph text + all table cell values |

### TC-012: TextExtractor — plain text with UTF-8 and fallback

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-14 |
| Input A | UTF-8 encoded text file |
| Expected A | Correct text extracted |
| Input B | ISO-8859-1 encoded text file |
| Expected B | Correct text extracted via fallback |

### TC-013: TextExtractor — empty content and truncation

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-15, BR-16 |
| Input A | PDF with no text (images only) |
| Expected A | Item marked 'skipped' |
| Input B | Text file with 150KB content |
| Expected B | Extracted text truncated to 100KB |

---

## 3. IT — Integration Tests

### TC-IT-001: Full queue lifecycle in PostgreSQL

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Precondition | Testcontainers PostgreSQL, 5 pending items |
| Steps | 1. Dequeue batch 2. Mark processing 3. Mark completed 4. Verify status |
| Expected | All 5 items status='completed', processed_at set |

### TC-IT-002: Download + Extract + Ingest pipeline

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Precondition | WireMock serving PDF file, Testcontainers Qdrant |
| Steps | 1. Process queue item 2. Verify KB has vector |
| Expected | PDF text extracted and stored in Qdrant with correct metadata |

### TC-IT-003: Retry exhaustion marks item failed

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Precondition | WireMock returns 500 for all retries |
| Steps | 1. Process item 2. Verify retry_count and status |
| Expected | retry_count=3, status='failed', error_message set |

### TC-IT-004: Concurrent batch processing

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Precondition | 5 items, WireMock with 2s delay per download |
| Steps | 1. Process batch 2. Measure total time |
| Expected | Total time < 5×2s (concurrent processing), all completed |

### TC-IT-005: Graceful shutdown resets processing items

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Precondition | 3 items in 'processing' state |
| Steps | 1. Trigger shutdown 2. Query queue |
| Expected | All 3 items reset to 'pending' |

---

## 4. E2E-API — End-to-End Tests

### TC-E2E-001: Processor starts, polls, processes, and completes batch

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Precondition | Full app context, 3 PDF attachments in queue |
| Steps | 1. Start processor 2. Wait for processing 3. Verify all completed |
| Expected | 3 items completed, KB has 3 new vectors |

### TC-E2E-002: Mixed MIME types — PDF processed, image skipped

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Precondition | Queue: 2 PDFs + 1 PNG + 1 DOCX |
| Expected | 2 PDFs + 1 DOCX completed, 1 PNG skipped |

### TC-E2E-003: Backoff behavior — empty queue then items arrive

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Steps | 1. Start with empty queue 2. Wait 2 poll cycles 3. Insert items 4. Verify processing |
| Expected | Backoff increases, then resets when items found |

### TC-E2E-004: Shutdown mid-processing — no data loss

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Steps | 1. Start processing 5 items 2. Trigger shutdown after 2 complete 3. Verify state |
| Expected | 2 completed, 3 reset to 'pending' |

---

## 5. SIT — System Integration Tests (Manual)

### TC-SIT-001: Real Jira attachment download and extraction

| Attribute | Value |
|-----------|-------|
| Level | SIT |
| Steps | 1. Queue real Jira attachment 2. Process 3. Search KB for content |
| Expected | Attachment text searchable in KB |

### TC-SIT-002: Large PDF (>10MB) processing

| Attribute | Value |
|-----------|-------|
| Level | SIT |
| Steps | 1. Upload 15MB PDF to Jira 2. Queue and process 3. Verify extraction |
| Expected | Text extracted within timeout, KB ingested |
