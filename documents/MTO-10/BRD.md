# Business Requirements Document (BRD)

## MCP Tool Orchestration — MTO-10: Upgrade MCP Orchestrator: Local Embedding, pgvector, Tool Management & Auto-Approve

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-10 |
| Title | Upgrade MCP Orchestrator: Local Embedding, pgvector, Tool Management & Auto-Approve |
| Author | BA Agent |
| Version | 1.1 |
| Date | 2026-05-03 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-10.docx |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | BA Agent – Business Analyst | Create document |
| Peer Reviewer | Duc Nguyen – Project Lead | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-03 | BA Agent | Initial draft — auto-generated from Jira ticket MTO-10. Full rebuild with embedded draw.io diagrams. |
| 1.2 | 2026-05-04 | BA Agent | Added Dual Operational Modes requirement (Standalone HTTP/SSE vs Local Bridge Stdio). |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| Duc Nguyen | ☐ I agree and confirm all criteria on this BRD as expected requirements |
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |

---

## 1. Introduction

### 1.1 Scope

This BRD covers the upgrade of the MCP Orchestrator Server to address four key areas:

1. **Local Embedding Service** — Replace the paid OpenAI embedding dependency with local alternatives (Ollama, LMStudio) to eliminate API costs and enable offline operation.
2. **PostgreSQL + pgvector** — Replace Qdrant/FAISS vector storage with PostgreSQL pgvector extension, leveraging the existing PostgreSQL instance for production-ready vector search with hybrid (vector + keyword) capabilities.
3. **Runtime Tool Management** — Add new MCP tools (`toggle_tool`, `reset_tools`) to enable/disable tools at runtime without server restart, with session-scoped state persisted in PostgreSQL.
4. **Auto-Approve Management** — Add `manage_auto_approve` MCP tool to manage auto-approve settings at runtime with persistent config file updates to `mcp-servers.json`.
5. **Config-DB Sync** — Synchronize `mcp-servers.json` configuration to PostgreSQL on startup for multi-instance consistency.
6. **Tool Filtering** — Support allowlist/blocklist tool filtering per server in configuration.

The system is a Kotlin/Ktor MCP Orchestration Server (Kotlin 2.3.20, Ktor 3.4.0, Koin 4.1.1) that acts as a proxy between AI clients and upstream MCP tool servers, providing semantic tool discovery and dynamic tool execution.

### 1.2 Out of Scope

- UI/frontend for tool management (CLI/MCP tool interface only)
- Migration of existing Qdrant data (clean re-index approach)
- Changes to upstream MCP server protocols
- Authentication/authorization for MCP tool calls
- LLM inference capabilities (only embedding is in scope)

### 1.3 Preliminary Requirements

- PostgreSQL 16+ instance with pgvector extension installed (`postgresql://postgres:postgres@localhost:5432/jira_assistant`)
- Ollama or LMStudio running locally for embedding generation
- Existing MCP Orchestrator codebase (Kotlin 2.3.20, Ktor 3.4.0, Koin 4.1.1)
- Java 21 runtime environment

---

## 2. Business Requirements

### 2.1 High Level Process Map

The upgraded MCP Orchestrator operates as follows:

1. On startup, the orchestrator reads `mcp-servers.json` and syncs server configuration to PostgreSQL.
2. It connects to upstream MCP servers and discovers available tools via `tools/list`.
3. Tools are filtered per server configuration (allowlist/blocklist), then embedded using the local embedding service (Ollama or LMStudio) and stored in PostgreSQL pgvector `tool_embeddings` table.
4. AI clients call `find_tools` to semantically search for tools — disabled tools are excluded from results.
5. AI clients call `execute_dynamic_tool` to invoke a tool — disabled tools are blocked with an error.
6. Operators can call `toggle_tool` to enable/disable tools at runtime (session-scoped, DB-persisted).
7. Operators can call `reset_tools` to restore all tools to default enabled state.
8. Operators can call `manage_auto_approve` to update auto-approve settings (persisted to both DB and `mcp-servers.json`).

![Use Case Diagram](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/use-case.png)

