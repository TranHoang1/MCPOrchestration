# Technical Design Document (TDD)

## KB-Server — MTO-116: Feature CRUD Tools — BA + AI Collaborative Feature Management

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-116 |
| Title | [KB-Server] Feature CRUD Tools — BA + AI Collaborative Feature Management |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-07-08 |
| Status | Draft |
| Related BRD | documents/MTO-116/BRD.md (v1.0) |
| Related FSD | documents/MTO-116/FSD.md (v1.0) |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | SA Agent – Solution Architect | Create document |
| Peer Reviewer | TA Agent – Technical Architect | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-07-08 | SA Agent | Initial TDD — auto-generated from BRD v1.0 and FSD v1.0 |

---

## 1. Introduction

### 1.1 Purpose

This TDD specifies the implementation design for five new MCP tools (`kb_feature_list`, `kb_feature_create`, `kb_feature_update`, `kb_feature_assign`, `kb_feature_delete`) in `kb-server` and the AI protection algorithm modification in `sync-pipeline`'s `FeatureDetectionDimension`.

### 1.2 Scope

| Module | Changes |
|--------|---------|
| kb-server | 5 new handler classes, 1 repository interface + impl, 1 validation utility, 1 new exception class, DI registration |
| sync-pipeline | 1 modified dimension class (FeatureDetectionDimension), 1 new method on IndexWriter interface + impl |

### 1.3 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.3.20 |
| MCP SDK | io.modelcontextprotocol:kotlin-sdk-server | 0.12.0 |
| DI | Koin | 4.1.1 |
| Database | PostgreSQL (sync.index_entries) | — |
| Connection Pool | HikariCP | — |
| Serialization | kotlinx.serialization-json | 1.8.1 |
| Coroutines | kotlinx.coroutines | 1.10.2 |
| Testing | JUnit 5 + MockK + Kotest | 5.11.4 / 1.14.2 / 5.9.1 |

### 1.4 Design Principles

- **Single Responsibility** — each handler does one CRUD operation
- **Interface Segregation** — FeatureRepository is separate from IndexWriter
- **Open/Closed** — new handlers extend existing KbToolHandler interface without modifying it
- **Fail-Safe AI** — if protection check fails, AI skips (never overwrites)
- **Idempotent Operations** — repeated calls produce same result

### 1.5 Constraints

- No schema migration — all new data stored in existing JSONB `data` column
- Feature count per project < 500 — no pagination needed for v1
- Single BA agent consumer — no multi-user concurrency for v1
- kb-server must have cross-schema access to `sync.index_entries`

### 1.6 References

| Document | Location |
|----------|----------|
| BRD | documents/MTO-116/BRD.md |
| FSD | documents/MTO-116/FSD.md |
| KbIngestHandler (pattern) | kb-server/src/.../protocol/handlers/KbIngestHandler.kt |
| KbDiModule (DI pattern) | kb-server/src/.../di/KbDiModule.kt |
| PostgresIndexWriter (DB pattern) | sync-pipeline/src/.../storage/PostgresIndexWriter.kt |
| FeatureDetectionDimension (target) | sync-pipeline/src/.../dimension/builtin/FeatureDetectionDimension.kt |

---

## 2. Architecture Overview

### 2.1 Component Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         MCP Client (IDE)                             │
│                    (BA Agent via Kiro/VS Code)                       │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ MCP Protocol (stdio/HTTP)
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          kb-server                                   │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │ protocol/handlers/feature/                                      │ │
│  │   KbFeatureListHandler ─┐                                       │ │
│  │   KbFeatureCreateHandler │                                      │ │
│  │   KbFeatureUpdateHandler ├──→ FeatureRepository (interface)     │ │
│  │   KbFeatureAssignHandler │         │                            │ │
│  │   KbFeatureDeleteHandler ┘         ▼                            │ │
│  │                            FeatureRepositoryImpl (PostgreSQL)    │ │
│  │                                    │                            │ │
│  │   FeatureValidation (shared)       │                            │ │
│  │   AuditService (existing) ◄────────┘                            │ │
│  └────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ SQL (HikariCP)
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│              PostgreSQL — sync.index_entries                          │
│              (dimension_id = 'feature_grouping')                      │
└───────────────────────────────┬─────────────────────────────────────┘
                                ▲ SQL (HikariCP)
┌───────────────────────────────┴─────────────────────────────────────┐
│                       sync-pipeline                                   │
│  FeatureDetectionDimension (MODIFIED)                                │
│    ├── loadExistingFeatures() ← NEW                                  │
│    ├── extractProtectedIds() ← NEW                                   │
│    ├── extractProtectedTickets() ← NEW                               │
│    ├── filterProtectedFeatures() ← NEW                               │
│    └── postProcess() ← MODIFIED (add protection logic)               │
│                                                                      │
│  IndexWriter (interface) ← NEW method: getFeatureEntries()           │
│  PostgresIndexWriter ← NEW implementation                            │
└──────────────────────────────────────────────────────────────────────┘
```


### 2.2 Package Structure

```
kb-server/src/main/kotlin/com/orchestrator/mcp/kb/
├── protocol/handlers/
│   ├── feature/                          ← NEW package
│   │   ├── KbFeatureListHandler.kt       ← kb_feature_list tool handler
│   │   ├── KbFeatureCreateHandler.kt     ← kb_feature_create tool handler
│   │   ├── KbFeatureUpdateHandler.kt     ← kb_feature_update tool handler
│   │   ├── KbFeatureAssignHandler.kt     ← kb_feature_assign tool handler
│   │   └── KbFeatureDeleteHandler.kt     ← kb_feature_delete tool handler
│   └── HandlerUtils.kt                   (existing — shared utilities)
├── feature/                               ← NEW package
│   ├── FeatureRepository.kt              ← Interface — feature CRUD operations
│   ├── FeatureRepositoryImpl.kt          ← PostgreSQL implementation
│   ├── FeatureValidation.kt             ← Shared validation logic
│   └── FeatureConstants.kt              ← Dimension ID, field names, defaults
├── KbExceptions.kt                        (existing — add KbDuplicateException)
└── di/KbDiModule.kt                      (existing — add new bindings)

