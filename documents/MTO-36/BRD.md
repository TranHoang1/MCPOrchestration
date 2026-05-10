# Business Requirements Document (BRD)

## MCPOrchestration — MTO-36: KB Refinery — Feature Network Mapping

---

## 1. Introduction

### 1.1 Scope

Build a feature relationship graph that combines Jira linked tickets with semantic similarity data (from MTO-35) to create a network map of feature dependencies. Provides a graph visualization data API for frontend consumption.

### 1.2 Dependencies

| Dependency | Description |
|------------|-------------|
| MTO-35 | Semantic entity links |
| MTO-22 | Existing graph module |

---

## 2. User Stories

| # | Story | Priority |
|---|-------|----------|
| 1 | As a PM, I want to see a network graph of related features so I can understand dependencies | MUST HAVE |
| 2 | As a developer, I want graph data API returning nodes and edges so frontend can render | MUST HAVE |
| 3 | As a BA, I want to filter the network by project/epic/label | SHOULD HAVE |
| 4 | As a user, I want edge weights based on similarity score | SHOULD HAVE |

---

## 3. Acceptance Criteria

1. Given tickets with semantic links, when graph API called, then returns nodes (tickets) and edges (links)
2. Given a center ticket, when requesting network, then returns N-hop neighborhood
3. Given filter by project, when applied, then only tickets from that project appear
4. Edge weight = similarity score from entity_links table

