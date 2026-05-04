# Discrepancy Report — MTO-10 FSD vs Codebase

## Document Information

| Field | Value |
|-------|-------|
| Date | 2026-05-03 |
| Author | SA Agent |
| FSD Version | 1.1 |
| Codebase Commit | HEAD |

---

## Discrepancies Found

### D-01: EmbeddingConfig missing `base_url` field

| Aspect | Detail |
|--------|--------|
| **FSD Reference** | §5.2a — EmbeddingConfig add `base_url` |
| **Codebase** | `src/main/kotlin/.../config/OrchestratorConfig.kt` — `EmbeddingConfig` has no `base_url` field |
| **Impact** | Medium — Required for Ollama/LMStudio provider URL configuration |
| **Resolution** | Add `@SerialName("base_url") val baseUrl: String = ""` to `EmbeddingConfig` |

### D-02: VectorDbConfig missing pgvector fields

| Aspect | Detail |
|--------|--------|
| **FSD Reference** | §5.2a — VectorDbConfig add `connection_string`, `hnsw_m`, `hnsw_ef_construction` |
| **Codebase** | `VectorDbConfig` only has `provider`, `host`, `port`, `collection_name` |
| **Impact** | High — Cannot configure pgvector without these fields |
| **Resolution** | Add 3 new fields with defaults to `VectorDbConfig` |

### D-03: UpstreamServerConfig missing `disabled`, `toolFilter`, `autoApprove`

| Aspect | Detail |
|--------|--------|
| **FSD Reference** | §5.2a — UpstreamServerConfig add `disabled`, `toolFilter`, `autoApprove` |
| **Codebase** | `UpstreamServerConfig` has: `name`, `transport`, `command`, `args`, `env`, `url` only |
| **Impact** | High — Required for tool filtering and auto-approve features |
| **Resolution** | Add 3 new fields with backward-compatible defaults |

### D-04: No `SessionConfig` data class exists

| Aspect | Detail |
|--------|--------|
| **FSD Reference** | §5.2a — New `SessionConfig` data class, added to `OrchestratorSettings` |
| **Codebase** | `OrchestratorSettings` has no `session` field |
| **Impact** | Medium — Required for toggle state session isolation |
| **Resolution** | Create `SessionConfig` data class, add to `OrchestratorSettings` |

### D-05: FSD references `ToolFilterConfig` but no such class exists

| Aspect | Detail |
|--------|--------|
| **FSD Reference** | §5.2a — New `ToolFilterConfig` data class |
| **Codebase** | No `ToolFilterConfig` class exists |
| **Impact** | Medium — Required for tool filtering feature |
| **Resolution** | Create `@Serializable data class ToolFilterConfig(val mode: String, val tools: List<String>)` |

### D-06: FSD §5.2b mentions `VectorDbClient` interface change for hybrid search

| Aspect | Detail |
|--------|--------|
| **FSD Reference** | §5.2b — Discusses adding optional `queryText` parameter to `search()` |
| **Codebase** | `VectorDbClient.search()` has signature: `search(collectionName, vector, limit, scoreThreshold)` |
| **Impact** | Low — FSD itself marks this as Open Issue #6 |
| **Resolution** | TDD decision: Keep interface unchanged. `PgVectorDbClient` adds `hybridSearch()` as additional method. Cast in `ToolDiscoveryServiceImpl` when provider is pgvector. |

### D-07: FSD references `management` package but no such package exists

| Aspect | Detail |
|--------|--------|
| **FSD Reference** | §13.7 — New package `com.orchestrator.mcp.management` |
| **Codebase** | Package does not exist (expected — it's new) |
| **Impact** | None — This is expected for new features |
| **Resolution** | Create package as part of implementation |

### D-08: ErrorCodes missing new codes referenced in FSD

| Aspect | Detail |
|--------|--------|
| **FSD Reference** | §10.1 — `TOOL_DISABLED`, `SERVER_DISABLED`, `CONFIG_WRITE_FAILED`, `CONFIG_CORRUPTED`, `PGVECTOR_UNAVAILABLE`, `EMBEDDING_PROVIDER_ERROR`, `SYNC_FAILED` |
| **Codebase** | `ErrorCodes.kt` has 9 codes, none of the new ones |
| **Impact** | Medium — Required for proper error handling |
| **Resolution** | Add all new error codes to `ErrorCodes.kt` |

### D-09: Exceptions.kt missing `ToolDisabledException` and `ConfigWriteException`

| Aspect | Detail |
|--------|--------|
| **FSD Reference** | §5.2b — New exception types in sealed hierarchy |
| **Codebase** | `McpOrchestratorException` sealed class has 8 types, missing the 2 new ones |
| **Impact** | Medium — Required for toggle and config write error handling |
| **Resolution** | Add `ToolDisabledException` and `ConfigWriteException` to sealed hierarchy |

### D-10: FSD assumes `mcp-servers.json` but codebase uses `application.yml` for upstream servers

| Aspect | Detail |
|--------|--------|
| **FSD Reference** | §3.5, §3.6 — References `mcp-servers.json` for config writes and Config-DB sync |
| **Codebase** | `ConfigurationManagerImpl` loads from `application.yml`. `JsonConfigLoader` supports `mcpServers` JSON format but as an alternative config source, not the primary one. |
| **Impact** | Medium — FSD assumes `mcp-servers.json` is the primary config file for upstream servers. Codebase has `JsonConfigLoader` that can read this format, but `manage_auto_approve` writes need to target the correct file. |
| **Resolution** | `manage_auto_approve` writes to the JSON config file path (discovered via `JsonConfigLoader` or configured). `ConfigDbSyncService` reads from whichever config source provides upstream servers. Implementation must handle both YAML and JSON config sources. |

---

## Summary

| Severity | Count | Description |
|----------|-------|-------------|
| High | 2 | Missing config fields required for core features (D-02, D-03) |
| Medium | 6 | Missing data classes, error codes, exceptions, config source ambiguity |
| Low | 1 | Interface change decision (D-06) |
| None | 1 | Expected new package (D-07) |

**Total: 10 discrepancies identified.** All are expected gaps between the FSD (which describes the target state) and the current codebase (MTO-5 baseline). No contradictions found — all discrepancies are additive changes required by MTO-10.