*[Edit in draw.io](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/use-case.drawio)*

![Business Flow](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/business-flow.png)

*[Edit in draw.io](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/business-flow.drawio)*

### 2.2 List of User Stories / Use Cases

| # | Story / Use Case | Priority | Source Ticket |
|---|------------------|----------|---------------|
| 1 | As a developer, I want to use local embedding services (Ollama/LMStudio) so that I can generate tool embeddings without paying for OpenAI API | MUST HAVE | MTO-10 (FR-1) |
| 2 | As a developer, I want to store tool embeddings in PostgreSQL pgvector so that I have a production-ready vector database without extra infrastructure | MUST HAVE | MTO-10 (FR-2) |
| 3 | As an operator, I want to enable/disable tools at runtime so that I can control tool availability without restarting the server | MUST HAVE | MTO-10 (FR-3) |
| 4 | As an operator, I want to reset all tool toggle states so that I can quickly restore default behavior | SHOULD HAVE | MTO-10 (FR-4) |
| 5 | As an operator, I want to manage auto-approve settings at runtime so that I can control which tools are auto-approved without editing config files manually | SHOULD HAVE | MTO-10 (FR-5) |
| 6 | As a system, I want to sync config to DB on startup so that multiple orchestrator instances share consistent state | MUST HAVE | MTO-10 (FR-6) |
| 7 | As a developer, I want to filter tools per server via config so that I can control which tools are indexed and exposed | SHOULD HAVE | MTO-10 (FR-7) |
| 8 | As a user/system, I want the orchestrator to support both Standalone (HTTP/SSE) and Local Bridge (Stdio) modes so that it can be deployed as a web service or integrated directly into IDEs | MUST HAVE | MTO-10 |

---

### 2.3 Details of User Stories

---

#### Business Flow

**Step 1:** Orchestrator starts and reads `mcp-servers.json` configuration file.

**Step 2:** Config-DB sync runs — inserts new servers, updates changed servers, marks removed servers as inactive in PostgreSQL.

**Step 3:** Orchestrator connects to each upstream MCP server and discovers available tools via `tools/list`.

**Step 4:** Tool filtering is applied per server — tools matching blocklist are excluded, or only allowlist tools are kept.

**Step 5:** Remaining tools are embedded using the configured local embedding provider (Ollama or LMStudio) and stored in PostgreSQL pgvector `tool_embeddings` table.

**Step 6:** AI client sends `find_tools` request with a natural language query.

**Step 7:** Orchestrator performs hybrid search (vector similarity + keyword tsvector) on pgvector, excluding disabled tools for the current session.

**Step 8:** AI client sends `execute_dynamic_tool` request — orchestrator checks if tool is enabled, then routes to upstream server.

**Step 9:** Operator may call `toggle_tool` to disable/enable specific tools or entire servers — state saved to DB per session_id.

**Step 10:** Operator may call `manage_auto_approve` to update auto-approve list — written immediately to both DB and `mcp-servers.json`.

> **Note:** Toggle states are session-scoped (per orchestrator instance). Auto-approve changes are persistent across restarts.

![Sequence — Tool Discovery & Execution](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-discovery.png)

*[Edit in draw.io](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-discovery.drawio)*

![Sequence — Tool Management (Toggle / Reset / Auto-Approve)](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-mgmt.png)

*[Edit in draw.io](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-mgmt.drawio)*

---

#### STORY 1: Local Embedding Service (Ollama/LMStudio)

> As a developer, I want to use local embedding services (Ollama/LMStudio) so that I can generate tool embeddings without paying for OpenAI API.

**Requirement Details:**

