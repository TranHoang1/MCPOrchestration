# Business Requirements Document (BRD)

## MCPOrchestration — MTO-18: Ticket Crawler – Deep Content Sync & KB Ingestion

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-18 |
| Title | Ticket Crawler – Deep Content Sync & KB Ingestion |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | BA Agent | Initial BRD |

---

## 1. Introduction

### 1.1 Scope

Implement a **TicketCrawler** component that performs deep content fetching for Jira tickets — retrieving full descriptions, comments, and building a ticket relationship graph. It uses content hashing for deduplication and ingests enriched content into the Knowledge Base vector database for semantic search by AI agents.

### 1.2 Out of Scope

- Attachment downloading/processing (handled by MTO-19)
- Breadth-first metadata scanning (handled by MTO-17)
- MCP tool exposure (handled by MTO-20)
- UI/Dashboard for monitoring (handled by MTO-21)

### 1.3 Preliminary Requirements

| # | Prerequisite | Source |
|---|-------------|--------|
| 1 | Database tables (jira_ticket_cache, jira_ticket_graph) | MTO-15 |
| 2 | JiraRestClient.getIssue() with full fields | MTO-16 |
| 3 | ProjectScanner has populated jira_ticket_cache | MTO-17 |
| 4 | KB vector DB (Qdrant) accessible | Infrastructure |

---

## 2. Business Requirements

### 2.1 High Level Process Map

1. Query jira_ticket_cache for tickets needing deep content (content_hash IS NULL or stale)
2. Fetch full content from Jira (description + comments)
3. Compute SHA-256 content hash for deduplication
4. Skip if hash unchanged (content not modified)
5. Parse issue links → build ticket graph
6. Format content for KB ingestion
7. Ingest into Knowledge Base vector DB
8. Queue attachments for processing (MTO-19)

### 2.2 User Stories

| # | Story | Priority |
|---|-------|----------|
| 1 | As a system, I want to fetch full ticket content so that AI agents can search descriptions and comments | MUST HAVE |
| 2 | As a system, I want content hash deduplication so that unchanged tickets are not re-ingested (saving API/KB costs) | MUST HAVE |
| 3 | As a system, I want to build a ticket relationship graph so that dependency analysis is possible | MUST HAVE |
| 4 | As a system, I want to ingest ticket content into KB so that semantic search works across all tickets | MUST HAVE |
| 5 | As a system, I want to queue attachments for processing so that attachment content becomes searchable | MUST HAVE |
| 6 | As a system, I want configurable batch processing so that API rate limits are respected | SHOULD HAVE |

---

### 2.3 Story Details

#### STORY 1: Deep Content Fetch

**Requirements:**
1. Query tickets from jira_ticket_cache WHERE content_hash IS NULL OR needs_refresh = true
2. Call getIssue(key, fields="description,comment", expand="renderedFields")
3. Parse description (ADF → markdown or plain text)
4. Parse comments (body, author, created timestamp)
5. Store full content in jira_ticket_cache.content_text column
6. Configurable batch size (default: 10 tickets per batch)

**Acceptance Criteria:**
- Full description fetched and stored for tickets without content
- All comments fetched with author and timestamp
- ADF (Atlassian Document Format) converted to readable text
- Batch processing respects rate limits

#### STORY 2: Content Hash Deduplication

**Requirements:**
1. Compute SHA-256 hash of (description + all comments concatenated)
2. Compare new hash vs stored content_hash in jira_ticket_cache
3. If hash unchanged → skip KB ingestion (save cost)
4. If hash changed → update content_hash, re-ingest into KB

**Acceptance Criteria:**
- SHA-256 hash computed correctly
- Unchanged tickets skipped (verified by log)
- Changed tickets re-ingested with updated hash

#### STORY 3: Ticket Graph Builder

**Requirements:**
1. Parse issue links from ticket metadata_json
2. Extract relationships: parent/child, blocks/is-blocked-by, relates-to, epic-link
3. Upsert into jira_ticket_graph table
4. Bidirectional: if A blocks B → insert (A→B, blocks) AND (B→A, is-blocked-by)

**Acceptance Criteria:**
- All relationship types extracted correctly
- Bidirectional edges created
- Duplicate edges handled (upsert)
- Graph queryable for dependency analysis

#### STORY 4: KB Ingestion

**Requirements:**
1. Format: Title = "{issue_key}: {summary}"
2. Content = description + formatted comments
3. Tags = project_key, issue_type, status, labels
4. Metadata = assignee, priority, sprint
5. Generate embedding and store in Qdrant

**Acceptance Criteria:**
- Content ingested into KB successfully
- Searchable via KB search tool
- Tags enable filtered search

#### STORY 5: Attachment Queue

**Requirements:**
1. Scan attachments from issue metadata
2. Insert into jira_attachment_queue (status = 'pending')
3. Do NOT download/process (MTO-19 handles that)
4. Skip already-queued attachments (by download_url)

**Acceptance Criteria:**
- Attachments queued for processing
- No duplicate queue entries
- Queue populated with correct metadata (filename, mime_type, url, size)

---

## 3. Dependencies

| Dependency | Type | Related Ticket |
|------------|------|----------------|
| Database Schema | System | MTO-15 |
| Jira REST Client | System | MTO-16 |
| Project Scanner (ticket cache data) | System | MTO-17 |
| Qdrant Vector DB | Infrastructure | — |
| OpenAI Embedding API | External | — |

---

## 4. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| Performance | Process 50 tickets/minute with content fetch |
| Performance | Hash comparison < 1ms per ticket |
| Reliability | Checkpoint after each batch (resumable) |
| Scalability | Handle projects with 5000+ tickets |
| Cost | Skip unchanged tickets to minimize API/embedding costs |

---

## 5. Risks and Assumptions

| Risk | Mitigation |
|------|------------|
| Large descriptions cause memory issues | Truncate at 100KB |
| Many comments per ticket (100+) | Paginate comment fetch, limit to latest 50 |
| Jira ADF format complex to parse | Use existing ADF→text libraries |
| KB ingestion rate limited | Batch with configurable delay |

---

## 6. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Business Flow | [business-flow.png](diagrams/business-flow.png) | [business-flow.drawio](diagrams/business-flow.drawio) |
| 2 | Use Case | [use-case.png](diagrams/use-case.png) | [use-case.drawio](diagrams/use-case.drawio) |
