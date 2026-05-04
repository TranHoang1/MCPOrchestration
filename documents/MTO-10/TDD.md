# Technical Design Document (TDD)

## MCP Orchestration Server — MTO-10: Upgrade MCP Orchestrator: Local Embedding, pgvector, Tool Management & Auto-Approve

---

## Document Information

| Version | Value |
|-------|-------|
| Jira Ticket | MTO-10 |
| Title | Upgrade MCP Orchestrator: Local Embedding, pgvector, Tool Management & Auto-Approve |
| Author | SA Agent |
| Version | 1.2 |
| Date | 2026-05-04 |
| Status | TA Enriched |
| Related BRD | documents/MTO-10/BRD.md |
| Related FSD | documents/MTO-10/FSD.md |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | SA Agent – Solution Architect | Create document |
| Peer Reviewer | Tech Lead – Senior Engineer | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-03 | SA Agent | Initiate document — auto-generated from BRD and FSD |
| 1.1 | 2026-05-03 | SA Agent | Updated all diagram image references to absolute local file paths to ensure high-fidelity DOCX export via pandoc. No architectural content changes. |
| 1.2 | 2026-05-04 | SA Agent | Integrated technical design for Dual Operational Modes (Standalone HTTP/SSE vs Local Bridge Stdio). |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm the technical design in this TDD |
| | ☐ I agree and confirm the technical design in this TDD |

---

## 1. Introduction

### 1.1 Purpose

This TDD specifies the technical design for upgrading the existing MCP Orchestration Server (MTO-5) with four major capabilities: local embedding providers (Ollama/LMStudio), PostgreSQL pgvector for vector storage, runtime tool management (toggle/reset/auto-approve), and config-DB synchronization. This document translates the FSD's 9 features (7 new, 2 modified) and 40 business rules into concrete implementation decisions.

### 1.2 Scope

**Technical scope covered:**
- New `EmbeddingService` implementations: `OllamaEmbeddingService`, `LmStudioEmbeddingService`
- New `VectorDbClient` implementation: `PgVectorDbClient` with hybrid search (vector + tsvector)
- New service layer: `ToolManagementService`, `ConfigDbSyncService`, `ToolFilterService`
- 3 new MCP tools: `toggle_tool`, `reset_tools`, `manage_auto_approve`
- 3 new PostgreSQL tables: `tool_embeddings`, `tool_toggle_state`, `server_config`
- Modifications to `ToolDiscoveryServiceImpl`, `ToolExecutionDispatcherImpl`, `McpServerFactory`, `AppModule`
- Config data class extensions: `EmbeddingConfig`, `VectorDbConfig`, `UpstreamServerConfig`

**Not covered:**
- UI/Dashboard (future phase)
- Migration of existing Qdrant data (fresh start assumed)
- Multi-tenant support

### 1.3 Technology Stack

| Layer | Technology | Version | Change |
|-------|-----------|---------|--------|
| Language | Kotlin | 2.3.20 | — |
| Framework | Ktor | 3.4.0 | — |
| DI Framework | Koin | 4.1.1 | — |
| Serialization | kotlinx.serialization | 1.8.1 | — |
| Coroutines | kotlinx.coroutines | 1.10.2 | — |
| Vector DB (NEW) | PostgreSQL + pgvector | 16+ / 0.7+ | Replaces Qdrant/FAISS |
| Connection Pool (NEW) | HikariCP | 5.1.0 | NEW dependency |
| JDBC Driver (NEW) | postgresql | 42.7.x | NEW dependency |
| Embeddings (NEW) | Ollama / LMStudio | Local | Replaces OpenAI (optional fallback) |
| MCP SDK | kotlin-sdk-server | 0.12.0 | — |
| Build Tool | Gradle (Kotlin DSL) | 8.x | — |
| Testing | Kotest + MockK + Testcontainers | 5.9.1 / 1.14.2 / 1.21.1 | — |

### 1.4 Design Principles

- **Interface/Impl pattern** — All new services follow existing `Interface` + `Impl` convention
- **Drop-in replacement** — New embedding/vector DB implementations satisfy existing interfaces
- **Fail-open for toggles** — If DB unreachable, assume all tools enabled (FSD §7.2)
- **Config as source of truth** — `mcp-servers.json` is authoritative; DB is runtime mirror (BR-27/BR-29)
- **Session isolation** — Toggle states scoped per `session_id` (BR-16)
- **Atomic config writes** — Temp file + rename prevents corruption (BR-26/BR-28)

### 1.5 Constraints

- PostgreSQL 16+ with pgvector extension required at runtime
- At least one embedding provider (Ollama or LMStudio) must be running for semantic search
- HNSW index dimensions fixed at creation — dimension change requires index rebuild
- `mcp-servers.json` must be on local filesystem (not remote/shared storage)
- HikariCP connection pool: min 2, max 10 connections

### 1.6 References

| Document | Location |
|----------|----------|
| BRD — MTO-10 | documents/MTO-10/BRD.md |
| FSD — MTO-10 | documents/MTO-10/FSD.md |
| TDD — MTO-5 (Base) | documents/MTO-5/TDD.md |
| pgvector Docs | https://github.com/pgvector/pgvector |
| Ollama API Docs | https://github.com/ollama/ollama/blob/main/docs/api.md |


---

## 2. System Architecture

### 2.1 Architecture Overview

The MTO-10 upgrade retains the four-layer architecture from MTO-5 (Transport → Protocol → Orchestration → Infrastructure) and extends it with new components at the Orchestration and Infrastructure layers. The key architectural change is replacing external paid dependencies (OpenAI, Qdrant) with local/self-hosted alternatives (Ollama/LMStudio, PostgreSQL pgvector) and adding a runtime tool management subsystem.

**Key architectural changes from MTO-5:**

| Layer | Change | Impact |
|-------|--------|--------|
| Protocol | McpServerFactory registers 5 tools (was 2) | +3 new MCP tool handlers |
| Orchestration | New `ToolManagementService`, `ToolFilterService` | New package `management/` |
| Orchestration | `ToolDiscoveryServiceImpl` adds toggle filtering | Modified `findTools()` flow |
| Orchestration | `ToolExecutionDispatcherImpl` adds disabled check | Modified `execute()` flow |
| Infrastructure | New `PgVectorDbClient` with hybrid search | Replaces Qdrant/FAISS |
| Infrastructure | New `OllamaEmbeddingService`, `LmStudioEmbeddingService` | Drop-in replacements |
| Infrastructure | New `ConfigDbSyncService` + `DatabaseModule` | PostgreSQL connection pool |
| Config | Extended data classes + new `SessionConfig`, `ToolFilterConfig` | Backward compatible |

![Architecture Diagram](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/architecture.png)

### 2.2 Component Diagram

