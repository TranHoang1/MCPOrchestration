# User Guide (UG)

## MCP Orchestration Server — MTO-5: Create MCP Tool Orchestration

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-5 |
| Title | Create MCP Tool Orchestration |
| Author | DEV Agent |
| Reviewer | BA Agent |
| Version | 1.0 |
| Date | 2026-05-03 |
| Status | Draft |
| Related BRD | BRD-v2-MTO-5.docx |
| Related FSD | FSD-v1-MTO-5.docx |
| Related TDD | TDD-v1-MTO-5.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-03 | DEV Agent | Initial document |

---

## 1. Introduction

### 1.1 Purpose

The **MCP Orchestration Server** is a Kotlin/Ktor application that acts as an intelligent proxy between the Kiro AI IDE and multiple upstream MCP (Model Context Protocol) Servers. Instead of loading all tool definitions into the AI's context window, the Orchestrator exposes exactly **two tools** — `find_tools` and `execute_dynamic_tool` — enabling semantic search and dynamic proxying to potentially hundreds of upstream tools.

This guide covers installation, configuration, usage, administration, and troubleshooting for system administrators, AI agent developers, and DevOps engineers.

### 1.2 Audience

| Audience | What They Need |
|----------|---------------|
| AI Agent (Kiro IDE) | How to discover and execute tools via the 2 exposed MCP tools |
| System Administrator | How to configure upstream servers, manage health, and tune performance |
| Developer | How to extend the system, add new upstream servers, or integrate with custom MCP servers |

### 1.3 Prerequisites

| Prerequisite | Version | Required |
|-------------|---------|----------|
| JDK (Java Development Kit) | 21+ | Yes |
| Gradle | 8.x (included via wrapper) | Yes |
| Qdrant (Vector Database) | 1.9+ | Optional (FAISS fallback available) |
| OpenAI API Key | — | Optional (keyword fallback available) |
| Docker | 20+ | Optional (for Qdrant container) |

---

## 2. Installation

### 2.1 Quick Start

```bash
# Step 1: Clone the repository
git clone <repository-url>
cd mcp-orchestrator

# Step 2: Build the fat JAR
./gradlew buildFatJar

# Step 3: Run the server (stdio mode — default)
java -jar build/libs/mcp-orchestrator-all.jar
```

### 2.2 System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| CPU | 2 cores | 4 cores |
| Memory | 512 MB heap | 1 GB heap |
| Disk | 100 MB (application) | 500 MB (with FAISS index) |
| OS | Any OS with JDK 21+ | Linux / macOS / Windows |
| Network | Localhost only (stdio) | Localhost + outbound HTTPS (for OpenAI API) |

### 2.3 Build from Source

```bash
# Full build with tests
./gradlew build

# Build fat JAR only (skip tests)
./gradlew buildFatJar -x test

# Run tests only
./gradlew test
```

**Build output:** `build/libs/mcp-orchestrator-all.jar`

### 2.4 Distribution Formats

| Format | Location | Use Case |
|--------|----------|----------|
| Fat JAR | `build/libs/mcp-orchestrator-all.jar` | Production deployment, Kiro IDE integration |
| Gradle run | `./gradlew run` | Development and debugging |

---

## 3. Configuration

### 3.1 Configuration File

The primary configuration file is `src/main/resources/application.yml` (bundled in the JAR). Override values using environment variables with the `${ENV_VAR}` syntax.

### 3.2 Configuration Reference

#### 3.2.1 Server Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `orchestrator.server.port` | Integer | `8080` | HTTP server port (used in HTTP transport mode) |
| `orchestrator.server.transport` | String | `"stdio"` | Transport mode: `"stdio"` or `"http"` |

#### 3.2.2 Discovery Settings (find_tools)

| Property | Type | Default | Range | Description |
|----------|------|---------|-------|-------------|
| `orchestrator.discovery.top_k` | Integer | `5` | 1–20 | Maximum number of results returned by `find_tools` |
| `orchestrator.discovery.similarity_threshold` | Float | `0.7` | 0.0–1.0 | Minimum cosine similarity score for results |
| `orchestrator.discovery.max_query_length` | Integer | `2000` | ≥1 | Maximum allowed query string length |
| `orchestrator.discovery.fallback_to_keyword` | Boolean | `true` | — | Enable keyword search fallback when Vector DB or Embedding service is unavailable |

