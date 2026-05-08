# Business Requirements Document (BRD)

## MCPOrchestration — MTO-22: 3D Graph Visualization – Force-Directed Graph Views

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-22 |
| Title | 3D Graph Visualization – Force-Directed Graph Views |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-07 |
| Status | Draft |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | BA Agent – Business Analyst | Create document |
| Peer Reviewer | Duc Nguyen – Product Owner | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-07 | BA Agent | Initiate document — auto-generated from Jira ticket MTO-22 |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| Duc Nguyen | ☐ I agree and confirm all criteria on this BRD as expected requirements |
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |

---

## 1. Introduction

### 1.1 Scope

Implement a 3D Graph Visualization feature for Jira ticket relationships using WebGL-based force-directed graph rendering. The system provides multiple graph views (Hierarchy, Functional, Business, Complexity, Timeline, Dependency, Team) to visualize project ticket relationships in an interactive 3D environment. This includes:

- Backend API endpoints to serve graph data (nodes + edges) for visualization
- Frontend single-page HTML viewer using 3d-force-graph.js library (WebGL)
- 7 distinct view modes with different layouts and color schemes
- Interactive features: zoom/pan/rotate, click details, hover highlight, filtering, search

### 1.2 Out of Scope

- Graph data crawling/syncing from Jira (handled by MTO-14 Ticket Crawler)
- Web Dashboard infrastructure/routing (handled by MTO-15 Web Dashboard)
- Real-time graph updates via WebSocket
- Graph editing (modifying ticket relationships from the visualization)
- Mobile-native app support (responsive web only)
- Export graph as image/PDF from the viewer

### 1.3 Preliminary Requirement

| Dependency | Description |
|------------|-------------|
| MTO-14 (Ticket Crawler) | Graph data must exist in DB — tickets and relationships crawled |
| MTO-15 (Web Dashboard) | Ktor server with static file serving and routing infrastructure |
| 3d-force-graph.js | CDN-hosted WebGL library for 3D graph rendering |

---

## 2. Business Requirements

### 2.1 High Level Process Map

The 3D Graph Visualization feature enables project managers and team members to explore Jira ticket relationships visually. Users access the graph viewer page, select a project, choose a view mode, and interact with the 3D force-directed graph to understand dependencies, team workload, complexity, and hierarchy.

![Business Flow](diagrams/business-flow.png)

### 2.2 List of User Stories / Use Cases

| # | Story / Use Case | Priority | Source Ticket |
|---|------------------|----------|---------------|
| 1 | As a project manager, I want to view ticket relationships as a 3D graph so that I can understand project structure visually | MUST HAVE | MTO-22 |
| 2 | As a project manager, I want to switch between 7 view modes so that I can analyze different aspects of the project | MUST HAVE | MTO-22 |
| 3 | As a team member, I want to click on a node to see ticket details so that I can quickly access information | MUST HAVE | MTO-22 |
| 4 | As a project manager, I want to filter nodes by type, status, assignee, and label so that I can focus on specific subsets | MUST HAVE | MTO-22 |
| 5 | As a user, I want to search for a specific ticket by key or summary so that I can locate it in the graph | MUST HAVE | MTO-22 |
| 6 | As a project manager, I want to view a subgraph around a specific issue so that I can analyze its immediate context | SHOULD HAVE | MTO-22 |
| 7 | As a user, I want the graph to handle 500+ nodes smoothly so that large projects are usable | MUST HAVE | MTO-22 |
| 8 | As a user, I want the viewer to be responsive on desktop and tablet so that I can use it on different devices | SHOULD HAVE | MTO-22 |

---

### 2.3 Details of User Stories

---

#### Business Flow

**Step 1:** User navigates to the Graph Viewer page (`/static/graph-viewer.html`)

**Step 2:** User selects a project from the project selector dropdown

**Step 3:** System calls `GET /sync/graph/{projectKey}` with default view (hierarchy)

**Step 4:** API returns graph data (nodes + edges + metadata) in JSON format