The component diagram shows all new (green) and modified (orange) components and their dependency relationships.

![Component Diagram](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/component.png)

| Component | Type | Responsibility | Technology |
|-----------|------|---------------|------------|
| OllamaEmbeddingService | NEW | Embeddings via Ollama local API | Ktor Client → `POST /api/embeddings` |
| LmStudioEmbeddingService | NEW | Embeddings via LMStudio OpenAI-compatible API | Ktor Client → `POST /v1/embeddings` |
| PgVectorDbClient | NEW | Vector storage + hybrid search via pgvector | JDBC + HikariCP |
| ToolManagementServiceImpl | NEW | toggle_tool, reset_tools, manage_auto_approve | JDBC + file I/O |
| ConfigDbSyncServiceImpl | NEW | Sync mcp-servers.json → PostgreSQL on startup | JDBC |
| ToolFilterServiceImpl | NEW | Allowlist/blocklist filtering during indexing | Pure logic |
| DatabaseModule | NEW | HikariCP connection pool management | HikariCP |
| McpServerFactory | MODIFIED | Register 5 tools (was 2) | MCP SDK |
| ToolDiscoveryServiceImpl | MODIFIED | Hybrid search + toggle filtering | pgvector SQL |
| ToolExecutionDispatcherImpl | MODIFIED | Disabled tool check before execution | JDBC query |
| ToolIndexer | MODIFIED | Apply tool filter before embedding | Delegates to ToolFilterService |
| AppModule (Koin) | MODIFIED | Conditional DI bindings by provider | Koin DSL |

### 2.3 Deployment Architecture

Deployment topology is unchanged from MTO-5 — single JVM process on developer machine. The key change is that PostgreSQL (already available for `jira_assistant` DB) replaces both Qdrant (Docker container) and OpenAI API (cloud service), simplifying the deployment footprint.

**Before (MTO-5):** Orchestrator → Qdrant (Docker) + OpenAI API (cloud)
**After (MTO-10):** Orchestrator → PostgreSQL (existing) + Ollama/LMStudio (local)

### 2.4 Communication Patterns

| From | To | Protocol | Pattern | Change |
|------|----|----------|---------|--------|
| Orchestrator | PostgreSQL | JDBC | Sync (HikariCP pool) | NEW |
| Orchestrator | Ollama | HTTP REST | Sync request-response | NEW |
| Orchestrator | LMStudio | HTTP REST | Sync request-response | NEW |
| Orchestrator | Qdrant | HTTP REST | Sync request-response | DEPRECATED (kept for backward compat) |
| Orchestrator | OpenAI API | HTTPS REST | Sync request-response | OPTIONAL fallback |
| Kiro IDE | Orchestrator | MCP/JSON-RPC | Sync request-response | +3 new tools |
| All others | — | — | — | Unchanged from MTO-5 |


---

## 3. API Design

### 3.1 API Overview

After MTO-10, the Orchestrator exposes **5 MCP tools** via JSON-RPC 2.0 (was 2 in MTO-5):

| # | Tool Name | Type | Description | Source |
|---|-----------|------|-------------|--------|
| 1 | `find_tools` | MODIFIED | Hybrid semantic/keyword search via pgvector, excludes disabled tools | UC-01, BR-38/39 |
| 2 | `execute_dynamic_tool` | MODIFIED | Proxy execution with disabled tool check | UC-02, BR-40 |
| 3 | `toggle_tool` | NEW | Enable/disable tool or server at runtime | UC-03, BR-13..19 |
| 4 | `reset_tools` | NEW | Reset all toggle states to defaults | UC-04, BR-20..23 |
| 5 | `manage_auto_approve` | NEW | Manage auto-approve with persistent config write | UC-05, BR-24..28 |

### 3.2 Tool: `toggle_tool` (NEW)

**Implements:** UC-03, BR-13, BR-14, BR-15, BR-16, BR-17, BR-18, BR-19

**MCP Tool Definition:**

```json
{
  "name": "toggle_tool",
  "description": "Enable or disable a specific tool or all tools from a server at runtime. Disabled tools are hidden from find_tools and blocked from execute_dynamic_tool.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "tool_name": { "type": "string", "description": "Name of the specific tool to toggle" },
      "server_name": { "type": "string", "description": "Name of the server whose tools to toggle" },
      "enabled": { "type": "boolean", "description": "true to enable, false to disable" }
    },
    "required": ["enabled"]
  }
}
```

**Success Response:** `{"success":true,"message":"Tool 'X' disabled.","affected_tools":1,"tool_name":"X"}`

**Error Responses:**

| Code | Condition | Message |
|------|-----------|---------|
| INVALID_PARAMS | Neither tool_name nor server_name provided | "At least one of tool_name or server_name is required." |
| TOOL_NOT_FOUND | Tool not in registry | "Tool '{name}' not found. Available tools: {suggestions}." |
| INVALID_PARAMS | Server not in registry | "Server '{name}' not found. Available servers: {list}." |
| INTERNAL_ERROR | DB write failure | "Failed to persist toggle state." |

### 3.3 Tool: `reset_tools` (NEW)

**Implements:** UC-04, BR-20, BR-21, BR-22, BR-23

**MCP Tool Definition:**

```json
{
  "name": "reset_tools",
  "description": "Reset all tool toggle states to defaults (all enabled). Optionally scope to a specific server and trigger re-indexing.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "server_name": { "type": "string", "description": "Optional: only reset tools from this server" },
      "reindex": { "type": "boolean", "description": "Whether to re-index tools after reset (default: true)", "default": true }
    }
  }
}
```

**Success Response:** `{"success":true,"message":"Reset 5 tool toggle states. Re-indexed 42 tools.","affected_tools":5,"reindexed":true}`

**Partial Success:** `{"success":true,"message":"Reset 5 states. Re-indexing failed: embedding service unavailable.","affected_tools":5,"reindexed":false,"reindex_warning":"..."}`

### 3.4 Tool: `manage_auto_approve` (NEW)

**Implements:** UC-05, BR-24, BR-25, BR-26, BR-27, BR-28

**MCP Tool Definition:**

```json
{
  "name": "manage_auto_approve",
  "description": "Add or remove tools from the auto-approve list. Changes are written immediately to mcp-servers.json and persist across restarts.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "tool_name": { "type": "string", "description": "Name of the specific tool to auto-approve/un-approve" },
      "server_name": { "type": "string", "description": "Name of the server whose tools to auto-approve/un-approve" },
      "auto_approve": { "type": "boolean", "description": "true to add to auto-approve, false to remove" }
    },
    "required": ["auto_approve"]
  }
}
```

**Success Response:** `{"success":true,"message":"Added 'X' to auto-approve.","config_updated":true,"db_updated":true,"affected_tools":["X"]}`

**Error Responses:**

