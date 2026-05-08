# Technical Design Document (TDD)

## MCPOrchestration — MTO-18: Ticket Crawler – Deep Content Sync & KB Ingestion

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-18 |
| Title | Ticket Crawler – Deep Content Sync & KB Ingestion |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-18.docx |
| Related FSD | FSD-v1-MTO-18.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | SA Agent | Initial TDD |

---

## 1. Introduction

### 1.1 Purpose

Technical design for the TicketCrawler — deep content fetching, content hash deduplication, ticket graph building, and KB ingestion.

### 1.2 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.3.20 |
| Coroutines | kotlinx.coroutines | 1.10.2 |
| Hashing | java.security.MessageDigest | JDK 21 |
| Database | PostgreSQL + Exposed | 16+ / 0.61.0 |
| Vector DB | Qdrant | 1.9+ |
| Embeddings | OpenAI text-embedding-3-small | — |
| HTTP Client | Ktor Client (CIO) | 3.4.0 |

### 1.3 Design Principles

- **Deduplication-first** — hash check before any expensive operation
- **Batch processing** — configurable batch size with delay between batches
- **Idempotent** — re-crawling a ticket is safe (upsert everywhere)
- **Graph completeness** — bidirectional edges for all relationships

---

## 2. System Architecture

### 2.1 Component Diagram

| Component | Responsibility | Technology |
|-----------|---------------|------------|
| TicketCrawler | Orchestrates crawl lifecycle | Kotlin Coroutines |
| ContentFetcher | Fetches full issue content from Jira | JiraRestClient |
| ContentHasher | SHA-256 hash computation and comparison | java.security |
| GraphBuilder | Parses links, upserts bidirectional edges | Exposed ORM |
| KBIngestor | Formats and ingests into vector DB | EmbeddingService + VectorDbClient |
| AttachmentQueuer | Populates attachment queue | Exposed ORM |

---

## 3. API Design

### 3.1 Internal API

```kotlin
interface TicketCrawler {
    suspend fun crawl(projectKey: String, options: CrawlOptions = CrawlOptions()): CrawlResult
    suspend fun crawlSingle(issueKey: String): CrawlItemResult
    fun isRunning(projectKey: String): Boolean
}

data class CrawlOptions(
    val batchSize: Int = 10,
    val batchDelay: Duration = 2.seconds,
    val forceCrawl: Boolean = false  // Ignore hash, re-crawl all
)

data class CrawlResult(
    val processed: Int,
    val skipped: Int,    // Hash unchanged
    val ingested: Int,   // New/updated in KB
    val graphEdges: Int, // Edges created
    val attachmentsQueued: Int,
    val duration: Duration
)
```

---

## 4. Database Design

### 4.1 Schema Changes

```sql
-- Add columns to jira_ticket_cache (migration)
ALTER TABLE jira_ticket_cache ADD COLUMN content_text TEXT;
ALTER TABLE jira_ticket_cache ADD COLUMN crawled_at TIMESTAMPTZ;

-- jira_ticket_graph table (from MTO-15 schema)
CREATE TABLE jira_ticket_graph (
    id            SERIAL PRIMARY KEY,
    source_key    VARCHAR(20) NOT NULL,
    target_key    VARCHAR(20) NOT NULL,
    relationship  VARCHAR(50) NOT NULL,
    project_key   VARCHAR(20) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(source_key, target_key, relationship)
);

CREATE INDEX idx_graph_project ON jira_ticket_graph(project_key);
CREATE INDEX idx_graph_source ON jira_ticket_graph(source_key);
CREATE INDEX idx_graph_target ON jira_ticket_graph(target_key);
```

### 4.2 Query Patterns

| Operation | Query | Performance |
|-----------|-------|-------------|
| Get uncrawled | `SELECT * FROM jira_ticket_cache WHERE project_key=? AND content_hash IS NULL LIMIT ?` | < 10ms |
| Update content | `UPDATE jira_ticket_cache SET content_text=?, content_hash=?, crawled_at=NOW() WHERE issue_key=?` | < 5ms |
| Upsert edge | `INSERT INTO jira_ticket_graph (...) ON CONFLICT (source_key, target_key, relationship) DO NOTHING` | < 5ms |
| Check attachment | `SELECT 1 FROM jira_attachment_queue WHERE download_url = ?` | < 1ms |

---

## 5. Class / Module Design

### 5.1 Package Structure

```
com.orchestrator.mcp/
└── crawler/
    ├── TicketCrawler.kt               # Interface
    ├── TicketCrawlerImpl.kt           # Main orchestration
    ├── ContentFetcher.kt              # Jira API content fetch
    ├── ContentHasher.kt               # SHA-256 hashing
    ├── GraphBuilder.kt                # Relationship graph builder
    ├── KBIngestor.kt                  # KB ingestion (reuse from attachment/)
    ├── AttachmentQueuer.kt            # Queue attachment entries
    ├── AdfParser.kt                   # Atlassian Document Format → text
    ├── config/
    │   └── CrawlerConfig.kt           # Configuration
    ├── model/
    │   ├── CrawlOptions.kt
    │   ├── CrawlResult.kt
    │   ├── TicketContent.kt           # Parsed content DTO
    │   └── TicketRelationship.kt      # Graph edge model
    └── di/
        └── CrawlerModule.kt           # Koin DI
```

### 5.2 Key Implementation

#### ContentHasher

