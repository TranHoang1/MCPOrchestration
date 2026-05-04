# Functional Specification Document (FSD)

## MCP Tool Orchestration — MTO-10: Upgrade MCP Orchestrator: Local Embedding, pgvector, Tool Management & Auto-Approve

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-10 |
| Title | Upgrade MCP Orchestrator: Local Embedding, pgvector, Tool Management & Auto-Approve |
| Author | BA Agent |
| Version | 1.2 |
| Date | 2026-05-03 |
| Status | TA Enriched |
| Related BRD | BRD-v1-MTO-10.docx |
| Related FSD | FSD-v1-MTO-10.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-03 | BA Agent | Initial draft — auto-generated from BRD and Jira ticket MTO-10 with code intelligence |
| 1.1 | 2026-05-03 | TA Agent | Technical enrichment — API contracts, pseudocode, codebase verification, integration details, NFR quantification, draw.io diagrams |
| 1.3 | 2026-05-04 | BA Agent | Added Dual Operational Modes technical specification (Standalone HTTP/SSE vs Local Bridge Stdio). |

---

## 1. Introduction

### 1.1 Purpose

This Functional Specification Document (FSD) describes **how** the MCP Orchestrator Server will implement the upgrade requirements defined in BRD-v1-MTO-10. It provides detailed use cases, data models, API contracts, integration specifications, and processing logic for:

1. Local Embedding Service (Ollama / LMStudio)
2. PostgreSQL + pgvector vector storage
3. Runtime Tool Management (`toggle_tool`, `reset_tools`)
4. Auto-Approve Management (`manage_auto_approve`)
5. Config-DB Sync on startup
6. Tool Filtering from configuration
7. Dual Operational Modes (Standalone HTTP/SSE vs Local Bridge Stdio)

### 1.2 Scope

**In Scope:**
- Detailed use cases with main, alternative, and exception flows for all 7 functional requirements from BRD
- API contracts for 3 new MCP tools and 2 enhanced existing tools
- Database schema for PostgreSQL pgvector tables (`tool_embeddings`, `tool_toggle_state`, `server_config`)
- Integration specifications for Ollama, LMStudio, and PostgreSQL pgvector
- State management and lifecycle diagrams for tool toggle states
- Error handling matrix with error codes, severity, and recovery actions
- Processing logic for startup sync, tool indexing, and config file management

**Out of Scope:**
- UI/frontend implementation
- Migration of existing Qdrant data
- Changes to upstream MCP server protocols
- Authentication/authorization for MCP tool calls
- LLM inference capabilities

### 1.3 Definitions & Acronyms

| Term | Definition |
|------|------------|
| MCP | Model Context Protocol — standard protocol for AI tool integration |
| pgvector | PostgreSQL extension for vector similarity search |
| HNSW | Hierarchical Navigable Small World — approximate nearest neighbor index algorithm |
| tsvector | PostgreSQL full-text search vector type for keyword matching |
| Ollama | Local LLM/embedding server supporting various open-source models |
| LMStudio | Desktop application for running local LLMs with OpenAI-compatible API |
| session_id | Unique UUID generated per orchestrator instance for state isolation |
| Upstream MCP Server | External MCP server providing actual tools (e.g., Jira, filesystem) |
| Koin | Kotlin dependency injection framework used in the project |
| Ktor | Kotlin async web framework used for HTTP transport |
| kaml | Kotlin YAML serialization library used for config parsing |

<!-- TA enrichment -->
| HikariCP | High-performance JDBC connection pool for PostgreSQL |
| Cosine Similarity | Vector distance metric: `1 - (a <=> b)` in pgvector; range 0.0–1.0 |
| GIN Index | Generalized Inverted Index — PostgreSQL index type for full-text search |
| Atomic Rename | File write pattern: write to `.tmp` then `rename()` for crash safety |
| session_id (UUID) | Generated once per orchestrator process via `java.util.UUID.randomUUID()` |
| CallToolResult | MCP SDK type (`io.modelcontextprotocol.kotlin.sdk.types.CallToolResult`) returned by tool handlers |
| ToolSchema | MCP SDK type (`io.modelcontextprotocol.kotlin.sdk.types.ToolSchema`) defining tool input JSON Schema |

### 1.4 References

| Document | Location |
|----------|----------|
| BRD | documents/MTO-10/BRD.md |
| MCP Protocol Specification | https://modelcontextprotocol.io/specification |
| pgvector Documentation | https://github.com/pgvector/pgvector |
| Ollama API Reference | https://github.com/ollama/ollama/blob/main/docs/api.md |
| LMStudio OpenAI Compatibility | https://lmstudio.ai/docs/api |
| Project Structure | .analysis/code-intelligence/project-structure.md |

---

## 2. System Overview

### 2.1 System Context Diagram

![System Context](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/system-context.png)

*[Edit in draw.io](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/system-context.drawio)*

The MCP Orchestrator Server sits between AI clients (Claude, Cursor, etc.) and upstream MCP tool servers. It provides semantic tool discovery via local embeddings stored in PostgreSQL pgvector, dynamic tool execution with routing, and runtime tool management.

**External Actors & Systems:**

| Actor / System | Type | Interaction |
|---------------|------|-------------|
| AI Client | Actor | Calls `find_tools`, `execute_dynamic_tool` via MCP protocol |
| Operator | Actor | Calls `toggle_tool`, `reset_tools`, `manage_auto_approve` via MCP protocol |
| Upstream MCP Servers | External System | Provide actual tools via `tools/list` and `tools/call` |
| PostgreSQL + pgvector | Database | Stores tool embeddings, toggle states, server config |
| Ollama | External Service | Generates text embeddings via REST API (`/api/embeddings`) |
| LMStudio | External Service | Generates text embeddings via OpenAI-compatible API (`/v1/embeddings`) |
| mcp-servers.json | Config File | Source of truth for server configuration and auto-approve settings |

### 2.2 System Architecture

![FSD Architecture](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/fsd-architecture.png)

The system follows a layered architecture with clear separation of concerns:

**Layer 1 — Protocol Layer** (`com.orchestrator.mcp.protocol`)
- `McpServerFactory` creates the MCP SDK `Server` instance
- `McpToolRegistrar` registers all 5 MCP tools (2 existing + 3 new)
- `McpToolSchemas` defines JSON Schema for tool parameters

**Layer 2 — Service Layer** (`com.orchestrator.mcp.discovery`, `execution`)
- `ToolDiscoveryServiceImpl` — semantic + keyword hybrid search (enhanced with pgvector)
- `ToolExecutionDispatcherImpl` — route to upstream with disabled-tool check (new)
- `ToolToggleService` (new) — manage enable/disable state per session
- `AutoApproveService` (new) — manage auto-approve with config file persistence

**Layer 3 — Infrastructure Layer** (`com.orchestrator.mcp.embedding`, `vectordb`, `registry`)
- `OllamaEmbeddingService` (new) — local embedding via Ollama API
- `LmStudioEmbeddingService` (new) — local embedding via LMStudio OpenAI-compatible API
- `PgVectorDbClient` (new) — PostgreSQL pgvector for vector storage + hybrid search
- `ToolRegistryImpl` — in-memory tool registry (existing, unchanged)
- `ConfigDbSyncService` (new) — sync mcp-servers.json to PostgreSQL on startup

**Layer 4 — Data Layer** (PostgreSQL)
- `tool_embeddings` table — vector storage with HNSW index
- `tool_toggle_state` table — session-scoped toggle states
- `server_config` table — mirrored server configuration

**Existing components preserved (backward compatible):**
- `OpenAiEmbeddingService` — still available when `provider=openai`
- `QdrantVectorDbClient` — still available when `vector_db.provider=qdrant`
- `FaissVectorDbClient` — still available as local fallback

---

## 3. Functional Requirements

### 3.1 Feature: Local Embedding Service (Ollama / LMStudio)

**Source:** BRD Story 1 (FR-1)

#### 3.1.1 Description

Replace the paid OpenAI embedding dependency with local alternatives. Implement two new `EmbeddingService` implementations — `OllamaEmbeddingService` and `LmStudioEmbeddingService` — that conform to the existing interface at `com.orchestrator.mcp.embedding.EmbeddingService`. The provider is selected via `EmbeddingConfig.provider` field in `application.yml`. The existing `OpenAiEmbeddingService` remains available for backward compatibility.

#### 3.1.2 Use Case

**Use Case ID:** UC-1
**Actor:** System (ToolIndexer during startup)
**Preconditions:** Embedding provider is configured in `application.yml`; local service (Ollama/LMStudio) is running
**Postconditions:** Tool descriptions are converted to embedding vectors of configured dimensions

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | ToolIndexer | | Calls `embeddingService.generateEmbeddings(toolDescriptions)` |
| 2 | | EmbeddingService | Reads `EmbeddingConfig.provider` to determine implementation |
| 3 | | OllamaEmbeddingService | Sends POST to `http://localhost:11434/api/embeddings` with `{model, prompt}` |
| 4 | | Ollama | Returns `{embedding: [float...]}` vector |
| 5 | | OllamaEmbeddingService | Validates vector dimensions match `EmbeddingConfig.dimensions` (768) |
| 6 | | OllamaEmbeddingService | Returns `FloatArray` to caller |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-1.1 | `provider=lmstudio` | Step 3: POST to `http://localhost:1234/v1/embeddings` with OpenAI-compatible format `{model, input}`. Step 4: Parse OpenAI-format response `{data: [{embedding: [...]}]}` |
| AF-1.2 | `provider=openai` | Step 3: Use existing `OpenAiEmbeddingService` (no change). Requires `api_key` in config |
| AF-1.3 | Custom `api_url` configured | Step 3: Use `EmbeddingConfig.apiUrl` instead of default base URL for the provider |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1.1 | Local service unreachable (connection refused) | Log ERROR with provider name and URL. Throw `EmbeddingServiceException(cause=ConnectException)`. ToolIndexer falls back to keyword-only indexing |
| EF-1.2 | Invalid response format (missing `embedding` field) | Log ERROR with raw response body (truncated to 500 chars). Throw `EmbeddingServiceException` |
| EF-1.3 | Dimension mismatch (returned vector size ≠ configured) | Log WARN with expected vs actual dimensions. Truncate if larger, zero-pad if smaller. Return adjusted vector |
| EF-1.4 | HTTP timeout (>30s) | Log ERROR. Throw `EmbeddingServiceException(cause=SocketTimeoutException)` |

#### 3.1.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-1.1 | Provider must be one of: `ollama`, `lmstudio`, `openai` | BRD Story 1 Validation |
| BR-1.2 | `api_key` is required when `provider=openai`, optional otherwise | BRD Story 1 Validation |
| BR-1.3 | Default dimensions = 768; valid range 1–4096 | BRD Story 1 Data Fields |
| BR-1.4 | `EmbeddingService` interface contract must not change | BRD Story 1 Req #4 |
| BR-1.5 | Health check (`isHealthy()`) must verify the local service is reachable | BRD Story 1 AC #4 |

#### 3.1.4 Data Specifications

**Input Data (Ollama API):**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| model | String | Y | Non-empty, max 100 chars | Ollama model name (e.g., `nomic-embed-text`) |
| prompt | String | Y | Non-empty, max 8192 chars | Text to embed |

**Output Data (Ollama API):**

| Field | Type | Description |
|-------|------|-------------|
| embedding | float[] | Vector of `dimensions` length |

**Input Data (LMStudio API — OpenAI-compatible):**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| model | String | Y | Non-empty | Model identifier |
| input | String or String[] | Y | Non-empty | Text(s) to embed |

**Output Data (LMStudio API):**

| Field | Type | Description |
|-------|------|-------------|
| data[].embedding | float[] | Vector of `dimensions` length |
| usage.prompt_tokens | int | Token count for billing/monitoring |

**Configuration Data (`EmbeddingConfig` — existing class, extended):**

| Field | Type | Required | Default | Validation | Description |
|-------|------|----------|---------|------------|-------------|
| provider | String | Y | `openai` | `ollama\|lmstudio\|openai` | Embedding provider selection |
| model | String | Y | `text-embedding-3-small` | Non-empty | Model name |
| api_key | String | N | `""` | Required if provider=openai | API key |
| dimensions | Int | N | 768 | 1–4096 | Vector dimensions |
| api_url | String | N | (per provider default) | Valid HTTP(S) URL | Override base URL |
| cache_enabled | Boolean | N | true | — | Enable embedding cache |
| cache_max_size | Int | N | 100 | 1–10000 | Max cached embeddings |
| cache_ttl_minutes | Int | N | 5 | 1–1440 | Cache TTL |

#### 3.1.5 UI Specifications

Not applicable — this feature is a backend service with no UI.

#### 3.1.6 API Specifications

**Internal API — No MCP tool exposed.** This feature provides an internal `EmbeddingService` implementation consumed by `ToolIndexer` and `ToolDiscoveryServiceImpl`.

**Ollama REST API (consumed):**

| Attribute | Value |
|-----------|-------|
| Method | POST |
| URL | `http://localhost:11434/api/embeddings` |
| Content-Type | `application/json` |
| Auth | None |

**LMStudio REST API (consumed):**

| Attribute | Value |
|-----------|-------|
| Method | POST |
| URL | `http://localhost:1234/v1/embeddings` |
| Content-Type | `application/json` |
| Auth | None (or Bearer token if configured) |

<!-- TA enrichment -->
**Ollama Request Body Schema:**

```json
{
  "model": "nomic-embed-text",
  "prompt": "Search for Jira issues by key"
}
```

**Ollama Response Body Schema:**

```json
{
  "embedding": [0.0123, -0.0456, 0.0789, "... (768 floats)"]
}
```

**LMStudio Request Body Schema (OpenAI-compatible):**

```json
{
  "model": "nomic-embed-text",
  "input": "Search for Jira issues by key"
}
```

**LMStudio Response Body Schema:**

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "index": 0,
      "embedding": [0.0123, -0.0456, 0.0789, "... (768 floats)"]
    }
  ],
  "model": "nomic-embed-text",
  "usage": { "prompt_tokens": 8, "total_tokens": 8 }
}
```

**Health Check Endpoints:**

| Provider | Method | Path | Expected Response |
|----------|--------|------|-------------------|
| Ollama | GET | `/api/tags` | 200 OK with JSON body |
| LMStudio | GET | `/v1/models` | 200 OK with model list |
| OpenAI | GET | `/v1/models` | 200 OK (with valid API key) |

**Pseudocode — EmbeddingService Provider Selection:**
[Implements: Story #1]

```kotlin
// com.orchestrator.mcp.embedding — provider factory logic in AppModule.kt
// Current DI binding (AppModule.kt line ~45) hardcodes OpenAiEmbeddingService.
// Must be changed to a when-branch on config.orchestrator.embedding.provider.