#### 3.2.3 Execution Settings (execute_dynamic_tool)

| Property | Type | Default | Range | Description |
|----------|------|---------|-------|-------------|
| `orchestrator.execution.timeout_seconds` | Integer | `30` | 5–300 | Timeout for upstream tool execution |
| `orchestrator.execution.validate_arguments` | Boolean | `true` | — | Validate tool arguments against input_schema before forwarding |
| `orchestrator.execution.max_retries` | Integer | `1` | ≥0 | Maximum retry attempts for failed executions |

#### 3.2.4 Embedding Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `orchestrator.embedding.provider` | String | `"openai"` | Embedding provider: `"openai"` |
| `orchestrator.embedding.model` | String | `"text-embedding-3-small"` | OpenAI embedding model name |
| `orchestrator.embedding.api_key` | String | `""` | OpenAI API key (use `${OPENAI_API_KEY}` for env var) |
| `orchestrator.embedding.dimensions` | Integer | `768` | Vector embedding dimensions |
| `orchestrator.embedding.cache_enabled` | Boolean | `true` | Enable in-memory embedding cache |
| `orchestrator.embedding.cache_max_size` | Integer | `100` | Maximum cached embeddings |
| `orchestrator.embedding.cache_ttl_minutes` | Integer | `5` | Cache entry time-to-live in minutes |

#### 3.2.5 Vector Database Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `orchestrator.vector_db.provider` | String | `"qdrant"` | Vector DB provider: `"qdrant"` or `"faiss"` |
| `orchestrator.vector_db.host` | String | `"localhost"` | Qdrant server hostname |
| `orchestrator.vector_db.port` | Integer | `6333` | Qdrant server port |
| `orchestrator.vector_db.collection_name` | String | `"mcp_tools"` | Qdrant collection name for tool vectors |

#### 3.2.6 Health Monitoring Settings

| Property | Type | Default | Range | Description |
|----------|------|---------|-------|-------------|
| `orchestrator.health.check_interval_seconds` | Integer | `30` | ≥1 | Interval between health checks for upstream servers |
| `orchestrator.health.auto_reconnect` | Boolean | `true` | — | Automatically reconnect to disconnected servers |
| `orchestrator.health.max_reconnect_attempts` | Integer | `5` | ≥0 | Maximum reconnection attempts before marking server as ERROR |

#### 3.2.7 Upstream Server Configuration

Each upstream server is defined as an entry in the `orchestrator.upstream_servers` list:

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `name` | String | Yes | Unique server identifier |
| `transport` | String | Yes | `"stdio"` or `"http"` |
| `command` | String | stdio only | Command to launch the MCP server process |
| `args` | List\<String\> | No | Command-line arguments |
| `env` | Map\<String, String\> | No | Environment variables for the process |
| `url` | String | http only | HTTP endpoint URL for the MCP server |

### 3.3 Environment Variables

| Variable | Description | Required | Example |
|----------|-------------|----------|---------|
| `OPENAI_API_KEY` | OpenAI API key for embedding generation | No (keyword fallback if missing) | `sk-proj-abc123...` |
| `JIRA_TOKEN` | Jira API token (for Jira MCP upstream) | No (per upstream config) | `ATATT3xFfGF0...` |
| `JIRA_URL` | Jira instance URL | No (per upstream config) | `https://mycompany.atlassian.net` |

### 3.4 Configuration Examples

#### Minimal Configuration (keyword search only, no external services)

```yaml
orchestrator:
  server:
    transport: stdio
  discovery:
    fallback_to_keyword: true
  upstream_servers: []
```

#### Full Configuration (with Qdrant + OpenAI + upstream servers)

```yaml
orchestrator:
  server:
    port: 8080
    transport: stdio

  discovery:
    top_k: 5
    similarity_threshold: 0.7
    max_query_length: 2000
    fallback_to_keyword: true

  execution:
    timeout_seconds: 30
    validate_arguments: true
    max_retries: 1

  embedding:
    provider: openai
    model: text-embedding-3-small
    api_key: ${OPENAI_API_KEY}
    dimensions: 768
    cache_enabled: true
    cache_max_size: 100
    cache_ttl_minutes: 5

  vector_db:
    provider: qdrant
    host: localhost
    port: 6333
    collection_name: mcp_tools

  health:
    check_interval_seconds: 30
    auto_reconnect: true
    max_reconnect_attempts: 5

  upstream_servers:
    - name: jira-server
      transport: stdio
      command: npx
      args: ["-y", "@modelcontextprotocol/server-jira"]
      env:
        JIRA_URL: "https://mycompany.atlassian.net"
        JIRA_TOKEN: ${JIRA_TOKEN}

    - name: git-server
      transport: http
      url: "http://localhost:3001/mcp"

    - name: database-server
      transport: stdio
      command: python
      args: ["-m", "mcp_server_database"]
```