1. Implement `OllamaEmbeddingService` that calls the Ollama local API at `http://localhost:11434/api/embeddings` (Source: MTO-10 FR-1)
2. Support LMStudio via OpenAI-compatible endpoint at `http://localhost:1234/v1/embeddings` (Source: MTO-10 FR-1)
3. Make embedding provider configurable in `EmbeddingConfig`: `ollama` / `lmstudio` / `openai` (Source: MTO-10 FR-1)
4. Preserve the existing `EmbeddingService` interface — new implementations must conform to it (Source: MTO-10 FR-1)
5. Embedding dimensions must be configurable with a default of 768 (Source: MTO-10 FR-1)

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| provider | String (enum) | Yes | Embedding provider selection | `ollama`, `lmstudio`, `openai` |
| model | String | Yes | Model name for embedding | `nomic-embed-text`, `text-embedding-3-small` |
| api_url | String | No | Override base URL for provider | `http://localhost:11434` |
| api_key | String | No | API key (required for OpenAI only) | `sk-...` |
| dimensions | Int | No | Embedding vector dimensions (default: 768) | `768` |

**Acceptance Criteria:**

1. When `provider=ollama`, the system calls `http://localhost:11434/api/embeddings` and returns a vector of configured dimensions
2. When `provider=lmstudio`, the system calls `http://localhost:1234/v1/embeddings` using OpenAI-compatible format
3. When `provider=openai`, the system behaves as before (backward compatible)
4. If the local embedding service is unavailable, the system logs an error and returns an appropriate `EmbeddingServiceException`
5. The `EmbeddingService` interface remains unchanged — existing code using the interface requires no modification

**Validation Rules:**

- `provider` must be one of: `ollama`, `lmstudio`, `openai`
- `dimensions` must be a positive integer (1–4096)
- `api_key` is required when `provider=openai`, optional otherwise
- `api_url` must be a valid HTTP/HTTPS URL if provided

**Error Handling:**

- Local service unreachable: Log error, throw `EmbeddingServiceException` with connection details
- Invalid response format: Log raw response, throw `EmbeddingServiceException`
- Dimension mismatch: Log warning, truncate or pad vector to configured dimensions

---

#### STORY 2: PostgreSQL + pgvector Vector Storage

> As a developer, I want to store tool embeddings in PostgreSQL pgvector so that I have a production-ready vector database without extra infrastructure.

**Requirement Details:**

1. Implement `PgVectorDbClient` conforming to the existing `VectorDbClient` interface (Source: MTO-10 FR-2)
2. Create `tool_embeddings` table with HNSW index for cosine similarity search (Source: MTO-10 FR-2)
3. Reuse the existing PostgreSQL instance at `postgresql://postgres:postgres@localhost:5432/jira_assistant` (Source: MTO-10 FR-2)
4. Support hybrid search combining vector similarity with keyword search using tsvector (Source: MTO-10 FR-2)

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| id | UUID | Yes | Primary key | `550e8400-e29b-41d4-a716-446655440000` |
| server_name | String | Yes | Upstream MCP server name | `atlassian` |
| tool_name | String | Yes | Tool identifier | `jira_get_issue` |
| description | String | Yes | Tool description for search | `Get a Jira issue by key` |
| embedding | vector(768) | Yes | Embedding vector | `[0.1, 0.2, ...]` |
| payload | JSONB | No | Additional tool metadata | `{"category": "jira"}` |
| input_schema | JSONB | No | Tool input JSON Schema | `{"type": "object", ...}` |

**Acceptance Criteria:**

1. `PgVectorDbClient` implements all methods of `VectorDbClient` interface: upsert, search, delete, count
2. HNSW index is created on the `embedding` column for cosine similarity (`vector_cosine_ops`)
3. Hybrid search returns results ranked by combined vector similarity and keyword relevance scores
4. The system creates the `tool_embeddings` table and pgvector extension automatically on first run if they don't exist
5. Existing Qdrant and FAISS implementations remain available as alternatives (backward compatible)

**Validation Rules:**

- `server_name` + `tool_name` combination must be unique (composite unique constraint)
- `embedding` vector dimension must match the configured dimensions (768 by default)
- `payload` and `input_schema` must be valid JSON if provided

**Error Handling:**

- PostgreSQL connection failure: Log error, throw `VectorDbUnavailableException`
- pgvector extension not installed: Log clear instructions for installation, throw `VectorDbUnavailableException`
- Dimension mismatch on insert: Reject with `InvalidParamsException`

---

