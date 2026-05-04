# User Guide (UG)

## MCP Orchestration Server — MTO-10: Upgrade MCP Orchestrator: Local Embedding, pgvector, Tool Management & Auto-Approve

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-10 |
| Title | Upgrade MCP Orchestrator: Local Embedding, pgvector, Tool Management & Auto-Approve |
| Author | DEV Agent |
| Reviewer | BA Agent |
| Version | 1.5 |
| Date | 2026-05-04 |
| Status | Final |
| Related BRD | BRD-v1.1-MTO-10.docx |
| Related FSD | FSD-v1.2-MTO-10.docx |
| Related TDD | TDD-v1.1-MTO-10.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-04 | DEV Agent | Initial MTO-10 document |
| 1.1 | 2026-05-04 | Scrum Master Agent | Simplified Quick Start based on user feedback |
| 1.2 | 2026-05-04 | DEV Agent | Fixed jar file names (using fat jar); cleaned up duplicated headers |
| 1.3 | 2026-05-04 | DEV Agent | **Comprehensive Merge:** Restored technical depth; Integrated MTO-10 features; **Architectural Correction:** Clarified Standalone (YAML/SSE) vs Local Bridge (JSON/Stdio) modes. |
| 1.4 | 2026-05-04 | BA Agent | Added "Core Requirements Summary" and "Field Explanation" (Why port/protocol?) based on user request. |
| 1.5 | 2026-05-04 | DEV Agent | Added Unified Entry Point details and JSON-based embedding/vector_db overrides for Stdio mode. |

---

## 1. Introduction

### 1.1 Purpose

The **MCP Orchestration Server** is a proxy that sits between AI clients (Claude, Cursor, etc.) and upstream MCP tool servers. It provides three primary benefits:
1.  **Context Window Optimization:** Reduces thousands of tools down to just 2 tools (`find_tools`, `execute_dynamic_tool`).
2.  **Semantic Discovery:** Uses local or cloud embeddings to find tools by natural language.
3.  **Governance & Safety:** Provides runtime tool management (`toggle_tool`) and automated approvals (`manage_auto_approve`).

### 1.2 Core Requirements Summary (MTO-10)

For quick reference, the MTO-10 upgrade focuses on these key objectives:
- **Local-First Architecture:** Eliminate dependency on OpenAI/Qdrant in favor of Ollama/LMStudio and PostgreSQL (pgvector).
- **Dynamic Control:** Enable/Disable tools at runtime via MCP commands without restarting.
- **Automated Workflows:** Persistently flag safe tools as "auto-approve" in both DB and config file.
- **Environment Parity:** Support both high-performance server mode (SSE) and direct IDE integration (Stdio).
- **Security Baseline:** Implement hard tool filtering (Allowlist/Blocklist) at the indexing layer.

---

---

## 2. Getting Started

### 2.1 Distribution Formats

| Format | File Name | Use Case |
|--------|-----------|----------|
| **Fat JAR** (recommended) | `mcp-orchestrator-all.jar` | Production — single file, all dependencies bundled |

### 2.2 Quick Start (The 5-Minute Setup)

1. **Step 1: Prepare Database**
Ensure PostgreSQL is running and the vector extension is enabled:
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

2. **Step 2: Run Application**
```bash
java -jar mcp-orchestrator-all.jar
```

---

## 3. Configuration Reference

The Orchestrator supports two distinct operational modes, each with its own configuration format.

### 3.1 Mode A: Standalone Instance (HTTP/SSE)
**Use Case:** Running the Orchestrator as a persistent web service.
- **Format:** YAML (`application.yml`).
- **Protocol:** HTTP Streamable / Server-Sent Events (SSE).

**Example `application.yml`:**
```yaml
orchestrator:
  server:
    port: 8080
    protocol: sse
  embedding:
    provider: ollama
    model: nomic-embed-text
  vector_db:
    provider: pgvector
    connection_string: "postgresql://..."

#### 3.1.1 Why these fields?

| Field | Why it is needed |
|-------|------------------|
| `protocol: sse` | This flag instructs the application to start as a **persistent web server** (Standalone Mode). If not set or set to `stdio`, it will wait for standard input, which is suitable for IDEs but not for multi-client web access. |
| `port: 8080` | Specifies the network port where the SSE stream is served. This is essential for the AI client to connect via HTTP. |
```

