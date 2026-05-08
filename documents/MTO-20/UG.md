# User Guide (UG)

## Jira Project Sync Service — MTO-20: MCP Tool Integration — Sync & Graph Tools

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-20 |
| Title | MCP Tool Integration — Sync & Graph Tools |
| Author | DEV Agent |
| Reviewer | BA Agent |
| Version | 1.0 |
| Date | 2025-07-15 |
| Status | Final |
| Related BRD | BRD-v1-MTO-20.docx |
| Related FSD | FSD-v1-MTO-20.docx |
| Related TDD | TDD-v1-MTO-20.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-15 | DEV Agent | Initial document |

---

## 1. Introduction

### 1.1 Purpose

This guide documents three MCP tools that expose Jira sync and graph capabilities to AI agents via the MCP protocol. These tools allow AI agents to trigger project synchronization, check sync status, and query the ticket relationship graph.

### 1.2 Audience

| Audience | What They Need |
|----------|---------------|
| AI Agent | Tool schemas, parameters, expected responses, and error handling |
| Developer | How to register tools, configure auto-approve, and extend |
| System Operator | How to verify tools are registered and accessible |

### 1.3 Prerequisites

| Prerequisite | Version | Required |
|-------------|---------|----------|
| MCP Orchestrator running | — | Yes |
| MTO-15 (DB Schema) | Initialized | Yes |
| MTO-17 (Project Scanner) | Available | Yes (for sync tool) |
| MTO-18 (Ticket Crawler) | Available | Yes (for graph tool) |

---

## 2. Getting Started

### 2.1 Tool Discovery

AI agents discover these tools via the MCP `tools/list` method or `find_tools`:

```json
// find_tools query
{ "query": "jira sync project" }

// Returns:
[
  { "name": "jira_project_sync", "description": "Trigger Jira project sync..." },
  { "name": "jira_sync_status", "description": "Check sync progress..." },
  { "name": "jira_ticket_graph", "description": "Query ticket relationship graph..." }
]
```

### 2.2 Auto-Approve Configuration

| Tool | Auto-Approve | Reason |
|------|-------------|--------|
| `jira_sync_status` | ✅ Yes | Read-only, no side effects |
| `jira_ticket_graph` | ✅ Yes | Read-only, no side effects |
| `jira_project_sync` | ❌ No | Write operation, triggers background job |

---

## 3. Tool Reference

### 3.1 jira_project_sync

**Purpose:** Trigger a Jira project synchronization job (scan + crawl + attachments).

**Input Schema:**

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

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| projectKey | string | Yes | — | Jira project key (uppercase, e.g., "MTO") |
| fullSync | boolean | No | false | `true` = full re-scan, `false` = incremental |

**Example Request:**

```json
{
  "projectKey": "MTO",
  "fullSync": false
}
```

**Success Response:**

```json
{
  "status": "started",
  "projectKey": "MTO",
  "estimatedIssues": 67
}
```

**Error Responses:**

| Condition | Response |
|-----------|----------|
| Sync already running | `{ "error": "Sync already running for MTO" }` |
| Invalid project key | `{ "error": "Invalid project key: abc" }` |
| Project not found | `{ "error": "Project not found in Jira: XYZ" }` |

**Behavior:**
- Returns immediately (async) — sync runs in background
- Only one sync per project at a time
- Requires user approval before execution (not auto-approved)

---

### 3.2 jira_sync_status

**Purpose:** Check synchronization progress for a Jira project.

**Input Schema:**

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

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| projectKey | string | Yes | Jira project key |

**Example Request:**

```json
{
  "projectKey": "MTO"
}
```

**Success Response:**

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

**Status Values:**

| Status | Meaning |
|--------|---------|
| `never_synced` | No sync has ever been run for this project |
| `idle` | No sync in progress |
| `syncing` | Sync actively running |
| `completed` | Last sync finished successfully |
| `error` | Last sync failed |
| `stopped` | Sync was manually stopped |

**Behavior:**
- Auto-approved (no user confirmation needed)
- Returns current state from database
- Response time < 100ms

---

### 3.3 jira_ticket_graph

**Purpose:** Query the Jira ticket relationship graph for dependency analysis and visualization.

