# MCP.so Submission Guide

## Submission Target
- **URL:** https://mcp.so/submit
- **Alternative:** GitHub Issue on https://github.com/chatmcp/mcpso

---

## Server Information

| Field | Value |
|-------|-------|
| **Name** | MCP Orchestrator |
| **Description** | Multi-server MCP orchestrator with semantic tool discovery, dynamic execution, health monitoring, and multi-language bridge clients (Node.js, Python, Bash, PowerShell, CMD) |
| **GitHub URL** | https://github.com/dnguyenminh/MCPOrchestration |
| **Category** | Developer Tools / AI Infrastructure |
| **Tags** | mcp, orchestrator, tool-discovery, multi-server, bridge, kotlin, ktor |
| **Author** | Duc Nguyen |
| **License** | MIT |

---

## Short Description (for listing)

MCP Orchestrator aggregates multiple upstream MCP servers into a single endpoint with semantic tool discovery (pgvector), dynamic execution routing, health monitoring, and auto-reconnect. Supports 5 bridge client languages.

---

## Features (for listing page)

- 🔍 **Semantic Tool Discovery** — Find tools by natural language description (pgvector + Ollama embeddings)
- 🔀 **Multi-Server Aggregation** — Connect multiple upstream MCP servers, expose all tools through one endpoint
- ⚡ **Dynamic Execution** — Route tool calls to correct upstream server automatically
- 🏥 **Health Monitoring** — Periodic ping, auto-reconnect with exponential backoff
- 🌐 **Multi-Language Bridges** — Node.js, Python, Bash, PowerShell, CMD clients
- 📁 **File Proxy** — Transparent file transfer for tools that accept file content
- 🔒 **Tool Management** — Enable/disable tools per session, auto-approve lists
- 📊 **Agent Logging** — Structured execution logs for multi-agent workflows

---

## Installation Instructions (README content)

### Prerequisites

- Java 21+ (for orchestrator server)
- PostgreSQL 15+ with pgvector extension
- Ollama (for local embeddings) — or any OpenAI-compatible API
- Node.js 20+ (for Node.js bridge client)

### Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/dnguyenminh/MCPOrchestration.git
cd MCPOrchestration

# 2. Build the server
./gradlew :orchestrator-server:shadowJar

# 3. Configure (edit application.yml)
cp orchestrator-server/src/main/resources/application.yml ./application.yml
# Edit: set transport, database, embedding provider