---

### 3.2 Mode B: Local Bridge (Stdio)
**Use Case:** Direct integration with IDEs (Cursor, Claude, VSCode).
- **Format:** JSON (inside Client app settings).
- **Protocol:** JSON-RPC 2.0 over Standard I/O (stdio).

**IDE Integration Example:**
**IDE Integration Example (Advanced JSON Configuration):**
In **Stdio mode**, you can now define your entire Orchestrator environment (including Embedding and Vector DB settings) directly within the client's configuration file (e.g., Cursor's `mcpServers` or a dedicated `config.json`).

```json
{
  "mcpServers": {
    "mcp-orchestrator": {
      "command": "java",
      "args": [
        "-jar", 
        "C:\\path\\to\\mcp-orchestrator-all.jar",
        "--config",
        "C:\\path\\to\\mcp-config.json"
      ],
      "env": {
        "EMBEDDING_PROVIDER": "ollama",
        "EMBEDDING_MODEL": "nomic-embed-text",
        "VECTOR_DB_PROVIDER": "pgvector",
        "VECTOR_DB_CONNECTION_STRING": "postgresql://postgres:password@localhost:5432/mcp_db"
      }
    }
  }
}
```

**Key Environment Variables:**

| Variable | Description |
|----------|-------------|
| `EMBEDDING_PROVIDER` | Local or Cloud provider (`ollama`, `openai`, `lmstudio`). |
| `EMBEDDING_MODEL` | Specific model name (e.g., `nomic-embed-text`). |
| `EMBEDDING_BASE_URL` | Base URL for the provider (e.g., `http://localhost:11434` for Ollama). |
| `VECTOR_DB_CONNECTION_STRING` | Full JDBC or connection string for the database. |
| `SERVER_PROTOCOL` | `stdio` (default) or `sse`. |

#### 3.2.1 Ollama Special Configuration
If you are using **Ollama** for local embeddings:
1.  **Pull the model:** Run `ollama pull nomic-embed-text`.
2.  **Verify Dimensions:** `nomic-embed-text` typically uses **768** dimensions. Ensure this matches your `EMBEDDING_DIMENSIONS` (default is 768).
3.  **Base URL:** If Ollama is running on the default port, you don't need to set `EMBEDDING_BASE_URL`. If it's on a custom port, set it to `http://your-ip:port`.

#### 3.2.2 Unified Entry Point
The Orchestrator uses a unified entry point: `com.orchestrator.mcp.Main`. 
- **Auto-Detection**: It automatically detects if it should run in **SSE** or **Stdio** mode based on the `orchestrator.server.protocol` configuration.
- **Output Safety**: In Stdio mode, all application logs are automatically redirected to `System.err` to ensure they don't interfere with the JSON-RPC communication on `System.out`.
> [!IMPORTANT]
> When running in **Stdio mode**, the Orchestrator will automatically merge settings found in the JSON configuration with your default YAML settings. JSON settings always take precedence.
> [!TIP]
> Bạn chỉ cần thêm phần `"env": { ... }` nếu trong file cấu hình của Orchestrator (`application.yml` hoặc `mcp-servers.json`) có sử dụng các biến placeholder dạng `${VAR_NAME}` cần truyền giá trị từ môi trường.

---

### 3.3 Upstream Server Configuration (JSON)
Regardless of the mode above, the Orchestrator manages its connection to upstream servers (Jira, Slack, etc.) via a separate JSON configuration file (typically `mcp-servers.json`).

- **Format:** JSON.
- **Upstream Protocols Supported:** Both **stdio** and **http/sse**.

**Example Upstream Config:**
```json
{
  "mcpServers": {
    "sample-server": {
      "command": "npx",
      "args": ["..."],
      "env": { "SECRET": "..." }
    }
  }
}
```

---

## 4. Deep Dive: MTO-10 New Features

MTO-10 adds advanced governance and local-first capabilities. Here is exactly how to use them.

### 4.1 Dynamic Tool Management (`toggle_tool`)

The `toggle_tool` command allows you to enable or disable any upstream tool without restarting the server.

