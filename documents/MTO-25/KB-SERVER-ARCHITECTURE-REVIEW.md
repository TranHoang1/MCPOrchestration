# KB Server Architecture Review

## Tách Knowledge Base thành Module/Server Riêng Biệt

| Field | Value |
|-------|-------|
| **Document Type** | Architecture Review & Design |
| **Scope** | MTO-25 → MTO-37 (KB Refinery Epic) |
| **Author** | TA Agent — Senior Technical Architect |
| **Date** | 2026-05-10 |
| **Status** | Draft |
| **Project** | MCPOrchestration |

---

## 1. Executive Summary

Tài liệu này phân tích kiến trúc hiện tại của Knowledge Base (KB) components trong `orchestrator-server` và đề xuất thiết kế tách KB thành một MCP server riêng biệt (`kb-server`). Mục tiêu:

1. **Separation of Concerns** — KB logic (storage, search, security, ingestion) tách khỏi orchestrator logic (tool discovery, routing, upstream management)
2. **Independent Scalability** — KB server có thể scale riêng (CPU-intensive cho embedding/segmentation, I/O-intensive cho DB)
3. **Independent Deployment** — Deploy KB server mà không ảnh hưởng orchestrator
4. **Cleaner Codebase** — Giảm complexity của orchestrator-server (hiện ~74 files, sẽ tăng đáng kể với MTO-25→37)

---

## 2. Current Architecture Analysis

### 2.1 KB-Related Packages trong orchestrator-server

Hiện tại, tất cả KB components nằm trong `orchestrator-server` module:

```
orchestrator-server/src/main/kotlin/com/orchestrator/mcp/
├── kbstore/           # MTO-26: KB entries schema, encryption, repository
│   ├── config/        # KbStoreConfig
│   ├── di/            # KbStoreModule (Koin)
│   ├── encryption/    # EncryptionService (AES-256-GCM)
│   ├── model/         # KbEntry, PiiMapping, BrSensitivityLevel, MappingType
│   └── repository/    # KbEntryRepository, PiiMappingRepository (JDBC)
├── masking/           # MTO-27: PII Masking Engine
│   ├── config/        # MaskingConfig
│   ├── di/            # maskingModule
│   ├── model/         # MaskingResult
│   └── strategy/      # PiiDetectionStrategy implementations
├── segmentation/      # MTO-28: LangChain4j Content Segmentation
│   ├── config/        # SegmentationConfig
│   ├── di/            # segmentationModule
│   ├── model/         # SegmentationResult
│   ├── prompt/        # SegmentationAiService, PromptBuilder
│   └── provider/      # ChatModelFactory (OpenAI/Ollama/Azure)
├── ocr/               # MTO-29: MarkItDown OCR Integration
│   ├── di/            # ocrModule
│   ├── extractor/     # ImageTextExtractor
│   └── model/         # OcrResult
├── brmasking/         # MTO-30: Business Rules Masking (AI-based)
│   ├── crypto/        # BrEncryptionService
│   ├── di/            # brMaskingModule
│   ├── model/         # BrMaskingConfig
│   └── prompt/        # BrIdentificationAiService
├── security/          # MTO-31, MTO-33: RLS + BR Access Control
│   ├── br/            # BrAccessService, BrDlpService, BrKeyManagement, BrRateLimit, BrSession
│   ├── config/        # RlsConfig
│   ├── di/            # securityModule, brAccessModule
│   ├── model/         # KbRole, BrAccessResult, BrSession
│   ├── pii/           # PII-specific security
│   └── repository/    # BrAccessAuditRepository
├── audit/             # MTO-34: Audit Log & Response Shaping
│   ├── di/            # auditModule
│   ├── model/         # AuditEvent
│   └── repository/    # AuditRepository
├── linking/           # MTO-35: Semantic Entity Linking
│   ├── di/            # linkingModule
│   ├── model/         # EntityLink, LinkingConfig, LinkingResult
│   └── repository/    # EntityLinkRepository
├── network/           # MTO-36: Feature Network Mapping
│   ├── di/            # networkModule
│   └── model/         # NetworkGraph
├── feedback/          # MTO-37: Feedback & Correction
│   ├── di/            # feedbackModule
│   ├── model/         # Feedback, FeedbackStats
│   └── repository/    # FeedbackRepository
├── queue/             # MTO-25: Dual-Priority Queue
│   ├── config/        # QueueConfig
│   ├── di/            # queueModule
│   ├── model/         # QueueTask, TaskStatus
│   └── repository/    # QueueTaskRepository
├── crawler/           # KB Ingestion Pipeline
│   ├── config/        # CrawlerConfig
│   ├── di/            # crawlerModule
│   ├── model/         # TicketContent, CrawlResult
│   └── ...            # AdfParser, ContentFetcher, GraphBuilder, KBIngestor
└── scanner/           # Jira Project Scanner
    ├── config/        # ScannerConfig
    ├── di/            # scannerModule
    └── ...            # PageFetcher, BatchUpserter, JqlBuilder
```

### 2.2 Shared Dependencies (Cross-Cutting)

| Component | Used By KB | Used By Orchestrator |
|-----------|-----------|---------------------|
| `orchestrator-client/embedding/EmbeddingService` | ✅ (KB ingestion, linking) | ✅ (tool indexing) |
| `orchestrator-client/vectordb/VectorDbClient` | ✅ (KB search, linking) | ✅ (tool discovery) |
| `orchestrator-core/config/OrchestratorConfig` | ✅ (KB config sections) | ✅ (server config) |
| `HikariDataSource` | ✅ (KB repositories) | ✅ (sync state, config DB) |
| `HttpClient (Ktor)` | ✅ (LLM calls, Jira API) | ✅ (upstream connections) |
| `Koin DI` | ✅ (KB modules) | ✅ (orchestrator modules) |

### 2.3 Current Data Flow

```
Jira API → Scanner → Crawler → PII Masking → Segmentation → BR Masking
                                     ↓              ↓            ↓
                              PiiMappingRepo   KbEntryRepo   BrEncryption
                                     ↓              ↓            ↓
                              pii_mapping      kb_entries    (encrypted)
                                                    ↓
                              Entity Linking ← Embedding → Vector DB
                                     ↓
                              entity_links table
```

