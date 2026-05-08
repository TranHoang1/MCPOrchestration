# Functional Specification Document (FSD)

## MCPOrchestration — MTO-22: 3D Graph Visualization – Force-Directed Graph Views

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-22 |
| Title | 3D Graph Visualization – Force-Directed Graph Views |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-22.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | BA Agent | Initial FSD |
| 1.0 | 2026-05-08 | TA Agent | Technical enrichment — API contracts, graph algorithms |

---

## 1. Introduction

### 1.1 Purpose

This FSD specifies the functional behavior of the 3D Graph Visualization feature — REST API endpoints serving graph data and a WebGL-based frontend viewer using 3d-force-graph.js.

### 1.2 Scope

- REST API: GET /sync/graph/{projectKey} and GET /sync/graph/{projectKey}/{issueKey}
- Graph data transformation from DB to visualization format
- 7 view modes with different layouts and color schemes
- Frontend: single HTML page with 3d-force-graph.js (CDN)
- Interactive features: zoom/pan/rotate, click details, hover highlight, filter, search

---

## 2. System Overview

### 2.1 Architecture

```
┌─────────────────────────────────────────────────────┐
│                 MCPOrchestration App                  │
│                                                      │
│  ┌───────────────────────────────────────────────┐  │
│  │            graph/ package (NEW)                │  │
│  │                                                │  │
│  │  ┌────────────┐  ┌──────────────┐            │  │
│  │  │ GraphRoutes│  │ GraphService │            │  │
│  │  │ (REST API) │  │ (Transform)  │            │  │
│  │  └────────────┘  └──────────────┘            │  │
│  │                         ↕                     │  │
│  │  ┌──────────────────────────────────────┐    │  │
│  │  │       GraphDataRepository            │    │  │
│  │  └──────────────────────────────────────┘    │  │
│  └───────────────────────────────────────────────┘  │
│        ↕                                             │
│  ┌──────────────┐                                   │
│  │  PostgreSQL   │  (jira_ticket_cache +             │
│  │              │   jira_ticket_graph)               │
│  └──────────────┘                                   │
└─────────────────────────────────────────────────────┘
         ↑
    ┌──────────┐
    │ Browser  │  (graph-viewer.html + 3d-force-graph.js)
    └──────────┘
```

---

## 3. Functional Requirements

### 3.1 Feature: GET /sync/graph/{projectKey} — Full Project Graph

**Use Case ID:** UC-01

**API Contract:**

| Attribute | Value |
|-----------|-------|
| Method | GET |
| Path | /sync/graph/{projectKey} |
| Query Params | view (enum, default: hierarchy) |
| Response | 200 OK — JSON |

**Query Parameters:**

| Param | Type | Required | Default | Values |
|-------|------|----------|---------|--------|
| view | String | No | hierarchy | hierarchy, functional, business, complexity, timeline, dependency, team |

**Response Schema:**

```json
{
  "nodes": [
    {
      "id": "MTO-14",
      "label": "Jira Project Sync Service",
      "type": "Epic",
      "status": "To Do",
      "priority": "High",
      "assignee": "Duc Nguyen",
      "storyPoints": 13,
      "group": "infrastructure",
      "color": "#4CAF50",
      "size": 8
    }
  ],
  "edges": [
    {
      "source": "MTO-14",
      "target": "MTO-15",
      "type": "parent",
      "label": "has child",
      "color": "#999",
      "width": 2
    }
  ],
  "metadata": {
    "projectKey": "MTO",
    "view": "hierarchy",
    "totalNodes": 22,
    "totalEdges": 35,
    "lastSynced": "2026-05-07T10:00:00Z"
  }
}
```

**Business Rules:**

| Rule ID | Rule |
|---------|------|
| BR-01 | Node color determined by active view mode |
| BR-02 | Node size: default 5, Epics 8, scaled by story points in complexity view |
| BR-03 | Edge width: parent=2, blocks=3, relates-to=1 |
| BR-04 | Edge color: parent=#999, blocks=#f44336, relates-to=#2196f3 |

---

### 3.2 Feature: GET /sync/graph/{projectKey}/{issueKey} — Subgraph

**Use Case ID:** UC-02

**API Contract:**

| Attribute | Value |
|-----------|-------|
| Method | GET |
| Path | /sync/graph/{projectKey}/{issueKey} |
| Query Params | depth (int, default: 2), view (enum) |
| Response | 200 OK or 404 |

**Query Parameters:**