#### STORY 3: Toggle Tool (Enable/Disable at Runtime)

> As an operator, I want to enable/disable tools at runtime so that I can control tool availability without restarting the server.

**Requirement Details:**

1. New MCP tool: `toggle_tool` (Source: MTO-10 FR-3)
2. Parameters: `tool_name` (String, optional), `server_name` (String, optional), `enabled` (Boolean, required) (Source: MTO-10 FR-3)
3. At least one of `tool_name` or `server_name` must be provided (Source: MTO-10 FR-3)
4. Disabling a tool hides it from `find_tools` results and blocks `execute_dynamic_tool` (Source: MTO-10 FR-3)
5. Toggle state is stored in PostgreSQL keyed by `session_id` for multi-instance safety (Source: MTO-10 FR-3)
6. Each orchestrator instance has its own `session_id` — toggle changes do not affect other instances (Source: MTO-10 FR-3)
7. Toggle state is written to DB immediately but NOT to config file (session-only) (Source: MTO-10)

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| tool_name | String | No* | Specific tool to toggle | `jira_get_issue` |
| server_name | String | No* | Toggle all tools for a server | `atlassian` |
| enabled | Boolean | Yes | Enable (true) or disable (false) | `false` |

*At least one of `tool_name` or `server_name` must be provided.

**Acceptance Criteria:**

1. Calling `toggle_tool(tool_name="jira_get_issue", enabled=false)` immediately hides the tool from `find_tools` results
2. Calling `execute_dynamic_tool` for a disabled tool returns an error indicating the tool is disabled
3. Calling `toggle_tool(server_name="atlassian", enabled=false)` disables all tools from that server
4. Toggle state persists in PostgreSQL — survives in-process state loss but is scoped to the session
5. Two orchestrator instances can have different toggle states for the same tool

**Validation Rules:**

- At least one of `tool_name` or `server_name` must be non-null
- If both are provided, `tool_name` takes precedence (toggle specific tool only)
- `tool_name` must reference an existing registered tool
- `server_name` must reference an existing configured server

**Error Handling:**

- Neither `tool_name` nor `server_name` provided: Return `InvalidParamsException` with message "At least one of tool_name or server_name is required"
- Tool/server not found: Return `ToolNotFoundException` with the name that was not found
- DB write failure: Log error, apply in-memory toggle, return success with warning

---

#### STORY 4: Reset Tools

> As an operator, I want to reset all tool toggle states so that I can quickly restore default behavior.

**Requirement Details:**

1. New MCP tool: `reset_tools` (Source: MTO-10 FR-4)
2. Parameters: `server_name` (String, optional), `reindex` (Boolean, default=true) (Source: MTO-10 FR-4)
3. Resets all toggle states to default (all tools enabled) (Source: MTO-10 FR-4)
4. If `server_name` is provided, only reset that server's tools (Source: MTO-10 FR-4)
5. If `reindex=true`, re-index tools after reset (Source: MTO-10 FR-4)
6. State written to DB immediately, NOT to config file (Source: MTO-10)

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| server_name | String | No | Reset only this server's tools | `atlassian` |
| reindex | Boolean | No | Re-index tools after reset (default: true) | `true` |

**Acceptance Criteria:**

1. Calling `reset_tools()` with no parameters enables all tools across all servers for the current session
2. Calling `reset_tools(server_name="atlassian")` only resets tools for the `atlassian` server
3. When `reindex=true`, tools are re-embedded and re-indexed in pgvector after reset
4. When `reindex=false`, only toggle states are cleared without re-indexing
5. After reset, `find_tools` returns all tools (no filters applied)

**Validation Rules:**

- `server_name`, if provided, must reference an existing configured server
- `reindex` defaults to `true` if not specified

**Error Handling:**

- Server not found: Return `ToolNotFoundException` with server name
- Re-index failure: Log error, return success with warning that re-indexing failed

---

#### STORY 5: Auto-Approve Management

> As an operator, I want to manage auto-approve settings at runtime so that I can control which tools are auto-approved without editing config files manually.

**Requirement Details:**

