# Business Requirements Document (BRD)

## MCPOrchestration — MTO-19: Attachment Processor – Background Queue Worker

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-19 |
| Title | Attachment Processor – Background Queue Worker |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-07 |
| Status | Draft |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | BA Agent – Business Analyst | Create document |
| Peer Reviewer | SA Agent – Solution Architect | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-07 | BA Agent | Initiate document — auto-generated from Jira ticket MTO-19 and linked tickets |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |

---

## 1. Introduction

### 1.1 Scope

Implement an Attachment Processor — a background coroutine worker that processes the attachment queue by downloading files from Jira, extracting text content based on MIME type, vectorizing the extracted text, and ingesting it into the Knowledge Base (KB) vector database. This enables AI agents to search and retrieve information from Jira attachments (PDFs, DOCX, text files) through semantic search.

### 1.2 Out of Scope

- OCR processing for image files (deferred to future iteration)
- Excel/spreadsheet text extraction
- Video/audio file processing
- Real-time streaming processing (batch-only)
- UI for monitoring processor status
- Custom embedding model training

### 1.3 Preliminary Requirement

| # | Prerequisite | Source |
|---|-------------|--------|
| 1 | Database Schema — `jira_attachment_queue` table must exist | Story 1 (Database Schema) |
| 2 | Jira REST Client — `downloadAttachment` method must be available | Story 2 (Jira REST Client) |
| 3 | KB Vector DB (Qdrant) must be running and accessible | Existing infrastructure |
| 4 | OpenAI API key configured for embedding generation | Existing configuration |

---

## 2. Business Requirements

### 2.1 High Level Process Map

The Attachment Processor operates as a background service within the MCP Orchestrator application. It continuously polls the attachment queue for pending items, downloads attachments from Jira, extracts text content, generates vector embeddings, and ingests the content into the Knowledge Base. This enables AI agents to semantically search attachment content when answering user queries.

**High-Level Flow:**
1. Background worker starts with application
2. Polls `jira_attachment_queue` for pending attachments
3. Downloads attachment from Jira via REST API
4. Extracts text based on MIME type (PDF, DOCX, text)
5. Generates embedding and ingests into KB vector DB
6. Marks attachment as completed in queue
7. Handles errors with retry logic and graceful degradation

### 2.2 List of User Stories / Use Cases

| # | Story / Use Case | Priority | Source Ticket |
|---|-----------------|----------|---------------|
| 1 | As a system, I want to automatically process queued attachments so that their content becomes searchable in the KB | MUST HAVE | MTO-19 |
| 2 | As a system, I want to extract text from PDF attachments so that PDF content is available for semantic search | MUST HAVE | MTO-19 |
| 3 | As a system, I want to extract text from DOCX attachments so that Word document content is available for semantic search | MUST HAVE | MTO-19 |
| 4 | As a system, I want to read text files directly so that plain text and markdown content is available for semantic search | MUST HAVE | MTO-19 |
| 5 | As a system, I want to gracefully skip unsupported file types so that the processor doesn't fail on unknown formats | MUST HAVE | MTO-19 |
| 6 | As a system, I want to retry failed downloads so that transient network issues don't cause permanent failures | MUST HAVE | MTO-19 |
| 7 | As a system, I want to limit concurrent downloads so that Jira API rate limits are respected | MUST HAVE | MTO-19 |
| 8 | As a system, I want to shut down gracefully so that in-progress work is completed before stopping | MUST HAVE | MTO-19 |

---

### 2.3 Details of User Stories

---

#### Business Flow

![Business Flow](diagrams/business-flow.png)

**Step 1:** Application starts → AttachmentProcessor coroutine launches

**Step 2:** Processor polls `jira_attachment_queue` table for records with `status = 'pending'`, ordered by `created_at`, limited by `batchSize` (default: 5)

**Step 3:** For each attachment in batch, mark `status = 'processing'`

