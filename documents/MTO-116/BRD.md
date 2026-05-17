# Business Requirements Document (BRD)

## KB-Server — MTO-116: CRUD Tools — BA + AI Collaborative Feature Management

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-116 |
| Title | [KB-Server] Feature CRUD Tools — BA + AI Collaborative Feature Management |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-07-08 |
| Status | Draft |
| Epic Parent | MTO-115 |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | BA Agent – Business Analyst | Create document |
| Peer Reviewer | SA Agent – Solution Architect | Review technical feasibility |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-07-08 | BA Agent | Initiate document — auto-generated from Jira ticket MTO-116 and linked tickets |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |

---

## 1. Introduction

### 1.1 Scope

This document defines the business requirements for implementing CRUD (Create, Read, Update, Delete) MCP tools that enable Business Analysts to manage project features collaboratively with AI-powered feature detection. The scope includes:

1. **Five new MCP tools** in `kb-server` for feature lifecycle management (`kb_feature_list`, `kb_feature_create`, `kb_feature_update`, `kb_feature_assign`, `kb_feature_delete`)
2. **AI feature detection fix** — introducing a `source` field to distinguish manual vs AI-detected features, ensuring AI never overwrites BA-created features
3. **Data model enhancement** — extending `sync.index_entries` data map with `source`, `created_by`, and `locked` fields

### 1.2 Out of Scope

- UI/frontend for feature management (future ticket)
- Feature dependency mapping between features
- Feature versioning/history tracking
- Bulk import/export of features
- Feature approval workflow
- Cross-project feature linking

### 1.3 Preliminary Requirement

- `sync-pipeline` module must be operational with `FeatureDetectionDimension` running in post-processing
- `kb-server` MCP protocol infrastructure must be available (handler registration, tool schema)
- Database table `sync.index_entries` must exist with current schema
- Ticket MTO-117 (parallel) may share data model changes — coordinate on `source` field definition

---

## 2. Business Requirements

### 2.1 High Level Process Map

The feature management system operates as a collaborative workspace where both BA (manual) and AI (automated) contribute to a unified feature list:

1. **AI Detection Phase** — During sync pipeline post-processing, `FeatureDetectionDimension` detects features via epic hierarchy and AI analysis, storing them with `source = "ai_detected"` or `source = "epic_hierarchy"`
2. **BA Management Phase** — BA uses MCP tools to create, update, assign tickets to, or delete features with `source = "manual"`
3. **Protection Phase** — On subsequent syncs, AI respects `source = "manual"` and `locked = true` entries, only adding new features or updating its own entries
4. **Unified View** — Both AI and BA features coexist in `sync.index_entries` (dimension_id = "feature_grouping"), queryable via `kb_feature_list`

### 2.2 List of User Stories / Use Cases

| # | Story / Use Case | Priority | Source Ticket |
|---|------------------|----------|---------------|
| 1 | As a BA, I want to list all features of a project so that I can see both AI-detected and manually created features | MUST HAVE | MTO-116 |
| 2 | As a BA, I want to create a new feature with assigned tickets so that I can define business features that AI cannot detect | MUST HAVE | MTO-116 |
| 3 | As a BA, I want to update an existing feature (name, description, tickets) so that I can refine feature definitions over time | MUST HAVE | MTO-116 |
| 4 | As a BA, I want to assign/unassign tickets to/from a feature so that I can correct AI groupings | MUST HAVE | MTO-116 |
| 5 | As a BA, I want to delete a feature so that I can remove incorrect or obsolete features | MUST HAVE | MTO-116 |
| 6 | As a system, AI sync must not overwrite features with source="manual" so that BA work is preserved | MUST HAVE | MTO-116 |
| 7 | As a system, AI can suggest new features (source="ai_detected") so that feature discovery is automated | SHOULD HAVE | MTO-116 |

---

### 2.3 Details of User Stories

---

#### Business Flow

**Step 1:** BA calls `kb_feature_list(project_key)` to view all current features (both AI-detected and manual)

**Step 2:** BA identifies gaps — features that AI missed or incorrectly grouped

**Step 3:** BA calls `kb_feature_create(project_key, name, ticket_keys, description)` to create a new manual feature

