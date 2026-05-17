# Functional Specification Document (FSD)

## MTO-131: Bridge Client Local MCP Server Management

---

## 1. Overview

This FSD specifies the functional behavior of the Bridge Client Local MCP Server Management feature. Bridge clients will spawn local MCP servers, receive routing tables from the orchestrator, and intelligently route tool calls between local and remote servers.

---

## 2. API Contracts

### 2.1 Routing Table API (Orchestrator → Bridge)

**Endpoint:** `GET /api/routing-table`

**Response 200:**
```json
{
  "version": "1.0.0",
  "updatedAt": "2026-05-17T12:00:00Z",
  "defaultLocation": "remote",
  "tools": {
    "read_file": { "location": "local", "server": "filesystem-server" },
    "jira_search": { "location": "remote", "server": "atlassian" },
    "embed": { "location": "local", "server": "embedding-server", "fallback": "remote" }
  }
}
```

**Delivery:** Sent as part of MCP `initialize` response `_meta.routingTable` field, or via dedicated endpoint.

### 2.2 Unified tools/list (Bridge → AI Client)

**Request:** Standard MCP `tools/list`

**Response:** Merged list of all local + remote tools. Each tool has standard MCP schema. Source metadata in `_meta` (optional, for debugging).

### 2.3 Routed tools/call (Bridge internal)

**Input:** Standard MCP `tools/call` from AI client
**Routing Logic:**
1. Lookup tool name in routing table
2. If `location === "local"` → forward to local MCP server via stdio
3. If `location === "remote"` → proxy to orchestrator via HTTP
4. If not found → return error

---

## 3. Data Models

### 3.1 RoutingTable
```typescript
interface RoutingTable {
  version: string;
  updatedAt: string; // ISO 8601
  defaultLocation: "local" | "remote";
  tools: Record<string, ToolRoute>;
}

interface ToolRoute {
  location: "local" | "remote";
  server: string;
  fallback?: "local" | "remote";
  priority?: number; // for conflict resolution (higher wins)
}
```

### 3.2 LocalServerConfig (mcp-servers.json)
```typescript
interface McpServersConfig {
  mcpServers: Record<string, ServerEntry>;
}

interface ServerEntry {
  command: string;
  args?: string[];
  env?: Record<string, string>;
  transport?: "stdio" | "sse"; // default: stdio
  timeout?: number; // init timeout ms, default: 30000
  maxRetries?: number; // restart attempts, default: 3
  disabled?: boolean; // skip this server
}
```

### 3.3 EmbeddingRequest/Response
```typescript
// Request (tools/call with name="embed")
interface EmbedArgs {
  texts: string[];
  model?: string; // default: "all-MiniLM-L6-v2"
}

// Response
interface EmbedResult {
  embeddings: number[][]; // shape: [texts.length, 384]
  model: string;
  dimensions: number; // 384
}
```

---

## 4. Sequence Flows

### 4.1 Startup Sequence
1. Bridge reads `mcp-servers.json` from CWD or `~/.mcp-bridge/`
2. Bridge spawns each configured server as child process (stdio)
3. Bridge sends `initialize` to each local server, waits for response (timeout: 30s)
4. Bridge sends `tools/list` to each local server → collects local tools
5. Bridge connects to remote orchestrator (existing HTTP/SSE)
6. Orchestrator sends routing table in `initialize` response `_meta`
7. Bridge receives remote `tools/list` from orchestrator
8. Bridge merges local + remote tools → unified registry ready
9. Bridge accepts AI client connections

### 4.2 Tool Call — Local Path
1. AI client sends `tools/call` with tool name (e.g., "read_file")
2. Bridge looks up routing table → location: "local", server: "filesystem-server"
3. Bridge forwards `tools/call` to local server via stdio
4. Local server processes and returns result
5. Bridge returns result to AI client (identical format)

### 4.3 Tool Call — Remote Path
1. AI client sends `tools/call` with tool name (e.g., "jira_search")
2. Bridge looks up routing table → location: "remote"
3. Bridge proxies request to orchestrator via HTTP POST /mcp
4. Orchestrator processes and returns result
5. Bridge returns result to AI client (identical format)

### 4.4 Embedding Fallback Flow
1. AI client (or bridge internally) needs embedding
2. Check: is embedding MCP server configured in local mcp-servers.json?
3. If YES → call local embedding server
4. If NO → check: is all-MiniLM-L6-v2 model cached locally?
5. If cached → load ONNX model, run inference
6. If not cached → download from HuggingFace (~80MB), cache, then run
7. Return embedding vector (384 dimensions)

---

## 5. Error Handling Matrix

| Scenario | Detection | Response | Recovery |
|----------|-----------|----------|----------|
| Local server fails to start | Process exit code != 0 | Log error, mark server failed | Retry up to maxRetries |
| Local server crashes during use | Process unexpected exit | Mark tools unavailable | Auto-restart, re-register tools |
| Local server timeout on init | No `initialize` response in 30s | Mark server failed | Skip server, continue |
| Remote orchestrator unreachable | HTTP connection refused/timeout | Remote tools unavailable | Retry with backoff |
| Tool not in routing table | Lookup returns null | Return error to AI client | N/A |
| Routing table malformed | JSON parse error | Keep cached version | Log warning |
| Config file invalid | JSON parse error | Keep existing config | Log error, notify |
| Model download fails | HTTP error / timeout | Disable embedding | Retry on next request |
| ONNX Runtime not available | Import/load error | Disable embedding | Log clear error message |

---

## 6. Configuration

### 6.1 Bridge Config (new section)
```yaml
localServers:
  enabled: true
  configPath: "./mcp-servers.json"
  healthCheckIntervalMs: 30000

routing:
  defaultLocation: "remote"
  conflictResolution: "local-first"

embedding:
  fallbackModel: "all-MiniLM-L6-v2"
  modelCachePath: "~/.mcp-bridge/models/"
  autoDownload: true
```

---

## 7. Non-Functional Requirements

| Requirement | Target | Measurement |
|-------------|--------|-------------|
| Local tool call latency | < 50ms | Time from bridge receive to response |
| Embedding inference | < 100ms per text | ONNX Runtime inference time |
| Startup time (5 local servers) | < 10s | Time from bridge start to ready |
| Memory (embedding model loaded) | < 200MB additional | RSS delta |
| Config reload | < 2s | Time from file change to server restart |

---

## 8. Acceptance Criteria Summary

1. Bridge spawns local MCP servers from mcp-servers.json on startup
2. Bridge receives routing table from orchestrator
3. AI client sees unified tools/list (local + remote merged)
4. Local tool calls have zero network traffic to orchestrator
5. Remote tool calls are proxied transparently
6. Embedding works offline with local model fallback
7. All 3 bridge implementations (Node.js, Python, Kotlin) support this feature
8. Cross-platform: Windows, Linux, macOS
