# Release Notes (RLN)

## MCPOrchestration — MTO-36: KB Refinery — Feature Network Mapping

---

## Release Information

| Field | Value |
|-------|-------|
| Release Version | 1.4.0 |
| Release Date | 2026-05-09 |
| Jira Ticket | MTO-36 |
| Environment | DEV / SIT / UAT / PROD |
| Author | DevOps Agent |
| Status | Draft |

---

## 1. What's New

### 1.1 Feature Summary

The MCP Orchestrator now includes **Feature Network Mapping** — a graph service that builds relationship networks from semantic entity links. It provides D3.js-ready JSON output for frontend visualization, supporting both focused neighborhood queries (N-hop BFS) and full project-wide network views.

**Key benefits:**
- **Visual dependency mapping:** See how features relate to each other at a glance
- **Configurable depth:** Query 1-hop (direct) to 5-hop (extended) neighborhoods
- **Filtering:** Narrow results by project or minimum similarity weight
- **Frontend-ready:** JSON output compatible with D3.js, Cytoscape.js, and similar libraries

### 1.2 User-Facing Changes

| # | Change | Description | Impact |
|---|--------|-------------|--------|
| 1 | Network graph API | Query feature relationships as graph data | High — enables visualization |
| 2 | N-hop neighborhood | BFS-based focused graph around a center ticket | High — targeted exploration |
| 3 | Full network view | Project-wide relationship overview | Medium — big picture view |
| 4 | Filtering | Filter by project key and minimum weight | Low — refinement |

---

## 2. Technical Changes

### 2.1 New Package

```
com.orchestrator.mcp.network/
├── NetworkService.kt                 (interface)
├── NetworkServiceImpl.kt             (BFS implementation ~70 lines)
├── model/
│   ├── NetworkGraph.kt               (graph data class)
│   ├── GraphNode.kt                  (node data class)
│   ├── GraphEdge.kt                  (edge data class)
│   └── NetworkConfig.kt              (configuration)
└── di/
    └── NetworkModule.kt              (Koin module)
```

### 2.2 Configuration Changes

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `orchestrator.network.enabled` | Boolean | true | Master toggle |
| `orchestrator.network.max-hops` | Int | 5 | Maximum BFS depth |
| `orchestrator.network.max-nodes` | Int | 1000 | Max nodes in response |
| `orchestrator.network.truncation-threshold` | Int | 500 | Truncation warning threshold |

### 2.3 DI Changes

| Interface | Implementation | Scope |
|-----------|---------------|-------|
| `NetworkService` | `NetworkServiceImpl` | Singleton |

### 2.4 Database Changes

None. Uses existing `entity_links` table from MTO-35.

### 2.5 API Output Format (D3.js-ready)

```json
{
  "nodes": [{ "id": "MTO-35", "label": "...", "type": "ticket", "properties": {} }],
  "edges": [{ "source": "MTO-35", "target": "MTO-36", "weight": 0.92, "type": "semantic" }],
  "metadata": { "totalNodes": 10, "totalEdges": 15, "centerNode": "MTO-35", "truncated": false }
}
```

---

## 3. Dependencies

### 3.1 Pre-requisite Releases

| Release | Version | Required Before |
|---------|---------|-----------------|
| MTO-35 (Entity Linking) | 1.3.0 | This release |
| MTO-10 (Base Orchestrator) | 1.0.0 | This release |

### 3.2 External System Changes

| System | Change Required |
|--------|----------------|
| PostgreSQL | None (uses existing entity_links) |
| Qdrant | None |
| Frontend | New — consume graph API (optional) |

---

## 4. Breaking Changes

None. Fully backward compatible. New feature only.

---

## 5. Known Limitations

| # | Limitation | Impact | Workaround |
|---|-----------|--------|------------|
| 1 | No graph caching | Repeated queries re-compute graph | Future: add Redis cache |
| 2 | Node labels require KB lookup | May be empty if KB entry not found | Use issue key as fallback label |
| 3 | No real-time updates | Graph reflects DB state at query time | Re-query for fresh data |
| 4 | Large graphs truncated at 1000 nodes | Some nodes may be excluded | Use project filter to narrow scope |

---

## 6. Migration Notes

No data migration required. Feature is purely additive.

---

## 7. Testing Summary

| Test Level | Total | Pass Rate |
|-----------|-------|-----------|
| Property-Based Tests | 3 | TBD |
| Unit Tests | 10 | TBD |
| Integration Tests | 3 | TBD |
| E2E API Tests | 5 | TBD |
| **Total** | **21** | TBD |

---

## 8. Deployment Instructions

See: [Deployment Guide (DPG.md)](DPG.md)

**Quick Reference:**
1. Update application.yml (add network config)
2. Deploy new JAR
3. Verify startup

**Estimated deployment time:** ~3 minutes (no DB migration needed)

---

## 9. Rollback Plan

**Quick rollback:** Set `network.enabled: false` and restart.
**Full rollback:** Restore previous JAR.
