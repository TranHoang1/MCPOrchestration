# Functional Specification Document (FSD)

## MCPOrchestration — MTO-36: KB Refinery — Feature Network Mapping

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-36 |
| Title | KB Refinery — Feature Network Mapping |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-36.docx |
| Related TDD | TDD-v1-MTO-36.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | BA + TA Agent | Initial FSD — full functional specification |

---

## 1. Introduction

### 1.1 Purpose

This FSD specifies the complete functional design for the **Feature Network Mapping** service. It builds a relationship graph combining Jira linked tickets with semantic similarity data (from MTO-35 Entity Linking) to create a network map of feature dependencies. The service provides a graph visualization data API producing D3.js-ready JSON output.

### 1.2 Scope

**In Scope:**
- Graph construction from entity_links (MTO-35) and Jira linked tickets
- BFS traversal for depth-limited neighborhood queries (N-hop)
- Full network graph for a project
- Filtering by project, epic, or label
- Edge weights based on similarity scores
- D3.js-compatible JSON output (nodes + edges format)
- Graph metadata (node count, edge count, center node)

**Out of Scope:**
- Frontend rendering (D3.js/Cytoscape.js implementation)
- Real-time graph updates (WebSocket push)
- Graph persistence/caching (computed on-demand)
- Cross-project network merging
- Graph layout algorithms (frontend responsibility)

### 1.3 Definitions & Acronyms

| Term | Definition |
|------|------------|
| Network Graph | Collection of nodes (tickets) and edges (relationships) |
| BFS | Breadth-First Search — traversal algorithm for hop-limited queries |
| Hop | One edge traversal in the graph (1-hop = direct neighbors) |
| D3.js | JavaScript library for data visualization |
| Edge Weight | Similarity score (0.0–1.0) representing relationship strength |

### 1.4 References

| Document | Location |
|----------|----------|
| BRD — MTO-36 | documents/MTO-36/BRD.md |
| TDD — MTO-36 | documents/MTO-36/TDD.md |
| MTO-35 FSD (Entity Linking) | documents/MTO-35/FSD.md |

---

## 2. System Overview

### 2.1 System Context

The Feature Network Mapping service sits on top of the Entity Linking layer (MTO-35), consuming persisted links from the `entity_links` table. It constructs graph structures on-demand and returns D3.js-compatible JSON for frontend visualization.

**Data Sources:**
- **EntityLinkRepository** (MTO-35): Semantic similarity links with scores
- **Jira Linked Tickets** (optional): Explicit ticket relationships (blocks, relates-to, etc.)

**Consumers:**
- **Frontend** (D3.js/Cytoscape.js): Renders interactive network visualization
- **AI Agents**: Query related features for context building

### 2.2 Integration Points

| System | Direction | Protocol | Purpose |
|--------|-----------|----------|---------|
| EntityLinkRepository (MTO-35) | Inbound | Internal API | Read semantic links |
| Jira API (optional) | Outbound | REST | Read linked tickets |
| Frontend | Outbound | JSON API | Provide graph data |

---

## 3. Functional Requirements

### 3.1 Feature: Get Network for Issue (N-Hop Neighborhood)