**Step 4:** BA calls `kb_feature_assign(feature_id, ticket_key)` to move tickets between features or add tickets to existing features

**Step 5:** BA calls `kb_feature_update(feature_id, ...)` to refine feature name/description

**Step 6:** Sync pipeline runs — AI detects features but skips entries where `source = "manual"` or `locked = true`

**Step 7:** BA calls `kb_feature_list` again to see updated unified list with both AI and manual features

> **Note:** AI features (source="ai_detected") can be "adopted" by BA via `kb_feature_update` which changes source to "manual" and sets `locked = true`.

---

#### STORY 1: List All Features

> As a BA, I want to list all features of a project so that I can see both AI-detected and manually created features in a unified view.

**Requirement Details:**

1. Tool `kb_feature_list` accepts `project_key` as required parameter
2. Returns all features from `sync.index_entries` where `dimension_id = "feature_grouping"` and `project_key` matches
3. Response includes feature metadata: id, name, source, ticket_keys, description, locked status, created_by, detection_method, confidence
4. Results are sorted by source (manual first) then by name alphabetically

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| project_key | String | Yes | Jira project key to filter features | "MTO" |

**Response Fields:**

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| feature_id | String | Unique feature identifier | "feature:epic-MTO-115" |
| feature_name | String | Human-readable feature name | "KB Feature Management" |
| source | String | Origin: manual, ai_detected, epic_hierarchy | "manual" |
| ticket_keys | List<String> | Associated ticket keys | ["MTO-116", "MTO-117"] |
| description | String? | Feature description | "CRUD tools for features" |
| locked | Boolean | Whether AI can modify this feature | true |
| created_by | String? | Who created (agent role or "ai") | "ba-agent" |
| detection_method | String? | How feature was detected | "epic_hierarchy" |
| confidence | Double? | AI confidence score (0.0-1.0) | 0.95 |

**Acceptance Criteria:**

1. `kb_feature_list("MTO")` returns all features for project MTO
2. Response includes both manual and AI-detected features
3. Each feature shows its source, locked status, and associated tickets
4. Empty project returns empty list (not error)
5. Invalid project_key returns empty list

**Validation Rules:**

- `project_key` must be non-empty string
- `project_key` must match pattern `[A-Z][A-Z0-9_]+` (Jira project key format)

**Error Handling:**

- Empty/null `project_key`: Return validation error `FEATURE_VALIDATION_ERROR: project_key is required`
- Invalid project_key format: Return validation error `FEATURE_VALIDATION_ERROR: invalid project_key format`
- Database error: Return `FEATURE_INTERNAL_ERROR: Failed to list features`

---

#### STORY 2: Create Feature

> As a BA, I want to create a new feature with assigned tickets so that I can define business features that AI cannot detect.

**Requirement Details:**

1. Tool `kb_feature_create` accepts project_key, name, ticket_keys, and optional description
2. Creates a new `IndexEntry` in `sync.index_entries` with `dimension_id = "feature_grouping"`
3. Sets `source = "manual"`, `locked = true`, `created_by = "ba-agent"`
4. Generates deterministic feature_id from project_key + name hash
5. Generates vector text for semantic search: "Feature: {name}. Tickets: {ticket_keys}"

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| project_key | String | Yes | Target project | "MTO" |
| name | String | Yes | Feature name | "KB Feature Management" |
| ticket_keys | List<String> | Yes | Tickets belonging to this feature | ["MTO-116", "MTO-117"] |
| description | String | No | Feature description | "CRUD tools for managing features" |

**Acceptance Criteria:**

1. `kb_feature_create("MTO", "Auth Module", ["MTO-10", "MTO-11"], "Authentication feature")` creates a new feature entry
2. Created feature has `source = "manual"` and `locked = true`
3. Created feature is visible in subsequent `kb_feature_list` calls
4. Duplicate feature name within same project returns error (no duplicates allowed)
5. Feature ID is deterministic — same inputs produce same ID

**Validation Rules:**

- `project_key` must be non-empty, valid Jira format
- `name` must be non-empty, max 200 characters
- `ticket_keys` must be non-empty list (at least 1 ticket)
- Each ticket_key must match Jira format `[A-Z]+-\d+`
- `description` max 2000 characters (optional)

**Error Handling:**

