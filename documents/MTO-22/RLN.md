# Release Notes (RLN)

## MCPOrchestration — MTO-22: 3D Graph Visualization – Force-Directed Graph Views

---

## Release Information

| Field | Value |
|-------|-------|
| Release Version | 1.7.0 |
| Release Date | 2025-07-18 |
| Jira Ticket | MTO-22 |
| Environment | DEV / SIT / UAT / PROD |
| Author | DevOps Agent |
| Status | Final |
| Related Epic | MTO-14 — Jira Project Sync Service |

---

## 1. Release Summary

This release introduces the **3D Graph Visualization** — an interactive WebGL-based viewer for exploring Jira ticket relationships as a force-directed 3D graph. It provides a REST API serving graph data with 7 configurable view modes, and a browser-based viewer using 3d-force-graph.js for immersive exploration of project structure.

**Release Type:** Feature addition (new API endpoints + static frontend)
**Risk Level:** Low (read-only API, CDN-hosted frontend library)
**Backward Compatible:** Yes — no existing endpoints or tools modified

---

## 2. What's New

### 2.1 New Features

| # | Feature | Description |
|---|---------|-------------|
| 1 | **Full Project Graph** | `GET /sync/graph/{projectKey}` returns all nodes + edges |
| 2 | **Subgraph (BFS)** | `GET /sync/graph/{projectKey}/{issueKey}` with depth-limited traversal |
| 3 | **7 View Modes** | Hierarchy, Dependency, Team, Complexity, Functional, Business, Timeline |
| 4 | **3D Force-Directed Layout** | Interactive WebGL visualization with Three.js |
| 5 | **Node Interaction** | Click for details, hover for connected highlights |
| 6 | **Search & Filter** | Search by ticket key, filter by type/status/priority |
| 7 | **Server-Side View Transform** | Color, size, and grouping computed on server |

### 2.2 View Modes

| View | Color By | Size By | Group By |
|------|----------|---------|----------|
| Hierarchy | Issue type | Type hierarchy (Epic > Story > Task) | Issue type |
| Dependency | Dependency depth | Connection count | Cluster |
| Team | Assignee | Story points | Assignee |
| Complexity | Priority | Story points | Priority |
| Functional | Component/label | Connection count | Label |
| Business | Epic | Story points | Epic |
| Timeline | Age (gradient) | Days since update | Sprint/quarter |

### 2.3 Technical Highlights

- **Recursive CTE** — BFS traversal via PostgreSQL recursive query for subgraph extraction
- **Strategy Pattern** — 7 ViewModeStrategy implementations for clean view separation
- **CDN-Hosted** — 3d-force-graph.js loaded from unpkg.com, zero npm build step
- **Performance-First** — 1000-node cap, warmup ticks for pre-computed layout
- **Responsive** — CSS Grid layout adapts to screen size

---

## 3. New Components

### 3.1 Package: `com.orchestrator.mcp.graph`

| Class | Type | Responsibility |
|-------|------|----------------|
| `GraphRoutes` | Ktor routing | REST API route handlers |
| `GraphService` | Class | View transformation + business logic |
| `GraphDataRepository` | Class | DB queries (full graph + BFS) |
| `ViewModeStrategy` | Interface | Color/size/group computation |
| `HierarchyViewStrategy` | Class | Type-based coloring |
| `DependencyViewStrategy` | Class | Depth-based coloring |
| `TeamViewStrategy` | Class | Assignee-based coloring |
| `ComplexityViewStrategy` | Class | Priority-based sizing |
| `FunctionalViewStrategy` | Class | Label-based grouping |
| `BusinessViewStrategy` | Class | Epic-based grouping |
| `TimelineViewStrategy` | Class | Age-based gradient |
| `GraphModule` | Koin module | DI bindings |

### 3.2 Frontend

| File | Location | Description |
|------|----------|-------------|
| `graph-viewer.html` | `resources/static/` | Single-file 3D viewer (HTML + CSS + JS) |

---

## 4. API Endpoints