sync-pipeline/src/main/kotlin/com/orchestrator/mcp/sync/pipeline/
├── storage/
│   ├── IndexWriter.kt                    (existing — add getFeatureEntries method)
│   └── PostgresIndexWriter.kt           (existing — implement getFeatureEntries)
└── dimension/builtin/
    └── FeatureDetectionDimension.kt      (existing — add AI protection logic)
```

### 2.3 Data Flow

| Flow | Path | Description |
|------|------|-------------|
| BA → Create Feature | IDE → MCP → KbFeatureCreateHandler → FeatureRepository → PostgreSQL | Manual feature creation |
| BA → List Features | IDE → MCP → KbFeatureListHandler → FeatureRepository → PostgreSQL | Read all features |
| BA → Update Feature | IDE → MCP → KbFeatureUpdateHandler → FeatureRepository → PostgreSQL | Partial update + adoption |
| BA → Assign Ticket | IDE → MCP → KbFeatureAssignHandler → FeatureRepository → PostgreSQL | Move ticket between features |
| BA → Delete Feature | IDE → MCP → KbFeatureDeleteHandler → FeatureRepository → PostgreSQL | Remove feature |
| AI → Sync Features | SyncOrchestrator → FeatureDetectionDimension → IndexWriter → PostgreSQL | AI detection with protection |

---

## 3. Detailed Class Design

### 3.1 FeatureConstants (NEW)

| Property | Value |
|----------|-------|
| Package | `com.orchestrator.mcp.kb.feature` |
| File | `FeatureConstants.kt` |
| Type | `object` |

```kotlin
object FeatureConstants {
    const val DIMENSION_ID = "feature_grouping"
    const val SOURCE_MANUAL = "manual"
    const val SOURCE_AI_DETECTED = "ai_detected"
    const val SOURCE_EPIC_HIERARCHY = "epic_hierarchy"
    const val CREATED_BY_BA = "ba-agent"
    const val CREATED_BY_AI = "ai-sync"
    const val LOCKED_TRUE = "true"
    const val LOCKED_FALSE = "false"
}
```

### 3.2 FeatureValidation (NEW)

| Property | Value |
|----------|-------|
| Package | `com.orchestrator.mcp.kb.feature` |
| File | `FeatureValidation.kt` |
| Type | `object` |
| Responsibilities | Input validation for all feature handlers |

**Public Methods:**

| Method | Signature | Returns | Throws |
|--------|-----------|---------|--------|
| validateProjectKey | `fun validateProjectKey(value: String?): String` | Validated project key | `KbValidationException` |
| validateFeatureName | `fun validateFeatureName(value: String?): String` | Validated name | `KbValidationException` |
| validateTicketKeys | `fun validateTicketKeys(keys: List<String>?): List<String>` | Validated list | `KbValidationException` |
| validateTicketKey | `fun validateTicketKey(key: String?): String` | Validated key | `KbValidationException` |
| validateDescription | `fun validateDescription(value: String?): String?` | Validated or null | `KbValidationException` |
| validateFeatureId | `fun validateFeatureId(value: String?): String` | Validated ID | `KbValidationException` |

**Validation Rules:**

| Field | Pattern | Max Length |
|-------|---------|-----------|
| project_key | `^[A-Z][A-Z0-9_]+$` | — |
| name | non-blank | 200 |
| ticket_key | `^[A-Z]+-\d+$` | — |
| description | nullable | 2000 |
| feature_id | non-blank | — |

### 3.3 FeatureRepository (NEW — Interface)

| Property | Value |
|----------|-------|
| Package | `com.orchestrator.mcp.kb.feature` |
| File | `FeatureRepository.kt` |
| Type | `interface` |
| Responsibilities | Data access abstraction for feature CRUD on sync.index_entries |

**Methods:**

```kotlin
interface FeatureRepository {
    suspend fun listByProject(projectKey: String): List<IndexEntry>
    suspend fun findById(entryKey: String): IndexEntry?
    suspend fun existsByName(projectKey: String, name: String): Boolean
    suspend fun findByTicketKey(projectKey: String, ticketKey: String): IndexEntry?
    suspend fun create(entry: IndexEntry)
    suspend fun update(entryKey: String, data: Map<String, String?>, vectorText: String?)
    suspend fun delete(entryKey: String): IndexEntry?
}
```


### 3.4 FeatureRepositoryImpl (NEW)

| Property | Value |
|----------|-------|
| Package | `com.orchestrator.mcp.kb.feature` |
| File | `FeatureRepositoryImpl.kt` |
| Type | `class` |
| Implements | `FeatureRepository` |
| Responsibilities | PostgreSQL queries against sync.index_entries for feature_grouping dimension |

**Constructor Dependencies:**

| Dependency | Type | Source |
|------------|------|--------|
| dataSource | `HikariDataSource` | Koin DI (existing binding) |

**Implementation Notes:**

- Uses `withContext(Dispatchers.IO)` for all DB operations (same pattern as `PostgresIndexWriter`)
- Uses `dataSource.connection.use { }` for connection management
- JSON serialization via `kotlinx.serialization.json.Json` for `data` column (JSONB)
- `findByTicketKey` uses SQL LIKE query + application-layer exact match (comma-split verification)
- `create` uses UPSERT (ON CONFLICT DO UPDATE) matching `PostgresIndexWriter.UPSERT_SQL` pattern
- `delete` uses DELETE ... RETURNING to get deleted entry data

**Key SQL Constants (companion object):**

```kotlin
companion object {
    private const val LIST_SQL = """
        SELECT id, dimension_id, project_key, ticket_key, entry_key,
               source_type, source_path, content_hash, derived_from, data, vector_text
        FROM sync.index_entries
        WHERE dimension_id = 'feature_grouping' AND project_key = ?
        ORDER BY CASE WHEN data->>'source' = 'manual' THEN 0 ELSE 1 END,
                 data->>'feature_name' ASC
    """
    private const val FIND_BY_ID_SQL = """
        SELECT id, dimension_id, project_key, ticket_key, entry_key,
               source_type, source_path, content_hash, derived_from, data, vector_text
        FROM sync.index_entries
        WHERE dimension_id = 'feature_grouping' AND entry_key = ?
    """
    private const val EXISTS_BY_NAME_SQL = """
        SELECT EXISTS(SELECT 1 FROM sync.index_entries
        WHERE dimension_id = 'feature_grouping' AND project_key = ? AND data->>'feature_name' = ?)
    """
    private const val FIND_BY_TICKET_SQL = """
        SELECT id, dimension_id, project_key, ticket_key, entry_key,
               source_type, source_path, content_hash, derived_from, data, vector_text
        FROM sync.index_entries
        WHERE dimension_id = 'feature_grouping' AND project_key = ?
              AND data->>'ticket_keys' LIKE '%' || ? || '%'
    """
    private const val UPDATE_SQL = """
        UPDATE sync.index_entries SET data = ?::jsonb, vector_text = ?, updated_at = NOW()
        WHERE dimension_id = 'feature_grouping' AND entry_key = ?
    """
    private const val DELETE_SQL = """
        DELETE FROM sync.index_entries
        WHERE dimension_id = 'feature_grouping' AND entry_key = ?
        RETURNING id, data
    """
}
```

### 3.5 KbFeatureListHandler (NEW)

| Property | Value |
|----------|-------|
| Package | `com.orchestrator.mcp.kb.protocol.handlers.feature` |
| File | `KbFeatureListHandler.kt` |
| Implements | `KbToolHandler` |
| Tool Name | `kb_feature_list` |

**Constructor Dependencies:**

| Dependency | Type |
|------------|------|
| featureRepository | `FeatureRepository` |
| auditService | `AuditService` |

**Public Methods:**

| Method | Signature | Description |
|--------|-----------|-------------|
| handle | `suspend fun handle(arguments: JsonObject?): CallToolResult` | List all features for a project |

**Implementation Steps:**

1. Extract and validate `project_key` via `FeatureValidation.validateProjectKey()`
2. Call `featureRepository.listByProject(projectKey)`
3. Map each `IndexEntry` to feature response JSON (extract fields from `data` map)
4. Build response: `{ features: [...], total_count: N, project_key: "..." }`
5. Log audit event via `auditService.log()`
6. Return `HandlerUtils.successResult(json)`

**Error Handling:**

- `KbValidationException` → caught, returns `HandlerUtils.errorResult(e)`
- `Exception` → caught, logs error, returns `FEATURE_INTERNAL_ERROR`

### 3.6 KbFeatureCreateHandler (NEW)

| Property | Value |
|----------|-------|
| Package | `com.orchestrator.mcp.kb.protocol.handlers.feature` |
| File | `KbFeatureCreateHandler.kt` |
| Implements | `KbToolHandler` |
| Tool Name | `kb_feature_create` |

**Constructor Dependencies:**

| Dependency | Type |
|------------|------|
| featureRepository | `FeatureRepository` |
| auditService | `AuditService` |

**Implementation Steps:**

1. Validate: `project_key`, `name`, `ticket_keys` (required), `description` (optional)
2. Check duplicate: `featureRepository.existsByName(projectKey, name)` → throw `KbDuplicateException` if exists
3. Generate feature ID: `"manual-" + sha256(projectKey + ":" + name).take(12)`
4. Build entry_key: `"feature:$featureId"`
5. Build `IndexEntry` with:
   - `id` = `UUID.nameUUIDFromBytes(entryKey.toByteArray()).toString()`
   - `dimensionId` = `"feature_grouping"`
   - `data` map with source="manual", locked="true", created_by="ba-agent"
   - `vectorText` = `"Feature: $name. Tickets: ${ticketKeys.joinToString(", ")}"`
6. Call `featureRepository.create(entry)`
7. Log audit, return success response

**Feature ID Generation Algorithm:**

```kotlin
private fun generateFeatureId(projectKey: String, name: String): String {
    val input = "$projectKey:$name"
    val hash = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(12)
    return "manual-$hash"
}
```

### 3.7 KbFeatureUpdateHandler (NEW)

| Property | Value |
|----------|-------|
| Package | `com.orchestrator.mcp.kb.protocol.handlers.feature` |
| File | `KbFeatureUpdateHandler.kt` |
| Implements | `KbToolHandler` |
| Tool Name | `kb_feature_update` |

**Constructor Dependencies:**

| Dependency | Type |
|------------|------|
| featureRepository | `FeatureRepository` |
| auditService | `AuditService` |

**Implementation Steps:**

1. Validate `feature_id` (required)
2. Extract optional fields: `name`, `ticket_keys`, `description`
3. Validate at least one optional field is provided → else throw `KbValidationException`
4. Find existing: `featureRepository.findById(featureId)` → throw `KbNotFoundException` if null
5. If name changed → check duplicate via `existsByName()`
6. Determine adoption: `adopted = existing.data["source"] != "manual"`
7. Build updated data map:
   - Merge existing data with new values
   - If adopted: set `source="manual"`, `locked="true"`, `created_by="ba-agent"`
8. Rebuild vectorText if name or ticket_keys changed
9. Call `featureRepository.update(entryKey, updatedData, vectorText)`
10. Log audit with adoption flag and updated fields list
11. Return response with `{ status, feature_id, updated_fields, source, locked, adopted }`


### 3.8 KbFeatureAssignHandler (NEW)

| Property | Value |
|----------|-------|
| Package | `com.orchestrator.mcp.kb.protocol.handlers.feature` |
| File | `KbFeatureAssignHandler.kt` |
| Implements | `KbToolHandler` |
| Tool Name | `kb_feature_assign` |

**Constructor Dependencies:**

| Dependency | Type |
|------------|------|
| featureRepository | `FeatureRepository` |
| auditService | `AuditService` |

**Implementation Steps:**

1. Validate `feature_id` and `ticket_key`
2. Find target feature: `featureRepository.findById(featureId)` → throw `KbNotFoundException` if null
3. Extract `projectKey` from target feature's `projectKey` field
4. Check idempotency: if ticket already in target's `ticket_keys` → return success (no-op)
5. Find old feature: `featureRepository.findByTicketKey(projectKey, ticketKey)`
6. If old feature exists and is different from target:
   - Remove ticket from old feature's `ticket_keys`
   - Update old feature in DB
7. Add ticket to target feature's `ticket_keys`
8. If target source != "manual" → set source="manual", locked="true" (adoption via assign)
9. Rebuild vectorText
10. Update target feature in DB
11. Log audit, return response with `{ status, feature_id, ticket_key, ticket_keys, removed_from }`

**Idempotency Check:**

```kotlin
val currentTickets = existing.data["ticket_keys"]?.split(",")?.map { it.trim() } ?: emptyList()
if (ticketKey in currentTickets) {
    return HandlerUtils.successResult(buildNoOpResponse(featureId, ticketKey, currentTickets))
}
```

### 3.9 KbFeatureDeleteHandler (NEW)

| Property | Value |
|----------|-------|
| Package | `com.orchestrator.mcp.kb.protocol.handlers.feature` |
| File | `KbFeatureDeleteHandler.kt` |
| Implements | `KbToolHandler` |
| Tool Name | `kb_feature_delete` |

**Constructor Dependencies:**

| Dependency | Type |
|------------|------|
| featureRepository | `FeatureRepository` |
| auditService | `AuditService` |

**Implementation Steps:**

1. Validate `feature_id`
2. Find existing: `featureRepository.findById(featureId)` → throw `KbNotFoundException` if null
3. Call `featureRepository.delete(featureId)`
4. Determine warning: if source is "ai_detected" or "epic_hierarchy" → warn about re-creation
5. Log audit, return `{ status, feature_id, feature_name, source, warning }`

### 3.10 KbDuplicateException (NEW)

| Property | Value |
|----------|-------|
| Package | `com.orchestrator.mcp.kb` |
| File | `KbExceptions.kt` (add to existing file) |

```kotlin
class KbDuplicateException(entity: String, identifier: String) :
    KbException("KB_DUPLICATE_ERROR", "Duplicate $entity: '$identifier'")
