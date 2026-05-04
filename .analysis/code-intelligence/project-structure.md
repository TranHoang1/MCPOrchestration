# Project Structure — MCPOrchestration

**Last Updated:** 2026-05-03
**Indexed By:** SM agent (hybrid: script + manual)

## 1. Project Overview

| Property | Value |
|----------|-------|
| **Project Name** | MCPOrchestration |
| **Type** | Single-module Gradle application |
| **Language** | Kotlin 2.3.20 |
| **Platform** | JVM 21 |
| **Framework** | Ktor 3.4.0 (server + client) |
| **DI** | Koin 4.1.1 |
| **Serialization** | kotlinx.serialization 1.8.1 + kaml 0.77.0 (YAML) |
| **MCP SDK** | io.modelcontextprotocol:kotlin-sdk-server 0.12.0 |
| **Build Tool** | Gradle (Kotlin DSL) with version catalog |
| **Packaging** | Fat JAR (`mcp-orchestrator-all.jar`) via Ktor plugin |
| **Transport** | stdio (default), HTTP (Ktor/Netty) |

## 2. Tech Stack

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
| Vector DB | Qdrant | 1.9+ (external) |
| Embeddings | OpenAI API | text-embedding-3-small |
| Testing | JUnit 5 | 5.11.4 |
| Testing | Kotest | 5.9.1 |
| Testing | MockK | 1.14.2 |
| Testing | Testcontainers | 1.21.1 |
| Testing | Ktor Test Host | 3.4.0 |

## 3. Package Structure

```
com.orchestrator.mcp/
├── Application.kt                    # Entry point — main(), stdio/HTTP transport bootstrap
├── config/
│   ├── OrchestratorConfig.kt         # @Serializable config data classes (9 classes)
│   ├── ConfigurationManager.kt       # Interface + Impl — YAML config loading, env var resolution
│   ├── ConfigValidator.kt            # Config validation rules
│   ├── ExternalConfigScanner.kt      # Scan external config directories
│   └── JsonConfigLoader.kt           # JSON config format support (mcpServers format)
├── di/
│   └── AppModule.kt                  # Koin module — all DI bindings
├── discovery/
│   ├── ToolDiscoveryService.kt       # Interface — find_tools business logic
│   ├── ToolDiscoveryServiceImpl.kt   # Impl — semantic + keyword search
│   ├── KeywordSearchEngine.kt        # TF-IDF keyword fallback search
│   └── model/
│       └── FindToolsResponse.kt      # find_tools response DTOs
├── embedding/
│   ├── EmbeddingService.kt           # Interface — text → vector embedding
│   └── OpenAiEmbeddingService.kt     # Impl — OpenAI API client
├── execution/
│   ├── ToolExecutionDispatcher.kt    # Interface — execute_tool routing
│   ├── ToolExecutionDispatcherImpl.kt # Impl — route to upstream, timeout, retry
│   └── model/
│       └── ExecuteToolResponse.kt    # execute_tool response DTOs
├── model/
│   ├── ErrorCodes.kt                 # MCP JSON-RPC error codes
│   ├── Exceptions.kt                 # Sealed exception hierarchy (8 exception types)
│   └── ToolDefinition.kt             # ToolDefinition + ToolEntry domain models
├── protocol/
│   ├── JsonRpcHandler.kt             # JSON-RPC message parsing
│   ├── McpProtocolHandler.kt         # MCP protocol method routing
│   ├── McpServerFactory.kt           # MCP SDK Server creation + tool registration
│   ├── McpToolRegistrar.kt           # Register find_tools + execute_tool as MCP tools
│   ├── McpToolSchemas.kt             # JSON Schema definitions for MCP tools
│   └── model/
│       ├── JsonRpcMessage.kt         # JSON-RPC request/response DTOs
│       └── McpMessages.kt            # MCP-specific message DTOs (initialize, tools/list, etc.)
├── registry/
│   ├── ToolRegistry.kt               # Interface — in-memory tool registry
│   ├── ToolRegistryImpl.kt           # Impl — ConcurrentHashMap-based registry
│   └── ToolIndexer.kt                # Index tools into vector DB on connect
├── transport/
│   ├── McpTransport.kt               # Interface — message transport abstraction
│   └── StdioTransport.kt             # Impl — stdin/stdout transport
├── upstream/
│   ├── UpstreamServerManager.kt      # Interface — manage upstream MCP server connections
│   ├── UpstreamServerManagerImpl.kt  # Impl — connect, disconnect, route
│   ├── McpConnection.kt              # Interface — single upstream connection
│   ├── StdioMcpConnection.kt         # Impl — stdio process connection
│   ├── HttpMcpConnection.kt          # Impl — HTTP/SSE connection
│   ├── HealthMonitor.kt              # Periodic health checks + auto-reconnect
│   └── model/
│       ├── ServerState.kt            # Enum: DISCONNECTED, CONNECTING, CONNECTED, ERROR
│       └── UpstreamServerInfo.kt     # Upstream server runtime info DTO
├── util/
│   └── RetryUtils.kt                 # Exponential backoff retry utility
└── vectordb/
    ├── VectorDbClient.kt             # Interface — vector DB operations
    ├── QdrantVectorDbClient.kt       # Impl — Qdrant REST API client
    ├── FaissVectorDbClient.kt        # Impl — FAISS in-memory vector DB (local fallback)
    └── model/
        └── VectorPoint.kt            # VectorPoint + SearchResult DTOs
```