| Method | Path | Description | Parameters |
|--------|------|-------------|------------|
| GET | `/sync/graph/{projectKey}` | Full project graph | `?view=hierarchy` |
| GET | `/sync/graph/{projectKey}/{issueKey}` | Subgraph from center node | `?depth=2&view=dependency` |

### Response Schema

```json
{
  "nodes": [
    {
      "id": "MTO-15",
      "label": "Database Schema...",
      "type": "Story",
      "status": "Done",
      "priority": "High",
      "assignee": "dev-agent",
      "group": "Story",
      "color": "#4CAF50",
      "size": 5
    }
  ],
  "edges": [
    {
      "source": "MTO-15",
      "target": "MTO-17",
      "label": "blocks",
      "color": "#999",
      "width": 1
    }
  ],
  "metadata": {
    "totalNodes": 8,
    "totalEdges": 12,
    "projectKey": "MTO",
    "centerIssue": null,
    "depth": null,
    "view": "hierarchy"
  }
}
```

---

## 5. Configuration Changes

```yaml
graph:
  enabled: true
  maxNodes: 1000
  defaultDepth: 2
  maxDepth: 5
```

---

## 6. Dependencies

### 6.1 No New Server-Side Dependencies

All existing Kotlin/Ktor libraries.

### 6.2 Frontend CDN Dependencies

| Library | Version | CDN URL |
|---------|---------|---------|
| 3d-force-graph.js | 1.73.x | `https://unpkg.com/3d-force-graph@1` |
| Three.js | r160+ | Bundled with 3d-force-graph |

### 6.3 Internal Dependencies

| Module | Required | Used For |
|--------|----------|----------|
| MTO-15 (DB Schema) | ✅ | jira_ticket_cache table |
| MTO-18 (Ticket Crawler) | ✅ | jira_ticket_graph table (populated) |

---

## 7. Testing Summary

| Level | Tests | Passed | Failed | Coverage |
|-------|-------|--------|--------|----------|
| Unit Tests | 14 | 14 | 0 | ViewStrategies, GraphService, Repository |
| Integration Tests | 5 | 5 | 0 | Ktor TestHost (API endpoints) |
| **Total** | **19** | **19** | **0** | — |

---

## 8. Known Issues & Limitations

| # | Issue | Severity | Workaround |
|---|-------|----------|------------|
| 1 | Requires internet for CDN library | Medium | Self-host 3d-force-graph.js if offline needed |
| 2 | 1000-node cap may truncate large projects | Low | Use subgraph with issueKey + depth |
| 3 | No edge bundling for dense graphs | Low | Future enhancement |
| 4 | No authentication | Medium | Internal network only |
| 5 | Labels hidden at distance (LOD) | Low | Zoom in to see labels |

---

## 9. Breaking Changes

No breaking changes. Existing endpoints and MCP tools unaffected.

---

## 10. Rollback Instructions

1. Stop application
2. Restore previous JAR
3. Restart application
4. `/sync/graph/*` endpoints will return 404, graph viewer unavailable

**Data loss risk:** None — graph API is read-only.

---

## 11. Epic MTO-14 — Complete Roadmap

| Story | Version | Status | Description |
|-------|---------|--------|-------------|
| MTO-15 | 1.1.0 | ✅ Deployed | Database Schema & Sync State |
| MTO-16 | 1.1.0 | ✅ Deployed | Jira REST Client |
| MTO-17 | 1.2.0 | ✅ Deployed | Project Scanner |
| MTO-18 | 1.3.0 | ✅ Deployed | Ticket Crawler |
| MTO-19 | 1.4.0 | ✅ Deployed | Attachment Processor |
| MTO-20 | 1.5.0 | ✅ Deployed | MCP Tool Integration |
| MTO-21 | 1.6.0 | ✅ Deployed | Web Dashboard |
| **MTO-22** | **1.7.0** | ✅ **This release** | **3D Graph Visualization** |

**Epic MTO-14 is now COMPLETE.** All 8 stories delivered.

---

## 12. Contact & Support

| Role | Contact | Responsibility |
|------|---------|----------------|
| Developer | DEV Agent | Implementation questions |
| Architect | SA Agent | Design decisions |
| QA | QA Agent | Test failures |
| DevOps | DevOps Agent | Deployment issues |
