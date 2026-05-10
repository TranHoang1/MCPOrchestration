# Release Notes (RLN)

## MCPOrchestration — MTO-35: KB Refinery — Semantic Entity Linking

---

## Release Information

| Field | Value |
|-------|-------|
| Release Version | 1.3.0 |
| Release Date | 2026-05-09 |
| Jira Ticket | MTO-35 |
| Environment | DEV / SIT / UAT / PROD |
| Author | DevOps Agent |
| Status | Draft |

---

## 1. What's New

### 1.1 Feature Summary

The MCP Orchestrator now includes **Semantic Entity Linking** — an automatic system that detects and creates relationships between KB entries based on semantic similarity. When a new KB entry is ingested, the system generates an embedding, searches for similar entries using HNSW-indexed cosine similarity, and persists links above a configurable threshold.

**Key benefits:**
- **Automatic discovery:** Related tickets are linked without manual effort
- **Configurable sensitivity:** Similarity threshold tunable per deployment (default 0.75)
- **Fast queries:** HNSW index enables < 100ms similarity search across 10,000+ entries
- **Batch support:** Bulk linking for migration/re-indexing scenarios

### 1.2 User-Facing Changes

| # | Change | Description | Impact |
|---|--------|-------------|--------|
| 1 | Auto-linking on ingest | New KB entries automatically linked to similar entries | High — enables dependency discovery |
| 2 | findSimilar API | Query for related tickets by similarity score | High — enables network visualization |
| 3 | Batch linking | Bulk link multiple entries in one operation | Medium — migration support |
| 4 | Configurable threshold | Tune link sensitivity via configuration | Low — admin configuration |

---

## 2. Technical Changes

### 2.1 New Package

```
com.orchestrator.mcp.linking/
├── EntityLinkingService.kt           (interface)
├── EntityLinkingServiceImpl.kt       (implementation ~80 lines)
├── model/
│   ├── EntityLink.kt                 (data class)
│   ├── LinkingConfig.kt              (configuration)
│   └── LinkingResult.kt              (result wrapper)
├── repository/
│   ├── EntityLinkRepository.kt       (interface)
│   └── EntityLinkRepositoryImpl.kt   (JDBC implementation ~75 lines)
└── di/
    └── LinkingModule.kt              (Koin module)
```

### 2.2 Database Changes

| Type | Object | Description |
|------|--------|-------------|
| New Table | `entity_links` | Stores semantic links between KB entries |
| New Index | `idx_entity_links_source` | Query by source issue key |
| New Index | `idx_entity_links_target` | Query by target issue key |
| New Index | `idx_entity_links_score` | Sort by similarity score |

### 2.3 Vector DB Changes

| Type | Object | Description |
|------|--------|-------------|
| New Collection | `kb_entity_embeddings` | HNSW-indexed collection for KB entry vectors (768 dims, cosine distance) |

### 2.4 Configuration Changes

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `orchestrator.linking.enabled` | Boolean | true | Master toggle |
| `orchestrator.linking.similarity-threshold` | Double | 0.75 | Min score for link creation |
| `orchestrator.linking.top-k` | Int | 10 | Max similar entries to find |
| `orchestrator.linking.batch-chunk-size` | Int | 50 | Entries per batch chunk |
| `orchestrator.linking.collection-name` | String | kb_entity_embeddings | Qdrant collection name |
| `orchestrator.linking.auto-link-on-ingest` | Boolean | true | Auto-link on KB ingest |

### 2.5 DI Changes

| Interface | Implementation | Scope |
|-----------|---------------|-------|
| `EntityLinkingService` | `EntityLinkingServiceImpl` | Singleton |
| `EntityLinkRepository` | `EntityLinkRepositoryImpl` | Singleton |

---

## 3. Dependencies

### 3.1 Pre-requisite Releases

| Release | Version | Status | Required Before |
|---------|---------|--------|-----------------|
| MTO-10 (Base Orchestrator) | 1.0.0 | Deployed | This release |
| MTO-26 (KB Entries) | 1.2.0 | Deployed | This release |
| PostgreSQL 16+ | 16.x | Available | This release |
| Qdrant | 1.9+ | Available | This release |

### 3.2 External System Changes

| System | Change Required | Status |
|--------|----------------|--------|
| PostgreSQL | Run migration V3 (entity_links table) | Pending |
| Qdrant | Create `kb_entity_embeddings` collection | Pending |
| OpenAI API | No changes (existing EmbeddingService) | N/A |

---

## 4. Breaking Changes

No breaking changes. Fully backward compatible.

- Existing tool discovery and execution unaffected
- Linking feature can be disabled via `linking.enabled: false`
- No existing tables or APIs modified

---

## 5. Known Limitations

| # | Limitation | Impact | Workaround |
|---|-----------|--------|------------|
| 1 | Links not updated when content changes | Stale links if content edited | Re-run linkEntry manually |
| 2 | No link deletion API | Cannot remove incorrect auto-links | Direct DB delete |
| 3 | Single embedding model only | Cannot mix embedding models | Use consistent model across all entries |
| 4 | No cross-instance linking | Links scoped to single Qdrant collection | Deploy shared Qdrant for multi-instance |

---

## 6. Migration Notes

### 6.1 Data Migration

| Migration | Description | Automated | Time |
|-----------|-------------|-----------|------|
| V3__create_entity_links.sql | Create table + indexes | Yes (Flyway) | < 1 second |
| Qdrant collection creation | Create HNSW collection | Manual (one-time) | < 5 seconds |

### 6.2 Initial Linking (Optional)

After deployment, existing KB entries can be batch-linked:

```kotlin
// Trigger via admin API or startup flag
entityLinkingService.batchLink(allExistingEntries)
```

Estimated time for 1000 entries: ~20 seconds (50 entries/sec)

---

## 7. Testing Summary

| Test Level | Total | Pass Rate |
|-----------|-------|-----------|
| Property-Based Tests | 4 | TBD |
| Unit Tests | 18 | TBD |
| Integration Tests | 6 | TBD |
| E2E API Tests | 6 | TBD |
| **Total** | **34** | TBD |

---

## 8. Deployment Instructions

See: [Deployment Guide (DPG.md)](DPG.md)

**Quick Reference:**
1. Run DB migration (V3)
2. Create Qdrant collection
3. Update application.yml
4. Deploy new JAR
5. Verify startup + smoke test

**Estimated deployment time:** ~5 minutes

---

## 9. Rollback Plan

See: [DPG.md §6 — Rollback Plan](DPG.md)

**Quick rollback:** Set `linking.enabled: false` and restart (no code rollback needed).
**Full rollback:** Restore previous JAR + drop entity_links table + delete Qdrant collection.