## 4. Source Code Statistics

| Category | Count |
|----------|-------|
| Total Kotlin files | 74 |
| Main source files | 44 |
| Test source files | 30 |
| Classes (total) | 124 |
| Functions (total) | 146 |
| Packages (main) | 12 |

### Main Source Breakdown

| Package | Files | Key Classes |
|---------|-------|-------------|
| `config` | 5 | OrchestratorConfig, ConfigurationManager, ConfigValidator, ExternalConfigScanner, JsonConfigLoader |
| `di` | 1 | appModule() |
| `discovery` | 4 | ToolDiscoveryService, ToolDiscoveryServiceImpl, KeywordSearchEngine, FindToolsResponse |
| `embedding` | 2 | EmbeddingService, OpenAiEmbeddingService |
| `execution` | 3 | ToolExecutionDispatcher, ToolExecutionDispatcherImpl, ExecuteToolResponse |
| `model` | 3 | ErrorCodes, McpOrchestratorException (sealed), ToolDefinition |
| `protocol` | 6 | JsonRpcHandler, McpProtocolHandler, McpServerFactory, McpToolRegistrar, McpToolSchemas |
| `registry` | 3 | ToolRegistry, ToolRegistryImpl, ToolIndexer |
| `transport` | 2 | McpTransport, StdioTransport |
| `upstream` | 7 | UpstreamServerManager, McpConnection, StdioMcpConnection, HttpMcpConnection, HealthMonitor |
| `util` | 1 | RetryUtils |
| `vectordb` | 4 | VectorDbClient, QdrantVectorDbClient, FaissVectorDbClient |

## 5. Architecture Patterns

| Pattern | Implementation |
|---------|---------------|
| **DI** | Koin module (`AppModule.kt`) — all bindings in single module |
| **Interface/Impl** | All services use interface + impl pattern (e.g., `ToolRegistry` / `ToolRegistryImpl`) |
| **Sealed Exceptions** | `McpOrchestratorException` sealed class with 8 typed exceptions |
| **Transport Abstraction** | `McpTransport` interface with stdio/HTTP implementations |
| **Connection Abstraction** | `McpConnection` interface with stdio/HTTP upstream implementations |
| **Strategy Pattern** | `VectorDbClient` with Qdrant (remote) and FAISS (local) strategies |
| **Fallback** | Semantic search → keyword search fallback in ToolDiscoveryServiceImpl |
| **Health Monitoring** | Periodic coroutine-based health checks with auto-reconnect |
| **Config Hierarchy** | YAML config → env var resolution → validation |
| **MCP SDK Integration** | `McpServerFactory` creates SDK `Server`, `McpToolRegistrar` registers tools |

## 6. DI Bindings (Koin)

| Interface | Implementation | Scope |
|-----------|---------------|-------|
| `ConfigurationManager` | `ConfigurationManagerImpl` | Singleton |
| `OrchestratorConfig` | from ConfigurationManager | Singleton |
| `HttpClient` | Ktor CIO client | Singleton |
| `EmbeddingService` | `OpenAiEmbeddingService` | Singleton |
| `VectorDbClient` | `QdrantVectorDbClient` | Singleton |
| `ToolRegistry` | `ToolRegistryImpl` | Singleton |
| `UpstreamServerManager` | `UpstreamServerManagerImpl` | Singleton |
| `HealthMonitor` | `HealthMonitor` | Singleton |
| `KeywordSearchEngine` | `KeywordSearchEngine` | Singleton |
| `ToolDiscoveryService` | `ToolDiscoveryServiceImpl` | Singleton |
| `ToolExecutionDispatcher` | `ToolExecutionDispatcherImpl` | Singleton |
| `McpServerFactory` | `McpServerFactory` | Singleton |