| Code | Condition | Message |
|------|-----------|---------|
| INVALID_PARAMS | Neither tool_name nor server_name | "At least one of tool_name or server_name is required." |
| CONFIG_CORRUPTED | Invalid JSON in config file | "Config file is corrupted. Cannot update." |
| CONFIG_WRITE_FAILED | File not writable | "Config file not writable. Changes applied to runtime only." |

### 3.5 Modified Tool: `find_tools`

**Changes from MTO-5:**
1. Backend: pgvector hybrid search (vector 70% + keyword 30%) replaces Qdrant ANN
2. Post-filter: exclude tools disabled via `toggle_tool` for current `session_id`
3. Embedding: generated by Ollama/LMStudio/OpenAI (provider-dependent)

No schema changes — existing `find_tools` input/output contract preserved.

### 3.6 Modified Tool: `execute_dynamic_tool`

**Changes from MTO-5:**
1. New step 3.5: check `tool_toggle_state` before execution
2. If tool or its server is disabled → return `TOOL_DISABLED` error

New error response: `{"success":false,"error":"TOOL_DISABLED","message":"Tool 'X' is currently disabled. Use toggle_tool to re-enable."}`

### 3.7 Internal Service APIs

#### ToolManagementService Interface

```kotlin
interface ToolManagementService {
    suspend fun toggleTool(request: ToggleToolRequest): ToggleToolResponse
    suspend fun resetTools(request: ResetToolsRequest): ResetToolsResponse
    suspend fun manageAutoApprove(request: ManageAutoApproveRequest): ManageAutoApproveResponse
    suspend fun isToolDisabled(toolName: String, serverName: String, sessionId: String): Boolean
    suspend fun loadDisabledTools(sessionId: String): DisabledState
}
```

#### ConfigDbSyncService Interface

```kotlin
interface ConfigDbSyncService {
    suspend fun sync(): SyncResult
    fun getSyncResult(): SyncResult?
}
```

#### ToolFilterService Interface

```kotlin
interface ToolFilterService {
    fun filterTools(
        tools: List<ToolDefinition>,
        serverName: String,
        toolFilter: ToolFilterConfig?
    ): List<ToolDefinition>
}
```


---

## 4. Database Design

### 4.1 Schema Overview

MTO-10 introduces 3 new PostgreSQL tables in the existing `jira_assistant` database. All tables use pgvector extension for vector operations.

![ER Diagram](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/er-diagram.png)

### 4.2 DDL Scripts

#### Extension Setup

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

#### Table: tool_embeddings

```sql
CREATE TABLE IF NOT EXISTS tool_embeddings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_name     VARCHAR(255) NOT NULL,
    tool_name       VARCHAR(255) NOT NULL,
    description     TEXT NOT NULL,
    embedding       vector(768) NOT NULL,
    payload         JSONB,
    input_schema    JSONB,
    search_vector   tsvector NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tool_embeddings_server_tool UNIQUE (server_name, tool_name)
);

CREATE INDEX IF NOT EXISTS idx_tool_embeddings_hnsw
    ON tool_embeddings USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_tool_embeddings_search
    ON tool_embeddings USING gin (search_vector);
```

#### Table: tool_toggle_state

```sql
CREATE TABLE IF NOT EXISTS tool_toggle_state (
    id          SERIAL PRIMARY KEY,
    session_id  VARCHAR(255) NOT NULL,
    tool_name   VARCHAR(255),
    server_name VARCHAR(255),
    enabled     BOOLEAN NOT NULL,
    toggled_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_toggle_target CHECK (tool_name IS NOT NULL OR server_name IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_toggle_session
    ON tool_toggle_state (session_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_toggle_session_tool
    ON tool_toggle_state (session_id, tool_name) WHERE tool_name IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_toggle_session_server
    ON tool_toggle_state (session_id, server_name) WHERE server_name IS NOT NULL;
```

#### Table: server_config

```sql
CREATE TABLE IF NOT EXISTS server_config (
    id          SERIAL PRIMARY KEY,
    server_name VARCHAR(255) NOT NULL UNIQUE,
    transport   VARCHAR(50) NOT NULL,
    command     TEXT,
    args        JSONB,
    env_keys    JSONB,
    url         TEXT,
    disabled    BOOLEAN DEFAULT false,
    tool_filter JSONB,
    auto_approve JSONB,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    synced_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 4.3 Migration Plan

| Order | Script | Description | Rollback |
|-------|--------|-------------|----------|
| 1 | `V10_001__enable_pgvector.sql` | `CREATE EXTENSION IF NOT EXISTS vector` | `DROP EXTENSION vector CASCADE` |
| 2 | `V10_002__create_tool_embeddings.sql` | Create table + HNSW + GIN indexes | `DROP TABLE tool_embeddings` |
| 3 | `V10_003__create_tool_toggle_state.sql` | Create table + partial unique indexes | `DROP TABLE tool_toggle_state` |
| 4 | `V10_004__create_server_config.sql` | Create table + unique index | `DROP TABLE server_config` |

All DDL is idempotent (`IF NOT EXISTS`). Executed by `PgVectorDbClient.createCollection()` and `ConfigDbSyncServiceImpl` on startup.

### 4.4 Key Query Patterns

| Operation | Query Pattern | Expected Performance |
|-----------|--------------|---------------------|
| Hybrid search (top-5) | CTE: vector_results FULL JOIN keyword_results | < 100ms (BR-10) |
| Upsert tool embedding | `INSERT ... ON CONFLICT DO UPDATE` | < 10ms per tool |
| Load disabled tools | `SELECT WHERE session_id = ? AND enabled = false` | < 5ms |
| Toggle upsert | `INSERT ... ON CONFLICT DO UPDATE` | < 5ms |
| Config-DB sync (50 servers) | Batch INSERT/UPDATE/soft-DELETE | < 5s (BR-31) |
| Delete server tools | `DELETE WHERE server_name = ?` | < 10ms |

#### Hybrid Search SQL

```sql
WITH vector_results AS (
    SELECT id, tool_name, server_name, description, input_schema, payload,
           1 - (embedding <=> $1::vector) AS vector_score
    FROM tool_embeddings
    WHERE 1 - (embedding <=> $1::vector) >= $2
    ORDER BY embedding <=> $1::vector
    LIMIT $3
),
keyword_results AS (
    SELECT id, tool_name, server_name, description, input_schema, payload,
           ts_rank(search_vector, plainto_tsquery($4)) AS keyword_score
    FROM tool_embeddings
    WHERE search_vector @@ plainto_tsquery($4)
    LIMIT $3
)
SELECT DISTINCT ON (id) *,
       COALESCE(v.vector_score, 0) * 0.7 + COALESCE(k.keyword_score, 0) * 0.3 AS combined_score
