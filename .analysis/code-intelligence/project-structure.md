# Project Structure — MCPOrchestration

**Last Updated:** 2026-07-06
**Indexed By:** SM agent (manual full re-index)

## 1. Project Overview

| Property | Value |
|----------|-------|
| **Project Name** | MCPOrchestration (MTO) |
| **Type** | Multi-module Gradle application (6 modules) |
| **Language** | Kotlin 2.3.20 |
| **Platform** | JVM 21 |
| **Framework** | Ktor 3.4.0 (server + client) |
| **DI** | Koin 4.1.1 |
| **Serialization** | kotlinx.serialization 1.8.1 + kaml 0.77.0 (YAML) |
| **MCP SDK** | io.modelcontextprotocol:kotlin-sdk-server 0.12.0 |
| **Build Tool** | Gradle (Kotlin DSL) with version catalog |
| **Packaging** | Fat JAR via Shadow plugin |
| **Transport** | stdio (default), HTTP/Streamable HTTP |
| **Database** | PostgreSQL (pgvector) + HikariCP |
| **Vector DB** | Qdrant / PgVector / FAISS (local fallback) |
| **AI/LLM** | LangChain4j (OpenAI, Ollama, Azure OpenAI) |

## 2. Module Architecture

```
mcp-orchestrator (root)
├── orchestrator-core        # Shared models, config, exceptions, utilities
├── orchestrator-client      # Upstream MCP connections, embedding, vector DB clients
├── orchestrator-server      # Main server: MCP protocol, Jira sync, KB store, graph, dashboard
├── orchestrator-bridge      # Bridge process: connects IDE to orchestrator via HTTP Streamable
├── kb-server                # Standalone Knowledge Base server (MCP tools: kb_ingest, kb_search)
└── sync-pipeline            # Shared sync pipeline: multi-dimensional Jira indexing (MTO-47)
```

| Module | Language | Purpose | Platform | Fat JAR |
|--------|----------|---------|----------|---------|
| **orchestrator-core** | Kotlin | Shared domain models, config DTOs, exceptions, utilities | JVM 21 | — (library) |
| **orchestrator-client** | Kotlin | Upstream MCP connections (stdio/HTTP), embedding services, vector DB clients | JVM 21 | — (library) |
| **orchestrator-server** | Kotlin | Main MCP orchestrator server — tool discovery, execution, Jira sync, KB store, graph API, dashboard | JVM 21 | `mcp-orchestrator-all.jar` |
| **orchestrator-bridge** | Kotlin | Bridge between IDE (Kiro/VS Code) and orchestrator — file transfer, tool promotion, health checks | JVM 21 | `orchestrator-bridge-all.jar` |
| **kb-server** | Kotlin | Standalone KB server — ingest, search, PII masking, content segmentation, graph viewer | JVM 21 | `kb-server-all.jar` |
| **sync-pipeline** | Kotlin | Shared sync pipeline — multi-dimensional Jira indexing, dimension strategy, AI feature detection | JVM 21 | — (library) |

### Inter-Module Dependencies

```
orchestrator-server ──depends──> orchestrator-core
orchestrator-server ──depends──> orchestrator-client
orchestrator-bridge ──depends──> orchestrator-core (implied via config)
kb-server ──depends──> orchestrator-core
kb-server ──depends──> orchestrator-client
sync-pipeline ──depends──> orchestrator-client
```

## 3. Tech Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Kotlin | 2.3.20 |
| JVM | JDK | 21 |
| Server Framework | Ktor (Netty) | 3.4.0 |
| HTTP Client | Ktor Client (CIO) | 3.4.0 |
| MCP Protocol | MCP Kotlin SDK | 0.12.0 |
| DI | Koin | 4.1.1 |
| Serialization | kotlinx.serialization-json | 1.8.1 |
| YAML | kaml | 0.77.0 |
| Coroutines | kotlinx.coroutines | 1.10.2 |
| Date/Time | kotlinx.datetime | 0.6.2 |
| IO | kotlinx.io | 0.7.0 |
| Logging | Logback Classic | 1.5.18 |
| Database | PostgreSQL + pgvector | — |
| Connection Pool | HikariCP | — |
| Vector DB | Qdrant / PgVector / FAISS | 1.9+ |
| Embeddings | OpenAI / Ollama / LM Studio / Azure OpenAI | text-embedding-3-small |
| AI/LLM | LangChain4j | 1.0.0-beta1 |
| Document Processing | Apache PDFBox | 3.0.4 |
| Document Processing | Apache POI | 5.3.0 |
| Testing | JUnit 5 | 5.11.4 |
| Testing | Kotest | 5.9.1 |
| Testing | MockK | 1.14.2 |
| Testing | Testcontainers | 1.21.1 |
| Testing | Ktor Test Host | 3.4.0 |
| 3D Visualization | 3d-force-graph (JS) | 1.x |

