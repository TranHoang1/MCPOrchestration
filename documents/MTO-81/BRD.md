# Business Requirements Document (BRD)

## MCPOrchestration — MTO-81: [Graph] Hiển thị ticket key labels trực tiếp trên nodes

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-81 |
| Title | [Graph] Hiển thị ticket key labels trực tiếp trên nodes |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-11 |
| Status | Draft |
| Epic | MTO-79 — [Epic] Graph Viewer - UX Improvements |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | BA Agent – Business Analyst | Create document |
| Peer Reviewer | Duc Nguyen – Project Lead | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-11 | BA Agent | Initiate document — auto-generated from Jira ticket MTO-81 |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |

---

## 1. Introduction

### 1.1 Scope

This change request adds **persistent ticket key labels directly on graph nodes** in the 3D Graph Viewer. Currently, users must hover or click on a node to discover which Jira ticket it represents. This is the **#1 UX issue** identified in the Graph Viewer evaluation (Discoverability score: 4/10).

The scope covers:

1. **Label Rendering** — Display ticket key text labels directly on or adjacent to each node in the 3D graph, without requiring hover/click interaction
2. **Label Content Strategy** — Epic nodes show `key + summary (truncated)`, Story/Task nodes show `key` only
3. **Zoom-Responsive Behavior** — Labels auto-hide when nodes become too small at far zoom levels, preventing visual clutter
4. **Anti-Overlap** — Labels must not overlap each other when zoomed out, using intelligent positioning or culling

### 1.2 Out of Scope

- Changes to the Graph API backend (data model already includes `id`, `label`, `type` fields)
- Node shape changes (nodes remain spheres)
- Edge label improvements (separate ticket MTO-82 if exists)
- Filtering/search functionality changes
- Detail panel modifications
- Dark/light theme toggle
- Mobile responsive design
- Performance optimization of the force-directed layout algorithm itself

### 1.3 Preliminary Requirements

1. **Graph Viewer** must be operational with 3d-force-graph library loaded
2. **Graph API** (`GET /sync/graph/{projectKey}`) must return node data including `id` (ticket key), `label` (summary), and `type` (issue type)
3. **3d-force-graph v1.x** — current library supports `nodeThreeObject` and `nodeThreeObjectExtend` for custom node rendering

---

## 2. Business Requirements

### 2.1 High Level Process Map

The label rendering operates as part of the graph visualization pipeline:

1. Graph data loaded from API → nodes contain `id`, `label`, `type`, `size`
2. For each node, determine label content based on issue type
3. Render label as 3D text sprite attached to node
4. On zoom/camera change, evaluate visibility threshold
5. Show/hide labels based on apparent node size on screen

### 2.2 List of User Stories / Use Cases

| # | Story / Use Case | Priority | Source Ticket |
|---|------------------|----------|---------------|
| 1 | As a user, I want to see ticket keys directly on graph nodes so that I can identify tickets without hovering | MUST HAVE | MTO-81 |
| 2 | As a user, I want Epic nodes to show key + truncated summary so that I can understand the epic context at a glance | MUST HAVE | MTO-81 |
| 3 | As a user, I want labels to auto-hide when zoomed out so that the graph remains readable | MUST HAVE | MTO-81 |
| 4 | As a user, I want labels to not overlap each other so that I can read them clearly | MUST HAVE | MTO-81 |

---

### 2.3 Details of User Stories

---

#### Business Flow

**Step 1:** User opens Graph Viewer and loads a project graph

**Step 2:** Graph renders with 3D force-directed layout. Each node displays its ticket key label directly on/near the node

**Step 3:** User zooms in — labels become more visible and readable

**Step 4:** User zooms out — labels that would be too small to read auto-hide, preventing clutter

**Step 5:** User identifies tickets of interest by reading labels directly, without needing to hover

**Step 6:** User clicks a node for full details (existing behavior preserved)

> **Note:** The hover tooltip (`nodeLabel`) remains as a fallback showing full `key: summary` for all node types.

---

#### STORY 1: Display Ticket Key Labels on Nodes

> As a user, I want to see ticket keys directly on graph nodes so that I can identify tickets without hovering.

**Requirement Details:**

