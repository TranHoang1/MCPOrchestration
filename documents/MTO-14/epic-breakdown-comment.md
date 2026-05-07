## Epic Breakdown — Stories Created

| # | Key | Story | Priority | SP | Labels |
|---|-----|-------|----------|----|---------| 
| 1 | MTO-15 | Database Schema & Sync State Management | High | 5 | backend, database, foundation |
| 2 | MTO-16 | Jira REST Client — Direct API Integration | High | 5 | backend, integration, foundation |
| 3 | MTO-17 | Project Scanner — Breadth-First Incremental Scan | High | 8 | backend, coroutines, foundation |
| 4 | MTO-18 | Ticket Crawler — Deep Content Sync & KB Ingestion | High | 8 | backend, knowledge-base, coroutines |
| 5 | MTO-19 | Attachment Processor — Background Queue Worker | Medium | 8 | backend, knowledge-base, coroutines |
| 6 | MTO-20 | MCP Tool Integration — Sync & Graph Tools | Medium | 5 | backend, mcp-tools, integration |
| 7 | MTO-21 | Web Dashboard — Sync Status & Monitoring | Medium | 5 | frontend, dashboard, websocket |
| 8 | MTO-22 | 3D Graph Visualization — Force-Directed Graph Views | Medium | 8 | frontend, visualization, webgl |

**Total Story Points: 52**

## Dependency Graph

```
MTO-15 (DB Schema) ──┬──→ MTO-17 (Scanner) ──→ MTO-18 (Crawler) ──→ MTO-22 (3D Graph)
MTO-16 (REST Client) ┘         │                      │
                               ├──→ MTO-20 (MCP Tools)
                               ├──→ MTO-21 (Dashboard) ──→ MTO-22 (3D Graph)
                               │
MTO-15 + MTO-16 ──→ MTO-19 (Attachments)
```

## Implementation Order (Recommended)

1. **Sprint 1**: MTO-15 + MTO-16 (parallel, no dependencies)
2. **Sprint 2**: MTO-17 + MTO-19 (parallel, both depend on Sprint 1)
3. **Sprint 3**: MTO-18 + MTO-21 (parallel)
4. **Sprint 4**: MTO-20 + MTO-22 (parallel, final integration)