## 4. Module Details

### 4.1 orchestrator-core

Shared library with domain models and utilities.

```
com.orchestrator.mcp.core/
├── config/
│   ├── ConfigurationManager.kt    # Interface — config loading
│   ├── InfraConfig.kt             # Infrastructure config DTOs
│   └── OrchestratorConfig.kt      # @Serializable config data classes
├── model/
│   ├── ErrorCodes.kt              # MCP JSON-RPC error codes
│   ├── Exceptions.kt              # Sealed exception hierarchy
│   └── ToolDefinition.kt          # ToolDefinition + ToolEntry domain models
└── util/
    └── RetryUtils.kt              # Exponential backoff retry utility
```

### 4.2 orchestrator-client

Client-side services for connecting to upstream MCP servers and external services.

```
com.orchestrator.mcp.client/
├── embedding/
│   ├── EmbeddingService.kt           # Interface — text → vector
│   ├── OpenAiEmbeddingService.kt     # OpenAI API implementation
│   ├── OllamaEmbeddingService.kt     # Ollama local LLM implementation
│   └── LmStudioEmbeddingService.kt   # LM Studio implementation
├── upstream/
│   ├── UpstreamServerManager.kt      # Interface — manage upstream connections
│   ├── UpstreamServerManagerImpl.kt  # Implementation
│   ├── McpConnection.kt             # Interface — single connection
│   ├── StdioMcpConnection.kt        # stdio process connection
│   ├── HttpMcpConnection.kt         # HTTP/SSE connection
│   ├── HealthMonitor.kt             # Periodic health checks + auto-reconnect
│   └── model/
│       ├── ServerState.kt           # Enum: DISCONNECTED, CONNECTING, CONNECTED, ERROR
│       └── UpstreamServerInfo.kt    # Runtime info DTO
└── vectordb/
    ├── VectorDbClient.kt            # Interface — vector DB operations
    ├── QdrantVectorDbClient.kt      # Qdrant REST API client
    ├── PgVectorDbClient.kt          # PostgreSQL pgvector client
    ├── FaissVectorDbClient.kt       # FAISS in-memory (local fallback)
    ├── DatabaseFactory.kt           # HikariCP DataSource factory
    ├── DatabaseInitializer.kt       # Schema initialization
    └── model/
        └── VectorPoint.kt           # VectorPoint + SearchResult DTOs
```

### 4.3 orchestrator-server (Main Server)

The primary MCP orchestrator server with all business logic.

```
com.orchestrator.mcp/
├── Application.kt                    # Ktor Application setup
├── HttpStreamableServer.kt           # HTTP Streamable transport + static file serving
├── Main.kt                           # Entry point (HTTP mode)
├── StdioMain.kt                      # Entry point (stdio mode)
├── config/                           # Server-specific config (ConfigDbSync, Validator, Scanner)
├── di/AppModule.kt                   # Koin DI module — all bindings
├── discovery/                        # Tool discovery (semantic + keyword search)
├── execution/                        # Tool execution dispatcher
├── protocol/                         # MCP protocol handling, tool registration
├── registry/                         # In-memory tool registry + indexer
├── transport/                        # Transport implementations (stdio, HTTP streamable, SSE)
│
├── ── Jira Sync Module ──
├── sync/                             # Sync state management, ticket cache, graph repository
├── synctools/                        # MCP tools: jira_project_sync, jira_sync_status, jira_ticket_graph
├── scanner/                          # Jira project scanner (JQL builder, page fetcher, batch upserter)
├── crawler/                          # Ticket crawler (ADF parser, content fetcher, KB ingestor, graph builder)
├── dashboard/                        # Sync dashboard (SSE events, WebSocket, status API)
│
├── ── Graph Module ──
├── graph/                            # Graph API (3D visualization backend)
│   ├── GraphService.kt              # Business logic — project graph + subgraph (BFS)
│   ├── GraphDataRepository.kt       # Data access — delegates to sync repos
│   ├── GraphRoutes.kt               # Ktor routes: /sync/graph/{project}
│   ├── model/GraphResponse.kt       # GraphNode, GraphEdge, GraphMetadata DTOs
│   └── views/                       # View mode strategies
│       ├── ViewModeStrategy.kt      # Interface
│       ├── HierarchyViewStrategy.kt # Color by issue type, size by hierarchy level
│       ├── DependencyViewStrategy.kt# Color by status
│       └── TeamViewStrategy.kt      # Color by assignee
│
├── ── Knowledge Base Store ──
├── kbstore/                          # KB storage (encryption, repository)
│
├── ── Security & Compliance ──
├── security/                         # RLS (Row-Level Security), role context, PII/BR masking
├── masking/                          # PII masking engine (strategy pattern)
├── brmasking/                        # Business Rule masking (crypto)
│
├── ── Content Processing ──
├── attachment/                       # Attachment download + text extraction (PDF, DOCX)
├── ocr/                              # OCR service for image attachments
├── segmentation/                     # Content segmentation (LangChain4j)
│
├── ── Infrastructure ──
├── queue/                            # Dual-priority queue (crash recovery, watchdog, workers)
├── session/                          # HTTP session management
├── network/                          # Network service
├── logging/                          # Agent log service
├── audit/                            # Audit trail (query + write)
├── feedback/                         # User feedback collection
├── linking/                          # Entity linking service
├── promotion/                        # Smart tool promotion (compact schema)
├── management/                       # Tool filter + management service
├── fileproxy/                        # File proxy (upload, download, wrapper tools)
└── usermanagement/                   # User management (CRUD, roles, routes)
```

