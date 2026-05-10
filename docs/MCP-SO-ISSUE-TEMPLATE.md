# GitHub Issue Content for mcp.so Submission
# Post at: https://github.com/chatmcp/mcpso/issues/new

## Title:
[Server] MCP Orchestrator — Multi-server aggregator with semantic tool discovery

## Body (copy below):

---

### Server Information

| Field | Value |
|-------|-------|
| **Name** | MCP Orchestrator |
| **GitHub** | https://github.com/dnguyenminh/MCPOrchestration |
| **Category** | Developer Tools |
| **Tags** | orchestrator, tool-discovery, multi-server, semantic-search, bridge |

### Description

MCP Orchestrator is a smart gateway that aggregates multiple upstream MCP servers into a single endpoint. AI agents discover tools by natural language using vector embeddings — no hardcoded tool names needed.

**Key value:** Instead of loading 80+ tool definitions into agent context (thousands of tokens), the orchestrator exposes only 2 tools. Agents discover what they need on-demand, saving massive tokens per request.

### Features

- 🔍 Semantic tool discovery (pgvector/Qdrant + Ollama/OpenAI embeddings)
- 🔀 Multi-server aggregation (N servers → 1 endpoint)
- ⚡ Dynamic execution routing (auto-routes to correct upstream server)
- 🏥 Health monitoring (30s ping, auto-reconnect with exponential backoff)
- 🌐 5 bridge clients (Node.js, Python, Bash, PowerShell, CMD)
- 📁 Transparent file proxy (saves 100% token cost for large files)
- 🔒 Per-session tool management (enable/disable/auto-approve)
- 📊 Structured agent logging for multi-agent pipelines

### Server Config

**Recommended (Node.js — auto-downloads via npx):**
```json
{
  "command": "npx",
  "args": ["mcp-orchestrator-bridge", "--url", "http://localhost:9180"]
}
```

### Alternative Configs

**Python (auto-downloads via uvx):**
```json
{
  "command": "uvx",
  "args": ["mcp-orchestrator-bridge", "--url", "http://localhost:9180"]
}
```

**Bash (download once: `curl -sL .../mcp-bridge.sh -o ~/.local/bin/mcp-bridge.sh`):**
```json
{
  "command": "bash",
  "args": ["~/.local/bin/mcp-bridge.sh", "--url", "http://localhost:9180"]
}
```

**PowerShell (download once: `Invoke-WebRequest .../mcp-bridge.ps1 -OutFile ~/mcp-bridge.ps1`):**
```json
{
  "command": "pwsh",
  "args": ["-NoProfile", "-File", "~/mcp-bridge.ps1", "-Url", "http://localhost:9180"]
}
```

### Tools Provided

| Tool | Description |
|------|-------------|
| `find_tools` | Semantic search — describe what you need in plain English, get matching tools with confidence scores |
| `execute_dynamic_tool` | Run any discovered tool — orchestrator routes to the correct upstream server automatically |
| `toggle_tool` | Disable noisy tools for current session |
| `reset_tools` | Restore all tools to default state |
| `manage_auto_approve` | Trust tools so IDE stops asking for confirmation |
| `agent_log` | Structured logging for multi-agent workflows |
| `stream_write_file` | Write files locally (zero network, zero tokens) |
| `embed_images` | Embed local images as base64 in markdown |

### Prerequisites

- Java 21+ (server)
- PostgreSQL + pgvector OR Qdrant (vector DB)
- Ollama OR OpenAI API key (embeddings)
- Node.js 20+ / Python 3.10+ / Bash / PowerShell (bridge client)

### Screenshots

See README for architecture diagrams: https://github.com/dnguyenminh/MCPOrchestration#-architecture
