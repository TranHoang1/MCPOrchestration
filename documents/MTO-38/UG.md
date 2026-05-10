# User Guide (UG)

## KB Server — User Guide

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-38 |
| Title | KB Server — User Guide |
| Author | DEV Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |

---

## 1. Introduction

The **KB Server** is a standalone MCP (Model Context Protocol) server that provides Knowledge Base services for AI agents. It enables semantic search, content ingestion, PII masking, audit logging, and knowledge graph visualization.

### 1.1 Who Is This For?

- **AI Agent developers** — configuring agents to use KB tools
- **DevOps engineers** — deploying and maintaining kb-server
- **System administrators** — managing security and access control

### 1.2 Key Features

- 13 MCP tools for knowledge management
- Semantic vector search (pgvector + HNSW)
- Automatic PII detection and masking
- Dual-priority queue for async processing
- Audit logging for compliance
- 3D knowledge graph visualization
- STDIO and HTTP transport modes

---

## 2. Quick Start

### 2.1 Prerequisites

- JDK 21 installed
- PostgreSQL 16+ with pgvector extension
- Ollama running locally (for embeddings)

### 2.2 Start in STDIO Mode (Development)

```bash
java -jar kb-server-all.jar --transport=stdio
```

### 2.3 Start in HTTP Mode

```bash
java -jar kb-server-all.jar --transport=http --config=kb-server.yml
```

Server starts on port 9181. Verify:
```bash
curl http://localhost:9181/api/graph/data
```

---

## 3. Configuration Reference

### 3.1 Configuration File

The kb-server uses YAML configuration. Pass via `--config=<path>` or use the bundled `application.yml`.

### 3.2 Server Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| kb.server.port | int | 9181 | HTTP server port |
| kb.server.transport | string | stdio | Transport mode (stdio/http) |

### 3.3 Database Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| kb.database.url | string | jdbc:postgresql://localhost:5432/mcp_orchestrator | JDBC URL |
| kb.database.schema | string | kb | Database schema name |
| kb.database.username | string | kb_app | DB username |
| kb.database.password | string | - | DB password (use env var) |
| kb.database.pool.maximum_size | int | 10 | Max connection pool size |
| kb.database.pool.minimum_idle | int | 2 | Min idle connections |
| kb.database.pool.idle_timeout_ms | int | 600000 | Idle connection timeout |
| kb.database.pool.connection_timeout_ms | int | 30000 | Connection timeout |

### 3.4 Embedding Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| kb.embedding.provider | string | ollama | Embedding provider |
| kb.embedding.model | string | nomic-embed-text | Model name |
| kb.embedding.base_url | string | http://localhost:11434 | Provider URL |
| kb.embedding.dimensions | int | 768 | Vector dimensions |
| kb.embedding.cache_enabled | boolean | true | Enable embedding cache |
| kb.embedding.cache_max_size | int | 200 | Max cache entries |
| kb.embedding.cache_ttl_minutes | int | 10 | Cache TTL |

### 3.5 Security Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| kb.security.encryption_key | string | - | AES-256 key for PII |
| kb.security.br_encryption_key | string | - | AES-256 key for BR |
| kb.security.default_role | string | developer | Default caller role |
| kb.security.rate_limit.pii_unmask_per_hour | int | 10 | PII unmask rate limit |
| kb.security.rate_limit.br_level1_per_hour | int | 5 | BR L1 unmask limit |
| kb.security.rate_limit.br_level2_per_hour | int | 15 | BR L2 unmask limit |
| kb.security.rate_limit.br_level3_per_hour | int | 30 | BR L3 unmask limit |

### 3.6 Queue Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| kb.queue.hpq_capacity | int | 100 | High-priority queue capacity |
| kb.queue.npq_capacity | int | 1000 | Normal-priority queue capacity |
| kb.queue.worker_count | int | 2 | Number of queue workers |
| kb.queue.watchdog_interval_seconds | int | 60 | Watchdog check interval |
| kb.queue.stuck_threshold_minutes | int | 5 | Task stuck threshold |
| kb.queue.max_retries | int | 3 | Max task retries |

### 3.7 Audit Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| kb.audit.enabled | boolean | true | Enable audit logging |
| kb.audit.retention_days | int | 90 | Audit log retention |

### 3.8 Minimal Configuration Example

```yaml
kb:
  server:
    port: 9181
    transport: http
  database:
    url: "jdbc:postgresql://localhost:5432/mcp_orchestrator"
    schema: "kb"
    username: "kb_app"
    password: "your_password"
  embedding:
    provider: "ollama"
    model: "nomic-embed-text"
    base_url: "http://localhost:11434"
    dimensions: 768
```

---

## 4. MCP Tools Reference

### 4.1 kb_search — Search Knowledge Base

**Description:** Semantic search across KB entries using vector similarity.

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| query | string | Yes | Search query (max 2000 chars) |
| project_key | string | No | Filter by project |
| top_k | int | No | Max results (1-20, default 5) |
| tags | string | No | Comma-separated tag filter |

**Example:**
```json
{"tool": "kb_search", "arguments": {"query": "queue system design", "top_k": 5}}
```

---

### 4.2 kb_ingest — Ingest Content