## 7. Configuration Reference

Config file: `src/main/resources/application.yml` (YAML, parsed by kaml)

| Section | Key Properties |
|---------|---------------|
| `orchestrator.server` | `port` (8080), `transport` (stdio) |
| `orchestrator.discovery` | `top_k` (5), `similarity_threshold` (0.7), `max_query_length` (2000), `fallback_to_keyword` (true) |
| `orchestrator.execution` | `timeout_seconds` (30), `validate_arguments` (true), `max_retries` (1) |
| `orchestrator.embedding` | `provider` (openai), `model` (text-embedding-3-small), `api_key` (env), `dimensions` (768) |
| `orchestrator.vector_db` | `provider` (qdrant), `host` (localhost), `port` (6333), `collection_name` (mcp_tools) |
| `orchestrator.health` | `check_interval_seconds` (30), `auto_reconnect` (true), `max_reconnect_attempts` (5) |
| `orchestrator.upstream_servers` | Array of `{name, transport, command, args, url}` |

## 8. Exception Hierarchy

```
McpOrchestratorException (sealed)
├── InvalidParamsException
├── ToolNotFoundException
├── ServerUnavailableException
├── ExecutionTimeoutException
├── UpstreamErrorException
├── VectorDbUnavailableException
├── EmbeddingServiceException
├── ConfigException
└── GenericMcpException
```

## 9. Test Structure

| Test Category | Directory | Files | Framework |
|--------------|-----------|-------|-----------|
| Unit Tests | `src/test/kotlin/.../config/` | 6 | Kotest + MockK |
| Unit Tests | `src/test/kotlin/.../discovery/` | 1 | Kotest + MockK |
| Unit Tests | `src/test/kotlin/.../embedding/` | 1 | Kotest + MockK |
| Unit Tests | `src/test/kotlin/.../execution/` | 1 | Kotest + MockK |
| Unit Tests | `src/test/kotlin/.../protocol/` | 2 | Kotest + MockK |
| Unit Tests | `src/test/kotlin/.../registry/` | 2 | Kotest + MockK |
| Unit Tests | `src/test/kotlin/.../upstream/` | 1 | Kotest + MockK |
| Unit Tests | `src/test/kotlin/.../util/` | 1 | Kotest + MockK |
| Unit Tests | `src/test/kotlin/.../vectordb/` | 1 | Kotest + MockK |
| Integration Tests | `src/test/kotlin/.../it/` | 7 | Kotest + Ktor TestHost |
| E2E API Tests | `src/test/kotlin/.../e2e/` | 5 | Kotest + Ktor TestHost |

### Integration Test Base

`IntegrationTestBase.kt` provides reusable test stacks:
- `DiscoveryStack` — mock embedding + vector DB + registry + keyword engine
- `ExecutionStack` — mock upstream manager + registry + config
- `ProtocolStack` — full MCP server factory with mocked dependencies
- `IndexerStack` — mock embedding + vector DB + registry

## 10. Build & Run Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build + run all tests |
| `./gradlew test` | Run all tests only |
| `./gradlew buildFatJar` | Build fat JAR (`build/libs/mcp-orchestrator-all.jar`) |
| `java -jar build/libs/mcp-orchestrator-all.jar` | Run server (stdio transport) |
| `java -jar build/libs/mcp-orchestrator-all.jar --config /path/to/config.yml` | Run with external config |

## 11. External Dependencies (Runtime)

| Service | Required | Purpose |
|---------|----------|---------|
| **Qdrant** | Yes (for semantic search) | Vector database for tool embeddings |
| **OpenAI API** | Yes (for semantic search) | Text embedding generation |
| **Upstream MCP Servers** | Yes (for tool execution) | Actual tool providers (configured in YAML) |

> **Note:** FAISS (`FaissVectorDbClient`) provides a local in-memory fallback when Qdrant is unavailable, but is not the default.
