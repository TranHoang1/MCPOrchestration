# Business Requirements Document (BRD)

## MCPOrchestration — MTO-20: MCP Tool Integration – Sync & Graph Tools

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-20 |
| Title | MCP Tool Integration – Sync & Graph Tools |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | BA Agent | Initial BRD |

---

## 1. Introduction

### 1.1 Scope

Expose MCP tools for the Jira Project Sync Service, enabling AI agents to trigger sync operations, check sync status, and query ticket graph data. Three new MCP tools will be registered:
- **jira_project_sync** — Trigger sync for a project (write operation, requires approval)
- **jira_sync_status** — Check sync progress (read-only, auto-approve)
- **jira_ticket_graph** — Query graph data (read-only, auto-approve)

### 1.2 Out of Scope

- Sync logic implementation (MTO-17 ProjectScanner)
- Deep content crawling (MTO-18 TicketCrawler)
- Graph data population (MTO-18 GraphBuilder)
- Web Dashboard UI (MTO-21)
- 3D Visualization (MTO-22)

### 1.3 Preliminary Requirements

| # | Prerequisite | Source |
|---|-------------|--------|
| 1 | Database Schema (sync state, ticket graph) | MTO-15 |
| 2 | ProjectScanner (scan logic) | MTO-17 |
| 3 | TicketCrawler (graph data) | MTO-18 |
| 4 | MCP Orchestrator tool registry | Existing |

---

## 2. Business Requirements

### 2.1 User Stories

| # | Story | Priority |
|---|-------|----------|
| 1 | As an AI agent, I want to trigger a project sync so that KB data stays current | MUST HAVE |
| 2 | As an AI agent, I want to check sync status so that I know when data is ready | MUST HAVE |
| 3 | As an AI agent, I want to query ticket graph so that I can analyze dependencies | MUST HAVE |
| 4 | As a system, I want read-only tools auto-approved so that agents can query freely | SHOULD HAVE |
| 5 | As a system, I want write tools to require approval so that accidental syncs are prevented | SHOULD HAVE |

---

### 2.2 Story Details

#### STORY 1: jira_project_sync Tool

**Description:** Trigger a sync job for a Jira project. Async — returns immediately, sync runs in background.

**Input Schema:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| projectKey | String | Yes | — | Jira project key to sync |
| fullSync | Boolean | No | false | Full re-sync vs incremental |

**Output Schema:**

```json
{
  "status": "started",
  "projectKey": "MTO",
  "estimatedIssues": 67
}
```

**Error Cases:**

| Error | Condition |
|-------|-----------|
| "Sync already running for {key}" | Another sync is active |
| "Invalid project key: {key}" | Key doesn't match pattern |

**Approval:** Requires user approval (write operation)

---

#### STORY 2: jira_sync_status Tool

**Description:** Check current sync progress for a project.

**Input Schema:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| projectKey | String | Yes | — | Project to check |

**Output Schema:**

```json
{
  "projectKey": "MTO",
  "status": "syncing",
  "progress": 67.5,
  "syncedIssues": 45,
  "totalIssues": 67,
  "lastSyncTime": "2026-05-07T10:00:00Z",
  "errors": []
}
```

**Approval:** Auto-approve (read-only)

---

#### STORY 3: jira_ticket_graph Tool

**Description:** Query ticket relationship graph data.

**Input Schema:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| projectKey | String | Yes | — | Project to query |
| issueKey | String | No | — | Center issue for subgraph |
| depth | Integer | No | 2 | Hops from center (1-5) |
| relationshipTypes | String[] | No | all | Filter by relationship type |

**Output Schema:**

```json
{
  "nodes": [
    { "id": "MTO-14", "label": "Epic", "type": "Epic", "status": "To Do" }
  ],
  "edges": [
    { "source": "MTO-14", "target": "MTO-15", "type": "parent" }
  ],
  "totalNodes": 22,
  "totalEdges": 35
}
```

**Approval:** Auto-approve (read-only)

---

## 3. Dependencies

| Dependency | Type | Related Ticket |
|------------|------|----------------|
| Database Schema | System | MTO-15 |
| ProjectScanner | System | MTO-17 |
| TicketCrawler (graph data) | System | MTO-18 |
| MCP Tool Registry | Existing | — |

---

## 4. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| Performance | jira_sync_status response < 100ms |
| Performance | jira_ticket_graph response < 500ms (500 nodes) |
| Security | jira_project_sync requires approval |
| Availability | Tools available when MCP server is running |

---

## 5. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Business Flow | [business-flow.png](diagrams/business-flow.png) | [business-flow.drawio](diagrams/business-flow.drawio) |
| 2 | Use Case | [use-case.png](diagrams/use-case.png) | [use-case.drawio](diagrams/use-case.drawio) |