**Step 5:** Frontend renders 3D force-directed graph using 3d-force-graph.js

**Step 6:** User interacts with graph: zoom/pan/rotate, hover nodes, click for details

**Step 7:** User switches view mode via dropdown → system re-fetches with new view parameter

**Step 8:** User applies filters (type, status, assignee, label) → graph updates dynamically

**Step 9:** User searches for specific ticket → matching node is highlighted and centered

> **Note:** The graph viewer is a single HTML page served as a static resource. All data is fetched via REST API calls.

---

#### STORY 1: View Ticket Relationships as 3D Graph

> As a project manager, I want to view ticket relationships as a 3D graph so that I can understand project structure visually

**Requirement Details:**

1. System provides a WebGL-based 3D force-directed graph visualization
2. Graph displays Jira tickets as nodes and relationships as edges
3. Nodes are colored and sized based on the active view mode
4. Edges represent ticket relationships (parent/child, blocks/is-blocked-by, relates-to)
5. Force-directed layout automatically positions nodes based on their connections
6. Graph supports mouse interaction: zoom (scroll), pan (right-click drag), rotate (left-click drag)
7. Touch interaction supported for tablet: pinch-zoom, two-finger pan, one-finger rotate

**Acceptance Criteria:**

1. 3D graph renders successfully with 3d-force-graph.js library
2. All ticket nodes from the project are displayed
3. All relationships between tickets are shown as edges
4. Graph is interactive (zoom/pan/rotate) with both mouse and touch
5. Performance: 500 nodes render without visible lag (>30 FPS)

---

#### STORY 2: Switch Between 7 View Modes

> As a project manager, I want to switch between 7 view modes so that I can analyze different aspects of the project

**Requirement Details:**

1. **Hierarchy View** — Tree layout, nodes colored by issue type (Epic=purple, Story=green, Task=blue, Bug=red)
2. **Functional View** — Cluster by component/label, nodes colored by component (auto-generated distinct colors)
3. **Business View** — Cluster by epic, nodes colored by epic (auto-generated distinct colors)
4. **Complexity View** — Node size proportional to story points, color by dependency depth (Green→Yellow→Orange→Red)
5. **Timeline View** — X-axis = sprint/created date, Y-axis = status, color by status (ToDo=gray, InProgress=blue, Done=green)
6. **Dependency View** — Highlight blocks/is-blocked-by chains, critical path in red, blocked in orange
7. **Team View** — Cluster by assignee, nodes colored by team member (auto-generated distinct colors)

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| view | String (enum) | Yes | View mode selector | "hierarchy" |

**Acceptance Criteria:**

1. View selector dropdown shows all 7 view options
2. At least 3 views fully functional: Hierarchy, Dependency, Team
3. Switching views re-renders graph with appropriate layout and colors
4. Color legend updates to match the active view
5. Each view applies correct clustering/layout algorithm

---

#### STORY 3: Click Node to See Ticket Details

> As a team member, I want to click on a node to see ticket details so that I can quickly access information

**Requirement Details:**

1. Clicking a node opens a details panel (side panel or overlay)
2. Details panel shows: ticket key, summary, type, status, priority, assignee, story points
3. Panel includes a link to open the ticket in Jira
4. Hovering a node highlights all directly connected nodes and edges

**Acceptance Criteria:**

1. Click on any node displays details panel with correct ticket information
2. Hover highlights connected nodes (1-hop neighbors)
3. Details panel has a clickable link to Jira ticket
4. Panel can be dismissed by clicking elsewhere or pressing Escape

---

#### STORY 4: Filter Nodes by Attributes

> As a project manager, I want to filter nodes by type, status, assignee, and label so that I can focus on specific subsets

**Requirement Details:**

1. Filter controls available for: issue type, status, assignee, label
2. Multiple filter values can be selected simultaneously
3. Filtered-out nodes are hidden (not just dimmed)
4. Edges connected to hidden nodes are also hidden
5. Filter state persists during view switches

**Acceptance Criteria:**

