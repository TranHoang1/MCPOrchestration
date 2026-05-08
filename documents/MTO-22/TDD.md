# Technical Design Document (TDD)

## MCPOrchestration — MTO-22: 3D Graph Visualization – Force-Directed Graph Views

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-22 |
| Title | 3D Graph Visualization – Force-Directed Graph Views |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-22.docx |
| Related FSD | FSD-v1-MTO-22.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | SA Agent | Initial TDD |

---

## 1. Introduction

### 1.1 Purpose

Technical design for the 3D Graph Visualization — backend API serving graph data and frontend WebGL viewer using 3d-force-graph.js.

### 1.2 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Backend | Ktor (Netty) | 3.4.0 |
| Serialization | kotlinx.serialization-json | 1.8.1 |
| Database | PostgreSQL + Exposed | 16+ / 0.61.0 |
| Frontend | 3d-force-graph.js (CDN) | 1.73.x |
| 3D Engine | Three.js (dependency of 3d-force-graph) | r160+ |
| Frontend UI | Vanilla JS + CSS | ES2020 |

### 1.3 Design Principles

- **Read-only API** — graph data is pre-computed by TicketCrawler
- **View transformation on server** — color/size/group computed server-side
- **CDN-hosted library** — no npm build step for frontend
- **Performance-first** — optimize for 500-node graphs

---

## 2. System Architecture

### 2.1 Component Diagram

| Component | Responsibility | Technology |
|-----------|---------------|------------|
| GraphRoutes | REST API route handlers | Ktor Routing |
| GraphService | Graph data transformation + view logic | Kotlin |
| GraphDataRepository | Query tickets + edges from DB | Exposed ORM |
| ViewModeStrategy | Color/size/group computation per view | Strategy pattern |
| graph-viewer.html | Frontend 3D visualization | 3d-force-graph.js |

### 2.2 Communication

| From | To | Protocol |
|------|----|----------|
| Browser | GraphRoutes | HTTP GET (JSON) |
| GraphRoutes | GraphService | In-process |
| GraphService | GraphDataRepository | In-process |
| GraphDataRepository | PostgreSQL | JDBC (Exposed) |

---

## 3. API Design

### 3.1 Route Registration

```kotlin
fun Application.configureGraphRoutes() {
    routing {
        route("/sync/graph") {
            get("/{projectKey}") { /* Full graph */ }
            get("/{projectKey}/{issueKey}") { /* Subgraph */ }
        }
        static("/static") {
            resources("static")
        }
    }
}
```

### 3.2 GraphService Implementation

```kotlin
class GraphService(
    private val repository: GraphDataRepository,
    private val viewStrategies: Map<ViewMode, ViewModeStrategy>
) {
    suspend fun getProjectGraph(projectKey: String, view: ViewMode): GraphResponse {
        val tickets = repository.getTicketsByProject(projectKey)
        val edges = repository.getEdgesByProject(projectKey)
        val strategy = viewStrategies[view] ?: viewStrategies[ViewMode.HIERARCHY]!!
        
        val nodes = tickets.map { strategy.toNode(it, tickets) }
        val graphEdges = edges.map { toGraphEdge(it) }
        
        return GraphResponse(nodes, graphEdges, buildMetadata(projectKey, view, nodes, graphEdges))
    }
    
    suspend fun getSubgraph(projectKey: String, issueKey: String, depth: Int, view: ViewMode): GraphResponse {
        val centerTicket = repository.getTicket(issueKey) ?: throw NotFoundException(issueKey)
        val (tickets, edges) = repository.bfsTraversal(issueKey, depth)
        val strategy = viewStrategies[view] ?: viewStrategies[ViewMode.HIERARCHY]!!
        
        val nodes = tickets.map { ticket ->
            val node = strategy.toNode(ticket, tickets)
            if (ticket.issueKey == issueKey) node.copy(size = node.size * 1.5f) else node
        }
        return GraphResponse(nodes, edges.map { toGraphEdge(it) }, buildMetadata(projectKey, view, nodes, edges))
    }
}
```