# 4. Run the server
java -jar orchestrator-server/build/libs/mcp-orchestrator-all.jar
```

### MCP Client Configuration

Add to your IDE's MCP config (e.g., `.kiro/settings/mcp.json`, `claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "orchestrator": {
      "command": "node",
      "args": [
        "/path/to/MCPOrchestration/mcp-client-bridge/dist/index.js",
        "--url",
        "http://localhost:9180"
      ]
    }
  }
}
```

#### Alternative: Python Bridge
```json
{
  "mcpServers": {
    "orchestrator": {
      "command": "python",
      "args": ["-m", "mcp_bridge", "--url", "http://localhost:9180"]
    }
  }
}
```

#### Alternative: PowerShell Bridge
```json
{
  "mcpServers": {
    "orchestrator": {
      "command": "pwsh",
      "args": ["-NoProfile", "-File", "/path/to/mcp-bridge.ps1", "-Url", "http://localhost:9180"]
    }
  }
}
```

### Upstream Server Configuration

Create `mcp-servers.json` to define upstream MCP servers:

```json
{
  "mcpServers": {
    "my-tool-server": {
      "command": "uvx",
      "args": ["my-mcp-tool-server"],
      "env": {}
    },
    "another-server": {
      "transport": "http-streamable",
      "url": "http://localhost:8081/mcp"
    }
  }
}
```

Run with config:
```bash
java -jar mcp-orchestrator-all.jar --config ./mcp-servers.json
```

---

## Available Tools (exposed to AI agents)

| Tool | Description |
|------|-------------|
| `find_tools` | Semantic search for tools by natural language query |
| `execute_dynamic_tool` | Execute any discovered tool on upstream servers |
| `toggle_tool` | Enable/disable tools per session |
| `reset_tools` | Reset all toggle states |
| `manage_auto_approve` | Configure auto-approve list |
| `agent_log` | Write structured execution logs |
| `stream_write_file` | Write files directly to disk (local, no network) |
| `embed_images` | Embed local images as base64 in markdown files |

Plus all tools from connected upstream servers (discoverable via `find_tools`).

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│              AI Agent (Claude, etc.)              │
└─────────────────┬───────────────────────────────┘
                  │ stdio (MCP protocol)
┌─────────────────▼───────────────────────────────┐
│         Bridge Client (Node/Python/PS/Bash)       │
│         • Health check (ping 30s)                 │
│         • Auto-reconnect                          │
│         • Local tools (stream_write, embed)        │
└─────────────────┬───────────────────────────────┘
                  │ HTTP Streamable (POST /mcp)
┌─────────────────▼───────────────────────────────┐
│           MCP Orchestrator Server (Kotlin/Ktor)    │
│         • Semantic discovery (pgvector)            │
│         • Dynamic execution routing                │
│         • Tool management & auto-approve           │
│         • File proxy (large content)               │
│         • Agent logging                            │
└───┬─────────┬─────────┬─────────┬───────────────┘
    │         │         │         │  stdio/HTTP
┌───▼───┐ ┌──▼──┐ ┌───▼───┐ ┌──▼──────┐
│Server1│ │Srv2 │ │Server3│ │ Server N │
│(Jira) │ │(DB) │ │(Docs) │ │ (...)    │
└───────┘ └─────┘ └───────┘ └──────────┘
```

---

## GitHub Actions Workflow

File: `.github/workflows/release.yml`

```yaml
name: Release MCP Orchestrator

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'corretto'

      - name: Build Shadow JAR
        run: ./gradlew :orchestrator-server:shadowJar

      - name: Build Node.js Bridge
        run: |
          cd mcp-client-bridge
          npm ci
          npm run build

      - name: Create Release
        uses: softprops/action-gh-release@v2
        with:
          files: |
            orchestrator-server/build/libs/mcp-orchestrator-all.jar
            mcp-bridge-bash/mcp-bridge.sh
            mcp-bridge-powershell/mcp-bridge.ps1
            mcp-bridge-cmd/mcp-bridge.cmd
          body: |
            ## MCP Orchestrator ${{ github.ref_name }}

            ### Downloads
            - `mcp-orchestrator-all.jar` — Server (requires Java 21+)
            - `mcp-bridge.sh` — Bash bridge client
            - `mcp-bridge.ps1` — PowerShell bridge client
            - `mcp-bridge.cmd` — Windows CMD bridge client

            ### Node.js Bridge
            ```bash
            cd mcp-client-bridge && npm ci && npm run build
            ```

            ### Python Bridge
            ```bash
            cd mcp-bridge-python && pip install -e .
            ```

  publish-python:
    runs-on: ubuntu-latest
    needs: build
    steps:
      - uses: actions/checkout@v4

      - name: Set up Python
        uses: actions/setup-python@v5
        with:
          python-version: '3.12'

      - name: Build & Publish to PyPI
        run: |
          cd mcp-bridge-python
          pip install build twine
          python -m build
          twine upload dist/*
        env:
          TWINE_USERNAME: __token__
          TWINE_PASSWORD: ${{ secrets.PYPI_TOKEN }}
```

---

## Checklist Before Submission

- [ ] README.md updated with clear installation instructions
- [ ] GitHub repo is public
- [ ] License file present (MIT)
- [ ] At least one release tag (e.g., v1.0.0)
- [ ] GitHub Actions workflow for automated releases
- [ ] Screenshots/demo (optional but recommended)
- [ ] mcp-servers.json example in README
- [ ] All bridge clients documented with usage examples
- [ ] Health check feature documented
- [ ] Architecture diagram in README
