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
| Version | 6.0 |
| Date | 2026-05-08 |
| Status | Reviewed |
| Related BRD | BRD-v3-MTO-5.docx |
| Related FSD | FSD-v2-MTO-5.docx |
| Related TDD | TDD-v2-MTO-5.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-03 | DEV Agent | Initial document |
| 2.0 | 2026-05-04 | DEV Agent | Rewritten from source code — accurate config reference, error codes, tool schemas, end-user focus |
| 2.1 | 2026-05-04 | BA Agent (Reviewer) | Removed developer-focused source code references, simplified error codes table for end-users |
| 3.0 | 2026-05-04 | DEV Agent | Added JSON config format (mcp.json) per Jira comment #10046. Dual YAML+JSON examples throughout. Restructured Configuration Methods section. |
| 4.0 | 2026-05-05 | SM Agent | Rewrote Section 3.1 — comprehensive application.yml guide: file structure overview (7 sub-sections), relationship with mcp.json, default vs override, complete annotated example. |
| 5.0 | 2026-05-06 | DEV Agent | Section 3.1 rewritten by DEV from source code (OrchestratorConfig.kt, ConfigurationManager.kt, ConfigValidator.kt). Added "Creating from Scratch" guide, verified all defaults against code, fixed config loading order accuracy. BA reviewed, QA verified. |
| 6.0 | 2026-05-08 | DEV Agent | Added `--config` CLI argument (Section 2.4 Method 4). Updated config loading order (4 methods). Added mcp-servers.json example in mcpServers format. Updated mcp.json example with `--config` arg. BA reviewed, QA verified. |

---

## 1. Introduction

### 1.1 Purpose

The **MCP Orchestration Server** is a Kotlin/Ktor proxy that sits between the Kiro AI IDE and multiple upstream MCP (Model Context Protocol) servers. Instead of loading every tool definition into the AI's context window, the Orchestrator exposes exactly **two tools**:

- **`find_tools`** — semantic search to discover relevant tools by natural language query
- **`execute_dynamic_tool`** — proxy execution that routes tool calls to the correct upstream server

This reduces context window usage from N tools down to 2, improving AI response quality and lowering token costs.

### 1.2 Audience

| Audience | What They Need |
|----------|---------------|
| AI Agent (Kiro IDE) | How to discover and execute tools via the 2 exposed MCP tools |
| System Administrator | How to configure upstream servers, monitor health, and tune performance |

### 1.3 Prerequisites

| Prerequisite | Version | Required |
|-------------|---------|----------|
| JDK (Java Runtime) | 21+ | Yes |
| Qdrant (Vector Database) | 1.9+ | No — keyword fallback if unavailable |
| OpenAI API Key | — | No — keyword fallback if unavailable |
| Docker | 20+ | No — only needed for Qdrant container |

---

## 2. Getting Started

> **This section is for end-users who download the release JAR and run it.** There are no "clone repo" or "build from source" instructions here.

### 2.1 Quick Start

```bash
# Step 1: Download the release
# Download mcp-orchestrator-all.jar from the GitHub Releases page

# Step 2: Set environment variables (REQUIRED — server crashes without OPENAI_API_KEY)
export OPENAI_API_KEY=sk-proj-your-key-here

# Step 3: Run
java -jar mcp-orchestrator-all.jar

# Step 4: Verify — look for these log lines:
#   MCP Orchestration Server v1.0.0 starting...
#   MCP Orchestration Server ready (stdio transport)
```

### 2.2 System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| JDK | 21+ | 21 LTS |
| Memory | 512 MB heap | 1 GB heap |
| Disk | 100 MB | 500 MB (with Qdrant data) |
| OS | Windows / macOS / Linux | Any OS with JDK 21+ |
| Network | Localhost only (stdio) | Localhost + outbound HTTPS (for OpenAI API) |

### 2.3 Distribution Formats

| Format | How to Get | Use Case |
|--------|-----------|----------|
| **Fat JAR** (recommended) | Download `mcp-orchestrator-all.jar` from Releases | Production — single file, all dependencies bundled |
| **Docker** (future) | `docker pull mcp-orchestrator:latest` | Containerized deployment |

### 2.4 Configuration Methods

The server supports **4 configuration methods**, listed from lowest to highest priority:

| # | Method | Priority | Format | Best For |
|---|--------|----------|--------|----------|
| 1 | **YAML file** (`application.yml`) | Lowest | YAML | Full server configuration with all options |
| 2 | **JSON file** (`mcp.json`) | Medium | JSON | Kiro IDE MCP server registration — easy copy-paste |
| 3 | **`--config` CLI argument** | Medium-High | JSON (mcpServers) | Loading upstream servers from an external file at startup |
| 4 | **Environment variables** | Highest (overrides files) | Shell | Secrets (API keys), CI/CD, Docker |

Higher-priority methods override lower-priority ones. For example, setting `OPENAI_API_KEY` as an environment variable overrides the `api_key` value in `application.yml`. Servers defined via `--config` override same-name servers from `application.yml` or `mcp.json`.

#### Method 1: YAML Configuration File (`application.yml`)

Place `application.yml` in the **same directory** as the JAR file. The server reads this file on startup and resolves `${ENV_VAR}` references to environment variables.

```yaml
orchestrator:
  server:
    transport: stdio          # "stdio" for Kiro IDE, "http" for standalone
    port: 8080                # only used in http mode

  discovery:
    top_k: 5                  # max results from find_tools (1–20)
    similarity_threshold: 0.7 # min cosine similarity (0.0–1.0)
    fallback_to_keyword: true # keyword search if Vector DB unavailable

  execution:
    timeout_seconds: 30       # upstream tool timeout (5–300)
    validate_arguments: true  # validate args against input_schema
    max_retries: 1            # retry failed executions (≥0)

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
```

#### Method 2: JSON Configuration File (`mcp.json`) — Kiro IDE Integration

Register the Orchestrator as an MCP server in your Kiro IDE settings. This JSON format is designed for easy copy-paste into IDE configuration.

**Minimal — just the Orchestrator (no upstream servers):**

```json
{
  "mcpServers": {
    "mcp-orchestrator": {
      "command": "java",
      "args": ["-jar", "/path/to/mcp-orchestrator-all.jar"],
      "env": {
        "OPENAI_API_KEY": "sk-proj-your-key-here"
      }
    }
  }
}
```

**With upstream servers configured via environment variables:**

```json
{
  "mcpServers": {
    "mcp-orchestrator": {
      "command": "java",
      "args": ["-jar", "/path/to/mcp-orchestrator-all.jar"],
      "env": {
        "OPENAI_API_KEY": "sk-proj-your-key-here",
        "JIRA_URL": "https://mycompany.atlassian.net",
        "JIRA_TOKEN": "ATATT3xFfGF0...",
        "GITHUB_TOKEN": "ghp_abc123..."
      }
    }
  }
}
```

