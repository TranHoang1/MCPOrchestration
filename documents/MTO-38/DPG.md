# Deployment Guide (DPG)

## MCPOrchestration — MTO-38: KB Server Deployment

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-38 |
| Title | KB Server — Deployment Guide |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |

---

## 1. Overview

This guide covers deployment of the **kb-server** module as a standalone MCP server. The kb-server can run in two modes:
- **STDIO mode** — spawned as subprocess by orchestrator-server (development)
- **HTTP mode** — standalone HTTP server on port 9181 (production)

---

## 2. Prerequisites

### 2.1 System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| JDK | 21 | 21 (LTS) |
| RAM | 512MB | 1GB |
| Disk | 100MB | 500MB |
| OS | Windows/Linux/macOS | Linux (production) |

### 2.2 External Dependencies

| Service | Version | Required | Purpose |
|---------|---------|----------|---------|
| PostgreSQL | 16+ | Yes | Data storage |
| pgvector extension | 0.7+ | Yes | Vector similarity search |
| Ollama | latest | Yes (for embeddings) | Text embedding generation |

### 2.3 Network Requirements

| Port | Service | Direction |
|------|---------|-----------|
| 9181 | KB Server HTTP | Inbound (HTTP mode only) |
| 5432 | PostgreSQL | Outbound |
| 11434 | Ollama | Outbound |

---

## 3. Build

### 3.1 Build ShadowJar

```bash
./gradlew :kb-server:shadowJar
```

Output: `kb-server/build/libs/kb-server-all.jar`

### 3.2 Verify Build

```bash
java -jar kb-server/build/libs/kb-server-all.jar --help
```

---

## 4. Configuration

### 4.1 Configuration File

Create `kb-server.yml`:

```yaml
kb:
  server:
    port: 9181
    transport: http    # stdio for dev, http for prod

  database:
    url: "jdbc:postgresql://localhost:5432/mcp_orchestrator"
    schema: "kb"
    username: "kb_app"
    password: "${KB_DB_PASSWORD}"
    pool:
      maximum_size: 10
      minimum_idle: 2

  embedding:
    provider: "ollama"
    model: "nomic-embed-text"
    base_url: "http://localhost:11434"
    dimensions: 768
    cache_enabled: true
    cache_max_size: 200
    cache_ttl_minutes: 10

  security:
    encryption_key: "${KB_ENCRYPTION_KEY}"
    br_encryption_key: "${KB_BR_ENCRYPTION_KEY}"
    default_role: "developer"
    rate_limit:
      pii_unmask_per_hour: 10
      br_level1_per_hour: 5
      br_level2_per_hour: 15
      br_level3_per_hour: 30

  queue:
    hpq_capacity: 100
    npq_capacity: 1000
    worker_count: 2
    watchdog_interval_seconds: 60
    stuck_threshold_minutes: 5
    max_retries: 3

  audit:
    enabled: true
    retention_days: 90
```

### 4.2 Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| KB_DB_PASSWORD | Yes | PostgreSQL password |
| KB_ENCRYPTION_KEY | Yes | AES-256 key for PII encryption |
| KB_BR_ENCRYPTION_KEY | Yes | AES-256 key for BR encryption |

---

## 5. Database Setup

### 5.1 Create Database and Schema

```sql
-- Create user
CREATE USER kb_app WITH PASSWORD 'your_password';

-- Grant permissions
GRANT USAGE ON SCHEMA kb TO kb_app;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA kb TO kb_app;

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;
```

### 5.2 Schema Auto-Creation

The kb-server automatically creates required tables on first startup via `KbDatabaseInitializer`. No manual migration needed.

---

## 6. Deployment Steps

### 6.1 STDIO Mode (Development)

Configure in orchestrator's `application.yml`:

```yaml
orchestrator:
  upstream_servers:
    - name: "kb-server"
      transport: "stdio"
      command: "java"
      args: ["-jar", "kb-server/build/libs/kb-server-all.jar", "--transport=stdio"]
```

The orchestrator will spawn kb-server as a subprocess automatically.

### 6.2 HTTP Mode (Production)

```bash
# Set environment variables
export KB_DB_PASSWORD="your_password"
export KB_ENCRYPTION_KEY="your_32_byte_key_base64"
export KB_BR_ENCRYPTION_KEY="your_32_byte_key_base64"

# Start server
java -jar kb-server-all.jar --transport=http --config=/etc/kb-server.yml
```

### 6.3 Verify Deployment

```bash
# Check server is running (HTTP mode)
curl http://localhost:9181/api/graph/data

# Check MCP tools available (send JSON-RPC)
curl -X POST http://localhost:9181/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

---

## 7. Rollback Plan

### 7.1 Rollback Steps

1. Stop kb-server process
2. Revert orchestrator config to remove kb-server upstream
3. Restart orchestrator-server
4. KB tools will be unavailable until re-deployed

### 7.2 Data Rollback

- Database schema (`kb.*`) can be dropped if needed: `DROP SCHEMA kb CASCADE;`
- No data migration from orchestrator needed (kb-server uses separate schema)

---

## 8. Monitoring

### 8.1 Health Check

- HTTP mode: GET /api/graph/data (returns 200 if healthy)
- STDIO mode: Send `{"jsonrpc":"2.0","method":"ping","id":1}` via stdin

### 8.2 Logs

- Location: stdout (structured JSON via Logback)
- Key log patterns:
  - `KB Server v1.0.0 starting` — startup
  - `Registered N KB tools` — tool registration
  - `Queue system started` — queue ready
  - `kb_search failed` — search errors
  - `Rate limit exceeded` — security events

---

## 9. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Deployment Flow | [deployment-flow.png](diagrams/deployment-flow.png) | [deployment-flow.drawio](diagrams/deployment-flow.drawio) |
| 2 | Rollback Flow | [rollback-flow.png](diagrams/rollback-flow.png) | [rollback-flow.drawio](diagrams/rollback-flow.drawio) |
