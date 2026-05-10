# Business Requirements Document (BRD)

## MCPOrchestration — MTO-38: KB Server — Tách Knowledge Base thành MCP Server Riêng Biệt

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-38 |
| Title | KB Server — Tách Knowledge Base thành MCP Server Riêng Biệt |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Parent Epic | MTO-24 (Knowledge Base Refinery) |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | BA Agent – Business Analyst | Create document |
| Peer Reviewer | SA Agent – Solution Architect | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | BA Agent | Initiate document — auto-generated from Jira ticket MTO-38 |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |

---

## 1. Introduction

### 1.1 Scope

This document defines the business requirements for **extracting the Knowledge Base (KB) functionality from orchestrator-server into a standalone MCP server** (`kb-server`). The refactoring creates an independent, deployable MCP server that exposes 13 KB tools via the Model Context Protocol, supporting both STDIO and HTTP transports.

The kb-server module encapsulates all KB-related concerns: storage, search, ingestion, PII masking, audit logging, graph visualization, network mapping, queue processing, and MCP protocol compliance. It communicates with the orchestrator-server as an upstream MCP server.

### 1.2 Out of Scope

- Changes to orchestrator-server's core tool routing logic
- UI/Frontend for KB management (future ticket)
- Distributed deployment across multiple nodes (single-instance for v1)
- LangChain4j AI segmentation integration (deferred to future tickets)
- OCR integration (deferred)
- Jira crawler/scanner (deferred)

### 1.3 Preliminary Requirement

- PostgreSQL database with pgvector extension available
- Kotlin 2.x + Gradle multi-project build configured
- orchestrator-core and orchestrator-client modules available as dependencies
- MCP SDK (io.modelcontextprotocol.kotlin.sdk) available
- Koin DI framework configured
- Ollama or compatible embedding provider running (for vector search)

---

## 2. Business Requirements

### 2.1 High Level Process Map

The KB Server separation addresses architectural concerns in the MCPOrchestration monolith:

1. **Separation of Concerns** — KB logic (storage, search, security, ingestion) separated from orchestrator logic (tool discovery, routing, upstream management)
2. **Independent Scalability** — KB server can scale independently (CPU-intensive for embedding, I/O-intensive for DB)
3. **Independent Deployment** — Deploy KB server without affecting orchestrator
4. **Cleaner Codebase** — Reduce orchestrator-server complexity

### 2.2 List of User Stories / Use Cases

| # | Story / Use Case | Priority | Source Ticket |
|---|------------------|----------|---------------|
| 1 | As a developer, I want kb-server to compile as a standalone Gradle module so that it can be built and deployed independently | MUST HAVE | MTO-38 |
| 2 | As a developer, I want kb-server to produce a shadowJar (kb-server-all.jar) so that it can be deployed as a single executable | MUST HAVE | MTO-38 |
| 3 | As a developer, I want 13 MCP tools registered and callable so that AI agents can interact with the knowledge base | MUST HAVE | MTO-38 |
| 4 | As a developer, I want kb-server to support STDIO and HTTP transport so that it works in both development and production | MUST HAVE | MTO-38 |
| 5 | As a developer, I want workspace root resolved via MCP roots/list protocol so that kb-server knows the project context | MUST HAVE | MTO-38 |
| 6 | As a developer, I want a graph viewer UI served from kb-server (port 9181) so that knowledge relationships can be visualized | SHOULD HAVE | MTO-38 |
| 7 | As a developer, I want orchestrator-server to remain unaffected (still compiles) so that existing functionality is preserved | MUST HAVE | MTO-38 |
| 8 | As a developer, I want 37+ unit tests to pass so that code quality is verified | MUST HAVE | MTO-38 |

---

### 2.3 Details of User Stories

---

#### Business Flow

![Business Flow](diagrams/business-flow.png)

**Step 1:** AI Agent (Claude/Kiro) connects to orchestrator-server via MCP protocol (STDIO)

**Step 2:** Orchestrator discovers kb-server as upstream MCP server (configured in application.yml)

**Step 3:** Agent calls `find_tools("knowledge base")` → orchestrator returns kb_* tool definitions

**Step 4:** Agent calls `execute_dynamic_tool(tool_name="kb_search", arguments={...})`

