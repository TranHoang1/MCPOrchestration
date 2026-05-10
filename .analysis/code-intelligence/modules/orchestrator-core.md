# Module Analysis — orchestrator-core

**Last Updated:** 2026-07-06
**Language:** Kotlin 2.3.20 | **Platform:** JVM 21

## Overview

Shared library module containing domain models, configuration DTOs, exception hierarchy, and utility functions used by all other modules.

## Key Files

| File | Purpose |
|------|---------|
| `ConfigurationManager.kt` | Interface for config loading |
| `InfraConfig.kt` | Infrastructure configuration DTOs |
| `OrchestratorConfig.kt` | @Serializable config data classes (9+ classes) |
| `ErrorCodes.kt` | MCP JSON-RPC error codes |
| `Exceptions.kt` | Sealed exception hierarchy (McpOrchestratorException) |
| `ToolDefinition.kt` | ToolDefinition + ToolEntry domain models |
| `RetryUtils.kt` | Exponential backoff retry utility |

## Exception Hierarchy

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

## Dependencies

- kotlinx.serialization
- kaml (YAML)
- kotlinx.coroutines
