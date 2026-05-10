# Technical Design Document (TDD)

## MCPOrchestration — MTO-38: KB Server — Tách Knowledge Base thành MCP Server Riêng Biệt

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-38 |
| Title | KB Server — Standalone MCP Server for Knowledge Base |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related FSD | FSD-v1-MTO-38.docx |
| Related BRD | BRD-v1-MTO-38.docx |
| Reference TDD | documents/MTO-25/TDD.md (94KB — full architecture) |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | SA Agent | Initial TDD — based on implemented kb-server module |

---

## 1. Introduction

### 1.1 Purpose

This TDD documents the technical design of the kb-server module — a standalone MCP server extracted from orchestrator-server that provides all Knowledge Base functionality as an independent deployable unit.

### 1.2 Scope

- Gradle subproject configuration (build.gradle.kts)
- Package structure and class design
- MCP protocol integration (tools/list, tools/call, roots/list)
- Database schema (PostgreSQL + pgvector)
- Queue system (DualPriorityQueue, Worker, Watchdog, CrashRecovery)
- PII masking pipeline
- Vector search implementation
- Transport layer (STDIO + HTTP)
- Graph visualization REST API
- DI configuration (Koin)

### 1.3 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.3.20 |
| Platform | JVM | 21 |
| MCP SDK | io.modelcontextprotocol:kotlin-sdk-server | 0.12.0 |
| Coroutines | kotlinx.coroutines | 1.10.2 |
| Serialization | kotlinx.serialization-json | 1.8.1 |
| DI | Koin | 4.1.1 |
| HTTP Server | Ktor (Netty) | 3.4.0 |
| Database | PostgreSQL + HikariCP | 42.7.5 / 5.1.0 |
| Vector DB | pgvector (HNSW) | via JDBC |
| Embedding | Ollama (nomic-embed-text) | local |
| YAML | kaml | 0.77.0 |
| Logging | Logback Classic | 1.5.18 |
| Testing | Kotest + MockK | 5.9.1 / 1.14.2 |
| Build | Gradle (Kotlin DSL) + Shadow | 9.0.0-beta12 |

### 1.4 Design Principles

| Principle | Application |
|-----------|-------------|
| Separation of Concerns | KB logic fully isolated from orchestrator |
| Interface/Impl Pattern | All services use interface + implementation |
| Koin DI | Single module with all bindings |
| MCP Protocol | Standard tool registration via SDK |
| Crash Safety | DB-first persistence before channel ops |
| Cooperative Cancellation | Kotlin coroutines with SupervisorJob |
| Non-Fatal Fallbacks | Vector failures do not block DB operations |

---

## 2. Architecture Overview

### 2.1 Module Structure

```
kb-server/
  build.gradle.kts          # Application + Shadow plugin
  src/main/kotlin/com/orchestrator/mcp/kb/
    KbMain.kt               # Entry point (CLI args parsing)
    KbApplication.kt        # Lifecycle management
    KbExceptions.kt         # Sealed exception hierarchy
    WorkspaceContext.kt     # Singleton for workspace root
    config/                 # Configuration loading
    di/                     # Koin DI module
    protocol/               # MCP server factory + tool handlers
      handlers/             # 13 tool handler implementations
    queue/                  # DualPriorityQueue system
      handler/              # Task-specific handlers
      model/                # Queue data models
      repository/           # Queue persistence
    store/                  # Data storage layer
      database/             # DB initializer
      encryption/           # AES-256-GCM encryption
      model/                # Domain models
      repository/           # KB entry CRUD
      vector/               # pgvector client
    masking/                # PII detection + masking
    audit/                  # Audit event logging
    graph/                  # Graph visualization
    network/                # BFS N-hop traversal
    transport/              # HTTP transport (Ktor)
  src/main/resources/
    application.yml         # Default config
  src/test/kotlin/          # Unit tests (37+)
```

### 2.2 Dependency Graph

```
kb-server
  depends on: orchestrator-core (shared models, config utilities)
  depends on: orchestrator-client (EmbeddingService, MCP SDK types)
  
orchestrator-server
  depends on: orchestrator-core
  depends on: orchestrator-client
  connects to: kb-server (as upstream MCP server)
```

### 2.3 Component Architecture