FROM vector_results v
FULL OUTER JOIN keyword_results k USING (id, tool_name, server_name, description, input_schema, payload)
ORDER BY combined_score DESC
LIMIT $3;
```

Parameters: `$1` = query embedding, `$2` = threshold, `$3` = top_k, `$4` = query text.


---

## 5. Class / Module Design

### 5.1 Package Structure (MTO-10 additions)

![Package Structure](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/package-structure.png)

### 5.2 Class Diagram

![Class Diagram](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/class-diagram.png)

### 5.3 Key Interfaces & Implementations

#### 5.3.1 EmbeddingService (existing interface, 2 new implementations)

```kotlin
// Existing interface — UNCHANGED
interface EmbeddingService {
    suspend fun generateEmbedding(text: String): FloatArray
    suspend fun generateEmbeddings(texts: List<String>): List<FloatArray>
    suspend fun isHealthy(): Boolean
}

// NEW: OllamaEmbeddingService
class OllamaEmbeddingService(
    private val httpClient: HttpClient,
    private val baseUrl: String,    // e.g. "http://localhost:11434"
    private val model: String,      // e.g. "nomic-embed-text"
    private val dimensions: Int
) : EmbeddingService

// NEW: LmStudioEmbeddingService
class LmStudioEmbeddingService(
    private val httpClient: HttpClient,
    private val baseUrl: String,    // e.g. "http://localhost:1234"
    private val model: String,
    private val dimensions: Int
) : EmbeddingService
```

#### 5.3.2 VectorDbClient (existing interface, 1 new implementation)

```kotlin
// Existing interface — UNCHANGED
interface VectorDbClient {
    suspend fun createCollection(name: String, dimensions: Int)
    suspend fun upsert(collectionName: String, points: List<VectorPoint>)
    suspend fun search(collectionName: String, vector: FloatArray, limit: Int, scoreThreshold: Float): List<SearchResult>
    suspend fun delete(collectionName: String, filter: Map<String, String>)
    suspend fun isHealthy(): Boolean
}

// NEW: PgVectorDbClient — implements VectorDbClient + adds hybrid search
class PgVectorDbClient(
    private val dataSource: HikariDataSource,
    private val hnswM: Int = 16,
    private val hnswEfConstruction: Int = 64
) : VectorDbClient {
    // Additional method for hybrid search (used internally by ToolDiscoveryServiceImpl)
    suspend fun hybridSearch(
        collectionName: String,
        vector: FloatArray,
        queryText: String,
        limit: Int,
        scoreThreshold: Float,
        vectorWeight: Float = 0.7f,
        keywordWeight: Float = 0.3f
    ): List<SearchResult>
}
```

#### 5.3.3 ToolManagementService (new)

```kotlin
interface ToolManagementService {
    suspend fun toggleTool(request: ToggleToolRequest): ToggleToolResponse
    suspend fun resetTools(request: ResetToolsRequest): ResetToolsResponse
    suspend fun manageAutoApprove(request: ManageAutoApproveRequest): ManageAutoApproveResponse
    suspend fun isToolDisabled(toolName: String, serverName: String, sessionId: String): Boolean
    suspend fun loadDisabledTools(sessionId: String): DisabledState
}
```

#### 5.3.4 ConfigDbSyncService (new)

```kotlin
interface ConfigDbSyncService {
    suspend fun sync(): SyncResult
    fun getSyncResult(): SyncResult?
}
```

#### 5.3.5 ToolFilterService (new)

```kotlin
interface ToolFilterService {
    fun filterTools(
        tools: List<ToolDefinition>,
        serverName: String,
        toolFilter: ToolFilterConfig?
    ): List<ToolDefinition>
}
```

### 5.4 Config Data Class Changes

```kotlin
// MODIFIED: EmbeddingConfig — add base_url
@Serializable
data class EmbeddingConfig(
    val provider: String = "openai",
    val model: String = "text-embedding-3-small",
    @SerialName("api_key") val apiKey: String = "",
    val dimensions: Int = 768,
    @SerialName("base_url") val baseUrl: String = "",  // NEW
    @SerialName("cache_enabled") val cacheEnabled: Boolean = true,
    @SerialName("cache_max_size") val cacheMaxSize: Int = 100,
    @SerialName("cache_ttl_minutes") val cacheTtlMinutes: Int = 5
)

// MODIFIED: VectorDbConfig — add pgvector fields
@Serializable
data class VectorDbConfig(
    val provider: String = "qdrant",
    val host: String = "localhost",
    val port: Int = 6333,
    @SerialName("collection_name") val collectionName: String = "mcp_tools",
    @SerialName("connection_string") val connectionString: String = "",  // NEW
    @SerialName("hnsw_m") val hnswM: Int = 16,                          // NEW
    @SerialName("hnsw_ef_construction") val hnswEfConstruction: Int = 64 // NEW
)

// MODIFIED: OrchestratorSettings — add session
@Serializable
data class OrchestratorSettings(
    // ... existing fields ...
    val session: SessionConfig = SessionConfig()  // NEW
)

// NEW: SessionConfig
@Serializable
data class SessionConfig(
    val id: String = "\${HOSTNAME:-orch-default}"
)

// NEW: ToolFilterConfig
@Serializable
data class ToolFilterConfig(
    val mode: String,           // "allowlist" or "blocklist"
    val tools: List<String>
)

// MODIFIED: UpstreamServerConfig — add new fields
@Serializable
data class UpstreamServerConfig(
    val name: String,
    val transport: String = "stdio",
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val url: String? = null,
    val disabled: Boolean = false,                    // NEW
    val toolFilter: ToolFilterConfig? = null,          // NEW
    val autoApprove: List<String> = emptyList()        // NEW
)
```

### 5.5 New Exception Types

```kotlin
// ADD to sealed hierarchy in Exceptions.kt
class ToolDisabledException(toolName: String) :
    McpOrchestratorException(
        ErrorCodes.TOOL_DISABLED,
        "Tool '$toolName' is currently disabled. Use toggle_tool to re-enable."
    )

class ConfigWriteException(message: String, cause: Throwable? = null) :
    McpOrchestratorException(ErrorCodes.CONFIG_WRITE_FAILED, message, cause)