**Source:** [Implements: Story #1, Story #2]

#### 3.1.1 Use Case

**Use Case ID:** UC-01
**Actor:** PM / Developer (via API or frontend)
**Preconditions:**
- Center issue key exists in entity_links table
- At least one link exists for the center issue

**Postconditions:**
- NetworkGraph returned with all nodes within N hops of center

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User | | Calls `getNetwork(centerIssueKey, hops=2)` |
| 2 | | NetworkService | Initialize BFS queue with center node |
| 3 | | NetworkService | For each level (1 to hops): |
| 4 | | EntityLinkRepository | Query links for current frontier nodes |
| 5 | | NetworkService | Add discovered nodes to graph, track visited |
| 6 | | NetworkService | Build GraphNode list with properties |
| 7 | | NetworkService | Build GraphEdge list with weights |
| 8 | | NetworkService | Return NetworkGraph with metadata |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-01 | hops = 0 | Return only the center node with no edges |
| AF-02 | Center node has no links | Return single-node graph |
| AF-03 | Graph exceeds 500 nodes | Truncate at 500 nodes, set metadata.truncated = true |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-01 | Center issue key not found in any links | Return empty graph with metadata.totalNodes = 0 |
| EF-02 | Database connection failure | Throw ServerUnavailableException |

---

### 3.2 Feature: Get Full Network (Project-Wide)

**Source:** [Implements: Story #1, Story #3]

#### 3.2.1 Use Case

**Use Case ID:** UC-02
**Actor:** PM (via API or frontend)
**Preconditions:**
- entity_links table has data
- Optional: projectKey filter provided

**Postconditions:**
- Full network graph returned (all nodes and edges, optionally filtered)

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User | | Calls `getFullNetwork(projectKey = "MTO")` |
| 2 | | EntityLinkRepository | Query all links (optionally filtered by project prefix) |
| 3 | | NetworkService | Build unique node set from all source + target keys |
| 4 | | NetworkService | Build edge list with weights |
| 5 | | NetworkService | Apply project filter if provided |
| 6 | | NetworkService | Return NetworkGraph with metadata |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-01 | No projectKey filter | Return entire graph (all projects) |
| AF-02 | Graph exceeds 1000 nodes | Truncate, set metadata.truncated = true |
| AF-03 | Project has no links | Return empty graph |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-01 | Database timeout on large query | Return partial results with metadata.partial = true |

---

### 3.3 Feature: Filter Network by Criteria

**Source:** [Implements: Story #3]

#### 3.3.1 Use Case

**Use Case ID:** UC-03
**Actor:** BA / PM
**Preconditions:**
- Network graph available (from UC-01 or UC-02)

**Postconditions:**
- Filtered graph returned matching criteria

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User | | Calls getNetwork with filter parameters |
| 2 | | NetworkService | Build full graph first |
| 3 | | NetworkService | Apply project filter (prefix match on issue key) |
| 4 | | NetworkService | Apply minimum weight filter (edges below threshold removed) |
| 5 | | NetworkService | Remove orphan nodes (no remaining edges) |
| 6 | | NetworkService | Return filtered NetworkGraph |

**Business Rules:**

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-01 | Project filter matches issue key prefix (e.g., "MTO" matches "MTO-35") | Story #3 |
| BR-02 | Minimum weight filter removes edges below specified score | Story #4 |
| BR-03 | Orphan nodes (no edges after filtering) are removed from result | Design |

---

### 3.4 Feature: Edge Weight from Similarity Score

**Source:** [Implements: Story #4]

#### 3.4.1 Use Case

**Use Case ID:** UC-04
**Actor:** Frontend (automatic)
**Preconditions:**
- Links exist with similarity scores

**Postconditions:**
- Edge weights in graph output reflect similarity scores

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | | NetworkService | Read similarity_score from entity_links |
| 2 | | NetworkService | Map to GraphEdge.weight field |
| 3 | | Frontend | Uses weight for edge thickness/opacity in visualization |

**Business Rules:**

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-04 | Edge weight = similarity_score from entity_links (0.0–1.0) | Story #4 |
| BR-05 | Jira linked tickets get weight = 1.0 (explicit relationship) | Design |
| BR-06 | Edge type distinguishes semantic vs jira-linked | Design |

---

## 4. Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-01 | Project filter matches issue key prefix | Story #3 |
| BR-02 | Minimum weight filter removes edges below threshold | Story #4 |
| BR-03 | Orphan nodes removed after filtering | Design |
| BR-04 | Edge weight = similarity_score (0.0–1.0) | Story #4 |
| BR-05 | Jira linked tickets get weight = 1.0 | Design |
| BR-06 | Edge type: "semantic" or "jira_linked" | Design |
| BR-07 | Max hops for BFS = 5 (prevent runaway traversal) | NFR |
| BR-08 | Max nodes in response = 1000 (truncate if exceeded) | NFR |
| BR-09 | BFS visits each node at most once (no cycles) | Algorithm |
| BR-10 | Center node always included in result (even if no edges) | Design |

---

## 5. Data Specifications

### 5.1 Input Data

#### 5.1.1 getNetwork Input

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| centerIssueKey | String | Yes | Non-empty, `[A-Z]+-\d+` | Center node for BFS |
| hops | Int | No | 0–5, default 2 | Traversal depth |

#### 5.1.2 getFullNetwork Input

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| projectKey | String? | No | `[A-Z]+` or null | Filter by project |
| minWeight | Double? | No | 0.0–1.0 or null | Minimum edge weight |

### 5.2 Output Data

#### 5.2.1 NetworkGraph (D3.js-ready JSON)

```json
{
  "nodes": [
    { "id": "MTO-35", "label": "Semantic Entity Linking", "type": "ticket", "properties": { "project": "MTO" } },
    { "id": "MTO-36", "label": "Feature Network Mapping", "type": "ticket", "properties": { "project": "MTO" } }
  ],
  "edges": [
    { "source": "MTO-35", "target": "MTO-36", "weight": 0.92, "type": "semantic" }
  ],
  "metadata": {
    "totalNodes": 2,
    "totalEdges": 1,
    "centerNode": "MTO-35",
    "truncated": false
  }
}
```

#### 5.2.2 GraphNode

| Field | Type | Description |
|-------|------|-------------|
| id | String | Issue key (unique identifier) |
| label | String | Ticket title/summary |
| type | String | Always "ticket" |
| properties | Map<String, String> | Additional metadata (project, status, etc.) |

#### 5.2.3 GraphEdge

| Field | Type | Description |
|-------|------|-------------|
| source | String | Source node ID (issue key) |
| target | String | Target node ID (issue key) |
| weight | Double | Similarity score (0.0–1.0) |
| type | String | "semantic" or "jira_linked" |

---

## 6. API Contracts

### 6.1 NetworkService Interface

```kotlin
interface NetworkService {
    /**
     * Get N-hop neighborhood graph centered on an issue.
     * Uses BFS traversal, limited by hops parameter.
     * @param centerIssueKey Center node for traversal
     * @param hops Max traversal depth (default 2, max 5)
     * @return NetworkGraph with nodes and edges within N hops
     */
    suspend fun getNetwork(centerIssueKey: String, hops: Int = 2): NetworkGraph

    /**
     * Get full network graph, optionally filtered by project.
     * @param projectKey Optional project prefix filter
     * @param minWeight Optional minimum edge weight filter
     * @return NetworkGraph with all matching nodes and edges
     */
    suspend fun getFullNetwork(
        projectKey: String? = null,
        minWeight: Double? = null
    ): NetworkGraph
}
```

### 6.2 MCP Tool Exposure

```json
{
  "name": "get_feature_network",
  "description": "Get a network graph of related features centered on an issue key",
  "inputSchema": {
    "type": "object",
    "properties": {
      "issue_key": { "type": "string", "description": "Center issue key" },
      "hops": { "type": "integer", "description": "Traversal depth (default 2, max 5)", "default": 2 },
      "project_filter": { "type": "string", "description": "Filter by project key (optional)" }
    },
    "required": ["issue_key"]
  }
}
```

---

## 7. Processing Logic

### 7.1 BFS Traversal Pseudocode

```
suspend fun getNetwork(centerIssueKey: String, hops: Int): NetworkGraph {
    val visited = mutableSetOf<String>()
    val nodes = mutableListOf<GraphNode>()
    val edges = mutableListOf<GraphEdge>()
    var frontier = listOf(centerIssueKey)

    visited.add(centerIssueKey)
    nodes.add(GraphNode(id = centerIssueKey, label = getLabel(centerIssueKey)))

    for (depth in 1..hops) {
        val nextFrontier = mutableListOf<String>()

        for (nodeKey in frontier) {
            val links = entityLinkRepository.findByIssueKey(nodeKey)

            for (link in links) {
                val neighborKey = if (link.sourceIssueKey == nodeKey)
                    link.targetIssueKey else link.sourceIssueKey

                // Add edge (always, even if node already visited)
                edges.add(GraphEdge(
                    source = link.sourceIssueKey,
                    target = link.targetIssueKey,
                    weight = link.similarityScore,
                    type = link.linkType.name.lowercase()
                ))

                // Add node only if not visited
                if (neighborKey !in visited) {
                    visited.add(neighborKey)
                    nodes.add(GraphNode(id = neighborKey, label = getLabel(neighborKey)))
                    nextFrontier.add(neighborKey)
                }
            }
        }

        frontier = nextFrontier

        // Safety: truncate if too many nodes
        if (nodes.size >= 500) break
    }

    return NetworkGraph(
        nodes = nodes,
        edges = edges.distinct(),
        metadata = GraphMetadata(
            totalNodes = nodes.size,
            totalEdges = edges.size,
            centerNode = centerIssueKey
        )
    )
}
```

### 7.2 Full Network Pseudocode

```
suspend fun getFullNetwork(projectKey: String?, minWeight: Double?): NetworkGraph {
    // Query all links (optionally filtered)
    val allLinks = if (projectKey != null) {
        entityLinkRepository.findByProject(projectKey)
    } else {
        entityLinkRepository.findAll()
    }

    // Apply weight filter
    val filteredLinks = if (minWeight != null) {
        allLinks.filter { it.similarityScore >= minWeight }
    } else {
        allLinks
    }

    // Build node set from all link endpoints
    val nodeKeys = filteredLinks.flatMap { listOf(it.sourceIssueKey, it.targetIssueKey) }.toSet()

    // Truncate if needed
    val truncated = nodeKeys.size > 1000
    val finalNodes = nodeKeys.take(1000).map { GraphNode(id = it, label = getLabel(it)) }

    val edges = filteredLinks.map { link ->
        GraphEdge(link.sourceIssueKey, link.targetIssueKey, link.similarityScore, "semantic")
    }

    return NetworkGraph(
        nodes = finalNodes,
        edges = edges,
        metadata = GraphMetadata(finalNodes.size, edges.size, centerNode = null)
    )
}
```

---

## 8. Non-Functional Requirements

| Category | Requirement | Target |
|----------|-------------|--------|
| Performance | getNetwork (2-hop) latency | < 200ms |
| Performance | getFullNetwork (1000 nodes) latency | < 500ms |
| Scalability | Max nodes in single response | 1000 |
| Scalability | Max hops for BFS | 5 |
| Availability | Graceful handling of empty graph | Return empty NetworkGraph |
| Data Freshness | Graph reflects latest entity_links | Real-time (query on demand) |

---

## 9. Error Handling

| Error Condition | Response | Recovery |
|-----------------|----------|----------|
| Center issue not found | Return empty graph (0 nodes) | No action needed |
| Database unavailable | Throw ServerUnavailableException | Connection pool retry |
| Graph too large (>1000 nodes) | Truncate + set metadata.truncated=true | Inform consumer |
| Invalid hops value (>5) | Clamp to 5, log WARN | Continue with max |
| Invalid project key format | Throw InvalidParamsException | Caller validates |

---

## 10. Open Issues

| # | Issue | Status | Decision Needed By |
|---|-------|--------|-------------------|
| 1 | Should Jira linked tickets be included (requires Jira API call)? | Open — start with entity_links only | PM |
| 2 | Should graph be cached for repeated queries? | Deferred to future iteration | SA |
| 3 | Node labels — where to get ticket titles? (KB or Jira API) | Open | TA |
