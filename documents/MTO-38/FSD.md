# Functional Specification Document (FSD)

## MCPOrchestration — MTO-38: KB Server — Tách Knowledge Base thành MCP Server Riêng Biệt

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-38 |
| Title | KB Server — Tách Knowledge Base thành MCP Server Riêng Biệt |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-38.docx |
| Related TDD (ref) | documents/MTO-25/TDD.md |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | BA Agent | Initial FSD — functional specification from BRD |
| 1.1 | 2026-05-09 | TA Agent | Technical enrichment — API contracts, pseudocode, integration specs |

---

## 1. Introduction

### 1.1 Purpose

This FSD specifies the functional behavior of the **kb-server** module — a standalone MCP server that provides Knowledge Base services (search, ingestion, PII masking, audit, graph visualization) as an independent deployable unit, separated from the orchestrator-server monolith.

### 1.2 Scope

- 13 MCP tools exposed via KbToolRegistrar
- STDIO and HTTP transport modes
- Queue system (DualPriorityQueue with HPQ/NPQ)
- PII masking pipeline (detect, mask, store)
- Vector search (pgvector + HNSW index)
- Audit logging for sensitive operations
- Graph visualization REST API + 3D UI
- Network service (BFS N-hop traversal)
- MCP roots/list protocol for workspace resolution
- External YAML configuration

### 1.3 Definitions and Acronyms

| Term | Definition |
|------|------------|
| MCP | Model Context Protocol — standard for AI agent to tool communication |
| KB | Knowledge Base — structured storage for project knowledge |
| STDIO | Standard Input/Output — transport mode for local development |
| HPQ | High-Priority Queue — for user-initiated tasks |
| NPQ | Normal-Priority Queue — for batch/system tasks |
| PII | Personally Identifiable Information |
| BR | Business Rules — sensitive business logic |
| HNSW | Hierarchical Navigable Small World — vector index algorithm |
| pgvector | PostgreSQL extension for vector similarity search |
| RLS | Row-Level Security — database access control |

### 1.4 References

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-38.docx |
| Architecture Review | documents/MTO-25/KB-SERVER-ARCHITECTURE-REVIEW.md |
| TDD (MTO-25) | documents/MTO-25/TDD.md |

---

## 2. System Overview

### 2.1 System Context

The kb-server operates as an upstream MCP server managed by the orchestrator-server. AI agents (Claude, Kiro) communicate with the orchestrator, which routes KB-related tool calls to kb-server.

**Actors:**
- **AI Agent** — Claude/Kiro IDE agent that calls KB tools
- **Orchestrator Server** — Routes MCP tool calls to appropriate upstream servers
- **KB Server** — Processes KB operations (this system)
- **PostgreSQL** — Persistent storage with pgvector extension
- **Ollama** — Local embedding model provider
- **Browser User** — Accesses graph visualization UI

### 2.2 System Boundaries

| In Scope | Out of Scope |
|----------|-------------|
| 13 KB MCP tools | Orchestrator routing logic |
| STDIO + HTTP transport | Frontend KB management UI |
| Queue processing | Distributed multi-node deployment |
| PII masking | OCR integration |
| Vector search | Jira crawler/scanner |
| Graph visualization | LangChain4j AI segmentation |
| Audit logging | User authentication (delegated to orchestrator) |

---

## 3. Functional Requirements

### 3.1 Use Case: UC-01 — Search Knowledge Base

**Actor:** AI Agent (via orchestrator)
**Priority:** MUST HAVE
**Trigger:** Agent calls kb_search tool

#### Main Flow

| Step | Actor | Action | System Response |
|------|-------|--------|-----------------|
| 1 | Agent | Calls kb_search with query string | Validates query (non-empty, max 2000 chars) |
| 2 | System | Generates embedding from query | Calls Ollama embedding API |
| 3 | System | Performs vector similarity search | Queries pgvector with HNSW index |
| 4 | System | Applies filters (project_key, tags) | Filters results post-search |
| 5 | System | Falls back to keyword search if vector empty | Uses PostgreSQL full-text search |
| 6 | System | Formats and returns results | Returns JSON with issue_key, content, score |
| 7 | System | Logs audit event | Records SEARCH event |