```

**Integration:** Added to existing sealed class hierarchy in `KbExceptions.kt`.

### 3.11 IndexWriter Extension (MODIFIED — sync-pipeline)

| Property | Value |
|----------|-------|
| File | `sync-pipeline/src/.../storage/IndexWriter.kt` |
| Change | Add new method to interface |

**New Method:**

```kotlin
interface IndexWriter {
    // ... existing methods ...

    /** Load existing feature entries for AI protection check. */
    suspend fun getFeatureEntries(projectKey: String): List<IndexEntry>
}
```

### 3.12 PostgresIndexWriter Extension (MODIFIED — sync-pipeline)

| Property | Value |
|----------|-------|
| File | `sync-pipeline/src/.../storage/PostgresIndexWriter.kt` |
| Change | Implement getFeatureEntries method |

**Implementation:**

```kotlin
override suspend fun getFeatureEntries(projectKey: String): List<IndexEntry> {
    return withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(FEATURES_SQL).use { stmt ->
                stmt.setString(1, projectKey)
                val rs = stmt.executeQuery()
                buildList { while (rs.next()) add(mapIndexEntry(rs)) }
            }
        }
    }
}

companion object {
    // ... existing SQL constants ...
    private const val FEATURES_SQL = """
        SELECT id, dimension_id, project_key, ticket_key, entry_key,
               source_type, source_path, content_hash, derived_from, data, vector_text
        FROM sync.index_entries
        WHERE dimension_id = 'feature_grouping' AND project_key = ?
    """
}
```

**Note:** A private `mapIndexEntry(ResultSet): IndexEntry` helper method is needed. This follows the same pattern as `mapSummaryRow` but maps all columns to an `IndexEntry` instance.

---

## 4. Database Design

### 4.1 Storage Strategy

**No schema migration required.** All feature data is stored in the existing `sync.index_entries` table using the JSONB `data` column. The `dimension_id = 'feature_grouping'` filter isolates feature records.

### 4.2 Data Map Schema (JSONB `data` column)

| Key | Type | Values | Default (if missing) | Description |
|-----|------|--------|---------------------|-------------|
| source | String | "manual" / "ai_detected" / "epic_hierarchy" | "ai_detected" | Origin of feature |
| created_by | String | "ba-agent" / "ai-sync" | "ai-sync" | Creator identity |
| locked | String | "true" / "false" | "false" | AI protection flag |
| feature_id | String | Unique ID | — | Feature identifier |
| feature_name | String | Display name | — | Human-readable name |
| ticket_keys | String | Comma-separated | — | "MTO-116,MTO-117" |
| description | String? | Free text | null | Feature description |
| detection_method | String? | Method used | null | "epic_hierarchy" / "ai_analysis" / "manual" |
| confidence | String? | "0.0" - "1.0" | null | AI confidence (null for manual) |
| epic_key | String? | Epic ticket key | null | Source epic (if epic_hierarchy) |

### 4.3 Existing Indexes (No Changes Needed)

```sql
-- Already exists — composite index for dimension + project queries
CREATE INDEX idx_index_entries_dimension_project
ON sync.index_entries (dimension_id, project_key);

