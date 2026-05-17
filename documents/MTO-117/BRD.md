# Business Requirements Document (BRD)

## KB-Server — MTO-117: Migrate Sync Tools from Orchestrator-Server

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-117 |
| Title | Migrate Sync Tools — Move jira_project_sync, jira_sync_status, jira_ticket_graph from Orchestrator |
| Author | BA Agent |
| Version | 1.3 |
| Date | 2025-07-19 |
| Status | Draft |
| Parent Epic | MTO-115 — KB-Server Consolidation |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | BA Agent – Business Analyst | Create document |
| Peer Reviewer | SA Agent – Solution Architect | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-19 | BA Agent | Initiate document — auto-generated from Jira ticket MTO-117 and epic MTO-115 context |
| 1.1 | 2026-05-15 | BA Agent | Added Story 7: Content Hash Skip Optimization — skip re-processing unchanged tickets during sync |
| 1.2 | 2026-05-15 | BA Agent | Added Story 8: Project-Scoped Code Indexing — bind source code index to Jira project_key for multi-project isolation |
| 1.3 | 2026-05-15 | BA Agent | Added Stories 9-10: Per-User Code Index Isolation + Reset Tool — prevent cross-user contamination |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |

---

## 1. Introduction

### 1.1 Scope

This document defines the business requirements for migrating all sync-related MCP tools and supporting modules from `orchestrator-server` to `kb-server`. The migration addresses a design flaw where sync responsibilities are split across two servers, causing tool duplication and unclear ownership.

**In scope:**

- Unify `jira_project_sync` (orchestrator-server) and `kb_sync_trigger` (kb-server) into a single tool on kb-server
- Unify `jira_sync_status` (orchestrator-server) and `kb_sync_status` (kb-server) into a single tool on kb-server
- Migrate `jira_ticket_graph` tool to kb-server
- Migrate sync dashboard HTTP routes (`/sync/*`) to kb-server
- Migrate crawler module (TicketCrawler, KBIngestor, ContentFetcher, GraphBuilder, AttachmentQueuer) to kb-server
- Backward compatibility for agents currently calling tools via orchestrator-server
- New tool: `jira_ticket_sync(issue_key)` for on-demand single-ticket sync (nice-to-have)

### 1.2 Out of Scope

- Changes to the `sync-pipeline` shared library module (already shared between both servers)
- Jira API client changes (remains in `orchestrator-client` module)
- UI redesign of graph-viewer or sync-dashboard HTML pages
- Authentication/authorization changes (existing auth middleware applies)
- Database schema changes (existing tables remain as-is)

### 1.3 Preliminary Requirement

- `kb-server` is operational with existing `kb_sync_trigger` and `kb_sync_status` handlers
- `sync-pipeline` shared module is available as a dependency for both servers
- PostgreSQL database with existing sync tables (`sync_state`, `ticket_cache`, `ticket_relations`, `attachment_queue`)
- Agent configuration system supports server endpoint updates
- MCP tool registration framework is available in kb-server (`KbToolHandler` interface)

---

## 2. Business Requirements

### 2.1 High Level Process Map

The migration consolidates sync tool ownership under `kb-server`, which is the natural home for knowledge base synchronization logic. The high-level flow after migration:

1. **Agent invokes sync tool** → MCP request routed to `kb-server`
2. **kb-server validates request** → Enqueues sync task via QueueService
3. **QueueWorker picks up task** → Invokes TicketCrawler for deep crawling
4. **TicketCrawler processes tickets** → ContentFetcher, GraphBuilder, KBIngestor work in pipeline
5. **Progress reported** → SyncStateTracker updates, SSE events emitted
6. **Agent queries status/graph** → kb-server responds with current state

### 2.2 List of User Stories / Use Cases