fun createEmbeddingService(config: EmbeddingConfig, httpClient: HttpClient): EmbeddingService {
    return when (config.provider) {
        "ollama" -> OllamaEmbeddingService(
            httpClient = httpClient,
            baseUrl = config.apiUrl.ifEmpty { "http://localhost:11434" },
            model = config.model,
            dimensions = config.dimensions
        )
        "lmstudio" -> LmStudioEmbeddingService(
            httpClient = httpClient,
            baseUrl = config.apiUrl.ifEmpty { "http://localhost:1234" },
            model = config.model,
            dimensions = config.dimensions
        )
        "openai" -> OpenAiEmbeddingService(
            httpClient = httpClient,
            apiKey = config.apiKey,
            model = config.model,
            dimensions = config.dimensions
        )
        else -> throw ConfigException("Unknown embedding provider: '${config.provider}'. Must be one of: ollama, lmstudio, openai")
    }
}
```

**Pseudocode — OllamaEmbeddingService.generateEmbedding:**

```kotlin
// OllamaEmbeddingService implements EmbeddingService interface (3 methods)
override suspend fun generateEmbedding(text: String): FloatArray {
    val response = httpClient.post("$baseUrl/api/embeddings") {
        contentType(ContentType.Application.Json)
        setBody(buildJsonObject {
            put("model", model)
            put("prompt", text)
        })
    }
    if (!response.status.isSuccess()) {
        throw EmbeddingServiceException(RuntimeException("Ollama returned ${response.status}"))
    }
    val body = response.body<JsonObject>()
    val embedding = body["embedding"]?.jsonArray
        ?: throw EmbeddingServiceException(RuntimeException("Missing 'embedding' field in Ollama response"))
    val vector = embedding.map { it.jsonPrimitive.float }.toFloatArray()
    // Dimension adjustment (BR-1.3, EF-1.3)
    return adjustDimensions(vector, dimensions)
}

private fun adjustDimensions(vector: FloatArray, target: Int): FloatArray {
    return when {
        vector.size == target -> vector
        vector.size > target -> {
            logger.warn("Truncating vector from ${vector.size} to $target dimensions")
            vector.copyOf(target)
        }
        else -> {
            logger.warn("Zero-padding vector from ${vector.size} to $target dimensions")
            vector.copyOf(target) // copyOf zero-pads when target > source
        }
    }
}
```

> **TA Note:** The existing `EmbeddingConfig` data class (verified in `OrchestratorConfig.kt`) is missing the `api_url` field. It must be added as `@SerialName("api_url") val apiUrl: String = ""`. The existing `provider` field defaults to `"openai"` which preserves backward compatibility.

---

### 3.2 Feature: PostgreSQL + pgvector Vector Storage

**Source:** BRD Story 2 (FR-2)

#### 3.2.1 Description

Implement `PgVectorDbClient` conforming to the existing `VectorDbClient` interface at `com.orchestrator.mcp.vectordb.VectorDbClient`. This replaces Qdrant as the default vector storage, reusing the existing PostgreSQL instance at `postgresql://postgres:postgres@localhost:5432/jira_assistant`. The implementation adds hybrid search combining cosine vector similarity with PostgreSQL tsvector keyword matching.

#### 3.2.2 Use Case

**Use Case ID:** UC-2
**Actor:** System (ToolIndexer, ToolDiscoveryService)
**Preconditions:** PostgreSQL 16+ running with pgvector extension installed; `vector_db.provider=pgvector` in config
**Postconditions:** Tool embeddings stored in `tool_embeddings` table with HNSW index; hybrid search operational

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Application startup | PgVectorDbClient | Connects to PostgreSQL using configured connection string |
| 2 | | PgVectorDbClient | Runs `CREATE EXTENSION IF NOT EXISTS vector` |
| 3 | | PgVectorDbClient | Creates `tool_embeddings` table if not exists (see Section 4.2) |
| 4 | | PgVectorDbClient | Creates HNSW index on `embedding` column if not exists |
| 5 | | PgVectorDbClient | Creates GIN index on `search_vector` (tsvector) column if not exists |
| 6 | ToolIndexer | PgVectorDbClient | Calls `upsert(collectionName, points)` to store tool embeddings |
| 7 | | PgVectorDbClient | Upserts rows using `INSERT ... ON CONFLICT (server_name, tool_name) DO UPDATE` |
| 8 | ToolDiscoveryService | PgVectorDbClient | Calls `search(collectionName, vector, limit, threshold)` |
| 9 | | PgVectorDbClient | Executes hybrid query: `ORDER BY (0.7 * vector_score + 0.3 * keyword_score) DESC` |
| 10 | | PgVectorDbClient | Returns `List<SearchResult>` ranked by combined score |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-2.1 | `vector_db.provider=qdrant` | Use existing `QdrantVectorDbClient` — no pgvector involved |
| AF-2.2 | `vector_db.provider=faiss` | Use existing `FaissVectorDbClient` — in-memory fallback |
| AF-2.3 | Embedding vector is null (keyword-only search) | Step 9: Skip vector similarity, use only tsvector `ts_rank` for ranking |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-2.1 | PostgreSQL connection failure | Log ERROR with connection string (masked password). Throw `VectorDbUnavailableException`. System falls back to keyword search |
| EF-2.2 | pgvector extension not installed | Log ERROR: "pgvector extension not found. Run: CREATE EXTENSION vector;". Throw `VectorDbUnavailableException` |
| EF-2.3 | Dimension mismatch on upsert | Log ERROR with expected vs actual dimensions. Throw `InvalidParamsException` — reject the upsert |
| EF-2.4 | HNSW index creation fails (insufficient memory) | Log WARN. Fall back to sequential scan (no index). Performance degraded but functional |

#### 3.2.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-2.1 | `server_name` + `tool_name` must be unique (composite unique constraint) | BRD Story 2 Validation |
| BR-2.2 | Embedding vector dimension must match `EmbeddingConfig.dimensions` | BRD Story 2 Validation |
| BR-2.3 | Hybrid search weight: 70% vector similarity + 30% keyword relevance | FSD design decision |
| BR-2.4 | Table and extension auto-created on first run if missing | BRD Story 2 AC #4 |
| BR-2.5 | Existing Qdrant/FAISS implementations remain available | BRD Story 2 AC #5 |

#### 3.2.4 Data Specifications

**VectorPoint (input to upsert — existing DTO, extended):**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| id | String (UUID) | Y | Valid UUID format | Unique point identifier |
| vector | FloatArray | Y | Length = configured dimensions | Embedding vector |
| payload | Map<String, Any> | N | Valid JSON-serializable | Metadata (server_name, tool_name, description, input_schema) |

**SearchResult (output from search — existing DTO):**

| Field | Type | Description |
|-------|------|-------------|
| id | String | Point UUID |
| score | Float | Combined similarity score (0.0–1.0) |
| payload | Map<String, Any> | Tool metadata |

**Hybrid Search Query Parameters:**

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| vector | FloatArray | N | null | Query embedding vector |
| keyword | String | N | null | Keyword search text |
| limit | Int | Y | 5 | Max results |
| scoreThreshold | Float | Y | 0.7 | Minimum combined score |
| excludeDisabled | Boolean | N | true | Exclude disabled tools (from toggle state) |
| sessionId | String | N | current session | Session for toggle state lookup |

#### 3.2.5 UI Specifications

Not applicable — backend storage layer.

#### 3.2.6 API Specifications

**Internal API — No MCP tool exposed.** `PgVectorDbClient` implements `VectorDbClient` interface consumed by `ToolIndexer` and `ToolDiscoveryServiceImpl`.

**PostgreSQL Connection (consumed):**

| Attribute | Value |
|-----------|-------|
| Protocol | JDBC / PostgreSQL wire protocol |
| Connection String | `postgresql://postgres:postgres@localhost:5432/jira_assistant` |
| Driver | `org.postgresql:postgresql` (JDBC) |
| Pool | HikariCP (recommended) |

<!-- TA enrichment -->
> **TA Note:** The existing `VectorDbClient` interface (verified in `vectordb/VectorDbClient.kt`) has 5 methods: `createCollection`, `upsert`, `search`, `delete`, `isHealthy`. The `search` method signature is `search(collectionName, vector, limit, scoreThreshold)` — it does NOT accept a keyword parameter. For hybrid search, `PgVectorDbClient` must either: (a) extend the interface with an overloaded `search` method accepting `keyword: String?`, or (b) extract the keyword from the query vector context. **Recommended approach:** add a new interface method with default implementation to preserve backward compatibility.

**Proposed VectorDbClient Extension:**

```kotlin
// Add to VectorDbClient interface — default impl preserves backward compat
interface VectorDbClient {
    // ... existing methods unchanged ...

    /**
     * Hybrid search combining vector similarity and keyword matching.
     * Default implementation delegates to vector-only search.
     */
    suspend fun hybridSearch(
        collectionName: String,
        vector: FloatArray?,
        keyword: String?,
        limit: Int,
        scoreThreshold: Float,
        excludeToolNames: Set<String> = emptySet()
    ): List<SearchResult> {
        // Default: fall back to vector-only search
        return if (vector != null) search(collectionName, vector, limit, scoreThreshold)
        else emptyList()
    }
}
```