```kotlin
class ContentHasher {
    fun computeHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    fun hasChanged(newHash: String, existingHash: String?): Boolean {
        return existingHash == null || existingHash != newHash
    }
}
```

#### GraphBuilder

```kotlin
class GraphBuilder(private val repository: GraphRepository) {
    suspend fun buildEdges(issueKey: String, links: List<IssueLink>, projectKey: String) {
        for (link in links) {
            val (forward, reverse) = mapRelationship(link)
            
            // Forward edge
            repository.upsertEdge(issueKey, link.targetKey, forward, projectKey)
            // Reverse edge (bidirectional)
            repository.upsertEdge(link.targetKey, issueKey, reverse, projectKey)
        }
    }
    
    private fun mapRelationship(link: IssueLink): Pair<String, String> = when (link.type) {
        "Blocks" -> "blocks" to "is-blocked-by"
        "is blocked by" -> "is-blocked-by" to "blocks"
        "Relates" -> "relates-to" to "relates-to"
        else -> link.type.lowercase() to link.type.lowercase()
    }
}
```

#### AdfParser

```kotlin
class AdfParser {
    fun toPlainText(adfJson: JsonElement?): String {
        if (adfJson == null) return ""
        return extractText(adfJson).trim()
    }
    
    private fun extractText(node: JsonElement): String {
        // Recursively extract text from ADF content nodes
        return when {
            node is JsonObject && node.containsKey("text") -> 
                node["text"]!!.jsonPrimitive.content
            node is JsonObject && node.containsKey("content") ->
                node["content"]!!.jsonArray.joinToString("") { extractText(it) }
            node is JsonArray ->
                node.joinToString("\n") { extractText(it) }
            else -> ""
        }
    }
}
```

---

## 6. Integration Design

### 6.1 Jira REST API (Full Content)

| Attribute | Value |
|-----------|-------|
| Endpoint | `GET /rest/api/3/issue/{issueKey}` |
| Fields | description, comment |
| Expand | renderedFields |
| Auth | Basic Auth |
| Timeout | 30s |
| Retry | 3 times, exponential backoff |

**Response Fields Used:**

| Field | Purpose |
|-------|---------|
| fields.description | ADF content → plain text |
| fields.comment.comments[] | Array of comments |
| fields.comment.comments[].body | Comment ADF content |
| fields.comment.comments[].author.displayName | Comment author |
| fields.comment.comments[].created | Comment timestamp |
| fields.issuelinks[] | Relationship edges |
| fields.attachment[] | Attachment metadata for queue |
| fields.parent.key | Parent issue key |

### 6.2 Qdrant Vector DB

| Attribute | Value |
|-----------|-------|
| Collection | mcp_tools (existing) |
| Point ID | Deterministic UUID from issue_key |
| Vector | 768 dimensions |
| Payload | title, content_preview, tags, issue_key, project_key |

---

## 7. Security Design

| Data | Protection |
|------|------------|
| Ticket content | Stored in PostgreSQL (internal) |
| Comments | May contain sensitive info — stored as-is |
| API credentials | Environment variables |
| Content hash | Non-reversible (SHA-256) |

---

## 8. Performance

| Metric | Target |
|--------|--------|
| Crawl throughput | 50 tickets/min |
| Hash computation | < 1ms per ticket |
| Graph edge upsert | < 5ms per edge |
| KB ingestion | < 3s per ticket |
| Memory per ticket | < 1MB (content + processing) |

---

## 9. Configuration

```yaml
crawler:
  enabled: true
  batchSize: 10
  batchDelay: 2s
  maxContentSize: 102400  # 100KB
  maxComments: 50
  forceCrawl: false
```

---

## 10. Implementation Checklist

| # | Task | File | Priority |
|---|------|------|----------|
| 1 | CrawlerConfig | crawler/config/CrawlerConfig.kt | High |
| 2 | Domain models | crawler/model/*.kt | High |
| 3 | AdfParser | crawler/AdfParser.kt | High |
| 4 | ContentHasher | crawler/ContentHasher.kt | High |
| 5 | ContentFetcher | crawler/ContentFetcher.kt | High |
| 6 | GraphBuilder | crawler/GraphBuilder.kt | High |
| 7 | AttachmentQueuer | crawler/AttachmentQueuer.kt | High |
| 8 | KBIngestor (reuse) | crawler/KBIngestor.kt | High |
| 9 | TicketCrawlerImpl | crawler/TicketCrawlerImpl.kt | High |
| 10 | CrawlerModule (Koin) | crawler/di/CrawlerModule.kt | High |
| 11 | DB migration | V3__add_content_columns.sql | High |
| 12 | Unit tests | test/.../crawler/*.kt | High |
| 13 | Integration test | test/.../crawler/it/*.kt | Medium |

---

## 11. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Architecture | [architecture.png](diagrams/architecture.png) | [architecture.drawio](diagrams/architecture.drawio) |
| 2 | Component | [component.png](diagrams/component.png) | [component.drawio](diagrams/component.drawio) |
| 3 | Class Diagram | [class-diagram.png](diagrams/class-diagram.png) | [class-diagram.drawio](diagrams/class-diagram.drawio) |
| 4 | Sequence - Crawl | [api-sequence-crawl.png](diagrams/api-sequence-crawl.png) | [api-sequence-crawl.drawio](diagrams/api-sequence-crawl.drawio) |
| 5 | DB Schema | [db-schema.png](diagrams/db-schema.png) | [db-schema.drawio](diagrams/db-schema.drawio) |