| # | Story / Use Case | Priority | Source Ticket |
|---|------------------|----------|---------------|
| 1 | As an AI agent, I want to trigger a Jira project sync via a single unified tool so that I don't need to know which server hosts the sync capability | MUST HAVE | MTO-117 |
| 2 | As an AI agent, I want to check sync progress via a single unified status tool so that I get consistent, comprehensive status information | MUST HAVE | MTO-117 |
| 3 | As an AI agent, I want to query the ticket relationship graph from kb-server so that graph data is co-located with the knowledge base | MUST HAVE | MTO-117 |
| 4 | As a developer, I want the sync dashboard to work from kb-server so that all sync monitoring is centralized | MUST HAVE | MTO-117 |
| 5 | As an AI agent, I want backward compatibility during migration so that existing workflows don't break | MUST HAVE | MTO-117 |
| 6 | As an AI agent, I want to sync a single ticket on-demand so that I can refresh specific ticket data without a full project sync | COULD HAVE | MTO-117 |
| 7 | As the system, I want to skip re-processing tickets whose content has not changed so that sync is faster and avoids unnecessary embedding regeneration | MUST HAVE | MTO-117 |
| 8 | As the system, I want source code index entries bound to Jira project_key so that multi-project KB search returns only relevant code context | MUST HAVE | MTO-117 |
| 9 | As the system, I want code index entries scoped per (project_key + user_id) so that one user's indexing mistakes do not pollute other users' KB context | MUST HAVE | MTO-117 |
| 10 | As a user, I want a tool to reset my code index for a project so that I can recover from incorrect indexing | MUST HAVE | MTO-117 |

---

### 2.3 Details of User Stories

---

#### Business Flow

**Step 1:** Agent determines it needs to sync Jira data (project-level or single-ticket)

**Step 2:** Agent calls the unified sync tool (`jira_project_sync`) via MCP protocol targeting kb-server

**Step 3:** kb-server validates the request (project key format, no duplicate sync running)

**Step 4:** kb-server enqueues a sync task with specified priority into the task queue

**Step 5:** QueueWorker dequeues the task and invokes the crawler pipeline

**Step 6:** Crawler fetches tickets from Jira, computes content hashes, builds graph edges, ingests into vector KB

**Step 7:** Progress is tracked via SyncStateTracker and emitted as SSE events for the dashboard

**Step 8:** Agent can query `jira_sync_status` at any time to check progress

**Step 9:** After sync completes, agent can query `jira_ticket_graph` for relationship data

> **Note:** During the transition period, orchestrator-server may forward sync tool calls to kb-server for backward compatibility. After all agent configs are updated, the forwarding can be removed.

---

#### STORY 1: Unified Project Sync Tool

> As an AI agent, I want to trigger a Jira project sync via a single unified tool so that I don't need to know which server hosts the sync capability.

**Requirement Details:**

1. Merge `jira_project_sync` (SyncToolHandler on orchestrator-server) and `kb_sync_trigger` (KbSyncTriggerHandler on kb-server) into one tool on kb-server
2. The unified tool MUST retain the tool name `jira_project_sync` for backward compatibility with existing agent prompts and configurations
3. The unified tool combines the best of both implementations: immediate async launch (from SyncToolHandler) + queue-based processing with priority support (from KbSyncTriggerHandler)
4. Remove `kb_sync_trigger` as a separate tool (merged into `jira_project_sync`)

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| projectKey | string | Yes | Jira project key to sync | "MTO" |
| fullSync | boolean | No (default: false) | Force full re-sync ignoring last sync timestamp | true |
| priority | string | No (default: "normal") | Queue priority: "high" or "normal" | "high" |

**Acceptance Criteria:**

1. `jira_project_sync` is registered and callable on kb-server
2. `jira_project_sync` is removed from orchestrator-server tool registry
3. `kb_sync_trigger` is removed (functionality merged into `jira_project_sync`)
4. Tool accepts `projectKey`, `fullSync`, and `priority` parameters
5. Tool enqueues sync task and returns immediately with task ID and status
6. Duplicate sync detection: returns error if sync already running for the same project
7. Audit trail is logged for every sync trigger

**Validation Rules:**

- `projectKey` must be non-empty, uppercase alphanumeric (1-10 chars)
- `priority` must be one of: "high", "normal" (case-insensitive)
- `fullSync` defaults to `false` if not provided

**Error Handling:**

- Missing `projectKey`: Return error `{"error": "projectKey is required"}`
- Invalid `projectKey` format: Return error `{"error": "Invalid project key format"}`
- Sync already running: Return error `{"error": "Sync already running for {projectKey}"}`
- Internal failure: Return error `{"error": "Failed to start sync: {message}"}` with `isError=true`

