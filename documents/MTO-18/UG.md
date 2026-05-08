# User Guide (UG)

## Jira Project Sync Service — MTO-18: Ticket Crawler — Deep Content Sync & KB Ingestion

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-18 |
| Title | Ticket Crawler — Deep Content Sync & KB Ingestion |
| Author | DEV Agent |
| Reviewer | BA Agent |
| Version | 1.0 |
| Date | 2025-07-15 |
| Status | Final |
| Related BRD | BRD-v1-MTO-18.docx |
| Related FSD | FSD-v1-MTO-18.docx |
| Related TDD | TDD-v1-MTO-18.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-15 | DEV Agent | Initial document |

---

## 1. Introduction

### 1.1 Purpose

This guide explains how to operate and troubleshoot the **TicketCrawler** — a background service that performs deep content fetching for Jira tickets. It retrieves full descriptions, comments, builds a ticket relationship graph, and ingests enriched content into the Knowledge Base (KB) vector database for semantic search by AI agents.

### 1.2 Audience

| Audience | What They Need |
|----------|---------------|
| System Operator | How to trigger crawls, monitor progress, and handle failures |
| Developer | How to integrate with TicketCrawler API and understand data flow |
| DevOps | How to monitor KB ingestion health and tune performance |

### 1.3 Prerequisites

| Prerequisite | Version | Required |
|-------------|---------|----------|
| JDK | 21+ | Yes |
| PostgreSQL | 16+ | Yes |
| Qdrant Vector DB | Running | Yes |
| OpenAI API key | — | Yes (for embeddings) |
| MTO-15 (DB Schema) | Initialized | Yes |
| MTO-16 (Jira Client) | Configured | Yes |
| MTO-17 (Project Scanner) | Run at least once | Yes |

---

## 2. Getting Started

### 2.1 Quick Start

The Ticket Crawler runs automatically after the Project Scanner completes. It processes tickets that have been cached but not yet deep-fetched.

```bash
# Step 1: Ensure all prerequisites are configured
export JIRA_BASE_URL="https://your-domain.atlassian.net"
export JIRA_EMAIL="your-email@example.com"
export JIRA_API_TOKEN="your-api-token"
export OPENAI_API_KEY="sk-..."
export QDRANT_URL="http://localhost:6333"

# Step 2: Run a project scan first (populates ticket cache)
# POST http://localhost:8080/sync/start {"projectKey": "MTO"}

# Step 3: Crawler automatically processes cached tickets
# Monitor via:
# GET http://localhost:8080/sync/status/MTO
```

### 2.2 System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Memory | 512 MB | 1 GB |
| Qdrant | Running, accessible | Same host or LAN |
| OpenAI API | Valid key with quota | text-embedding-3-small model access |
| Network | Stable to Jira + Qdrant + OpenAI | Low-latency |

---

## 3. Configuration

### 3.1 Crawler Configuration

```yaml
sync:
  crawler:
    batchSize: 10              # Tickets per batch
    maxComments: 50            # Max comments to fetch per ticket
    maxDescriptionLength: 102400  # 100KB max description
    concurrency: 3             # Concurrent ticket fetches
    enabled: true              # Enable/disable crawler
```

### 3.2 KB Ingestion Configuration

```yaml
kb:
  qdrant:
    url: ${QDRANT_URL:http://localhost:6333}
    collection: jira_tickets
  openai:
    apiKey: ${OPENAI_API_KEY}
    model: text-embedding-3-small
```

### 3.3 Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `QDRANT_URL` | Yes | `http://localhost:6333` | Qdrant vector DB URL |
| `OPENAI_API_KEY` | Yes | — | OpenAI API key for embeddings |
| `CRAWL_BATCH_SIZE` | No | `10` | Tickets per crawl batch |
| `CRAWL_MAX_COMMENTS` | No | `50` | Max comments per ticket |
| `CRAWL_CONCURRENCY` | No | `3` | Concurrent ticket fetches |

---

## 4. Usage

### 4.1 How the Crawler Works

The crawler operates in a pipeline after the Project Scanner:

```
ProjectScanner (MTO-17)     TicketCrawler (MTO-18)
┌──────────────────┐        ┌──────────────────────────┐
│ Fetch metadata   │───────→│ 1. Query un-crawled      │
│ Cache in DB      │        │ 2. Fetch full content    │
│ (lightweight)    │        │ 3. Compute content hash  │
└──────────────────┘        │ 4. Build ticket graph    │
                            │ 5. Ingest into KB        │
                            │ 6. Queue attachments     │
                            └──────────────────────────┘
```

### 4.2 Content Hash Deduplication

The crawler uses SHA-256 hashing to avoid re-processing unchanged tickets:

1. Computes hash of `description + all comments`
2. Compares with stored `content_hash` in `jira_ticket_cache`
3. If unchanged → skip KB ingestion (saves API costs)
4. If changed → update hash, re-ingest into KB

### 4.3 Monitoring Crawl Progress

#### Via REST API

```bash
curl http://localhost:8080/sync/status/MTO
```

Response includes crawl phase:
```json
{
  "phases": {
    "scan": { "status": "completed", "progress": 100 },
    "crawl": { "status": "syncing", "progress": 45.0, "issuesCrawled": 30, "totalIssues": 67 },
    "attachments": { "status": "idle", "progress": 0 }
  }
}
```

#### Via SQL

```sql
-- Tickets needing crawl (no content hash yet)
SELECT COUNT(*) as pending_crawl
FROM jira_ticket_cache 
WHERE project_key = 'MTO' AND (content_hash IS NULL OR kb_ingested = FALSE);

-- Recently crawled tickets
SELECT ticket_key, summary, synced_at, kb_ingested
FROM jira_ticket_cache
WHERE project_key = 'MTO' AND kb_ingested = TRUE
ORDER BY synced_at DESC LIMIT 10;
```