-- Already exists — unique constraint for upsert conflict resolution
CREATE UNIQUE INDEX idx_index_entries_dimension_entrykey
ON sync.index_entries (dimension_id, entry_key);
```

### 4.4 Query Performance Analysis

| Query | Index Used | Expected Rows | Target Time |
|-------|-----------|---------------|-------------|
| listByProject | idx_dimension_project | < 500 | < 50ms |
| findById | idx_dimension_entrykey | 1 | < 5ms |
| existsByName | idx_dimension_project + seq scan on data | < 500 | < 50ms |
| findByTicketKey | idx_dimension_project + LIKE on data | < 500 | < 100ms |
| create (upsert) | idx_dimension_entrykey (conflict) | 1 | < 10ms |
| update | idx_dimension_entrykey | 1 | < 10ms |
| delete | idx_dimension_entrykey | 1 | < 10ms |

**Note on findByTicketKey:** The LIKE query on JSONB `data->>'ticket_keys'` is acceptable because:
1. Feature count per project is < 500 (small dataset)
2. Application layer performs exact match after LIKE (split by comma, check membership)
3. No GIN index needed for v1 — can be added later if performance degrades


---

## 5. AI Protection Algorithm

### 5.1 Overview

The `FeatureDetectionDimension.postProcess()` method is modified to load existing features and filter AI-detected results before writing. This ensures manual/locked features are never overwritten.

### 5.2 Modified postProcess() — Detailed Steps

```
ALGORITHM: AI Feature Detection with Protection
INPUT: projectKey, config (DimensionConfig)
OUTPUT: List<IndexEntry> (only non-conflicting AI features)