---

#### STORY 2: Unified Sync Status Tool

> As an AI agent, I want to check sync progress via a single unified status tool so that I get consistent, comprehensive status information.

**Requirement Details:**

1. Merge `jira_sync_status` (StatusToolHandler on orchestrator-server) and `kb_sync_status` (KbSyncStatusHandler on kb-server) into one tool on kb-server
2. The unified tool MUST retain the tool name `jira_sync_status` for backward compatibility
3. The unified response combines real-time progress data (from StatusToolHandler: progress %, synced/total counts) with queue metrics (from KbSyncStatusHandler: queue depths, processing counts)
4. Remove `kb_sync_status` as a separate tool (merged into `jira_sync_status`)

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| projectKey | string | No | Filter status by project key | "MTO" |

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| projectKey | string | Requested project key (if provided) |
| status | string | Current sync status: idle, syncing, completed, failed |
| progress | integer | Sync progress percentage (0-100) |
| syncedIssues | integer | Number of issues synced so far |
| totalIssues | integer | Total issues to sync |
| lastSyncTime | string | ISO timestamp of last completed sync |
| queue.hpqDepth | integer | High-priority queue depth |
| queue.npqDepth | integer | Normal-priority queue depth |
| queue.processing | integer | Currently processing tasks |
| queue.completedToday | integer | Tasks completed today |
| queue.failedToday | integer | Tasks failed today |

**Acceptance Criteria:**

1. `jira_sync_status` is registered and callable on kb-server
2. `jira_sync_status` is removed from orchestrator-server tool registry
3. `kb_sync_status` is removed (functionality merged into `jira_sync_status`)
4. Response includes both progress data AND queue metrics in a single response
5. When `projectKey` is provided, response includes project-specific progress
6. When `projectKey` is omitted, response includes global queue metrics only

**Error Handling:**

- Internal failure: Return error with `isError=true` and descriptive message
- Invalid project key: Return queue metrics with status "unknown" for the project

---

#### STORY 3: Migrate Ticket Graph Tool

> As an AI agent, I want to query the ticket relationship graph from kb-server so that graph data is co-located with the knowledge base.

**Requirement Details:**

1. Move `jira_ticket_graph` tool from orchestrator-server (GraphToolHandler) to kb-server
2. Retain the same tool name `jira_ticket_graph` and identical input/output schema
3. The tool queries `TicketGraphRepository` and `TicketCacheRepository` for graph data
4. BFS traversal logic (subgraph from a center issue) must be preserved exactly
5. Node limit of 1000 must be preserved for performance

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| projectKey | string | Yes | Jira project key | "MTO" |
| issueKey | string | No | Center issue for subgraph traversal | "MTO-15" |
| depth | integer | No (default: 2, range: 1-5) | BFS traversal depth from center issue | 3 |

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| nodes | array | Array of node objects (key, summary, status, issueType, labels, createdAt) |
| edges | array | Array of edge objects (source, target, type, category) |
| metadata.totalNodes | integer | Total node count |
| metadata.totalEdges | integer | Total edge count |
| metadata.projectKey | string | Project key |
| metadata.centerIssue | string | Center issue (if subgraph query) |
| metadata.depth | integer | Traversal depth used |

**Acceptance Criteria:**

1. `jira_ticket_graph` is registered and callable on kb-server
2. `jira_ticket_graph` is removed from orchestrator-server tool registry
3. Full project graph query works (projectKey only)
4. Subgraph BFS traversal works (projectKey + issueKey + depth)
5. Response format is identical to current implementation
6. Node limit of 1000 is enforced
7. Graph API HTTP endpoint (`/sync/graph/*`) also migrated to kb-server

**Validation Rules:**

- `projectKey` is required, non-empty
- `depth` must be between 1 and 5 (coerced if out of range)
- `issueKey` format: uppercase letters + hyphen + digits (e.g., "MTO-15")

**Error Handling:**

- Missing `projectKey`: Return error `{"error": "projectKey is required"}`
- Graph query failure: Return error `{"error": "Graph query failed: {message}"}`
- Empty graph (no data): Return valid response with empty nodes/edges arrays

---

#### STORY 4: Migrate Sync Dashboard Routes