> **Tip:** When using `mcp.json`, the Orchestrator still reads `application.yml` for detailed settings (discovery, execution, health). The JSON file primarily controls how the IDE launches the server and passes environment variables.

#### Method 3: `--config` CLI Argument — External Server Configuration

Pass `--config <path>` when launching the JAR to load upstream servers from an external JSON file in **mcpServers format**. This is useful when you want to keep upstream server definitions separate from the bundled `application.yml`.

**How it works:**
1. The Orchestrator parses `--config <path>` from the command-line arguments
2. Reads the JSON file at `<path>` (absolute or relative to working directory)
3. Parses the `mcpServers` key — each entry becomes an upstream server
4. Transport is auto-detected: `url` present → HTTP, `command` present → stdio
5. Servers from `--config` override same-name servers from `application.yml` or `mcp.json`

**Command line:**

```bash
java -jar mcp-orchestrator-all.jar --config ./mcp-servers.json
```

**`mcp-servers.json` (mcpServers format):**

```json
{
  "mcpServers": {
    "jira-server": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-jira"],
      "env": {
        "JIRA_URL": "https://mycompany.atlassian.net",
        "JIRA_TOKEN": "ATATT3xFfGF0..."
      }
    },
    "git-server": {
      "url": "http://localhost:3001/mcp"
    },
    "database-server": {
      "command": "python",
      "args": ["-m", "mcp_server_database"],
      "env": {
        "DB_URL": "postgresql://localhost:5432/mydb"
      }
    }
  }
}
```

**Kiro IDE `mcp.json` with `--config`:**

```json
{
  "mcpServers": {
    "mcp-orchestrator": {
      "command": "java",
      "args": ["-jar", "/path/to/mcp-orchestrator-all.jar", "--config", "./mcp-servers.json"],
      "env": {
        "OPENAI_API_KEY": "sk-proj-your-key-here"
      }
    }
  }
}
```

> **Tip:** Use `--config` when you want to share upstream server definitions across team members (commit `mcp-servers.json` to the repo) while keeping secrets in each developer's `mcp.json` env section.

**If the file is not found**, the server logs a warning and continues without the external servers:
```
WARN  Config file not found: /path/to/mcp-servers.json. Continuing without it.
```

#### Method 4: Environment Variables

Environment variables override any value in `application.yml`. The server resolves `${VAR_NAME}` patterns in YAML to the corresponding environment variable.

```bash
# Required for semantic search (optional — keyword fallback if missing)
export OPENAI_API_KEY=sk-proj-abc123...

# Per-upstream-server secrets
export JIRA_TOKEN=ATATT3xFfGF0...
export JIRA_URL=https://mycompany.atlassian.net

# Run
java -jar mcp-orchestrator-all.jar
```

### 2.5 Verify Configuration

After starting the server, verify with these 4 checks:

**Check 1 — Server started:**
```
# Expected log output:
MCP Orchestration Server v1.0.0 starting...
MCP Orchestration Server ready (stdio transport)
```

**Check 2 — Upstream servers connected:**
```
# For each configured upstream server, look for:
Health check OK: jira-server
Health check OK: git-server
```

**Check 3 — MCP handshake works (send via stdin):**
```json
{"jsonrpc":"2.0","id":1,"method":"tools/list"}
```
Expected: response listing 2 tools (`find_tools` and `execute_dynamic_tool`).

**Check 4 — Tool discovery works:**
```json
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"find_tools","arguments":{"query":"create a Jira ticket"}}}
```
Expected: response with matching tools from upstream servers.

**Common startup issues:**

| Symptom | Cause | Fix |
|---------|-------|-----|
| No log output at all | JDK not installed or wrong version | Install JDK 21+: `java -version` |
| `ConfigException: Failed to parse configuration` | `OPENAI_API_KEY` env var not set (bundled config requires it) | Set `export OPENAI_API_KEY=sk-proj-...` or `export OPENAI_API_KEY=not-configured` for keyword-only mode |
| `search_mode: keyword` in responses | Qdrant or OpenAI unavailable | Normal — keyword fallback is working. Install Qdrant for semantic search. |

---

## 3. Configuration Reference

### 3.1 Understanding `application.yml`

#### What is `application.yml`?