### 2.4 Problems with Current Architecture

| # | Problem | Impact |
|---|---------|--------|
| 1 | **Monolithic growth** — orchestrator-server đã có 30+ packages, sẽ thêm 13 packages nữa | Maintainability ↓ |
| 2 | **Mixed concerns** — Tool routing logic cùng module với AI/LLM processing | Cognitive load ↑ |
| 3 | **Scaling mismatch** — Orchestrator cần low-latency (tool routing), KB cần high-throughput (batch processing) | Resource waste |
| 4 | **Deployment coupling** — Thay đổi LLM prompt (segmentation) requires redeploy orchestrator | Deployment risk ↑ |
| 5 | **Testing complexity** — Integration tests cần mock cả orchestrator + KB dependencies | Test time ↑ |
| 6 | **Single point of failure** — KB LLM timeout (10s) blocks orchestrator thread pool | Availability ↓ |

</content>
<parameter name="mode">write

---

## 3. Proposed Architecture: KB Server as Separate MCP Server

### 3.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        IDE / AI Agent                             │
│                    (Claude, Kiro, etc.)                           │
└──────────────────────────┬──────────────────────────────────────┘
                           │ MCP Protocol (stdio/HTTP)
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    orchestrator-server                            │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────────────┐ │
│  │ find_    │  │ execute_     │  │ Tool Registry + Indexer    │ │
│  │ tools    │  │ dynamic_tool │  │ (Qdrant/PgVector)         │ │
│  └──────────┘  └──────┬───────┘  └───────────────────────────┘ │
│                        │ route to upstream                        │
└────────────────────────┼────────────────────────────────────────┘
                         │
          ┌──────────────┼──────────────────┐
          │              │                  │
          ▼              ▼                  ▼
┌──────────────┐ ┌──────────────┐  ┌──────────────────┐
│  kb-server   │ │ other MCP    │  │ other MCP        │
│  (MCP)       │ │ servers      │  │ servers          │
│              │ │ (filesystem, │  │ (fetch, etc.)    │
│  - kb_search │ │  git, etc.)  │  │                  │
│  - kb_read   │ └──────────────┘  └──────────────────┘
│  - kb_ingest │
│  - kb_link   │
│  - kb_audit  │
└──────┬───────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│                    Shared Infrastructure                       │
│  ┌────────────┐  ┌────────────┐  ┌────────────────────────┐ │
│  │ PostgreSQL │  │ Qdrant /   │  │ LLM Provider           │ │
│  │ (kb_entries│  │ PgVector   │  │ (OpenAI/Ollama/Azure)  │ │
│  │  pii_map,  │  │            │  │                        │ │
│  │  audit,    │  │            │  │                        │ │
│  │  queue)    │  │            │  │                        │ │
│  └────────────┘  └────────────┘  └────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 Module Structure (Gradle Multi-Project)

```
MCPOrchestration/
├── orchestrator-core/          # Shared models, config, utilities
├── orchestrator-client/        # Shared clients (embedding, vectordb, upstream)
├── orchestrator-server/        # MCP Orchestrator (tool routing only)
├── orchestrator-bridge/        # Bridge for IDE integration
├── kb-server/                  # ← NEW: Knowledge Base MCP Server
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/orchestrator/mcp/kb/
│       ├── KbApplication.kt           # Entry point
│       ├── KbMain.kt                  # Main function (stdio/HTTP)
│       ├── config/                    # KB-specific config
│       ├── di/                        # KB Koin modules
│       ├── protocol/                  # MCP tool registration
│       │   ├── KbMcpServerFactory.kt  # Creates MCP Server with KB tools
│       │   └── KbToolRegistrar.kt     # Registers kb_search, kb_read, etc.
│       ├── store/                     # KB storage (from kbstore/)
│       ├── masking/                   # PII masking (from masking/)
│       ├── segmentation/              # Content segmentation (from segmentation/)
│       ├── brmasking/                 # BR masking (from brmasking/)
│       ├── security/                  # RLS, BR access, sessions
│       ├── audit/                     # Audit logging
│       ├── linking/                   # Entity linking
│       ├── network/                   # Feature network
│       ├── feedback/                  # Feedback & corrections
│       ├── queue/                     # Dual-priority queue
│       ├── crawler/                   # Ticket crawler + ingestion
│       ├── scanner/                   # Jira project scanner
│       └── ocr/                       # OCR integration
└── settings.gradle.kts         # include("kb-server")
```

### 3.3 KB Server MCP Tools (Public API)