#### Alternative Flows

| ID | Condition | Action |
|----|-----------|--------|
| AF-01 | Vector DB unavailable | Fall back to keyword-only search |
| AF-02 | Embedding service unavailable | Fall back to keyword-only search |
| AF-03 | No results found | Return empty results array |

#### Exception Flows

| ID | Condition | Action |
|----|-----------|--------|
| EF-01 | Query is empty/blank | Return KB_VALIDATION_ERROR |
| EF-02 | Query exceeds 2000 chars | Return KB_VALIDATION_ERROR |
| EF-03 | Database connection failed | Return KB_INTERNAL_ERROR |

#### API Contract — kb_search

**Input Schema:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| query | string | Yes | - | Natural language search query (max 2000 chars) |
| project_key | string | No | - | Filter by Jira project key |
| top_k | integer | No | 5 | Max results (1-20) |
| include_technical | boolean | No | true | Include technical content |
| tags | string | No | - | Comma-separated tags filter |

**Response Schema:**

| Field | Type | Description |
|-------|------|-------------|
| results | array | List of matching entries |
| results[].issue_key | string | Jira issue key |
| results[].project_key | string | Project key |
| results[].content | string | Entry content (public) |
| results[].score | float | Relevance score (0-1) |
| results[].created_at | string | ISO timestamp |
| results[].updated_at | string | ISO timestamp |
| total | integer | Total results count |

---

### 3.2 Use Case: UC-02 — Ingest Content

**Actor:** AI Agent (via orchestrator)
**Priority:** MUST HAVE
**Trigger:** Agent calls kb_ingest tool

#### Main Flow

| Step | Actor | Action | System Response |
|------|-------|--------|-----------------|
| 1 | Agent | Calls kb_ingest with title + content | Validates inputs |
| 2 | System | Extracts issue_key from title (or uses provided) | Regex pattern [A-Z]+-digits |
| 3 | System | Computes SHA-256 content hash | For deduplication |
| 4 | System | Stores entry in PostgreSQL | Upsert by issue_key |
| 5 | System | Generates embedding | Calls Ollama API |
| 6 | System | Indexes in vector DB | Upserts in pgvector |
| 7 | System | Logs audit event | Records INGEST event |
| 8 | System | Returns success response | issue_key + content_hash |

#### Alternative Flows

| ID | Condition | Action |
|----|-----------|--------|
| AF-01 | Entry with same issue_key exists | Upsert (update existing) |
| AF-02 | Vector indexing fails | Store in DB anyway (non-fatal), log warning |
| AF-03 | No issue_key in title | Generate UUID-based key (KB-xxxxxxxx) |

#### Exception Flows

| ID | Condition | Action |
|----|-----------|--------|
| EF-01 | Title is empty | Return KB_VALIDATION_ERROR |
| EF-02 | Content is empty/blank | Return KB_VALIDATION_ERROR |
| EF-03 | Database write fails | Return KB_INTERNAL_ERROR |

#### API Contract — kb_ingest

**Input Schema:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| title | string | Yes | - | Entry title |
| content | string | Yes | - | Full content to ingest |
| issue_key | string | No | - | Jira issue key (auto-extracted from title if missing) |
| tags | string | No | - | Comma-separated tags |
| priority | string | No | normal | Queue priority (high/normal) |

**Response Schema:**

| Field | Type | Description |
|-------|------|-------------|
| status | string | "ingested" |
| issue_key | string | Assigned issue key |
| content_hash | string | SHA-256 hash |
| message | string | Success message |

---

### 3.3 Use Case: UC-03 — Read KB Entry