### 4.4 orchestrator-bridge

Bridge process that connects IDE (Kiro/VS Code) to the orchestrator server.

```
com.orchestrator.mcp.bridge/
├── BridgeApplication.kt         # Entry point
├── BridgeConfig.kt              # Bridge configuration
├── BridgeServer.kt              # MCP server for IDE communication
├── BridgeToolPromoter.kt        # Promote frequently-used tools
├── FileTransferHandler.kt       # File transfer between IDE and server
├── HealthCheckConfig.kt         # Health check configuration
├── HealthCheckManager.kt        # Monitor orchestrator health
├── HttpStreamableClient.kt      # HTTP Streamable client to orchestrator
├── ImageEmbedder.kt             # Embed images in markdown
├── LocalEmbedImagesTool.kt      # MCP tool: embed_images
├── LocalStreamWriteTool.kt      # MCP tool: stream_write_file
├── ReconnectionManager.kt       # Auto-reconnect logic
└── WorkspaceContext.kt          # IDE workspace context
```

### 4.5 kb-server

Standalone Knowledge Base server with MCP tools.

```
com.orchestrator.mcp.kb/
├── KbApplication.kt              # Application setup
├── KbMain.kt                     # Entry point
├── KbExceptions.kt               # KB-specific exceptions
├── WorkspaceContext.kt           # Workspace context
├── config/                       # KB config (KbConfig, KbConfigLoader, sections)
├── di/KbDiModule.kt              # Koin DI module
├── protocol/                     # MCP protocol (KbMcpServerFactory, KbToolRegistrar, handlers)
├── transport/KbHttpTransport.kt  # HTTP transport + static file serving + graph viewer
├── store/                        # KB storage (database, encryption, vector, repository)
├── queue/                        # Async processing queue (dual-priority, crash recovery)
├── masking/                      # PII detection + masking
├── audit/                        # Audit service
├── network/                      # Network service
└── graph/                        # Graph viewer (same as orchestrator-server graph module)
    ├── GraphService.kt
    ├── GraphDataRepository.kt
    ├── GraphRoutes.kt
    ├── model/
    └── views/
```

### 4.6 sync-pipeline

Shared library for multi-dimensional Jira indexing (MTO-47).

```
com.orchestrator.mcp.sync.pipeline/
├── SyncOrchestrator.kt                # Interface — main entry point
├── model/
│   ├── CrawledTicket.kt               # Full ticket data after Jira fetch
│   ├── CrawledComment.kt              # Comment data
│   ├── CrawledLink.kt                 # Issue link data
│   ├── CrawledAttachment.kt           # Attachment metadata
│   ├── JiraUser.kt                    # User identity
│   ├── IndexEntry.kt                  # Universal indexed record
│   ├── SourceRef.kt                   # Provenance tracking
│   ├── DimensionConfig.kt             # Dimension configuration
│   ├── SyncOptions.kt                 # Sync options
│   ├── SyncResult.kt                  # Sync result
│   ├── SyncProgress.kt               # Progress tracking
│   └── SyncStatus.kt                 # Status enum
├── dimension/
│   └── IndexDimension.kt             # Strategy interface for dimensions
├── storage/
│   ├── IndexWriter.kt                # Interface — write index entries
│   └── VectorIndexWriter.kt          # Interface — vector operations
├── state/
│   └── SyncStateTracker.kt           # Interface — state machine
├── ai/
│   ├── AiAnalysisService.kt          # Interface — AI analysis
│   ├── FeatureGroup.kt               # Feature group model
│   └── TicketSummary.kt              # Ticket summary for AI
└── config/
    ├── SyncPipelineConfig.kt          # Top-level config
    └── SyncSubConfigs.kt              # Sub-configurations (AI, embedding, vector, etc.)
```

