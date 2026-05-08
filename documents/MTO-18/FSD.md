# Functional Specification Document (FSD)

## MCPOrchestration — MTO-18: Ticket Crawler – Deep Content Sync & KB Ingestion

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-18 |
| Title | Ticket Crawler – Deep Content Sync & KB Ingestion |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-18.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | BA Agent | Initial FSD |
| 1.0 | 2026-05-08 | TA Agent | Technical enrichment |

---

## 1. Introduction

### 1.1 Purpose

This FSD specifies the functional behavior of the TicketCrawler — a service that fetches full ticket content from Jira, deduplicates via content hashing, builds a ticket relationship graph, and ingests content into the Knowledge Base.

### 1.2 Scope

- Deep content fetching (description + comments)
- Content hash deduplication (SHA-256)
- Ticket graph building (bidirectional edges)
- KB ingestion (embedding + vector store)
- Attachment queue population

---

## 2. System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    MCPOrchestration App                       │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              crawler/ package (NEW)                   │    │
│  │                                                      │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────┐  │    │
│  │  │TicketCrawler │→ │ContentFetcher│→ │  Graph   │  │    │
│  │  │  (Service)   │  │ (Jira API)   │  │ Builder  │  │    │
│  │  └──────────────┘  └──────────────┘  └──────────┘  │    │
│  │         ↕                                    ↓       │    │
│  │  ┌──────────────┐                   ┌──────────────┐│    │
│  │  │Content Hasher│                   │ KB Ingestor  ││    │
│  │  └──────────────┘                   └──────────────┘│    │
│  └─────────────────────────────────────────────────────┘    │
│         ↕                    ↕                ↕              │
│  ┌──────────────┐    ┌──────────────┐  ┌──────────────┐    │
│  │  PostgreSQL   │    │  Jira API    │  │   Qdrant     │    │
│  └──────────────┘    └──────────────┘  └──────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Functional Requirements

### 3.1 Feature: Deep Content Fetch

**Use Case ID:** UC-01

**Main Flow:**

| Step | System | Description |
|------|--------|-------------|
| 1 | TicketCrawler | Query jira_ticket_cache WHERE content_hash IS NULL LIMIT batchSize |
| 2 | ContentFetcher | Call getIssue(key, fields="description,comment", expand="renderedFields") |
| 3 | ContentFetcher | Parse ADF description → plain text/markdown |
| 4 | ContentFetcher | Parse comments (body, author, created) |
| 5 | TicketCrawler | Concatenate: description + "\n---\n" + comments |
| 6 | TicketCrawler | Store content_text in jira_ticket_cache |

**Business Rules:**

| Rule ID | Rule |
|---------|------|
| BR-01 | Batch size configurable, default 10 |
| BR-02 | Max 50 comments per ticket (latest first) |
| BR-03 | Max content size: 100KB (truncate with warning) |
| BR-04 | ADF → plain text conversion (strip formatting) |

---

### 3.2 Feature: Content Hash Deduplication

**Use Case ID:** UC-02

**Main Flow:**

| Step | System | Description |
|------|--------|-------------|
| 1 | ContentHasher | Compute SHA-256(description + comments) |
| 2 | ContentHasher | Compare with stored content_hash |
| 3 | ContentHasher | If same → skip (return UNCHANGED) |
| 4 | ContentHasher | If different → return CHANGED, update hash |

**Business Rules:**

| Rule ID | Rule |
|---------|------|
| BR-05 | SHA-256 of UTF-8 encoded content string |
| BR-06 | Hash stored as hex string (64 chars) |
| BR-07 | NULL hash = never crawled = always process |

---

### 3.3 Feature: Ticket Graph Builder

**Use Case ID:** UC-03

**Main Flow:**

| Step | System | Description |
|------|--------|-------------|
| 1 | GraphBuilder | Parse metadata_json.issuelinks from ticket |
| 2 | GraphBuilder | Extract: type, inward/outward issue key |
| 3 | GraphBuilder | Map to relationship types |
| 4 | GraphBuilder | Upsert bidirectional edges into jira_ticket_graph |

**Relationship Mapping:**

| Jira Link Type | Forward Edge | Reverse Edge |
|---------------|-------------|--------------|
| Blocks | blocks | is-blocked-by |
| is blocked by | is-blocked-by | blocks |
| relates to | relates-to | relates-to |
| is parent of | parent | child |
| is child of | child | parent |
| Epic Link | epic | story-of-epic |

**Business Rules:**

