# Software Test Cases (STC)

## MCPOrchestration — MTO-20: MCP Tool Integration – Sync & Graph Tools

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-20 |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Related STP | STP-v1-MTO-20.docx |

---

## 1. PBT — Property-Based Tests

### TC-PBT-001: projectKey validation accepts only valid patterns

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | UC-01, UC-02, UC-03 |
| Property | For any string matching `^[A-Z][A-Z0-9_]+$`, validation passes; otherwise rejects |
| Generator | Arb.string(1..20) |
| Iterations | 1000 |

### TC-PBT-002: depth parameter clamped to 1-5

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | BR-09 |
| Property | For any Int, effective depth = clamp(input, 1, 5) |
| Generator | Arb.int(-10..100) |
| Iterations | 500 |

### TC-PBT-003: Graph response never exceeds 1000 nodes

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | BR-11 |
| Property | For any graph size, response.nodes.size ≤ 1000 |
| Generator | Arb.int(1..5000) (simulated graph sizes) |
| Iterations | 100 |

---

## 2. UT — Unit Tests

### TC-001: jira_project_sync — returns immediately (async)

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-01 |
| Input | { "projectKey": "MTO" } |
| Expected | Response within 200ms, status="started", scan running in background |

### TC-002: jira_project_sync — rejects duplicate sync

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-02 |
| Precondition | Scan already running for "MTO" |
| Expected | Error: "Sync already running for MTO" |

### TC-003: jira_project_sync — not in auto-approve list

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-03 |
| Steps | 1. Check tool metadata 2. Verify autoApprove = false |
| Expected | Tool requires user approval before execution |

### TC-004: jira_project_sync — invalid project key rejected

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | Input validation |
| Input | { "projectKey": "invalid-key" } |
| Expected | Error: "Invalid project key: invalid-key" |

### TC-005: jira_sync_status — returns current state from DB

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-04 |
| Precondition | Mock SyncStateManager returns RUNNING state |
| Expected | Response: status="syncing", progress=67.5, phases breakdown |

### TC-006: jira_sync_status — never_synced for unknown project

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-05 |
| Precondition | No state exists for "UNKNOWN" |
| Expected | Response: status="never_synced" |

### TC-007: jira_sync_status — includes phase breakdown

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-06 |
| Expected | Response contains phases.scan, phases.crawl, phases.attachments |

### TC-008: jira_ticket_graph — full project graph (no issueKey)

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-08 |
| Input | { "projectKey": "MTO" } |
| Expected | All nodes and edges for project returned |

### TC-009: jira_ticket_graph — BFS subgraph from center issue

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-07 |
| Input | { "projectKey": "MTO", "issueKey": "MTO-14", "depth": 2 } |
| Expected | Only nodes within 2 hops of MTO-14 returned |

### TC-010: jira_ticket_graph — depth clamped to valid range

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-09 |
| Input A | depth = 0 → effective depth = 1 |
| Input B | depth = 10 → effective depth = 5 |

### TC-011: jira_ticket_graph — relationship type filter

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-10 |
| Input | { "projectKey": "MTO", "relationshipTypes": ["blocks", "parent"] } |
| Expected | Only edges with type "blocks" or "parent" in response |

### TC-012: jira_ticket_graph — max 1000 nodes truncation

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-11 |
| Precondition | Mock graph with 1500 nodes |
| Expected | Response has exactly 1000 nodes + truncation warning |

---

## 3. IT — Integration Tests

### TC-IT-001: Tool registration — all 3 tools discoverable

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Precondition | Ktor testApplication with MCP server |
| Steps | 1. Call tools/list 2. Verify 3 new tools present |
| Expected | jira_project_sync, jira_sync_status, jira_ticket_graph in tool list |

### TC-IT-002: Auto-approve config loaded correctly

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Steps | 1. Check tool metadata for auto-approve flag |
| Expected | jira_sync_status and jira_ticket_graph: autoApprove=true; jira_project_sync: false |

### TC-IT-003: jira_project_sync triggers real ProjectScanner

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Precondition | Testcontainers PostgreSQL, mock Jira |
| Steps | 1. Call tool 2. Wait 3. Check DB state |
| Expected | jira_sync_state shows RUNNING then COMPLETED |

### TC-IT-004: jira_ticket_graph queries real DB

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Precondition | Testcontainers PostgreSQL with graph data |
| Steps | 1. Call tool with projectKey 2. Verify response matches DB |
| Expected | Nodes and edges match jira_ticket_graph table |

---

## 4. E2E-API — End-to-End Tests

### TC-E2E-001: Full sync tool lifecycle via MCP protocol

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Steps | 1. MCP client calls jira_project_sync 2. Poll jira_sync_status until complete |
| Expected | Sync starts, progresses, completes — status tool reflects each phase |

### TC-E2E-002: Graph tool with real data after sync

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Precondition | Sync completed for project |
| Steps | 1. Call jira_ticket_graph 2. Verify nodes match synced tickets |
| Expected | Graph contains all synced tickets as nodes with correct relationships |

### TC-E2E-003: Error propagation — sync tool with invalid key

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Steps | 1. Call jira_project_sync with invalid key via MCP |
| Expected | MCP error response with clear message |

### TC-E2E-004: Concurrent tool calls — status while sync running

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Steps | 1. Start sync 2. Immediately call status 3. Call status again later |
| Expected | First status: syncing with progress. Second: completed |

### TC-E2E-005: Graph subgraph query with depth and filters

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Steps | 1. Call graph with issueKey, depth=2, relationshipTypes=["parent"] |
| Expected | Only parent/child edges within 2 hops of center issue |

---

## 5. SIT — System Integration Tests (Manual)

### TC-SIT-001: AI agent (Claude/Kiro) discovers and calls sync tool

| Attribute | Value |
|-----------|-------|
| Level | SIT |
| Steps | 1. Ask AI to sync project 2. Verify tool called 3. Check approval flow |
| Expected | AI finds tool, requests approval, sync executes |

### TC-SIT-002: AI agent queries graph and interprets results

| Attribute | Value |
|-----------|-------|
| Level | SIT |
| Steps | 1. Ask AI about ticket relationships 2. Verify graph tool called |
| Expected | AI calls jira_ticket_graph, interprets nodes/edges correctly |