- Missing required fields: `FEATURE_VALIDATION_ERROR: {field} is required`
- Duplicate feature name: `FEATURE_DUPLICATE_ERROR: Feature '{name}' already exists in project {project_key}`
- Invalid ticket_key format: `FEATURE_VALIDATION_ERROR: invalid ticket_key format: {key}`
- Database error: `FEATURE_INTERNAL_ERROR: Failed to create feature`

---

#### STORY 3: Update Feature

> As a BA, I want to update an existing feature (name, description, tickets) so that I can refine feature definitions over time.

**Requirement Details:**

1. Tool `kb_feature_update` accepts feature_id (required) and optional fields: name, ticket_keys, description
2. Only updates provided fields (partial update / PATCH semantics)
3. If updating an AI-detected feature, changes `source` to "manual" and sets `locked = true` (BA adoption)
4. Updates vector text if name or ticket_keys change
5. Preserves fields not included in the update

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| feature_id | String | Yes | Feature to update | "feature:epic-MTO-115" |
| name | String | No | New feature name | "KB Feature CRUD" |
| ticket_keys | List<String> | No | Replace ticket list | ["MTO-116", "MTO-117", "MTO-118"] |
| description | String | No | New description | "Updated description" |

**Acceptance Criteria:**

1. `kb_feature_update("feature:abc", name="New Name")` updates only the name
2. `kb_feature_update("feature:abc", ticket_keys=["MTO-1"])` replaces the ticket list
3. Updating an AI feature (source="ai_detected") changes source to "manual" and locked to true
4. Feature not found returns error
5. At least one optional field must be provided

**Validation Rules:**

- `feature_id` must be non-empty
- At least one of `name`, `ticket_keys`, `description` must be provided
- `name` if provided: non-empty, max 200 characters
- `ticket_keys` if provided: non-empty list, valid Jira format
- `description` if provided: max 2000 characters
- Updated name must not conflict with existing feature in same project

**Error Handling:**

- Feature not found: `FEATURE_NOT_FOUND: Feature '{feature_id}' does not exist`
- No fields to update: `FEATURE_VALIDATION_ERROR: at least one field must be provided for update`
- Name conflict: `FEATURE_DUPLICATE_ERROR: Feature '{name}' already exists in project {project_key}`
- Database error: `FEATURE_INTERNAL_ERROR: Failed to update feature`

---

#### STORY 4: Assign Ticket to Feature

> As a BA, I want to assign/unassign tickets to/from a feature so that I can correct AI groupings or add new tickets.

**Requirement Details:**

1. Tool `kb_feature_assign` accepts feature_id and ticket_key
2. Adds ticket_key to the feature's ticket_keys list (if not already present)
3. If ticket is already assigned to another feature in the same project, it is removed from the old feature (ticket belongs to one feature at a time)
4. If the feature is AI-detected, assigning a ticket changes source to "manual" and sets locked to true
5. Updates vector text to include new ticket

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| feature_id | String | Yes | Target feature | "feature:epic-MTO-115" |
| ticket_key | String | Yes | Ticket to assign | "MTO-120" |

**Acceptance Criteria:**

1. `kb_feature_assign("feature:abc", "MTO-120")` adds MTO-120 to feature abc
2. If MTO-120 was in feature xyz, it is removed from xyz and added to abc
3. Assigning a ticket already in the feature is idempotent (no error, no duplicate)
4. Feature not found returns error
5. Response confirms the assignment and shows updated ticket list

**Validation Rules:**

- `feature_id` must be non-empty
- `ticket_key` must be non-empty, valid Jira format `[A-Z]+-\d+`

**Error Handling:**

- Feature not found: `FEATURE_NOT_FOUND: Feature '{feature_id}' does not exist`
- Invalid ticket_key: `FEATURE_VALIDATION_ERROR: invalid ticket_key format`
- Database error: `FEATURE_INTERNAL_ERROR: Failed to assign ticket`

---

#### STORY 5: Delete Feature

> As a BA, I want to delete a feature so that I can remove incorrect or obsolete features.

**Requirement Details:**