**Description:** Store content in KB with automatic PII masking and vector indexing.

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| title | string | Yes | Entry title |
| content | string | Yes | Content to ingest |
| issue_key | string | No | Jira issue key |
| tags | string | No | Comma-separated tags |

**Example:**
```json
{"tool": "kb_ingest", "arguments": {"title": "MTO-38 BRD", "content": "...", "tags": "brd,kb-server"}}
```

---

### 4.3 kb_read — Read Entry

**Description:** Read a specific KB entry by issue key.

**Parameters:** `issue_key` (required)

---

### 4.4 kb_delete — Delete Entry

**Description:** Delete a KB entry and its vector index.

**Parameters:** `issue_key` (required)

---

### 4.5 kb_link — Find Similar

**Description:** Find semantically similar KB entries.

**Parameters:** `issue_key` (required), `top_k` (optional, default 5)

---

### 4.6 kb_feedback — Submit Feedback

**Description:** Submit feedback on a KB entry quality.

**Parameters:** `issue_key` (required), `rating` (1-5), `comment` (optional)

---

### 4.7 kb_audit_query — Query Audit Logs

**Description:** Query audit events with filters.

**Parameters:** `event_type`, `issue_key`, `from_date`, `to_date`, `limit`

---

### 4.8 kb_sync_trigger — Trigger Sync

**Description:** Trigger Jira project sync to KB.

**Parameters:** `project_key` (required)

---

### 4.9 kb_sync_status — Check Sync Status

**Description:** Check current sync job progress.

**Parameters:** `job_id` (optional)

---

### 4.10 kb_unmask_pii — Unmask PII (Restricted)

**Description:** Unmask PII data for authorized users. Rate limited.

**Parameters:** `issue_key` (required)

**Rate Limit:** 10 requests per hour per session.

---

### 4.11 kb_unmask_br — Unmask Business Rules (Restricted)

**Description:** Unmask business rules by sensitivity level.

**Parameters:** `issue_key` (required), `level` (1-3)

---

### 4.12 kb_graph — Knowledge Graph

**Description:** Get knowledge graph data (nodes and edges).

**Parameters:** `project_key` (optional)

---

### 4.13 kb_network — Network Traversal

**Description:** BFS N-hop traversal from a starting node.

**Parameters:** `start_key` (required), `hops` (1-5, default 2)

---

## 5. Administration

### 5.1 Database Management

The kb-server auto-creates its schema on first startup. Tables:
- `kb.kb_entries` — Main KB storage
- `kb.audit_events` — Audit log
- `kb.queue_tasks` — Queue persistence

### 5.2 Queue Monitoring

The queue system runs automatically:
- **Worker** processes tasks from HPQ (high priority) before NPQ (normal)
- **Watchdog** checks every 60s for stuck tasks (>5min)
- **Crash Recovery** restores IN_PROGRESS tasks on restart

### 5.3 Graph Visualization

Access the 3D knowledge graph at: `http://localhost:9181/graph`

REST API:
- `GET /api/graph/data` — Full graph JSON
- `GET /api/graph/node/{id}` — Single node details

---

## 6. Troubleshooting

### 6.1 Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| Server won't start | Missing config | Check --config path |
| DB connection failed | Wrong credentials | Verify kb.database.* settings |
| Embedding timeout | Ollama not running | Start Ollama: `ollama serve` |
| Vector search empty | No embeddings indexed | Ingest content first |
| Rate limit error | Too many unmask calls | Wait 1 hour or increase limit |
| Queue tasks stuck | Worker crashed | Restart server (auto-recovery) |

### 6.2 Error Codes

| Code | Description | Action |
|------|-------------|--------|
| KB_VALIDATION_ERROR | Invalid input | Check parameters |
| KB_NOT_FOUND | Entry not found | Verify issue_key |
| KB_UNAUTHORIZED | Insufficient role | Check security config |
| KB_RATE_LIMITED | Rate limit exceeded | Wait and retry |
| KB_CONFIG_ERROR | Bad configuration | Fix config file |
| KB_INTERNAL_ERROR | Server error | Check logs |
| KB_DB_ERROR | Database error | Check PostgreSQL |
| KB_EMBEDDING_ERROR | Embedding failed | Check Ollama |

### 6.3 Log Analysis

Key log patterns to watch:
```
INFO  - KB Server v1.0.0 starting (transport=http)    # Startup
INFO  - Registered 13 KB tools                         # Tools ready
INFO  - Queue system started (worker + watchdog)       # Queue ready
WARN  - DB init skipped                                # DB not available
WARN  - Vector indexing failed                         # Non-fatal
ERROR - kb_search failed                               # Search error
```

---

## 7. FAQ

**Q: Can I run kb-server without PostgreSQL?**
A: No, PostgreSQL is required for data storage. The server will start but tools will fail.

**Q: Can I use a different embedding model?**
A: Yes, change `kb.embedding.model` in config. Ensure dimensions match.

**Q: How do I generate encryption keys?**
A: Use any 32-byte base64-encoded key. Example: `openssl rand -base64 32`

**Q: Can multiple kb-servers share the same database?**
A: Not recommended for v1. Single instance only.

**Q: How do I backup KB data?**
A: Standard PostgreSQL backup: `pg_dump -n kb mcp_orchestrator > kb_backup.sql`