---

## 4. Usage

### 4.1 Tool Discovery — `find_tools`

**Description:** Search for available MCP tools by describing what you want to accomplish in natural language. Returns tool definitions with full input schemas so you can call them via `execute_dynamic_tool`.

**MCP JSON-RPC Request:**

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "find_tools",
    "arguments": {
      "query": "check logs and create Jira ticket",
      "top_k": 5,
      "threshold": 0.7
    }
  }
}
```

**Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `query` | String | Yes | — | Natural language description of the desired action (max 2000 chars) |
| `top_k` | Integer | No | 5 | Maximum number of results (1–20) |
| `threshold` | Float | No | 0.7 | Minimum similarity score (0.0–1.0) |

**Example — Successful Response:**

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"tools\":[{\"name\":\"read_logs\",\"description\":\"Read application log files\",\"input_schema\":{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"lines\":{\"type\":\"integer\",\"default\":100}}},\"server_name\":\"log-server\",\"server_status\":\"CONNECTED\",\"similarity_score\":0.92},{\"name\":\"create_jira_issue\",\"description\":\"Create a new Jira issue\",\"input_schema\":{\"type\":\"object\",\"properties\":{\"project_key\":{\"type\":\"string\"},\"summary\":{\"type\":\"string\"},\"issue_type\":{\"type\":\"string\"}}},\"server_name\":\"jira-server\",\"server_status\":\"CONNECTED\",\"similarity_score\":0.87}],\"search_mode\":\"semantic\",\"total_indexed\":150}"
      }
    ]
  }
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `tools[].name` | String | Tool name (use this in `execute_dynamic_tool`) |
| `tools[].description` | String | Human-readable tool description |
| `tools[].input_schema` | Object | JSON Schema defining the tool's input parameters |
| `tools[].server_name` | String | Upstream MCP server hosting this tool |
| `tools[].server_status` | String | `CONNECTED`, `DISCONNECTED`, or `ERROR` |
| `tools[].similarity_score` | Float | Cosine similarity score (0.0–1.0) |
| `search_mode` | String | `"semantic"` (normal) or `"keyword"` (fallback) |
| `total_indexed` | Integer | Total number of tools in the index |

### 4.2 Tool Execution — `execute_dynamic_tool`

**Description:** Execute a discovered tool on its upstream MCP server. Use `find_tools` first to discover available tools and their input schemas.

**MCP JSON-RPC Request:**

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "execute_dynamic_tool",
    "arguments": {
      "tool_name": "read_logs",
      "arguments": {
        "path": "/var/log/app.log",
        "lines": 50
      }
    }
  }
}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `tool_name` | String | Yes | Exact tool name as returned by `find_tools` |
| `arguments` | Object | No | Arguments conforming to the tool's `input_schema` |

**Example — Successful Response:**

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "2026-05-01 10:30:00 INFO Application started\n2026-05-01 10:30:01 INFO Processing request..."
      }
    ],
    "_meta": {
      "upstream_server": "log-server",
      "execution_time_ms": 145
    }
  }
}
```

### 4.3 MCP Protocol Handshake — `initialize`

Before using tools, the MCP client (Kiro IDE) must perform the protocol handshake:

```json
{
  "jsonrpc": "2.0",
  "id": 0,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": { "name": "kiro", "version": "1.0.0" }
  }
}
```

**Response:**

```json
{
  "jsonrpc": "2.0",
  "id": 0,
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": { "tools": {} },
    "serverInfo": { "name": "mcp-orchestrator", "version": "1.0.0" }
  }
}
```

### 4.4 List Available Tools — `tools/list`