1. Tool `kb_feature_delete` accepts feature_id
2. Deletes the IndexEntry from `sync.index_entries`
3. Only features with `source = "manual"` can be deleted directly
4. AI-detected features (source="ai_detected" or "epic_hierarchy") can be deleted but will be re-created on next sync unless the underlying epic/tickets are removed
5. Returns confirmation with deleted feature details

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| feature_id | String | Yes | Feature to delete | "feature:manual-abc123" |

**Acceptance Criteria:**

1. `kb_feature_delete("feature:manual-abc")` removes the feature from index_entries
2. Deleted feature no longer appears in `kb_feature_list`
3. Feature not found returns error
4. Deleting AI feature shows warning that it may be re-created on next sync
5. Tickets previously in the deleted feature become unassigned (available for other features)

**Validation Rules:**

- `feature_id` must be non-empty

**Error Handling:**

- Feature not found: `FEATURE_NOT_FOUND: Feature '{feature_id}' does not exist`
- Database error: `FEATURE_INTERNAL_ERROR: Failed to delete feature`

---

#### STORY 6: AI Sync Protection

> As a system, AI sync must not overwrite features with source="manual" so that BA work is preserved across sync cycles.

**Requirement Details:**

1. `FeatureDetectionDimension.postProcess()` must check existing features before writing
2. Features with `source = "manual"` or `locked = true` are never modified by AI
3. AI can only INSERT new features (source="ai_detected") or UPDATE features where `source != "manual"` and `locked != true`
4. AI must merge its results with existing manual features — not replace the entire feature list
5. If AI detects a feature that overlaps with a manual feature (same tickets), AI skips it

**Data Fields (new fields in IndexEntry.data map):**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| source | String | Yes | Origin of feature | "manual" / "ai_detected" / "epic_hierarchy" |
| created_by | String | Yes | Creator identifier | "ba-agent" / "ai-sync" |
| locked | String | Yes | Whether AI can modify | "true" / "false" |

**Acceptance Criteria:**

1. After sync, features with `source = "manual"` remain unchanged
2. After sync, features with `locked = "true"` remain unchanged
3. AI can create new features with `source = "ai_detected"`, `locked = "false"`
4. AI can update existing features where `source = "ai_detected"` and `locked = "false"`
5. AI does not create duplicate features for tickets already assigned to manual features
6. Existing features without `source` field are treated as `source = "ai_detected"` (backward compatibility)

**Validation Rules:**

- `source` must be one of: "manual", "ai_detected", "epic_hierarchy"
- `locked` must be "true" or "false" (stored as string in data map)
- `created_by` must be non-empty

**Error Handling:**

- If AI cannot determine source of existing feature: treat as "ai_detected" (safe default — AI can update its own)
- If database read fails during protection check: skip AI update for that feature (fail-safe)

---

#### STORY 7: AI Feature Suggestion

> As a system, AI can suggest new features (source="ai_detected") so that feature discovery remains automated and additive.

**Requirement Details:**

1. AI continues to run `detectFeatures()` during post-processing
2. Before writing results, AI loads existing features and filters out conflicts
3. New AI features are written with `source = "ai_detected"`, `created_by = "ai-sync"`, `locked = "false"`
4. AI features are visible in `kb_feature_list` alongside manual features
5. BA can "adopt" AI features by calling `kb_feature_update` (which changes source to "manual")

**Acceptance Criteria:**

1. AI creates new features for ticket groups not covered by manual features
2. AI features appear in `kb_feature_list` with `source = "ai_detected"`
3. AI features have `locked = "false"` — BA can modify or delete them
4. BA can adopt AI feature via update (source changes to "manual", locked becomes "true")
5. On next sync, adopted features are protected from AI modification

---

## 3. Dependencies

| Dependency | Type | Related Ticket | Description |
|------------|------|----------------|-------------|
| sync.index_entries table | System | N/A | Features stored in existing table with dimension_id="feature_grouping" |
| FeatureDetectionDimension | System | MTO-116 | Must be modified to respect source/locked fields |
| kb-server MCP infrastructure | System | N/A | Handler registration, tool schema, protocol layer (pattern: KbIngestHandler) |
| IndexWriter | System | N/A | Used by FeatureDetectionDimension to read/write index entries |
| AiAnalysisService | System | N/A | AI feature detection — must remain functional with additive-only behavior |
| MTO-117 | Parallel Work | MTO-117 | May share data model changes — coordinate on source field definition |
| MTO-115 | Epic Parent | MTO-115 | Parent epic for feature management capabilities |