**Step 5:** Orchestrator routes request to kb-server via MCP connection (STDIO subprocess or HTTP)

**Step 6:** kb-server processes request (search, ingest, audit, etc.) using internal services

**Step 7:** kb-server returns MCP response → orchestrator forwards to agent

**Step 8:** For graph visualization, kb-server serves REST API + 3D UI on port 9181

---

#### STORY 1: Standalone Gradle Module Compilation

> As a developer, I want kb-server to compile as a standalone Gradle module so that it can be built and deployed independently.

**Requirement Details:**

1. kb-server is a Gradle subproject in settings.gradle.kts: `include("kb-server")`
2. Has its own build.gradle.kts with application plugin and shadow plugin
3. Depends on orchestrator-core and orchestrator-client (shared modules)
4. Main class: `com.orchestrator.mcp.kb.KbMainKt`
5. Compiles with `./gradlew :kb-server:compileKotlin` without errors

**Acceptance Criteria:**

1. GIVEN the project is configured WHEN `./gradlew :kb-server:compileKotlin` is run THEN compilation succeeds with 0 errors
2. GIVEN kb-server depends on orchestrator-core WHEN shared models are used THEN they resolve correctly
3. GIVEN kb-server depends on orchestrator-client WHEN MCP SDK types are used THEN they resolve correctly

---

#### STORY 2: ShadowJar Build

> As a developer, I want kb-server to produce a shadowJar (kb-server-all.jar) so that it can be deployed as a single executable.

**Requirement Details:**

1. ShadowJar task configured with `archiveBaseName = "kb-server"`, `archiveClassifier = "all"`
2. All dependencies bundled into single JAR
3. Service files merged correctly (META-INF/services)
4. Executable via `java -jar kb-server-all.jar`
5. Supports CLI arguments: `--config=<path>` and `--transport=<stdio|http>`

**Acceptance Criteria:**

1. GIVEN the project is configured WHEN `./gradlew :kb-server:shadowJar` is run THEN kb-server-all.jar is produced
2. GIVEN kb-server-all.jar exists WHEN `java -jar kb-server-all.jar --transport=stdio` is run THEN the server starts in STDIO mode
3. GIVEN kb-server-all.jar exists WHEN `java -jar kb-server-all.jar --transport=http` is run THEN the server starts on port 9181

---

#### STORY 3: 13 MCP Tools Registration

> As a developer, I want 13 MCP tools registered and callable so that AI agents can interact with the knowledge base.

**Requirement Details:**

The following 13 tools must be registered via KbToolRegistrar:

| # | Tool Name | Category | Description |
|---|-----------|----------|-------------|
| 1 | kb_search | Read | Semantic search KB entries by query |
| 2 | kb_read | Read | Read specific KB entry by issue_key |
| 3 | kb_ingest | Write | Ingest content into KB |
| 4 | kb_delete | Write | Delete KB entry by issue_key |
| 5 | kb_link | Read | Find semantically similar entries |
| 6 | kb_feedback | Write | Submit feedback on KB entry |
| 7 | kb_audit_query | Read | Query audit logs |
| 8 | kb_sync_trigger | Write | Trigger Jira project sync |
| 9 | kb_sync_status | Read | Check sync progress |
| 10 | kb_unmask_pii | Read (restricted) | Unmask PII for authorized users |
| 11 | kb_unmask_br | Read (restricted) | Unmask business rules |
| 12 | kb_graph | Read | Get knowledge graph data |
| 13 | kb_network | Read | Get feature network (BFS N-hop) |

**Acceptance Criteria:**

1. GIVEN kb-server starts WHEN tools/list is called THEN all 13 tools are returned with correct schemas
2. GIVEN kb_search is called with valid query WHEN KB has matching entries THEN results are returned ranked by relevance
3. GIVEN kb_ingest is called with title+content WHEN input is valid THEN entry is stored and indexed
4. GIVEN kb_audit_query is called WHEN audit events exist THEN filtered results are returned

---

#### STORY 4: STDIO and HTTP Transport Support

> As a developer, I want kb-server to support STDIO and HTTP transport so that it works in both development and production.

**Requirement Details:**