> As a developer, I want the sync dashboard to work from kb-server so that all sync monitoring is centralized.

**Requirement Details:**

1. Migrate all `/sync/*` HTTP routes from orchestrator-server to kb-server:
   - `GET /sync/status/{projectKey}` — JSON status endpoint
   - `POST /sync/start` — Trigger sync via HTTP
   - `POST /sync/stop` — Cancel running sync
   - `GET /sync/live` — SSE stream for real-time progress
   - `GET /sync/graph/{projectKey}` — Graph data API
   - `GET /sync/graph/{projectKey}/{issueKey}` — Subgraph API
   - `GET /sync/projects` — List synced projects
   - `GET /sync/dashboard` — Serve dashboard HTML
   - `GET /sync/graph-viewer` — Serve graph viewer HTML
2. Static HTML files (`sync-dashboard.html`, `graph-viewer.html`) move to kb-server resources
3. Authentication middleware must be applied to all API endpoints (existing pattern)
4. SSE event bus for real-time sync progress must be available in kb-server

**Acceptance Criteria:**

1. All `/sync/*` routes respond correctly from kb-server
2. Sync dashboard HTML loads and functions (start/stop sync, view progress)
3. Graph viewer HTML loads and renders ticket graphs
4. SSE live stream delivers real-time sync events
5. Authentication is enforced on all API endpoints
6. Routes are removed from orchestrator-server
7. No CORS issues when dashboard is served from kb-server

**Error Handling:**

- Unauthenticated request: Return 401 with error message
- Invalid project key in path: Return 400 with usage hint
- SSE connection drop: Client auto-reconnects (existing behavior)

---

#### STORY 5: Backward Compatibility

> As an AI agent, I want backward compatibility during migration so that existing workflows don't break.

**Requirement Details:**

1. Agents currently configured to call `jira_project_sync`, `jira_sync_status`, `jira_ticket_graph` via orchestrator-server must continue to work during the transition period
2. Two strategies available (choose one or combine):
   - **Option A — Tool Forwarding:** orchestrator-server registers thin proxy tools that forward requests to kb-server via internal HTTP/MCP call
   - **Option B — Config Update:** Update all agent MCP server configurations to point sync tools at kb-server endpoint
3. Tool names remain unchanged (`jira_project_sync`, `jira_sync_status`, `jira_ticket_graph`) — only the hosting server changes
4. Transition period: tools available on both servers until all agents are confirmed migrated

**Acceptance Criteria:**

1. Existing agents can call sync tools without code changes (either via forwarding or config update)
2. No duplicate execution: a sync triggered via forwarding does not create a second sync task
3. Clear deprecation logging on orchestrator-server if forwarding is used
4. Documentation updated with new server endpoints
5. After migration confirmed complete, forwarding stubs can be safely removed from orchestrator-server

**Validation Rules:**

- If forwarding is used, orchestrator-server must validate that kb-server is reachable before forwarding
- Forwarding timeout: 30 seconds max

**Error Handling:**

- kb-server unreachable (forwarding mode): Return error `{"error": "Sync service unavailable, please retry"}`
- Config mismatch detected: Log warning with instructions to update agent config

---

#### STORY 6: Single Ticket On-Demand Sync (Nice-to-Have)

> As an AI agent, I want to sync a single ticket on-demand so that I can refresh specific ticket data without a full project sync.

**Requirement Details:**

1. New tool `jira_ticket_sync` registered on kb-server
2. Syncs a single ticket plus its directly linked tickets (1-hop)
3. Uses existing `TicketCrawler.crawlSingle(issueKey)` method
4. Updates graph edges, KB vector entry, and ticket cache for the target ticket
5. Does NOT require a full project sync to be running

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| issueKey | string | Yes | Jira issue key to sync | "MTO-117" |
| includeLinked | boolean | No (default: true) | Also sync directly linked tickets | false |

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| issueKey | string | The synced issue key |
| status | string | "completed" or "failed" |
| changed | boolean | Whether content changed since last sync |
| ingested | boolean | Whether KB entry was updated |
| linkedSynced | integer | Number of linked tickets also synced |
| duration | string | Processing duration |

**Acceptance Criteria:**