```

### 5.6 New Error Codes

```kotlin
// ADD to ErrorCodes.kt
const val TOOL_DISABLED = "TOOL_DISABLED"
const val SERVER_DISABLED = "SERVER_DISABLED"
const val CONFIG_WRITE_FAILED = "CONFIG_WRITE_FAILED"
const val CONFIG_CORRUPTED = "CONFIG_CORRUPTED"
const val PGVECTOR_UNAVAILABLE = "PGVECTOR_UNAVAILABLE"
const val SYNC_FAILED = "SYNC_FAILED"
```

### 5.7 Design Patterns

| Pattern | Where Used | Rationale |
|---------|-----------|-----------|
| Strategy | `EmbeddingService` with 3 implementations | Provider selected at config time via Koin |
| Strategy | `VectorDbClient` with Qdrant/FAISS/pgvector | Backend selected at config time |
| Interface/Impl | All new services | Testability, mockability |
| Template Method | `ToolIndexer.indexTools()` + `ToolFilterService` | Filter step injected before embedding |
| Facade | `ToolManagementService` | Single entry point for toggle/reset/auto-approve |
| Singleton (Koin) | All services | Single instance per JVM |


---

## 6. Integration Design

### 6.1 External System: Ollama (NEW)

| Attribute | Value |
|-----------|-------|
| Protocol | HTTP REST |
| Endpoint | `http://localhost:11434/api/embeddings` (default) |
| Authentication | None (local service) |
| Connect Timeout | 5s |
| Request Timeout | 30s |
| Retry Policy | No retry (fail fast → keyword fallback) |

**Request:** `POST {base_url}/api/embeddings`
```json
{ "model": "nomic-embed-text", "prompt": "Get details of a specific Jira issue" }
```

**Response:**
```json
{ "embedding": [0.0023, -0.0091, 0.0142, ...] }
```

**Error Mapping:**

| HTTP Status | Exception | Fallback |
|-------------|-----------|----------|
| Connection refused | `EmbeddingServiceException` | Keyword-only search |
| 404 (model not found) | `EmbeddingServiceException` | Keyword-only search |
| 500 | `EmbeddingServiceException` | Keyword-only search |
| Timeout | `EmbeddingServiceException` | Keyword-only search |

### 6.2 External System: LMStudio (NEW)

| Attribute | Value |
|-----------|-------|
| Protocol | HTTP REST (OpenAI-compatible) |
| Endpoint | `http://localhost:1234/v1/embeddings` (default) |
| Authentication | None (local service) |
| Connect Timeout | 5s |
| Request Timeout | 30s |

**Request:** `POST {base_url}/v1/embeddings`
```json
{ "model": "nomic-embed-text", "input": "Get details of a specific Jira issue" }
```

**Response:**
```json
{ "data": [{ "embedding": [0.0023, -0.0091, ...], "index": 0 }] }
```

**Batch Request:** `"input": ["text one", "text two"]` → multiple entries in `data[]`.

### 6.3 External System: PostgreSQL + pgvector (NEW)

| Attribute | Value |
|-----------|-------|
| Protocol | JDBC (PostgreSQL wire protocol) |
| Connection String | `jdbc:postgresql://localhost:5432/jira_assistant` |
| Authentication | Username/password from config |
| Connection Pool | HikariCP: min=2, max=10, idle timeout=600s |
| Query Timeout | 30s |
| Retry Policy | 3 attempts, exponential backoff (1s, 2s, 4s) |

### 6.4 Sequence Diagram: toggle_tool Flow

```mermaid
sequenceDiagram
    participant K as Kiro IDE
    participant F as McpServerFactory
    participant M as ToolManagementService
    participant R as ToolRegistry
    participant DB as PostgreSQL

    K->>F: tools/call {toggle_tool, tool_name="X", enabled=false}
    F->>M: toggleTool(request)
    M->>M: Validate: tool_name or server_name provided
    M->>R: lookupTool("X")
    R-->>M: ToolEntry (exists)
    M->>DB: UPSERT tool_toggle_state (session_id, tool_name="X", enabled=false)
    DB-->>M: OK
    M-->>F: ToggleToolResponse(success=true, affected=1)
    F-->>K: CallToolResult {text: JSON response}
```

### 6.5 Sequence Diagram: find_tools with Hybrid Search + Toggle Filter

```mermaid
sequenceDiagram
    participant K as Kiro IDE
    participant F as McpServerFactory
    participant D as ToolDiscoveryService
    participant E as EmbeddingService (Ollama)
    participant PG as PgVectorDbClient
    participant M as ToolManagementService

    K->>F: tools/call {find_tools, query="search Jira"}
    F->>D: findTools(query, topK, threshold)
    D->>E: generateEmbedding("search Jira")
    E-->>D: FloatArray[768]
    D->>PG: hybridSearch(vector, "search Jira", topK*2, threshold)
    PG-->>D: List<SearchResult> (over-fetched)
    D->>M: loadDisabledTools(sessionId)
    M-->>D: DisabledState {toolNames, serverNames}
    D->>D: Filter out disabled tools
    D->>D: Trim to topK
    D-->>F: FindToolsResponse
    F-->>K: CallToolResult
```

### 6.6 Sequence Diagram: Config-DB Sync on Startup

```mermaid
sequenceDiagram
    participant App as Application.main()
    participant Sync as ConfigDbSyncService
    participant CM as ConfigurationManager
    participant DB as PostgreSQL

    App->>Sync: sync()
    Sync->>CM: Read mcp-servers.json
    CM-->>Sync: McpServersConfig
    Sync->>DB: SELECT * FROM server_config WHERE is_active=true
    DB-->>Sync: List<ServerConfigRow>
    Sync->>Sync: Compute diff (new, changed, removed)
    Sync->>DB: INSERT new servers
    Sync->>DB: UPDATE changed servers
    Sync->>DB: SET is_active=false for removed servers
    DB-->>Sync: OK
    Sync-->>App: SyncResult(added=2, updated=1, deactivated=0)
```


---

## 7. Security Design

### 7.1 Authentication

No changes from MTO-5. All transports (stdio/HTTP) are local — no authentication required.

### 7.2 Data Protection

| Data Type | At Rest | In Transit | In Logs | In DB |
|-----------|---------|------------|---------|-------|
| API keys (OpenAI) | `${ENV_VAR}` in config | N/A (local) | Masked | NOT stored (BR-32) |
| DB credentials | Config/env var | JDBC (local) | Masked | N/A |
| Env var values | mcp-servers.json | N/A | Excluded | `env_keys` stores NAMES only |
| Toggle states | PostgreSQL | JDBC (local) | session_id logged | Session-scoped |
| Tool descriptions | PostgreSQL + pgvector | JDBC (local) | Truncated | Full text stored |

### 7.3 Input Validation

| Field | Validation | Sanitization |
|-------|-----------|--------------|
| `toggle_tool.tool_name` | Must exist in ToolRegistry | Trim whitespace |
| `toggle_tool.server_name` | Must exist in ToolRegistry | Trim whitespace |
| `toggle_tool.enabled` | Required boolean | Type check |
| `manage_auto_approve.auto_approve` | Required boolean | Type check |
| `reset_tools.server_name` | Must exist if provided | Trim whitespace |
| `embedding.provider` | One of: `ollama`, `lmstudio`, `openai` | Config validation |
| `embedding.dimensions` | 1–4096 | Clamp to range |
| `embedding.base_url` | Valid HTTP(S) URL | URL parsing |
| SQL parameters | Parameterized queries (PreparedStatement) | No string interpolation |