1. STDIO transport: reads JSON-RPC from stdin, writes to stdout (development mode)
2. HTTP transport: Ktor-based HTTP server on configurable port (default 9181)
3. Transport selected via `--transport=` CLI argument or `kb.server.transport` in config
4. Both transports implement full MCP protocol (initialize, tools/list, tools/call)

**Acceptance Criteria:**

1. GIVEN `--transport=stdio` WHEN MCP messages are sent via stdin THEN responses are written to stdout
2. GIVEN `--transport=http` WHEN HTTP POST to /mcp endpoint THEN MCP responses are returned
3. GIVEN no transport argument WHEN config has `transport: stdio` THEN STDIO mode is used

---

#### STORY 5: MCP roots/list Protocol Support

> As a developer, I want workspace root resolved via MCP roots/list protocol so that kb-server knows the project context.

**Requirement Details:**

1. kb-server implements `roots/list` capability in MCP protocol
2. When orchestrator connects, it provides workspace roots via roots/list
3. kb-server uses workspace root to resolve relative file paths
4. WorkspaceContext singleton stores resolved root path
5. Fallback: use current working directory if roots/list not available

**Acceptance Criteria:**

1. GIVEN orchestrator provides roots/list WHEN kb-server initializes THEN workspace root is stored in WorkspaceContext
2. GIVEN workspace root is set WHEN file operations reference relative paths THEN they resolve correctly
3. GIVEN roots/list is not available WHEN kb-server starts THEN CWD is used as fallback

---

#### STORY 6: Graph Viewer UI

> As a developer, I want a graph viewer UI served from kb-server (port 9181) so that knowledge relationships can be visualized.

**Requirement Details:**