### 4.4 Ticket Graph

The crawler builds a relationship graph from Jira issue links:

```sql
-- View relationships for a ticket
SELECT source_key, target_key, link_type, category
FROM jira_ticket_graph
WHERE source_key = 'MTO-14' OR target_key = 'MTO-14';

-- Count relationships per project
SELECT COUNT(*) as edge_count
FROM jira_ticket_graph g
JOIN jira_ticket_cache c ON g.source_key = c.ticket_key
WHERE c.project_key = 'MTO';
```

### 4.5 KB Search Verification

After crawling, verify content is searchable in KB:

```bash
# Via KB search tool
kb_search { "query": "MTO-15 database schema" }
```

---

## 5. Administration

### 5.1 Force Re-Crawl for Specific Tickets

```sql
-- Reset content hash to force re-crawl
UPDATE jira_ticket_cache 
SET content_hash = NULL, kb_ingested = FALSE
WHERE ticket_key IN ('MTO-15', 'MTO-16');
```

### 5.2 Force Re-Crawl for Entire Project

```sql
UPDATE jira_ticket_cache 
SET content_hash = NULL, kb_ingested = FALSE
WHERE project_key = 'MTO';
```

### 5.3 Monitor KB Ingestion Status

```sql
-- Ingestion progress
SELECT 
    COUNT(*) FILTER (WHERE kb_ingested = TRUE) as ingested,
    COUNT(*) FILTER (WHERE kb_ingested = FALSE) as pending,
    COUNT(*) as total
FROM jira_ticket_cache 
WHERE project_key = 'MTO';
```

### 5.4 Graph Statistics

```sql
-- Relationship type distribution
SELECT category, link_type, COUNT(*) as count
FROM jira_ticket_graph
GROUP BY category, link_type
ORDER BY count DESC;
```

---

## 6. Troubleshooting

### 6.1 Common Issues

| # | Symptom | Cause | Solution |
|---|---------|-------|----------|
| 1 | Tickets not being crawled | Scanner hasn't run yet | Run project scan first (MTO-17) |
| 2 | KB ingestion failing | Qdrant not accessible | Check `QDRANT_URL`, verify Qdrant is running |
| 3 | OpenAI embedding errors | Invalid API key or quota exceeded | Verify `OPENAI_API_KEY`, check billing |
| 4 | Crawl very slow | Too many comments per ticket | Reduce `maxComments` config |
| 5 | Memory issues | Large ticket descriptions | `maxDescriptionLength` truncates at 100KB |
| 6 | Duplicate KB entries | Re-crawl without hash check | Content hash deduplication prevents this normally |
| 7 | Graph edges missing | Ticket links not in Jira response | Ensure `getIssue` includes `issuelinks` field |

### 6.2 Error Handling

| Error | Auto-Recovery | Manual Action |
|-------|---------------|---------------|
| Jira API timeout | Retry 3x (via JiraRestClient) | Check network, increase timeout |
| ADF parse error | Skip ticket, log warning | Review ticket format in Jira |
| Qdrant connection refused | Retry with backoff | Restart Qdrant, check URL |
| OpenAI rate limit (429) | Retry with backoff | Wait or upgrade API plan |
| Content too large | Truncate at 100KB | Increase `maxDescriptionLength` if needed |

### 6.3 Logs

| Log Pattern | Meaning |
|-------------|---------|
| `INFO TicketCrawler - Crawling batch 3/7 (10 tickets)` | Batch processing |
| `INFO TicketCrawler - MTO-15: content unchanged (hash match), skipping KB` | Deduplication working |
| `INFO TicketCrawler - MTO-16: ingested into KB (1,234 chars)` | Successful ingestion |
| `WARN TicketCrawler - MTO-99: ADF parse error, using raw text` | Fallback to raw text |
| `ERROR TicketCrawler - KB ingestion failed for MTO-20: Qdrant timeout` | Ingestion failure |

### 6.4 FAQ

**Q: How does the crawler decide which tickets to process?**
A: It queries `jira_ticket_cache WHERE content_hash IS NULL OR kb_ingested = FALSE`. These are tickets that have been scanned (MTO-17) but not yet deep-fetched.

**Q: What content is ingested into KB?**
A: Title = `"{issue_key}: {summary}"`, Content = description + formatted comments, Tags = project_key, issue_type, status, labels.

**Q: How are Jira comments formatted?**
A: Each comment is formatted as: `"[Author Name, 2025-07-14]: comment body"` and concatenated with newlines.

**Q: What happens to attachments?**
A: The crawler queues them in `jira_attachment_queue` with status='PENDING'. The Attachment Processor (MTO-19) handles actual download and processing.

**Q: Can I re-ingest all tickets into KB?**
A: Yes. Reset `kb_ingested = FALSE` for the project (see §5.2), then trigger a new sync.

---

## 7. Appendix

### 7.1 Glossary

| Term | Definition |
|------|------------|
| Deep Content Fetch | Retrieving full description + comments (vs. metadata-only scan) |
| Content Hash | SHA-256 of description + comments for change detection |
| ADF | Atlassian Document Format — Jira's rich text format |
| KB Ingestion | Storing content as vector embeddings in Qdrant for semantic search |
| Ticket Graph | Directed graph of relationships between Jira tickets |

### 7.2 Related Documents

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-18.docx |
| FSD | FSD-v1-MTO-18.docx |
| TDD | TDD-v1-MTO-18.docx |
| DPG | DPG-v1-MTO-18.docx |
