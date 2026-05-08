# Deployment Guide (DPG)

## MCPOrchestration — MTO-18: Ticket Crawler – Deep Content Sync & KB Ingestion

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-18 |
| Title | Ticket Crawler – Deep Content Sync & KB Ingestion |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2025-07-18 |
| Status | Final |
| Related TDD | TDD-v1-MTO-18.docx |
| Related FSD | FSD-v1-MTO-18.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-18 | DevOps Agent | Initial deployment guide for Ticket Crawler module |

---

## 1. Deployment Overview

### 1.1 Summary

This deployment introduces the **TicketCrawler** module — deep content fetching from Jira issues, content hash deduplication, ticket relationship graph building, and Knowledge Base vector ingestion. The change adds:

- `TicketCrawler` service for orchestrating crawl lifecycle
- `ContentFetcher` for full issue content retrieval
- `ContentHasher` for SHA-256 deduplication
- `GraphBuilder` for bidirectional relationship edge creation
- `KBIngestor` for vector DB embedding and storage
- `AdfParser` for Atlassian Document Format → plain text conversion
- `AttachmentQueuer` for populating the attachment download queue
- Database migration: 2 new columns on `jira_ticket_cache`

### 1.2 Deployment Type

| Aspect | Value |
|--------|-------|
| Type | Rolling update |
| Risk Level | Low-Medium — additive schema change (ALTER TABLE ADD COLUMN) |
| Rollback Complexity | Low — columns can be dropped, code reverted |
| Downtime Required | None (ALTER TABLE ADD COLUMN is non-blocking in PostgreSQL) |
| Data Migration | Schema migration only (new columns, nullable) |

### 1.3 Affected Components

| Component | Change Type | Impact |
|-----------|-------------|--------|
| `orchestrator-server` module | New classes added | New package `com.orchestrator.mcp.crawler` |
| PostgreSQL database | Schema migration | 2 new columns on `jira_ticket_cache` |
| `jira_ticket_graph` table | Populated | Edges created by GraphBuilder |
| Qdrant vector DB | New points | Ticket content embeddings |
| `AppModule.kt` (Koin DI) | Extended | CrawlerModule registered |
| `application.yml` | Extended | New `crawler` config section |
| Fat JAR | Rebuilt | Includes crawler classes |

---

## 2. Prerequisites

### 2.1 Infrastructure Requirements

| Requirement | Minimum | Recommended | Notes |
|-------------|---------|-------------|-------|
| PostgreSQL | 16.0 | 16.x latest | MTO-15 tables must exist |
| JVM | 21 | 21 LTS | GraalVM or OpenJDK |
| Qdrant | 1.9+ | Latest | Vector DB for KB ingestion |
| OpenAI API | — | — | For text-embedding-3-small |
| RAM (JVM) | 1 GB | 2 GB | Crawler processes content in-memory |
| Network | Outbound HTTPS | — | Jira API + OpenAI API + Qdrant |

### 2.2 Software Dependencies

| Dependency | Version | Already Present | Notes |
|------------|---------|-----------------|-------|
| Kotlin | 2.3.20 | ✅ Yes | No change |
| Exposed ORM | 0.61.0 | ✅ Yes | No change |
| kotlinx.coroutines | 1.10.2 | ✅ Yes | No change |
| Ktor Client | 3.4.0 | ✅ Yes | No change |
| java.security.MessageDigest | JDK 21 | ✅ Yes | SHA-256 hashing |

**No new external dependencies introduced.**

### 2.3 Pre-requisite Deployments

| Ticket | Component | Required | Status |
|--------|-----------|----------|--------|
| MTO-15 | Database Schema | ✅ Must be deployed | Tables exist |
| MTO-16 | Jira REST Client | ✅ Must be deployed | API client available |
| MTO-17 | Project Scanner | ✅ Must be deployed | Populates jira_ticket_cache |

### 2.4 Access Requirements

| Access | Purpose | Who |
|--------|---------|-----|
| Jira Cloud API | Fetch full issue content | Application |
| OpenAI API | Generate embeddings | Application |
| Qdrant | Store/query vectors | Application |
| PostgreSQL | Read/write ticket data + graph | Application |

---

## 3. Pre-Deployment Checklist

| # | Check | Command / Action | Expected Result |
|---|-------|-----------------|-----------------|
| 1 | MTO-17 deployed | Check scanner classes in JAR | Present |
| 2 | jira_ticket_cache has data | `SELECT COUNT(*) FROM jira_ticket_cache;` | > 0 |
| 3 | Qdrant accessible | `curl http://localhost:6333/collections` | 200 OK |
| 4 | OpenAI API key set | Verify `OPENAI_API_KEY` env var | Set |
| 5 | Fat JAR built | `./gradlew buildFatJar` | `BUILD SUCCESSFUL` |
| 6 | All tests pass | `./gradlew test` | All pass |
| 7 | Backup current JAR | `cp mcp-orchestrator-all.jar mcp-orchestrator-all.jar.bak` | Done |
| 8 | Backup database | `pg_dump -Fc mcp_orchestrator > backup_pre_mto18.dump` | Done |