| Component | Responsibility | Key Classes |
|-----------|---------------|-------------|
| Entry Point | CLI parsing, bootstrap | KbMain, KbApplication |
| Protocol | MCP server, tool registration | KbMcpServerFactory, KbToolRegistrar |
| Handlers | Tool call processing | 13 KbToolHandler implementations |
| Store | Data persistence | KbEntryRepository, KbVectorClient |
| Queue | Async task processing | DualPriorityQueue, QueueWorker, QueueWatchdog |
| Masking | PII detection/masking | PiiDetector, PiiMaskingEngine |
| Audit | Event logging | AuditService, AuditEventRepository |
| Graph | Visualization API | GraphService, GraphRoutes |
| Network | Relationship traversal | NetworkService |
| Transport | HTTP server | KbHttpTransport |
| Config | YAML loading | KbConfigLoader, KbConfig |

---

## 3. Detailed Design

### 3.1 Entry Point and Lifecycle

**KbMain.kt** — Parses CLI arguments (--config, --transport), loads config, creates KbApplication.

**KbApplication.kt** — Orchestrates startup:
1. Start Koin DI with kbAppModule(config)
2. Initialize database schema (idempotent)
3. Run crash recovery (restore stuck tasks)
4. Start queue worker + watchdog
5. Create MCP server via KbMcpServerFactory
6. Start transport (STDIO or HTTP)

**Shutdown:** stopKoin() cleans up all singletons.

### 3.2 MCP Protocol Layer

**KbMcpServerFactory** — Creates MCP SDK Server instance, registers all tools via KbToolRegistrar.

**KbToolRegistrar** — Iterates List<KbToolHandler>, calls server.addTool() for each.

**KbToolHandler** (interface):
- toolName: String
- description: String
- inputSchema: ToolSchema
- handle(arguments: JsonObject?): CallToolResult

**13 Handler Implementations:**

| Handler | Tool Name | Category |
|---------|-----------|----------|
| KbSearchHandler | kb_search | Read |
| KbReadHandler | kb_read | Read |
| KbIngestHandler | kb_ingest | Write |
| KbDeleteHandler | kb_delete | Write |
| KbLinkHandler | kb_link | Read |
| KbFeedbackHandler | kb_feedback | Write |
| KbAuditHandler | kb_audit_query | Read |
| KbSyncTriggerHandler | kb_sync_trigger | Write |
| KbSyncStatusHandler | kb_sync_status | Read |
| KbUnmaskPiiHandler | kb_unmask_pii | Read (restricted) |
| KbUnmaskBrHandler | kb_unmask_br | Read (restricted) |
| KbGraphHandler | kb_graph | Read |
| KbNetworkHandler | kb_network | Read |

### 3.3 Queue System

**DualPriorityQueue** — Two Kotlin Channels (HPQ capacity=100, NPQ capacity=1000). Select prioritizes HPQ.

**QueueWorker** — Coroutine loop: select from DualPriorityQueue, dispatch to TaskHandler, update status.

**QueueWatchdog** — Periodic check (60s interval): find tasks stuck > 5min, retry up to 3 times.

**CrashRecoveryService** — On startup: query DB for IN_PROGRESS tasks, re-enqueue them.

**TaskHandler** (interface) — Process specific task types (sync, ingestion batch).

### 3.4 Data Storage

**KbEntryRepository** — CRUD operations on kb_entries table:
- upsert(entry): INSERT ON CONFLICT UPDATE
- findByIssueKey(key): SELECT by issue_key
- searchByKeyword(query, limit): PostgreSQL full-text search
- delete(issueKey): DELETE + cascade

**KbVectorClient** (pgvector) — Vector operations:
- upsert(vectorEntry): INSERT embedding + metadata
- search(embedding, topK, threshold, projectKey): cosine similarity search
- delete(issueKey): Remove vector

**KbDatabaseInitializer** — Idempotent DDL:
- CREATE SCHEMA IF NOT EXISTS kb
- CREATE TABLE kb_entries (with pgvector column)
- CREATE TABLE audit_events
- CREATE TABLE queue_tasks
- CREATE INDEX (HNSW on embedding column)

### 3.5 PII Masking

**PiiDetector** — Regex-based detection for: email, phone, bank_account, id_card, name.