## 5. UI Pages (Web Frontend)

### 5.1 Graph Viewer (`graph-viewer.html`)

**Location:** `orchestrator-server/src/main/resources/static/graph-viewer.html` + `kb-server/src/main/resources/static/graph-viewer.html`

**Technology:** Vanilla JS + [3d-force-graph](https://github.com/vasturiano/3d-force-graph) (Three.js-based)

**Features:**
- 3D force-directed graph visualization of Jira issues
- 3 view modes: Hierarchy (color by type), Dependency (color by status), Team (color by assignee)
- Node click → details panel (summary, type, status, priority, group)
- Node hover → highlight connected nodes/edges
- Search → camera fly-to matching node
- Legend (auto-generated from node groups)
- Stats bar (node count, edge count, view mode)

**API Endpoint:** `GET /sync/graph/{projectKey}?view={hierarchy|dependency|team}`

**Current Limitations:**
- No filtering (by status, type, priority, sprint, label)
- No timeline/sprint view
- No Kanban board view
- No node grouping/clustering
- No export (PNG, SVG)
- No minimap/overview
- No zoom controls (only mouse)
- No dark/light theme toggle
- No responsive design for mobile
- Details panel is minimal (no links to Jira, no sub-tasks, no comments)
- No real-time updates (must refresh to see changes)
- No multi-project support in single view
- No path highlighting (shortest path between two nodes)
- No critical path visualization
- No sprint burndown overlay

### 5.2 Sync Dashboard (`sync-dashboard.html`)

**Location:** `orchestrator-server/src/main/resources/static/sync-dashboard.html`

**Technology:** Vanilla JS + Server-Sent Events (SSE)

**Features:**
- Real-time sync progress (SSE via `/sync/live`)
- Start/Stop sync buttons
- Progress bar with percentage
- Status cards: Status, Synced Issues, Last Sync
- Queue badges: Pending, Processing, Completed, Failed
- Event log (recent 50 events)
- WebSocket connection indicator
- Polling fallback (every 10s)

**API Endpoints:**
- `GET /sync/live` — SSE stream
- `POST /sync/start` — Start sync `{projectKey, fullSync}`
- `POST /sync/stop` — Stop sync `{projectKey}`
- `GET /sync/status/{projectKey}` — Poll status

**Current Limitations:**
- Single project at a time
- No historical sync data/charts
- No attachment processing progress details
- No error details/retry UI
- No configuration UI
- No multi-project overview
- No sync scheduling UI

## 6. API Endpoints Summary

### orchestrator-server HTTP endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/mcp` | MCP Streamable HTTP endpoint |
| GET | `/sync/graph/{projectKey}` | Graph data (JSON) |
| GET | `/sync/graph/{projectKey}/{issueKey}` | Subgraph (BFS traversal) |
| GET | `/sync/live` | SSE event stream |
| POST | `/sync/start` | Start Jira sync |
| POST | `/sync/stop` | Stop Jira sync |
| GET | `/sync/status/{projectKey}` | Sync status |
| GET | `/sync/graph-viewer` | Serve graph-viewer.html |
| GET | `/static/*` | Static files |
| GET | `/health` | Health check |

### kb-server HTTP endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/mcp` | MCP endpoint (kb_ingest, kb_search tools) |
| GET | `/sync/graph/{projectKey}` | Graph data |
| GET | `/sync/graph-viewer` | Serve graph-viewer.html |
| GET | `/graph/{projectKey}` | Graph data (alias) |
| GET | `/static/*` | Static files |
| GET | `/health` | Health check |

## 7. MCP Tools Exposed

### orchestrator-server tools

| Tool | Description |
|------|-------------|
| `find_tools` | Semantic + keyword search for tools across upstream servers |
| `execute_dynamic_tool` | Execute any discovered tool on upstream server |
| `jira_project_sync` | Trigger Jira project sync |
| `jira_sync_status` | Get sync status |
| `jira_ticket_graph` | Get ticket graph data |
| `agent_log` | Write agent execution log |
| `embed_images` | Embed images in markdown as base64 |
| `stream_write_file` | Write file to disk |
| `export_drawio` | Export draw.io to PNG |

### kb-server tools

| Tool | Description |
|------|-------------|
| `kb_ingest` | Ingest content into KB (PII masking, segmentation, embedding, indexing) |
| `kb_search` | Semantic search KB entries (RLS-filtered) |

## 8. Database Schema (PostgreSQL)

### orchestrator-server tables

| Table | Purpose |
|-------|---------|
| `sync_state` | Sync state per project (status, progress, timestamps) |
| `ticket_cache` | Cached Jira tickets (key, summary, type, status, priority, labels) |
| `ticket_graph` | Ticket relationships (source_key, target_key, link_type) |
| `attachment_queue` | Attachment processing queue |
| `mcp_servers` | Registered upstream MCP servers |
| `tool_index` | Tool definitions + embeddings |
| `audit_log` | Audit trail |
| `user_sessions` | HTTP sessions |
| `users` | User management |
| `feedback` | User feedback |

### kb-server tables

| Table | Purpose |
|-------|---------|
| `kb_entries` | Knowledge base entries (title, content, tags, embeddings) |
| `kb_segments` | Content segments (chunked for better retrieval) |
| `kb_queue` | Async processing queue |
| `kb_audit` | KB audit trail |

## 9. Architecture Patterns

| Pattern | Implementation |
|---------|---------------|
| **Multi-module Gradle** | 5 modules with shared core + client libraries |
| **DI** | Koin modules per feature area |
| **Interface/Impl** | All services use interface + impl pattern |
| **Sealed Exceptions** | `McpOrchestratorException` sealed class |
| **Strategy Pattern** | ViewModeStrategy (graph views), VectorDbClient, EmbeddingService |
| **Event Bus** | SharedFlow-based SyncEventBus for SSE |
| **Dual-Priority Queue** | High/normal priority async task processing |
| **Crash Recovery** | Queue watchdog + crash recovery service |
| **BFS Graph Traversal** | Subgraph extraction with configurable depth |
| **Content Pipeline** | Crawl → Parse ADF → Extract text → Segment → Embed → Index |
| **RLS (Row-Level Security)** | PostgreSQL RLS policies for multi-tenant KB access |
| **PII Masking** | Strategy-based PII detection + masking before storage |
| **Tool Promotion** | Smart promotion of frequently-used tools to reduce discovery latency |
| **File Proxy** | Transparent file transfer between IDE and server |

## 10. Build & Run Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build all modules + run tests |
| `./gradlew test` | Run all tests |
| `./gradlew :orchestrator-server:shadowJar` | Build orchestrator fat JAR |
| `./gradlew :kb-server:shadowJar` | Build KB server fat JAR |
| `./gradlew :orchestrator-bridge:shadowJar` | Build bridge fat JAR |
| `java -jar orchestrator-server/build/libs/mcp-orchestrator-all.jar` | Run orchestrator (HTTP) |
| `java -jar kb-server/build/libs/kb-server-all.jar` | Run KB server |
| `java -jar orchestrator-bridge/build/libs/orchestrator-bridge-all.jar` | Run bridge |

## 11. External Dependencies (Runtime)

| Service | Required | Purpose |
|---------|----------|---------|
| **PostgreSQL** | Yes | Primary database (tickets, KB, users, audit) |
| **pgvector extension** | Yes (for KB) | Vector similarity search |
| **OpenAI API** | Yes (for semantic search) | Text embedding generation |
| **Qdrant** | Optional | External vector DB (alternative to pgvector) |
| **Ollama** | Optional | Local LLM for embeddings |
| **Upstream MCP Servers** | Yes | Actual tool providers (Jira, GitHub, etc.) |

## 12. Source Code Statistics

| Module | Main .kt files | Test .kt files | Packages |
|--------|---------------|----------------|----------|
| orchestrator-core | 7 | — | 3 |
| orchestrator-client | 19 | — | 4 |
| orchestrator-server | ~120+ | ~40+ | 30+ |
| orchestrator-bridge | 13 | — | 1 |
| kb-server | ~30+ | ~10+ | 10 |
| **Total** | **~190+** | **~50+** | **48+** |