---

## 4. Database Migration

### 4.1 Migration Script

```sql
-- V4__add_crawler_columns.sql
ALTER TABLE jira_ticket_cache ADD COLUMN IF NOT EXISTS content_text TEXT;
ALTER TABLE jira_ticket_cache ADD COLUMN IF NOT EXISTS crawled_at TIMESTAMPTZ;
```

### 4.2 Execution

```bash
# Run migration manually or let application auto-migrate on startup
psql -h localhost -U postgres -d mcp_orchestrator -f V4__add_crawler_columns.sql
```

### 4.3 Verification

```sql
SELECT column_name, data_type FROM information_schema.columns
WHERE table_name = 'jira_ticket_cache' AND column_name IN ('content_text', 'crawled_at');
-- Expected: 2 rows
```

### 4.4 Rollback Script

```sql
ALTER TABLE jira_ticket_cache DROP COLUMN IF EXISTS content_text;
ALTER TABLE jira_ticket_cache DROP COLUMN IF EXISTS crawled_at;
```

---

## 5. Configuration Changes

### 5.1 New Configuration Section

```yaml
crawler:
  enabled: true
  batchSize: 10
  batchDelay: 2s
  maxContentSize: 102400    # 100KB max content per ticket
  maxComments: 50           # Max comments to fetch per ticket
  forceCrawl: false         # Ignore hash, re-crawl all
```

---

## 6. Deployment Steps

### 6.1 Build Phase

```bash
git checkout MTO-18
git pull origin MTO-18
./gradlew clean test
./gradlew buildFatJar

# Verify crawler classes
jar tf build/libs/mcp-orchestrator-all.jar | grep "crawler/"
```

### 6.2 Deploy

```bash
# Stop application
sudo systemctl stop mcp-orchestrator

# Run DB migration
psql -h localhost -U postgres -d mcp_orchestrator -f V4__add_crawler_columns.sql

# Deploy JAR
cp build/libs/mcp-orchestrator-all.jar /opt/mcp-orchestrator/mcp-orchestrator-all.jar

# Update application.yml with crawler config

# Start application
sudo systemctl start mcp-orchestrator
sleep 10
```

---

## 7. Post-Deployment Verification

| # | Check | Method | Expected |
|---|-------|--------|----------|
| 1 | Application started | Check logs | `Application started` |
| 2 | Crawler module loaded | Check logs | `CrawlerModule registered` |
| 3 | DB columns exist | SQL query | content_text, crawled_at columns present |
| 4 | Qdrant connection | Check logs | No Qdrant connection errors |
| 5 | Existing features work | MCP tools/list | Returns tool list |

---

## 8. Rollback Plan

### 8.1 Rollback Steps

```bash
# Stop application
sudo systemctl stop mcp-orchestrator

# Restore JAR
cp /opt/mcp-orchestrator/mcp-orchestrator-all.jar.bak \
   /opt/mcp-orchestrator/mcp-orchestrator-all.jar

# Rollback DB (optional — columns are nullable, won't break old code)
psql -h localhost -U postgres -d mcp_orchestrator -c \
  "ALTER TABLE jira_ticket_cache DROP COLUMN IF EXISTS content_text;
   ALTER TABLE jira_ticket_cache DROP COLUMN IF EXISTS crawled_at;"

# Restart
sudo systemctl start mcp-orchestrator
```

### 8.2 Quick Disable

```yaml
crawler:
  enabled: false
```

---

## 9. Monitoring

| Log Pattern | Meaning | Action |
|-------------|---------|--------|
| `Crawl started for {project}` | Crawl initiated | None |
| `Ticket {key} hash unchanged, skipping` | Deduplication working | None |
| `KB ingestion completed for {key}` | Vector stored | None |
| `Graph edges created: {N}` | Relationships mapped | None |
| `OpenAI API error` | Embedding failure | Check API key/quota |
| `Qdrant connection refused` | Vector DB down | Check Qdrant service |

---

## Appendix: Related Documents

| Document | Reference |
|----------|-----------|
| BRD | BRD-v1-MTO-18.docx |
| FSD | FSD-v1-MTO-18.docx |
| TDD | TDD-v1-MTO-18.docx |
| STP | STP-v1-MTO-18.docx |
| Test Report | STR-v1-MTO-18.docx |