**Example: Disabling a dangerous tool**
```json
{
  "name": "execute_dynamic_tool",
  "arguments": {
    "tool_name": "toggle_tool",
    "arguments": {
      "tool_name": "jira_delete_issue",
      "enabled": false
    }
  }
}
```

---

### 4.2 Auto-Approve Configuration (`manage_auto_approve`)

Some tools are "read-only" and safe to run automatically. You can tell the Orchestrator to flag these for the AI.

**Example: Auto-approving Jira Get Issue**
```json
{
  "name": "execute_dynamic_tool",
  "arguments": {
    "tool_name": "manage_auto_approve",
    "arguments": {
      "tool_name": "jira_get_issue",
      "auto_approve": true
    }
  }
}
```

---

### 4.3 Local Embedding Guide (Ollama / LMStudio)

MTO-10 allows the Orchestrator to run completely offline for tool discovery.

#### 4.3.1 Recommended Models
- **Ollama:** `nomic-embed-text` (768 dimensions) or `all-minilm` (384 dimensions).
- LMStudio: Use any model tagged with `feature: embeddings`.

#### 4.3.3 Automatic Dimension Normalization
MTO-10 handles model inconsistencies automatically. If your local model (e.g., `all-minilm`) produces 384 dimensions but your database expects 768, the Orchestrator will **automatically pad** the vector with zeros. Conversely, if the vector is too large, it will be **truncated**. This ensures the system remains stable across different local AI providers.

#### 4.3.2 Verifying Connectivity
If the Orchestrator cannot reach your local provider, look for những lỗi này in logs:
- `Embedding provider 'ollama' is unreachable at localhost:11434`.
- **Fix:** Chạy `ollama serve` hoặc đảm bảo LMStudio "Local Server" tab đang hoạt động.

---

### 4.4 Global Reset (`reset_tools`)

Nếu bạn muốn xóa toàn bộ các tùy chỉnh bật/tắt tạm thời và quay về trạng thái mặc định từ file cấu hình:

```json
{
  "name": "execute_dynamic_tool",
  "arguments": {
    "tool_name": "reset_tools",
    "arguments": {
      "reindex": true
    }
  }
}
```
- **reindex: true**: Orchestrator sẽ quét lại toàn bộ server và làm mới bộ nhớ tìm kiếm.

---

### 4.5 Tool Filtering (Allowlist / Blocklist)

MTO-10 cho phép bạn giới hạn các tool được phép sử dụng ngay từ file cấu hình `mcp-servers.json`.

**Cách cấu hình:**
```json
{
  "mcpServers": {
    "jira-server": {
      "command": "npx",
      "args": ["..."],
      "toolFilter": {
        "mode": "blocklist",
        "tools": ["jira_delete_issue", "jira_delete_project"]
      }
    }
  }
}
```

---

### 4.6 Config-DB Synchronization

File `mcp-servers.json` là **Nguồn sự thật duy nhất** (Source of Truth). Hệ thống tự động đồng bộ vào DB khi khởi động.

---

### 4.7 PostgreSQL pgvector Maintenance

Hệ thống tự động tạo bảng `mcp_tool_embeddings`.

- **To Clear Index:** Chạy lệnh SQL: `TRUNCATE TABLE mcp_tool_embeddings;`

---

## 5. Usage

### 5.1 Discovery (`find_tools`)
**Request:**
```json
{"name": "find_tools", "arguments": {"query": "get my jira tickets"}}
```

### 5.2 Execution (`execute_dynamic_tool`)
**Request:**
```json
{
  "name": "execute_dynamic_tool",
  "arguments": {
    "tool_name": "jira_get_issue",
    "arguments": {"issue_key": "MTO-10"}
  }
}
```

---

## 6. Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Connection Refused | Ollama/LMStudio not running | Ensure your local AI server is active. |
| `search_mode: keyword` | Vector DB/Embedding failed | Check PostgreSQL connection. |
| Tool not found | Indexing in progress | Wait 10-20 seconds after startup. |

---

## 7. Appendix: Glossary & Related Docs
- **FAT JAR:** All-in-one executable.
- **pgvector:** PostgreSQL extension for vector search.