---

## 4. Stakeholders

| Role | Name / Team | Responsibility | Source |
|------|-------------|----------------|--------|
| BA Agent | Business Analysis Team | Primary user of CRUD tools, defines manual features | Tool consumer |
| AI Sync Pipeline | Automated System | Detects features, respects manual features | System actor |
| SA Agent | Solution Architecture | Validates data model changes, reviews handler design | Technical review |
| DEV Agent | Development Team | Implements handlers and AI protection logic | Implementation |
| QA Agent | Quality Assurance | Validates AC, tests AI/BA interaction scenarios | Testing |

---

## 5. Risks and Assumptions

### 5.1 Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| AI overwrites manual features due to race condition (sync runs while BA is editing) | High | Medium | Use `locked` field as hard protection; AI reads lock status before any write |
| Data migration — existing features lack `source` field | Medium | High | Backward compatibility: treat missing source as "ai_detected" (safe default) |
| Feature ID collision between manual and AI-generated features | Medium | Low | Use distinct prefixes: "feature:manual-{hash}" vs "feature:epic-{key}" vs "feature:ai-{hash}" |
| Ticket assigned to multiple features across different sources | Medium | Medium | Enforce single-feature-per-ticket rule in `kb_feature_assign`; AI respects existing assignments |
| Performance degradation with large feature lists | Low | Low | Index on dimension_id + project_key already exists; feature count typically < 100 per project |
| MTO-117 parallel work creates conflicting data model changes | Medium | Medium | Coordinate source field definition early; use same enum values |

### 5.2 Assumptions

- `sync.index_entries` table supports the existing `data` map (JSONB) without schema migration for new fields
- Feature count per project is manageable (< 200 features) — no pagination needed for v1
- BA Agent is the only manual consumer of CRUD tools (no multi-user concurrency for v1)
- AI sync runs periodically (not real-time) — no real-time conflict resolution needed
- `IndexWriter` already supports read operations (getTicketSummaries exists, similar query for features)
- kb-server has access to sync.index_entries table (same database or cross-schema access)

---

## 6. Non-Functional Requirements

| Category | Requirement | Details |
|----------|-------------|---------|
| Performance | Tool response time < 2 seconds | All CRUD operations must complete within 2s for projects with < 200 features |
| Performance | AI sync additive check < 5 seconds | Loading existing features and filtering conflicts must not significantly slow sync |
| Reliability | Atomic operations | Create/Update/Delete must be atomic — no partial writes |
| Reliability | Fail-safe AI behavior | If protection check fails, AI must skip (not overwrite) |
| Data Integrity | Single source of truth | Features stored in one location (sync.index_entries) — no duplication |
| Data Integrity | Idempotent operations | Repeated calls with same params produce same result (no duplicates) |
| Backward Compatibility | Existing features preserved | Migration must not break existing AI-detected features |
| Security | Role-based access | Only BA-role callers can use CRUD tools (enforced by kb-server RLS) |
| Observability | Audit logging | All CRUD operations logged with caller, timestamp, action (pattern: AuditService) |
| Scalability | Support up to 500 features per project | Data model and queries must handle growth beyond initial assumptions |

---

## 7. Related Tickets

| Ticket Key | Summary | Status | Type | Relationship |
|------------|---------|--------|------|--------------|
| MTO-116 | [KB-Server] Feature CRUD Tools — BA + AI Collaborative Feature Management | In Progress | Story | Main ticket |
| MTO-115 | Feature Management Epic | Active | Epic | Parent epic |
| MTO-117 | (Parallel) Related feature management work | Planned | Story | Parallel sibling — coordinate data model |

---

## 8. Appendix

### Data Model Details

**Storage Location:** `sync.index_entries` table

**Dimension ID:** `"feature_grouping"`

**IndexEntry structure (existing):**

```kotlin
data class IndexEntry(
    val id: String,              // Deterministic: hash of entryKey
    val dimensionId: String,     // "feature_grouping"
    val projectKey: String,      // "MTO"
    val ticketKey: String?,      // null for features (multi-ticket)
    val entryKey: String,        // "feature:{feature_id}"
    val sourceRef: SourceRef,    // Provenance tracking
    val data: Map<String, String?>,  // Feature metadata
    val vectorText: String?      // For semantic search
)
```