1. `jira_ticket_sync` is registered and callable on kb-server
2. Single ticket is fetched, hashed, and ingested if changed
3. Linked tickets (1-hop) are also synced when `includeLinked=true`
4. Graph edges are updated for the target ticket
5. Response includes sync result details
6. Does not interfere with running project-level syncs

**Validation Rules:**

- `issueKey` must match pattern: `[A-Z]+-\d+`
- `issueKey` must belong to a known project (exists in ticket_cache or fetchable from Jira)

**Error Handling:**

- Missing `issueKey`: Return error `{"error": "issueKey is required"}`
- Invalid format: Return error `{"error": "Invalid issue key format"}`
- Ticket not found in Jira: Return error `{"error": "Issue {issueKey} not found"}`
- Crawl failure: Return error with details and `isError=true`

---

#### STORY 7: Content Hash Skip Optimization

> As the system, I want to skip re-processing tickets whose content has not changed so that sync is faster and avoids unnecessary embedding regeneration.

**Requirement Details:**

1. During sync pipeline processing, before running DimensionProcessor on a ticket, compare the ticket's `contentHash` against the stored `content_hash` in `sync.index_entries`
2. If hash matches (content unchanged) → skip dimension processing, skip vector embedding, increment `skipped` counter
3. If hash differs or no existing entry → process normally (full dimension + embedding pipeline)
4. Content hash is computed from `summary + description` (already done in `TicketFetcherImpl`)
5. This optimization applies to incremental sync only — `fullSync=true` bypasses hash check (forces re-process)

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| contentHash | String | Yes (computed) | SHA-256 of ticket summary + description | `a1b2c3d4e5...` (64 chars) |
| existingHash | String? | Queried | Stored hash from previous sync | `a1b2c3d4e5...` or null |

**Acceptance Criteria:**

1. Unchanged tickets are skipped during incremental sync (no dimension processing, no embedding)
2. Changed tickets are fully processed (all dimensions + embedding regenerated)
3. New tickets (no existing entry) are fully processed
4. `fullSync=true` bypasses hash check — all tickets processed regardless
5. Sync result includes `skippedTickets` count for visibility
6. Performance improvement: incremental sync of 100 tickets where 90 unchanged completes in < 10% of full sync time
7. `jira_sync_status` response includes skipped count

**Validation Rules:**

- Content hash must be exactly 64 characters (SHA-256 hex)
- Hash comparison is case-insensitive
- Null/empty existing hash → treat as "changed" (process ticket)

**Error Handling:**

- Hash lookup failure (DB error) → treat as "changed" (process ticket, fail-safe)
- Hash computation failure → log warning, process ticket normally

---

#### STORY 8: Project-Scoped Code Indexing

> As the system, I want source code index entries bound to Jira project_key so that multi-project KB search returns only relevant code context.

**Requirement Details:**

1. Code intelligence indexer (`full-indexer.ts`) must read a `jiraProjectKey` field from `index-config.json`
2. All KB payloads generated by the indexer must use `jiraProjectKey` as the `project` field (not folder name)
3. When agents ingest code index payloads into KB via `kb_ingest`, the `project_key` must match the Jira project key
4. `kb_search` must filter by `project_key` when agents are working on a specific ticket — preventing cross-project contamination
5. Each workspace/repository maps to exactly ONE Jira project key (1:1 mapping)
6. If `jiraProjectKey` is not configured, fall back to folder name (backward compatible)

**Data Fields:**

| Field | Location | Type | Required | Description | Example |
|-------|----------|------|----------|-------------|---------|
| jiraProjectKey | index-config.json | String | No (fallback to folder name) | Jira project key for this codebase | "MTO" |
| project | kb-payloads.json | String | Yes | Project identifier used in KB ingestion | "MTO" |
| project_key | kb_ingest call | String | Yes | Project key passed to KB server | "MTO" |

**Acceptance Criteria:**

1. `index-config.json` supports `jiraProjectKey` field
2. `full-indexer.ts` uses `jiraProjectKey` (if present) as `project` in KB payloads
3. If `jiraProjectKey` not set, falls back to `path.basename(rootDir)` (backward compatible)
4. All code index entries in KB have correct `project_key` matching Jira project
5. `kb_search(query, project_key="MTO")` returns only MTO code entries, not other projects
6. Agent processes (BA/TA/SA/DEV) always pass `project_key` when searching KB for code context