1. Every node in the graph MUST display a text label visible without user interaction
2. Labels are rendered as 2D text sprites (CSS2DRenderer or canvas-based sprites) that always face the camera (billboard effect)
3. Label is positioned slightly above or below the node sphere to avoid occlusion
4. Label font size is proportional to node size for visual hierarchy
5. Label color contrasts with the dark background (white or light color with slight transparency)

**Acceptance Criteria:**

1. When graph loads, all nodes display their ticket key as a visible text label
2. Labels are readable at default zoom level
3. Labels face the camera regardless of rotation angle (billboard behavior)
4. Labels do not obscure the node sphere color (positioned offset, not overlapping)
5. Existing hover tooltip behavior is preserved as supplementary information

---

#### STORY 2: Epic Nodes Show Key + Truncated Summary

> As a user, I want Epic nodes to show key + truncated summary so that I can understand the epic context at a glance.

**Requirement Details:**

1. Nodes with `type === "Epic"` display label format: `{key}\n{summary}` (two lines)
2. Summary is truncated to maximum 30 characters with ellipsis (`...`) if longer
3. Epic labels use slightly larger font size than Story/Task labels (reflecting their larger node size)
4. Story and Task nodes display only the ticket key (e.g., `MTO-81`)

**Label Content Rules:**

| Node Type | Label Format | Example |
|-----------|-------------|---------|
| Epic | `{key}\n{summary (≤30 chars)}` | `MTO-79\nGraph Viewer - UX Impro...` |
| Story | `{key}` | `MTO-81` |
| Task | `{key}` | `MTO-85` |
| Bug | `{key}` | `MTO-90` |
| Sub-task | `{key}` | `MTO-91` |

**Acceptance Criteria:**

1. Epic nodes display two-line labels with key and truncated summary
2. Summary truncation occurs at 30 characters with `...` appended
3. Non-Epic nodes display only the ticket key
4. Font size for Epic labels is 20-30% larger than Story/Task labels
5. Multi-line labels are properly aligned (centered)

---

#### STORY 3: Labels Auto-Hide When Zoomed Out

> As a user, I want labels to auto-hide when zoomed out so that the graph remains readable.

**Requirement Details:**

1. Labels have a visibility threshold based on the apparent size of the node on screen
2. When the camera is far away (zoomed out), nodes appear small — labels below a minimum readable size are hidden
3. The threshold is calculated per frame based on camera distance to each node
4. Transition between visible/hidden should be smooth (opacity fade, not abrupt show/hide)
5. At maximum zoom out, only Epic labels (largest nodes) may remain visible

**Visibility Algorithm:**

```
apparentSize = nodeSize / distanceToCamera
if (apparentSize < THRESHOLD) → hide label (fade out)
if (apparentSize >= THRESHOLD) → show label (fade in)
```

**Threshold Values:**

| Node Type | Show Threshold | Hide Threshold |
|-----------|---------------|----------------|
| Epic | 0.8 | 0.6 |
| Story/Task | 1.2 | 1.0 |
| Sub-task | 1.5 | 1.3 |

> Hysteresis (different show/hide thresholds) prevents flickering at boundary distances.

**Acceptance Criteria:**

1. Labels disappear smoothly when zooming out past the threshold
2. Labels reappear smoothly when zooming back in
3. No flickering at threshold boundaries (hysteresis implemented)
4. At maximum zoom out with 100+ nodes, the graph is not cluttered with overlapping text
5. Epic labels remain visible longer than Story/Task labels (larger threshold)

---

#### STORY 4: Labels Do Not Overlap

> As a user, I want labels to not overlap each other so that I can read them clearly.

**Requirement Details:**

1. When multiple nodes are close together, their labels should not overlap and become unreadable
2. Primary strategy: zoom-based auto-hide (Story 3) naturally reduces overlap at far distances
3. Secondary strategy: at close zoom, if labels would overlap, apply minor position offset or reduce opacity of less important labels
4. Priority for label visibility: Epic > Story > Task > Bug > Sub-task

**Anti-Overlap Strategies (in priority order):**

1. **Zoom-based culling** — Most effective. At far zoom, only Epic labels visible
2. **Size-based priority** — Larger nodes (Epics) always show labels; smaller nodes yield
3. **Opacity reduction** — Dense clusters: reduce opacity of lower-priority labels to 50%