### 7.4 Config File Security

- Atomic writes: temp file + rename (BR-26)
- File lock: `Mutex` (in-process) + `FileLock` (cross-process) for `manage_auto_approve`
- Config file permission check: warn if world-readable
- No secrets written to DB: `env_keys` stores key names only, not values (BR-32)

---

## 8. Performance & Scalability

### 8.1 Performance Targets

| Operation | Target | Source |
|-----------|--------|--------|
| Embedding generation (local) | < 500ms per tool | BR-06 |
| pgvector hybrid search (top-5) | < 100ms | BR-10 |
| Config-DB sync (50 servers) | < 5s | BR-31 |
| toggle_tool response | < 200ms | FSD §9 |
| manage_auto_approve (incl. file write) | < 500ms | FSD §9 |
| Server startup (DB init + sync, excl. indexing) | < 10s | FSD §9 |
| Tool indexing (100 tools, local embedding) | < 60s | FSD §9 |
| Keyword-only fallback (top-5) | < 50ms | FSD §9 |

### 8.2 Connection Pooling

| Resource | Min | Max | Connect Timeout | Idle Timeout |
|----------|-----|-----|-----------------|-------------|
| PostgreSQL (HikariCP) | 2 | 10 | 5s | 600s |
| Ktor HttpClient (CIO) | — | — | 5s (connect) | 30s (request) |

### 8.3 Caching Strategy

| Cache | What | TTL | Eviction | Size |
|-------|------|-----|----------|------|
| Embedding cache | text → FloatArray | 5 min | LRU | 100 entries (~60MB) |
| Toggle state cache | session_id → DisabledState | 30s | TTL refresh | 1 entry per session |

### 8.4 Scalability Limits

| Dimension | Limit | Rationale |
|-----------|-------|-----------|
| Total tools | 10,000 | pgvector HNSW performance (BR-11) |
| Upstream servers | 50 | Config-DB sync target (BR-31) |
| Concurrent MCP calls | 10 | Coroutine-based, limited by DB pool |
| Vector dimensions | 768 (default) | Configurable 1–4096 |

---

## 9. Monitoring & Observability

### 9.1 Logging

| Log Event | Level | Fields | Source |
|-----------|-------|--------|--------|
| Tool toggled | INFO | session_id, tool/server_name, enabled | ToolManagementService |
| Tools reset | INFO | session_id, affected_count, reindexed | ToolManagementService |
| Auto-approve changed | INFO | tool/server_name, config_updated, db_updated | ToolManagementService |
| Config-DB sync | INFO | added, updated, deactivated, duration_ms | ConfigDbSyncService |
| Embedding provider init | INFO | provider, model, base_url, status | AppModule |
| Tool filter applied | INFO | server, mode, original_count, filtered_count | ToolFilterService |
| Hybrid search | INFO | query (truncated), results, mode, duration_ms | ToolDiscoveryService |
| Tool disabled error | WARN | tool_name, session_id | ToolExecutionDispatcher |
| DB connection failure | ERROR | connection_string (masked), error | DatabaseModule |
| Embedding service down | WARN | provider, base_url, error | EmbeddingService |

### 9.2 Health Checks

| Check | Component | Expected |
|-------|-----------|----------|
| PostgreSQL connectivity | `PgVectorDbClient.isHealthy()` | `SELECT 1` succeeds |
| Embedding service | `EmbeddingService.isHealthy()` | Provider endpoint reachable |
| Upstream servers | `HealthMonitor` (existing) | Unchanged from MTO-5 |

---

## 10. Deployment Considerations

### 10.1 Environment Configuration

| Property | DEV | Notes |
|----------|-----|-------|
| `embedding.provider` | `ollama` | Local provider |
| `embedding.model` | `nomic-embed-text` | Default model |
| `embedding.base_url` | `http://localhost:11434` | Ollama default |
| `vector_db.provider` | `pgvector` | PostgreSQL backend |
| `vector_db.connection_string` | `postgresql://postgres:postgres@localhost:5432/jira_assistant` | Existing DB |
| `session.id` | `${HOSTNAME:-orch-default}` | Auto from hostname |

### 10.2 New Dependencies (build.gradle.kts)

```kotlin
// Version catalog additions
[libraries]
hikaricp = { module = "com.zaxxer:HikariCP", version = "5.1.0" }
postgresql = { module = "org.postgresql:postgresql", version = "42.7.5" }
pgvector = { module = "com.pgvector:pgvector", version = "0.1.6" }

// build.gradle.kts
dependencies {
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.pgvector)
}
```

### 10.3 Rollback Strategy

1. Revert `embedding.provider` to `openai` and `vector_db.provider` to `qdrant` in config
2. Existing Qdrant/OpenAI code paths preserved — backward compatible
3. New DB tables can be dropped without affecting base system
4. New MCP tools simply won't be registered if code is reverted


---

## 11. Implementation Checklist

### 11.1 New Files to Create

| # | File Path | Type | Description | FSD Source |
|---|-----------|------|-------------|------------|
| 1 | `src/main/kotlin/.../db/DatabaseModule.kt` | Infrastructure | HikariCP DataSource factory | §6.3 |
| 2 | `src/main/kotlin/.../embedding/OllamaEmbeddingService.kt` | Implementation | Ollama embedding client | UC-01, BR-01..06 |
| 3 | `src/main/kotlin/.../embedding/LmStudioEmbeddingService.kt` | Implementation | LMStudio embedding client | UC-01, BR-01..06 |
| 4 | `src/main/kotlin/.../vectordb/PgVectorDbClient.kt` | Implementation | pgvector hybrid search | UC-02, BR-07..12 |
| 5 | `src/main/kotlin/.../management/ToolManagementService.kt` | Interface | toggle/reset/auto-approve | UC-03..05 |
| 6 | `src/main/kotlin/.../management/ToolManagementServiceImpl.kt` | Implementation | Tool management logic | UC-03..05, BR-13..28 |
| 7 | `src/main/kotlin/.../management/ConfigDbSyncService.kt` | Interface | Config-DB sync | UC-06, BR-29..32 |
| 8 | `src/main/kotlin/.../management/ConfigDbSyncServiceImpl.kt` | Implementation | Diff + sync algorithm | UC-06, BR-29..32 |
| 9 | `src/main/kotlin/.../management/model/ToggleToolRequest.kt` | DTO | toggle_tool input | §3.2 |
| 10 | `src/main/kotlin/.../management/model/ToggleToolResponse.kt` | DTO | toggle_tool output | §3.2 |
| 11 | `src/main/kotlin/.../management/model/ResetToolsRequest.kt` | DTO | reset_tools input | §3.3 |
| 12 | `src/main/kotlin/.../management/model/ResetToolsResponse.kt` | DTO | reset_tools output | §3.3 |
| 13 | `src/main/kotlin/.../management/model/ManageAutoApproveRequest.kt` | DTO | manage_auto_approve input | §3.4 |
| 14 | `src/main/kotlin/.../management/model/ManageAutoApproveResponse.kt` | DTO | manage_auto_approve output | §3.4 |
| 15 | `src/main/kotlin/.../management/model/DisabledState.kt` | Domain | Disabled tools/servers set | §7.2 |
| 16 | `src/main/kotlin/.../management/model/SyncResult.kt` | Domain | Sync operation result | §7.5 |
| 17 | `src/main/kotlin/.../registry/ToolFilterService.kt` | Interface | Tool filtering | UC-07, BR-33..37 |
| 18 | `src/main/kotlin/.../registry/ToolFilterServiceImpl.kt` | Implementation | Allowlist/blocklist logic | UC-07, BR-33..37 |