1. New MCP tool: `manage_auto_approve` (Source: MTO-10 FR-5)
2. Parameters: `tool_name` (String, optional), `server_name` (String, optional), `auto_approve` (Boolean, required) (Source: MTO-10 FR-5)
3. **Writes immediately to `mcp-servers.json`** — persistent across restarts (Source: MTO-10 FR-5)
4. Updates the `autoApprove` array in the config file for the target server (Source: MTO-10 FR-5)
5. Also updates DB simultaneously for runtime consistency (Source: MTO-10 FR-5)

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| tool_name | String | No* | Specific tool to set auto-approve | `jira_get_issue` |
| server_name | String | No* | Set auto-approve for all tools in server | `atlassian` |
| auto_approve | Boolean | Yes | Enable (true) or disable (false) auto-approve | `true` |

*At least one of `tool_name` or `server_name` must be provided.

**Acceptance Criteria:**

1. Calling `manage_auto_approve(tool_name="jira_get_issue", auto_approve=true)` adds the tool to the `autoApprove` array in `mcp-servers.json`
2. Calling `manage_auto_approve(tool_name="jira_get_issue", auto_approve=false)` removes the tool from the `autoApprove` array
3. Config file changes are atomic — no corruption on crash
4. DB is updated simultaneously with the config file
5. Changes take effect immediately without server restart

**Validation Rules:**

- At least one of `tool_name` or `server_name` must be non-null
- `tool_name` must reference an existing registered tool
- `server_name` must reference an existing configured server

**Error Handling:**

- Config file write failure: Log error, update DB only, return warning
- Config file locked by another process: Retry with backoff, then fail with clear error message
- DB write failure: Log error, config file still updated, return warning

---

#### STORY 6: Config-DB Sync on Startup

> As a system, I want to sync config to DB on startup so that multiple orchestrator instances share consistent state.

**Requirement Details:**

1. On every startup, read `mcp-servers.json` and sync to PostgreSQL (Source: MTO-10 FR-6)
2. New servers in config → INSERT into DB (Source: MTO-10 FR-6)
3. Servers removed from config → DELETE or mark inactive in DB (Source: MTO-10 FR-6)
4. Servers with changed settings (disabled, toolFilter, autoApprove) → UPDATE in DB (Source: MTO-10 FR-6)
5. Config file is the **source of truth**, DB is the runtime mirror (Source: MTO-10 FR-6)

**Acceptance Criteria:**

1. After startup, DB contains exactly the servers defined in `mcp-servers.json`
2. Servers removed from config are marked inactive (not hard-deleted) in DB
3. Changed server settings (disabled, toolFilter, autoApprove) are reflected in DB
4. Sync completes before tool indexing begins
5. Sync is idempotent — running multiple times produces the same result

**Validation Rules:**

- Config file must be valid JSON
- Server names must be unique within the config file
- DB schema must exist (auto-created if missing)

**Error Handling:**

- Config file not found: Log error, start with empty server list
- Config file parse error: Log error with line number, abort startup
- DB connection failure during sync: Log error, continue with config-only mode (degraded)

---

#### STORY 7: Tool Filtering from Config

> As a developer, I want to filter tools per server via config so that I can control which tools are indexed and exposed.

**Requirement Details:**

1. Support `toolFilter` field in `mcp-servers.json` per server (Source: MTO-10 FR-7)
2. `toolFilter` has two fields: `mode` ("allowlist" or "blocklist") and `tools` (array of tool names) (Source: MTO-10 FR-7)
3. Filter is applied at `ToolIndexer.indexTools()` before embedding and registration (Source: MTO-10 FR-7)
4. Filtered tools are not embedded, not registered, and not stored in vector DB (Source: MTO-10 FR-7)

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| mode | String (enum) | Yes | Filter mode | `allowlist` or `blocklist` |
| tools | String[] | Yes | List of tool names to filter | `["jira_delete_issue", "jira_create_issue"]` |

**Acceptance Criteria:**

