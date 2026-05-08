# Technical Design Document (TDD)

## MCPOrchestration — MTO-20: MCP Tool Integration – Sync & Graph Tools

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-20 |
| Title | MCP Tool Integration – Sync & Graph Tools |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-20.docx |
| Related FSD | FSD-v1-MTO-20.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | SA Agent | Initial TDD |

---

## 1. Introduction

### 1.1 Purpose

Technical design for registering three new MCP tools (jira_project_sync, jira_sync_status, jira_ticket_graph) into the existing MCP Orchestrator tool registry.

### 1.2 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| MCP SDK | io.modelcontextprotocol:kotlin-sdk-server | 0.12.0 |
| Serialization | kotlinx.serialization-json | 1.8.1 |
| DI | Koin | 4.1.1 |
| Database | PostgreSQL + Exposed | 16+ / 0.61.0 |

### 1.3 Design Principles

- **Leverage existing infrastructure** — use McpServerFactory and McpToolRegistrar patterns
- **Thin handler layer** — tools delegate to existing services (ProjectScanner, GraphDataRepository)
- **Schema-first** — JSON Schema defines tool contracts
- **Consistent error handling** — use existing McpOrchestratorException hierarchy

---

## 2. System Architecture

### 2.1 Component Diagram

| Component | Responsibility | Technology |
|-----------|---------------|------------|
| SyncToolRegistrar | Register 3 tools with MCP server | MCP SDK |
| SyncToolHandler | Handle jira_project_sync invocations | Kotlin |
| StatusToolHandler | Handle jira_sync_status invocations | Kotlin |
| GraphToolHandler | Handle jira_ticket_graph invocations | Kotlin |

### 2.2 Integration with Existing Code

The tools integrate with the existing MCP protocol layer:

```
McpServerFactory.createServer()
  → McpToolRegistrar.registerTools()  // existing tools (find_tools, execute_tool)
  → SyncToolRegistrar.registerTools() // NEW — registers 3 sync tools
```

---

## 3. API Design

### 3.1 Tool Registration

```kotlin
class SyncToolRegistrar(
    private val projectScanner: ProjectScanner,
    private val syncStateManager: SyncStateManager,
    private val graphRepository: GraphDataRepository
) {
    fun registerTools(server: Server) {
        server.addTool(
            name = "jira_project_sync",
            description = "Trigger Jira project sync. Starts background sync job for a project. Returns immediately.",
            inputSchema = Tool.Input(
                properties = mapOf(
                    "projectKey" to JsonObject(mapOf("type" to JsonPrimitive("string"), "description" to JsonPrimitive("Jira project key"))),
                    "fullSync" to JsonObject(mapOf("type" to JsonPrimitive("boolean"), "default" to JsonPrimitive(false)))
                ),
                required = listOf("projectKey")
            )
        ) { request ->
            syncToolHandler.handle(request.arguments)
        }
        
        server.addTool(
            name = "jira_sync_status",
            description = "Check sync progress for a Jira project. Returns current status, progress percentage, and phase breakdown.",
            inputSchema = Tool.Input(
                properties = mapOf(
                    "projectKey" to JsonObject(mapOf("type" to JsonPrimitive("string")))
                ),
                required = listOf("projectKey")
            )
        ) { request ->
            statusToolHandler.handle(request.arguments)
        }
        
        server.addTool(
            name = "jira_ticket_graph",
            description = "Query Jira ticket relationship graph. Returns nodes and edges for visualization or analysis.",
            inputSchema = Tool.Input(
                properties = mapOf(
                    "projectKey" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                    "issueKey" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                    "depth" to JsonObject(mapOf("type" to JsonPrimitive("integer"), "default" to JsonPrimitive(2))),
                    "relationshipTypes" to JsonObject(mapOf("type" to JsonPrimitive("array")))
                ),
                required = listOf("projectKey")
            )
        ) { request ->
            graphToolHandler.handle(request.arguments)
        }
    }
}
```

### 3.2 Tool Handlers

