# Module Analysis — orchestrator-client

**Last Updated:** 2026-07-06
**Language:** Kotlin 2.3.20 | **Platform:** JVM 21

## Overview

Client-side services for connecting to upstream MCP servers, generating embeddings, and interacting with vector databases.

## Key Files

| File | Purpose |
|------|---------|
| `EmbeddingService.kt` | Interface — text → vector embedding |
| `OpenAiEmbeddingService.kt` | OpenAI API embedding client |
| `OllamaEmbeddingService.kt` | Ollama local LLM embedding client |
| `LmStudioEmbeddingService.kt` | LM Studio embedding client |
| `UpstreamServerManager.kt` | Interface — manage upstream MCP connections |
| `UpstreamServerManagerImpl.kt` | Implementation — connect, disconnect, route |
| `McpConnection.kt` | Interface — single upstream connection |
| `StdioMcpConnection.kt` | stdio process connection |
| `HttpMcpConnection.kt` | HTTP/SSE connection |
| `HealthMonitor.kt` | Periodic health checks + auto-reconnect |
| `VectorDbClient.kt` | Interface — vector DB operations |
| `QdrantVectorDbClient.kt` | Qdrant REST API client |
| `PgVectorDbClient.kt` | PostgreSQL pgvector client |
| `FaissVectorDbClient.kt` | FAISS in-memory (local fallback) |
| `DatabaseFactory.kt` | HikariCP DataSource factory |
| `DatabaseInitializer.kt` | Schema initialization |

## Patterns

- **Strategy Pattern**: Multiple embedding providers (OpenAI, Ollama, LM Studio)
- **Strategy Pattern**: Multiple vector DB backends (Qdrant, PgVector, FAISS)
- **Interface/Impl**: All services
- **Health Monitoring**: Coroutine-based periodic checks

## Dependencies

- Ktor Client (CIO)
- kotlinx.coroutines
- PostgreSQL + HikariCP