1. Load ticket summaries for AI analysis
   summaries = indexWriter.getTicketSummaries(projectKey)
   IF summaries is empty → RETURN emptyList()

2. Load existing features (NEW)
   existingFeatures = indexWriter.getFeatureEntries(projectKey)

3. Extract protected feature IDs (NEW)
   protectedIds = existingFeatures
     .filter { entry →
       entry.data["source"] == "manual" ||
       entry.data["locked"] == "true" ||
       (entry.data["source"] == null && entry.data["locked"] == null)  // legacy: skip unknown
     }
     .map { it.id }
     .toSet()

4. Extract protected ticket keys (NEW)
   protectedTickets = existingFeatures
     .filter { it.id in protectedIds }
     .flatMap { it.data["ticket_keys"]?.split(",") ?: emptyList() }
     .map { it.trim() }
     .filter { it.isNotBlank() }
     .toSet()

5. Detect features via AI (UNCHANGED)
   detectedFeatures = aiService.detectFeatures(summaries)
   LOG: "Detected {N} features for project {projectKey}"

6. Filter protected features (NEW)
   newFeatures = detectedFeatures.filter { feature →
     val featureEntryKey = "feature:${feature.featureId}"
     val featureUuid = deterministicId(featureEntryKey)
     // Rule 1: Skip if this feature's UUID matches a protected feature
     featureUuid !in protectedIds &&
     // Rule 2: Skip if ANY ticket in this feature belongs to a protected feature
     feature.ticketKeys.none { it in protectedTickets }
   }
   LOG: "After protection filter: {N} features to write"

7. Build index entries (MODIFIED — add source/locked fields)
   RETURN newFeatures.map { feature →
     IndexEntry(
       id = deterministicId("feature:${feature.featureId}"),
       dimensionId = "feature_grouping",
       projectKey = projectKey,
       entryKey = "feature:${feature.featureId}",
       sourceRef = SourceRef(type="ai_derived", ...),
       data = buildFeatureData(feature) + mapOf(
         "source" to "ai_detected",
         "created_by" to "ai-sync",
         "locked" to "false"
       ),
       vectorText = "Feature: ${feature.featureName}. Tickets: ..."
     )
   }
```

### 5.3 Backward Compatibility Rules

| Condition | Treatment | Rationale |
|-----------|-----------|-----------|
| `source` field missing | Treat as `"ai_detected"` | Legacy AI features can be updated by AI |
| `locked` field missing | Treat as `"false"` | Legacy features are not protected |
| `source = "manual"` | ALWAYS protected | BA-created features never touched |
| `locked = "true"` | ALWAYS protected | Explicitly locked features never touched |
| `source = "ai_detected"` AND `locked = "false"` | AI can update | AI owns these features |

### 5.4 Fail-Safe Behavior

| Failure Point | Behavior | Rationale |
|---------------|----------|-----------|
| `getFeatureEntries()` throws exception | Log error, return emptyList() from postProcess() | Never overwrite if protection check fails |
| `extractProtectedIds()` returns empty but features exist | Treat all as unprotected (AI updates its own) | Only happens if all features are AI-owned |
| `filterProtectedFeatures()` filters all features | Return emptyList() — no AI features written | Safe: manual features preserved |

### 5.5 Race Condition Handling

| Scenario | Outcome | Acceptable? |
|----------|---------|-------------|
| BA creates feature WHILE AI sync runs | AI may write conflicting entry → next sync will respect it | Yes (eventual consistency) |
| BA deletes AI feature WHILE AI sync runs | AI's UPSERT recreates it with locked="false" | Yes (BA can delete again) |
| BA adopts AI feature WHILE AI sync runs | AI may overwrite adoption → BA must re-adopt | Acceptable for v1 (single BA user) |

**Mitigation for v2:** Add `updated_at` timestamp check — AI skips entries modified within last 60 seconds.

---

## 6. Error Handling Strategy

### 6.1 Exception Hierarchy

```
KbException (sealed)
├── KbValidationException     → FEATURE_VALIDATION_ERROR (400)
├── KbNotFoundException       → FEATURE_NOT_FOUND (404)
├── KbDuplicateException      → KB_DUPLICATE_ERROR (409) ← NEW
├── KbAccessDeniedException   → KB_ACCESS_DENIED (403)
├── KbInternalException       → KB_INTERNAL_ERROR (500)
├── KbEncryptionException     → KB_ENCRYPTION_ERROR (500)
├── KbLlmTimeoutException     → KB_LLM_TIMEOUT (504)
├── KbQueueFullException      → KB_QUEUE_FULL (503)
└── KbRateLimitedException    → KB_RATE_LIMITED (429)
```

### 6.2 Error Code Mapping

| Error Code | Exception Class | HTTP Equiv | When |
|-----------|----------------|------------|------|
| `FEATURE_VALIDATION_ERROR` | `KbValidationException` | 400 | Invalid input format, missing required field |
| `FEATURE_NOT_FOUND` | `KbNotFoundException` | 404 | Feature ID does not exist in DB |
| `FEATURE_DUPLICATE_ERROR` | `KbDuplicateException` | 409 | Feature name already exists in project |
| `FEATURE_INTERNAL_ERROR` | `KbInternalException` | 500 | Database failure, unexpected error |

### 6.3 Handler Error Pattern

All feature handlers follow this try-catch pattern (same as `KbIngestHandler`):

```kotlin
override suspend fun handle(arguments: JsonObject?): CallToolResult {
    return try {
        // ... business logic ...
        HandlerUtils.successResult(responseJson)
    } catch (e: KbException) {
        HandlerUtils.errorResult(e)
    } catch (e: Exception) {
        logger.error("kb_feature_xxx failed: {}", e.message, e)
        HandlerUtils.errorResult("FEATURE_INTERNAL_ERROR", "Failed to xxx feature: ${e.message}")
    }
}
```

### 6.4 Error Response Format

```json
{
  "content": [{ "type": "text", "text": "{\"error\":{\"code\":\"FEATURE_NOT_FOUND\",\"message\":\"No entry found for 'feature:manual-abc'\"}}" }],
  "isError": true
}
```


---

## 7. DI Registration

### 7.1 Changes to KbDiModule.kt

Add the following bindings to `kbHandlersModule()`:

```kotlin
fun kbHandlersModule() = module {
    // ... existing handlers (unchanged) ...
    single { KbSearchHandler(get(), get(), get(), get()) } bind KbToolHandler::class
    single { KbReadHandler(get(), get()) } bind KbToolHandler::class
    single { KbIngestHandler(get(), get(), get(), get()) } bind KbToolHandler::class
    // ... other existing handlers ...

    // Feature CRUD handlers (NEW)
    single { FeatureRepositoryImpl(get()) } bind FeatureRepository::class
    single { KbFeatureListHandler(get(), get()) } bind KbToolHandler::class
    single { KbFeatureCreateHandler(get(), get()) } bind KbToolHandler::class
    single { KbFeatureUpdateHandler(get(), get()) } bind KbToolHandler::class
    single { KbFeatureAssignHandler(get(), get()) } bind KbToolHandler::class
    single { KbFeatureDeleteHandler(get(), get()) } bind KbToolHandler::class
}
```

### 7.2 Import Additions

```kotlin
import com.orchestrator.mcp.kb.feature.FeatureRepository
import com.orchestrator.mcp.kb.feature.FeatureRepositoryImpl
import com.orchestrator.mcp.kb.protocol.handlers.feature.*
```

### 7.3 Dependency Graph

```
KbFeatureListHandler
  ├── FeatureRepository (interface) → FeatureRepositoryImpl
  │     └── HikariDataSource (existing)
  └── AuditService (existing)