**Pseudocode — PgVectorDbClient.hybridSearch:**
[Implements: Story #2]

```kotlin
class PgVectorDbClient(
    private val dataSource: HikariDataSource
) : VectorDbClient {

    override suspend fun createCollection(name: String, dimensions: Int) {
        dataSource.connection.use { conn ->
            conn.createStatement().execute("CREATE EXTENSION IF NOT EXISTS vector")
            conn.prepareStatement("""
                CREATE TABLE IF NOT EXISTS $name (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    server_name VARCHAR(255) NOT NULL,
                    tool_name VARCHAR(255) NOT NULL,
                    description TEXT NOT NULL,
                    embedding vector($dimensions),
                    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('english', description)) STORED,
                    payload JSONB,
                    input_schema JSONB,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    UNIQUE (server_name, tool_name)
                )
            """.trimIndent()).execute()
            // Create indexes
            conn.createStatement().execute("""
                CREATE INDEX IF NOT EXISTS idx_${name}_hnsw ON $name 
                USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)
            """.trimIndent())
            conn.createStatement().execute("""
                CREATE INDEX IF NOT EXISTS idx_${name}_search ON $name USING gin (search_vector)
            """.trimIndent())
        }
    }

    override suspend fun upsert(collectionName: String, points: List<VectorPoint>) {
        dataSource.connection.use { conn ->
            val stmt = conn.prepareStatement("""
                INSERT INTO $collectionName (id, server_name, tool_name, description, embedding, payload, input_schema, updated_at)
                VALUES (?::uuid, ?, ?, ?, ?::vector, ?::jsonb, ?::jsonb, NOW())
                ON CONFLICT (server_name, tool_name) DO UPDATE SET
                    description = EXCLUDED.description,
                    embedding = EXCLUDED.embedding,
                    payload = EXCLUDED.payload,
                    input_schema = EXCLUDED.input_schema,
                    updated_at = NOW()
            """.trimIndent())
            for (point in points) {
                stmt.setString(1, point.id)
                stmt.setString(2, point.payload["server_name"])
                stmt.setString(3, point.payload["name"])
                stmt.setString(4, point.payload["description"])
                stmt.setString(5, point.vector.joinToString(",", "[", "]"))
                stmt.setString(6, /* remaining payload as JSON */)
                stmt.setString(7, point.schemaPayload?.toString())
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    override suspend fun hybridSearch(
        collectionName: String,
        vector: FloatArray?,
        keyword: String?,
        limit: Int,
        scoreThreshold: Float,
        excludeToolNames: Set<String>
    ): List<SearchResult> {
        // See Section 5.3 for full SQL query
        // Combines: 0.7 * cosine_similarity + 0.3 * ts_rank
        // Excludes tools in excludeToolNames (disabled tools)
    }
}
```

> **TA Note:** The existing `VectorPoint.payload` is typed as `Map<String, String>` (verified in `vectordb/model/VectorPoint.kt`). The `id` field uses format `"$serverName::$toolName"` (verified in `ToolIndexer.kt` line ~95). The `PgVectorDbClient` must parse this composite ID or use `server_name`/`tool_name` from payload for the UPSERT conflict target.

> **TA Note:** The existing `VectorDbConfig` data class is missing `connection_string` field. Must add: `@SerialName("connection_string") val connectionString: String = ""`. The DI binding in `AppModule.kt` (line ~55) must add a `when` branch for `provider == "pgvector"`.

---

### 3.3 Feature: Toggle Tool (Enable/Disable at Runtime)

**Source:** BRD Story 3 (FR-3)

#### 3.3.1 Description

New MCP tool `toggle_tool` allows operators to enable or disable specific tools or entire servers at runtime without restarting the orchestrator. Toggle state is session-scoped (per orchestrator instance, identified by `session_id`) and persisted in PostgreSQL `tool_toggle_state` table. Disabled tools are excluded from `find_tools` results and blocked from `execute_dynamic_tool`.

#### 3.3.2 Use Case

**Use Case ID:** UC-3
**Actor:** Operator (via MCP client)
**Preconditions:** Orchestrator is running; tool/server exists in registry; PostgreSQL is connected
**Postconditions:** Tool toggle state updated in DB; subsequent `find_tools`/`execute_dynamic_tool` respect new state

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Operator | | Calls `toggle_tool(tool_name="jira_get_issue", enabled=false)` |
| 2 | | McpToolRegistrar | Validates input parameters |
| 3 | | ToolToggleService | Looks up tool in `ToolRegistry` to verify it exists |
| 4 | | ToolToggleService | Upserts row in `tool_toggle_state` table: `(session_id, tool_name, server_name, enabled=false)` |
| 5 | | ToolToggleService | Updates in-memory toggle cache |
| 6 | | McpToolRegistrar | Returns success: `{"toggled": "jira_get_issue", "enabled": false, "scope": "tool"}` |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-3.1 | `server_name` provided instead of `tool_name` | Step 3: Look up all tools for server in `ToolRegistry.getToolsByServer()`. Step 4: Upsert toggle state for ALL tools of that server. Return `{"toggled": "atlassian", "enabled": false, "scope": "server", "tools_affected": 15}` |
| AF-3.2 | Both `tool_name` and `server_name` provided | `tool_name` takes precedence — toggle only the specific tool (ignore `server_name`) |
| AF-3.3 | Re-enabling a previously disabled tool | Step 4: Update `enabled=true` in DB. Step 5: Remove from disabled set in memory cache |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-3.1 | Neither `tool_name` nor `server_name` provided | Return `InvalidParamsException`: "At least one of tool_name or server_name is required" |
| EF-3.2 | `tool_name` not found in registry | Return `ToolNotFoundException(tool_name)` |
| EF-3.3 | `server_name` not found in registry | Return `ToolNotFoundException(server_name)` |
| EF-3.4 | DB write failure | Log ERROR. Apply toggle in-memory only. Return success with warning: `{"warning": "State saved in-memory only — DB write failed"}` |

#### 3.3.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-3.1 | At least one of `tool_name` or `server_name` must be non-null | BRD Story 3 Validation |
| BR-3.2 | If both provided, `tool_name` takes precedence | BRD Story 3 Validation |
| BR-3.3 | Toggle state is session-scoped — does not affect other orchestrator instances | BRD Story 3 Req #6 |
| BR-3.4 | Toggle state persists in DB but NOT in config file | BRD Story 3 Req #7 |
| BR-3.5 | Disabled tools are excluded from `find_tools` search results | BRD Story 3 Req #4 |
| BR-3.6 | Disabled tools return error on `execute_dynamic_tool` | BRD Story 3 Req #4 |

#### 3.3.4 Data Specifications

**Input Data (MCP Tool Parameters):**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| tool_name | String | N* | Existing tool in registry | Specific tool to toggle |
| server_name | String | N* | Existing server in registry | Toggle all tools for server |
| enabled | Boolean | Y | true or false | Enable or disable |

*At least one of `tool_name` or `server_name` required.

**Output Data (MCP Tool Response):**

| Field | Type | Description |
|-------|------|-------------|
| toggled | String | Tool name or server name that was toggled |
| enabled | Boolean | New state |
| scope | String | `"tool"` or `"server"` |
| tools_affected | Int | Number of tools affected (for server scope) |
| warning | String? | Present if DB write failed |

#### 3.3.5 UI Specifications

Not applicable — MCP tool interface only.

#### 3.3.6 API Specifications

**MCP Tool: `toggle_tool`**

| Attribute | Value |
|-----------|-------|
| Tool Name | `toggle_tool` |
| Protocol | MCP `tools/call` |
| Transport | stdio / HTTP (same as orchestrator) |

**Parameters JSON Schema:**

```json
{
  "type": "object",
  "properties": {
    "tool_name": {
      "type": "string",
      "description": "Specific tool to enable/disable"
    },
    "server_name": {
      "type": "string",
      "description": "Toggle all tools for this server"
    },
    "enabled": {
      "type": "boolean",
      "description": "true = enable, false = disable"
    }
  },
  "required": ["enabled"]
}
```

**Success Response:**

```json
{
  "content": [{
    "type": "text",
    "text": "{\"toggled\": \"jira_get_issue\", \"enabled\": false, \"scope\": \"tool\"}"
  }]
}
```

**Error Codes:**

| Code | Message | Description |
|------|---------|-------------|
| INVALID_PARAMS | At least one of tool_name or server_name is required | Neither parameter provided |
| TOOL_NOT_FOUND | Tool 'xyz' is not registered | tool_name not in registry |
| TOOL_NOT_FOUND | Server 'xyz' has no registered tools | server_name not in registry |

<!-- TA enrichment -->
**MCP SDK Registration Pattern:**
[Implements: Story #3]

The existing `McpServerFactory` (verified in `protocol/McpServerFactory.kt`) registers tools via `server.addTool()`. The 3 new tools follow the same pattern. The factory must be extended to accept `ToolToggleService`, `AutoApproveService`, and `ToolIndexer` as constructor dependencies.

```kotlin
// In McpServerFactory — new method following existing registerFindTools pattern
private fun registerToggleTool(server: Server) {
    server.addTool(
        name = "toggle_tool",
        description = "Enable or disable a specific tool or all tools for a server at runtime. " +
            "Disabled tools are hidden from find_tools and blocked from execute_dynamic_tool.",
        inputSchema = toggleToolSchema()
    ) { request ->
        handleToggleTool(request.arguments)
    }
}

private suspend fun handleToggleTool(arguments: JsonObject?): CallToolResult {
    val toolName = arguments?.get("tool_name")?.jsonPrimitive?.contentOrNull
    val serverName = arguments?.get("server_name")?.jsonPrimitive?.contentOrNull
    val enabled = arguments?.get("enabled")?.jsonPrimitive?.booleanOrNull
        ?: return errorResult("INVALID_PARAMS", "'enabled' parameter is required")

    if (toolName == null && serverName == null) {
        return errorResult("INVALID_PARAMS", "At least one of tool_name or server_name is required")
    }

    return try {
        val result = toolToggleService.toggle(
            toolName = toolName,
            serverName = serverName,
            enabled = enabled,
            sessionId = currentSessionId
        )
        CallToolResult(content = listOf(TextContent(text = json.encodeToString(result))))
    } catch (e: McpOrchestratorException) {
        errorResult(e.errorCode, e.message ?: "Unknown error")
    }
}
```

**Pseudocode — ToolToggleService.toggle:**

```kotlin
class ToolToggleService(
    private val toolRegistry: ToolRegistry,
    private val dataSource: HikariDataSource  // nullable if DB unavailable
) {
    // In-memory cache: sessionId -> Set<disabledToolName>
    private val disabledTools = ConcurrentHashMap<UUID, MutableSet<String>>()

    suspend fun toggle(toolName: String?, serverName: String?, enabled: Boolean, sessionId: UUID): ToggleResult {
        // Step 1: Resolve target tools
        val targetTools: List<String> = when {
            toolName != null -> {
                toolRegistry.lookupTool(toolName) ?: throw ToolNotFoundException(toolName)
                listOf(toolName)
            }
            serverName != null -> {
                val tools = toolRegistry.getToolsByServer(serverName)
                if (tools.isEmpty()) throw ToolNotFoundException(serverName)
                tools.map { it.name }
            }
            else -> throw InvalidParamsException("At least one of tool_name or server_name is required")
        }

        // Step 2: Update in-memory cache
        val sessionSet = disabledTools.getOrPut(sessionId) { ConcurrentHashMap.newKeySet() }
        if (enabled) sessionSet.removeAll(targetTools.toSet())
        else sessionSet.addAll(targetTools)

        // Step 3: Persist to DB (best-effort)
        var warning: String? = null
        try {
            persistToggleState(sessionId, targetTools, enabled)
        } catch (e: Exception) {
            logger.error("DB write failed for toggle: ${e.message}")
            warning = "State saved in-memory only — DB write failed"
        }

        return ToggleResult(
            toggled = toolName ?: serverName!!,
            enabled = enabled,
            scope = if (toolName != null) "tool" else "server",
            toolsAffected = targetTools.size,
            warning = warning
        )
    }

    fun isDisabled(toolName: String, sessionId: UUID): Boolean {
        return disabledTools[sessionId]?.contains(toolName) == true
    }
}
```

> **TA Note:** The existing `ToolDiscoveryServiceImpl` (verified in `discovery/ToolDiscoveryServiceImpl.kt`) does NOT currently filter disabled tools. It must be modified to accept `ToolToggleService` and call `isDisabled()` before returning results. Similarly, `ToolExecutionDispatcherImpl` (verified in `execution/ToolExecutionDispatcherImpl.kt`) must add a disabled-tool check between the `lookupAndValidate` step and the `getConnection` step.

**New Exception — ToolDisabledException:**

```kotlin
// Add to model/Exceptions.kt sealed hierarchy
class ToolDisabledException(toolName: String) :
    McpOrchestratorException(
        ErrorCodes.TOOL_DISABLED,
        "Tool '$toolName' is currently disabled. Use toggle_tool to re-enable."
    )
```

> **TA Note:** `ErrorCodes.TOOL_DISABLED` must be added to `model/ErrorCodes.kt`. Verified current file has 9 constants; add `const val TOOL_DISABLED = "TOOL_DISABLED"`.

---

### 3.4 Feature: Reset Tools

**Source:** BRD Story 4 (FR-4)

#### 3.4.1 Description

New MCP tool `reset_tools` restores all tool toggle states to default (all enabled) for the current session. Optionally scoped to a single server. Optionally triggers re-indexing of tools (re-embed and re-store in pgvector).

#### 3.4.2 Use Case

**Use Case ID:** UC-4
**Actor:** Operator (via MCP client)
**Preconditions:** Orchestrator is running; some tools may be disabled via `toggle_tool`
**Postconditions:** All toggle states cleared (all tools enabled); optionally tools re-indexed

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Operator | | Calls `reset_tools()` with no parameters |
| 2 | | McpToolRegistrar | Validates input (no required params) |
| 3 | | ToolToggleService | Deletes all rows from `tool_toggle_state` WHERE `session_id = current_session` |
| 4 | | ToolToggleService | Clears in-memory toggle cache |
| 5 | | ToolIndexer | Since `reindex=true` (default): re-discovers tools from all upstream servers, re-embeds, re-upserts into pgvector |
| 6 | | McpToolRegistrar | Returns success: `{"reset": "all", "tools_enabled": 45, "reindexed": true}` |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-4.1 | `server_name="atlassian"` provided | Step 3: Delete only rows WHERE `session_id = current AND server_name = 'atlassian'`. Step 5: Re-index only `atlassian` server tools. Return `{"reset": "atlassian", "tools_enabled": 15, "reindexed": true}` |
| AF-4.2 | `reindex=false` | Skip Step 5 entirely. Return `{"reset": "all", "tools_enabled": 45, "reindexed": false}` |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-4.1 | `server_name` not found in registry | Return `ToolNotFoundException(server_name)` |
| EF-4.2 | DB delete failure | Log ERROR. Clear in-memory cache anyway. Return success with warning |
| EF-4.3 | Re-index failure (embedding service down) | Log ERROR. Toggle states still cleared. Return `{"reset": "all", "tools_enabled": 45, "reindexed": false, "warning": "Re-indexing failed: embedding service unavailable"}` |

#### 3.4.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-4.1 | Reset only affects current session's toggle states | BRD Story 4 / BR-3.3 |
| BR-4.2 | `reindex` defaults to `true` if not specified | BRD Story 4 Data Fields |
| BR-4.3 | Reset does NOT modify config file | BRD Persistence Rules |
| BR-4.4 | If `server_name` provided, only that server's tools are reset | BRD Story 4 Req #4 |

#### 3.4.4 Data Specifications

**Input Data:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| server_name | String | N | Existing server in registry | Reset only this server |
| reindex | Boolean | N | Default: true | Re-index tools after reset |

**Output Data:**

| Field | Type | Description |
|-------|------|-------------|
| reset | String | `"all"` or server name |
| tools_enabled | Int | Count of tools now enabled |
| reindexed | Boolean | Whether re-indexing was performed |
| warning | String? | Present if re-index or DB operation failed |

#### 3.4.5 UI Specifications

Not applicable — MCP tool interface only.

#### 3.4.6 API Specifications

**MCP Tool: `reset_tools`**

**Parameters JSON Schema:**

```json
{
  "type": "object",
  "properties": {
    "server_name": {
      "type": "string",
      "description": "Reset only this server's tools (omit for all)"
    },
    "reindex": {
      "type": "boolean",
      "description": "Re-index tools after reset (default: true)",
      "default": true
    }
  },
  "required": []
}
```

**Error Codes:**

| Code | Message | Description |
|------|---------|-------------|
| TOOL_NOT_FOUND | Server 'xyz' has no registered tools | server_name not in registry |

<!-- TA enrichment -->
**Pseudocode — reset_tools handler:**
[Implements: Story #4]

```kotlin
private suspend fun handleResetTools(arguments: JsonObject?): CallToolResult {
    val serverName = arguments?.get("server_name")?.jsonPrimitive?.contentOrNull
    val reindex = arguments?.get("reindex")?.jsonPrimitive?.booleanOrNull ?: true

    // Validate server_name if provided
    if (serverName != null) {
        val tools = toolRegistry.getToolsByServer(serverName)
        if (tools.isEmpty()) throw ToolNotFoundException(serverName)
    }

    // Step 1: Clear toggle states
    val toolsEnabled = toolToggleService.resetAll(
        sessionId = currentSessionId,
        serverName = serverName
    )

    // Step 2: Re-index if requested
    var reindexed = false
    var warning: String? = null
    if (reindex) {
        try {
            if (serverName != null) toolIndexer.indexServer(serverName)
            else toolIndexer.indexAll()
            reindexed = true
        } catch (e: Exception) {
            logger.error("Re-indexing failed: ${e.message}")
            warning = "Re-indexing failed: ${e.message}"
        }
    }

    val result = buildJsonObject {
        put("reset", serverName ?: "all")
        put("tools_enabled", toolsEnabled)
        put("reindexed", reindexed)
        warning?.let { put("warning", it) }
    }
    return CallToolResult(content = listOf(TextContent(text = result.toString())))
}
```

---

### 3.5 Feature: Auto-Approve Management

**Source:** BRD Story 5 (FR-5)

#### 3.5.1 Description

New MCP tool `manage_auto_approve` allows operators to add or remove tools from the auto-approve list at runtime. Unlike `toggle_tool`, this writes immediately to both PostgreSQL and the `mcp-servers.json` config file, making changes persistent across restarts.

#### 3.5.2 Use Case

**Use Case ID:** UC-5
**Actor:** Operator (via MCP client)
**Preconditions:** Orchestrator is running; `mcp-servers.json` is writable; tool/server exists
**Postconditions:** Auto-approve list updated in both DB and config file; changes effective immediately

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Operator | | Calls `manage_auto_approve(tool_name="jira_get_issue", auto_approve=true)` |
| 2 | | McpToolRegistrar | Validates input parameters |
| 3 | | AutoApproveService | Looks up tool in `ToolRegistry` — determines which server owns it |
| 4 | | AutoApproveService | Reads current `mcp-servers.json` into memory |
| 5 | | AutoApproveService | Adds `"jira_get_issue"` to the `autoApprove` array of the owning server |
| 6 | | AutoApproveService | Writes updated JSON to temp file, then atomically renames to `mcp-servers.json` |
| 7 | | AutoApproveService | Upserts auto-approve state in `server_config` table in PostgreSQL |
| 8 | | McpToolRegistrar | Returns success: `{"tool": "jira_get_issue", "server": "atlassian", "auto_approve": true}` |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-5.1 | `auto_approve=false` | Step 5: Remove tool from `autoApprove` array. If array becomes empty, remove the `autoApprove` key entirely |
| AF-5.2 | `server_name` provided instead of `tool_name` | Step 5: If `auto_approve=true`, set `autoApprove: ["*"]` (wildcard) for the server. If `false`, remove `autoApprove` key entirely |
| AF-5.3 | Tool already in auto-approve list and `auto_approve=true` | No-op for config file. Return success with note: `"already_approved": true` |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-5.1 | Neither `tool_name` nor `server_name` provided | Return `InvalidParamsException`: "At least one of tool_name or server_name is required" |
| EF-5.2 | Tool/server not found in registry | Return `ToolNotFoundException` |
| EF-5.3 | Config file write failure (permissions, disk full) | Log ERROR. Update DB only. Return success with `"warning": "Config file not updated — DB only"` |
| EF-5.4 | Config file locked by another process | Retry 3 times with 500ms backoff. If still locked, return error: `"Config file is locked by another process"` |
| EF-5.5 | DB write failure | Log ERROR. Config file still updated. Return success with `"warning": "DB not updated — config file only"` |
| EF-5.6 | Config file is malformed JSON | Log ERROR. Do not overwrite. Return error: `"mcp-servers.json contains invalid JSON — manual fix required"` |

#### 3.5.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-5.1 | At least one of `tool_name` or `server_name` must be non-null | BRD Story 5 Validation |
| BR-5.2 | Config file writes use atomic rename pattern (write temp → rename) | BRD Story 5 AC #3 |
| BR-5.3 | Both DB and config file are updated; partial success is acceptable with warning | BRD Story 5 Error Handling |
| BR-5.4 | Changes take effect immediately without server restart | BRD Story 5 AC #5 |
| BR-5.5 | `autoApprove: ["*"]` means all tools for that server are auto-approved | FSD design decision |

#### 3.5.4 Data Specifications

**Input Data:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| tool_name | String | N* | Existing tool in registry | Specific tool |
| server_name | String | N* | Existing server in registry | All tools for server |
| auto_approve | Boolean | Y | true or false | Add or remove from auto-approve |

*At least one required.

**Output Data:**

| Field | Type | Description |
|-------|------|-------------|
| tool | String? | Tool name (if tool-scoped) |
| server | String | Server name owning the tool |
| auto_approve | Boolean | New auto-approve state |
| already_approved | Boolean? | True if no change was needed |
| warning | String? | Present if partial failure |

#### 3.5.5 UI Specifications

Not applicable — MCP tool interface only.

#### 3.5.6 API Specifications

**MCP Tool: `manage_auto_approve`**

**Parameters JSON Schema:**

```json
{
  "type": "object",
  "properties": {
    "tool_name": {
      "type": "string",
      "description": "Specific tool to set auto-approve for"
    },
    "server_name": {
      "type": "string",
      "description": "Set auto-approve for all tools in this server"
    },
    "auto_approve": {
      "type": "boolean",
      "description": "true = add to auto-approve, false = remove"
    }
  },
  "required": ["auto_approve"]
}
```

**Error Codes:**

| Code | Message | Description |
|------|---------|-------------|
| INVALID_PARAMS | At least one of tool_name or server_name is required | Neither parameter provided |
| TOOL_NOT_FOUND | Tool 'xyz' is not registered | tool_name not in registry |
| TOOL_NOT_FOUND | Server 'xyz' has no registered tools | server_name not in registry |
| CONFIG_INVALID | mcp-servers.json contains invalid JSON | Config file corrupted |

<!-- TA enrichment -->
**Pseudocode — AutoApproveService.updateAutoApprove:**
[Implements: Story #5]

```kotlin
class AutoApproveService(
    private val toolRegistry: ToolRegistry,
    private val configFilePath: String,
    private val dataSource: HikariDataSource?
) {
    private val logger = LoggerFactory.getLogger(AutoApproveService::class.java)
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun updateAutoApprove(
        toolName: String?, serverName: String?, autoApprove: Boolean
    ): AutoApproveResult {
        // Step 1: Resolve server
        val resolvedServer = when {
            toolName != null -> {
                val entry = toolRegistry.lookupTool(toolName)
                    ?: throw ToolNotFoundException(toolName)
                entry.serverName
            }
            serverName != null -> {
                if (toolRegistry.getToolsByServer(serverName).isEmpty())
                    throw ToolNotFoundException(serverName)
                serverName
            }
            else -> throw InvalidParamsException("At least one of tool_name or server_name is required")
        }

        // Step 2: Update config file (atomic write)
        var configWarning: String? = null
        try {
            updateConfigFile(resolvedServer, toolName, autoApprove)
        } catch (e: Exception) {
            logger.error("Config file write failed: ${e.message}")
            configWarning = "Config file not updated — DB only"
        }

        // Step 3: Update DB
        var dbWarning: String? = null
        try {
            updateDb(resolvedServer, toolName, autoApprove)
        } catch (e: Exception) {
            logger.error("DB write failed: ${e.message}")
            dbWarning = "DB not updated — config file only"
        }

        val warning = listOfNotNull(configWarning, dbWarning).joinToString("; ").ifEmpty { null }
        return AutoApproveResult(
            tool = toolName, server = resolvedServer,
            autoApprove = autoApprove, warning = warning
        )
    }

    private fun updateConfigFile(serverName: String, toolName: String?, autoApprove: Boolean) {
        val configFile = File(configFilePath)
        val content = configFile.readText()
        val configJson = Json.parseToJsonElement(content).jsonObject.toMutableMap()

        val serverObj = configJson[serverName]?.jsonObject?.toMutableMap()
            ?: throw ToolNotFoundException(serverName)

        val currentList = serverObj["autoApprove"]?.jsonArray
            ?.map { it.jsonPrimitive.content }?.toMutableList()
            ?: mutableListOf()

        if (toolName != null) {
            if (autoApprove && toolName !in currentList) currentList.add(toolName)
            if (!autoApprove) currentList.remove(toolName)
        } else {
            // Server-level: wildcard
            if (autoApprove) { currentList.clear(); currentList.add("*") }
            else currentList.clear()
        }

        if (currentList.isEmpty()) serverObj.remove("autoApprove")
        else serverObj["autoApprove"] = JsonArray(currentList.map { JsonPrimitive(it) })

        configJson[serverName] = JsonObject(serverObj)

        // Atomic write: temp file → validate → rename
        val tmpFile = File("$configFilePath.tmp")
        tmpFile.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), JsonObject(configJson)))
        // Validate by re-parsing
        Json.parseToJsonElement(tmpFile.readText())
        // Atomic rename
        if (!tmpFile.renameTo(configFile)) {
            tmpFile.delete()
            throw IOException("Atomic rename failed for $configFilePath")
        }
    }
}
```

> **TA Note:** The existing `JsonConfigLoader` (verified in `config/JsonConfigLoader.kt`) reads `mcp-servers.json` but only for loading. The `AutoApproveService` needs write access. The config file path should be resolved from `ConfigurationManager` or passed via DI. The existing `UpstreamServerConfig` data class (verified in `OrchestratorConfig.kt`) does NOT have `disabled`, `autoApprove`, or `toolFilter` fields — these must be added.

---

### 3.6 Feature: Config-DB Sync on Startup

**Source:** BRD Story 6 (FR-6)

#### 3.6.1 Description

On every startup, the orchestrator reads `mcp-servers.json` and synchronizes server configuration to PostgreSQL `server_config` table. The config file is the source of truth; the DB is a runtime mirror for multi-instance consistency and query support.

#### 3.6.2 Use Case

**Use Case ID:** UC-6
**Actor:** System (automatic on startup)
**Preconditions:** `mcp-servers.json` exists and is valid JSON; PostgreSQL is accessible
**Postconditions:** `server_config` table reflects exactly the servers in config file

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Application.main() | ConfigDbSyncService | Triggered during startup, before tool indexing |
| 2 | | ConfigDbSyncService | Reads and parses `mcp-servers.json` via `JsonConfigLoader` |
| 3 | | ConfigDbSyncService | Queries `server_config` table for all existing rows |
| 4 | | ConfigDbSyncService | Compares config servers vs DB servers |
| 5 | | ConfigDbSyncService | INSERT new servers (in config but not in DB) |
| 6 | | ConfigDbSyncService | UPDATE changed servers (settings differ: disabled, toolFilter, autoApprove) |
| 7 | | ConfigDbSyncService | Mark removed servers as `active=false` (in DB but not in config) |
| 8 | | ConfigDbSyncService | Logs sync summary: `"Config-DB sync: 2 added, 1 updated, 0 deactivated"` |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-6.1 | First startup (empty DB) | Step 3 returns empty list. Step 5: INSERT all servers from config. Steps 6–7 skipped |
| AF-6.2 | No changes detected | Steps 5–7 are no-ops. Log: `"Config-DB sync: no changes detected"` |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-6.1 | Config file not found | Log ERROR: "mcp-servers.json not found at {path}". Start with empty server list. Tool indexing skipped |
| EF-6.2 | Config file parse error (invalid JSON) | Log ERROR with parse error details. Abort startup with `ConfigException` |
| EF-6.3 | DB connection failure during sync | Log ERROR. Continue startup in config-only mode (degraded). Toggle/auto-approve features unavailable |

#### 3.6.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-6.1 | Config file is the source of truth; DB is the runtime mirror | BRD Story 6 Req #5 |
| BR-6.2 | Removed servers are soft-deleted (`active=false`), not hard-deleted | BRD Story 6 Req #3 |
| BR-6.3 | Sync must complete before tool indexing begins | BRD Story 6 AC #4 |
| BR-6.4 | Sync is idempotent — multiple runs produce same result | BRD Story 6 AC #5 |
| BR-6.5 | Server names must be unique within config file | BRD Story 6 Validation |

#### 3.6.4 Data Specifications

**Input Data (from mcp-servers.json):**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| server_name (key) | String | Y | Unique, non-empty | Server identifier |
| command | String | N | — | Stdio command to launch server |
| args | String[] | N | — | Command arguments |
| env | Map<String,String> | N | — | Environment variables |
| url | String | N | Valid URL | HTTP endpoint for SSE transport |
| disabled | Boolean | N | Default: false | Server disabled flag |
| autoApprove | String[] | N | — | List of auto-approved tool names |
| toolFilter | Object | N | — | Tool filtering config (see FR-7) |

**Sync Result (internal):**

| Field | Type | Description |
|-------|------|-------------|
| added | Int | Servers inserted into DB |
| updated | Int | Servers updated in DB |
| deactivated | Int | Servers marked inactive |

#### 3.6.5 API Specifications

No MCP tool exposed — this is an internal startup process.

<!-- TA enrichment -->
**Pseudocode — ConfigDbSyncService.sync:**
[Implements: Story #6]

```kotlin
class ConfigDbSyncService(
    private val configLoader: JsonConfigLoader,
    private val dataSource: HikariDataSource
) {
    private val logger = LoggerFactory.getLogger(ConfigDbSyncService::class.java)

    suspend fun sync(): SyncResult {
        val configServers = configLoader.loadServers()  // Map<String, ServerJsonConfig>
        val dbServers = loadDbServers()                  // Map<String, ServerConfigRow>

        var added = 0; var updated = 0; var deactivated = 0

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                // INSERT new servers
                for ((name, config) in configServers) {
                    if (name !in dbServers) {
                        insertServer(conn, name, config)
                        added++
                    } else {
                        // UPDATE if changed
                        val dbRow = dbServers[name]!!
                        if (hasChanges(dbRow, config)) {
                            updateServer(conn, name, config)
                            updated++
                        }
                    }
                }
                // DEACTIVATE removed servers (soft delete)
                for ((name, _) in dbServers) {
                    if (name !in configServers) {
                        deactivateServer(conn, name)
                        deactivated++
                    }
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }

        logger.info("Config-DB sync: $added added, $updated updated, $deactivated deactivated")
        return SyncResult(added, updated, deactivated)
    }

    private fun hasChanges(dbRow: ServerConfigRow, config: ServerJsonConfig): Boolean {
        return dbRow.disabled != config.disabled
            || dbRow.autoApprove != config.autoApprove
            || dbRow.toolFilterMode != config.toolFilter?.mode
            || dbRow.toolFilterTools != config.toolFilter?.tools
    }
}
```

> **TA Note:** This service must be invoked in `Application.main()` BEFORE `ToolIndexer.indexAll()`. The startup sequence in Section 6.1 Step 4–5 correctly orders this. The `JsonConfigLoader` (verified in `config/JsonConfigLoader.kt`) currently loads config but may need extension to return structured server data including `disabled`, `autoApprove`, and `toolFilter` fields.

---

### 3.7 Feature: Tool Filtering from Config

**Source:** BRD Story 7 (FR-7)

#### 3.7.1 Description

Support `toolFilter` field in `mcp-servers.json` per server to control which tools are indexed and exposed. Filter is applied at `ToolIndexer.indexTools()` before embedding and registration. Filtered tools are never embedded, registered, or stored in vector DB.

#### 3.7.2 Use Case

**Use Case ID:** UC-7
**Actor:** System (ToolIndexer during startup)
**Preconditions:** `mcp-servers.json` contains `toolFilter` for one or more servers
**Postconditions:** Only allowed tools are indexed; filtered tools are invisible to clients

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | ToolIndexer | | Discovers tools from upstream server via `tools/list` |
| 2 | | ToolIndexer | Reads `toolFilter` config for this server |
| 3 | | ToolIndexer | If `mode=blocklist`: removes tools whose names are in `tools` array |
| 4 | | ToolIndexer | Remaining tools are embedded and registered normally |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-7.1 | `mode=allowlist` | Step 3: Keep ONLY tools whose names are in `tools` array. Remove all others |
| AF-7.2 | No `toolFilter` for server | Steps 2–3 skipped. All tools indexed (backward compatible) |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-7.1 | Invalid `mode` value (not allowlist/blocklist) | Log WARN: "Invalid toolFilter mode '{value}' for server '{name}' — ignoring filter". Index all tools |
| EF-7.2 | Empty `tools` array | Log WARN: "Empty toolFilter.tools for server '{name}' — ignoring filter". Index all tools |

#### 3.7.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-7.1 | `mode` must be `allowlist` or `blocklist` | BRD Story 7 Validation |
| BR-7.2 | `tools` must be non-empty array | BRD Story 7 Validation |
| BR-7.3 | Tool names are case-sensitive | BRD Story 7 Validation |
| BR-7.4 | No `toolFilter` = all tools exposed (backward compatible) | BRD Story 7 AC #3 |
| BR-7.5 | Filter changes require server restart (config-based) | BRD Story 7 AC #5 |

#### 3.7.4 Data Specifications

**toolFilter Config Structure:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| mode | String | Y | `allowlist` or `blocklist` | Filter mode |
| tools | String[] | Y | Non-empty array | Tool names to filter |

**Example Config:**

```json
{
  "atlassian": {
    "command": "npx",
    "args": ["-y", "@anthropic/mcp-atlassian"],
    "toolFilter": {
      "mode": "blocklist",
      "tools": ["jira_delete_issue", "confluence_delete_page"]
    }
  }
}
```

#### 3.7.5 API Specifications

No MCP tool exposed — config-based, applied at startup.

<!-- TA enrichment -->
**Pseudocode — ToolFilterService.applyFilter:**
[Implements: Story #7]

```kotlin
class ToolFilterService {
    private val logger = LoggerFactory.getLogger(ToolFilterService::class.java)

    fun applyFilter(
        tools: List<ToolDefinition>,
        filterConfig: ToolFilterConfig?
    ): List<ToolDefinition> {
        if (filterConfig == null) return tools  // No filter = all tools (BR-7.4)

        if (filterConfig.tools.isEmpty()) {
            logger.warn("Empty toolFilter.tools — ignoring filter")
            return tools  // EF-7.2
        }

        return when (filterConfig.mode) {
            "blocklist" -> tools.filter { it.name !in filterConfig.tools }
            "allowlist" -> tools.filter { it.name in filterConfig.tools }
            else -> {
                logger.warn("Invalid toolFilter mode '${filterConfig.mode}' — ignoring filter")
                tools  // EF-7.1
            }
        }
    }
}
```

> **TA Note:** The existing `ToolIndexer.indexTools()` (verified in `registry/ToolIndexer.kt`) does NOT currently apply any filtering. The filter must be applied BEFORE the `embeddingService.generateEmbeddings(texts)` call (around line ~75 of `ToolIndexer.kt`). The `ToolFilterService` should be injected into `ToolIndexer` via Koin.

---

### 3.8 Feature: Dual Operational Modes (Standalone vs Local Bridge)

**Source:** BRD Story 8

#### 3.8.1 Description

The MCP Orchestrator must support two distinct operational modes to accommodate different deployment scenarios. 
1. **Standalone Mode (SSE)**: The application runs as a persistent web service, accepting connections via the Server-Sent Events (SSE) transport protocol. 
2. **Local Bridge Mode (Stdio)**: The application runs as a child process of a host client (e.g., Cursor, Claude Desktop), communicating via standard input/output (stdio).

#### 3.8.2 Operational Specification

| Feature | Standalone (SSE) | Local Bridge (Stdio) |
|---------|------------------|----------------------|
| **Transport** | HTTP/SSE (Ktor Server) | Stdio (Process Streams) |
| **Config Source** | `application.yml` | Command Line / Host settings |
| **Lifecycle** | Persistent (Daemon) | Ephemeral (tied to host process) |
| **Discovery** | Dynamic (Vector DB Index) | Host-injected / Pre-indexed |
| **Use Case** | Multi-user / Cloud deployment | Local IDE development / Personal assistant |

#### 3.8.3 Implementation Logic

The entry point of the application (`Main.kt`) must detect the desired mode based on environment variables or CLI flags.

**Standalone Detection:**
- `orchestrator.server.protocol == "sse"` (configured in `application.yml`)
- `PORT` environment variable is set.

**Local Bridge Detection (Default):**
- No `PORT` set.
- Execution via `java -jar ...` without SSE-specific flags.

**Pseudocode — Mode Selection:**

```kotlin
fun main(args: Array<String>) {
    val config = loadConfig()
    if (config.server.protocol == "sse") {
        logger.info("Starting in Standalone Mode (SSE)")
        startSseServer(config)
    } else {
        logger.info("Starting in Local Bridge Mode (Stdio)")
        startStdioServer(config)
    }
}
```

---

## 4. Data Model

### 4.1 Entity Relationship Diagram

![ER Diagram](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/er-diagram.png)

*[Edit in draw.io](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/er-diagram.drawio)*

The data model introduces 3 new PostgreSQL tables alongside the existing pgvector extension. All tables reside in the `jira_assistant` database.

<!-- TA enrichment -->
> **TA Note — Codebase Verification:** The existing codebase has NO PostgreSQL tables — all current storage is via Qdrant REST API (`QdrantVectorDbClient`) or in-memory FAISS (`FaissVectorDbClient`). The 3 tables below are entirely new. The `VectorPoint` DTO (verified in `vectordb/model/VectorPoint.kt`) uses `Map<String, String>` for payload — the pgvector implementation must handle the mapping from this flat string map to the typed columns (`server_name`, `tool_name`, `description`) plus JSONB for remaining metadata.

### 4.2 Database Tables

#### Table: `tool_embeddings`

Primary storage for tool vectors and metadata. Replaces Qdrant collection `mcp_tools`.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | UUID | N | `gen_random_uuid()` | Primary key |
| server_name | VARCHAR(255) | N | — | Upstream MCP server name |
| tool_name | VARCHAR(255) | N | — | Tool identifier |
| description | TEXT | N | — | Tool description (for keyword search) |
| embedding | vector(768) | Y | NULL | Embedding vector (NULL if embedding failed) |
| search_vector | tsvector | N | — | Auto-generated from `description` for keyword search |
| payload | JSONB | Y | NULL | Additional tool metadata |
| input_schema | JSONB | Y | NULL | Tool input JSON Schema |
| created_at | TIMESTAMPTZ | N | `NOW()` | Row creation timestamp |
| updated_at | TIMESTAMPTZ | N | `NOW()` | Last update timestamp |

**Constraints:**

| Constraint | Type | Columns | Description |
|------------|------|---------|-------------|
| pk_tool_embeddings | PRIMARY KEY | id | UUID primary key |
| uq_tool_embeddings_server_tool | UNIQUE | (server_name, tool_name) | One embedding per tool per server |

**Indexes:**

| Index Name | Columns | Type | Description |
|------------|---------|------|-------------|
| idx_tool_embeddings_hnsw | embedding | HNSW (vector_cosine_ops) | Approximate nearest neighbor for cosine similarity |
| idx_tool_embeddings_search | search_vector | GIN | Full-text keyword search |
| idx_tool_embeddings_server | server_name | B-tree | Filter by server |

**DDL:**

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS tool_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_name VARCHAR(255) NOT NULL,
    tool_name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    embedding vector(768),
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('english', description)) STORED,
    payload JSONB,
    input_schema JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (server_name, tool_name)
);

CREATE INDEX IF NOT EXISTS idx_tool_embeddings_hnsw
    ON tool_embeddings USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_tool_embeddings_search
    ON tool_embeddings USING gin (search_vector);

CREATE INDEX IF NOT EXISTS idx_tool_embeddings_server
    ON tool_embeddings (server_name);
```

---

#### Table: `tool_toggle_state`

Session-scoped tool enable/disable state. Each orchestrator instance has its own session_id.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGSERIAL | N | auto | Primary key |
| session_id | UUID | N | — | Orchestrator instance identifier |
| tool_name | VARCHAR(255) | Y | NULL | Specific tool (NULL if server-level toggle) |
| server_name | VARCHAR(255) | Y | NULL | Server name (NULL if tool-level toggle) |
| enabled | BOOLEAN | N | true | Current toggle state |
| toggled_at | TIMESTAMPTZ | N | `NOW()` | When the toggle was applied |

**Constraints:**

| Constraint | Type | Columns | Description |
|------------|------|---------|-------------|
| pk_tool_toggle_state | PRIMARY KEY | id | Auto-increment PK |
| uq_toggle_session_tool | UNIQUE | (session_id, tool_name) | One state per tool per session |
| ck_toggle_at_least_one | CHECK | tool_name, server_name | At least one must be non-null |

**Indexes:**

| Index Name | Columns | Type | Description |
|------------|---------|------|-------------|
| idx_toggle_session | session_id | B-tree | Filter by session |
| idx_toggle_session_server | (session_id, server_name) | B-tree | Filter by session + server |

**DDL:**

```sql
CREATE TABLE IF NOT EXISTS tool_toggle_state (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL,
    tool_name VARCHAR(255),
    server_name VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT true,
    toggled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (session_id, tool_name),
    CHECK (tool_name IS NOT NULL OR server_name IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_toggle_session
    ON tool_toggle_state (session_id);

CREATE INDEX IF NOT EXISTS idx_toggle_session_server
    ON tool_toggle_state (session_id, server_name);
```

---

#### Table: `server_config`

Mirrored server configuration from `mcp-servers.json`. Updated on startup sync and auto-approve changes.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGSERIAL | N | auto | Primary key |
| server_name | VARCHAR(255) | N | — | Unique server identifier |
| command | VARCHAR(1024) | Y | NULL | Stdio launch command |
| args | JSONB | Y | `'[]'` | Command arguments array |
| env | JSONB | Y | `'{}'` | Environment variables map |
| url | VARCHAR(1024) | Y | NULL | HTTP endpoint URL |
| disabled | BOOLEAN | N | false | Server disabled flag |
| auto_approve | JSONB | Y | `'[]'` | Auto-approved tool names array |
| tool_filter_mode | VARCHAR(20) | Y | NULL | `allowlist` or `blocklist` |
| tool_filter_tools | JSONB | Y | `'[]'` | Tool names for filter |
| active | BOOLEAN | N | true | false = removed from config (soft delete) |
| synced_at | TIMESTAMPTZ | N | `NOW()` | Last sync timestamp |

**Constraints:**

| Constraint | Type | Columns | Description |
|------------|------|---------|-------------|
| pk_server_config | PRIMARY KEY | id | Auto-increment PK |
| uq_server_config_name | UNIQUE | server_name | One row per server |

**DDL:**

```sql
CREATE TABLE IF NOT EXISTS server_config (
    id BIGSERIAL PRIMARY KEY,
    server_name VARCHAR(255) NOT NULL UNIQUE,
    command VARCHAR(1024),
    args JSONB DEFAULT '[]',
    env JSONB DEFAULT '{}',
    url VARCHAR(1024),
    disabled BOOLEAN NOT NULL DEFAULT false,
    auto_approve JSONB DEFAULT '[]',
    tool_filter_mode VARCHAR(20),
    tool_filter_tools JSONB DEFAULT '[]',
    active BOOLEAN NOT NULL DEFAULT true,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 5. Integration Specifications

### 5.1 External System: Ollama

| Attribute | Value |
|-----------|-------|
| Protocol | HTTP REST |
| Base URL | `http://localhost:11434` (configurable via `embedding.api_url`) |
| Authentication | None |
| Data Format | JSON |
| Timeout | 30 seconds |

**Endpoint: Generate Embedding**

| Attribute | Value |
|-----------|-------|
| Method | POST |
| Path | `/api/embeddings` |
| Content-Type | `application/json` |

**Request Mapping:**

| Source Field (Orchestrator) | Target Field (Ollama) | Transformation |
|----------------------------|----------------------|----------------|
| EmbeddingConfig.model | model | Direct copy |
| text (input parameter) | prompt | Direct copy |

**Response Mapping:**

| Source Field (Ollama) | Target Field (Orchestrator) | Transformation |
|----------------------|----------------------------|----------------|
| embedding | FloatArray | Parse JSON array to FloatArray; validate length = dimensions |

**Health Check:** `GET /api/tags` — returns 200 if Ollama is running.

<!-- TA enrichment -->
**Retry Policy (Ollama):**

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Max Retries | 2 | Local service — fast retry is cheap |
| Backoff | 500ms, 1000ms | Exponential with base 500ms |
| Retry On | `ConnectException`, `SocketTimeoutException` | Network-level failures only |
| Do Not Retry | HTTP 4xx, malformed response | Client errors are not transient |

**Error Response Mapping:**

| Ollama HTTP Status | Orchestrator Action | Exception |
|--------------------|--------------------|-----------| 
| 200 OK | Parse embedding | — |
| 404 Not Found | Model not found | `EmbeddingServiceException("Model '${model}' not found in Ollama")` |
| 500 Internal Server Error | Log + retry | `EmbeddingServiceException` after retries exhausted |
| Connection Refused | Log + throw immediately | `EmbeddingServiceException(cause=ConnectException)` |
| Timeout (>30s) | Log + throw | `EmbeddingServiceException(cause=SocketTimeoutException)` |

> **TA Note:** The existing `RetryUtils.kt` (verified in `util/RetryUtils.kt`) provides `withRetry()` with exponential backoff. Reuse this for embedding service calls.

---

### 5.2 External System: LMStudio

| Attribute | Value |
|-----------|-------|
| Protocol | HTTP REST (OpenAI-compatible) |
| Base URL | `http://localhost:1234` (configurable via `embedding.api_url`) |
| Authentication | Optional Bearer token |
| Data Format | JSON |
| Timeout | 30 seconds |

**Endpoint: Generate Embedding**

| Attribute | Value |
|-----------|-------|
| Method | POST |
| Path | `/v1/embeddings` |
| Content-Type | `application/json` |

**Request Mapping:**

| Source Field (Orchestrator) | Target Field (LMStudio) | Transformation |
|----------------------------|------------------------|----------------|
| EmbeddingConfig.model | model | Direct copy |
| text (input parameter) | input | Direct copy (String or String[]) |

**Response Mapping:**

| Source Field (LMStudio) | Target Field (Orchestrator) | Transformation |
|------------------------|----------------------------|----------------|
| data[0].embedding | FloatArray | Parse first element; validate length = dimensions |
| usage.prompt_tokens | (logged) | Log at DEBUG level for monitoring |

**Health Check:** `GET /v1/models` — returns 200 with model list if LMStudio is running.

<!-- TA enrichment -->
**Retry Policy (LMStudio):** Same as Ollama (Section 5.1).

**Error Response Mapping:**

| LMStudio HTTP Status | Orchestrator Action | Exception |
|----------------------|--------------------|-----------| 
| 200 OK | Parse `data[0].embedding` | — |
| 401 Unauthorized | Invalid Bearer token | `EmbeddingServiceException("LMStudio auth failed")` |
| 404 Not Found | Model not loaded | `EmbeddingServiceException("Model '${model}' not loaded in LMStudio")` |
| 500+ | Log + retry | `EmbeddingServiceException` after retries exhausted |

---

### 5.3 External System: PostgreSQL + pgvector

| Attribute | Value |
|-----------|-------|
| Protocol | JDBC (PostgreSQL wire protocol) |
| Connection String | `postgresql://postgres:postgres@localhost:5432/jira_assistant` |
| Driver | `org.postgresql:postgresql` |
| Connection Pool | HikariCP (min=2, max=10) |
| Extensions Required | `vector` (pgvector) |

**Data Mapping (VectorPoint → tool_embeddings):**

| Source Field (VectorPoint) | Target Column | Transformation |
|---------------------------|---------------|----------------|
| id | id | UUID string → UUID type |
| vector | embedding | FloatArray → vector(768) literal `'[0.1,0.2,...]'` |
| payload["server_name"] | server_name | Extract from payload map |
| payload["tool_name"] | tool_name | Extract from payload map |
| payload["description"] | description | Extract from payload map |
| payload["input_schema"] | input_schema | Serialize to JSONB |
| (remaining payload) | payload | Serialize to JSONB |

**Hybrid Search SQL:**

```sql
SELECT id, server_name, tool_name, description, payload, input_schema,
       (1 - (embedding <=> $1::vector)) AS vector_score,
       ts_rank(search_vector, plainto_tsquery('english', $2)) AS keyword_score,
       (0.7 * (1 - (embedding <=> $1::vector)) + 0.3 * ts_rank(search_vector, plainto_tsquery('english', $2))) AS combined_score
FROM tool_embeddings
WHERE (1 - (embedding <=> $1::vector)) >= $3
  AND NOT EXISTS (
      SELECT 1 FROM tool_toggle_state
      WHERE session_id = $4 AND tool_name = tool_embeddings.tool_name AND enabled = false
  )
ORDER BY combined_score DESC
LIMIT $5;
```

<!-- TA enrichment -->
**HikariCP Connection Pool Configuration:**

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| `minimumIdle` | 2 | Keep 2 warm connections for low-latency queries |
| `maximumPoolSize` | 10 | Sufficient for single-instance orchestrator |
| `connectionTimeout` | 5000ms | Fail fast if DB is unreachable |
| `idleTimeout` | 300000ms (5min) | Release idle connections |
| `maxLifetime` | 1800000ms (30min) | Prevent stale connections |
| `leakDetectionThreshold` | 10000ms | Detect connection leaks in dev |

**Connection Failure Handling:**

| Scenario | Detection | Recovery |
|----------|-----------|----------|
| DB down at startup | `HikariPool` init fails | Log ERROR, continue in degraded mode (keyword-only search) |
| DB down at runtime | `SQLException` on query | Log ERROR, fall back to keyword search for that request |
| Connection leak | HikariCP leak detection | Log WARN with stack trace |
| pgvector extension missing | `CREATE EXTENSION` fails | Log ERROR with install instructions, throw `VectorDbUnavailableException` |

**Hybrid Search — Keyword-Only Fallback:**

When `vector` parameter is NULL (embedding service was unavailable), the query degrades to keyword-only:

```sql
SELECT id, server_name, tool_name, description, payload, input_schema,
       0.0 AS vector_score,
       ts_rank(search_vector, plainto_tsquery('english', $1)) AS keyword_score,
       ts_rank(search_vector, plainto_tsquery('english', $1)) AS combined_score
FROM tool_embeddings
WHERE search_vector @@ plainto_tsquery('english', $1)
  AND NOT EXISTS (
      SELECT 1 FROM tool_toggle_state
      WHERE session_id = $2 AND tool_name = tool_embeddings.tool_name AND enabled = false
  )
ORDER BY combined_score DESC
LIMIT $3;
```

---

### 5.4 External System: mcp-servers.json (Config File)

| Attribute | Value |
|-----------|-------|
| Protocol | Local filesystem |
| Path | Configurable; default: `./mcp-servers.json` |
| Format | JSON |
| Access | Read on startup; Read+Write for auto-approve |

**Atomic Write Pattern:**

1. Write updated JSON to `mcp-servers.json.tmp`
2. Validate the temp file is valid JSON by re-parsing
3. Rename `mcp-servers.json.tmp` → `mcp-servers.json` (atomic on most filesystems)
4. If rename fails, delete temp file and report error

<!-- TA enrichment -->
**File Locking Strategy:**

Since `manage_auto_approve` can be called concurrently, the config file write must be serialized:

```kotlin
// Use Kotlin Mutex (kotlinx.coroutines.sync.Mutex) for coroutine-safe locking
private val configFileMutex = Mutex()

suspend fun updateConfigFile(...) {
    configFileMutex.withLock {
        // Read → Modify → Write temp → Validate → Rename
    }
}
```

**Concurrency Scenarios:**

| Scenario | Handling |
|----------|----------|
| Two `manage_auto_approve` calls simultaneously | Mutex serializes — second waits for first to complete |
| `manage_auto_approve` during startup sync | Startup sync is read-only on config file — no conflict |
| External editor modifies file during write | Atomic rename overwrites — last writer wins |

---

## 6. Processing Logic

### 6.1 Startup Sequence

**Trigger:** Application.main() invocation
**Schedule:** Once on startup
**Input:** `application.yml`, `mcp-servers.json`
**Output:** Fully initialized orchestrator with indexed tools

**Processing Steps:**

| Step | Description | Error Handling |
|------|-------------|----------------|
| 1 | Load `application.yml` via `ConfigurationManager` | `ConfigException` → abort startup |
| 2 | Initialize Koin DI container with `appModule()` | Exception → abort startup |
| 3 | Generate unique `session_id` (UUID) for this instance | — |
| 4 | Connect to PostgreSQL; create tables/extensions if missing | `VectorDbUnavailableException` → continue in degraded mode |
| 5 | Run Config-DB Sync (UC-6) | See EF-6.x |
| 6 | Connect to upstream MCP servers via `UpstreamServerManager` | Per-server: log error, skip failed servers |
| 7 | Discover tools from each connected server (`tools/list`) | Per-server: log error, skip |
| 8 | Apply tool filters per server config (UC-7) | See EF-7.x |
| 9 | Embed filtered tools via `EmbeddingService` | `EmbeddingServiceException` → keyword-only mode |
| 10 | Upsert embeddings into pgvector via `PgVectorDbClient` | `VectorDbUnavailableException` → keyword-only mode |
| 11 | Register tools in `ToolRegistry` | — |
| 12 | Register MCP tools (find_tools, execute_dynamic_tool, toggle_tool, reset_tools, manage_auto_approve) | — |
| 13 | Start health monitor | — |
| 14 | Start MCP transport (stdio or HTTP) | — |

### 6.2 Tool Discovery (Enhanced find_tools)

**Trigger:** AI client calls `find_tools` MCP tool
**Input:** `{query, top_k, threshold}`
**Output:** `FindToolsResponse` with ranked tool list

**Processing Steps:**

| Step | Description | Error Handling |
|------|-------------|----------------|
| 1 | Validate input: query non-empty, top_k 1–20, threshold 0.0–1.0 | `InvalidParamsException` |
| 2 | Generate embedding for query via `EmbeddingService` | `EmbeddingServiceException` → skip to step 4 |
| 3 | Execute hybrid search on pgvector (vector + keyword) excluding disabled tools for current session | `VectorDbUnavailableException` → skip to step 4 |
| 4 | If step 2 or 3 failed: fall back to `KeywordSearchEngine` (TF-IDF) | — |
| 5 | Map search results to `FindToolsResponse` with scores | — |
| 6 | Return top_k results above threshold | — |

<!-- TA enrichment -->
**Enhanced find_tools — Disabled Tool Filtering:**
[Implements: BR-3.5]

```kotlin
// Modified ToolDiscoveryServiceImpl.findTools — add after vector search (Step 3)
// The pgvector hybrid search SQL already excludes disabled tools via NOT EXISTS subquery.
// For keyword fallback, filtering must be applied in-memory:

private fun keywordFallback(query: String, topK: Int, threshold: Float): FindToolsResponse {
    val results = keywordEngine.search(query, topK, threshold)
    // Filter out disabled tools for current session
    val filtered = results.filter { !toolToggleService.isDisabled(it.name, currentSessionId) }
    return FindToolsResponse(tools = filtered, searchMode = "keyword", totalIndexed = toolRegistry.getToolCount())
}
```

### 6.3 Tool Execution (Enhanced execute_dynamic_tool)

**Trigger:** AI client calls `execute_dynamic_tool` MCP tool
**Input:** `{tool_name, arguments}`
**Output:** `ExecuteToolResponse` with tool result

**Processing Steps:**

| Step | Description | Error Handling |
|------|-------------|----------------|
| 1 | Validate `tool_name` is non-empty | `InvalidParamsException` |
| 2 | Look up tool in `ToolRegistry` | `ToolNotFoundException` |
| 3 | **NEW:** Check if tool is disabled for current session in `tool_toggle_state` | If disabled: return error `"Tool '{name}' is currently disabled. Use toggle_tool to re-enable."` |
| 4 | Verify upstream server is connected | `ServerUnavailableException` |
| 5 | Route `tools/call` to upstream server with arguments | `ExecutionTimeoutException`, `UpstreamErrorException` |
| 6 | Return upstream response to client | — |

<!-- TA enrichment -->
**Modified ToolExecutionDispatcherImpl.execute — Disabled Check:**
[Implements: BR-3.6]

```kotlin
// Insert between lookupAndValidate() and getConnection() in ToolExecutionDispatcherImpl
// Verified: current code at execution/ToolExecutionDispatcherImpl.kt does NOT have this check.

override suspend fun execute(toolName: String, arguments: JsonObject?): ExecuteToolResponse {
    logger.info("execute_dynamic_tool: tool=$toolName")
    val toolEntry = lookupAndValidate(toolName)

    // NEW: Check disabled state
    if (toolToggleService.isDisabled(toolName, currentSessionId)) {
        throw ToolDisabledException(toolName)
    }

    val connection = getConnection(toolName, toolEntry.serverName)
    // ... rest unchanged
}
```

### 6.4 Config File Update (Auto-Approve)

**Trigger:** Operator calls `manage_auto_approve`
**Input:** `{tool_name, server_name, auto_approve}`
**Output:** Updated `mcp-servers.json` and DB

**Processing Steps:**

| Step | Description | Error Handling |
|------|-------------|----------------|
| 1 | Validate input parameters | `InvalidParamsException` |
| 2 | Look up tool/server in registry | `ToolNotFoundException` |
| 3 | Read current `mcp-servers.json` into JsonObject | `ConfigException` if malformed |
| 4 | Modify `autoApprove` array for target server | — |
| 5 | Write to `mcp-servers.json.tmp` | IOException → return warning |
| 6 | Validate temp file by re-parsing | If invalid → delete temp, return error |
| 7 | Atomic rename `.tmp` → `mcp-servers.json` | IOException → return warning |
| 8 | Update `server_config` table in PostgreSQL | SQLException → return warning |
| 9 | Return success response | — |

---

## 7. Security Requirements

### 7.1 Authentication & Authorization

| Role | Permissions | Features |
|------|-------------|----------|
| AI Client | Execute | `find_tools`, `execute_dynamic_tool` |
| Operator | Execute + Manage | All 5 MCP tools including `toggle_tool`, `reset_tools`, `manage_auto_approve` |
| System | Internal | Config-DB sync, tool indexing, health monitoring |

> **Note:** MCP protocol does not natively support role-based access control. All connected clients have access to all registered tools. Role separation is enforced by client configuration (which tools are exposed to which client). Authentication/authorization is out of scope per BRD Section 1.2.

### 7.2 Data Security

| Data Type | Security Measure | Details |
|-----------|-----------------|---------|
| PostgreSQL credentials | Environment variable | Connection string from env var, not hardcoded in config |
| OpenAI API key | Environment variable | `${OPENAI_API_KEY}` resolved at runtime via `ConfigurationManager.resolveEnvVars()` |
| mcp-servers.json | File permissions | Should be readable/writable only by orchestrator process user |
| Tool arguments | Pass-through | Arguments are forwarded to upstream servers without logging (may contain sensitive data) |
| Embedding vectors | No encryption | Stored as plain vectors in PostgreSQL — not sensitive data |

### 7.3 Audit Trail

| Event | Logged Fields | Retention |
|-------|--------------|-----------|
| Tool toggled | session_id, tool_name, server_name, enabled, timestamp | In `tool_toggle_state` table — session lifetime |
| Auto-approve changed | tool_name, server_name, auto_approve, timestamp | In application log (INFO level) |
| Config-DB sync | servers added/updated/deactivated, timestamp | In application log (INFO level) |
| Tool execution | tool_name, server_name, duration_ms, success/error | In application log (INFO level) |
| Embedding generation | provider, model, text_length, duration_ms | In application log (DEBUG level) |

---

## 8. Non-Functional Specifications

| Category | Specification | Target |
|----------|--------------|--------|
| Performance — Hybrid Search | `find_tools` response time with pgvector HNSW | < 200ms for 1000 tools |
| Performance — Embedding | Single text embedding generation (local) | < 500ms per text |
| Performance — Toggle | `toggle_tool` response time | < 50ms |
| Performance — Config Write | `manage_auto_approve` with file write | < 200ms |
| Availability | Graceful degradation if embedding service down | Keyword-only search fallback |
| Availability | Graceful degradation if PostgreSQL down | In-memory registry + keyword search |
| Scalability | Maximum tools indexed | 10,000+ (HNSW scales to millions) |
| Scalability | Concurrent sessions | Multiple orchestrator instances with independent session_ids |
| Multi-instance Safety | Toggle state isolation | Per session_id — no cross-instance interference |
| Backward Compatibility | Existing configs without new fields | Must work without modification |
| Backward Compatibility | OpenAI + Qdrant still available | Provider selection via config |
| Config Integrity | Atomic file writes | Write-to-temp-then-rename pattern |
| Startup Time | Config-DB sync + tool indexing | < 30s for 100 tools |

<!-- TA enrichment -->
**Quantified Performance Targets:**

| Metric | Target (p50) | Target (p95) | Target (p99) | Measurement Method |
|--------|-------------|-------------|-------------|-------------------|
| `find_tools` hybrid search | < 50ms | < 200ms | < 500ms | Application log `duration_ms` |
| `find_tools` keyword fallback | < 20ms | < 50ms | < 100ms | Application log `duration_ms` |
| Embedding generation (Ollama, single text) | < 200ms | < 500ms | < 1000ms | Application log `duration_ms` |
| Embedding generation (LMStudio, single text) | < 200ms | < 500ms | < 1000ms | Application log `duration_ms` |
| `toggle_tool` response | < 10ms | < 50ms | < 100ms | Application log `duration_ms` |
| `reset_tools` (without reindex) | < 20ms | < 50ms | < 100ms | Application log `duration_ms` |
| `reset_tools` (with reindex, 100 tools) | < 15s | < 30s | < 60s | Application log `duration_ms` |
| `manage_auto_approve` (incl. file write) | < 50ms | < 200ms | < 500ms | Application log `duration_ms` |
| Config-DB sync (100 servers) | < 2s | < 5s | < 10s | Startup log |
| PostgreSQL connection acquisition | < 5ms | < 20ms | < 50ms | HikariCP metrics |

**Resource Limits:**

| Resource | Limit | Rationale |
|----------|-------|-----------|
| JVM Heap | 512MB–1GB | Sufficient for 10K tool embeddings in-memory cache |
| PostgreSQL connections (HikariCP) | max 10 | Single-instance orchestrator |
| Embedding cache (in-memory) | 100 entries, 5min TTL | Avoid re-embedding same text |
| Tool registry (in-memory) | 10,000 entries | ConcurrentHashMap — O(1) lookup |
| Config file size | < 1MB | JSON parse in-memory |

**Degradation Modes:**

| Component Down | Impact | Fallback | User-Visible Effect |
|----------------|--------|----------|---------------------|
| Ollama/LMStudio | No embeddings | Keyword-only search | Lower search quality; `searchMode: "keyword"` in response |
| PostgreSQL | No vector search, no toggle persistence | In-memory registry + keyword search | Toggle states lost on restart |
| Upstream MCP server | Tools from that server unavailable | Skip server during indexing | Fewer tools in `find_tools` results |

---

## 9. Error Handling & Logging

### 9.1 Error Codes

| Code | Severity | Message | User Action | System Action |
|------|----------|---------|-------------|---------------|
| INVALID_PARAMS | Warning | Parameter validation failed: {details} | Fix input parameters | Return JSON-RPC error -32602 |
| TOOL_NOT_FOUND | Warning | Tool '{name}' is not registered | Use `find_tools` to discover tools | Return JSON-RPC error -32602 |
| TOOL_DISABLED | Warning | Tool '{name}' is currently disabled | Use `toggle_tool` to re-enable | Return JSON-RPC error -32602 |
| SERVER_UNAVAILABLE | Error | Server hosting '{tool}' is unavailable | Retry later | Return JSON-RPC error -32603 |
| EXECUTION_TIMEOUT | Error | Tool execution timed out after {n}s | Retry with simpler input | Return JSON-RPC error -32603 |
| UPSTREAM_ERROR | Error | Upstream error: {message} | Check upstream server logs | Return JSON-RPC error -32603 |
| VECTOR_DB_UNAVAILABLE | Warning | Vector DB unavailable, using keyword fallback | No action needed | Fall back to keyword search |
| EMBEDDING_SERVICE_ERROR | Warning | Embedding service unavailable | Ensure Ollama/LMStudio is running | Fall back to keyword search |
| CONFIG_INVALID | Critical | Config file error: {details} | Fix config file manually | Abort startup or return error |

<!-- TA enrichment -->
| TOOL_DISABLED | Warning | Tool '{name}' is currently disabled | Use `toggle_tool` to re-enable | Return JSON-RPC error -32602 |
| CONFIG_FILE_LOCKED | Warning | Config file is locked by another process | Retry later | Retry 3x with 500ms backoff, then return error |
| DB_WRITE_FAILED | Warning | Database write failed: {details} | No action needed | Continue with in-memory state, log warning |

**JSON-RPC Error Code Mapping:**

| Orchestrator Error Code | JSON-RPC Code | Category |
|------------------------|---------------|----------|
| INVALID_PARAMS | -32602 | Client error |
| TOOL_NOT_FOUND | -32602 | Client error |
| TOOL_DISABLED | -32602 | Client error |
| SERVER_UNAVAILABLE | -32603 | Server error |
| EXECUTION_TIMEOUT | -32603 | Server error |
| UPSTREAM_ERROR | -32603 | Server error |
| VECTOR_DB_UNAVAILABLE | -32603 | Server error (non-fatal) |
| EMBEDDING_SERVICE_ERROR | -32603 | Server error (non-fatal) |
| CONFIG_INVALID | -32603 | Server error (fatal on startup) |

### 9.2 Logging Specifications

| Log Type | Level | Content | Destination |
|----------|-------|---------|-------------|
| Startup | INFO | Config loaded, DB connected, tools indexed count | stdout (Logback) |
| Config-DB Sync | INFO | Servers added/updated/deactivated counts | stdout |
| Tool Toggle | INFO | session_id, tool/server toggled, new state | stdout |
| Auto-Approve | INFO | tool/server, new auto-approve state, file written | stdout |
| Tool Discovery | DEBUG | Query text, results count, search mode (hybrid/keyword) | stdout |
| Tool Execution | INFO | tool_name, server, duration_ms, success/error | stdout |
| Embedding | DEBUG | Provider, model, text length, vector dimensions, duration_ms | stdout |
| Health Check | DEBUG | Server name, status, latency_ms | stdout |
| Errors | ERROR | Exception class, message, stack trace (first 10 frames) | stderr |

<!-- TA enrichment -->
**Structured Logging Format (Logback pattern):**

The project uses Logback Classic (verified in `build.gradle.kts`). Recommended pattern for structured log parsing:

```xml
<!-- logback.xml — existing file at src/main/resources/logback.xml -->
<pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n</pattern>
```

**Key Log Events with MDC Context:**

| Event | MDC Keys | Example Log Line |
|-------|----------|-----------------|
| Tool toggle | `session_id`, `tool_name`, `enabled` | `INFO ToolToggleService - Tool toggled: tool=jira_get_issue, enabled=false, session=abc-123` |
| Auto-approve change | `tool_name`, `server_name`, `auto_approve` | `INFO AutoApproveService - Auto-approve updated: tool=jira_get_issue, server=atlassian, approved=true` |
| Config-DB sync | `added`, `updated`, `deactivated` | `INFO ConfigDbSyncService - Config-DB sync: 2 added, 1 updated, 0 deactivated` |
| Embedding generation | `provider`, `model`, `text_length`, `duration_ms` | `DEBUG OllamaEmbeddingService - Embedding generated: provider=ollama, model=nomic-embed-text, len=45, dims=768, duration=123ms` |
| Hybrid search | `query_truncated`, `results`, `mode`, `duration_ms` | `DEBUG ToolDiscoveryServiceImpl - find_tools: query="search jira...", results=5, mode=hybrid, duration=45ms` |

---

## 10. Testing Considerations

### 10.1 Test Scenarios

| ID | Scenario | Input | Expected Output | Priority |
|----|----------|-------|-----------------|----------|
| TC-1.1 | Ollama embedding — happy path | `provider=ollama`, text="search jira" | FloatArray of 768 dimensions | High |
| TC-1.2 | LMStudio embedding — happy path | `provider=lmstudio`, text="search jira" | FloatArray of 768 dimensions | High |
| TC-1.3 | Embedding — service unreachable | `provider=ollama`, Ollama not running | `EmbeddingServiceException` thrown | High |
| TC-1.4 | Embedding — dimension mismatch | Ollama returns 384-dim vector, config=768 | Vector zero-padded to 768 | Medium |
| TC-2.1 | pgvector upsert — happy path | VectorPoint with valid data | Row inserted in tool_embeddings | High |
| TC-2.2 | pgvector hybrid search | Query vector + keyword "jira" | Results ranked by combined score | High |
| TC-2.3 | pgvector — extension missing | PostgreSQL without pgvector | `VectorDbUnavailableException` | High |
| TC-2.4 | pgvector — duplicate upsert | Same server_name+tool_name twice | Row updated (not duplicated) | Medium |
| TC-3.1 | toggle_tool — disable specific tool | `tool_name="jira_get_issue", enabled=false` | Tool hidden from find_tools | High |
| TC-3.2 | toggle_tool — disable entire server | `server_name="atlassian", enabled=false` | All server tools hidden | High |
| TC-3.3 | toggle_tool — no params | `enabled=false` only | `InvalidParamsException` | High |
| TC-3.4 | toggle_tool — tool not found | `tool_name="nonexistent"` | `ToolNotFoundException` | Medium |
| TC-3.5 | execute disabled tool | Toggle off, then execute | Error: tool is disabled | High |
| TC-4.1 | reset_tools — all servers | No params | All tools re-enabled | High |
| TC-4.2 | reset_tools — specific server | `server_name="atlassian"` | Only atlassian tools reset | Medium |
| TC-4.3 | reset_tools — with reindex=false | `reindex=false` | States cleared, no re-indexing | Medium |
| TC-5.1 | manage_auto_approve — add tool | `tool_name="x", auto_approve=true` | Tool added to config file autoApprove array | High |
| TC-5.2 | manage_auto_approve — remove tool | `tool_name="x", auto_approve=false` | Tool removed from config file | High |
| TC-5.3 | manage_auto_approve — config file locked | File locked by another process | Retry 3x, then error | Medium |
| TC-5.4 | manage_auto_approve — DB failure | PostgreSQL down | Config file updated, warning returned | Medium |
| TC-6.1 | Config-DB sync — first startup | Empty DB | All servers inserted | High |
| TC-6.2 | Config-DB sync — server removed | Server removed from config | Marked `active=false` in DB | High |
| TC-6.3 | Config-DB sync — config missing | No mcp-servers.json | Empty server list, log error | Medium |
| TC-7.1 | Tool filter — blocklist | blocklist with 2 tools | Those 2 tools not indexed | High |
| TC-7.2 | Tool filter — allowlist | allowlist with 3 tools | Only those 3 tools indexed | High |
| TC-7.3 | Tool filter — no filter | No toolFilter in config | All tools indexed | High |
| TC-7.4 | Tool filter — invalid mode | `mode="invalid"` | Filter ignored, all tools indexed | Medium |

<!-- TA enrichment -->
### 10.2 Integration Test Scenarios

These tests require Testcontainers (already in `build.gradle.kts`) for PostgreSQL with pgvector.

| ID | Scenario | Setup | Verification | Framework |
|----|----------|-------|-------------|-----------|
| IT-1.1 | pgvector end-to-end: upsert + hybrid search | Testcontainers PostgreSQL 16 + pgvector | Upsert 10 tools, search returns ranked results | Kotest + Testcontainers |
| IT-1.2 | Embedding → pgvector pipeline | Mock Ollama (WireMock or Ktor mock client) + Testcontainers PG | Index 5 tools, verify embeddings stored in DB | Kotest + Testcontainers |
| IT-2.1 | Toggle tool → find_tools excludes it | In-memory registry + ToolToggleService | Toggle off tool, verify find_tools doesn't return it | Kotest + MockK |
| IT-2.2 | Toggle tool → execute_dynamic_tool blocked | Full ProtocolStack | Toggle off, execute → ToolDisabledException | Kotest + IntegrationTestBase |
| IT-3.1 | Config-DB sync — full cycle | Testcontainers PG + temp config file | Write config, sync, verify DB rows match | Kotest + Testcontainers |
| IT-3.2 | Auto-approve → config file updated | Temp config file | Call manage_auto_approve, read file, verify JSON | Kotest |
| IT-4.1 | Startup sequence — degraded mode | No PG, no Ollama | Verify startup completes with keyword-only mode | Kotest |
| IT-4.2 | Multi-session toggle isolation | Two ToolToggleService instances with different session_ids | Toggle in session A, verify session B unaffected | Kotest |

### 10.3 Performance Test Targets

| ID | Scenario | Load | Target | Tool |
|----|----------|------|--------|------|
| PT-1.1 | Hybrid search throughput | 100 concurrent queries, 1000 indexed tools | p95 < 200ms | Kotest + coroutines |
| PT-1.2 | Embedding batch throughput | 100 texts, Ollama local | Total < 30s | Manual benchmark |
| PT-1.3 | Toggle state lookup | 10,000 lookups/sec | p99 < 1ms (in-memory) | Kotest |
| PT-1.4 | Config file atomic write | 50 concurrent writes | No corruption, all succeed serially | Kotest + coroutines |

### 10.4 Security Test Cases

| ID | Scenario | Input | Expected | Priority |
|----|----------|-------|----------|----------|
| ST-1.1 | SQL injection in tool_name | `tool_name="'; DROP TABLE tool_embeddings;--"` | Parameterized query prevents injection | High |
| ST-1.2 | Path traversal in config file path | Config path `../../etc/passwd` | Rejected by ConfigValidator | High |
| ST-1.3 | Oversized embedding text | 100KB text input | Truncated to `maxQueryLength` (2000 chars) | Medium |
| ST-1.4 | Malformed JSON in config file | Invalid JSON in mcp-servers.json | `ConfigException` thrown, no overwrite | High |

---

## 11. Appendix

### 11.1 State Diagram — Tool Toggle Lifecycle

![State — Tool Toggle](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/state-tool-toggle.png)

*[Edit in draw.io](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/state-tool-toggle.drawio)*

**States:**

| State | Description |
|-------|-------------|
| ENABLED (default) | Tool is discoverable via `find_tools` and executable via `execute_dynamic_tool` |
| DISABLED | Tool is hidden from `find_tools` and blocked from `execute_dynamic_tool` |

**Transitions:**

| From | To | Trigger | Condition |
|------|----|---------|-----------|
| ENABLED | DISABLED | `toggle_tool(enabled=false)` | Valid tool/server name |
| DISABLED | ENABLED | `toggle_tool(enabled=true)` | Valid tool/server name |
| DISABLED | ENABLED | `reset_tools()` | Clears all toggle states |
| (any) | ENABLED | Server restart | Toggle state is session-scoped; new session starts clean |

### 11.2 Sequence Diagrams

![Sequence — Tool Discovery & Execution](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-discovery.png)

*[Edit in draw.io](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-discovery.drawio)*

![Sequence — Tool Management (Toggle / Reset / Auto-Approve)](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-mgmt.png)

*[Edit in draw.io](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-mgmt.drawio)*

#### 11.2.1 Detailed Management Sequences

![Sequence — Toggle Tool](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-toggle-tool.png)
![Sequence — Auto-Approve](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-auto-approve.png)

![Sequence — Startup & Config Sync](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-startup.png)

*[Edit in draw.io](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-startup.drawio)*

#### 11.2.2 Detailed Sync Sequence

![Sequence — Config Sync](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-config-sync.png)

### 11.3 New Kotlin Classes (Implementation Guide)

| Class | Package | Implements | Description |
|-------|---------|------------|-------------|
| `OllamaEmbeddingService` | `com.orchestrator.mcp.embedding` | `EmbeddingService` | Ollama REST API client |
| `LmStudioEmbeddingService` | `com.orchestrator.mcp.embedding` | `EmbeddingService` | LMStudio OpenAI-compatible client |
| `PgVectorDbClient` | `com.orchestrator.mcp.vectordb` | `VectorDbClient` | PostgreSQL pgvector operations |
| `ToolToggleService` | `com.orchestrator.mcp.registry` | (new interface) | Toggle state management |
| `AutoApproveService` | `com.orchestrator.mcp.config` | (new interface) | Auto-approve with config persistence |
| `ConfigDbSyncService` | `com.orchestrator.mcp.config` | (new interface) | Startup config-to-DB sync |
| `ToolFilterService` | `com.orchestrator.mcp.registry` | (new interface) | Apply allowlist/blocklist filters |

### 11.4 Modified Kotlin Classes

| Class | Package | Changes |
|-------|---------|---------|
| `EmbeddingConfig` | `com.orchestrator.mcp.config` | Add `api_url` field |
| `VectorDbConfig` | `com.orchestrator.mcp.config` | Add `connection_string` field; support `provider=pgvector` |
| `UpstreamServerConfig` | `com.orchestrator.mcp.config` | Add `disabled`, `autoApprove`, `toolFilter` fields |
| `McpToolRegistrar` | `com.orchestrator.mcp.protocol` | Register 3 new MCP tools |
| `McpToolSchemas` | `com.orchestrator.mcp.protocol` | Add schemas for toggle_tool, reset_tools, manage_auto_approve |
| `ToolIndexer` | `com.orchestrator.mcp.registry` | Apply tool filters before indexing |
| `ToolDiscoveryServiceImpl` | `com.orchestrator.mcp.discovery` | Exclude disabled tools from search |
| `ToolExecutionDispatcherImpl` | `com.orchestrator.mcp.execution` | Check disabled state before execution |
| `AppModule.kt` | `com.orchestrator.mcp.di` | Add DI bindings for new services |

### 11.5 New Dependencies (Gradle)

| Dependency | Version | Purpose |
|------------|---------|---------|
| `org.postgresql:postgresql` | 42.7.x | PostgreSQL JDBC driver |
| `com.zaxxer:HikariCP` | 5.1.x | Connection pooling |
| `com.pgvector:pgvector` | 0.1.x | pgvector Java support (vector type) |

### 11.6 Configuration Changes (`application.yml`)

**New/modified fields:**

```yaml
orchestrator:
  embedding:
    provider: ollama          # NEW: ollama | lmstudio | openai
    model: nomic-embed-text   # CHANGED: default model for local
    api_url: ""               # NEW: override base URL
    # api_key: ${OPENAI_API_KEY}  # Only needed for openai
  vector_db:
    provider: pgvector        # NEW: pgvector | qdrant | faiss
    connection_string: "postgresql://postgres:postgres@localhost:5432/jira_assistant"  # NEW
    # host, port, collection_name still used for qdrant
```

### 11.7 Persistence Rules Summary

| Action | Write to DB (session) | Write to Config File |
|--------|----------------------|---------------------|
| `toggle_tool` | Immediately | No (session-only) |
| `reset_tools` | Immediately | No |
| `manage_auto_approve` | Immediately | **Immediately** |
| Startup sync | From config → DB | No change |

### 11.8 Change Log from BRD

| Item | BRD Statement | FSD Clarification |
|------|--------------|-------------------|
| Hybrid search weight | Not specified | 70% vector + 30% keyword (BR-2.3) |
| Auto-approve wildcard | Not specified | `["*"]` means all tools for server (BR-5.5) |
| HNSW parameters | Not specified | m=16, ef_construction=64 (standard defaults) |
| Connection pooling | Not specified | HikariCP with min=2, max=10 |
| Session cleanup | Not specified | Toggle states remain in DB after session ends (no auto-cleanup) |

---

## Diagram Index

| # | Diagram | Type | File (Editable) | File (Image) | Embedded In |
|---|---------|------|-----------------|--------------|-------------|
| 1 | System Context | Context Diagram | [system-context.drawio](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/system-context.drawio) | [system-context.png](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/system-context.png) | Section 2.1 |
| 2 | ER Diagram | Entity Relationship | [er-diagram.drawio](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/er-diagram.drawio) | [er-diagram.png](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/er-diagram.png) | Section 4.1 |
| 3 | State — Tool Toggle | State Diagram | [state-tool-toggle.drawio](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/state-tool-toggle.drawio) | [state-tool-toggle.png](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/state-tool-toggle.png) | Section 11.1 |
| 4 | Sequence — Tool Discovery | Sequence Diagram | [sequence-tool-discovery.drawio](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-discovery.drawio) | [sequence-tool-discovery.png](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-discovery.png) | Section 11.2 |
| 5 | Sequence — Tool Management | Sequence Diagram | [sequence-tool-mgmt.drawio](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-mgmt.drawio) | [sequence-tool-mgmt.png](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-tool-mgmt.png) | Section 11.2 |
| 6 | Sequence — Startup | Sequence Diagram | [sequence-startup.drawio](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-startup.drawio) | [sequence-startup.png](c:/projects/kotlin/MCPOrchestration_AntiGravity/documents/MTO-10/diagrams/sequence-startup.png) | Section 11.2 |

<!-- TA enrichment -->
### 11.9 Draw.io XML Diagrams

#### System Context Diagram

```xml
<mxfile>
  <diagram name="System Context — MCP Orchestrator (MTO-10)">
    <mxGraphModel dx="1422" dy="762" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1169" pageHeight="827">
      <root>
        <mxCell id="0"/>
        <mxCell id="1" parent="0"/>
        <!-- MCP Orchestrator (center) -->
        <mxCell id="2" value="MCP Orchestrator Server&#xa;(Kotlin/Ktor)" style="rounded=1;whiteSpace=wrap;fillColor=#dae8fc;strokeColor=#6c8ebf;fontSize=14;fontStyle=1;" vertex="1" parent="1">
          <mxGeometry x="420" y="300" width="220" height="80" as="geometry"/>
        </mxCell>
        <!-- AI Client -->
        <mxCell id="3" value="AI Client&#xa;(Claude, Cursor)" style="shape=actor;whiteSpace=wrap;fillColor=#fff2cc;strokeColor=#d6b656;" vertex="1" parent="1">
          <mxGeometry x="100" y="200" width="80" height="80" as="geometry"/>
        </mxCell>
        <!-- Operator -->
        <mxCell id="4" value="Operator" style="shape=actor;whiteSpace=wrap;fillColor=#fff2cc;strokeColor=#d6b656;" vertex="1" parent="1">
          <mxGeometry x="100" y="400" width="80" height="80" as="geometry"/>
        </mxCell>
        <!-- Upstream MCP Servers -->
        <mxCell id="5" value="Upstream MCP Servers&#xa;(Jira, Filesystem, etc.)" style="rounded=1;whiteSpace=wrap;fillColor=#f8cecc;strokeColor=#b85450;" vertex="1" parent="1">
          <mxGeometry x="750" y="200" width="200" height="60" as="geometry"/>
        </mxCell>
        <!-- PostgreSQL + pgvector -->
        <mxCell id="6" value="PostgreSQL 16+&#xa;+ pgvector" style="shape=cylinder3;whiteSpace=wrap;fillColor=#d5e8d4;strokeColor=#82b366;size=15;" vertex="1" parent="1">
          <mxGeometry x="750" y="350" width="140" height="80" as="geometry"/>
        </mxCell>
        <!-- Ollama -->
        <mxCell id="7" value="Ollama&#xa;(Local Embedding)" style="rounded=1;whiteSpace=wrap;fillColor=#e1d5e7;strokeColor=#9673a6;" vertex="1" parent="1">
          <mxGeometry x="480" y="500" width="140" height="50" as="geometry"/>
        </mxCell>
        <!-- LMStudio -->
        <mxCell id="8" value="LMStudio&#xa;(Local Embedding)" style="rounded=1;whiteSpace=wrap;fillColor=#e1d5e7;strokeColor=#9673a6;" vertex="1" parent="1">
          <mxGeometry x="300" y="500" width="140" height="50" as="geometry"/>
        </mxCell>
        <!-- mcp-servers.json -->
        <mxCell id="9" value="mcp-servers.json" style="shape=document;whiteSpace=wrap;fillColor=#f5f5f5;strokeColor=#666666;" vertex="1" parent="1">
          <mxGeometry x="750" y="470" width="130" height="60" as="geometry"/>
        </mxCell>
        <!-- Edges -->
        <mxCell id="e1" value="find_tools&#xa;execute_dynamic_tool" style="endArrow=classic;startArrow=classic;" edge="1" source="3" target="2" parent="1"/>
        <mxCell id="e2" value="toggle_tool&#xa;reset_tools&#xa;manage_auto_approve" style="endArrow=classic;startArrow=classic;" edge="1" source="4" target="2" parent="1"/>
        <mxCell id="e3" value="tools/list&#xa;tools/call" style="endArrow=classic;startArrow=classic;" edge="1" source="2" target="5" parent="1"/>
        <mxCell id="e4" value="JDBC&#xa;(embeddings, toggle, config)" style="endArrow=classic;startArrow=classic;" edge="1" source="2" target="6" parent="1"/>
        <mxCell id="e5" value="POST /api/embeddings" style="endArrow=classic;startArrow=classic;" edge="1" source="2" target="7" parent="1"/>
        <mxCell id="e6" value="POST /v1/embeddings" style="endArrow=classic;startArrow=classic;" edge="1" source="2" target="8" parent="1"/>
        <mxCell id="e7" value="Read/Write" style="endArrow=classic;startArrow=classic;" edge="1" source="2" target="9" parent="1"/>
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>
```

#### Data Flow Diagram — Tool Indexing Pipeline

```xml
<mxfile>
  <diagram name="Data Flow — Tool Indexing Pipeline">
    <mxGraphModel dx="1200" dy="700" grid="1" gridSize="10" guides="1">
      <root>
        <mxCell id="0"/>
        <mxCell id="1" parent="0"/>
        <!-- Process: ToolIndexer -->
        <mxCell id="p1" value="1.0&#xa;ToolIndexer&#xa;indexAll()" style="ellipse;whiteSpace=wrap;fillColor=#dae8fc;strokeColor=#6c8ebf;" vertex="1" parent="1">
          <mxGeometry x="100" y="200" width="140" height="80" as="geometry"/>
        </mxCell>
        <!-- Process: ToolFilterService -->
        <mxCell id="p2" value="2.0&#xa;ToolFilterService&#xa;applyFilter()" style="ellipse;whiteSpace=wrap;fillColor=#dae8fc;strokeColor=#6c8ebf;" vertex="1" parent="1">
          <mxGeometry x="320" y="200" width="140" height="80" as="geometry"/>
        </mxCell>
        <!-- Process: EmbeddingService -->
        <mxCell id="p3" value="3.0&#xa;EmbeddingService&#xa;generateEmbeddings()" style="ellipse;whiteSpace=wrap;fillColor=#dae8fc;strokeColor=#6c8ebf;" vertex="1" parent="1">
          <mxGeometry x="540" y="200" width="160" height="80" as="geometry"/>
        </mxCell>
        <!-- Process: PgVectorDbClient -->
        <mxCell id="p4" value="4.0&#xa;PgVectorDbClient&#xa;upsert()" style="ellipse;whiteSpace=wrap;fillColor=#dae8fc;strokeColor=#6c8ebf;" vertex="1" parent="1">
          <mxGeometry x="780" y="200" width="140" height="80" as="geometry"/>
        </mxCell>
        <!-- Data Store: Upstream Servers -->
        <mxCell id="d1" value="Upstream MCP Servers" style="shape=parallelogram;whiteSpace=wrap;fillColor=#f8cecc;strokeColor=#b85450;" vertex="1" parent="1">
          <mxGeometry x="60" y="50" width="180" height="50" as="geometry"/>
        </mxCell>
        <!-- Data Store: mcp-servers.json -->
        <mxCell id="d2" value="mcp-servers.json&#xa;(toolFilter config)" style="shape=document;whiteSpace=wrap;fillColor=#f5f5f5;" vertex="1" parent="1">
          <mxGeometry x="300" y="50" width="160" height="50" as="geometry"/>
        </mxCell>
        <!-- Data Store: Ollama/LMStudio -->
        <mxCell id="d3" value="Ollama / LMStudio" style="rounded=1;whiteSpace=wrap;fillColor=#e1d5e7;strokeColor=#9673a6;" vertex="1" parent="1">
          <mxGeometry x="550" y="50" width="140" height="50" as="geometry"/>
        </mxCell>
        <!-- Data Store: PostgreSQL -->
        <mxCell id="d4" value="PostgreSQL&#xa;tool_embeddings" style="shape=cylinder3;whiteSpace=wrap;fillColor=#d5e8d4;strokeColor=#82b366;size=10;" vertex="1" parent="1">
          <mxGeometry x="780" y="50" width="140" height="60" as="geometry"/>
        </mxCell>
        <!-- Flows -->
        <mxCell id="f1" value="tools/list → List&lt;ToolDefinition&gt;" style="endArrow=classic;" edge="1" source="d1" target="p1" parent="1"/>
        <mxCell id="f2" value="All tools" style="endArrow=classic;" edge="1" source="p1" target="p2" parent="1"/>
        <mxCell id="f3" value="toolFilter config" style="endArrow=classic;" edge="1" source="d2" target="p2" parent="1"/>
        <mxCell id="f4" value="Filtered tools" style="endArrow=classic;" edge="1" source="p2" target="p3" parent="1"/>
        <mxCell id="f5" value="text → vector" style="endArrow=classic;startArrow=classic;" edge="1" source="p3" target="d3" parent="1"/>
        <mxCell id="f6" value="VectorPoint[]" style="endArrow=classic;" edge="1" source="p3" target="p4" parent="1"/>
        <mxCell id="f7" value="UPSERT" style="endArrow=classic;" edge="1" source="p4" target="d4" parent="1"/>
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>
```

#### Integration Architecture Diagram

```xml
<mxfile>
  <diagram name="Integration Architecture — MTO-10">
    <mxGraphModel dx="1200" dy="700" grid="1" gridSize="10" guides="1">
      <root>
        <mxCell id="0"/>
        <mxCell id="1" parent="0"/>
        <!-- Orchestrator boundary -->
        <mxCell id="b1" value="MCP Orchestrator Server" style="rounded=1;whiteSpace=wrap;fillColor=none;strokeColor=#6c8ebf;dashed=1;fontSize=14;fontStyle=1;verticalAlign=top;" vertex="1" parent="1">
          <mxGeometry x="200" y="100" width="500" height="400" as="geometry"/>
        </mxCell>
        <!-- Protocol Layer -->
        <mxCell id="l1" value="McpServerFactory&#xa;(5 MCP tools)" style="rounded=1;whiteSpace=wrap;fillColor=#dae8fc;strokeColor=#6c8ebf;" vertex="1" parent="1">
          <mxGeometry x="320" y="130" width="180" height="50" as="geometry"/>
        </mxCell>
        <!-- Service Layer -->
        <mxCell id="l2a" value="ToolDiscoveryServiceImpl" style="rounded=1;whiteSpace=wrap;fillColor=#fff2cc;strokeColor=#d6b656;" vertex="1" parent="1">
          <mxGeometry x="220" y="220" width="180" height="40" as="geometry"/>
        </mxCell>
        <mxCell id="l2b" value="ToolToggleService" style="rounded=1;whiteSpace=wrap;fillColor=#fff2cc;strokeColor=#d6b656;fontStyle=1;" vertex="1" parent="1">
          <mxGeometry x="420" y="220" width="140" height="40" as="geometry"/>
        </mxCell>
        <mxCell id="l2c" value="AutoApproveService" style="rounded=1;whiteSpace=wrap;fillColor=#fff2cc;strokeColor=#d6b656;fontStyle=1;" vertex="1" parent="1">
          <mxGeometry x="580" y="220" width="140" height="40" as="geometry"/>
        </mxCell>
        <!-- Infrastructure Layer -->
        <mxCell id="l3a" value="OllamaEmbeddingService" style="rounded=1;whiteSpace=wrap;fillColor=#e1d5e7;strokeColor=#9673a6;fontStyle=1;" vertex="1" parent="1">
          <mxGeometry x="220" y="310" width="170" height="40" as="geometry"/>
        </mxCell>
        <mxCell id="l3b" value="PgVectorDbClient" style="rounded=1;whiteSpace=wrap;fillColor=#d5e8d4;strokeColor=#82b366;fontStyle=1;" vertex="1" parent="1">
          <mxGeometry x="420" y="310" width="140" height="40" as="geometry"/>
        </mxCell>
        <mxCell id="l3c" value="ConfigDbSyncService" style="rounded=1;whiteSpace=wrap;fillColor=#d5e8d4;strokeColor=#82b366;fontStyle=1;" vertex="1" parent="1">
          <mxGeometry x="580" y="310" width="140" height="40" as="geometry"/>
        </mxCell>
        <!-- External: PostgreSQL -->
        <mxCell id="ext1" value="PostgreSQL 16&#xa;+ pgvector" style="shape=cylinder3;whiteSpace=wrap;fillColor=#d5e8d4;strokeColor=#82b366;size=10;" vertex="1" parent="1">
          <mxGeometry x="450" y="530" width="120" height="60" as="geometry"/>
        </mxCell>
        <!-- External: Ollama -->
        <mxCell id="ext2" value="Ollama&#xa;:11434" style="rounded=1;whiteSpace=wrap;fillColor=#e1d5e7;strokeColor=#9673a6;" vertex="1" parent="1">
          <mxGeometry x="220" y="530" width="100" height="50" as="geometry"/>
        </mxCell>
        <!-- External: Config File -->
        <mxCell id="ext3" value="mcp-servers.json" style="shape=document;whiteSpace=wrap;fillColor=#f5f5f5;" vertex="1" parent="1">
          <mxGeometry x="620" y="530" width="120" height="50" as="geometry"/>
        </mxCell>
        <!-- Bold = NEW components -->
        <mxCell id="note" value="Bold = NEW components (MTO-10)" style="text;fontSize=11;fontStyle=2;" vertex="1" parent="1">
          <mxGeometry x="220" y="430" width="250" height="20" as="geometry"/>
        </mxCell>
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>
```

#### State Diagram — Tool Toggle Lifecycle (draw.io XML)

```xml
<mxfile>
  <diagram name="State Diagram — Tool Toggle Lifecycle">
    <mxGraphModel dx="800" dy="500" grid="1" gridSize="10" guides="1">
      <root>
        <mxCell id="0"/>
        <mxCell id="1" parent="0"/>
        <!-- Initial state -->
        <mxCell id="s0" value="" style="ellipse;fillColor=#000000;strokeColor=#000000;" vertex="1" parent="1">
          <mxGeometry x="50" y="200" width="20" height="20" as="geometry"/>
        </mxCell>
        <!-- ENABLED state -->
        <mxCell id="s1" value="ENABLED&#xa;(default)&#xa;—&#xa;• Visible in find_tools&#xa;• Executable via execute_dynamic_tool" style="rounded=1;whiteSpace=wrap;fillColor=#d5e8d4;strokeColor=#82b366;align=center;" vertex="1" parent="1">
          <mxGeometry x="150" y="160" width="250" height="100" as="geometry"/>
        </mxCell>
        <!-- DISABLED state -->
        <mxCell id="s2" value="DISABLED&#xa;—&#xa;• Hidden from find_tools&#xa;• Blocked from execute_dynamic_tool&#xa;• ToolDisabledException thrown" style="rounded=1;whiteSpace=wrap;fillColor=#f8cecc;strokeColor=#b85450;align=center;" vertex="1" parent="1">
          <mxGeometry x="500" y="160" width="250" height="100" as="geometry"/>
        </mxCell>
        <!-- Transitions -->
        <mxCell id="t0" value="startup / new session" style="endArrow=classic;" edge="1" source="s0" target="s1" parent="1"/>
        <mxCell id="t1" value="toggle_tool(enabled=false)" style="endArrow=classic;curved=1;" edge="1" source="s1" target="s2" parent="1">
          <mxGeometry relative="1" as="geometry"><Array as="points"><mxPoint x="450" y="140"/></Array></mxGeometry>
        </mxCell>
        <mxCell id="t2" value="toggle_tool(enabled=true)&#xa;OR reset_tools()" style="endArrow=classic;curved=1;" edge="1" source="s2" target="s1" parent="1">
          <mxGeometry relative="1" as="geometry"><Array as="points"><mxPoint x="450" y="290"/></Array></mxGeometry>
        </mxCell>
        <mxCell id="t3" value="server restart&#xa;(new session_id)" style="endArrow=classic;dashed=1;" edge="1" source="s2" target="s1" parent="1">
          <mxGeometry relative="1" as="geometry"><Array as="points"><mxPoint x="450" y="340"/></Array></mxGeometry>
        </mxCell>
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>
```

### 11.10 Open Issues

<!-- TA enrichment -->

| # | Issue | Impact | Owner | Target Date | Status |
|---|-------|--------|-------|-------------|--------|
| OI-1 | **Session cleanup strategy**: Toggle states accumulate in `tool_toggle_state` table after sessions end. Need a cleanup policy (TTL? cron job? on-startup purge of old sessions?) | Medium — DB bloat over time | Dev Team | Before v1.1 release | Open |
| OI-2 | **Hybrid search weight tuning**: The 70/30 vector/keyword split (BR-2.3) is a design decision, not from BRD. Should this be configurable in `application.yml`? | Low — affects search quality | Project Lead | Sprint review | Open |
| OI-3 | **VectorDbClient interface extension**: Adding `hybridSearch()` method changes the interface contract. Existing `QdrantVectorDbClient` and `FaissVectorDbClient` need default implementations. Verify no downstream consumers are affected. | Medium — breaking change risk | Dev Team | Before implementation | Open |
| OI-4 | **UpstreamServerConfig schema extension**: Adding `disabled`, `autoApprove`, `toolFilter` fields to the `@Serializable` data class. Must verify kaml deserialization handles missing fields gracefully (defaults) for backward compatibility with existing `application.yml` files. | High — backward compat | Dev Team | Before implementation | Open |
| OI-5 | **PostgreSQL pgvector extension availability**: The `CREATE EXTENSION IF NOT EXISTS vector` requires superuser or extension already installed. Document infrastructure prerequisite clearly for DevOps. | Medium — deployment blocker | DevOps Team | Before first deployment | Open |
| OI-6 | **Embedding model consistency**: If the operator changes the embedding model (e.g., from `nomic-embed-text` to `all-minilm`), existing vectors in `tool_embeddings` become incompatible. Need a re-index trigger or model version tracking. | High — silent search degradation | Dev Team | Before v1.1 release | Open |
| OI-7 | **Config file path resolution**: `mcp-servers.json` path is currently resolved relative to CWD. For `manage_auto_approve` to write back, the path must be deterministic. Consider adding `orchestrator.config_file_path` to `application.yml`. | Low — operational clarity | Dev Team | During implementation | Open |