```kotlin
class SyncToolHandler(private val projectScanner: ProjectScanner) {
    suspend fun handle(args: JsonObject): CallToolResult {
        val projectKey = args["projectKey"]?.jsonPrimitive?.content
            ?: return errorResult("projectKey is required")
        val fullSync = args["fullSync"]?.jsonPrimitive?.booleanOrNull ?: false
        
        return try {
            // Launch async — don't await completion
            CoroutineScope(Dispatchers.Default).launch {
                projectScanner.scan(projectKey, ScanOptions(forceFullScan = fullSync))
            }
            val state = projectScanner.getProgress(projectKey)
            successResult(mapOf(
                "status" to "started",
                "projectKey" to projectKey,
                "estimatedIssues" to (state?.totalIssues ?: 0)
            ))
        } catch (e: ScanAlreadyRunningException) {
            errorResult("Sync already running for $projectKey")
        }
    }
}

class StatusToolHandler(private val syncStateManager: SyncStateManager) {
    suspend fun handle(args: JsonObject): CallToolResult {
        val projectKey = args["projectKey"]?.jsonPrimitive?.content
            ?: return errorResult("projectKey is required")
        
        val state = syncStateManager.getState(projectKey)
            ?: return successResult(mapOf("projectKey" to projectKey, "status" to "never_synced"))
        
        return successResult(mapOf(
            "projectKey" to projectKey,
            "status" to state.status.name.lowercase(),
            "progress" to state.progress,
            "syncedIssues" to state.syncedIssues,
            "totalIssues" to state.totalIssues,
            "lastSyncTime" to state.lastSyncTime?.toString()
        ))
    }
}

class GraphToolHandler(private val graphRepository: GraphDataRepository) {
    suspend fun handle(args: JsonObject): CallToolResult {
        val projectKey = args["projectKey"]?.jsonPrimitive?.content
            ?: return errorResult("projectKey is required")
        val issueKey = args["issueKey"]?.jsonPrimitive?.contentOrNull
        val depth = args["depth"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 5) ?: 2
        
        val (nodes, edges) = if (issueKey != null) {
            graphRepository.bfsTraversal(issueKey, depth)
        } else {
            graphRepository.getFullGraph(projectKey)
        }
        
        // Node response includes labels and createdAt (MTO-18 alignment)
        return successResult(mapOf(
            "nodes" to nodes.take(1000).map { it.toSimpleMap() },
            "edges" to edges.map { it.toSimpleMap() },
            "metadata" to mapOf(
                "totalNodes" to nodes.size,
                "totalEdges" to edges.size,
                "projectKey" to projectKey,
                "centerIssue" to issueKey,
                "depth" to depth
            )
        ))
    }
}

// Node response schema (MTO-18 alignment):
// {
//   "key": "MTO-15",
//   "summary": "Database Schema...",
//   "status": "In Progress",
//   "issueType": "Story",
//   "labels": ["backend", "database"],
//   "createdAt": "2026-05-06T12:29:14Z"
// }
```

---

## 4. Database Design

No new tables. Uses existing:
- `jira_sync_state` — for status queries
- `jira_ticket_cache` — for node data
- `jira_ticket_graph` — for edge data

---

## 5. Class / Module Design

### 5.1 Package Structure

```
com.orchestrator.mcp/
└── synctools/
    ├── SyncToolRegistrar.kt           # Register tools with MCP server
    ├── SyncToolHandler.kt             # jira_project_sync handler
    ├── StatusToolHandler.kt           # jira_sync_status handler
    ├── GraphToolHandler.kt            # jira_ticket_graph handler
    └── di/
        └── SyncToolsModule.kt         # Koin DI
```

### 5.2 Koin Module

```kotlin
val syncToolsModule = module {
    single { SyncToolHandler(get()) }
    single { StatusToolHandler(get()) }
    single { GraphToolHandler(get()) }
    single { SyncToolRegistrar(get(), get(), get()) }
}
```

---

## 6. Auto-Approve Configuration

```yaml
orchestrator:
  tools:
    autoApprove:
      - jira_sync_status      # Read-only, safe
      - jira_ticket_graph     # Read-only, safe
      # jira_project_sync NOT listed — requires approval
```

Implementation in existing config:

```kotlin
// In OrchestratorConfig
data class ToolsConfig(
    val autoApprove: List<String> = emptyList()
)
```

---

## 7. Security Design

| Tool | Approval | Rationale |
|------|----------|-----------|
| jira_project_sync | Required | Write operation, triggers background job |
| jira_sync_status | Auto-approve | Read-only, no side effects |
| jira_ticket_graph | Auto-approve | Read-only, no side effects |

---

## 8. Performance

| Tool | Target Response Time |
|------|---------------------|
| jira_project_sync | < 200ms (async start) |
| jira_sync_status | < 100ms (DB query) |
| jira_ticket_graph (full, 500 nodes) | < 500ms |
| jira_ticket_graph (subgraph, depth 2) | < 200ms |

---

## 9. Implementation Checklist

| # | Task | File | Priority |
|---|------|------|----------|
| 1 | SyncToolHandler | synctools/SyncToolHandler.kt | High |
| 2 | StatusToolHandler | synctools/StatusToolHandler.kt | High |
| 3 | GraphToolHandler | synctools/GraphToolHandler.kt | High |
| 4 | SyncToolRegistrar | synctools/SyncToolRegistrar.kt | High |
| 5 | SyncToolsModule (Koin) | synctools/di/SyncToolsModule.kt | High |
| 6 | Register in McpServerFactory | protocol/McpServerFactory.kt | High |
| 7 | Add autoApprove config | config/OrchestratorConfig.kt | Medium |
| 8 | Unit tests | test/.../synctools/*.kt | High |
| 9 | Integration test | test/.../synctools/it/*.kt | Medium |

---

## 10. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Architecture | [architecture.png](diagrams/architecture.png) | [architecture.drawio](diagrams/architecture.drawio) |
| 2 | Component | [component.png](diagrams/component.png) | [component.drawio](diagrams/component.drawio) |
| 3 | Sequence - Tool Invocation | [api-sequence-tool.png](diagrams/api-sequence-tool.png) | [api-sequence-tool.drawio](diagrams/api-sequence-tool.drawio) |
| 4 | Class Diagram | [class-diagram.png](diagrams/class-diagram.png) | [class-diagram.drawio](diagrams/class-diagram.drawio) |