1. With `mode=blocklist`, tools in the list are excluded — all others are indexed
2. With `mode=allowlist`, only tools in the list are indexed — all others are excluded
3. If `toolFilter` is not present for a server, all tools are exposed (backward compatible)
4. Filtered tools do not appear in `find_tools` results and cannot be executed via `execute_dynamic_tool`
5. Filter changes require server restart (config-based, not runtime)

**Validation Rules:**

- `mode` must be either `allowlist` or `blocklist`
- `tools` must be a non-empty array of strings
- Tool names in the filter are case-sensitive

**Error Handling:**

- Invalid mode value: Log warning, ignore filter (expose all tools)
- Empty tools array: Log warning, ignore filter (expose all tools)

---
 
#### STORY 8: Dual Operational Modes (Standalone vs Local Bridge)
 
> As a user/system, I want the orchestrator to support both Standalone (HTTP/SSE) and Local Bridge (Stdio) modes so that it can be deployed as a web service or integrated directly into IDEs.
 
**Requirement Details:**
 
1. **Standalone Mode**: The system must be capable of running as a persistent HTTP service using the Server-Sent Events (SSE) transport. Configuration for this mode is primarily managed via `application.yml`.
2. **Local Bridge Mode**: The system must be capable of running as a standard I/O (stdio) sub-process, allowing direct integration with MCP-compatible clients like Cursor and Claude Desktop. Configuration is provided by the host client (IDE).
3. **Unified Upstream Management**: Regardless of the operational mode, upstream server definitions must remain centralized in `mcp-servers.json` (or a specified config file).
 
**Acceptance Criteria:**
 
1. The application can be started in SSE mode (HTTP server) by setting `orchestrator.server.protocol: sse` in `application.yml`.
2. The application can be started in stdio mode (default or CLI flag) to act as a bridge for IDEs.
3. Upstream tool discovery and execution logic are identical across both modes.
4. Environment variables are resolved correctly in both modes, allowing for secure management of upstream secrets.
 
---
 
## 3. Dependencies

| Dependency | Type | Related Ticket | Description |
|------------|------|----------------|-------------|
| PostgreSQL 16+ with pgvector | Infrastructure | MTO-10 | Existing PostgreSQL instance must have pgvector extension installed |
| Ollama / LMStudio | Infrastructure | MTO-10 | Local embedding service must be running for tool indexing |
| Existing EmbeddingService interface | System | MTO-10 | New implementations must conform to existing interface contract |
| Existing VectorDbClient interface | System | MTO-10 | PgVectorDbClient must implement existing interface contract |
| mcp-servers.json | Configuration | MTO-10 | Config file must be writable for auto-approve management |
| Java 21 Runtime | Infrastructure | MTO-10 | Required JVM version for Kotlin 2.3.20 |

---

## 4. Stakeholders

| Role | Name / Team | Responsibility | Source |
|------|-------------|----------------|--------|
| Project Lead / Reporter | Duc Nguyen | Requirements definition, review, approval | MTO-10 Reporter/Creator |
| Development Team | MCP Orchestration Team | Implementation of all functional requirements | MTO-10 Project |
| Operations | DevOps Team | Infrastructure setup (PostgreSQL, Ollama) | MTO-10 Dependencies |

---

## 5. Risks and Assumptions

### 5.1 Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Local embedding quality differs from OpenAI | Medium | Medium | Benchmark embedding quality with test queries before switching |
| pgvector HNSW index performance at scale | Low | Low | Monitor query latency; tune HNSW parameters (m, ef_construction) |
| Config file corruption during atomic write | High | Low | Use write-to-temp-then-rename pattern for atomic file writes |
| Session isolation complexity with multiple instances | Medium | Medium | Use unique session_id per instance; test with concurrent instances |
| Ollama/LMStudio service availability | Medium | Medium | Implement health check and fallback to OpenAI if configured |

### 5.2 Assumptions

- PostgreSQL instance at `localhost:5432/jira_assistant` is available and has pgvector extension installed
- Ollama or LMStudio is installed and running on the developer's machine
- The existing `EmbeddingService` and `VectorDbClient` interfaces are stable and will not change
- `mcp-servers.json` file has write permissions for the orchestrator process
- Each orchestrator instance generates a unique session_id on startup