### 11.2 Existing Files to Modify

| # | File Path | Changes | FSD Source |
|---|-----------|---------|------------|
| 1 | `.../config/OrchestratorConfig.kt` | Add `base_url` to EmbeddingConfig, pgvector fields to VectorDbConfig, `SessionConfig`, `ToolFilterConfig`, extend `UpstreamServerConfig` | §5.2a |
| 2 | `.../di/AppModule.kt` | Conditional DI: embedding provider switch, pgvector binding, new service bindings | §13.6 |
| 3 | `.../model/ErrorCodes.kt` | Add `TOOL_DISABLED`, `CONFIG_WRITE_FAILED`, `CONFIG_CORRUPTED`, `PGVECTOR_UNAVAILABLE`, `SYNC_FAILED` | §10.1 |
| 4 | `.../model/Exceptions.kt` | Add `ToolDisabledException`, `ConfigWriteException` to sealed hierarchy | §5.2b |
| 5 | `.../protocol/McpServerFactory.kt` | Register 3 new tools: toggle_tool, reset_tools, manage_auto_approve | §3.2..3.4 |
| 6 | `.../protocol/McpToolSchemas.kt` | Add schema definitions for 3 new tools | §3.2..3.4 |
| 7 | `.../discovery/ToolDiscoveryServiceImpl.kt` | Use hybrid search (PgVectorDbClient), add toggle filter post-processing | §3.8, §7.4 |
| 8 | `.../execution/ToolExecutionDispatcherImpl.kt` | Add disabled tool check before execution | §3.9, BR-40 |
| 9 | `.../registry/ToolIndexer.kt` | Inject ToolFilterService, apply filter before embedding | §3.7, BR-37 |
| 10 | `build.gradle.kts` / `libs.versions.toml` | Add HikariCP, postgresql, pgvector dependencies | §10.2 |
| 11 | `src/main/resources/application.yml` | Add pgvector, session, base_url config sections | §13.2 |

### 11.3 New Test Files

| # | File Path | Type | Covers |
|---|-----------|------|--------|
| 1 | `src/test/kotlin/.../embedding/OllamaEmbeddingServiceTest.kt` | Unit | TC-01 |
| 2 | `src/test/kotlin/.../embedding/LmStudioEmbeddingServiceTest.kt` | Unit | TC-02 |
| 3 | `src/test/kotlin/.../vectordb/PgVectorDbClientTest.kt` | Unit | TC-04, TC-05 |
| 4 | `src/test/kotlin/.../management/ToolManagementServiceImplTest.kt` | Unit | TC-06..16 |
| 5 | `src/test/kotlin/.../management/ConfigDbSyncServiceImplTest.kt` | Unit | TC-17, TC-18 |
| 6 | `src/test/kotlin/.../registry/ToolFilterServiceImplTest.kt` | Unit | TC-19..21 |
| 7 | `src/test/kotlin/.../it/PgVectorIntegrationTest.kt` | Integration | TC-04, TC-22 (Testcontainers) |
| 8 | `src/test/kotlin/.../it/ToolManagementIntegrationTest.kt` | Integration | TC-06..13, TC-24 |
| 9 | `src/test/kotlin/.../it/ConfigDbSyncIntegrationTest.kt` | Integration | TC-17, TC-18 |
| 10 | `src/test/kotlin/.../e2e/E2eToolManagementApiTest.kt` | E2E | TC-06..16 via MCP protocol |

---

## 12. Testing Strategy

### 12.1 Test Alignment with FSD Test Scenarios

| FSD TC | Test Type | Test File | Key Assertions |
|--------|-----------|-----------|----------------|
| TC-01 | Unit | OllamaEmbeddingServiceTest | Mock HTTP → verify FloatArray[768] |
| TC-02 | Unit | LmStudioEmbeddingServiceTest | Mock HTTP → verify FloatArray[768] |
| TC-03 | Unit | ToolDiscoveryServiceImplTest (existing, extend) | Mock embedding throws → verify keyword fallback |
| TC-04 | Integration | PgVectorIntegrationTest | Testcontainers PostgreSQL → hybrid search |
| TC-05 | Integration | PgVectorIntegrationTest | Upsert same tool twice → verify single record |
| TC-06 | Unit + Integration | ToolManagementServiceImplTest | Toggle tool → verify DB state + find_tools exclusion |
| TC-07 | Unit | ToolManagementServiceImplTest | Toggle server → verify all tools disabled |
| TC-08 | Unit | ToolManagementServiceImplTest | Re-enable → verify tool visible |
| TC-09 | Integration | ToolManagementIntegrationTest | Two session_ids → verify isolation |
| TC-10 | Unit | ToolExecutionDispatcherImplTest (extend) | Disabled tool → ToolDisabledException |
| TC-11 | Unit | ToolManagementServiceImplTest | Reset all → verify 0 disabled |
| TC-12 | Unit | ToolManagementServiceImplTest | Reset server-scoped → verify other servers unchanged |
| TC-13 | Unit | ToolManagementServiceImplTest | Reset + reindex → verify ToolIndexer called |
| TC-14 | Unit | ToolManagementServiceImplTest | Auto-approve add → verify config file updated |
| TC-15 | Unit | ToolManagementServiceImplTest | Auto-approve remove → verify removed from array |
| TC-16 | Integration | ToolManagementIntegrationTest | Concurrent auto-approve → verify no corruption |
| TC-17 | Integration | ConfigDbSyncIntegrationTest | New server in config → verify INSERT |
| TC-18 | Integration | ConfigDbSyncIntegrationTest | Server removed → verify is_active=false |
| TC-19 | Unit | ToolFilterServiceImplTest | Allowlist → verify only listed tools |
| TC-20 | Unit | ToolFilterServiceImplTest | Blocklist → verify listed tools excluded |
| TC-21 | Unit | ToolFilterServiceImplTest | No filter → verify all tools pass |
| TC-22 | Integration | PgVectorIntegrationTest | Fresh DB → verify tables + indexes created |
| TC-23 | Unit | ConfigValidatorTest (extend) | Invalid provider → validation error |
| TC-24 | Integration | ToolManagementIntegrationTest | find_tools with 2/5 disabled → verify 3 returned |