**Input Schema:**

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
      "description": "Filter by relationship types"
    }
  }
}
```

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| projectKey | string | Yes | — | Jira project key |
| issueKey | string | No | — | Center issue for subgraph traversal |
| depth | integer | No | 2 | BFS depth (1-5 hops) |
| relationshipTypes | string[] | No | all | Filter: "blocks", "parent", "relates-to", "epic" |

**Example 1: Full Project Graph**

```json
{
  "projectKey": "MTO"
}
```

**Example 2: Subgraph from Specific Issue**

```json
{
  "projectKey": "MTO",
  "issueKey": "MTO-14",
  "depth": 3,
  "relationshipTypes": ["parent", "blocks"]
}
```

**Success Response:**

```json
{
  "nodes": [
    {
      "id": "MTO-14",
      "label": "Jira Project Sync Service",
      "type": "Epic",
      "status": "To Do",
      "priority": "High",
      "assignee": "Duc Nguyen"
    },
    {
      "id": "MTO-15",
      "label": "Database Schema & Sync State Management",
      "type": "Story",
      "status": "Done",
      "priority": "High",
      "assignee": "Duc Nguyen"
    }
  ],
  "edges": [
    {
      "source": "MTO-14",
      "target": "MTO-15",
      "type": "parent",
      "label": "has child"
    }
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

**Behavior:**
- Auto-approved (read-only)
- If `issueKey` provided → BFS subgraph from that issue
- If only `projectKey` → full project graph
- Max 1000 nodes in response (truncated with warning)
- Response time < 500ms for 500 nodes

**Relationship Types:**

| Type | Description | Example |
|------|-------------|---------|
| `parent` | Parent/child hierarchy | Epic → Story |
| `blocks` | Blocking dependency | MTO-15 blocks MTO-17 |
| `relates-to` | General relationship | Related tickets |
| `epic` | Epic link | Story belongs to Epic |
| `subtask` | Subtask relationship | Task → Subtask |

---

## 4. Usage Patterns

### 4.1 Sync a Project (AI Agent Workflow)

```
1. Check current status:
   jira_sync_status { "projectKey": "MTO" }
   
2. If status is "idle" or "completed", trigger sync:
   jira_project_sync { "projectKey": "MTO" }
   
3. Poll for completion:
   jira_sync_status { "projectKey": "MTO" }
   // Repeat until status != "syncing"
```

### 4.2 Analyze Dependencies

```
1. Get full project graph:
   jira_ticket_graph { "projectKey": "MTO" }
   
2. Find blockers for a specific ticket:
   jira_ticket_graph { 
     "projectKey": "MTO", 
     "issueKey": "MTO-20", 
     "depth": 1, 
     "relationshipTypes": ["blocks"] 
   }
```

### 4.3 Impact Analysis

```
1. Get subgraph around a ticket:
   jira_ticket_graph { 
     "projectKey": "MTO", 
     "issueKey": "MTO-15", 
     "depth": 3 
   }
   
2. Analyze which tickets depend on MTO-15
   // Look at edges where MTO-15 is the target with type "blocks"
```

---

## 5. Troubleshooting

### 5.1 Common Issues

| # | Symptom | Cause | Solution |
|---|---------|-------|----------|
| 1 | Tool not found in `find_tools` | Tools not registered | Verify app is running, check startup logs |
| 2 | "Sync already running" error | Previous sync not finished | Wait or stop the running sync |
| 3 | Empty graph response | No tickets synced yet | Run `jira_project_sync` first |
| 4 | Graph truncated (1000 nodes) | Large project | Use `issueKey` + `depth` to get subgraph |
| 5 | "Project not found" | Invalid project key | Verify project key exists in Jira |
| 6 | Stale graph data | Sync hasn't run recently | Trigger new sync |

### 5.2 FAQ

**Q: How often should I sync?**
A: For active projects, every 5-15 minutes (incremental). The incremental sync only fetches changes.

**Q: Can I sync multiple projects?**
A: Yes. Call `jira_project_sync` for each project. They run independently.

**Q: Why does `jira_project_sync` require approval?**
A: It triggers a background job that makes many API calls to Jira. The approval step prevents accidental or excessive syncing.

**Q: How fresh is the graph data?**
A: As fresh as the last sync. Check `lastSyncTime` in the status response.

---

## 6. Appendix

### 6.1 Related Documents

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-20.docx |
| FSD | FSD-v1-MTO-20.docx |
| TDD | TDD-v1-MTO-20.docx |
| DPG | DPG-v1-MTO-20.docx |