**Validation Rules:**

- `jiraProjectKey` must match pattern `[A-Z][A-Z0-9_]+` (Jira project key format)
- If invalid format → log warning, fall back to folder name

**Error Handling:**

- Missing `jiraProjectKey` in config → use folder name (no error, backward compatible)
- Invalid format → log warning, use folder name

---

#### STORY 9: Per-User Code Index Isolation

> As the system, I want code index entries scoped per (project_key + user_id) so that one user's indexing mistakes do not pollute other users' KB context.

**Requirement Details:**

1. KB entries are categorized into two scopes:
   - **Shared** (`user_id = NULL`): Jira tickets, features, documents — visible to all users in the project
   - **Per-user** (`user_id = <user_id>`): Code index entries — visible only to the owning user
2. `kb_entries` and `kb_entry_embeddings` tables gain a nullable `user_id` column
3. Code index ingestion (`tags` contains "code-index") MUST include `user_id`
4. `kb_search` filter logic: `WHERE (user_id IS NULL OR user_id = :caller_user_id)` — returns shared + user's own entries
5. User A cannot see User B's code index entries
6. Shared entries (Jira tickets, features) remain visible to all users in the project

**Data Fields:**

| Field | Table | Type | Nullable | Description |
|-------|-------|------|----------|-------------|
| user_id | kb_entries | VARCHAR(100) | Yes (NULL = shared) | Owner of per-user entries |
| user_id | kb_entry_embeddings | VARCHAR(100) | Yes (NULL = shared) | Owner of per-user vectors |

**Acceptance Criteria:**

1. Code index entries are stored with `user_id` = caller's identity
2. `kb_search` returns shared entries + caller's own per-user entries (not other users')
3. Jira ticket entries remain shared (`user_id = NULL`) — visible to all
4. Feature entries remain shared (`user_id = NULL`) — visible to all
5. Existing entries (without `user_id`) are treated as shared (backward compatible)
6. Database migration adds `user_id` column with NULL default (non-breaking)

**Validation Rules:**

- `user_id` for code-index entries: must be non-empty when tag contains "code-index"
- `user_id` for shared entries: must be NULL (enforced at application level)
- `user_id` format: alphanumeric + dots/underscores/hyphens, max 100 chars

**Error Handling:**

- Code-index ingest without `user_id` → reject with error "user_id required for code-index entries"
- Search without `user_id` in context → return only shared entries (safe default)

---

#### STORY 10: Code Index Reset Tool

> As a user, I want a tool to reset my code index for a project so that I can recover from incorrect indexing.

**Requirement Details:**

1. New MCP tool `kb_code_index_reset` on kb-server
2. Deletes ALL code-index entries for a specific (project_key + user_id) combination
3. Deletes corresponding vector embeddings
4. Returns count of deleted entries for confirmation
5. Does NOT affect shared entries (Jira tickets, features, documents)
6. Does NOT affect other users' code index entries

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| project_key | String | Yes | Project to reset code index for | "MTO" |
| user_id | String | Yes | User whose code index to reset | "user-A" |
| confirm | Boolean | Yes | Safety confirmation flag | true |

**Acceptance Criteria:**

1. `kb_code_index_reset(project_key="MTO", user_id="user-A", confirm=true)` deletes all code-index entries for user-A in MTO
2. After reset, `kb_search` for user-A returns no code-index results (only shared entries)
3. Other users' code index entries are NOT affected
4. Shared entries (tickets, features) are NOT affected
5. `confirm=false` or missing → return preview (count of entries to be deleted) without deleting
6. Audit log records the reset action

**Error Handling:**

- Missing `confirm=true` → return preview only, no deletion
- No entries found → return `{ deleted: 0, message: "No code index entries found" }`
- Database error → return error with details

---

## 3. Dependencies