### 12.2 Test Infrastructure

- **Unit tests:** Kotest StringSpec + MockK for all interfaces
- **Integration tests:** Testcontainers with PostgreSQL 16 + pgvector image
- **E2E tests:** Ktor TestHost with full Koin DI (Testcontainers for DB)
- **IntegrationTestBase:** Extend with `ManagementStack` and `PgVectorStack` data classes

---

## 13. Appendix

### 13.1 Glossary

| Term | Definition |
|------|------------|
| pgvector | PostgreSQL extension for vector similarity search |
| HNSW | Hierarchical Navigable Small World — ANN index algorithm |
| Ollama | Local LLM/embedding server (open source) |
| LMStudio | Local LLM/embedding server with OpenAI-compatible API |
| Session ID | Unique identifier per orchestrator instance for toggle isolation |
| tsvector | PostgreSQL full-text search vector type |
| Hybrid Search | Combined vector similarity + keyword (tsvector) search |
| HikariCP | High-performance JDBC connection pool |

### 13.2 Koin DI Bindings (MTO-10)

| Interface | Implementation | Scope | Condition |
|-----------|---------------|-------|-----------|
| `EmbeddingService` | `OllamaEmbeddingService` | Singleton | `embedding.provider == "ollama"` |
| `EmbeddingService` | `LmStudioEmbeddingService` | Singleton | `embedding.provider == "lmstudio"` |
| `EmbeddingService` | `OpenAiEmbeddingService` | Singleton | `embedding.provider == "openai"` |
| `VectorDbClient` | `PgVectorDbClient` | Singleton | `vector_db.provider == "pgvector"` |
| `VectorDbClient` | `QdrantVectorDbClient` | Singleton | `vector_db.provider == "qdrant"` |
| `HikariDataSource` | `DatabaseModule.createDataSource()` | Singleton | Always (when pgvector) |
| `ToolManagementService` | `ToolManagementServiceImpl` | Singleton | Always |
| `ConfigDbSyncService` | `ConfigDbSyncServiceImpl` | Singleton | Always |
| `ToolFilterService` | `ToolFilterServiceImpl` | Singleton | Always |

### 13.3 Configuration Reference (Updated application.yml)

```yaml
orchestrator:
  embedding:
    provider: ollama                    # NEW: ollama | lmstudio | openai
    model: nomic-embed-text
    dimensions: 768
    base_url: http://localhost:11434    # NEW: provider base URL
    # api_key: ${OPENAI_API_KEY}       # Only for openai provider

  vector_db:
    provider: pgvector                  # NEW: pgvector (replaces qdrant/faiss)
    connection_string: postgresql://postgres:postgres@localhost:5432/jira_assistant
    collection_name: tool_embeddings
    hnsw_m: 16                          # NEW
    hnsw_ef_construction: 64            # NEW

  session:
    id: ${HOSTNAME:-orch-default}       # NEW: session ID for toggle isolation
```

### 13.4 Open Questions (from FSD)

| # | Question | TDD Decision |
|---|----------|-------------|
| 1 | Connection pool library | HikariCP 5.1.0 — industry standard, Kotlin-friendly |
| 2 | Hybrid search weight ratio | 70/30 vector/keyword — configurable via constants, benchmark later |
| 3 | Session ID generation | Hostname-based default with config override (`session.id`) |
| 4 | File locking mechanism | `kotlinx.coroutines.sync.Mutex` (in-process) + `java.nio.channels.FileLock` (cross-process) |
| 5 | Embedding cache | Reuse existing cache config from `EmbeddingConfig` (cache_enabled, cache_max_size, cache_ttl_minutes) |
| 6 | VectorDbClient.search() interface | Keep interface unchanged. `PgVectorDbClient` adds `hybridSearch()` as additional method. `ToolDiscoveryServiceImpl` casts to `PgVectorDbClient` when available. |
| 7 | Toggle state caching | In-memory cache with 30s TTL. Direct DB query as fallback. |
| 8 | tsvector language | Hardcoded `'english'` — configurable in future if needed |
| 9 | Concurrent write safety | Mutex + FileLock combination per §7.3 |
| 10 | Ollama model auto-pull | No auto-pull. Fail with clear error message. |
| 11 | HNSW index rebuild | Drop + recreate. Acceptable downtime for local dev tool. |
| 12 | findTools() sessionId | Inject via Koin-scoped `SessionConfig.id`. No signature change. |

### 13.5 Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Architecture Overview | [architecture.png](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/architecture.png) | [architecture.drawio](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/architecture.drawio) |
| 2 | Component Diagram | [component.png](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/component.png) | [component.drawio](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/component.drawio) |
| 3 | Class Diagram | [class-diagram.png](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/class-diagram.png) | [class-diagram.drawio](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/class-diagram.drawio) |
| 4 | ER Diagram | [er-diagram.png](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/er-diagram.png) | [er-diagram.drawio](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/er-diagram.drawio) |
| 5 | Package Structure | [package-structure.png](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/package-structure.png) | [package-structure.drawio](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/package-structure.drawio) |

### 13.6 Dual Operational Mode Implementation

#### 13.6.1 Mode Detection Logic (Main.kt)

The application entry point detects the transport mode based on the `orchestrator.server.protocol` configuration.

```kotlin
fun main(args: Array<String>) {
    val koinApp = initKoin()
    val config = koinApp.koin.get<OrchestratorConfig>()
    
    if (config.server.protocol == "sse") {
        startSseServer(koinApp)
    } else {
        startStdioServer(koinApp)
    }
}
```

#### 13.6.2 Standalone Mode (SSE)

- **Engine:** Ktor Server with Netty/CIO.
- **Route:** `POST /sse` - handles incoming SSE connections.
- **State:** Persistent connection maintained per client.

#### 13.6.3 Local Bridge Mode (Stdio)

- **Engine:** MCP SDK `StdioServerTransport`.
- **Stream:** `System.in` / `System.out`.
- **Initialization:** Ephemeral connection, context-less per process.

### 13.7 Configuration Mapping

| Component | Standalone (SSE) | Local Bridge (Stdio) |
|-----------|------------------|----------------------|
| **Orchestrator Config** | `application.yml` | `application.yml` (default) or Environment Variables |
| **Upstream Config** | `mcp-servers.json` | `mcp-servers.json` |
| **Port Mapping** | `orchestrator.server.port` | Ignored |