**Actor:** AI Agent
**Priority:** MUST HAVE
**Trigger:** Agent calls kb_read tool

#### Main Flow

| Step | Actor | Action | System Response |
|------|-------|--------|-----------------|
| 1 | Agent | Calls kb_read with issue_key | Validates issue_key format |
| 2 | System | Queries PostgreSQL by issue_key | SELECT from kb_entries |
| 3 | System | Applies RLS filtering | Returns public_content based on role |
| 4 | System | Returns entry content | Full entry with metadata |

#### Exception Flows

| ID | Condition | Action |
|----|-----------|--------|
| EF-01 | issue_key not found | Return KB_NOT_FOUND error |
| EF-02 | Invalid issue_key format | Return KB_VALIDATION_ERROR |

---

### 3.4 Use Case: UC-04 — Delete KB Entry

**Actor:** AI Agent
**Priority:** MUST HAVE
**Trigger:** Agent calls kb_delete tool

#### Main Flow

| Step | Actor | Action | System Response |
|------|-------|--------|-----------------|
| 1 | Agent | Calls kb_delete with issue_key | Validates issue_key |
| 2 | System | Deletes from PostgreSQL | DELETE from kb_entries |
| 3 | System | Removes from vector index | Delete vector by issue_key |
| 4 | System | Logs audit event | Records DELETE event |
| 5 | System | Returns confirmation | deleted: true |

---

### 3.5 Use Case: UC-05 — Find Similar Entries (Link)

**Actor:** AI Agent
**Priority:** SHOULD HAVE
**Trigger:** Agent calls kb_link tool

#### Main Flow

| Step | Actor | Action | System Response |
|------|-------|--------|-----------------|
| 1 | Agent | Calls kb_link with issue_key | Validates issue_key exists |
| 2 | System | Retrieves entry embedding | From vector DB |
| 3 | System | Performs similarity search | Excludes self from results |
| 4 | System | Returns similar entries | Ranked by similarity score |

---

### 3.6 Use Case: UC-06 — Submit Feedback

**Actor:** AI Agent
**Priority:** SHOULD HAVE
**Trigger:** Agent calls kb_feedback tool

#### Main Flow

| Step | Actor | Action | System Response |
|------|-------|--------|-----------------|
| 1 | Agent | Calls kb_feedback with issue_key + rating + comment | Validates inputs |
| 2 | System | Stores feedback record | INSERT into kb_feedback table |
| 3 | System | Logs audit event | Records FEEDBACK event |
| 4 | System | Returns confirmation | feedback_id |

---

### 3.7 Use Case: UC-07 — Query Audit Logs

**Actor:** AI Agent
**Priority:** MUST HAVE
**Trigger:** Agent calls kb_audit_query tool

#### Main Flow

| Step | Actor | Action | System Response |
|------|-------|--------|-----------------|
| 1 | Agent | Calls kb_audit_query with filters | Validates date range |
| 2 | System | Queries audit_events table | Applies filters (type, date, issue_key) |
| 3 | System | Returns paginated results | List of audit events |

#### API Contract — kb_audit_query

**Input Schema:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| event_type | string | No | - | Filter by event type |
| issue_key | string | No | - | Filter by issue key |
| from_date | string | No | - | Start date (ISO format) |
| to_date | string | No | - | End date (ISO format) |
| limit | integer | No | 50 | Max results (1-200) |

---

### 3.8 Use Case: UC-08 — Trigger Jira Sync

**Actor:** AI Agent
**Priority:** SHOULD HAVE
**Trigger:** Agent calls kb_sync_trigger tool

#### Main Flow

| Step | Actor | Action | System Response |
|------|-------|--------|-----------------|
| 1 | Agent | Calls kb_sync_trigger with project_key | Validates project exists |
| 2 | System | Enqueues sync job in HPQ | Returns job_id |
| 3 | System | Worker picks up job | Processes in background |
| 4 | System | Syncs Jira issues to KB | Batch ingestion |