KB Server sẽ expose các MCP tools sau (discoverable via orchestrator's `find_tools`):

| Tool Name | Description | Category |
|-----------|-------------|----------|
| `kb_search` | Semantic search KB entries by query text | Read |
| `kb_read` | Read a specific KB entry by issue_key | Read |
| `kb_ingest` | Ingest content into KB (title, content, tags) | Write |
| `kb_delete` | Delete a KB entry by issue_key | Write |
| `kb_link` | Find semantically similar entries | Read |
| `kb_feedback` | Submit feedback on a KB entry | Write |
| `kb_audit_query` | Query audit logs (admin only) | Read |
| `kb_sync_trigger` | Trigger Jira project sync | Write |
| `kb_sync_status` | Check sync progress | Read |
| `kb_unmask_pii` | Unmask PII for authorized users | Read (restricted) |
| `kb_unmask_br` | Unmask business rules for authorized users | Read (restricted) |

### 3.4 Tool Schema Examples

```json
// kb_search
{
  "name": "kb_search",
  "description": "Search knowledge base entries semantically. Returns matching entries ranked by relevance.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "query": { "type": "string", "description": "Natural language search query" },
      "project_key": { "type": "string", "description": "Filter by project (optional)" },
      "top_k": { "type": "integer", "default": 5, "description": "Max results" },
      "include_technical": { "type": "boolean", "default": true }
    },
    "required": ["query"]
  }
}

// kb_ingest
{
  "name": "kb_ingest",
  "description": "Ingest content into the knowledge base. Handles PII masking, segmentation, and indexing automatically.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "title": { "type": "string", "description": "Entry title (e.g., 'MTO-25 BRD')" },
      "content": { "type": "string", "description": "Full content to ingest" },
      "issue_key": { "type": "string", "description": "Jira issue key" },
      "tags": { "type": "string", "description": "Comma-separated tags" }
    },
    "required": ["title", "content"]
  }
}

// kb_read
{
  "name": "kb_read",
  "description": "Read a specific KB entry by issue key. Returns content filtered by caller's role.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "issue_key": { "type": "string", "description": "Jira issue key (e.g., MTO-25)" },
      "include_links": { "type": "boolean", "default": false }
    },
    "required": ["issue_key"]
  }
}
```



---

## 4. Detailed Component Design

### 4.1 KB Server Entry Point

```kotlin
// kb-server/src/main/kotlin/com/orchestrator/mcp/kb/KbMain.kt
package com.orchestrator.mcp.kb

fun main(args: Array<String>) {
    val configPath = args.firstOrNull { it.startsWith("--config=") }
        ?.removePrefix("--config=")
    val transport = args.firstOrNull { it.startsWith("--transport=") }
        ?.removePrefix("--transport=") ?: "stdio"

    when (transport) {
        "stdio" -> startStdioServer(configPath)
        "http" -> startHttpServer(configPath)
        else -> error("Unknown transport: $transport")
    }
}
```

### 4.2 KB MCP Server Factory

```kotlin
// kb-server/src/main/kotlin/com/orchestrator/mcp/kb/protocol/KbMcpServerFactory.kt
package com.orchestrator.mcp.kb.protocol

class KbMcpServerFactory(
    private val kbSearchHandler: KbSearchHandler,
    private val kbReadHandler: KbReadHandler,
    private val kbIngestHandler: KbIngestHandler,
    private val kbLinkHandler: KbLinkHandler,
    private val kbAuditHandler: KbAuditHandler,
    private val kbSyncHandler: KbSyncHandler,
    private val kbFeedbackHandler: KbFeedbackHandler,
    private val kbUnmaskHandler: KbUnmaskHandler
) {
    fun create(): Server {
        val server = Server(
            serverInfo = Implementation(name = "kb-server", version = "1.0.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false)
                )
            )
        )
        KbToolRegistrar.registerAll(server, this)
        return server
    }
}
```

### 4.3 Dependency Graph (KB Server Internal)

```
KbMcpServerFactory
├── KbSearchHandler
│   ├── EmbeddingService (orchestrator-client)
│   ├── VectorDbClient (orchestrator-client)
│   └── RoleContextService (security)
├── KbReadHandler
│   ├── KbEntryRepository (store)
│   ├── ResponseShaper (audit)
│   └── RoleContextService (security)
├── KbIngestHandler
│   ├── PiiMaskingEngine (masking)
│   ├── ContentSegmentationService (segmentation)
│   ├── BrMaskingService (brmasking)
│   ├── KbEntryRepository (store)
│   ├── EmbeddingService (orchestrator-client)
│   ├── VectorDbClient (orchestrator-client)
│   ├── EntityLinkingService (linking)
│   └── QueueService (queue) — for async processing
├── KbLinkHandler
│   ├── EntityLinkingService (linking)
│   └── NetworkService (network)
├── KbAuditHandler
│   ├── AuditQueryService (audit)
│   └── RoleContextService (security)
├── KbSyncHandler
│   ├── TicketCrawler (crawler)
│   ├── ProjectScanner (scanner)
│   └── QueueService (queue)
├── KbFeedbackHandler
│   └── FeedbackService (feedback)
└── KbUnmaskHandler
    ├── BrAccessService (security/br)
    ├── PiiMappingRepository (store)
    ├── AuditService (audit)
    └── RoleContextService (security)
```

### 4.4 Configuration (kb-server application.yml)

```yaml
kb:
  server:
    port: 8081
    transport: http  # stdio | http

  database:
    url: "jdbc:postgresql://localhost:5432/kb_refinery"
    username: "${KB_DB_USER:kb_app}"
    password: "${KB_DB_PASSWORD}"
    pool_size: 10

  embedding:
    provider: "ollama"  # openai | ollama | lmstudio
    model: "nomic-embed-text"
    base_url: "http://localhost:11434"
    dimensions: 768

  vector_db:
    provider: "pgvector"  # pgvector | qdrant
    collection_name: "kb_entries"

  segmentation:
    provider: "ollama"
    model_name: "llama3"
    temperature: 0.1
    timeout_seconds: 30
    br_local_only: true

  masking:
    strategies:
      - email
      - phone
      - bank_account
      - id_card
      - name

  security:
    encryption_key: "${KB_ENCRYPTION_KEY}"
    br_encryption_key: "${BR_ENCRYPTION_KEY}"
    session_ttl_minutes: 30
    rate_limit:
      pii_unmask_per_hour: 10
      br_level1_per_hour: 5
      br_level2_per_hour: 15
      br_level3_per_hour: 30

  queue:
    hpq_capacity: 100
    npq_capacity: 1000
    worker_count: 2
    watchdog_interval_seconds: 60
    stuck_threshold_minutes: 5

  sync:
    jira_base_url: "${JIRA_BASE_URL}"
    jira_token: "${JIRA_API_TOKEN}"
    jira_email: "${JIRA_EMAIL}"
```

### 4.5 Gradle Build Configuration

```kotlin
// kb-server/build.gradle.kts
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.shadow)
    application
}

application {
    mainClass.set("com.orchestrator.mcp.kb.KbMainKt")
}

dependencies {
    // Shared project modules
    implementation(project(":orchestrator-core"))
    implementation(project(":orchestrator-client"))

    // MCP SDK
    implementation(libs.mcp.sdk.server)

    // Ktor Client (for LLM API calls, Jira API)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // KotlinX
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // DI
    implementation(libs.koin.core)

    // Database
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    // LangChain4j (Segmentation + BR Masking)
    implementation("dev.langchain4j:langchain4j:1.0.0-beta1")
    implementation("dev.langchain4j:langchain4j-open-ai:1.0.0-beta1")
    implementation("dev.langchain4j:langchain4j-ollama:1.0.0-beta1")

    // Document Processing (OCR via MCP, PDF/DOCX extraction)
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    // Logging
    implementation(libs.logback.classic)
    // YAML
    implementation(libs.kaml)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.testcontainers:postgresql:1.21.4")
}

tasks.shadowJar {
    archiveBaseName.set("kb-server")
    archiveClassifier.set("all")
    archiveVersion.set("")
    mergeServiceFiles()
}
```



---

## 5. API Contracts Between Orchestrator and KB Server

### 5.1 Communication Protocol

KB Server communicates with Orchestrator via **MCP protocol** (same as any upstream server):

| Aspect | Value |
|--------|-------|
| Protocol | MCP (Model Context Protocol) |
| Transport | stdio (development) / HTTP Streamable (production) |
| Discovery | Orchestrator connects to KB Server as upstream, indexes KB tools |
| Execution | `execute_dynamic_tool` routes to KB Server via `McpConnection` |

### 5.2 Orchestrator Configuration for KB Server

```yaml
# orchestrator application.yml
orchestrator:
  upstream_servers:
    - name: "kb-server"
      transport: "http"
      url: "http://localhost:8081/mcp"
      # OR for stdio:
      # transport: "stdio"
      # command: "java"
      # args: ["-jar", "kb-server-all.jar", "--config=kb-config.yml"]
```

### 5.3 Request/Response Flow

```
Agent → find_tools("search knowledge base")
     → Orchestrator returns: kb_search tool definition

Agent → execute_dynamic_tool(tool_name="kb_search", arguments={query: "MTO-25 BRD"})
     → Orchestrator routes to kb-server via McpConnection
     → kb-server processes request, returns results
     → Orchestrator returns results to Agent
```

### 5.4 Error Contract

KB Server returns errors in standard MCP format:

```json
{
  "content": [{"type": "text", "text": "{\"error\":{\"code\":\"KB_NOT_FOUND\",\"message\":\"No entry found for MTO-999\"}}"}],
  "isError": true
}
```

Error codes:

| Code | HTTP Equiv | Description |
|------|-----------|-------------|
| `KB_NOT_FOUND` | 404 | Entry not found |
| `KB_ACCESS_DENIED` | 403 | Insufficient role/permissions |
| `KB_RATE_LIMITED` | 429 | Rate limit exceeded |
| `KB_VALIDATION_ERROR` | 400 | Invalid input parameters |
| `KB_ENCRYPTION_ERROR` | 500 | Encryption/decryption failure |
| `KB_LLM_TIMEOUT` | 504 | LLM provider timeout |
| `KB_QUEUE_FULL` | 503 | Queue at capacity |
| `KB_INTERNAL_ERROR` | 500 | Unexpected internal error |

---

## 6. Data Migration Strategy

### 6.1 Database Separation

| Option | Description | Recommendation |
|--------|-------------|----------------|
| **A: Shared DB, separate schemas** | Both servers connect to same PostgreSQL, KB uses `kb` schema | ✅ Phase 1 |
| **B: Separate databases** | KB Server has its own PostgreSQL instance | Phase 2 (production) |

**Phase 1 (Recommended):** Shared database with schema separation:

```sql
-- KB Server owns these tables (kb schema):
CREATE SCHEMA IF NOT EXISTS kb;

-- Move tables to kb schema:
ALTER TABLE public.kb_entries SET SCHEMA kb;
ALTER TABLE public.pii_mapping SET SCHEMA kb;
ALTER TABLE public.queue_tasks SET SCHEMA kb;
ALTER TABLE public.entity_links SET SCHEMA kb;
ALTER TABLE public.pii_access_audit SET SCHEMA kb;
ALTER TABLE public.br_access_audit SET SCHEMA kb;
ALTER TABLE public.feedback SET SCHEMA kb;

-- Orchestrator keeps these in public schema:
-- jira_ticket_cache (sync state)
-- jira_ticket_graph (graph data)
-- attachment_queue
-- tool_auto_approve
-- tool_toggle_state
```

### 6.2 Migration Steps

```
Phase 1: Code Separation (No data migration needed)
─────────────────────────────────────────────────────
1. Create kb-server Gradle subproject
2. Move KB packages from orchestrator-server → kb-server
3. Register KB tools in KbMcpServerFactory
4. Configure orchestrator to connect to kb-server as upstream
5. Both servers share same PostgreSQL instance
6. Run integration tests

Phase 2: Schema Separation
─────────────────────────────────────────────────────
1. Create kb schema in PostgreSQL
2. Move KB tables to kb schema (ALTER TABLE SET SCHEMA)
3. Update kb-server connection to use kb schema
4. Update orchestrator to NOT access kb tables directly
5. Verify all queries use correct schema prefix

Phase 3: Database Separation (Optional, for production scale)
─────────────────────────────────────────────────────
1. Create separate PostgreSQL instance for KB
2. Migrate data using pg_dump/pg_restore
3. Update kb-server connection string
4. Remove KB tables from orchestrator database
5. Verify cross-service communication works
```

### 6.3 Backward Compatibility

During migration, maintain backward compatibility:

1. **Orchestrator still has `jira_ticket_cache.kb_ingested` column** — KB Server updates this via a callback or shared DB access
2. **Vector DB collection** — KB Server takes ownership of `kb_entries` collection; orchestrator keeps `mcp_tools` collection
3. **Embedding Service** — Both servers can use the same embedding provider (configured independently)

---

## 7. Deployment Topology

### 7.1 Development (Local)

```
┌─────────────────────────────────────────────┐
│ Developer Machine                            │
│                                              │
│  IDE (Kiro/Claude)                           │
│       │ stdio                                │
│       ▼                                      │
│  orchestrator-server (stdio)                 │
│       │ stdio (subprocess)                   │
│       ▼                                      │
│  kb-server (stdio)                           │
│       │                                      │
│       ▼                                      │
│  PostgreSQL (localhost:5432)                  │
│  Ollama (localhost:11434)                     │
└─────────────────────────────────────────────┘
```

**Configuration for stdio mode:**
```yaml
# orchestrator upstream_servers
- name: "kb-server"
  transport: "stdio"
  command: "java"
  args: ["-jar", "kb-server-all.jar", "--transport=stdio"]
```

### 7.2 Production (HTTP)

```
┌──────────────────────────────────────────────────────────┐
│ Production Environment                                    │
│                                                           │
│  Load Balancer                                            │
│       │                                                   │
│       ▼                                                   │
│  ┌─────────────────┐    ┌─────────────────┐             │
│  │ orchestrator-1   │    │ orchestrator-2   │  (2 replicas)│
│  │ port: 8080       │    │ port: 8080       │             │
│  └────────┬─────────┘    └────────┬─────────┘             │
│           │                        │                       │
│           └────────────┬───────────┘                       │
│                        │ HTTP                               │
│                        ▼                                    │
│  ┌─────────────────────────────────────┐                  │
│  │ kb-server (internal, port: 8081)     │  (1-2 replicas) │
│  │ - CPU-intensive (LLM, embedding)     │                  │
│  │ - I/O-intensive (DB, vector search)  │                  │
│  └────────────────────┬────────────────┘                  │
│                        │                                    │
│           ┌────────────┼────────────┐                      │
│           ▼            ▼            ▼                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                │
│  │PostgreSQL│  │ PgVector │  │  Ollama  │                 │
│  │ (RDS)    │  │ (ext.)   │  │ (GPU)    │                 │
│  └──────────┘  └──────────┘  └──────────┘                │
└──────────────────────────────────────────────────────────┘
```

### 7.3 Docker Compose (Development)

```yaml
version: '3.8'
services:
  orchestrator:
    build: ./orchestrator-server
    ports: ["8080:8080"]
    environment:
      - UPSTREAM_KB_URL=http://kb-server:8081/mcp
    depends_on: [kb-server, postgres]

  kb-server:
    build: ./kb-server
    ports: ["8081:8081"]
    environment:
      - KB_DB_URL=jdbc:postgresql://postgres:5432/kb_refinery
      - KB_ENCRYPTION_KEY=${KB_ENCRYPTION_KEY}
      - BR_ENCRYPTION_KEY=${BR_ENCRYPTION_KEY}
      - OLLAMA_BASE_URL=http://ollama:11434
    depends_on: [postgres, ollama]

  postgres:
    image: pgvector/pgvector:pg16
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: kb_refinery
      POSTGRES_USER: kb_app
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data

  ollama:
    image: ollama/ollama:latest
    ports: ["11434:11434"]
    volumes:
      - ollama_models:/root/.ollama

volumes:
  pgdata:
  ollama_models:
```



---

## 8. Code Migration Plan

### 8.1 Package Mapping (orchestrator-server → kb-server)

| Current Package (orchestrator-server) | Target Package (kb-server) | Notes |
|---------------------------------------|---------------------------|-------|
| `com.orchestrator.mcp.kbstore.*` | `com.orchestrator.mcp.kb.store.*` | Core storage |
| `com.orchestrator.mcp.masking.*` | `com.orchestrator.mcp.kb.masking.*` | PII masking |
| `com.orchestrator.mcp.segmentation.*` | `com.orchestrator.mcp.kb.segmentation.*` | LLM segmentation |
| `com.orchestrator.mcp.brmasking.*` | `com.orchestrator.mcp.kb.brmasking.*` | BR masking |
| `com.orchestrator.mcp.security.br.*` | `com.orchestrator.mcp.kb.security.br.*` | BR access control |
| `com.orchestrator.mcp.security.{Rls*,Role*}` | `com.orchestrator.mcp.kb.security.rls.*` | RLS policies |
| `com.orchestrator.mcp.audit.*` | `com.orchestrator.mcp.kb.audit.*` | Audit logging |
| `com.orchestrator.mcp.linking.*` | `com.orchestrator.mcp.kb.linking.*` | Entity linking |
| `com.orchestrator.mcp.network.*` | `com.orchestrator.mcp.kb.network.*` | Feature network |
| `com.orchestrator.mcp.feedback.*` | `com.orchestrator.mcp.kb.feedback.*` | Feedback |
| `com.orchestrator.mcp.queue.*` | `com.orchestrator.mcp.kb.queue.*` | Task queue |
| `com.orchestrator.mcp.crawler.*` | `com.orchestrator.mcp.kb.crawler.*` | Ticket crawler |
| `com.orchestrator.mcp.scanner.*` | `com.orchestrator.mcp.kb.scanner.*` | Project scanner |
| `com.orchestrator.mcp.ocr.*` | `com.orchestrator.mcp.kb.ocr.*` | OCR |

### 8.2 What Stays in orchestrator-server

| Package | Reason |
|---------|--------|
| `protocol/` | MCP tool routing (find_tools, execute_dynamic_tool) |
| `discovery/` | Tool discovery (semantic search for tools, not KB content) |
| `execution/` | Tool execution dispatcher |
| `registry/` | Tool registry + indexer |
| `management/` | Tool toggle, auto-approve |
| `fileproxy/` | File proxy for tool I/O |
| `session/` | HTTP session management |
| `transport/` | MCP transport (stdio, HTTP) |
| `config/` | Orchestrator config (minus KB sections) |
| `di/` | Orchestrator DI (minus KB modules) |
| `logging/` | Agent log service |
| `dashboard/` | Sync dashboard (moves to kb-server or stays as thin proxy) |
| `graph/` | Graph API (moves to kb-server) |
| `sync/` | Jira sync state (shared — orchestrator tracks sync state, KB does actual sync) |
| `synctools/` | Sync tool handlers (move to kb-server) |

### 8.3 Shared Module Dependencies

```
orchestrator-core (shared)
├── config/OrchestratorConfig.kt  → Split into OrchestratorConfig + KbConfig
├── config/SessionConfig.kt       → Stays shared
├── model/ErrorCodes.kt           → Stays shared
├── model/Exceptions.kt           → Stays shared
├── model/ToolDefinition.kt       → Stays shared (orchestrator uses)
└── util/RetryUtils.kt            → Stays shared

orchestrator-client (shared)
├── embedding/EmbeddingService.kt → Stays shared (both use embeddings)
├── embedding/*Impl.kt            → Stays shared
├── vectordb/VectorDbClient.kt    → Stays shared (both use vector DB)
├── vectordb/*Impl.kt             → Stays shared
├── upstream/                     → Orchestrator only (KB doesn't manage upstreams)
└── vectordb/DatabaseFactory.kt   → Stays shared
```

### 8.4 Migration Execution Order

```
Step 1: Create kb-server subproject skeleton
        - build.gradle.kts
        - KbMain.kt, KbApplication.kt
        - KbMcpServerFactory.kt (empty, registers no tools yet)
        - settings.gradle.kts: include("kb-server")
        - Verify: ./gradlew :kb-server:build passes

Step 2: Move store package (MTO-26 foundation)
        - Move kbstore/ → kb-server/store/
        - Move security/model/KbRole.kt → kb-server/security/
        - Move security/RlsConnectionWrapper.kt → kb-server/security/
        - Verify: Unit tests pass in kb-server

Step 3: Move masking package (MTO-27)
        - Move masking/ → kb-server/masking/
        - Verify: Masking tests pass

Step 4: Move segmentation + brmasking (MTO-28, MTO-30)
        - Move segmentation/ → kb-server/segmentation/
        - Move brmasking/ → kb-server/brmasking/
        - Verify: Tests pass

Step 5: Move security packages (MTO-31, MTO-32, MTO-33)
        - Move security/br/ → kb-server/security/br/
        - Move security/pii/ → kb-server/security/pii/
        - Move security/di/ → kb-server/security/di/
        - Verify: RLS tests pass

Step 6: Move audit, linking, network, feedback (MTO-34-37)
        - Move audit/ → kb-server/audit/
        - Move linking/ → kb-server/linking/
        - Move network/ → kb-server/network/
        - Move feedback/ → kb-server/feedback/
        - Verify: Tests pass

Step 7: Move queue + crawler + scanner (MTO-25, MTO-18, MTO-17)
        - Move queue/ → kb-server/queue/
        - Move crawler/ → kb-server/crawler/
        - Move scanner/ → kb-server/scanner/
        - Move ocr/ → kb-server/ocr/
        - Verify: Tests pass

Step 8: Register KB MCP tools
        - Implement KbToolRegistrar with all tool handlers
        - Wire handlers to moved services via Koin
        - Verify: KB tools discoverable via orchestrator

Step 9: Remove KB packages from orchestrator-server
        - Delete moved packages
        - Remove KB Koin module includes from AppModule
        - Remove KB dependencies from orchestrator-server/build.gradle.kts
        - Verify: orchestrator-server builds clean

Step 10: Integration testing
        - Start both servers
        - Verify find_tools discovers KB tools
        - Verify execute_dynamic_tool routes to KB server
        - Verify full pipeline: ingest → search → read
```



---

## 9. Impact Analysis

### 9.1 Impact on Existing Tickets

| Ticket | Impact | Action Required |
|--------|--------|-----------------|
| MTO-25 (Queue) | Code moves to kb-server | Implement in kb-server from start |
| MTO-26 (Schema) | Already implemented, code moves | Move kbstore/ package |
| MTO-27 (PII Masking) | Already implemented, code moves | Move masking/ package |
| MTO-28 (Segmentation) | Already implemented, code moves | Move segmentation/ package |
| MTO-29 (OCR) | Already implemented, code moves | Move ocr/ package |
| MTO-30 (BR Masking) | Already implemented, code moves | Move brmasking/ package |
| MTO-31 (RLS) | Already implemented, code moves | Move security/ package |
| MTO-32 (PII Access) | Code moves | Move security/pii/ |
| MTO-33 (BR Access) | Already implemented, code moves | Move security/br/ package |
| MTO-34 (Audit) | Already implemented, code moves | Move audit/ package |
| MTO-35 (Linking) | Already implemented, code moves | Move linking/ package |
| MTO-36 (Network) | Already implemented, code moves | Move network/ package |
| MTO-37 (Feedback) | Already implemented, code moves | Move feedback/ package |

### 9.2 Impact on Agent Workflows

| Agent | Current Behavior | After Migration |
|-------|-----------------|-----------------|
| SM | Calls `find_tools("kb")` → gets KB tools | **No change** — tools still discoverable |
| BA | Calls `kb_ingest` via `execute_dynamic_tool` | **No change** — same tool interface |
| TA | Calls `kb_search` for BRD lookup | **No change** — same tool interface |
| DEV | Calls `kb_read` for implementation context | **No change** — same tool interface |
| QA | Calls `kb_search` for test context | **No change** — same tool interface |

**Key insight:** Because KB tools are accessed via MCP protocol through the orchestrator, the separation is **transparent to agents**. They don't know or care whether the tool runs in-process or in a separate server.

### 9.3 Performance Considerations

| Metric | Current (In-Process) | After (Separate Server) | Mitigation |
|--------|---------------------|------------------------|------------|
| KB search latency | ~50ms | ~55ms (+5ms network) | Negligible for HTTP localhost |
| KB ingest latency | ~5-10s (LLM) | ~5-10s (LLM) | No change — LLM dominates |
| Orchestrator startup | ~8s (loads KB modules) | ~3s (no KB modules) | ✅ Improvement |
| Memory usage (orchestrator) | ~512MB | ~256MB | ✅ Improvement |
| Memory usage (kb-server) | N/A | ~512MB | Dedicated resources |

### 9.4 Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Network latency between servers | Low | Low | Same host in dev; internal network in prod |
| KB server crash affects orchestrator | Medium | Medium | Orchestrator handles upstream failures gracefully (existing pattern) |
| Configuration drift between servers | Medium | Medium | Shared orchestrator-core config models |
| Migration breaks existing tests | High | Medium | Incremental migration with CI verification at each step |
| Increased operational complexity | Medium | Low | Docker Compose for dev; K8s for prod |

---

## 10. Alternatives Considered

### 10.1 Option A: Keep Everything in orchestrator-server (Status Quo)

**Pros:**
- No migration effort
- Single deployment unit
- Simpler local development

**Cons:**
- Monolith grows to 100+ files
- Cannot scale KB independently
- LLM timeouts affect tool routing
- Deployment risk increases with each change

**Verdict:** ❌ Not sustainable as KB Refinery scope grows

### 10.2 Option B: Gradle Submodule Only (No Separate Process)

**Pros:**
- Code separation without operational complexity
- Shared JVM process
- No network overhead

**Cons:**
- Still single deployment unit
- Cannot scale independently
- LLM processing still blocks orchestrator threads
- No fault isolation

**Verdict:** ⚠️ Acceptable as intermediate step, but doesn't solve scaling/isolation

### 10.3 Option C: Separate MCP Server (Recommended) ✅

**Pros:**
- Full separation of concerns
- Independent scaling (GPU for LLM, more DB connections for KB)
- Fault isolation (KB crash doesn't kill orchestrator)
- Independent deployment
- Transparent to agents (MCP protocol abstraction)

**Cons:**
- Migration effort (~2-3 sprints)
- Operational complexity (2 servers instead of 1)
- Network latency (negligible for localhost/internal)

**Verdict:** ✅ Best long-term architecture

### 10.4 Option D: Microservices (Multiple KB Services)

**Pros:**
- Maximum granularity
- Each service scales independently

**Cons:**
- Over-engineering for current scale
- Distributed transaction complexity
- Too many services to manage
- Overkill for single-team project

**Verdict:** ❌ Over-engineering — revisit when team grows to 5+ developers

---

## 11. Implementation Timeline

### Phase 1: Foundation (Sprint 1 — 2 weeks)

| Task | Effort | Dependencies |
|------|--------|-------------|
| Create kb-server subproject skeleton | 2h | None |
| Implement KbMain.kt (stdio + HTTP) | 4h | Skeleton |
| Implement KbMcpServerFactory (empty) | 2h | KbMain |
| Move kbstore/ package | 4h | Skeleton |
| Move security/ package (RLS, roles) | 4h | kbstore |
| Verify unit tests pass | 4h | All moves |
| **Total** | **~20h** | |

### Phase 2: Core Pipeline (Sprint 2 — 2 weeks)

| Task | Effort | Dependencies |
|------|--------|-------------|
| Move masking/ package | 2h | Phase 1 |
| Move segmentation/ package | 4h | Phase 1 |
| Move brmasking/ package | 2h | Phase 1 |
| Move queue/ package | 4h | Phase 1 |
| Move crawler/ + scanner/ | 4h | Phase 1 |
| Implement KbToolRegistrar (all tools) | 8h | All moves |
| Wire Koin DI for kb-server | 4h | All moves |
| **Total** | **~28h** | |

### Phase 3: Advanced Features + Cleanup (Sprint 3 — 2 weeks)

| Task | Effort | Dependencies |
|------|--------|-------------|
| Move audit/ package | 2h | Phase 2 |
| Move linking/ + network/ | 4h | Phase 2 |
| Move feedback/ package | 2h | Phase 2 |
| Move ocr/ package | 2h | Phase 2 |
| Remove KB from orchestrator-server | 4h | All moves verified |
| Integration tests (full pipeline) | 8h | Cleanup |
| Docker Compose setup | 4h | Integration tests |
| Documentation update | 4h | All |
| **Total** | **~30h** | |

### Total Estimated Effort: ~78 hours (3 sprints)



---

## 12. Testing Strategy

### 12.1 Unit Tests (Per Package)

Each moved package retains its existing unit tests. Test dependencies update:
- `orchestrator-server` test → `kb-server` test
- Mock boundaries remain the same (interface-based design)

### 12.2 Integration Tests

| Test Category | Scope | Framework |
|---------------|-------|-----------|
| KB Store Integration | PostgreSQL + encryption | Testcontainers + Kotest |
| KB Pipeline Integration | Masking → Segmentation → Storage | Kotest + MockK (mock LLM) |
| KB MCP Protocol | Tool registration + execution | Ktor TestHost |
| Cross-Server Integration | Orchestrator → KB Server routing | Docker Compose + HTTP |

### 12.3 E2E Test Scenario

```kotlin
// E2E: Full KB pipeline via MCP protocol
test("ingest and search via MCP") {
    // 1. Start kb-server (HTTP mode)
    // 2. Configure orchestrator to connect to kb-server
    // 3. Call find_tools("ingest knowledge base") → returns kb_ingest
    // 4. Call execute_dynamic_tool("kb_ingest", {title: "Test", content: "..."})
    // 5. Call execute_dynamic_tool("kb_search", {query: "Test"})
    // 6. Verify search returns the ingested entry
}
```

### 12.4 Performance Benchmarks

| Benchmark | Target | Method |
|-----------|--------|--------|
| KB search (vector) | < 100ms p95 | JMH microbenchmark |
| KB ingest (full pipeline) | < 15s p95 | Integration test with real LLM |
| KB read (single entry) | < 50ms p95 | Load test with k6 |
| Orchestrator → KB routing | < 10ms overhead | Latency comparison test |

---

## 13. Security Considerations

### 13.1 Network Security

| Concern | Mitigation |
|---------|-----------|
| KB Server exposed to network | Internal-only port (not exposed to internet) |
| Data in transit | TLS for HTTP transport in production |
| Encryption keys | Environment variables, never in config files |
| DB credentials | Separate credentials for KB Server |

### 13.2 Access Control Architecture

```
Agent Request → Orchestrator → KB Server → RLS (PostgreSQL)
                                    ↓
                              Role Context
                              (from request metadata)
```

KB Server determines the caller's role from:
1. **MCP request metadata** (agent name in tool arguments)
2. **Session token** (for BR/PII unmask operations)
3. **Default role** (DEVELOPER for standard read operations)

### 13.3 Encryption Key Management

| Key | Owner | Storage |
|-----|-------|---------|
| `KB_ENCRYPTION_KEY` | KB Server | Environment variable |
| `BR_ENCRYPTION_KEY` | KB Server | Environment variable |
| DB password | KB Server | Environment variable |
| LLM API key | KB Server | Environment variable |

**Important:** Orchestrator does NOT need access to KB encryption keys. All encryption/decryption happens within KB Server.

---

## 14. Observability

### 14.1 Logging

| Server | Log Format | Key Events |
|--------|-----------|------------|
| orchestrator-server | JSON (structured) | Tool routing, upstream connection |
| kb-server | JSON (structured) | Ingestion, search, access control, audit |

### 14.2 Metrics (Future)

| Metric | Source | Purpose |
|--------|--------|---------|
| `kb.search.latency` | KB Server | Search performance |
| `kb.ingest.duration` | KB Server | Pipeline throughput |
| `kb.queue.depth` | KB Server | Queue health |
| `kb.access.denied` | KB Server | Security monitoring |
| `orchestrator.kb.routing.latency` | Orchestrator | Network overhead |

### 14.3 Health Checks

KB Server exposes `/health` endpoint:
```json
{
  "status": "healthy",
  "components": {
    "database": "up",
    "vector_db": "up",
    "llm_provider": "up",
    "queue": { "hpq_depth": 0, "npq_depth": 12 }
  }
}
```

Orchestrator's `HealthMonitor` checks KB Server health periodically (existing pattern).

---

## 15. Open Questions & Decisions Needed

| # | Question | Options | Recommendation | Owner | Target Date |
|---|----------|---------|----------------|-------|-------------|
| 1 | Should KB Server have its own vector DB collection or share with orchestrator? | Separate / Shared | **Separate** — `kb_entries` for KB, `mcp_tools` for orchestrator | TA | Sprint 1 |
| 2 | Should Jira sync logic stay in orchestrator or move to KB? | Stay / Move | **Move to KB** — sync is KB's responsibility | TA | Sprint 1 |
| 3 | Should graph/dashboard UI be served by KB Server or orchestrator? | KB / Orchestrator / Separate | **KB Server** — it owns the data | TA | Sprint 2 |
| 4 | How to handle `jira_ticket_cache.kb_ingested` flag? | Shared table / Event / Callback | **Shared DB** (Phase 1), **Event** (Phase 2) | TA | Sprint 1 |
| 5 | Should OCR (MTO-29) stay in KB or be a separate utility? | KB / Separate | **KB** — OCR is part of ingestion pipeline | TA | Sprint 2 |
| 6 | Transport for dev: stdio subprocess or HTTP? | stdio / HTTP | **HTTP** — simpler debugging, hot-reload | DEV | Sprint 1 |
| 7 | Should we create a `kb-client` module for type-safe KB API? | Yes / No | **No** (Phase 1) — MCP protocol is sufficient | TA | Future |

---

## 16. Conclusion & Recommendations

### 16.1 Recommendation

**Proceed with Option C: Separate MCP Server** — tách KB thành `kb-server` Gradle subproject chạy như một MCP server riêng biệt.

### 16.2 Key Benefits

1. **Transparent to agents** — No changes needed in agent prompts or workflows
2. **Leverages existing infrastructure** — Uses MCP protocol, Koin DI, same tech stack
3. **Incremental migration** — Can be done package-by-package with CI verification
4. **Future-proof** — Enables independent scaling, deployment, and team ownership

### 16.3 Immediate Next Steps

1. ✅ Create `kb-server` subproject in `settings.gradle.kts`
2. ✅ Implement minimal `KbMain.kt` + `KbMcpServerFactory.kt`
3. ✅ Move `kbstore/` package as first migration
4. ✅ Configure orchestrator to connect to kb-server as upstream
5. ✅ Verify end-to-end: `find_tools("kb")` → `execute_dynamic_tool("kb_search")`

### 16.4 Success Criteria

| Criteria | Measurement |
|----------|-------------|
| All KB tools discoverable via orchestrator | `find_tools` returns KB tools |
| All KB operations work via MCP routing | Integration tests pass |
| Orchestrator startup time reduced | < 4s (from ~8s) |
| KB Server independently deployable | `java -jar kb-server-all.jar` works standalone |
| No agent workflow changes required | Existing agent prompts work unchanged |
| Test coverage maintained | All existing tests pass in new location |

---

## Appendix A: Ticket-to-Package Mapping

| Ticket | Summary | KB Server Package |
|--------|---------|-------------------|
| MTO-25 | Dual-Priority Queue | `kb.queue` |
| MTO-26 | KB Entries Schema | `kb.store` |
| MTO-27 | PII Masking Engine | `kb.masking` |
| MTO-28 | Content Segmentation | `kb.segmentation` |
| MTO-29 | OCR Integration | `kb.ocr` |
| MTO-30 | BR Masking | `kb.brmasking` |
| MTO-31 | RLS Policies | `kb.security.rls` |
| MTO-32 | PII Access Control | `kb.security.pii` |
| MTO-33 | BR Encryption & Access | `kb.security.br` |
| MTO-34 | Audit & Response Shaping | `kb.audit` |
| MTO-35 | Entity Linking | `kb.linking` |
| MTO-36 | Feature Network | `kb.network` |
| MTO-37 | Feedback & Correction | `kb.feedback` |

---

## Appendix B: Architecture Decision Record (ADR)

**ADR-001: Separate KB Server via MCP Protocol**

- **Status:** Proposed
- **Context:** KB Refinery (MTO-24 epic, 13 sub-tickets) adds significant complexity to orchestrator-server. The KB domain (AI processing, encryption, access control) is fundamentally different from orchestrator domain (tool routing, upstream management).
- **Decision:** Extract KB into a separate MCP server (`kb-server`) that communicates with orchestrator via standard MCP protocol.
- **Consequences:**
  - (+) Clean separation of concerns
  - (+) Independent scaling and deployment
  - (+) Fault isolation
  - (+) Transparent to agents
  - (-) Migration effort (~78 hours)
  - (-) Slightly increased operational complexity
  - (-) ~5ms additional latency per KB operation

---

*Document generated by TA Agent — Senior Technical Architect*
*Last updated: 2026-05-10*