| Param | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| depth | Int | No | 2 | Hops from center issue |
| view | String | No | hierarchy | View mode |

**Business Rules:**

| Rule ID | Rule |
|---------|------|
| BR-05 | BFS traversal from center issue up to depth hops |
| BR-06 | Center node has size * 1.5 and distinct border |
| BR-07 | Include all edges between included nodes |

---

### 3.3 Feature: View Modes

#### 3.3.1 Color Schemes

| View | Color By | Palette |
|------|----------|---------|
| hierarchy | Issue Type | Epic=#9C27B0, Story=#4CAF50, Task=#2196F3, Bug=#F44336 |
| functional | Component/Label | Auto-generated HSL (hue = index * 137.5°) |
| business | Epic parent | Auto-generated HSL |
| complexity | Dependency depth | #4CAF50(0) → #FFEB3B(1-2) → #FF9800(3-4) → #F44336(5+) |
| timeline | Status | ToDo=#9E9E9E, InProgress=#2196F3, Done=#4CAF50 |
| dependency | Critical path | Normal=#9E9E9E, Critical=#F44336, Blocked=#FF9800 |
| team | Assignee | Auto-generated HSL |

#### 3.3.2 Node Sizing

| View | Size Logic |
|------|-----------|
| hierarchy | Type-based: Epic=8, Story=5, Task=4, Bug=5 |
| complexity | storyPoints * 1.5 (min 3, max 15) |
| Others | Default 5 |

#### 3.3.3 Grouping/Clustering

| View | Group By |
|------|----------|
| functional | First label or component |
| business | Epic parent key |
| team | Assignee name |
| Others | No explicit grouping |

---

### 3.4 Feature: Frontend Interactions

#### 3.4.1 Click Node → Details Panel

**Display fields:** id, label, type, status, priority, assignee, storyPoints
**Link:** Open in Jira (new tab)
**Dismiss:** Click elsewhere or Escape key

#### 3.4.2 Hover → Highlight Connected

**Behavior:** Dim all nodes except hovered node and its 1-hop neighbors
**Opacity:** Non-connected nodes at 0.2 opacity

#### 3.4.3 Filter

**Filter fields:** type, status, assignee, label
**Logic:** AND between different fields, OR within same field
**Effect:** Hide non-matching nodes and their edges

#### 3.4.4 Search

**Input:** Ticket key or partial summary (case-insensitive)
**Effect:** Highlight matching node, camera animates to center on it
**Multiple matches:** Show dropdown list for selection

---

## 4. Data Model

### 4.1 Source Tables

- `jira_ticket_cache` — node data (issue_key, summary, status, type, priority, assignee, metadata_json)
- `jira_ticket_graph` — edge data (source_key, target_key, relationship_type)

### 4.2 Graph Transformation

```kotlin
fun buildGraphResponse(tickets: List<TicketCache>, edges: List<TicketGraphEdge>, view: ViewMode): GraphResponse {
    val nodes = tickets.map { ticket ->
        GraphNode(
            id = ticket.issueKey,
            label = ticket.summary,
            type = ticket.issueType,
            status = ticket.status,
            priority = ticket.priority,
            assignee = ticket.assignee,
            storyPoints = ticket.metadata?.storyPoints ?: 0,
            group = determineGroup(ticket, view),
            color = determineColor(ticket, view),
            size = determineSize(ticket, view)
        )
    }
    val graphEdges = edges.map { edge ->
        GraphEdge(
            source = edge.sourceKey,
            target = edge.targetKey,
            type = edge.relationshipType,
            label = edge.relationshipType.displayLabel,
            color = edgeColor(edge.relationshipType),
            width = edgeWidth(edge.relationshipType)
        )
    }
    return GraphResponse(nodes, graphEdges, metadata)
}
```

---

## 5. Non-Functional Requirements

| Category | Target |
|----------|--------|
| API response (500 nodes) | < 500ms |
| Frontend render (500 nodes) | > 30 FPS |
| Layout stabilization | < 3 seconds |
| Browser memory | < 200MB for 500 nodes |
| Min viewport | 768px width |

---

## 6. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | System Context | [system-context.png](diagrams/system-context.png) | [system-context.drawio](diagrams/system-context.drawio) |
| 2 | Sequence - Load Graph | [sequence-load-graph.png](diagrams/sequence-load-graph.png) | [sequence-load-graph.drawio](diagrams/sequence-load-graph.drawio) |
| 3 | State - View Modes | [state-views.png](diagrams/state-views.png) | [state-views.drawio](diagrams/state-views.drawio) |