---

### 3.9 Use Case: UC-09 — Check Sync Status

**Actor:** AI Agent
**Priority:** SHOULD HAVE
**Trigger:** Agent calls kb_sync_status tool

Returns current sync job status (pending, in_progress, completed, failed) with progress percentage.

---

### 3.10 Use Case: UC-10 — Unmask PII

**Actor:** AI Agent (restricted)
**Priority:** MUST HAVE
**Trigger:** Agent calls kb_unmask_pii tool

#### Main Flow

| Step | Actor | Action | System Response |
|------|-------|--------|-----------------|
| 1 | Agent | Calls kb_unmask_pii with issue_key | Validates authorization |
| 2 | System | Checks rate limit | Max 10/hour per session |
| 3 | System | Decrypts PII data | AES-256-GCM decryption |
| 4 | System | Returns unmasked content | Original PII values |
| 5 | System | Logs audit event (CRITICAL) | Records PII_UNMASK event |

#### Exception Flows

| ID | Condition | Action |
|----|-----------|--------|
| EF-01 | Rate limit exceeded | Return KB_RATE_LIMITED |
| EF-02 | Unauthorized role | Return KB_UNAUTHORIZED |
| EF-03 | Decryption key missing | Return KB_CONFIG_ERROR |

---

### 3.11 Use Case: UC-11 — Unmask Business Rules

**Actor:** AI Agent (restricted)
**Priority:** MUST HAVE
**Trigger:** Agent calls kb_unmask_br tool

Similar to UC-10 but for business rules. Rate limits vary by sensitivity level (L1: 5/hr, L2: 15/hr, L3: 30/hr).

---

### 3.12 Use Case: UC-12 — Get Knowledge Graph

**Actor:** AI Agent or Browser User
**Priority:** SHOULD HAVE
**Trigger:** Agent calls kb_graph or user accesses /api/graph

#### Main Flow

| Step | Actor | Action | System Response |
|------|-------|--------|-----------------|
| 1 | Actor | Requests graph data | Validates parameters |
| 2 | System | Queries entries + relationships | Builds node/edge graph |
| 3 | System | Returns graph JSON | Nodes (entries) + edges (links) |

#### REST API (HTTP mode only)

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/graph/data | Full graph data (nodes + edges) |
| GET | /api/graph/node/{id} | Single node with connections |
| GET | /graph | 3D visualization UI (static HTML) |

---

### 3.13 Use Case: UC-13 — Network Traversal

**Actor:** AI Agent
**Priority:** SHOULD HAVE
**Trigger:** Agent calls kb_network tool

#### Main Flow

| Step | Actor | Action | System Response |
|------|-------|--------|-----------------|
| 1 | Agent | Calls kb_network with start_key + hops | Validates parameters |
| 2 | System | Performs BFS N-hop traversal | From start node, N levels deep |
| 3 | System | Returns connected subgraph | Nodes within N hops |

#### API Contract — kb_network

**Input Schema:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| start_key | string | Yes | - | Starting node issue_key |
| hops | integer | No | 2 | Traversal depth (1-5) |
| include_content | boolean | No | false | Include full content in results |

---

## 4. Business Rules