Returns the 2 registered MCP tools with their full schemas:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list"
}
```

### 4.5 Typical Workflow

1. **Initialize** — Kiro IDE sends `initialize` to establish MCP session
2. **Discover** — AI agent calls `find_tools("check logs and create Jira ticket")`
3. **Review** — AI agent receives tool schemas for `read_logs` and `create_jira_issue`
4. **Execute** — AI agent calls `execute_dynamic_tool("read_logs", {path: "/var/log/app.log"})`
5. **Process** — Orchestrator proxies the request to the log-server and returns the result
6. **Continue** — AI agent calls `execute_dynamic_tool("create_jira_issue", {project_key: "PROJ", summary: "Error found in logs"})` based on log analysis

---

## 6. Administration

### 6.1 Adding a New Upstream MCP Server

**Steps:**

1. Edit `application.yml` and add a new entry under `upstream_servers`:

```yaml
upstream_servers:
  - name: my-new-server
    transport: stdio
    command: npx
    args: ["-y", "my-mcp-server-package"]
    env:
      API_KEY: ${MY_SERVER_API_KEY}
```

2. Restart the Orchestrator (or trigger hot-reload if supported).
3. The server will automatically:
   - Connect to the new upstream server
   - Call `tools/list` to discover its tools
   - Generate embeddings for each tool description
   - Index tools in the Vector Database
4. Verify by calling `find_tools` with a query matching the new server's tools.

### 6.2 Monitoring Server Health

The Health Monitor runs automatically at the configured interval (default: 30 seconds). It:

- Sends `ping` requests to all `CONNECTED` servers
- Transitions servers to `DISCONNECTED` if ping fails
- Auto-reconnects `DISCONNECTED` servers with exponential backoff
- Marks servers as `ERROR` after exceeding `max_reconnect_attempts`

**Server State Machine:**

```
STARTING → CONNECTED (connection successful)
STARTING → ERROR (connection failed)
CONNECTED → DISCONNECTED (health check failed)
DISCONNECTED → STARTING (auto-reconnect triggered)
DISCONNECTED → ERROR (max reconnect attempts exceeded)
ERROR → STARTING (manual retry / config reload)
```

**Monitoring via logs:**

```
# Successful health check
INFO  HealthMonitor - Health check OK: jira-server

# Server disconnected
WARN  HealthMonitor - Health check failed for jira-server: Connection refused
INFO  HealthMonitor - Server state transition: jira-server CONNECTED → DISCONNECTED

# Auto-reconnect
INFO  HealthMonitor - Reconnecting to jira-server (attempt 1, backoff=1000ms)
INFO  HealthMonitor - Server state transition: jira-server DISCONNECTED → CONNECTED

# Max attempts exceeded
INFO  HealthMonitor - Server state transition: jira-server DISCONNECTED → ERROR (max attempts)
```

### 6.3 Hot-Reload Configuration

The `ConfigurationManager` supports hot-reload:

1. Modify `application.yml` while the server is running
2. Call `reload()` programmatically (or restart the server)
3. The system will:
   - Parse and validate the new configuration
   - Reject invalid configs (keeps previous valid config)
   - Connect to newly added servers
   - Disconnect from removed servers
   - Re-index tools for changed servers
   - Apply new settings (Top-K, thresholds, timeouts)

**Log output on reload:**

```
INFO  ConfigurationManagerImpl - Configuration reloaded successfully
```

### 6.4 Starting Qdrant (Vector Database)

If using Qdrant for semantic search:

```bash
# Option 1: Docker
docker run -p 6333:6333 -p 6334:6334 qdrant/qdrant:latest