1. Filter by issue type works (Epic, Story, Task, Bug)
2. Filter by status works (To Do, In Progress, Done, etc.)
3. Filter by assignee works (list of team members)
4. Multiple filters can be combined (AND logic)
5. Graph re-renders smoothly after filter change

---

#### STORY 5: Search for Ticket by Key or Summary

> As a user, I want to search for a specific ticket by key or summary so that I can locate it in the graph

**Requirement Details:**

1. Search input field accepts ticket key (e.g., "MTO-14") or partial summary text
2. Matching node is highlighted and camera centers on it
3. Search is case-insensitive
4. If multiple matches, show list of results for user to select

**Acceptance Criteria:**

1. Search by exact ticket key finds and highlights the node
2. Search by partial summary text finds matching nodes
3. Camera smoothly animates to center on the found node
4. Non-matching nodes are dimmed during search

---

#### STORY 6: View Subgraph Around Specific Issue

> As a project manager, I want to view a subgraph around a specific issue so that I can analyze its immediate context

**Requirement Details:**

1. API endpoint: `GET /sync/graph/{projectKey}/{issueKey}` returns subgraph
2. Query parameter `depth` controls how many hops from the center issue (default: 2)
3. Center issue is visually distinguished (larger size, different border)
4. Subgraph includes all nodes within N hops and their connecting edges

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| depth | Integer | No (default: 2) | Number of hops from center | 2 |
| view | String (enum) | No (default: hierarchy) | View mode | "dependency" |

**Acceptance Criteria:**

1. Subgraph API returns only nodes within specified depth
2. Center node is visually distinct
3. Depth parameter correctly limits the graph scope
4. All edges between included nodes are shown

---

#### STORY 7: Performance — Handle 500+ Nodes

> As a user, I want the graph to handle 500+ nodes smoothly so that large projects are usable

**Requirement Details:**

1. WebGL rendering maintains >30 FPS with 500 nodes
2. Force simulation converges within 3 seconds
3. Interaction (zoom/pan/rotate) remains responsive during simulation
4. Node labels are rendered efficiently (LOD — show labels only when zoomed in)

**Acceptance Criteria:**

1. 500-node graph renders without browser freeze
2. Interaction remains smooth (no visible stutter)
3. Initial layout stabilizes within 3 seconds
4. Memory usage stays under 200MB for 500-node graph

---

#### STORY 8: Responsive Design (Desktop + Tablet)

> As a user, I want the viewer to be responsive on desktop and tablet so that I can use it on different devices

**Requirement Details:**

1. Graph viewer fills available viewport
2. Controls (view selector, filters, search) adapt to screen size
3. Touch interactions work on tablet (pinch-zoom, pan, rotate)
4. Minimum supported viewport: 768px width (tablet portrait)

**Acceptance Criteria:**

1. Desktop: full-width graph with side panel for controls
2. Tablet: graph fills screen, controls in collapsible overlay
3. Touch gestures work correctly on tablet
4. No horizontal scrolling on any supported viewport

---

## 3. Dependencies

| Dependency | Type | Related Ticket | Description |
|------------|------|----------------|-------------|
| Ticket Crawler | System | MTO-14 | Graph data (tickets + relationships) must be crawled and stored in DB |
| Web Dashboard | Infrastructure | MTO-15 | Ktor server with static file serving, shared routing infrastructure |
| 3d-force-graph.js | External Library | N/A | WebGL-based 3D force-directed graph library (CDN) |
| WebGL Support | Infrastructure | N/A | User's browser must support WebGL 2.0 |

---

## 4. Stakeholders

| Role | Name / Team | Responsibility | Source |
|------|-------------|----------------|--------|
| Product Owner | Duc Nguyen | Define requirements, accept/reject | Reporter |
| Developer | TBD | Implement API + Frontend | Assignee |
| QA | TBD | Test functionality + performance | Team |

---

## 5. Risks and Assumptions