**Acceptance Criteria:**

1. At default zoom level, labels of adjacent nodes are readable without overlap
2. When zooming out, lower-priority labels hide before higher-priority ones
3. No two labels are fully overlapping at any zoom level where both are visible
4. Performance remains smooth (60fps) with up to 200 visible labels

---

## 3. Dependencies

| Dependency | Type | Related Ticket | Description |
|------------|------|----------------|-------------|
| 3d-force-graph library | System | N/A | Must support `nodeThreeObject` or `nodeThreeObjectExtend` for custom rendering |
| Three.js (bundled) | System | N/A | Required for CSS2DRenderer or Sprite-based text labels |
| Graph API | System | N/A | Must return `id`, `label`, `type` fields per node (already available) |
| MTO-79 | Epic | MTO-79 | Parent epic — Graph Viewer UX Improvements |

---

## 4. Stakeholders

| Role | Name / Team | Responsibility | Source |
|------|-------------|----------------|--------|
| Reporter | Duc Nguyen | Define requirements, accept delivery | Jira reporter |
| Developer | TBD | Implement label rendering | Assignee |
| QA | QA Agent | Verify acceptance criteria | Process |

---

## 5. Risks and Assumptions

### 5.1 Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Performance degradation with many labels | High | Medium | Implement zoom-based culling; limit visible labels to 50 at any time |
| Text readability on dark background | Medium | Low | Use white text with slight shadow/outline for contrast |
| 3d-force-graph API limitations | Medium | Low | Library supports `nodeThreeObject` — verified in docs |
| Label positioning conflicts with node hover | Low | Low | Offset labels above nodes; preserve hover zone on sphere |

### 5.2 Assumptions

- The 3d-force-graph library's `nodeThreeObject` or `nodeThreeObjectExtend` API is sufficient for adding text sprites
- Three.js `Sprite` with `SpriteMaterial` (canvas-based texture) provides adequate text rendering quality
- Typical graph size is 50-300 nodes (based on Jira project MTO)
- Users primarily use desktop browsers with WebGL support
- The existing dark background (`#0d1117`) is maintained

---

## 6. Non-Functional Requirements

| Category | Requirement | Details |
|----------|-------------|---------|
| Performance | Label rendering must not drop below 30fps | With 200 nodes and labels visible, maintain smooth interaction |
| Performance | Label show/hide transitions complete within 300ms | Smooth opacity animation |
| Usability | Labels readable at default zoom | Font size ≥ 10px apparent size at default camera distance |
| Accessibility | Sufficient contrast ratio | White text on dark background meets WCAG AA (4.5:1 minimum) |
| Compatibility | Works in Chrome, Firefox, Edge (latest) | WebGL + Three.js sprite support |
| Scalability | Handles up to 500 nodes | Culling algorithm ensures max ~50 labels visible simultaneously |

---

## 7. Related Tickets

| Ticket Key | Summary | Status | Type | Relationship |
|------------|---------|--------|------|--------------|
| MTO-81 | [Graph] Hiển thị ticket key labels trực tiếp trên nodes | Docs Review | Story | Main ticket |
| MTO-79 | [Epic] Graph Viewer - UX Improvements | To Do | Epic | Parent epic |

---

## 8. Appendix

### Glossary

| Term | Definition |
|------|------------|
| Billboard | A 2D element in 3D space that always faces the camera |
| Sprite | A Three.js object that renders a 2D texture always facing the camera |
| Culling | The process of hiding elements that are not relevant at the current view |
| Hysteresis | Using different thresholds for show vs hide to prevent flickering |
| Apparent size | The perceived size of an object based on its actual size and distance from camera |

### Technical Reference

| Document | Link / Location |
|----------|-----------------|
| 3d-force-graph docs | https://github.com/vasturiano/3d-force-graph |
| Three.js Sprite docs | https://threejs.org/docs/#api/en/objects/Sprite |
| Current graph-viewer.html | `orchestrator-server/src/main/resources/static/graph-viewer.html` |
| Graph API endpoint | `GET /sync/graph/{projectKey}?view={mode}` |
| GraphResponse model | `orchestrator-server/.../graph/model/GraphResponse.kt` |