**New fields in `data` map:**

| Key | Type | Values | Description |
|-----|------|--------|-------------|
| source | String | "manual" / "ai_detected" / "epic_hierarchy" | Who created this feature |
| created_by | String | "ba-agent" / "ai-sync" | Specific creator identity |
| locked | String | "true" / "false" | Hard protection flag against AI overwrite |
| feature_id | String | Unique ID | Feature identifier |
| feature_name | String | Display name | Human-readable name |
| ticket_keys | String | Comma-separated | "MTO-116,MTO-117" |
| description | String? | Free text | Feature description |
| detection_method | String? | Method used | "epic_hierarchy" / "ai_analysis" / "manual" |
| confidence | String? | "0.0" - "1.0" | AI confidence (null for manual) |
| epic_key | String? | Epic ticket key | Source epic (if epic_hierarchy) |

**Feature ID Generation:**

- Manual features: `"feature:manual-{sha256(projectKey + name).take(12)}"`
- Epic-based features: `"feature:epic-{epicKey}"` (existing pattern)
- AI-detected features: `"feature:ai-{sha256(ticketKeys.sorted().joinToString()).take(12)}"`

**SourceRef for manual features:**

```kotlin
SourceRef(
    type = "manual",
    path = "manual:feature/{featureId}",
    syncedAt = Clock.System.now(),
    derivedFrom = ticketKeys.map { "jira:$projectKey/$it" }
)
```

### Handler Pattern Reference

New handlers follow the same pattern as `KbIngestHandler`:
- Implement `KbToolHandler` interface
- Define `toolName`, `description`, `inputSchema`
- Implement `handle(arguments: JsonObject?): CallToolResult`
- Use `HandlerUtils` for parameter extraction and response building
- Log via `AuditService` for observability
- Wrap errors in typed exceptions (`KbValidationException`, `KbException`)

### AI Protection Algorithm (FeatureDetectionDimension changes)

```
postProcess(projectKey):
  1. existingFeatures = loadExistingFeatures(projectKey)
  2. protectedIds = existingFeatures
       .filter { it.data["source"] == "manual" || it.data["locked"] == "true" }
       .map { it.id }
  3. protectedTickets = existingFeatures
       .filter { it.id in protectedIds }
       .flatMap { it.data["ticket_keys"]?.split(",") ?: emptyList() }
  4. aiFeatures = aiService.detectFeatures(summaries)
  5. newFeatures = aiFeatures.filter { ai ->
       ai.featureId !in protectedIds &&
       ai.ticketKeys.none { it in protectedTickets }
     }
  6. Write newFeatures with source="ai_detected", locked="false"
  7. Update existing AI features (source="ai_detected", locked="false") if changed
```

### Glossary

| Term | Definition |
|------|------------|
| Feature | A logical grouping of related Jira tickets representing a business capability |
| Manual Feature | A feature created by BA via CRUD tools (source="manual") |
| AI-Detected Feature | A feature discovered by AI analysis during sync (source="ai_detected") |
| Epic-Hierarchy Feature | A feature derived from Jira epic structure (source="epic_hierarchy") |
| Locked | A protection flag that prevents AI from modifying a feature |
| Adoption | The act of a BA updating an AI feature, converting it to manual/locked |
| Dimension | A category of indexed data in sync pipeline (e.g., "feature_grouping") |
| IndexEntry | A single record in sync.index_entries representing one indexed item |

### Reference Documents

| Document | Link / Location |
|----------|-----------------|
| FeatureDetectionDimension | sync-pipeline/src/.../dimension/builtin/FeatureDetectionDimension.kt |
| AiAnalysisServiceImpl | sync-pipeline/src/.../ai/AiAnalysisServiceImpl.kt |
| IndexEntry Model | sync-pipeline/src/.../model/IndexEntry.kt |
| SourceRef Model | sync-pipeline/src/.../model/SourceRef.kt |
| KbIngestHandler (pattern) | kb-server/src/.../protocol/handlers/KbIngestHandler.kt |
| BRD Template | documents/templates/BRD-TEMPLATE.md |