1. REST API endpoints for graph data (GET /api/graph/*)
2. 3D visualization UI served as static files
3. Graph shows KB entries as nodes, relationships as edges
4. Network service provides BFS N-hop traversal
5. Accessible at http://localhost:9181/graph

**Acceptance Criteria:**

1. GIVEN kb-server runs in HTTP mode WHEN GET /api/graph/data is called THEN graph JSON is returned
2. GIVEN kb-server runs in HTTP mode WHEN browser navigates to /graph THEN 3D UI is rendered
3. GIVEN graph has entries with links WHEN N-hop query is made THEN connected entries are returned

---

#### STORY 7: Orchestrator Unaffected

> As a developer, I want orchestrator-server to remain unaffected (still compiles) so that existing functionality is preserved.

**Requirement Details:**

1. orchestrator-server compiles without errors after kb-server extraction
2. No breaking changes to orchestrator-server's public API
3. orchestrator-server can optionally connect to kb-server as upstream
4. Existing orchestrator tests continue to pass

**Acceptance Criteria:**

1. GIVEN kb-server module is added WHEN `./gradlew :orchestrator-server:compileKotlin` is run THEN compilation succeeds
2. GIVEN orchestrator-server has existing tests WHEN `./gradlew :orchestrator-server:test` is run THEN all tests pass
3. GIVEN kb-server is configured as upstream WHEN orchestrator starts THEN kb tools are discoverable

---

#### STORY 8: Unit Tests Pass

> As a developer, I want 37+ unit tests to pass so that code quality is verified.

**Requirement Details:**

1. Unit tests cover: queue system, PII masking, config loading, tool handlers, audit service
2. Tests use MockK for mocking, Kotest for assertions
3. Tests run with `./gradlew :kb-server:test`
4. Minimum 37 tests passing

**Acceptance Criteria:**

1. GIVEN test suite exists WHEN `./gradlew :kb-server:test` is run THEN 37+ tests pass
2. GIVEN queue tests exist WHEN DualPriorityQueue is tested THEN HPQ priority over NPQ is verified
3. GIVEN masking tests exist WHEN PII patterns are tested THEN all strategies detect correctly

---

## 3. Dependencies

| Dependency | Type | Related Ticket | Description |
|------------|------|----------------|-------------|
| PostgreSQL + pgvector | Infrastructure | N/A | Database with vector search extension |
| orchestrator-core | System | N/A | Shared models, config utilities |
| orchestrator-client | System | N/A | MCP SDK types, embedding client |
| MCP SDK | System | N/A | Model Context Protocol implementation |
| Kotlin Coroutines | System | N/A | Async processing for queue workers |
| Koin DI | System | N/A | Dependency injection framework |
| Ollama | Infrastructure | N/A | Embedding provider for vector search |
| MTO-25 | External | MTO-25 | Dual-Priority Queue design (implemented in kb-server) |
| MTO-24 | External | MTO-24 | Parent epic — KB Refinery architecture |

---

## 4. Stakeholders

| Role | Name / Team | Responsibility | Source |
|------|-------------|----------------|--------|
| Product Owner | Duc Nguyen | Define requirements, accept deliverables | Jira reporter |
| Solution Architect | SA Agent | Technical design and review | Team |
| Developer | DEV Agent | Implementation | Team |
| QA Engineer | QA Agent | Testing and verification | Team |

---

## 5. Risks and Assumptions

### 5.1 Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Breaking orchestrator-server during extraction | High | Low | Compile both modules in CI, run existing tests |
| Shared dependency version conflicts | Medium | Medium | Use Gradle version catalog (libs.versions.toml) |
| MCP protocol incompatibility between versions | High | Low | Pin MCP SDK version, integration tests |
| Database schema conflicts (shared DB) | Medium | Low | Use separate `kb` schema, Flyway migrations |
| Performance degradation from inter-process communication | Medium | Medium | STDIO for dev (low latency), HTTP for prod (scalable) |

### 5.2 Assumptions

- Single JVM instance for kb-server (no distributed coordination)
- Shared PostgreSQL database with schema separation (kb schema)
- Embedding provider (Ollama) available on localhost:11434
- orchestrator-server and kb-server run on same machine in development
- MCP SDK supports both STDIO and HTTP transports

---

## 6. Non-Functional Requirements

| Category | Requirement | Details |
|----------|-------------|---------|
| Performance | Tool response latency < 500ms | For kb_search, kb_read (excluding LLM calls) |
| Performance | Ingestion throughput ≥ 10 entries/minute | For kb_ingest with PII masking |
| Reliability | Zero data loss on crash | Queue crash recovery restores in-progress tasks |
| Reliability | Graceful shutdown | Drain queue, close DB connections |
| Scalability | Support 1000+ KB entries | Vector search with HNSW index |
| Security | PII data encrypted at rest | AES-256-GCM encryption |
| Security | Rate limiting on unmask operations | Configurable per-hour limits |
| Observability | Structured logging (Logback) | All tool calls logged with correlation ID |
| Observability | Audit trail for sensitive operations | PII unmask, BR unmask logged |
| Maintainability | ≤ 200 lines per file | Kotlin code standards compliance |
| Testability | ≥ 37 unit tests passing | MockK + Kotest framework |

---

## 7. Related Tickets

| Ticket Key | Summary | Status | Type | Relationship |
|------------|---------|--------|------|--------------|
| MTO-38 | KB Server — Tách Knowledge Base thành MCP Server Riêng Biệt | To Do | Story | Main ticket |
| MTO-25 | KB Refinery — Dual-Priority Queue (Kotlin Channels) | To Do | Story | Queue system design |
| MTO-24 | Knowledge Base Refinery - AI-Powered Data Extraction, Masking & Access Control | To Do | Epic | Parent epic |

---

## 8. Appendix

### Glossary

| Term | Definition |
|------|------------|
| MCP | Model Context Protocol — standard for AI agent ↔ tool communication |
| KB | Knowledge Base — structured storage for project knowledge |
| STDIO | Standard Input/Output — transport mode for local development |
| ShadowJar | Gradle plugin that bundles all dependencies into single JAR |
| pgvector | PostgreSQL extension for vector similarity search |
| HNSW | Hierarchical Navigable Small World — vector index algorithm |
| HPQ | High-Priority Queue — for user-initiated tasks |
| NPQ | Normal-Priority Queue — for batch/system tasks |
| PII | Personally Identifiable Information |
| BR | Business Rules — sensitive business logic |
| roots/list | MCP protocol method for workspace root discovery |

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Business Flow | [business-flow.png](diagrams/business-flow.png) | [business-flow.drawio](diagrams/business-flow.drawio) |
| 2 | Use Case Diagram | [use-case.png](diagrams/use-case.png) | [use-case.drawio](diagrams/use-case.drawio) |
