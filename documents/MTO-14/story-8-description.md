## Mô tả

Implement 3D Graph Visualization cho Jira ticket relationships — WebGL-based force-directed graph với multiple views.

## Scope

### API Endpoint

- **GET /sync/graph/{projectKey}** — Graph data cho visualization
  - Query params: view (hierarchy|functional|business|complexity|timeline|dependency|team)
  - Response: { nodes: [...], edges: [...], metadata: {...} }

- **GET /sync/graph/{projectKey}/{issueKey}** — Subgraph xung quanh 1 issue
  - Query params: depth (default 2), view
  - Response: same format, filtered

### Node Format

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

### Edge Format

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

### Views (Graph Layouts & Coloring)

1. **Hierarchy** — Tree layout, color by issue type (Epic/Story/Task/Bug)
2. **Functional** — Cluster by component/label, color by component
3. **Business** — Cluster by epic, color by epic
4. **Complexity** — Node size = story points, color = dependency depth (red = high)
5. **Timeline** — X-axis = sprint/created date, Y-axis = status
6. **Dependency** — Highlight blocks/is-blocked-by chains, critical path in red
7. **Team** — Cluster by assignee, color by team member

### Frontend (3d-force-graph.js)

- Library: 3d-force-graph (CDN, WebGL-based)
- Single HTML page: resources/static/graph-viewer.html
- Features:
  - Zoom/pan/rotate (mouse + touch)
  - Click node → show details panel
  - Hover → highlight connected nodes
  - View selector dropdown
  - Filter by: issue type, status, assignee, label
  - Search: find node by key/summary
  - Legend: color coding explanation
- Performance: handle 500+ nodes smoothly

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

## Acceptance Criteria

- [ ] API endpoint trả về graph data đúng format
- [ ] 3D graph render thành công với 3d-force-graph.js
- [ ] Ít nhất 3 views hoạt động (Hierarchy, Dependency, Team)
- [ ] Click node hiển thị details
- [ ] Filter hoạt động (type, status, assignee)
- [ ] Search tìm node by key
- [ ] Performance: 500+ nodes không lag
- [ ] Responsive (desktop + tablet)
- [ ] Unit tests cho graph data transformation
- [ ] Manual test: navigate graph, switch views

## Story Points: 8

## Dependencies

- **Blocked by:** Story 4 (Ticket Crawler) — cần graph data trong DB
- **Blocked by:** Story 7 (Web Dashboard) — cùng Ktor server, share routing