KbFeatureCreateHandler
  ├── FeatureRepository
  └── AuditService

KbFeatureUpdateHandler
  ├── FeatureRepository
  └── AuditService

KbFeatureAssignHandler
  ├── FeatureRepository
  └── AuditService

KbFeatureDeleteHandler
  ├── FeatureRepository
  └── AuditService
```

---

## 8. Testing Strategy

### 8.1 Unit Test Classes

| Test Class | Package | Tests |
|-----------|---------|-------|
| `FeatureValidationTest` | `com.orchestrator.mcp.kb.feature` | All validation rules |
| `KbFeatureListHandlerTest` | `com.orchestrator.mcp.kb.protocol.handlers.feature` | List handler logic |
| `KbFeatureCreateHandlerTest` | `com.orchestrator.mcp.kb.protocol.handlers.feature` | Create handler logic |
| `KbFeatureUpdateHandlerTest` | `com.orchestrator.mcp.kb.protocol.handlers.feature` | Update + adoption logic |
| `KbFeatureAssignHandlerTest` | `com.orchestrator.mcp.kb.protocol.handlers.feature` | Assign + move logic |
| `KbFeatureDeleteHandlerTest` | `com.orchestrator.mcp.kb.protocol.handlers.feature` | Delete + warning logic |
| `FeatureRepositoryImplTest` | `com.orchestrator.mcp.kb.feature` | SQL queries (Testcontainers) |
| `FeatureDetectionProtectionTest` | `com.orchestrator.mcp.sync.pipeline.dimension.builtin` | AI protection algorithm |

### 8.2 Key Test Scenarios

#### FeatureValidationTest

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Valid project key "MTO" | Returns "MTO" |
| 2 | Invalid project key "mto" (lowercase) | Throws KbValidationException |
| 3 | Empty project key | Throws KbValidationException |
| 4 | Valid ticket key "MTO-123" | Returns "MTO-123" |
| 5 | Invalid ticket key "mto123" | Throws KbValidationException |
| 6 | Name exceeds 200 chars | Throws KbValidationException |
| 7 | Description at exactly 2000 chars | Returns description |
| 8 | Description exceeds 2000 chars | Throws KbValidationException |

#### KbFeatureCreateHandlerTest

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Valid create with all fields | Returns status="created", source="manual", locked=true |
| 2 | Duplicate feature name | Returns FEATURE_DUPLICATE_ERROR |
| 3 | Missing project_key | Returns FEATURE_VALIDATION_ERROR |
| 4 | Empty ticket_keys list | Returns FEATURE_VALIDATION_ERROR |
| 5 | Feature ID is deterministic | Same inputs → same feature_id |
| 6 | Invalid ticket key format | Returns FEATURE_VALIDATION_ERROR |

#### KbFeatureUpdateHandlerTest

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Update name only | Returns updated_fields=["name"] |
| 2 | Update AI feature (adoption) | Returns adopted=true, source="manual", locked=true |
| 3 | Feature not found | Returns FEATURE_NOT_FOUND |
| 4 | No fields provided | Returns FEATURE_VALIDATION_ERROR |
| 5 | Name conflicts with existing | Returns FEATURE_DUPLICATE_ERROR |
| 6 | Update manual feature (no adoption) | Returns adopted=false |

#### KbFeatureAssignHandlerTest

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Assign ticket to feature | Returns status="assigned" |
| 2 | Ticket already in feature (idempotent) | Returns success, no change |
| 3 | Ticket moved from another feature | Returns removed_from=old_feature_id |
| 4 | Feature not found | Returns FEATURE_NOT_FOUND |
| 5 | Assign triggers adoption (AI feature) | source changes to "manual" |

#### KbFeatureDeleteHandlerTest

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Delete manual feature | Returns status="deleted", warning=null |
| 2 | Delete AI feature | Returns warning about re-creation |
| 3 | Feature not found | Returns FEATURE_NOT_FOUND |

#### FeatureDetectionProtectionTest

| # | Scenario | Expected |
|---|----------|----------|
| 1 | No existing features | AI writes all detected features |
| 2 | Manual feature exists | AI skips features with overlapping tickets |
| 3 | Locked feature exists | AI skips features with overlapping tickets |
| 4 | AI feature exists (not locked) | AI can update it |
| 5 | Legacy feature (no source field) | Treated as ai_detected, AI can update |
| 6 | Mixed: some manual, some AI | Only non-conflicting AI features written |
| 7 | getFeatureEntries fails | postProcess returns emptyList (fail-safe) |

### 8.3 Test Dependencies

| Dependency | Purpose |
|-----------|---------|
| MockK | Mock FeatureRepository, AuditService, IndexWriter |
| Kotest | Property-based testing for validation |
| Testcontainers (PostgreSQL) | Integration tests for FeatureRepositoryImpl |
| JUnit 5 | Test framework |


---

## 9. Implementation Tasks

### 9.1 Ordered Task List

| # | Task | Module | Files | Estimate | Dependencies |
|---|------|--------|-------|----------|--------------|
| 1 | Add `KbDuplicateException` to `KbExceptions.kt` | kb-server | 1 | 0.5h | — |
| 2 | Create `FeatureConstants.kt` | kb-server | 1 | 0.5h | — |
| 3 | Create `FeatureValidation.kt` + unit tests | kb-server | 2 | 1h | Task 1 |
| 4 | Create `FeatureRepository.kt` interface | kb-server | 1 | 0.5h | — |
| 5 | Create `FeatureRepositoryImpl.kt` | kb-server | 1 | 2h | Task 4 |
| 6 | Write `FeatureRepositoryImplTest.kt` (Testcontainers) | kb-server | 1 | 2h | Task 5 |
| 7 | Create `KbFeatureListHandler.kt` + unit test | kb-server | 2 | 1.5h | Tasks 3, 4 |
| 8 | Create `KbFeatureCreateHandler.kt` + unit test | kb-server | 2 | 2h | Tasks 3, 4 |
| 9 | Create `KbFeatureUpdateHandler.kt` + unit test | kb-server | 2 | 2.5h | Tasks 3, 4 |
| 10 | Create `KbFeatureAssignHandler.kt` + unit test | kb-server | 2 | 2.5h | Tasks 3, 4 |
| 11 | Create `KbFeatureDeleteHandler.kt` + unit test | kb-server | 2 | 1h | Tasks 3, 4 |
| 12 | Update `KbDiModule.kt` — register new bindings | kb-server | 1 | 0.5h | Tasks 5, 7-11 |
| 13 | Add `getFeatureEntries()` to `IndexWriter` interface | sync-pipeline | 1 | 0.5h | — |
| 14 | Implement `getFeatureEntries()` in `PostgresIndexWriter` | sync-pipeline | 1 | 1h | Task 13 |
| 15 | Modify `FeatureDetectionDimension.postProcess()` — add protection | sync-pipeline | 1 | 2h | Task 14 |
| 16 | Write `FeatureDetectionProtectionTest.kt` | sync-pipeline | 1 | 2h | Task 15 |
| 17 | Integration test — full flow (create → list → AI sync → verify protection) | both | 1 | 2h | All above |

**Total Estimate:** ~22.5 hours (≈ 3 dev days)

### 9.2 Critical Path

```
Task 1 → Task 3 → Task 4 → Task 5 → Tasks 7-11 (parallel) → Task 12 → Task 17
                                                                          ↑
