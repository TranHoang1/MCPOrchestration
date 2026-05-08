# Functional Specification Document (FSD)

## MCPOrchestration — MTO-20: MCP Tool Integration – Sync & Graph Tools

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-20 |
| Title | MCP Tool Integration – Sync & Graph Tools |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-20.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | BA Agent | Initial FSD |
| 1.0 | 2026-05-08 | TA Agent | Technical enrichment — tool schemas, integration |

---

## 1. Introduction

### 1.1 Purpose

This FSD specifies the functional behavior of three new MCP tools that expose Jira sync and graph capabilities to AI agents via the MCP protocol.

### 1.2 Scope

- Tool registration in MCP Orchestrator
- Input validation and schema definition
- Integration with ProjectScanner and GraphDataRepository
- Auto-approve configuration for read-only tools

---

## 2. System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    MCPOrchestration App                       │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │           MCP Protocol Layer (existing)              │    │
│  │  McpToolRegistrar → registers tools                  │    │
│  └─────────────────────────────────────────────────────┘    │
│         ↕                                                    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │         sync-tools/ package (NEW)                    │    │
│  │                                                      │    │
│  │  ┌────────────────┐  ┌──────────────────┐          │    │
│  │  │SyncToolHandlers│  │GraphToolHandler  │          │    │
│  │  └────────────────┘  └──────────────────┘          │    │
│  └─────────────────────────────────────────────────────┘    │
│         ↕                         ↕                          │
│  ┌──────────────┐         ┌──────────────────┐             │
│  │ProjectScanner│         │GraphDataRepository│             │
│  │  (MTO-17)    │         │   (MTO-18)       │             │
│  └──────────────┘         └──────────────────┘             │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Functional Requirements

### 3.1 Tool: jira_project_sync

**Use Case ID:** UC-01

**Tool Schema:**

| Attribute | Value |
|-----------|-------|
| Name | jira_project_sync |
| Description | Trigger Jira project sync. Starts background sync job. |
| Auto-approve | No (write operation) |

**Input JSON Schema:**

```json
{
  "type": "object",
  "required": ["projectKey"],
  "properties": {
    "projectKey": {
      "type": "string",
      "description": "Jira project key (e.g., 'MTO')",
      "pattern": "^[A-Z][A-Z0-9_]+$"
    },
    "fullSync": {
      "type": "boolean",
      "default": false,
      "description": "Full re-sync (true) or incremental (false)"
    }
  }
}
```

**Output:**

```json
{ "status": "started", "projectKey": "MTO", "estimatedIssues": 67 }
```

**Error Responses:**

| Condition | Error |
|-----------|-------|
| Sync already running | `{ "error": "Sync already running for MTO" }` |
| Invalid project key | `{ "error": "Invalid project key: abc" }` |

**Business Rules:**

| Rule ID | Rule |
|---------|------|
| BR-01 | Async execution — return immediately, sync runs in background |
| BR-02 | Only one sync per project at a time |
| BR-03 | Requires user approval before execution |

---

### 3.2 Tool: jira_sync_status

**Use Case ID:** UC-02

**Tool Schema:**

| Attribute | Value |
|-----------|-------|
| Name | jira_sync_status |
| Description | Check sync progress for a Jira project |
| Auto-approve | Yes (read-only) |

**Input JSON Schema:**

```json
{
  "type": "object",
  "required": ["projectKey"],
  "properties": {
    "projectKey": {
      "type": "string",
      "description": "Jira project key to check status"
    }
  }
}
```

**Output:**

```json
{
  "projectKey": "MTO",
  "status": "syncing",
  "progress": 67.5,
  "syncedIssues": 45,
  "totalIssues": 67,
  "lastSyncTime": "2026-05-07T10:00:00Z",
  "phases": {
    "scan": { "status": "completed", "progress": 100 },
    "crawl": { "status": "syncing", "progress": 45 },
    "attachments": { "status": "idle", "progress": 0 }
  },
  "errors": []
}
```

**Business Rules:**

| Rule ID | Rule |
|---------|------|
| BR-04 | Return current state from jira_sync_state table |
| BR-05 | If no state exists, return status="never_synced" |
| BR-06 | Include phase breakdown (scan, crawl, attachments) |

---

### 3.3 Tool: jira_ticket_graph

**Use Case ID:** UC-03

**Tool Schema:**

| Attribute | Value |
|-----------|-------|
| Name | jira_ticket_graph |
| Description | Query Jira ticket relationship graph |
| Auto-approve | Yes (read-only) |

**Input JSON Schema:**

```json
{
  "type": "object",
  "required": ["projectKey"],
  "properties": {
    "projectKey": {
      "type": "string",
      "description": "Jira project key"
    },
    "issueKey": {
      "type": "string",
      "description": "Center issue for subgraph (optional)"
    },
    "depth": {
      "type": "integer",
      "default": 2,
      "minimum": 1,
      "maximum": 5,
      "description": "Hops from center issue"
    },
    "relationshipTypes": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Filter by relationship types (e.g., ['blocks', 'parent'])"
    }
  }
}
```

**Output:**

```json
{
  "nodes": [
    { "id": "MTO-14", "label": "Jira Project Sync Service", "type": "Epic", "status": "To Do", "assignee": "Duc Nguyen" }
  ],
  "edges": [
    { "source": "MTO-14", "target": "MTO-15", "type": "parent", "label": "has child" }
  ],
  "metadata": {
    "totalNodes": 22,
    "totalEdges": 35,
    "projectKey": "MTO",
    "centerIssue": null,
    "depth": 2
  }
}
```

**Business Rules:**

| Rule ID | Rule |
|---------|------|
| BR-07 | If issueKey provided → BFS subgraph from that issue |
| BR-08 | If only projectKey → full project graph |
| BR-09 | Depth clamped to 1-5 range |
| BR-10 | relationshipTypes filter applied after graph retrieval |
| BR-11 | Max 1000 nodes in response (truncate with warning) |

---

## 4. Integration Specifications

### 4.1 MCP Tool Registration

Tools are registered via the existing `McpToolRegistrar` mechanism:

```kotlin
// In McpServerFactory or dedicated SyncToolRegistrar
server.addTool(
    name = "jira_project_sync",
    description = "Trigger Jira project sync...",
    inputSchema = syncInputSchema,
    handler = { args -> syncToolHandler.handleSync(args) }
)
```

### 4.2 Auto-Approve Configuration

```yaml
orchestrator:
  tools:
    autoApprove:
      - jira_sync_status
      - jira_ticket_graph
    # jira_project_sync NOT in auto-approve (requires approval)
```

---

## 5. Non-Functional Requirements

| Category | Target |
|----------|--------|
| jira_sync_status response | < 100ms |
| jira_ticket_graph response (500 nodes) | < 500ms |
| jira_project_sync response | < 200ms (async start) |
| Tool discovery | Tools appear in find_tools results |

---

## 6. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | System Context | [system-context.png](diagrams/system-context.png) | [system-context.drawio](diagrams/system-context.drawio) |
| 2 | Sequence - Sync Tool | [sequence-sync-tool.png](diagrams/sequence-sync-tool.png) | [sequence-sync-tool.drawio](diagrams/sequence-sync-tool.drawio) |