| Dependency | Type | Related Ticket | Description |
|------------|------|----------------|-------------|
| sync-pipeline module | System | MTO-47 | Shared library providing SyncOrchestrator, SyncStateTracker, and pipeline interfaces — must be available as kb-server dependency |
| orchestrator-client module | System | N/A | Provides EmbeddingService and VectorDbClient used by KBIngestor |
| PostgreSQL database | Infrastructure | MTO-15 | Existing sync tables (sync_state, ticket_cache, ticket_relations, attachment_queue) |
| Jira REST API | External | N/A | Source of ticket data — accessed via JiraClient in orchestrator-client |
| Qdrant Vector DB | Infrastructure | N/A | Target for KB ingestion (vector embeddings) |
| Agent MCP configurations | System | N/A | Agent configs must be updated to point sync tools at kb-server |
| MTO-116 | System | MTO-116 | Parallel ticket under MTO-115 — coordinate to avoid merge conflicts in kb-server |
| Authentication middleware | System | MTO-109 | Existing auth middleware must be applied to migrated HTTP routes |

---

## 4. Stakeholders

| Role | Name / Team | Responsibility | Source |
|------|-------------|----------------|--------|
| Product Owner | SM Agent | Approve migration scope and timeline | Epic owner |
| Solution Architect | SA Agent | Validate architecture decisions (single source of truth) | Technical review |
| Technical Architect | TA Agent | Design module structure and DI wiring in kb-server | Technical design |
| Developer | DEV Agent | Implement migration, write tests | Implementation |
| QA | QA Agent | Verify tool behavior, backward compatibility, dashboard functionality | Testing |
| DevOps | DevOps Agent | Update deployment configs, verify CI/CD pipeline | Deployment |

---

## 5. Risks and Assumptions

### 5.1 Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Agent workflows break during migration | High | Medium | Implement forwarding stubs on orchestrator-server during transition; phased rollout |
| Performance degradation — kb-server handling additional load | Medium | Low | kb-server already handles KB operations; sync tools are lightweight dispatchers |
| Merge conflicts with MTO-116 (parallel work on kb-server) | Medium | Medium | Coordinate with MTO-116; work in separate packages/directories |
| Data inconsistency during transition (tools on both servers) | High | Low | Ensure only one server actually executes sync; forwarding mode prevents duplicate execution |
| SSE event bus not available in kb-server | Medium | Low | kb-server already has WebSocket/SSE infrastructure for queue notifications |
| Graph repository access from kb-server | Low | Low | TicketGraphRepository is in sync-pipeline shared module, already accessible |

### 5.2 Assumptions

- `kb-server` has sufficient capacity to handle sync tool requests in addition to existing KB operations
- The `sync-pipeline` shared module provides all necessary interfaces (SyncOrchestrator, SyncStateTracker) without modification
- Agent configurations can be updated without downtime (hot-reload or restart acceptable)
- The existing database connection pool in kb-server can handle additional queries from sync tools
- Tool names (`jira_project_sync`, `jira_sync_status`, `jira_ticket_graph`) are the canonical names used in agent prompts
- MTO-116 does not modify the same files/packages that this ticket touches

---

## 6. Non-Functional Requirements

| Category | Requirement | Details |
|----------|-------------|---------|
| Performance | Sync tool response time | Tool invocation must return within 2 seconds (async dispatch, not waiting for sync completion) |
| Performance | Graph query response time | Graph queries must return within 5 seconds for projects with up to 1000 tickets |
| Performance | SSE latency | Real-time events must be delivered within 1 second of state change |
| Reliability | No data loss during migration | All existing sync state, ticket cache, and graph data must remain accessible after migration |
| Reliability | Graceful degradation | If kb-server is temporarily unavailable, agents receive clear error messages (not timeouts) |
| Scalability | Concurrent sync support | System must support at least 3 concurrent project syncs without interference |
| Security | Authentication | All HTTP endpoints require valid auth token (existing middleware) |
| Security | Tool access control | MCP tools accessible only to authenticated MCP clients |
| Maintainability | Single source of truth | After migration, sync logic exists in exactly one place (kb-server) — no duplication |
| Maintainability | Clean removal | orchestrator-server sync code can be fully deleted after migration confirmed |
| Observability | Logging | All tool invocations logged with project key, caller info, and result status |
| Observability | Audit trail | Sync triggers logged via AuditService (existing in kb-server) |

---

## 7. Related Tickets

