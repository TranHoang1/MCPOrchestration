# Technical Design Document (TDD)

## MCPOrchestration — MTO-36: KB Refinery — Feature Network Mapping

---

## 1. Architecture Overview

### 1.1 Package Structure

```
com.orchestrator.mcp.network/
├── NetworkService.kt                 (interface)
├── NetworkServiceImpl.kt             (implementation)
├── model/
│   ├── NetworkGraph.kt               (graph data class)
│   ├── GraphNode.kt                  (node data class)
│   ├── GraphEdge.kt                  (edge data class)
│   └── NetworkConfig.kt              (configuration)
└── di/
    └── NetworkModule.kt              (Koin module)
```

### 1.2 Design

The NetworkService combines data from:
1. `EntityLinkRepository` (semantic links from MTO-35)
2. Jira linked tickets (if available)

It builds a graph structure suitable for frontend visualization (D3.js, Cytoscape.js).

---

## 2. Detailed Design

### 2.1 NetworkService Interface

```kotlin
interface NetworkService {
    suspend fun getNetwork(centerIssueKey: String, hops: Int = 2): NetworkGraph
    suspend fun getFullNetwork(projectKey: String? = null): NetworkGraph
}
```

### 2.2 NetworkGraph Model

```kotlin
data class NetworkGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val metadata: GraphMetadata
)

data class GraphNode(
    val id: String,
    val label: String,
    val type: String = "ticket",
    val properties: Map<String, String> = emptyMap()
)

data class GraphEdge(
    val source: String,
    val target: String,
    val weight: Double,
    val type: String = "semantic"
)

data class GraphMetadata(
    val totalNodes: Int,
    val totalEdges: Int,
    val centerNode: String?
)
```

---

## 3. Implementation Checklist

| # | File | Lines (est.) |
|---|------|-------------|
| 1 | NetworkService.kt | ~12 |
| 2 | NetworkServiceImpl.kt | ~70 |
| 3 | NetworkGraph.kt | ~20 |
| 4 | GraphNode.kt | ~12 |
| 5 | GraphEdge.kt | ~12 |
| 6 | NetworkConfig.kt | ~10 |
| 7 | NetworkModule.kt | ~15 |

