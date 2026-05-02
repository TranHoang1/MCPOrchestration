# Project Structure — MCPOrchestration

**Last Updated:** 2026-05-02
**Project Type:** gradle-kotlin (greenfield — no source code yet)

## Status

This is a **greenfield project**. Source code will be created during Phase 5 (Implementation).

Current documents:
- BRD v2: `documents/MTO-5/BRD.md`
- FSD v1: `documents/MTO-5/FSD.md`
- TDD v1: `documents/MTO-5/TDD.md`

## Planned Modules (from TDD)

| Module | Purpose | Language | Framework |
|--------|---------|----------|-----------|
| mcp-orchestrator | MCP Orchestration Server | Kotlin 2.3.20 | Ktor 3.4.0 |

## Planned Package Structure (from TDD §5.1)

```
com.orchestrator.mcp/
├── Application.kt
├── config/
├── transport/
├── protocol/
├── discovery/
├── execution/
├── registry/
├── embedding/
├── vectordb/
├── upstream/
├── model/
├── di/
└── util/
```

## Key Dependencies (from TDD §1.3)

| Dependency | Version |
|-----------|---------|
| Kotlin | 2.3.20 |
| Ktor | 3.4.0 |
| Koin | 4.1.1 |
| kotlinx.serialization | 1.8.1 |
| kotlinx.coroutines | 1.10.2 |
| Qdrant (Vector DB) | 1.9+ |
| OpenAI Embeddings | API v1 |