| Ticket Key | Summary | Status | Type | Relationship |
|------------|---------|--------|------|--------------|
| MTO-117 | Migrate Sync Tools — Move jira_project_sync, jira_sync_status, jira_ticket_graph from Orchestrator | In Progress | Task | Main ticket |
| MTO-115 | KB-Server Consolidation | In Progress | Epic | Parent epic |
| MTO-116 | Parallel KB-Server work | In Progress | Task | Sibling (coordinate) |
| MTO-47 | Unified Sync Pipeline — Multi-Dimensional Jira Indexing | Done | Epic | Provides sync-pipeline shared module |
| MTO-15 | Database Schema & Sync State Management | Done | Story | Provides database tables used by sync tools |
| MTO-14 | Jira Project Sync Service | Done | Epic | Original epic that created the sync tools |
| MTO-109 | Authentication for HTTP endpoints | Done | Story | Provides auth middleware for dashboard routes |

---

## 8. Appendix

### Migration Checklist

| # | Item | From | To | Action |
|---|------|------|----|--------|
| 1 | SyncToolHandler.kt | orchestrator-server/synctools/ | kb-server/protocol/handlers/ | Rewrite as KbToolHandler implementation |
| 2 | StatusToolHandler.kt | orchestrator-server/synctools/ | kb-server/protocol/handlers/ | Merge with KbSyncStatusHandler |
| 3 | GraphToolHandler.kt | orchestrator-server/synctools/ | kb-server/protocol/handlers/ | Rewrite as KbToolHandler implementation |
| 4 | SyncToolRegistrar.kt | orchestrator-server/synctools/ | N/A | Remove (kb-server uses KbToolHandler auto-registration) |
| 5 | TicketCrawlerImpl.kt | orchestrator-server/crawler/ | kb-server/crawler/ | Move package |
| 6 | KBIngestorImpl.kt | orchestrator-server/crawler/ | kb-server/crawler/ | Move package |
| 7 | ContentFetcher | orchestrator-server/crawler/ | kb-server/crawler/ | Move package |
| 8 | GraphBuilder | orchestrator-server/crawler/ | kb-server/crawler/ | Move package |
| 9 | AttachmentQueuer | orchestrator-server/crawler/ | kb-server/crawler/ | Move package |
| 10 | SyncDashboardHandler.kt | orchestrator-server/server/ | kb-server/http/ | Adapt to kb-server HTTP framework |
| 11 | sync-dashboard.html | orchestrator-server/resources/static/ | kb-server/resources/static/ | Move file |
| 12 | graph-viewer.html | orchestrator-server/resources/static/ | kb-server/resources/static/ | Move file |
| 13 | GraphRoutes.kt | orchestrator-server/graph/ | kb-server/http/ | Adapt to kb-server routing |

### Tool Name Mapping (Before → After)

| Current Tool | Current Server | After Migration | New Server |
|--------------|---------------|-----------------|------------|
| jira_project_sync | orchestrator-server | jira_project_sync | kb-server |
| kb_sync_trigger | kb-server | _(removed, merged into jira_project_sync)_ | — |
| jira_sync_status | orchestrator-server | jira_sync_status | kb-server |
| kb_sync_status | kb-server | _(removed, merged into jira_sync_status)_ | — |
| jira_ticket_graph | orchestrator-server | jira_ticket_graph | kb-server |
| _(new)_ | — | jira_ticket_sync | kb-server |

### Glossary

| Term | Definition |
|------|------------|
| MCP | Model Context Protocol — communication protocol between AI agents and tool servers |
| SSE | Server-Sent Events — HTTP-based unidirectional real-time event streaming |
| BFS | Breadth-First Search — graph traversal algorithm used for subgraph extraction |
| HPQ | High Priority Queue — queue for urgent sync tasks |
| NPQ | Normal Priority Queue — queue for standard sync tasks |
| KB | Knowledge Base — vector database storing embedded ticket content for semantic search |
| SyncOrchestrator | Core interface in sync-pipeline module that coordinates the sync process |
| KbToolHandler | Interface in kb-server for registering MCP tools with standardized error handling |

### Reference Documents

| Document | Link / Location |
|----------|-----------------|
| sync-pipeline module analysis | `.analysis/code-intelligence/modules/sync-pipeline.md` |
| MTO-15 BRD (Database Schema) | `documents/MTO-15/BRD.md` |
| Architecture Overview | `docs/diagrams/architecture-overview.drawio` |