**PiiMaskingEngine** — Pipeline:
1. Detect PII spans in content
2. Replace with placeholders: [EMAIL_1], [PHONE_2], etc.
3. Encrypt original values with AES-256-GCM
4. Store mapping (placeholder to encrypted value)

### 3.6 Security

**Encryption** — AES-256-GCM for PII and BR data at rest.

**Rate Limiting** — In-memory counters per session, configurable limits per hour.

**Role-Based Access** — RLS filtering on query results based on caller role (developer, admin, etc.).

### 3.7 Transport

**STDIO** — Uses MCP SDK StdioServerTransport (stdin/stdout). Default for development.

**HTTP** — Ktor/Netty server on port 9181:
- POST /mcp — MCP JSON-RPC endpoint
- GET /api/graph/* — Graph REST API
- GET /graph — Static 3D visualization UI

### 3.8 Configuration

**KbConfigLoader** — Loads YAML config:
1. Check --config= CLI argument
2. Load external file (or classpath application.yml)
3. Parse with kaml into KbConfig data class
4. Resolve environment variables (${VAR} syntax)

**KbConfig** — @Serializable data class hierarchy matching YAML structure.

---

## 4. Database Schema

### 4.1 Tables

**kb.kb_entries:**
```sql
CREATE TABLE kb.kb_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_key VARCHAR(50) UNIQUE NOT NULL,
    project_key VARCHAR(20) NOT NULL,
    public_content TEXT,
    technical_content TEXT,
    business_rules TEXT,
    masked_full TEXT,
    br_sensitivity_level VARCHAR(20) DEFAULT 'INTERNAL',
    content_hash VARCHAR(64) NOT NULL,
    embedding vector(768),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_kb_entries_embedding ON kb.kb_entries
    USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_kb_entries_project ON kb.kb_entries(project_key);
```

**kb.audit_events:**
```sql
CREATE TABLE kb.audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(30) NOT NULL,
    issue_key VARCHAR(50),
    action VARCHAR(50) NOT NULL,
    actor VARCHAR(100),
    success BOOLEAN NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_audit_events_type ON kb.audit_events(event_type);
CREATE INDEX idx_audit_events_date ON kb.audit_events(created_at);
```

**kb.queue_tasks:**
```sql
CREATE TABLE kb.queue_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    priority VARCHAR(10) DEFAULT 'NORMAL',
    status VARCHAR(20) DEFAULT 'PENDING',
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);
CREATE INDEX idx_queue_tasks_status ON kb.queue_tasks(status);
```

---

## 5. Build Configuration

### 5.1 build.gradle.kts (kb-server)

Key configuration:
- plugins: application, kotlin-jvm, serialization, shadow
- mainClass: com.orchestrator.mcp.kb.KbMainKt
- shadowJar: archiveBaseName = "kb-server", classifier = "all"
- dependencies: orchestrator-core, orchestrator-client, MCP SDK, Ktor, HikariCP, PostgreSQL JDBC, kaml, Koin, Logback

### 5.2 settings.gradle.kts

```kotlin
include("orchestrator-core")
include("orchestrator-client")
include("orchestrator-server")
include("kb-server")
```

---

## 6. Error Handling

### 6.1 Exception Hierarchy

```
KbException (sealed)
  KbValidationException     — Invalid input parameters
  KbNotFoundException       — Entry not found
  KbUnauthorizedException   — Insufficient permissions
  KbRateLimitedException    — Rate limit exceeded
  KbConfigException         — Configuration error
  KbDatabaseException       — Database operation failed
  KbEmbeddingException      — Embedding service error
  KbInternalException       — Unexpected error
```

### 6.2 Error Response Format

All handlers catch exceptions and return CallToolResult with isError=true:
```json
{
  "error_code": "KB_VALIDATION_ERROR",
  "message": "Query must not be empty"
}
```

---

## 7. Testing Strategy

### 7.1 Unit Tests (37+)

| Package | Test Class | Coverage |
|---------|-----------|----------|
| queue | DualPriorityQueueTest | HPQ priority, capacity limits |
| queue | QueueWorkerTest | Task dispatch, error handling |
| queue | QueueWatchdogTest | Stuck detection, retry logic |
| queue | CrashRecoveryServiceTest | Recovery on startup |
| masking | PiiDetectorTest | All PII patterns |
| masking | PiiMaskingEngineTest | Mask + encrypt pipeline |
| protocol | KbSearchHandlerTest | Search flow, fallback |
| protocol | KbIngestHandlerTest | Ingest flow, dedup |
| store | KbEntryRepositoryTest | CRUD operations |
| store | KbVectorClientTest | Vector operations |

### 7.2 Test Framework

- Kotest 5.9.1 (FunSpec style)
- MockK 1.14.2 (mocking)
- No Testcontainers for unit tests (mocked DB)

---

## 8. Deployment

### 8.1 Build

```bash
./gradlew :kb-server:shadowJar
# Output: kb-server/build/libs/kb-server-all.jar
```

### 8.2 Run

```bash
# STDIO mode (development)
java -jar kb-server-all.jar --transport=stdio

# HTTP mode (production)
java -jar kb-server-all.jar --transport=http --config=/etc/kb-server.yml
```

### 8.3 Orchestrator Configuration

In orchestrator's application.yml:
```yaml
orchestrator:
  upstream_servers:
    - name: "kb-server"
      transport: "stdio"
      command: "java"
      args: ["-jar", "kb-server/build/libs/kb-server-all.jar", "--transport=stdio"]
```

---

## 9. Security Design

### 9.1 Data Protection

| Data Type | Protection | Key Management |
|-----------|-----------|----------------|
| PII (email, phone, etc.) | AES-256-GCM encryption | Config: security.encryption_key |
| Business Rules | AES-256-GCM encryption | Config: security.br_encryption_key |
| Audit Logs | Plain text (no PII) | Retained 90 days |
| Embeddings | Not encrypted | Derived from masked content |

### 9.2 Access Control

| Operation | Required Role | Rate Limit |
|-----------|--------------|------------|
| kb_search, kb_read | Any | None |
| kb_ingest, kb_delete | developer+ | None |
| kb_unmask_pii | admin | 10/hour |
| kb_unmask_br (L1) | admin | 5/hour |
| kb_unmask_br (L2) | developer+ | 15/hour |
| kb_unmask_br (L3) | Any | 30/hour |

---

## 10. Implementation Checklist

| # | Task | Status | Files |
|---|------|--------|-------|
| 1 | Gradle module setup | DONE | kb-server/build.gradle.kts |
| 2 | Entry point (KbMain + KbApplication) | DONE | KbMain.kt, KbApplication.kt |
| 3 | Config loading | DONE | config/KbConfigLoader.kt, KbConfig.kt |
| 4 | Koin DI module | DONE | di/KbAppModule.kt |
| 5 | MCP Server Factory | DONE | protocol/KbMcpServerFactory.kt |
| 6 | Tool Registrar | DONE | protocol/KbToolRegistrar.kt |
| 7 | 13 Tool Handlers | DONE | protocol/handlers/*.kt |
| 8 | Queue System | DONE | queue/*.kt |
| 9 | PII Masking | DONE | masking/*.kt |
| 10 | Vector Client (pgvector) | DONE | store/vector/KbVectorClient.kt |
| 11 | DB Initializer | DONE | store/database/KbDatabaseInitializer.kt |
| 12 | Audit Service | DONE | audit/*.kt |
| 13 | Graph Routes | DONE | graph/GraphRoutes.kt |
| 14 | Network Service | DONE | network/NetworkService.kt |
| 15 | HTTP Transport | DONE | transport/KbHttpTransport.kt |
| 16 | WorkspaceContext | DONE | WorkspaceContext.kt |
| 17 | Unit Tests (37+) | DONE | src/test/kotlin/**/*Test.kt |
| 18 | ShadowJar build | DONE | build/libs/kb-server-all.jar |

---

## 11. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Architecture Overview | [architecture.png](diagrams/architecture.png) | [architecture.drawio](diagrams/architecture.drawio) |
| 2 | Component Diagram | [component.png](diagrams/component.png) | [component.drawio](diagrams/component.drawio) |
| 3 | Class Diagram — Protocol | [class-protocol.png](diagrams/class-protocol.png) | [class-protocol.drawio](diagrams/class-protocol.drawio) |