`application.yml` is the **primary configuration file** for the MCP Orchestration Server. It is a [YAML](https://yaml.org/) text file that controls **all server behavior**: transport mode, tool discovery settings, execution timeouts, embedding configuration, vector database connection, health monitoring, and upstream MCP server definitions.

The server resolves `${ENV_VAR}` references to environment variables at load time. If a referenced variable is not set, it resolves to an empty string.

#### File Structure Overview

The file has a single top-level key `orchestrator` with **7 sub-sections**:

```yaml
orchestrator:
  server:            # 1. Transport mode and port
  discovery:         # 2. Tool search settings — top_k, threshold, fallback
  execution:         # 3. Upstream tool execution — timeout, retries, validation
  embedding:         # 4. OpenAI embedding model and cache settings
  vector_db:         # 5. Qdrant vector database connection
  health:            # 6. Health monitoring and auto-reconnect
  upstream_servers:  # 7. List of upstream MCP servers to connect to
```

Each sub-section has sensible defaults. You only need to specify properties you want to override — everything else uses the built-in defaults shown in Section 3.2.

#### Creating Your Configuration

Since the server reads its bundled `application.yml` from the classpath and resolves `${VAR_NAME}` references from environment variables, the primary way to configure the server is through **environment variables** and **`mcp.json`**.

**Option A — Kiro IDE users (recommended):** Configure everything in `mcp.json`, optionally with `--config` for upstream servers:

```json
{
  "mcpServers": {
    "mcp-orchestrator": {
      "command": "java",
      "args": ["-jar", "/path/to/mcp-orchestrator-all.jar", "--config", "./mcp-servers.json"],
      "env": {
        "OPENAI_API_KEY": "sk-proj-your-key-here",
        "JIRA_URL": "https://mycompany.atlassian.net",
        "JIRA_TOKEN": "ATATT3xFfGF0..."
      }
    }
  }
}
```

Where `mcp-servers.json` defines your upstream servers:

```json
{
  "mcpServers": {
    "jira-server": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-jira"],
      "env": {
        "JIRA_URL": "https://mycompany.atlassian.net",
        "JIRA_TOKEN": "ATATT3xFfGF0..."
      }
    },
    "git-server": {
      "url": "http://localhost:3001/mcp"
    }
  }
}
```

Place `mcp.json` at `.kiro/settings/mcp.json` in your workspace. The IDE reads it and launches the Orchestrator with the specified arguments and environment variables.

**Option B — Command-line users:** Set environment variables before running:

```bash
# Required — server crashes without this
export OPENAI_API_KEY="sk-proj-your-key-here"

# Optional — for upstream server secrets
export JIRA_URL="https://mycompany.atlassian.net"
export JIRA_TOKEN="ATATT3xFfGF0..."

# Run
java -jar mcp-orchestrator-all.jar
```

```powershell
# Windows PowerShell
$env:OPENAI_API_KEY = "sk-proj-your-key-here"
$env:JIRA_URL = "https://mycompany.atlassian.net"
$env:JIRA_TOKEN = "ATATT3xFfGF0..."

java -jar mcp-orchestrator-all.jar
```

**Verify startup.** Look for these log lines:

```
MCP Orchestration Server v1.0.0 starting...
MCP Orchestration Server ready (stdio transport)
```

If you see `ConfigException: Failed to parse configuration`, check that `OPENAI_API_KEY` is set (see "Default Behavior" section below for details).

#### Relationship Between `application.yml`, `mcp.json`, and `--config`

These files serve **different purposes** and work together:

| File | Format | Purpose | Contains |
|------|--------|---------|----------|
| `application.yml` | YAML | **Server settings** — how the Orchestrator behaves internally | All 7 config sections: transport, discovery, execution, embedding, vector_db, health, upstream_servers |
| `mcp.json` | JSON | **IDE launch config** — how Kiro IDE starts the Orchestrator process | The `java -jar` command, JAR path, `--config` arg, and environment variables (API keys, tokens) |
| `mcp-servers.json` | JSON | **External server definitions** — loaded via `--config` CLI arg | mcpServers entries (upstream server definitions in IDE-friendly format) |

**How they interact:**

1. You edit `mcp.json` in your Kiro IDE settings (`.kiro/settings/mcp.json`) to register the Orchestrator as an MCP server
2. `mcp.json` specifies the launch command (`java -jar ...`), optionally with `--config` argument, and passes environment variables (like `OPENAI_API_KEY`)
3. When the IDE launches the Orchestrator, it sets those environment variables in the process
4. The Orchestrator reads `application.yml` and resolves `${VAR_NAME}` references from those environment variables
5. If `--config <path>` is provided, the Orchestrator also loads upstream servers from that external JSON file (mcpServers format)

**Example flow:**

```
mcp.json: "args": ["--config", "./mcp-servers.json"], "env": { "OPENAI_API_KEY": "sk-proj-abc123" }
    ↓ (IDE launches Orchestrator with --config arg and env vars set)
application.yml: api_key: ${OPENAI_API_KEY}
    ↓ (ConfigurationManager resolves at load time)
Effective value: api_key: "sk-proj-abc123"
    +
mcp-servers.json: { "mcpServers": { "jira-server": { ... } } }
    ↓ (ConfigurationManager loads CLI config servers)
Effective upstream_servers: [jira-server from --config, ...]
```

**When to use which:**

| Scenario | Where to Configure |
|----------|--------------------|
| Change discovery top_k from 5 to 10 | `application.yml` → `discovery.top_k: 10` |
| Set OpenAI API key | `mcp.json` → `"env": { "OPENAI_API_KEY": "sk-..." }` |
| Add a new upstream MCP server | `application.yml` → add entry to `upstream_servers` list |
| Add upstream servers from shared file | `--config ./mcp-servers.json` (mcpServers format) |
| Pass Jira token securely | `mcp.json` → `"env": { "JIRA_TOKEN": "..." }` |
| Change execution timeout | `application.yml` → `execution.timeout_seconds: 60` |
| Change JAR file path | `mcp.json` → `"args": ["-jar", "/new/path/to/jar"]` |
| Share server definitions across team | `--config ./mcp-servers.json` (commit to repo) |

#### Default Behavior — What Happens Without `application.yml`

The JAR file ships with a **bundled** `application.yml` inside the classpath. The server always reads this bundled file on startup.

> **⚠️ Known Limitation (v1.0.0):** The current version does **not** automatically detect an external `application.yml` file placed next to the JAR. Configuration customization is done through **environment variables** that override `${VAR_NAME}` references in the bundled config. External file override support is planned for a future release.

**⚠️ Required Environment Variable:** The bundled config contains `api_key: ${OPENAI_API_KEY}`. If the `OPENAI_API_KEY` environment variable is **not set**, the server will crash with:

```
ConfigException: Failed to parse configuration: Value for 'api_key' is invalid:
Unexpected null or empty value for non-null field.
```

**To start the server successfully, you must either:**

1. **Set `OPENAI_API_KEY`** to any non-empty value (even a dummy value like `"not-configured"` if you don't need semantic search):
   ```bash
   export OPENAI_API_KEY="sk-proj-your-key-here"   # real key for semantic search
   # OR
   export OPENAI_API_KEY="not-configured"           # dummy value for keyword-only mode
   ```

2. **Or pass it via `mcp.json`** (recommended for Kiro IDE):
   ```json
   {
     "mcpServers": {
       "mcp-orchestrator": {
         "command": "java",
         "args": ["-jar", "/path/to/mcp-orchestrator-all.jar"],
         "env": {
           "OPENAI_API_KEY": "sk-proj-your-key-here"
         }
       }
     }
   }
   ```

**Bundled default values** (when env vars are set):

| Section | Default Behavior |
|---------|-----------------|
| `server` | stdio transport, port 8080 (unused in stdio mode) |
| `discovery` | top_k=5, threshold=0.7, max_query_length=2000, keyword fallback enabled |
| `execution` | 30s timeout, argument validation on, 1 retry |
| `embedding` | OpenAI provider, text-embedding-3-small model, uses `OPENAI_API_KEY` env var |
| `vector_db` | Qdrant at localhost:6333, collection "mcp_tools" |
| `health` | 30s check interval, auto-reconnect on, max 5 attempts |
| `upstream_servers` | **Empty list** — no upstream servers connected |

**In practice, this means:**
- The server starts successfully when `OPENAI_API_KEY` is set (even to a dummy value)
- It exposes `find_tools` and `execute_dynamic_tool` via stdio
- `find_tools` returns empty results (no upstream servers → no tools indexed)
- To make it useful, configure upstream servers via environment variables or `mcp.json`

#### Customizing Server Settings

The bundled `application.yml` contains sensible defaults for all 7 sections. To change settings like `top_k`, `timeout_seconds`, or `similarity_threshold`, you currently need to modify the bundled config and rebuild, or wait for external file override support in a future release.

**Settings you CAN customize via environment variables today:**
- `OPENAI_API_KEY` — embedding API key (referenced as `${OPENAI_API_KEY}`)
- Any upstream server secrets (e.g., `JIRA_TOKEN`, `JIRA_URL`) — referenced as `${VAR_NAME}` in the upstream_servers section

**Settings that use bundled defaults (not overridable via env vars in v1.0.0):**
- `discovery.top_k` (default: 5)
- `discovery.similarity_threshold` (default: 0.7)
- `execution.timeout_seconds` (default: 30)
- `health.check_interval_seconds` (default: 30)
- All other non-secret settings

> **Note:** The complete annotated `application.yml` below shows all available properties and their defaults. This serves as a reference for understanding the server's behavior and for future versions that support external file override.

#### Complete Annotated `application.yml`

Below is a **complete** `application.yml` showing every property with its default value and inline comments. Copy this as a starting point and modify what you need:

```yaml
orchestrator:

  # ── 1. Server Settings ──────────────────────────────────────────────
  server:
    port: 8080              # HTTP port (only used when transport = "http")
    transport: stdio         # "stdio" for Kiro IDE, "http" for standalone HTTP server

  # ── 2. Discovery Settings ───────────────────────────────────────────
  # Controls how find_tools searches for matching tools
  discovery:
    top_k: 5                 # Max results returned by find_tools (range: 1–20)
    similarity_threshold: 0.7 # Min cosine similarity score (range: 0.0–1.0)
                              #   Lower = more results but less relevant
                              #   Higher = fewer but more precise results
    max_query_length: 2000   # Max query string length in characters (≥1)
    fallback_to_keyword: true # If true, uses keyword search when Vector DB
                              # or Embedding service is unavailable

  # ── 3. Execution Settings ───────────────────────────────────────────
  # Controls how execute_dynamic_tool forwards requests to upstream servers
  execution:
    timeout_seconds: 30      # Upstream tool timeout in seconds (range: 5–300)
                              # Throws EXECUTION_TIMEOUT if exceeded
    validate_arguments: true  # Validate tool arguments against input_schema
                              # before forwarding to upstream server
    max_retries: 1           # Retry failed executions (≥0, 0 = no retries)

  # ── 4. Embedding Settings ───────────────────────────────────────────
  # OpenAI embedding model for semantic search (optional — keyword fallback)
  embedding:
    provider: openai                    # Only "openai" supported currently
    model: text-embedding-3-small       # OpenAI embedding model name
    api_key: ${OPENAI_API_KEY}          # Resolved from environment variable
                                        # Leave empty for keyword-only mode
    dimensions: 768                     # Must match model output dimensions
    cache_enabled: true                 # In-memory cache to reduce API calls
    cache_max_size: 100                 # Max cached embedding vectors
    cache_ttl_minutes: 5                # Cache entry time-to-live in minutes

  # ── 5. Vector Database Settings ─────────────────────────────────────
  # Qdrant connection for semantic search (optional — keyword fallback)
  vector_db:
    provider: qdrant          # Only "qdrant" supported currently
    host: localhost            # Qdrant server hostname
    port: 6333                 # Qdrant HTTP API port
    collection_name: mcp_tools # Qdrant collection for tool vectors

  # ── 6. Health Monitoring Settings ───────────────────────────────────
  # Periodic health checks and auto-reconnect for upstream servers
  health:
    check_interval_seconds: 30  # Seconds between health check cycles (≥1)
    auto_reconnect: true         # Auto-reconnect to disconnected servers
                                 # Uses exponential backoff (starting at 1000ms)
    max_reconnect_attempts: 5    # Max reconnect attempts before ERROR state (≥0)

  # ── 7. Upstream MCP Servers ─────────────────────────────────────────
  # List of MCP servers the Orchestrator connects to and indexes tools from.
  # Each server's tools become discoverable via find_tools.
  upstream_servers:
    # Example: Jira MCP server (stdio transport — launches a process)
    - name: jira-server              # Unique identifier (must not be blank)
      transport: stdio                # "stdio" = launch process, "http" = connect to URL
      command: npx                    # Command to launch the MCP server
      args:                           # Command-line arguments
        - "-y"
        - "@modelcontextprotocol/server-jira"
      env:                            # Environment variables for the process
        JIRA_URL: ${JIRA_URL}         # Resolved from parent environment
        JIRA_TOKEN: ${JIRA_TOKEN}

    # Example: HTTP-based MCP server (connects to running service)
    - name: git-server
      transport: http
      url: "http://localhost:3001/mcp"  # Required for http transport
```

### 3.2 All Configuration Properties

#### 3.2.1 Server Settings (`orchestrator.server.*`)

| Property | Type | Default | Valid Values | Description |
|----------|------|---------|-------------|-------------|
| `port` | Int | `8080` | Any valid port | HTTP server port (only used when `transport = "http"`) |
| `transport` | String | `"stdio"` | `"stdio"`, `"http"` | Transport mode. Use `stdio` for Kiro IDE integration. |

#### 3.2.2 Discovery Settings (`orchestrator.discovery.*`)

| Property | Type | Default | Range | Description |
|----------|------|---------|-------|-------------|
| `top_k` | Int | `5` | 1–20 | Maximum number of tool results returned by `find_tools` |
| `similarity_threshold` | Float | `0.7` | 0.0–1.0 | Minimum cosine similarity score. Lower = more results but less relevant. |
| `max_query_length` | Int | `2000` | ≥1 | Maximum allowed query string length in characters |
| `fallback_to_keyword` | Boolean | `true` | — | If `true`, falls back to keyword search when Vector DB or Embedding service is unavailable |

#### 3.2.3 Execution Settings (`orchestrator.execution.*`)

| Property | Type | Default | Range | Description |
|----------|------|---------|-------|-------------|
| `timeout_seconds` | Int | `30` | 5–300 | Timeout in seconds for upstream tool execution. Throws `EXECUTION_TIMEOUT` if exceeded. |
| `validate_arguments` | Boolean | `true` | — | If `true`, validates tool arguments against the tool's `input_schema` before forwarding |
| `max_retries` | Int | `1` | ≥0 | Number of retry attempts for failed upstream executions |

#### 3.2.4 Embedding Settings (`orchestrator.embedding.*`)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `provider` | String | `"openai"` | Embedding provider. Currently only `"openai"` is supported. |
| `model` | String | `"text-embedding-3-small"` | OpenAI embedding model name |
| `api_key` | String | `""` | OpenAI API key. Use `${OPENAI_API_KEY}` to read from environment. |
| `dimensions` | Int | `768` | Vector dimensions. Must match the model output (768 for `text-embedding-3-small`). |
| `cache_enabled` | Boolean | `true` | Enable in-memory embedding cache to reduce API calls |
| `cache_max_size` | Int | `100` | Maximum number of cached embedding vectors |
| `cache_ttl_minutes` | Int | `5` | Cache entry time-to-live in minutes |

#### 3.2.5 Vector Database Settings (`orchestrator.vector_db.*`)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `provider` | String | `"qdrant"` | Vector DB provider: `"qdrant"` |
| `host` | String | `"localhost"` | Qdrant server hostname |
| `port` | Int | `6333` | Qdrant HTTP API port |
| `collection_name` | String | `"mcp_tools"` | Qdrant collection name for tool vectors |

#### 3.2.6 Health Monitoring Settings (`orchestrator.health.*`)

| Property | Type | Default | Range | Description |
|----------|------|---------|-------|-------------|
| `check_interval_seconds` | Int | `30` | ≥1 | Seconds between health check cycles for all upstream servers |
| `auto_reconnect` | Boolean | `true` | — | Automatically reconnect to disconnected servers with exponential backoff |
| `max_reconnect_attempts` | Int | `5` | ≥0 | Maximum reconnection attempts before marking server as `ERROR` |

#### 3.2.7 Upstream Server Configuration (`orchestrator.upstream_servers[]`)

Each entry in the `upstream_servers` list defines one upstream MCP server:

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `name` | String | **Yes** | — | Unique server identifier (must not be blank) |
| `transport` | String | **Yes** | `"stdio"` | `"stdio"` or `"http"` |
| `command` | String | stdio only | `null` | Command to launch the MCP server process |
| `args` | List | No | `[]` | Command-line arguments for the process |
| `env` | Map | No | `{}` | Environment variables passed to the process |
| `url` | String | http only | `null` | HTTP endpoint URL for the MCP server |

**Validation rules:**
- `name` must not be blank
- If `transport = "stdio"` → `command` is required
- If `transport = "http"` → `url` is required

### 3.3 Environment Variables

| Variable | Description | Required | Example |
|----------|-------------|----------|---------|
| `OPENAI_API_KEY` | OpenAI API key for embedding generation | No (keyword fallback) | `sk-proj-abc123...` |

Additional environment variables can be referenced in `application.yml` using `${VAR_NAME}` syntax. Common examples:

| Variable | Used For | Example |
|----------|----------|---------|
| `JIRA_TOKEN` | Jira MCP upstream server auth | `ATATT3xFfGF0...` |
| `JIRA_URL` | Jira instance URL | `https://mycompany.atlassian.net` |
| `GITHUB_TOKEN` | GitHub MCP upstream server auth | `ghp_abc123...` |

### 3.4 Configuration Examples

#### Minimal — Keyword Search Only (no external services)

**YAML (`application.yml`):**

```yaml
orchestrator:
  server:
    transport: stdio
  discovery:
    fallback_to_keyword: true
  upstream_servers: []
```

**JSON (`mcp.json`) — for Kiro IDE:**

```json
{
  "mcpServers": {
    "mcp-orchestrator": {
      "command": "java",
      "args": ["-jar", "/path/to/mcp-orchestrator-all.jar"]
    }
  }
}
```

#### Production — Semantic Search with Multiple Upstream Servers

**YAML (`application.yml`):**

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
        JIRA_URL: ${JIRA_URL}
        JIRA_TOKEN: ${JIRA_TOKEN}

    - name: git-server
      transport: http
      url: "http://localhost:3001/mcp"

    - name: database-server
      transport: stdio
      command: python
      args: ["-m", "mcp_server_database"]
```

**JSON (`mcp.json`) — for Kiro IDE with `--config` and env vars:**

```json
{
  "mcpServers": {
    "mcp-orchestrator": {
      "command": "java",
      "args": ["-jar", "/path/to/mcp-orchestrator-all.jar", "--config", "./mcp-servers.json"],
      "env": {
        "OPENAI_API_KEY": "sk-proj-your-key-here",
        "JIRA_URL": "https://mycompany.atlassian.net",
        "JIRA_TOKEN": "ATATT3xFfGF0...",
        "GITHUB_TOKEN": "ghp_abc123..."
      }
    }
  }
}
```

**`mcp-servers.json` (mcpServers format — loaded via `--config`):**

```json
{
  "mcpServers": {
    "jira-server": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-jira"],
      "env": {
        "JIRA_URL": "https://mycompany.atlassian.net",
        "JIRA_TOKEN": "ATATT3xFfGF0..."
      }
    },
    "git-server": {
      "url": "http://localhost:3001/mcp"
    },
    "database-server": {
      "command": "python",
      "args": ["-m", "mcp_server_database"]
    }
  }
}
```

> **Note:** The `mcp.json` file passes environment variables and `--config` argument to the Orchestrator process. The `--config` file uses the same mcpServers format, making it easy to define upstream servers in a familiar JSON structure. Secrets go in `mcp.json` env, server definitions go in `mcp-servers.json`.

#### Multi-IDE Setup — Sharing Config Across Machines

If you use the Orchestrator on multiple machines, keep `application.yml` in a shared location and use `mcp.json` per-machine for secrets:

**Shared `application.yml` (committed to repo):**

```yaml
orchestrator:
  server:
    transport: stdio
  discovery:
    top_k: 5
    similarity_threshold: 0.7
    fallback_to_keyword: true
  execution:
    timeout_seconds: 30
  embedding:
    provider: openai
    model: text-embedding-3-small
    api_key: ${OPENAI_API_KEY}
  upstream_servers:
    - name: jira-server
      transport: stdio
      command: npx
      args: ["-y", "@modelcontextprotocol/server-jira"]
      env:
        JIRA_URL: ${JIRA_URL}
        JIRA_TOKEN: ${JIRA_TOKEN}
```

**Per-machine `mcp.json` (NOT committed — contains secrets):**

```json
{
  "mcpServers": {
    "mcp-orchestrator": {
      "command": "java",
      "args": ["-jar", "C:/tools/mcp-orchestrator-all.jar"],
      "env": {
        "OPENAI_API_KEY": "sk-proj-machine1-key",
        "JIRA_URL": "https://mycompany.atlassian.net",
        "JIRA_TOKEN": "ATATT3xFfGF0-machine1-token"
      }
    }
  }
}
```

---

## 4. Usage

### 4.1 Tool Discovery — `find_tools`

**What it does:** Searches for available MCP tools by natural language query. Uses semantic search (cosine similarity on vector embeddings) with automatic keyword fallback if the Vector DB or Embedding service is unavailable.

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

**Input Parameters:**

| Parameter | Type | Required | Default | Range | Description |
|-----------|------|----------|---------|-------|-------------|
| `query` | String | **Yes** | — | 1–2000 chars | Natural language description of the desired action |
| `top_k` | Integer | No | 5 | 1–20 | Maximum number of results to return |
| `threshold` | Number | No | 0.7 | 0.0–1.0 | Minimum similarity score for results |

**Validation behavior:**
- Query is trimmed of whitespace before processing
- Empty query after trimming → `INVALID_PARAMS` error
- Query exceeding `max_query_length` (default 2000) → `INVALID_PARAMS` error
- `top_k` is clamped to range 1–20 (out-of-range values are silently adjusted)
- `threshold` is clamped to range 0.0–1.0

**Example Response (semantic search):**

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

**Response fields:**

| Field | Type | Description |
|-------|------|-------------|
| `tools` | Array | List of matching tool results |
| `tools[].name` | String | Tool name — use this exact value in `execute_dynamic_tool` |
| `tools[].description` | String | Human-readable tool description |
| `tools[].input_schema` | Object | JSON Schema defining the tool's input parameters (from upstream server) |
| `tools[].server_name` | String | Name of the upstream MCP server hosting this tool |
| `tools[].server_status` | String | Server health: `CONNECTED`, `DISCONNECTED`, `ERROR`, or `STARTING` |
| `tools[].similarity_score` | Float | Cosine similarity score (0.0–1.0). Higher = more relevant. |
| `search_mode` | String | `"semantic"` (normal) or `"keyword"` (fallback mode) |
| `total_indexed` | Integer | Total number of tools currently in the index |

**Fallback behavior:**
- If Embedding service fails → automatic keyword search fallback
- If Vector DB fails → automatic keyword search fallback
- The `search_mode` field in the response indicates which mode was used
- Keyword search matches tool names and descriptions using substring matching

### 4.2 Tool Execution — `execute_dynamic_tool`

**What it does:** Executes a tool on its upstream MCP server. The Orchestrator looks up the tool in its registry, verifies the upstream server is connected, forwards the JSON-RPC request, and returns the result.

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

**Input Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `tool_name` | String | **Yes** | Exact tool name as returned by `find_tools` (case-sensitive) |
| `arguments` | Object | No | Arguments conforming to the tool's `input_schema`. Passed as-is to the upstream server. |

**Execution pipeline:**
1. Look up `tool_name` in the ToolRegistry → `TOOL_NOT_FOUND` if not found
2. Check upstream server state → `SERVER_UNAVAILABLE` if not `CONNECTED`
3. Get active connection to the upstream server
4. Build JSON-RPC `tools/call` request with the tool name and arguments
5. Send request with timeout (`execution.timeout_seconds`) → `EXECUTION_TIMEOUT` if exceeded
6. Check upstream response for errors → `UPSTREAM_ERROR` if present
7. Extract content from upstream response and return with metadata

**Example Response (success):**

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

**Response metadata:**

| Field | Type | Description |
|-------|------|-------------|
| `_meta.upstream_server` | String | Name of the upstream server that executed the tool |
| `_meta.execution_time_ms` | Long | Execution duration in milliseconds (including network round-trip) |

### 4.3 MCP Protocol Handshake

Before using tools, the MCP client must perform the protocol handshake:

**Request:**
```json
{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"kiro","version":"1.0.0"}}}
```

**Response:**
```json
{"jsonrpc":"2.0","id":0,"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"mcp-orchestrator","version":"1.0.0"}}}
```

### 4.4 List Available Tools

Returns the 2 registered MCP tools with their full JSON Schemas:

```json
{"jsonrpc":"2.0","id":1,"method":"tools/list"}
```

### 4.5 Typical Workflow

```
1. Initialize    → Kiro IDE sends "initialize" to establish MCP session
2. Discover      → AI agent calls find_tools("check logs and create Jira ticket")
3. Review        → AI agent receives tool schemas for read_logs and create_jira_issue
4. Execute       → AI agent calls execute_dynamic_tool("read_logs", {path: "/var/log/app.log"})
5. Process       → Orchestrator proxies request to log-server, returns result
6. Continue      → AI agent calls execute_dynamic_tool("create_jira_issue", {...})
```

---

## 5. Administration

### 5.1 Adding a New Upstream MCP Server

**Option A — via `application.yml` (YAML):**

```yaml
upstream_servers:
  - name: my-new-server
    transport: stdio
    command: npx
    args: ["-y", "my-mcp-server-package"]
    env:
      API_KEY: ${MY_SERVER_API_KEY}
```

**Option B — via `mcp.json` (JSON) — pass secrets as env vars:**

```json
{
  "mcpServers": {
    "mcp-orchestrator": {
      "command": "java",
      "args": ["-jar", "/path/to/mcp-orchestrator-all.jar"],
      "env": {
        "MY_SERVER_API_KEY": "your-api-key-here"
      }
    }
  }
}
```

**Option C — via `--config` (mcpServers format) — recommended for team sharing:**

Add the new server to your `mcp-servers.json` file:

```json
{
  "mcpServers": {
    "my-new-server": {
      "command": "npx",
      "args": ["-y", "my-mcp-server-package"],
      "env": {
        "API_KEY": "your-api-key-here"
      }
    }
  }
}
```

Then launch with `--config`:
```bash
java -jar mcp-orchestrator-all.jar --config ./mcp-servers.json
```

After updating configuration:
1. Restart the Orchestrator (or trigger hot-reload — see Section 5.3).
2. The server automatically:
   - Connects to the new upstream server
   - Calls `tools/list` to discover its tools
   - Generates embeddings for each tool description
   - Indexes tools in the Vector Database
3. Verify by calling `find_tools` with a query matching the new server's tools.

### 5.2 Monitoring Server Health

The Health Monitor runs automatically at the configured interval (default: 30 seconds). It checks all upstream servers and manages state transitions.

**Server States:**

| State | Meaning |
|-------|---------|
| `STARTING` | Server connection is being established |
| `CONNECTED` | Server is healthy and responding to pings |
| `DISCONNECTED` | Health check failed — auto-reconnect will be attempted |
| `ERROR` | Max reconnect attempts exceeded — requires manual intervention |

**State Machine:**

```
STARTING ──→ CONNECTED       (connection successful)
STARTING ──→ ERROR           (connection failed)
CONNECTED ──→ DISCONNECTED   (health check / ping failed)
DISCONNECTED ──→ STARTING    (auto-reconnect triggered)
DISCONNECTED ──→ ERROR       (max_reconnect_attempts exceeded)
ERROR ──→ STARTING           (manual retry / config reload)
```

**Health check behavior:**
- `CONNECTED` servers: sends `ping` request. If ping fails → transitions to `DISCONNECTED`
- `DISCONNECTED` servers: if `auto_reconnect = true`, attempts reconnection with exponential backoff. Backoff starts at 1000ms and doubles each attempt.
- `ERROR` servers: no automatic action. Requires restart or config reload.

**Monitoring via logs:**

```
INFO  HealthMonitor - Health check OK: jira-server
WARN  HealthMonitor - Health check failed for jira-server: Connection refused
INFO  HealthMonitor - Server state transition: jira-server CONNECTED → DISCONNECTED
INFO  HealthMonitor - Reconnecting to jira-server (attempt 1, backoff=1000ms)
INFO  HealthMonitor - Server state transition: jira-server DISCONNECTED → CONNECTED
INFO  HealthMonitor - Server state transition: jira-server DISCONNECTED → ERROR (max attempts)
```

### 5.3 Hot-Reload Configuration

The server supports hot-reload:

1. Modify `application.yml` while the server is running
2. Trigger reload (programmatically via `reload()` method, or restart the server)
3. The system will:
   - Parse and validate the new configuration
   - **Reject invalid configs** — keeps the previous valid config and logs an error
   - Connect to newly added servers
   - Disconnect from removed servers
   - Re-index tools for changed servers
   - Apply new settings (top_k, thresholds, timeouts)

**Log output on successful reload:**
```
INFO  Configuration reloaded successfully
```

**Log output on failed reload:**
```
ERROR Failed to reload configuration: {reason}. Keeping previous config.
```

### 5.4 Starting Qdrant (Vector Database)

If using semantic search (recommended for best results):

```bash
# Option 1: Docker (quickest)
docker run -p 6333:6333 -p 6334:6334 qdrant/qdrant:latest

# Option 2: Docker Compose
docker compose up -d qdrant
```

If Qdrant is not available, the system automatically falls back to keyword-based search. The `search_mode` field in `find_tools` responses indicates which mode is active.

---

## 6. Troubleshooting

### 6.1 Common Issues

| # | Symptom | Cause | Solution |
|---|---------|-------|----------|
| 1 | `find_tools` returns empty results | No tools indexed, or query doesn't match | Verify upstream servers are connected. Try broader queries. Check `total_indexed` in response. |
| 2 | `search_mode: "keyword"` in responses | Vector DB or Embedding service unavailable | Check Qdrant is running (`localhost:6333`). Check `OPENAI_API_KEY` is set. |
| 3 | `TOOL_NOT_FOUND` error | Tool name doesn't match any registered tool | Use `find_tools` first to discover exact tool names. Names are case-sensitive. |
| 4 | `SERVER_UNAVAILABLE` error | Upstream server is disconnected or in error state | Check server health in logs. Verify the upstream server process is running. |
| 5 | `EXECUTION_TIMEOUT` error | Upstream server took longer than configured timeout | Increase `execution.timeout_seconds` or investigate upstream server performance. |
| 6 | Server fails to start with `ConfigException` | Invalid `application.yml` | Check logs for specific validation error. See Section 6.3 for validation rules. |
| 7 | Upstream server stuck in `ERROR` state | Connection failed after max reconnect attempts | Fix the upstream server, then restart the Orchestrator. |
| 8 | `OPENAI_API_KEY` resolves to empty | Environment variable not set | Set `export OPENAI_API_KEY=sk-...` before starting. Or add to `mcp.json` env section. |
| 9 | Qdrant connection refused | Qdrant not running or wrong port | Start Qdrant: `docker run -p 6333:6333 qdrant/qdrant`. Check `vector_db.host` and `vector_db.port`. |
| 10 | Slow `find_tools` responses | Large tool index or cold embedding cache | Enable `cache_enabled: true`. Reduce `top_k`. Check network latency to OpenAI API. |

### 6.2 Error Codes

| Code | Typical Message | When It Happens | What To Do |
|------|----------------|-----------------|------------|
| `INVALID_PARAMS` | "Query parameter is required and must be non-empty" | Empty or missing query in `find_tools` | Provide a non-empty query string |
| `INVALID_PARAMS` | "Query exceeds maximum length of 2000 characters" | Query too long | Shorten query to ≤2000 characters |
| `TOOL_NOT_FOUND` | "Tool 'xyz' is not registered. Use find_tools to discover available tools." | Tool name doesn't match any registered tool | Use `find_tools` first to discover exact tool names (case-sensitive) |
| `SERVER_UNAVAILABLE` | "Server hosting 'xyz' is currently unavailable." | Upstream server is disconnected or in error state | Wait for auto-reconnect or restart the upstream server |
| `EXECUTION_TIMEOUT` | "Tool execution timed out after 30s." | Upstream server didn't respond in time | Increase `execution.timeout_seconds` or check upstream server performance |
| `UPSTREAM_ERROR` | "Upstream error: {details}" | The upstream MCP server returned an error | Check upstream server logs for details |
| `VECTOR_DB_UNAVAILABLE` | "Vector DB is unavailable, using keyword fallback" | Qdrant is not reachable | Start Qdrant or accept keyword fallback mode |
| `EMBEDDING_SERVICE_ERROR` | "Embedding service unavailable" | OpenAI API unreachable or key invalid | Check `OPENAI_API_KEY` and network connectivity |
| `CONFIG_INVALID` | "Configuration validation failed: {details}" | Invalid values in configuration file | Fix the reported validation errors in `application.yml` |
| `INTERNAL_ERROR` | (varies) | Unrecoverable internal error | Check server logs for details. Restart if needed. |

**JSON-RPC standard error codes:**

| Code | Meaning |
|------|---------|
| `-32700` | Parse error — invalid JSON |
| `-32600` | Invalid request — not a valid JSON-RPC request |
| `-32601` | Method not found — unknown MCP method |
| `-32602` | Invalid params — parameter validation failed |
| `-32603` | Internal error — server-side failure |

### 6.3 Configuration Validation Rules

The server validates all configuration values on startup and reload. Invalid configs are rejected with a `ConfigException` listing all errors.

| Property | Rule | Error Message |
|----------|------|---------------|
| `discovery.top_k` | Must be 1–20 | "discovery.top_k must be between 1 and 20, got: {value}" |
| `discovery.similarity_threshold` | Must be 0.0–1.0 | "discovery.similarity_threshold must be between 0.0 and 1.0, got: {value}" |
| `discovery.max_query_length` | Must be ≥1 | "discovery.max_query_length must be positive, got: {value}" |
| `execution.timeout_seconds` | Must be 5–300 | "execution.timeout_seconds must be between 5 and 300, got: {value}" |
| `server.transport` | Must be "stdio" or "http" | "server.transport must be 'stdio' or 'http', got: {value}" |
| `health.check_interval_seconds` | Must be ≥1 | "health.check_interval_seconds must be positive, got: {value}" |
| `health.max_reconnect_attempts` | Must be ≥0 | "health.max_reconnect_attempts must be non-negative, got: {value}" |
| `upstream_servers[].name` | Must not be blank | "Upstream server name must not be blank" |
| `upstream_servers[].command` | Required if transport=stdio | "Upstream server '{name}' with stdio transport must have a command" |
| `upstream_servers[].url` | Required if transport=http | "Upstream server '{name}' with http transport must have a url" |

### 6.4 Logs

| Logger | Content | Useful For |
|--------|---------|------------|
| Application | Startup sequence, transport mode | Verifying server started correctly |
| Health Monitor | Server state transitions, reconnect attempts, ping results | Diagnosing upstream connectivity |
| Tool Discovery | Search queries, result counts, search mode | Debugging search quality |
| Tool Execution | Tool executions, upstream server, duration, success/failure | Performance monitoring |
| Configuration | Config load/reload events, validation errors | Configuration troubleshooting |
| Tool Indexer | Indexing operations, tool counts per server | Verifying tool registration |

**Log levels:**
- `ERROR` — Failures requiring attention (connection failures, unrecoverable errors)
- `WARN` — Degraded operation (fallback to keyword search, health check failures)
- `INFO` — Business events (tool discovery, execution, state transitions, config reload)
- `DEBUG` — Technical details (individual health checks, cache hits/misses)

### 6.5 FAQ

**Q: Can I run the Orchestrator without Qdrant or OpenAI?**
A: Yes. The system uses keyword-based search as a fallback. Set `discovery.fallback_to_keyword: true` (this is the default). Semantic search quality will be lower, but the system remains fully functional.

**Q: How many upstream tools can the system handle?**
A: Designed for 1000+ tools across 50+ servers. Qdrant's HNSW index provides sub-10ms search latency for up to 10,000 vectors.

**Q: What happens if an upstream server goes down?**
A: The Health Monitor detects the failure within one check interval (default: 30s), marks the server as `DISCONNECTED`, and begins auto-reconnect with exponential backoff (starting at 1000ms). Tools from that server remain in the search index but are flagged with `server_status: "DISCONNECTED"`.

**Q: Can I use a different embedding model?**
A: Currently only OpenAI's `text-embedding-3-small` is supported. The `dimensions` config must match the model output (768). Support for additional providers can be added by implementing the `EmbeddingService` interface.

**Q: How does the fallback mechanism work?**
A: If the Embedding service fails → keyword search. If the Vector DB fails → keyword search. If both are available → semantic search. The `search_mode` field in the response indicates which mode was used.

**Q: Is the Orchestrator stateless?**
A: The Orchestrator maintains in-memory state (ToolRegistry, server connections, embedding cache) that is rebuilt on startup from upstream servers. Qdrant provides persistent storage for tool embeddings. If Qdrant is unavailable, the system operates in degraded mode with keyword search only.

**Q: What is the `${VAR_NAME}` syntax in application.yml?**
A: The server resolves `${VAR_NAME}` patterns to environment variables when loading the configuration. If the variable is not set, it resolves to an empty string. This is useful for secrets like API keys.

**Q: Should I use YAML or JSON for configuration?**
A: Use **both**. Put detailed server settings in `application.yml` (YAML) and IDE launch configuration with secrets in `mcp.json` (JSON). The JSON format is easier to copy-paste into Kiro IDE settings. See Section 2.4 for details.

**Q: Can I configure upstream servers in mcp.json instead of application.yml?**
A: Not directly in `mcp.json` itself — that file controls how the IDE launches the Orchestrator. However, you have two options: (1) Pass upstream server secrets via `mcp.json`'s `env` section and reference them in `application.yml` with `${VAR_NAME}`. (2) Use the `--config` CLI argument to load upstream servers from a separate JSON file in mcpServers format: `"args": ["-jar", "/path/to/jar", "--config", "./mcp-servers.json"]`. The `--config` file uses the same mcpServers format as `mcp.json`, making it easy to define servers in a familiar JSON structure.

**Q: What is the `--config` CLI argument?**
A: `--config <path>` tells the Orchestrator to load upstream server definitions from an external JSON file in mcpServers format. Each key under `mcpServers` becomes a server name, and transport is auto-detected (`url` → HTTP, `command` → stdio). Servers from `--config` have higher priority than `application.yml` servers with the same name. If the file is not found, the server logs a warning and continues without it.

---

## 7. API Reference

### 7.1 `find_tools`

| Attribute | Value |
|-----------|-------|
| Name | `find_tools` |
| Description | Search for available MCP tools by describing what you want to accomplish. Returns tool definitions with input schemas so you can call them via execute_dynamic_tool. |
| Protocol | MCP JSON-RPC 2.0 |
| Transport | stdio / HTTP |

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

### 7.2 `execute_dynamic_tool`

| Attribute | Value |
|-----------|-------|
| Name | `execute_dynamic_tool` |
| Description | Execute a tool on an upstream MCP server. Use find_tools first to discover available tools and their input schemas. |
| Protocol | MCP JSON-RPC 2.0 |
| Transport | stdio / HTTP |

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

### 7.3 MCP Protocol Methods

| Method | Description | Direction |
|--------|-------------|-----------|
| `initialize` | MCP session handshake — establishes protocol version and capabilities | Client → Server |
| `tools/list` | List the 2 registered tools (`find_tools`, `execute_dynamic_tool`) with full schemas | Client → Server |
| `tools/call` | Execute one of the 2 tools | Client → Server |
| `ping` | Health check — used internally by HealthMonitor | Client → Server |

---

## 8. Appendix

### 8.1 Glossary

| Term | Definition |
|------|------------|
| MCP | Model Context Protocol — open standard for AI tool communication |
| Orchestrator | The MCP Orchestration Server (this application) |
| Upstream Server | An external MCP Server that hosts actual tools (e.g., Jira MCP, Git MCP) |
| Tool Definition | Metadata describing a tool: name, description, input_schema |
| Vector DB | Vector Database used for semantic similarity search (Qdrant) |
| Embedding | Dense vector representation of text, used for semantic search |
| Top-K | The K most similar results returned from a vector search |
| JSON-RPC | JSON Remote Procedure Call — the wire protocol used by MCP |
| Context Window | The limited token budget available to an AI model per request |
| HNSW | Hierarchical Navigable Small World — graph-based ANN index algorithm used by Qdrant |
| Fat JAR | A single JAR file containing all dependencies, ready to run with `java -jar` |
| Hot-Reload | Ability to update configuration without restarting the server |
| Exponential Backoff | Retry strategy where wait time doubles after each failed attempt |
| mcp.json | JSON configuration file used by Kiro IDE to register MCP servers |

### 8.2 Related Documents

| Document | Location |
|----------|----------|
| BRD | BRD-v3-MTO-5.docx |
| FSD | FSD-v2-MTO-5.docx |
| TDD | TDD-v2-MTO-5.docx |
| STP | STP-v1-MTO-5.docx |
| STC | STC-v1-MTO-5.xlsx |
| DPG | DPG-v1-MTO-5.docx |
| RLN | RLN-v1-MTO-5.docx |

### 8.3 Version Compatibility

| System Version | Config Version | Breaking Changes |
|---------------|---------------|-----------------|
| 1.0.0 | v1 | Initial release |

### 8.4 Version History

This document tracks the MCP Orchestration Server releases:

| Version | Date | Key Changes |
|---------|------|-------------|
| 1.0.0 | 2026-05-01 | Initial release — find_tools, execute_dynamic_tool, health monitoring |