| ID | Rule | Category | Implementation |
|----|------|----------|----------------|
| BR-01 | PII unmask rate limited to 10/hour per session | Security | RateLimiter in KbUnmaskPiiHandler |
| BR-02 | BR unmask rate varies by level (L1:5, L2:15, L3:30 per hour) | Security | RateLimiter in KbUnmaskBrHandler |
| BR-03 | All sensitive operations must be audited | Compliance | AuditService.log() in every handler |
| BR-04 | Vector indexing failure is non-fatal | Resilience | Try-catch in indexInVectorDb() |
| BR-05 | HPQ tasks processed before NPQ | Priority | DualPriorityQueue select() logic |
| BR-06 | Stuck tasks (>5min) auto-retried up to 3 times | Reliability | QueueWatchdog monitoring |
| BR-07 | Crash recovery restores in-progress tasks on startup | Reliability | CrashRecoveryService.recover() |
| BR-08 | Content hash used for deduplication | Efficiency | SHA-256 hash comparison on upsert |
| BR-09 | Workspace root resolved via MCP roots/list | Protocol | WorkspaceContext singleton |
| BR-10 | Fallback to CWD if roots/list unavailable | Resilience | WorkspaceContext fallback |
| BR-11 | PII encrypted at rest with AES-256-GCM | Security | PiiMaskingEngine encryption |
| BR-12 | Audit logs retained for 90 days | Compliance | Configurable retention_days |
| BR-13 | Queue capacity: HPQ=100, NPQ=1000 | Performance | Configurable in YAML |
| BR-14 | Search results limited to top_k (max 20) | Performance | Enforced in KbSearchHandler |
| BR-15 | Embedding cache TTL = 10 minutes, max 200 entries | Performance | LRU cache in embedding service |

---

## 5. Data Specifications

### 5.1 KB Entry Model

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| id | UUID | No | Primary key |
| issue_key | String | No | Jira issue key (unique) |
| project_key | String | No | Extracted from issue_key |
| public_content | Text | Yes | Publicly visible content |
| technical_content | Text | Yes | Technical details (role-filtered) |
| business_rules | Text | Yes | Encrypted BR content |
| masked_full | Text | Yes | Full content with PII masked |
| br_sensitivity_level | Enum | No | INTERNAL, CONFIDENTIAL, RESTRICTED |
| content_hash | String | No | SHA-256 hash for dedup |
| created_at | Timestamp | No | Creation time |
| updated_at | Timestamp | No | Last update time |

### 5.2 Audit Event Model

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| event_type | Enum | SEARCH, INGEST, DELETE, PII_UNMASK, BR_UNMASK, FEEDBACK, SYNC |
| issue_key | String (nullable) | Related KB entry |
| action | String | Tool name that triggered event |
| actor | String | Session/user identifier |
| success | Boolean | Operation outcome |
| metadata | JSONB | Additional context |
| created_at | Timestamp | Event time |

### 5.3 Queue Task Model

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Primary key |
| task_type | String | Task category |
| payload | JSONB | Task data |
| priority | Enum | HIGH, NORMAL |
| status | Enum | PENDING, IN_PROGRESS, COMPLETED, FAILED, STUCK |
| retry_count | Int | Current retry attempt |
| max_retries | Int | Maximum retries (default 3) |
| created_at | Timestamp | Enqueue time |
| started_at | Timestamp (nullable) | Processing start |
| completed_at | Timestamp (nullable) | Completion time |

---

## 6. Non-Functional Requirements

| Category | Requirement | Target | Measurement |
|----------|-------------|--------|-------------|
| Performance | kb_search latency | < 500ms (p95) | Excluding embedding generation |
| Performance | kb_ingest throughput | >= 10 entries/min | With PII masking enabled |
| Performance | Embedding cache hit rate | > 60% | For repeated queries |
| Reliability | Zero data loss on crash | 100% | DB-first persistence |
| Reliability | Graceful shutdown | < 5s | Drain queue + close connections |
| Scalability | KB entries supported | 1000+ | With HNSW index |
| Security | PII encryption | AES-256-GCM | At-rest encryption |
| Security | Rate limiting | Configurable | Per-hour per-session |
| Observability | Structured logging | Logback JSON | Correlation ID per request |
| Observability | Audit coverage | 100% sensitive ops | All unmask + delete operations |
| Maintainability | File size limit | <= 200 LOC | Kotlin code standards |
| Testability | Unit test coverage | 37+ tests | MockK + Kotest |

---

## 7. Integration Requirements

### 7.1 Orchestrator to KB Server (MCP Protocol)

