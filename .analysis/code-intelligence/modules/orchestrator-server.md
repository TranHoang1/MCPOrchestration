# Module Analysis — orchestrator-server

**Last Updated:** 2026-07-06
**Language:** Kotlin 2.3.20 | **Framework:** Ktor 3.4.0 | **Platform:** JVM 21

## Overview

Main MCP orchestrator server. Handles tool discovery, execution, Jira sync, KB store, graph visualization, and dashboard.

## Key Packages

| Package | Purpose | Key Classes |
|---------|---------|-------------|
| `config` | Server config, DB sync, validation | ConfigurationManager, ConfigDbSyncService, ConfigValidator |
| `di` | Koin DI module | AppModule |
| `discovery` | Tool discovery (semantic + keyword) | ToolDiscoveryService, KeywordSearchEngine |
| `execution` | Tool execution routing | ToolExecutionDispatcher |
| `protocol` | MCP protocol handling | McpServerFactory, McpToolRegistrar, JsonRpcHandler |
| `registry` | In-memory tool registry | ToolRegistry, ToolIndexer |
| `transport` | Transport layer (stdio, HTTP, SSE) | StdioTransport, HttpStreamableTransport |
| `sync` | Jira sync state management | SyncStateManager, TicketCacheRepository, TicketGraphRepository |
| `synctools` | MCP sync tools | SyncToolRegistrar, SyncToolHandler, GraphToolHandler |
| `scanner` | Jira project scanner | ProjectScanner, JqlBuilder, PageFetcher, BatchUpserter |
| `crawler` | Ticket content crawler | TicketCrawler, AdfParser, KBIngestor, GraphBuilder |
| `dashboard` | Sync dashboard (SSE) | SyncDashboardService, SyncEventBus, WebSocketHandler |
| `graph` | Graph visualization API | GraphService, GraphDataRepository, ViewModeStrategy |
| `kbstore` | KB storage | (encryption, repository) |
| `security` | RLS, role context | RlsDatabaseInitializer, RoleContextService |
| `masking` | PII masking | PiiMaskingEngine (strategy pattern) |
| `brmasking` | Business rule masking | BrMaskingService (crypto) |
| `attachment` | Attachment processing | AttachmentProcessor, TextExtractor |
| `ocr` | OCR for images | OcrService |
| `segmentation` | Content segmentation | ContentSegmentationService (LangChain4j) |
| `queue` | Async task queue | DualPriorityQueue, QueueWorker, CrashRecoveryService |
| `session` | HTTP sessions | SessionManager |
| `audit` | Audit trail | AuditService, AuditQueryService |
| `feedback` | User feedback | FeedbackService |
| `linking` | Entity linking | EntityLinkingService |
| `promotion` | Tool promotion | SmartToolPromoter |
| `management` | Tool management | ToolManagementService, ToolFilterService |
| `fileproxy` | File proxy | FileProxyService, WrapperToolGenerator |
| `usermanagement` | User CRUD | (service, repository, routes, tools) |
| `network` | Network utilities | NetworkService |
| `logging` | Agent logging | AgentLogService |

## Dependencies

- `orchestrator-core` (shared models)
- `orchestrator-client` (upstream connections, embedding, vector DB)
- Ktor Server (Netty)
- Ktor Client (CIO)
- MCP Kotlin SDK
- Koin
- PostgreSQL + HikariCP
- Apache PDFBox + POI
- LangChain4j

## Static Web Assets

| File | Purpose |
|------|---------|
| `static/graph-viewer.html` | 3D force-directed graph viewer (3d-force-graph library) |
| `static/sync-dashboard.html` | Real-time sync dashboard (SSE) |

## Graph API

- `GET /sync/graph/{projectKey}?view={hierarchy|dependency|team}` — Full project graph
- `GET /sync/graph/{projectKey}/{issueKey}?depth=2&view=hierarchy` — Subgraph (BFS)
- View modes: Hierarchy (type-colored), Dependency (status-colored), Team (assignee-colored)