| Rule ID | Rule |
|---------|------|
| BR-08 | Bidirectional: always insert both directions |
| BR-09 | Upsert: ON CONFLICT (source, target, relationship) DO NOTHING |
| BR-10 | Also extract parent field as parent/child edge |

---

### 3.4 Feature: KB Ingestion

**Use Case ID:** UC-04

**Main Flow:**

| Step | System | Description |
|------|--------|-------------|
| 1 | KBIngestor | Format title: "{issue_key}: {summary}" |
| 2 | KBIngestor | Format content: description + comments |
| 3 | KBIngestor | Set tags: project_key, issue_type, status, labels |
| 4 | KBIngestor | Generate embedding via EmbeddingService |
| 5 | KBIngestor | Store in Qdrant via VectorDbClient |

**Business Rules:**

| Rule ID | Rule |
|---------|------|
| BR-11 | Title: "{issue_key}: {summary}" |
| BR-12 | Tags: project_key, issue_type, status + all labels |
| BR-13 | Chunk content > 8000 tokens into overlapping segments |
| BR-14 | Only ingest if content hash CHANGED |

---

### 3.5 Feature: Attachment Queue Population

**Use Case ID:** UC-05

**Main Flow:**

| Step | System | Description |
|------|--------|-------------|
| 1 | TicketCrawler | Extract attachments from issue response |
| 2 | TicketCrawler | For each attachment: check if already in queue |
| 3 | TicketCrawler | If not queued: INSERT into jira_attachment_queue |

**Business Rules:**

| Rule ID | Rule |
|---------|------|
| BR-15 | Dedup by download_url (unique constraint) |
| BR-16 | Initial status = 'pending' |
| BR-17 | Store: issue_key, filename, mime_type, download_url, file_size |

---

## 4. Data Model

### 4.1 Updated Entity: jira_ticket_cache

| Attribute | Type | New? | Description |
|-----------|------|------|-------------|
| content_text | TEXT | Yes | Full description + comments |
| content_hash | VARCHAR(64) | Existing | SHA-256 hash for dedup |
| crawled_at | TIMESTAMPTZ | Yes | When content was last fetched |

### 4.2 Entity: jira_ticket_graph

| Attribute | Type | Description |
|-----------|------|-------------|
| id | SERIAL (PK) | Auto-increment |
| source_key | VARCHAR(20) | Source ticket key |
| target_key | VARCHAR(20) | Target ticket key |
| relationship | VARCHAR(50) | Relationship type |
| project_key | VARCHAR(20) | Project for filtering |

---

## 5. Processing Logic (Pseudocode)

```kotlin
suspend fun crawl(projectKey: String, options: CrawlOptions): CrawlResult {
    var processed = 0
    var skipped = 0
    var ingested = 0
    
    while (true) {
        val batch = repository.getUncrawledTickets(projectKey, options.batchSize)
        if (batch.isEmpty()) break
        
        for (ticket in batch) {
            // Fetch full content
            val content = contentFetcher.fetchContent(ticket.issueKey)
            
            // Hash check
            val newHash = contentHasher.computeHash(content)
            if (newHash == ticket.contentHash) {
                skipped++
                continue
            }
            
            // Update cache with content
            repository.updateContent(ticket.issueKey, content.text, newHash)
            
            // Build graph edges
            graphBuilder.buildEdges(ticket.issueKey, content.links, projectKey)
            
            // Ingest into KB
            kbIngestor.ingest(ticket.issueKey, ticket.summary, content.text, ticket.metadata)
            ingested++
            
            // Queue attachments
            attachmentQueuer.queueAttachments(ticket.issueKey, content.attachments)
            
            processed++
        }
        
        delay(options.batchDelay) // Rate limit respect
    }
    
    return CrawlResult(processed, skipped, ingested)
}
```

---

## 6. Non-Functional Requirements

| Category | Target |
|----------|--------|
| Throughput | 50 tickets/minute (with content fetch) |
| Hash comparison | < 1ms per ticket |
| KB ingestion | < 3s per ticket (embedding + store) |
| Resumability | Checkpoint via content_hash (NULL = not crawled) |

---

## 7. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | System Context | [system-context.png](diagrams/system-context.png) | [system-context.drawio](diagrams/system-context.drawio) |
| 2 | Sequence - Crawl | [sequence-crawl.png](diagrams/sequence-crawl.png) | [sequence-crawl.drawio](diagrams/sequence-crawl.drawio) |
| 3 | State - Content | [state-content.png](diagrams/state-content.png) | [state-content.drawio](diagrams/state-content.drawio) |