| Aspect | Specification |
|--------|---------------|
| Protocol | MCP (Model Context Protocol) JSON-RPC 2.0 |
| Transport (dev) | STDIO — orchestrator spawns kb-server as subprocess |
| Transport (prod) | HTTP — kb-server runs on port 9181 |
| Discovery | orchestrator calls tools/list, kb-server returns 13 tools |
| Execution | orchestrator calls tools/call, kb-server executes handler |
| Workspace | kb-server calls roots/list on client, gets workspace root |

### 7.2 KB Server to PostgreSQL

| Aspect | Specification |
|--------|---------------|
| Driver | PostgreSQL JDBC 42.7.5 |
| Pool | HikariCP (max 10, min idle 2) |
| Schema | kb (separate from orchestrator) |
| Extensions | pgvector (HNSW index) |
| Migrations | KbDatabaseInitializer (idempotent DDL) |

### 7.3 KB Server to Ollama

| Aspect | Specification |
|--------|---------------|
| Protocol | HTTP REST API |
| Endpoint | http://localhost:11434/api/embeddings |
| Model | nomic-embed-text |
| Dimensions | 768 |
| Timeout | 30 seconds |

### 7.4 Graph REST API (HTTP mode)

| Method | Path | Request | Response |
|--------|------|---------|----------|
| GET | /api/graph/data | query params: project_key | nodes + edges JSON |
| GET | /api/graph/node/{id} | path param: node ID | node + connections JSON |
| GET | /graph | - | Static HTML (3D visualization) |

---

## 8. Configuration Specification

### 8.1 Configuration File Structure

File: kb-server.yml (external, passed via --config=)

See Section 8 in BRD for full YAML structure. Key sections:
- kb.server: port, transport
- kb.database: url, schema, pool settings
- kb.embedding: provider, model, cache
- kb.masking: strategies, placeholder format
- kb.security: encryption keys, rate limits
- kb.queue: capacities, worker count, watchdog
- kb.audit: enabled, retention

---

## 9. Error Handling

| Error Code | Description | Recovery |
|------------|-------------|----------|
| KB_VALIDATION_ERROR | Invalid input parameters | Fix input and retry |
| KB_NOT_FOUND | Entry not found by issue_key | Check issue_key exists |
| KB_UNAUTHORIZED | Insufficient role for operation | Escalate to admin |
| KB_RATE_LIMITED | Rate limit exceeded | Wait and retry |
| KB_CONFIG_ERROR | Missing configuration | Fix config file |
| KB_INTERNAL_ERROR | Unexpected internal error | Check logs, retry |
| KB_DB_ERROR | Database connection failed | Check PostgreSQL |
| KB_EMBEDDING_ERROR | Embedding service unavailable | Check Ollama |

---

## 10. Open Issues

| # | Issue | Status | Owner | Impact |
|---|-------|--------|-------|--------|
| 1 | LangChain4j segmentation integration deferred | Deferred | SA | Content segmentation uses simple splitting |
| 2 | OCR integration not included | Deferred | PO | Cannot ingest image-based documents |
| 3 | Multi-node deployment not supported | Deferred | SA | Single instance only for v1 |
| 4 | Jira crawler/scanner deferred | Deferred | PO | Manual sync trigger only |

---

## 11. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | System Context | [system-context.png](diagrams/system-context.png) | [system-context.drawio](diagrams/system-context.drawio) |
| 2 | Sequence — Search Flow | [sequence-search.png](diagrams/sequence-search.png) | [sequence-search.drawio](diagrams/sequence-search.drawio) |
| 3 | Sequence — Ingest Flow | [sequence-ingest.png](diagrams/sequence-ingest.png) | [sequence-ingest.drawio](diagrams/sequence-ingest.drawio) |
| 4 | State — Queue Task | [state-queue-task.png](diagrams/state-queue-task.png) | [state-queue-task.drawio](diagrams/state-queue-task.drawio) |