---

## 6. Non-Functional Requirements

| Category | Requirement | Details |
|----------|-------------|---------|
| Multi-instance Safety | Multiple orchestrator instances must run concurrently without conflict | Toggle states are session-scoped; DB operations use proper isolation |
| Backward Compatibility | No toolFilter = expose all tools; existing OpenAI provider still works | Existing configurations must work without modification |
| Session Isolation | Toggle state per session, not affecting other instances | Each instance has unique session_id in PostgreSQL |
| Config File Integrity | Atomic file writes for `mcp-servers.json` | Write-to-temp-then-rename pattern to prevent corruption on crash |
| Performance | Hybrid search (vector + keyword) must return results within 200ms | HNSW index + tsvector index on PostgreSQL |
| Availability | Graceful degradation if embedding service is unavailable | Keyword-only search fallback when embeddings fail |

---

## 7. Related Tickets

| Ticket Key | Summary | Status | Type | Relationship |
|------------|---------|--------|------|--------------|
| MTO-10 | Upgrade MCP Orchestrator: Local Embedding, pgvector, Tool Management & Auto-Approve | To Do | Story | Main ticket |

---

## 8. Appendix

### Persistence Rules Summary

| Action | Write to DB (session) | Write to Config File |
|--------|----------------------|---------------------|
| `toggle_tool` | Immediately | No (session-only) |
| `reset_tools` | Immediately | No |
| `manage_auto_approve` | Immediately | **Immediately** |
| Startup | Sync from config → DB | No change |

### Orchestrator Tools (Post-Upgrade)

| Tool | Description | Status |
|------|-------------|--------|
| `find_tools` | Semantic/keyword hybrid search | Existing (improved with pgvector) |
| `execute_dynamic_tool` | Route to upstream MCP tool | Existing (added disabled check) |
| `toggle_tool` | Enable/disable tool/server at runtime | **New** |
| `reset_tools` | Reset toggle states to default | **New** |
| `manage_auto_approve` | Manage auto-approve settings | **New** |

### Glossary

| Term | Definition |
|------|------------|
| MCP | Model Context Protocol — standard protocol for AI tool integration |
| pgvector | PostgreSQL extension for vector similarity search |
| HNSW | Hierarchical Navigable Small World — approximate nearest neighbor index algorithm |
| tsvector | PostgreSQL full-text search vector type |
| Ollama | Local LLM/embedding server supporting various models |
| LMStudio | Desktop application for running local LLMs with OpenAI-compatible API |
| session_id | Unique identifier per orchestrator instance for state isolation |
| Upstream MCP Server | External MCP server providing actual tools (e.g., Jira, filesystem) |

### Reference Documents

| Document | Link / Location |
|----------|-----------------|
| Jira Ticket MTO-10 | https://jiraassist.atlassian.net/browse/MTO-10 |
| MCP Protocol Specification | https://modelcontextprotocol.io/specification |
| pgvector Documentation | https://github.com/pgvector/pgvector |
| Ollama API Reference | https://github.com/ollama/ollama/blob/main/docs/api.md |

---

## Diagram Index

| # | Diagram | Type | File (Editable) | File (Image) | Embedded In |
|---|---------|------|-----------------|--------------|-------------|
| 1 | Use Case Diagram | UML Use Case | [use-case.drawio](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/use-case.drawio) | [use-case.png](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/use-case.png) | Section 2.1 |
| 2 | Business Flow | Swimlane / Cross-functional | [business-flow.drawio](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/business-flow.drawio) | [business-flow.png](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/business-flow.png) | Section 2.1 |
| 3 | Sequence — Tool Discovery & Execution | Sequence Diagram | [sequence-tool-discovery.drawio](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-discovery.drawio) | [sequence-tool-discovery.png](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-discovery.png) | Section 2.3 |
| 4 | Sequence — Tool Management | Sequence Diagram | [sequence-tool-mgmt.drawio](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-mgmt.drawio) | [sequence-tool-mgmt.png](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-mgmt.png) | Section 2.3 |