### 5.1 Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| WebGL not supported on older browsers | High | Low | Show fallback message, require modern browser |
| Performance degradation with >500 nodes | High | Medium | Implement LOD, node clustering for large graphs |
| 3d-force-graph.js library breaking changes | Medium | Low | Pin CDN version, test before upgrading |
| Graph data inconsistency (stale data from crawler) | Medium | Medium | Show "last synced" timestamp, allow manual refresh |
| Complex dependency chains cause visual clutter | Medium | High | Provide depth filter, edge bundling for dense areas |

### 5.2 Assumptions

- Users have modern browsers with WebGL 2.0 support (Chrome 56+, Firefox 51+, Edge 79+, Safari 15+)
- Ticket Crawler (MTO-14) provides complete relationship data including blocks/is-blocked-by links
- Maximum project size for initial release: 500 tickets (can be extended later)
- 3d-force-graph.js library is stable and maintained (MIT license, active GitHub)
- Graph data is read-only — no write-back to Jira from the visualization

---

## 6. Non-Functional Requirements

| Category | Requirement | Details |
|----------|-------------|---------|
| Performance | Graph rendering | 500 nodes at >30 FPS, initial layout <3s |
| Performance | API response time | Graph data endpoint <500ms for 500 nodes |
| Performance | Memory | <200MB browser memory for 500-node graph |
| Usability | Responsiveness | Desktop + tablet (min 768px width) |
| Usability | Interaction | Mouse + touch support |
| Compatibility | Browser | Chrome 56+, Firefox 51+, Edge 79+, Safari 15+ |
| Compatibility | WebGL | WebGL 2.0 required |
| Reliability | Error handling | Graceful fallback if WebGL unavailable |
| Security | Data access | Graph data respects project permissions |

---

## 7. Related Tickets

| Ticket Key | Summary | Status | Type | Relationship |
|------------|---------|--------|------|--------------|
| MTO-22 | 3D Graph Visualization – Force-Directed Graph Views | Docs Review | Story | Main ticket |
| MTO-14 | Jira Project Sync Service | To Do | Epic | Blocks (provides graph data) |
| MTO-15 | Web Dashboard | To Do | Story | Blocks (provides server infrastructure) |

---

## 8. Appendix

### API Response Format

**Node Format:**
```json
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
```

**Edge Format:**
```json
{
  "source": "MTO-14",
  "target": "MTO-15",
  "type": "parent",
  "label": "has child",
  "color": "#999",
  "width": 2
}
```

### Color Schemes

| View | Color By | Palette |
|------|----------|---------|
| Hierarchy | Issue Type | Epic=purple, Story=green, Task=blue, Bug=red |
| Functional | Component | Auto-generated distinct colors |
| Business | Epic | Auto-generated distinct colors |
| Complexity | Dependency Depth | Green(0) → Yellow(1-2) → Orange(3-4) → Red(5+) |
| Timeline | Status | ToDo=gray, InProgress=blue, Done=green |
| Dependency | Critical Path | Normal=gray, Critical=red, Blocked=orange |
| Team | Assignee | Auto-generated distinct colors |

### Glossary

| Term | Definition |
|------|------------|
| Force-Directed Graph | A graph layout algorithm where nodes repel each other and edges act as springs |
| WebGL | Web Graphics Library — JavaScript API for rendering 2D/3D graphics in browser |
| LOD | Level of Detail — rendering technique that reduces detail for distant objects |
| Subgraph | A subset of a graph containing specific nodes and their connecting edges |
| Critical Path | The longest chain of dependent tickets that determines minimum project duration |

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Business Flow | [business-flow.png](diagrams/business-flow.png) | [business-flow.drawio](diagrams/business-flow.drawio) |
| 2 | Use Case Diagram | [use-case.png](diagrams/use-case.png) | [use-case.drawio](diagrams/use-case.drawio) |

### Reference Documents

| Document | Link / Location |
|----------|-----------------|
| 3d-force-graph.js | https://github.com/vasturiano/3d-force-graph |
| Jira Ticket | MTO-22 |
| BRD-v1-MTO-22.docx | Attached to Jira ticket |