### 3.3 ViewModeStrategy

```kotlin
interface ViewModeStrategy {
    fun toNode(ticket: TicketCache, allTickets: List<TicketCache>): GraphNode
}

class HierarchyViewStrategy : ViewModeStrategy {
    override fun toNode(ticket: TicketCache, allTickets: List<TicketCache>): GraphNode {
        return GraphNode(
            id = ticket.issueKey,
            label = ticket.summary,
            type = ticket.issueType,
            status = ticket.status,
            priority = ticket.priority,
            assignee = ticket.assignee,
            storyPoints = ticket.storyPoints,
            group = ticket.issueType,
            color = typeColor(ticket.issueType),
            size = typeSize(ticket.issueType)
        )
    }
    
    private fun typeColor(type: String) = when(type) {
        "Epic" -> "#9C27B0"
        "Story" -> "#4CAF50"
        "Task" -> "#2196F3"
        "Bug" -> "#F44336"
        else -> "#9E9E9E"
    }
}
```

---

## 4. Database Design

### 4.1 Tables Used

**jira_ticket_cache** (from MTO-15) — node data
**jira_ticket_graph** (from MTO-18) — edge data

```sql
-- Edge table (from MTO-18)
CREATE TABLE jira_ticket_graph (
    id            SERIAL PRIMARY KEY,
    source_key    VARCHAR(20) NOT NULL,
    target_key    VARCHAR(20) NOT NULL,
    relationship  VARCHAR(50) NOT NULL,
    project_key   VARCHAR(20) NOT NULL,
    UNIQUE(source_key, target_key, relationship)
);

CREATE INDEX idx_graph_project ON jira_ticket_graph(project_key);
CREATE INDEX idx_graph_source ON jira_ticket_graph(source_key);
```

### 4.2 Query Patterns

| Operation | Query | Performance |
|-----------|-------|-------------|
| All nodes for project | `SELECT * FROM jira_ticket_cache WHERE project_key = ?` | < 50ms (500 rows) |
| All edges for project | `SELECT * FROM jira_ticket_graph WHERE project_key = ?` | < 20ms |
| BFS traversal | Recursive CTE with depth limit | < 100ms (depth 2) |

### 4.3 BFS Query (Subgraph)

```sql
WITH RECURSIVE subgraph AS (
    SELECT source_key, target_key, relationship, 1 as depth
    FROM jira_ticket_graph
    WHERE source_key = ? OR target_key = ?
    
    UNION ALL
    
    SELECT g.source_key, g.target_key, g.relationship, s.depth + 1
    FROM jira_ticket_graph g
    JOIN subgraph s ON (g.source_key = s.target_key OR g.target_key = s.source_key)
    WHERE s.depth < ?
)
SELECT DISTINCT * FROM subgraph;
```

---

## 5. Class / Module Design

### 5.1 Package Structure

```
com.orchestrator.mcp/
└── graph/
    ├── GraphRoutes.kt                 # Ktor route definitions
    ├── GraphService.kt                # Business logic
    ├── GraphDataRepository.kt         # Database queries
    ├── views/
    │   ├── ViewModeStrategy.kt        # Interface
    │   ├── HierarchyViewStrategy.kt
    │   ├── DependencyViewStrategy.kt
    │   ├── TeamViewStrategy.kt
    │   ├── ComplexityViewStrategy.kt
    │   ├── FunctionalViewStrategy.kt
    │   ├── BusinessViewStrategy.kt
    │   └── TimelineViewStrategy.kt
    ├── model/
    │   ├── GraphResponse.kt           # Response DTO
    │   ├── GraphNode.kt               # Node DTO
    │   ├── GraphEdge.kt               # Edge DTO
    │   └── ViewMode.kt                # Enum
    └── di/
        └── GraphModule.kt             # Koin DI
```

---

## 6. Frontend Design

### 6.1 graph-viewer.html

Single HTML file at `resources/static/graph-viewer.html`:

```html
<!DOCTYPE html>
<html>
<head>
    <title>Project Graph Viewer</title>
    <script src="https://unpkg.com/3d-force-graph@1"></script>
    <style>/* Embedded CSS */</style>
</head>
<body>
    <div id="controls">
        <select id="project-select"></select>
        <select id="view-select">
            <option value="hierarchy">Hierarchy</option>
            <option value="dependency">Dependency</option>
            <option value="team">Team</option>
            <option value="complexity">Complexity</option>
            <option value="functional">Functional</option>
            <option value="business">Business</option>
            <option value="timeline">Timeline</option>
        </select>
        <input id="search" placeholder="Search ticket...">
        <div id="filters"><!-- Dynamic filter checkboxes --></div>
    </div>
    <div id="graph-container"></div>
    <div id="details-panel" class="hidden"></div>
    <div id="legend"></div>
    <script>/* Embedded JS */</script>
</body>
</html>
```

### 6.2 3d-force-graph Configuration

```javascript
const Graph = ForceGraph3D()(document.getElementById('graph-container'))
    .nodeLabel(node => `${node.id}: ${node.label}`)
    .nodeColor(node => node.color)
    .nodeVal(node => node.size)
    .linkColor(link => link.color)
    .linkWidth(link => link.width)
    .linkLabel(link => link.label)
    .onNodeClick(node => showDetails(node))
    .onNodeHover(node => highlightConnected(node))
    .d3AlphaDecay(0.02)      // Slower decay = better layout
    .d3VelocityDecay(0.3)    // Damping
    .warmupTicks(100)         // Pre-compute layout
    .cooldownTicks(200);      // Max iterations
```

---

## 7. Performance Optimization

| Technique | Implementation |
|-----------|---------------|
| Server-side computation | Color/size/group computed on server, not client |
| Warmup ticks | Pre-compute 100 force iterations before render |
| LOD labels | Show labels only when camera distance < threshold |
| Node sprites | Use simple spheres, not complex geometries |
| Edge bundling | Not implemented (future enhancement) |
| Pagination | Not needed (500 nodes fits in single response) |

---

## 8. Deployment Considerations

### 8.1 CDN Dependencies

```html
<script src="https://unpkg.com/3d-force-graph@1.73.3/dist/3d-force-graph.min.js"></script>
```

### 8.2 Configuration

```yaml
graph:
  enabled: true
  maxNodes: 1000
  defaultDepth: 2
  maxDepth: 5
```

---

## 9. Implementation Checklist

| # | Task | File | Priority |
|---|------|------|----------|
| 1 | GraphResponse DTOs | graph/model/*.kt | High |
| 2 | ViewMode enum | graph/model/ViewMode.kt | High |
| 3 | GraphDataRepository | graph/GraphDataRepository.kt | High |
| 4 | ViewModeStrategy interface + 3 impls | graph/views/*.kt | High |
| 5 | GraphService | graph/GraphService.kt | High |
| 6 | GraphRoutes | graph/GraphRoutes.kt | High |
| 7 | GraphModule (Koin) | graph/di/GraphModule.kt | High |
| 8 | graph-viewer.html | resources/static/graph-viewer.html | High |
| 9 | Remaining 4 view strategies | graph/views/*.kt | Medium |
| 10 | Unit tests | test/.../graph/*.kt | High |
| 11 | Integration test | test/.../graph/it/*.kt | Medium |

---

## 10. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Architecture | [architecture.png](diagrams/architecture.png) | [architecture.drawio](diagrams/architecture.drawio) |
| 2 | Component | [component.png](diagrams/component.png) | [component.drawio](diagrams/component.drawio) |
| 3 | Class Diagram | [class-diagram.png](diagrams/class-diagram.png) | [class-diagram.drawio](diagrams/class-diagram.drawio) |
| 4 | Sequence - Load Graph | [api-sequence-load.png](diagrams/api-sequence-load.png) | [api-sequence-load.drawio](diagrams/api-sequence-load.drawio) |