**Step 4:** Download attachment from Jira using `JiraRestClient.downloadAttachment(url)` with semaphore-limited concurrency (max 3)

**Step 5:** Determine extraction strategy based on `mime_type`:
- `application/pdf` → PDFBox extraction
- `application/vnd.openxmlformats-officedocument.wordprocessingml.document` → Apache POI extraction
- `text/*` → Direct read
- Other → Skip, mark as 'skipped'

**Step 6:** Format extracted text with metadata: Title = `"{issue_key} - {filename}"`, Tags = `[attachment, {issue_key}, {mime_type}]`

**Step 7:** Ingest into KB vector DB (generate embedding → store in Qdrant)

**Step 8:** Mark `status = 'completed'`, set `processed_at = NOW()`

**Step 9:** If queue empty → exponential backoff (30s → 60s → 120s → ... → 5min max)

**Step 10:** Repeat from Step 2

> **Note:** On download/extract/ingest failure, retry up to 3 times. After max retries, mark as 'failed' with error message. Failed items can be manually reset to 'pending' for re-processing.

---

#### STORY 1: Background Queue Processing

> As a system, I want to automatically process queued attachments so that their content becomes searchable in the KB

**Requirement Details:**

1. Background coroutine loop that runs continuously while application is active
2. Polls `jira_attachment_queue` WHERE `status = 'pending'` ORDER BY `created_at`
3. Configurable batch size (default: 5 items per poll)
4. Configurable poll interval (default: 30s when idle)
5. Exponential backoff when queue is empty (up to 5 minutes max)
6. Marks items as 'processing' before starting work (prevents duplicate processing)

**Configuration:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| enabled | Boolean | Yes | Enable/disable the processor | true |
| batchSize | Integer | Yes | Number of items to process per poll | 5 |
| pollInterval | Duration | Yes | Time between polls when items exist | 30s |
| maxConcurrentDownloads | Integer | Yes | Semaphore limit for parallel downloads | 3 |
| maxRetries | Integer | Yes | Max retry attempts per item | 3 |
| supportedMimeTypes | List<String> | Yes | MIME types to process | [application/pdf, ...] |

**Acceptance Criteria:**

1. Background worker polls queue and processes attachments automatically
2. Worker starts when application starts (if enabled = true)
3. Worker respects batch size configuration
4. Worker uses exponential backoff when queue is empty
5. Worker can be disabled via configuration

---

#### STORY 2: PDF Text Extraction

> As a system, I want to extract text from PDF attachments so that PDF content is available for semantic search

**Requirement Details:**

1. Use Apache PDFBox library to extract text from PDF files
2. Handle multi-page PDFs (extract all pages)
3. Handle encrypted/password-protected PDFs gracefully (mark as 'failed' with descriptive error)
4. Handle corrupted PDFs gracefully (mark as 'failed')
5. Extracted text should preserve paragraph structure where possible

**Acceptance Criteria:**

1. PDF text extraction works for standard PDF files
2. Multi-page PDFs have all pages extracted
3. Encrypted PDFs are marked as 'failed' with error "Password-protected PDF"
4. Corrupted PDFs are marked as 'failed' with error "Invalid PDF format"
5. Empty PDFs (no extractable text) are marked as 'skipped'

---

#### STORY 3: DOCX Text Extraction

> As a system, I want to extract text from DOCX attachments so that Word document content is available for semantic search

**Requirement Details:**

1. Use Apache POI library to extract text from DOCX files
2. Extract text from paragraphs, tables, headers, footers
3. Handle corrupted DOCX files gracefully
4. Preserve document structure (headings, paragraphs) in extracted text

**Acceptance Criteria:**

1. DOCX text extraction works for standard Word documents
2. Text from tables is extracted with structure preserved
3. Corrupted DOCX files are marked as 'failed' with descriptive error
4. Empty DOCX files are marked as 'skipped'

---

#### STORY 4: Plain Text Reading

> As a system, I want to read text files directly so that plain text and markdown content is available for semantic search

**Requirement Details:**

