# Module Analysis — kb-server

**Last Updated:** 2026-07-06
**Language:** Kotlin 2.3.20 | **Platform:** JVM 21

## Overview

Standalone Knowledge Base server. Provides MCP tools for ingesting and searching knowledge base entries. Handles PII masking, content segmentation, embedding generation, and vector indexing. Also serves a graph viewer UI.

## Key Packages

| Package | Purpose | Key Classes |
|---------|---------|-------------|
| `config` | KB configuration | KbConfig, KbConfigLoader, KbConfigSections |
| `di` | Koin DI module | KbDiModule |
| `protocol` | MCP protocol | KbMcpServerFactory, KbToolRegistrar, KbToolHandler |
| `transport` | HTTP transport | KbHttpTransport (serves MCP + static + graph) |
| `store` | KB storage | (database, encryption, vector, repository) |
| `queue` | Async processing | DualPriorityQueue, QueueWorker, CrashRecoveryService |
| `masking` | PII masking | PiiDetector, PiiMaskingEngine |
| `audit` | Audit trail | AuditService |
| `network` | Network utilities | NetworkService |
| `graph` | Graph viewer | GraphService, GraphDataRepository, GraphRoutes |

## MCP Tools

| Tool | Description |
|------|-------------|
| `kb_ingest` | Ingest content into KB (auto: PII mask → segment → embed → index) |
| `kb_search` | Semantic search with RLS filtering |

## HTTP Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/mcp` | MCP protocol endpoint |
| GET | `/sync/graph/{projectKey}` | Graph data |
| GET | `/sync/graph-viewer` | Graph viewer HTML |
| GET | `/graph/{projectKey}` | Graph data (alias) |
| GET | `/static/*` | Static files |
| GET | `/health` | Health check |

## Content Pipeline

```
kb_ingest request
  → PII Detection + Masking
  → Content Segmentation (LangChain4j chunking)
  → Embedding Generation (OpenAI/Ollama)
  → Vector Indexing (pgvector)
  → Metadata Storage (PostgreSQL)
```

## Dependencies

- orchestrator-core
- orchestrator-client
- Ktor Client (CIO)
- MCP Kotlin SDK
- Koin
- PostgreSQL + HikariCP + pgvector