Task 13 → Task 14 → Task 15 → Task 16 ──────────────────────────────────┘
```

### 9.3 Parallelization Opportunities

| Parallel Track A (kb-server) | Parallel Track B (sync-pipeline) |
|------------------------------|----------------------------------|
| Tasks 1-12 | Tasks 13-16 |
| Can be done by DEV-1 | Can be done by DEV-2 |
| Integration test (Task 17) requires both tracks complete |

---

## 10. Flyway Migration

**NOT REQUIRED.** All new data fields are stored in the existing JSONB `data` column of `sync.index_entries`. No DDL changes needed.

Existing table structure supports:
- New keys in `data` map without schema change
- Backward compatibility (missing keys treated with defaults)
- No index changes needed (existing indexes sufficient for < 500 features/project)

---

## 11. Performance & Scalability

### 11.1 Performance Targets

| Operation | Target | Condition |
|-----------|--------|-----------|
| kb_feature_list | < 500ms | ≤ 500 features per project |
| kb_feature_create | < 1s | Single insert + duplicate check |
| kb_feature_update | < 1s | Single read + update |
| kb_feature_assign | < 1.5s | Read + find old + 2 updates |
| kb_feature_delete | < 500ms | Single delete with RETURNING |
| AI protection check | < 2s | ≤ 500 features loaded |

### 11.2 Connection Pool Usage

All feature operations use the existing `HikariDataSource` connection pool (shared with other kb-server operations). No additional pool configuration needed.

| Setting | Value | Rationale |
|---------|-------|-----------|
| maximumPoolSize | 10 (existing) | Feature ops are fast (< 100ms each) |
| connectionTimeout | 30000ms (existing) | Sufficient for all operations |
| idleTimeout | 600000ms (existing) | Standard idle timeout |

### 11.3 Scalability Limits

| Metric | Current Limit | Mitigation (if exceeded) |
|--------|--------------|--------------------------|
| Features per project | 500 | Add pagination to kb_feature_list |
| Tickets per feature | 100 | Switch from comma-separated to JSONB array |
| Concurrent BA users | 1 | Add optimistic locking (version field) |
| LIKE query on ticket_keys | < 500 rows | Add GIN index on data->'ticket_keys' |

---

## 12. Security & Audit

### 12.1 Access Control

All feature tools are protected by existing kb-server RLS (Row-Level Security) mechanism. No additional security implementation needed.

| Tool | Required Role | Enforcement |
|------|--------------|-------------|
| kb_feature_list | Any authenticated | Existing RLS |
| kb_feature_create | BA role | Existing RLS |
| kb_feature_update | BA role | Existing RLS |
| kb_feature_assign | BA role | Existing RLS |
| kb_feature_delete | BA role | Existing RLS |

### 12.2 Audit Events

All handlers log via existing `AuditService`:

```kotlin
auditService.log(AuditEvent(
    eventType = AuditEventType.FEATURE_CRUD,  // NEW enum value to add
    issueKey = null,
    action = "kb_feature_xxx",
    success = true,
    metadata = mapOf("feature_id" to featureId, "project_key" to projectKey)
))
```

**Note:** Add `FEATURE_CRUD` to `AuditEventType` enum.

### 12.3 Input Sanitization

| Field | Sanitization | Rationale |
|-------|-------------|-----------|
| project_key | Regex validation only | Stored as-is in DB |
| name | Length check, no HTML | Stored in JSONB, not rendered |
| description | Length check only | Stored in JSONB, not rendered |
| ticket_key | Regex validation only | Stored as-is |
| feature_id | Non-blank check | Used as DB lookup key |

---

## 13. Monitoring & Observability

### 13.1 Logging

| Event | Level | Fields |
|-------|-------|--------|
| Feature created | INFO | feature_id, project_key, name, ticket_count |
| Feature updated | INFO | feature_id, updated_fields, adopted |
| Feature deleted | INFO | feature_id, source |
| Feature assigned | INFO | feature_id, ticket_key, removed_from |
| AI protection: features filtered | INFO | project_key, detected_count, filtered_count |
| AI protection: fail-safe triggered | WARN | project_key, error_message |
| Handler error | ERROR | tool_name, error_code, message, stacktrace |

### 13.2 Key Metrics (Future)

| Metric | Type | Description |
|--------|------|-------------|
| feature_crud_operations_total | Counter | Total CRUD operations by type |
| feature_crud_duration_ms | Histogram | Operation latency |
| ai_protection_filtered_total | Counter | Features filtered by protection |
| ai_protection_failures_total | Counter | Protection check failures |


---

## 14. Open Questions & Decisions

| # | Question | Status | Decision |
|---|----------|--------|----------|
| 1 | Should `findByTicketKey` use GIN index for exact array matching? | Resolved | No — LIKE + app-layer check is sufficient for < 500 features |
| 2 | Should AI protection use `updated_at` timestamp to avoid race conditions? | Deferred to v2 | v1 accepts eventual consistency |
| 3 | Should `kb_feature_assign` support bulk assignment (multiple tickets)? | Deferred | v1 supports single ticket per call |
| 4 | Should deleted AI features be soft-deleted to prevent re-creation? | Resolved | No — hard delete + warning message is simpler |
| 5 | Coordinate with MTO-117 on `source` field definition | Open | Must align enum values before implementation |

---

## 15. Discrepancy Analysis (BRD vs FSD)

### 15.1 Minor Discrepancies Found

| # | Topic | BRD Statement | FSD Statement | Impact | Resolution |
|---|-------|---------------|---------------|--------|------------|
| 1 | Delete restriction | BRD Story 5: "Only features with source='manual' can be deleted directly" | FSD §3.5.2: Any feature can be deleted (no source restriction) | Low | **Follow FSD** — all features deletable, AI features get warning |
| 2 | KbNotFoundException constructor | BRD uses generic message pattern | Existing code: `KbNotFoundException(issueKey: String)` takes issueKey param | Low | **Reuse existing** — pass feature_id as issueKey parameter |
| 3 | Error code naming | BRD: `FEATURE_DUPLICATE_ERROR` | FSD §5: `FEATURE_DUPLICATE_ERROR`, Existing code pattern: `KB_DUPLICATE_ERROR` | Low | **Use `KB_DUPLICATE_ERROR`** — consistent with existing exception hierarchy |

### 15.2 Alignment Decisions

- **Delete behavior:** FSD is authoritative. Any feature (manual or AI) can be deleted. AI features show a warning that they may be re-created on next sync.
- **Error codes:** Use existing `KB_*` prefix pattern from `KbExceptions.kt` for consistency. The FSD's `FEATURE_*` codes are logical names; implementation maps to `KB_*` codes.
- **KbNotFoundException:** Reuse existing class — pass `feature_id` as the `issueKey` parameter (it's just a string identifier).

---

## 16. Appendix

### 16.1 Glossary

| Term | Definition |
|------|------------|
| Feature | Logical grouping of Jira tickets representing a business capability |
| Manual Feature | Feature created by BA via CRUD tools (source="manual") |
| AI-Detected Feature | Feature discovered by AI during sync (source="ai_detected") |
| Locked | Protection flag preventing AI from modifying a feature |
| Adoption | BA updates an AI feature → converts to manual/locked |
| IndexEntry | Universal record in sync.index_entries table |
| Dimension | Category of indexed data (e.g., "feature_grouping") |
| Idempotent | Repeated calls with same params produce same result |

### 16.2 File Inventory (New + Modified)

| # | File | Action | Module | Lines (est.) |
|---|------|--------|--------|-------------|
| 1 | `kb/feature/FeatureConstants.kt` | NEW | kb-server | ~20 |
| 2 | `kb/feature/FeatureValidation.kt` | NEW | kb-server | ~60 |
| 3 | `kb/feature/FeatureRepository.kt` | NEW | kb-server | ~25 |
| 4 | `kb/feature/FeatureRepositoryImpl.kt` | NEW | kb-server | ~150 |
| 5 | `kb/protocol/handlers/feature/KbFeatureListHandler.kt` | NEW | kb-server | ~80 |
| 6 | `kb/protocol/handlers/feature/KbFeatureCreateHandler.kt` | NEW | kb-server | ~120 |
| 7 | `kb/protocol/handlers/feature/KbFeatureUpdateHandler.kt` | NEW | kb-server | ~130 |
| 8 | `kb/protocol/handlers/feature/KbFeatureAssignHandler.kt` | NEW | kb-server | ~130 |
| 9 | `kb/protocol/handlers/feature/KbFeatureDeleteHandler.kt` | NEW | kb-server | ~70 |
| 10 | `kb/KbExceptions.kt` | MODIFIED | kb-server | +3 |
| 11 | `kb/di/KbDiModule.kt` | MODIFIED | kb-server | +8 |
| 12 | `sync/pipeline/storage/IndexWriter.kt` | MODIFIED | sync-pipeline | +3 |
| 13 | `sync/pipeline/storage/PostgresIndexWriter.kt` | MODIFIED | sync-pipeline | +25 |
| 14 | `sync/pipeline/dimension/builtin/FeatureDetectionDimension.kt` | MODIFIED | sync-pipeline | +50 |

### 16.3 Audit Event Type Addition

Add to `AuditEventType` enum:

```kotlin
enum class AuditEventType {
    // ... existing values ...
    FEATURE_CRUD  // NEW — covers all feature CRUD operations
}
```

### 16.4 HandlerUtils Extension (Optional)

Consider adding to `HandlerUtils.kt` for JSON array extraction:

```kotlin
/** Extract a JSON array of strings from arguments */
fun requireStringArray(args: JsonObject?, key: String): List<String>? {
    val arr = args?.get(key) as? JsonArray ?: return null
    return arr.map { it.jsonPrimitive.content }
}
```

This utility is needed by `KbFeatureCreateHandler` and `KbFeatureUpdateHandler` to extract `ticket_keys` from the input arguments.

---

*End of Technical Design Document*
