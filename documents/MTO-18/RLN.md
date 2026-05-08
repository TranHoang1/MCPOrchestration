# Release Notes (RLN)

## MCPOrchestration — MTO-18: Ticket Crawler – Deep Content Sync & KB Ingestion

---

## Release Information

| Field | Value |
|-------|-------|
| Release Version | 1.3.0 |
| Release Date | 2025-07-18 |
| Jira Ticket | MTO-18 |
| Environment | DEV / SIT / UAT / PROD |
| Author | DevOps Agent |
| Status | Final |
| Related Epic | MTO-14 — Jira Project Sync Service |

---

## 1. Release Summary

This release introduces the **Ticket Crawler** module — deep content fetching from Jira issues with SHA-256 content hash deduplication, bidirectional ticket relationship graph building, and automatic Knowledge Base vector ingestion via OpenAI embeddings + Qdrant.

**Release Type:** Feature addition (minor schema change — additive columns)
**Risk Level:** Low-Medium
**Backward Compatible:** Yes — new nullable columns, no existing behavior changed

---

## 2. What's New

### 2.1 New Features

| # | Feature | Description |
|---|---------|-------------|
| 1 | **Deep Content Fetch** | Retrieves full issue description + comments from Jira (ADF format) |
| 2 | **ADF Parser** | Converts Atlassian Document Format to plain text for indexing |
| 3 | **Content Hash Deduplication** | SHA-256 hash prevents re-processing unchanged tickets |
| 4 | **Ticket Relationship Graph** | Bidirectional edges (blocks/is-blocked-by, relates-to, parent/child) |
| 5 | **KB Vector Ingestion** | Automatic embedding generation + Qdrant storage for semantic search |
| 6 | **Attachment Queuing** | Discovers attachments and queues for background processing (MTO-19) |
| 7 | **Batch Processing** | Configurable batch size with delay between batches |

### 2.2 Technical Highlights

- **Deduplication-First** — Hash check before any expensive API call or embedding generation
- **Bidirectional Graph** — Every relationship creates 2 edges (forward + reverse)
- **Idempotent** — Re-crawling is safe (upsert everywhere, hash guards)
- **Memory-Efficient** — Processes tickets one batch at a time, no full dataset in memory

---

## 3. New Components

### 3.1 Package: `com.orchestrator.mcp.crawler`

| Class | Type | Responsibility |
|-------|------|----------------|
| `TicketCrawler` | Interface | Crawl lifecycle API |
| `TicketCrawlerImpl` | Class | Main orchestration |
| `ContentFetcher` | Class | Full issue content retrieval |
| `ContentHasher` | Class | SHA-256 computation + comparison |
| `GraphBuilder` | Class | Bidirectional edge creation |
| `KBIngestor` | Class | Embedding + vector upsert |
| `AdfParser` | Class | ADF JSON → plain text |
| `AttachmentQueuer` | Class | Queue attachment entries |
| `CrawlerConfig` | Data class | Configuration |
| `CrawlerModule` | Koin module | DI bindings |

---

## 4. Database Changes

### 4.1 Schema Migration

| Type | Object | Description |
|------|--------|-------------|
| New Column | `jira_ticket_cache.content_text` | Full text content (nullable TEXT) |
| New Column | `jira_ticket_cache.crawled_at` | Timestamp of last crawl (nullable TIMESTAMPTZ) |

### 4.2 Tables Populated

| Table | Data Written | Source |
|-------|-------------|--------|
| `jira_ticket_cache` | content_text, content_hash, crawled_at | Jira API |
| `jira_ticket_graph` | Bidirectional relationship edges | Issue links |
| `jira_attachment_queue` | Attachment metadata for download | Issue attachments |

---

## 5. Configuration Changes

```yaml
crawler:
  enabled: true
  batchSize: 10
  batchDelay: 2s
  maxContentSize: 102400
  maxComments: 50
  forceCrawl: false
```

---

## 6. Dependencies

### 6.1 No New External Dependencies

All libraries already present in the project.

### 6.2 External Service Dependencies

| Service | Purpose | Required |
|---------|---------|----------|
| Qdrant | Vector storage | ✅ For KB ingestion |
| OpenAI API | Embedding generation | ✅ For KB ingestion |
| Jira Cloud API | Content fetching | ✅ |

### 6.3 Internal Dependencies

| Module | Required | Relationship |
|--------|----------|-------------|
| MTO-15 (DB Schema) | ✅ | Tables: jira_ticket_cache, jira_ticket_graph, jira_attachment_queue |
| MTO-16 (Jira Client) | ✅ | JiraRestClient for API calls |
| MTO-17 (Scanner) | ✅ | Populates jira_ticket_cache with metadata first |

---

## 7. Testing Summary

| Level | Tests | Passed | Failed | Coverage |
|-------|-------|--------|--------|----------|
| Unit Tests | 18 | 18 | 0 | ContentHasher, AdfParser, GraphBuilder, CrawlerImpl |
| Integration Tests | 7 | 7 | 0 | Full crawl with Testcontainers + mock Qdrant |
| **Total** | **25** | **25** | **0** | — |

---

## 8. Known Issues & Limitations

| # | Issue | Severity | Workaround |
|---|-------|----------|------------|
| 1 | Large tickets (>100KB content) truncated | Low | Configurable maxContentSize |
| 2 | Comments limited to 50 per ticket | Low | Configurable maxComments |
| 3 | No incremental graph update (full rebuild per crawl) | Low | Upsert handles duplicates |

---

## 9. Rollback Instructions

1. Stop application
2. Restore previous JAR
3. Optionally drop new columns: `ALTER TABLE jira_ticket_cache DROP COLUMN content_text, DROP COLUMN crawled_at;`
4. Restart application

**Data loss risk:** Crawled content and graph edges will be lost if columns dropped. Vector DB points remain (manual cleanup if needed).

---

## 10. Future Roadmap

| Story | Status | Dependency on MTO-18 |
|-------|--------|---------------------|
| MTO-15: DB Schema | ✅ Deployed | Prerequisite |
| MTO-16: Jira Client | ✅ Deployed | Prerequisite |
| MTO-17: Project Scanner | ✅ Deployed | Prerequisite |
| **MTO-18: Ticket Crawler** | ✅ **This release** | — |
| MTO-19: Attachment Processor | Next | Uses attachment queue populated by crawler |
| MTO-20: MCP Tool Integration | Planned | Reads graph data from crawler |
| MTO-22: 3D Graph Visualization | Planned | Visualizes graph built by crawler |