1. Read `text/plain` and `text/markdown` files directly as UTF-8
2. No special processing needed — content is already text
3. Handle encoding issues gracefully (fallback to ISO-8859-1 if UTF-8 fails)

**Acceptance Criteria:**

1. Text files are read directly without transformation
2. Markdown files are read directly without transformation
3. Encoding errors are handled gracefully
4. Large text files (>1MB) are truncated with warning

---

#### STORY 5: Unsupported Type Handling

> As a system, I want to gracefully skip unsupported file types so that the processor doesn't fail on unknown formats

**Requirement Details:**

1. Check MIME type against `supportedMimeTypes` configuration
2. If not supported → mark as 'skipped' with reason
3. Do not attempt download for unsupported types (save bandwidth)
4. Log skipped items for monitoring

**Acceptance Criteria:**

1. Unsupported MIME types are skipped gracefully (no exception)
2. Skipped items have status = 'skipped' with reason field populated
3. Image files (image/*) are skipped with note "OCR not yet supported"
4. Unknown types are skipped with note "Unsupported MIME type: {type}"

---

#### STORY 6: Retry Logic

> As a system, I want to retry failed downloads so that transient network issues don't cause permanent failures

**Requirement Details:**

1. Download failure → retry up to 3 times with exponential backoff
2. Extract failure → mark as 'failed' immediately (no retry — likely corrupt file)
3. Ingest failure → retry up to 3 times (KB might be temporarily unavailable)
4. After max retries → mark as 'failed' with error message
5. Failed items can be manually reset to 'pending' for re-processing

**Error Handling:**

| Error Scenario | System Behavior |
|---------------|-----------------|
| Download timeout | Retry with exponential backoff (1s, 2s, 4s) |
| Download 404 | Mark 'failed' immediately — file doesn't exist |
| Download 429 (rate limit) | Retry after Retry-After header value |
| Extract exception | Mark 'failed' with exception message |
| KB unavailable | Retry with backoff, then mark 'failed' |
| Out of memory (large file) | Mark 'failed' with "File too large" |

**Acceptance Criteria:**

1. Failed downloads are retried up to maxRetries times
2. Exponential backoff between retries (1s, 2s, 4s)
3. After max retries, item is marked 'failed' with error message
4. 404 errors are not retried (immediate failure)
5. Rate limit responses are respected

---

#### STORY 7: Rate Limiting

> As a system, I want to limit concurrent downloads so that Jira API rate limits are respected

**Requirement Details:**

1. Semaphore with `maxConcurrentDownloads` permits (default: 3)
2. Delegate Jira rate limit handling to JiraRestClient
3. Exponential backoff when queue is empty (30s → 60s → 120s → 240s → 300s max)
4. Reset backoff when new items appear in queue

**Acceptance Criteria:**

1. Maximum concurrent downloads never exceeds configured limit
2. Jira rate limits (429 responses) are handled gracefully
3. Backoff increases exponentially when queue is empty
4. Backoff resets to base interval when items are found

---

#### STORY 8: Graceful Shutdown

> As a system, I want to shut down gracefully so that in-progress work is completed before stopping

**Requirement Details:**

1. On shutdown signal, stop accepting new batches
2. Wait for current batch to complete (with timeout)
3. Items in 'processing' state that didn't complete → reset to 'pending'
4. Log shutdown progress

**Acceptance Criteria:**

1. Graceful shutdown finishes current batch before stopping
2. Shutdown has a configurable timeout (default: 60s)
3. If timeout exceeded, force stop and reset incomplete items to 'pending'
4. No data loss on shutdown — incomplete items are re-processable

---

## 3. Dependencies

| Dependency | Type | Related Ticket | Description |
|------------|------|----------------|-------------|
| Database Schema | System | Story 1 | `jira_attachment_queue` table must exist with required columns |
| Jira REST Client | System | Story 2 | `downloadAttachment` method for streaming file downloads |
| Qdrant Vector DB | Infrastructure | N/A | Running Qdrant instance for vector storage |
| OpenAI API | External | N/A | API key for text-embedding-3-small model |
| Apache PDFBox | Library | N/A | PDF text extraction library |
| Apache POI | Library | N/A | DOCX text extraction library |

---

## 4. Stakeholders

| Role | Name / Team | Responsibility | Source |
|------|-------------|----------------|--------|
| Reporter | Duc Nguyen | Define requirements, accept delivery | Jira reporter |
| Developer | TBD | Implement the processor | Jira assignee |
| SA | SA Agent | Technical design review | Pipeline |

---

## 5. Risks and Assumptions

### 5.1 Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Large PDF files cause OOM | High | Medium | Implement file size limit (configurable, default 50MB) |
| Jira rate limiting blocks downloads | Medium | Medium | Respect rate limits, exponential backoff |
| Qdrant unavailability causes backlog | Medium | Low | Retry with backoff, items stay in queue |
| Corrupted files cause extraction crashes | Medium | Medium | Wrap extraction in try-catch, mark as 'failed' |
| Queue grows faster than processing | Low | Low | Monitor queue depth, scale batch size |

### 5.2 Assumptions

- Jira REST Client (Story 2) provides a working `downloadAttachment` method that handles authentication
- Database schema (Story 1) provides `jira_attachment_queue` table with columns: id, issue_key, filename, mime_type, download_url, status, error_message, created_at, processed_at, retry_count
- Qdrant is available at the configured host:port
- OpenAI API key is valid and has sufficient quota
- Attachments are typically < 50MB in size
- Processing latency of 30s-5min per batch is acceptable

---

## 6. Non-Functional Requirements

| Category | Requirement | Details |
|----------|-------------|---------|
| Performance | Process batch within 60s | 5 attachments per batch, 3 concurrent downloads |
| Performance | Poll interval configurable | Default 30s, backoff up to 5min when idle |
| Reliability | Retry failed operations | Max 3 retries with exponential backoff |
| Reliability | No data loss on shutdown | Graceful shutdown with item reset |
| Scalability | Configurable concurrency | Batch size and concurrent downloads adjustable |
| Availability | Auto-restart on crash | Coroutine supervisor handles restart |
| Monitoring | Log all state transitions | INFO level for success, WARN for skip, ERROR for failure |
| Security | No credential logging | Download URLs and API keys must not appear in logs |

---

## 7. Related Tickets

| Ticket Key | Summary | Status | Type | Relationship |
|------------|---------|--------|------|--------------|
| MTO-19 | Attachment Processor – Background Queue Worker | Docs Review | Story | Main ticket |
| Story 1 | Database Schema | Unknown | Story | Blocks MTO-19 (provides queue table) |
| Story 2 | Jira REST Client | Unknown | Story | Blocks MTO-19 (provides download method) |

---

## 8. Appendix

### Glossary

| Term | Definition |
|------|------------|
| Attachment Queue | Database table (`jira_attachment_queue`) holding pending attachment processing jobs |
| KB | Knowledge Base — vector database (Qdrant) storing searchable content |
| Embedding | Vector representation of text generated by OpenAI text-embedding-3-small model |
| MIME Type | Media type identifier (e.g., application/pdf, text/plain) |
| Backoff | Progressively increasing wait time between retries |
| Semaphore | Concurrency control mechanism limiting parallel operations |

### Reference Documents

| Document | Link / Location |
|----------|-----------------|
| Project Structure | .analysis/code-intelligence/project-structure.md |
| Existing KB Integration | src/main/kotlin/com/orchestrator/mcp/vectordb/ |
| Configuration Reference | src/main/resources/application.yml |

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Business Flow | [business-flow.png](diagrams/business-flow.png) | [business-flow.drawio](diagrams/business-flow.drawio) |
| 2 | Use Case Diagram | [use-case.png](diagrams/use-case.png) | [use-case.drawio](diagrams/use-case.drawio) |