# Option 2: Docker Compose
docker compose up -d qdrant
```

If Qdrant is not available, the system automatically falls back to keyword-based search.

---

## 7. Troubleshooting

### 7.1 Common Issues

| # | Symptom | Cause | Solution |
|---|---------|-------|----------|
| 1 | `find_tools` returns empty results | No tools indexed, or query doesn't match any tool descriptions | Verify upstream servers are connected and tools are indexed. Try broader queries. |
| 2 | `find_tools` returns `search_mode: "keyword"` | Vector DB or Embedding service unavailable | Check Qdrant is running (`localhost:6333`). Check `OPENAI_API_KEY` is set. |
| 3 | `execute_dynamic_tool` returns `TOOL_NOT_FOUND` | Tool name doesn't match any registered tool | Use `find_tools` first to discover exact tool names. Names are case-sensitive. |
| 4 | `execute_dynamic_tool` returns `SERVER_UNAVAILABLE` | Upstream server is disconnected or in error state | Check server health in logs. Verify the upstream server process is running. |
| 5 | `execute_dynamic_tool` returns `EXECUTION_TIMEOUT` | Upstream server took longer than configured timeout | Increase `execution.timeout_seconds` or investigate upstream server performance. |
| 6 | Server fails to start | Invalid `application.yml` configuration | Check logs for `ConfigException`. Validate YAML syntax and required fields. |
| 7 | Upstream server shows `ERROR` state | Connection failed after max reconnect attempts | Check upstream server command/URL. Restart the Orchestrator to retry. |
| 8 | `OPENAI_API_KEY` not found | Environment variable not set | Set `export OPENAI_API_KEY=sk-...` before starting the server. |
| 9 | Qdrant connection refused | Qdrant not running or wrong port | Start Qdrant: `docker run -p 6333:6333 qdrant/qdrant`. Check `vector_db.host` and `vector_db.port`. |
| 10 | Slow `find_tools` responses | Large number of tools or cold embedding cache | Enable `cache_enabled: true`. Reduce `top_k`. Check network latency to OpenAI API. |

### 7.2 Error Codes

| Code | Message | Description | Action |
|------|---------|-------------|--------|
| `INVALID_PARAMS` | Query parameter is required and must be non-empty | Empty or null query in `find_tools` | Provide a non-empty query string |
| `INVALID_PARAMS` | Query exceeds maximum length of 2000 characters | Query too long | Shorten the query to ≤2000 characters |
| `INVALID_PARAMS` | Argument validation failed: {details} | Tool arguments don't match input_schema | Check the tool's `input_schema` from `find_tools` results |
| `TOOL_NOT_FOUND` | Tool '{name}' is not registered | Tool name not in registry | Use `find_tools` to discover available tool names |
| `SERVER_UNAVAILABLE` | Server hosting '{name}' is currently unavailable | Upstream server disconnected | Wait for auto-reconnect or restart the upstream server |
| `EXECUTION_TIMEOUT` | Tool execution timed out after {N}s | Upstream didn't respond in time | Increase `execution.timeout_seconds` or check upstream server |
| `UPSTREAM_ERROR` | Upstream error: {message} | Error from the upstream MCP server | Check the upstream server logs for details |
| `VECTOR_DB_UNAVAILABLE` | Vector DB is unavailable, using keyword fallback | Qdrant not reachable | Start Qdrant or accept keyword fallback mode |
| `EMBEDDING_SERVICE_ERROR` | Embedding service unavailable | OpenAI API unreachable or key invalid | Check `OPENAI_API_KEY` and network connectivity |
| `CONFIG_INVALID` | Configuration validation failed: {details} | Invalid configuration values | Fix the reported validation errors in `application.yml` |
| `INTERNAL_ERROR` | Tool discovery failed. Please retry. | Unrecoverable internal error | Check server logs for stack trace. Restart if needed. |

### 7.3 Logs

| Log Location | Content | Useful For |
|-------------|---------|------------|
| stdout/stderr | All application logs (Logback) | General debugging, health check status |
| `HealthMonitor` logger | Server state transitions, reconnect attempts | Diagnosing upstream connectivity issues |
| `ToolDiscoveryServiceImpl` logger | Search queries, result counts, search mode | Debugging search quality and fallback behavior |
| `ToolExecutionDispatcherImpl` logger | Tool executions, durations, success/failure | Performance monitoring and error tracking |
| `ConfigurationManagerImpl` logger | Config load/reload events | Configuration troubleshooting |
| `ToolIndexer` logger | Indexing operations, tool counts per server | Verifying tool registration |

**Log levels:**
- `ERROR` — Failures requiring attention (connection failures, unrecoverable errors)
- `WARN` — Degraded operation (fallback to keyword search, health check failures)
- `INFO` — Business events (tool discovery, execution, state transitions, config reload)
- `DEBUG` — Technical details (individual health checks, cache hits)

### 7.4 FAQ

**Q: Can I run the Orchestrator without Qdrant or OpenAI?**
A: Yes. Set `discovery.fallback_to_keyword: true` (default). The system will use keyword-based search over cached tool definitions. Semantic search quality will be lower, but the system remains fully functional.

**Q: How many upstream tools can the system handle?**
A: The system is designed for 1000+ tools across 50+ servers. Qdrant HNSW index provides sub-10ms search latency for up to 10,000 vectors.

**Q: What happens if an upstream server goes down?**
A: The Health Monitor detects the failure within one check interval (default: 30s), marks the server as `DISCONNECTED`, and begins auto-reconnect with exponential backoff. Tools from that server remain in the search index but are flagged with `server_status: "DISCONNECTED"`.

**Q: Can I use a different embedding model?**
A: Currently, only OpenAI's `text-embedding-3-small` is supported. The `dimensions` config must match the model's output (768 for `text-embedding-3-small`). Support for additional providers can be added by implementing the `EmbeddingService` interface.

**Q: How does the fallback mechanism work?**
A: If the Embedding service fails → keyword search. If the Vector DB fails → keyword search. If both are available → semantic search. The `search_mode` field in the response indicates which mode was used.

**Q: Is the Orchestrator stateless?**
A: The Orchestrator maintains in-memory state (ToolRegistry, server connections) that is rebuilt on startup from upstream servers. The Vector DB (Qdrant) provides persistent storage for tool embeddings. If Qdrant is unavailable, the system operates in degraded mode with keyword search only.

---

## 8. API Reference

### 8.1 `find_tools`

| Attribute | Value |
|-----------|-------|
| Name | `find_tools` |
| Protocol | MCP JSON-RPC 2.0 |
| Transport | stdio / HTTP |
| Auth | None (local transport) |

**Input Schema:**

```json
{
  "type": "object",
  "properties": {
    "query": {
      "type": "string",
      "description": "Natural language description of the action you want to perform",
      "maxLength": 2000
    },
    "top_k": {
      "type": "integer",
      "description": "Maximum number of results to return (default: 5)",
      "default": 5,
      "minimum": 1,
      "maximum": 20
    },
    "threshold": {
      "type": "number",
      "description": "Minimum similarity score threshold (default: 0.7)",
      "default": 0.7,
      "minimum": 0.0,
      "maximum": 1.0
    }
  },
  "required": ["query"]
}
```

### 8.2 `execute_dynamic_tool`

| Attribute | Value |
|-----------|-------|
| Name | `execute_dynamic_tool` |
| Protocol | MCP JSON-RPC 2.0 |
| Transport | stdio / HTTP |
| Auth | None (local transport) |

**Input Schema:**

```json
{
  "type": "object",
  "properties": {
    "tool_name": {
      "type": "string",
      "description": "The exact name of the tool to execute (as returned by find_tools)"
    },
    "arguments": {
      "type": "object",
      "description": "Arguments to pass to the tool, conforming to its input_schema",
      "additionalProperties": true
    }
  },
  "required": ["tool_name"]
}
```

### 8.3 MCP Protocol Methods

| Method | Description | Direction |
|--------|-------------|-----------|
| `initialize` | MCP session handshake | Client → Server |
| `tools/list` | List the 2 registered tools with schemas | Client → Server |
| `tools/call` | Execute `find_tools` or `execute_dynamic_tool` | Client → Server |
| `ping` | Health check | Client → Server |

---

## 9. Appendix

### 9.1 Glossary

| Term | Definition |
|------|------------|
| MCP | Model Context Protocol — open standard for AI tool communication |
| Orchestrator | The MCP Orchestration Server (this application) |
| Upstream Server | An external MCP Server that hosts actual tools (e.g., Jira MCP, Git MCP) |
| Tool Definition | Metadata describing a tool: name, description, input_schema |
| Vector DB | Vector Database used for semantic similarity search (Qdrant or FAISS) |
| Embedding | Dense vector representation of text, used for semantic search |
| Top-K | The K most similar results returned from a vector search |
| JSON-RPC | JSON Remote Procedure Call — the wire protocol used by MCP |
| Context Window | The limited token budget available to an AI model per request |
| HNSW | Hierarchical Navigable Small World — graph-based ANN index algorithm |
| ANN | Approximate Nearest Neighbor — fast similarity search technique |
| FAISS | Facebook AI Similarity Search — local vector search library |
| Hot-Reload | Ability to update configuration without restarting the server |

### 9.2 Related Documents

| Document | Location |
|----------|----------|
| BRD | BRD-v2-MTO-5.docx |
| FSD | FSD-v1-MTO-5.docx |
| TDD | TDD-v1-MTO-5.docx |
| STP | STP-v1-MTO-5.docx |
| STC | STC-v1-MTO-5.xlsx |

### 9.3 Version Compatibility

| System Version | Config Version | Breaking Changes |
|---------------|---------------|-----------------|
| 1.0.0 | v1 | Initial release |
