# Deployment Guide (DPG)

## MCP Orchestration Server — MTO-5: Create MCP Tool Orchestration

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-5 |
| Title | Create MCP Tool Orchestration |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2026-05-03 |
| Status | Final |
| Related TDD | TDD-v1-MTO-5.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-03 | DevOps Agent | Initiate document — auto-generated from TDD and project context |

---

## Sign-Off

| Name | Role | Signature and date |
|------|------|--------------------|
| | Dev Lead | ☐ Approved for deployment |
| | QA Lead | ☐ Testing completed |
| | Ops Lead | ☐ Infrastructure ready |

---

## 1. Overview

### 1.1 Feature Summary

The **MCP Orchestration Server** is a Kotlin/Ktor application that acts as an intelligent proxy between the Kiro AI IDE and multiple upstream MCP (Model Context Protocol) Servers. It exposes exactly 2 MCP tools (`find_tools` and `execute_dynamic_tool`) to minimize AI context window consumption while providing access to an unlimited number of upstream tools via semantic search and dynamic proxying.

### 1.2 Deployment Scope

| Item | Type | Description |
|------|------|-------------|
| MCP Orchestration Server | New Application | Kotlin/Ktor fat JAR — single JVM process |
| application.yml | New Configuration | Server, discovery, execution, embedding, vector DB, health, upstream servers config |
| FAISS Index | New Data Store | Local file-based vector index (`~/.mcp-orchestrator/faiss.index`) |
| Qdrant (optional) | External Service | Vector DB for production semantic search (Docker container) |
| OpenAI API | External Service | Embedding generation via `text-embedding-3-small` |

### 1.3 Target Environments

| Environment | URL/Access | Deploy Order | Approval Required |
|-------------|-----------|-------------|-------------------|
| DEV (Local) | `localhost:8080` or stdio pipe | 1st | No |
| SIT | N/A (local dev tool) | — | — |
| UAT | Developer machine | 2nd | QA Sign-off |
| PROD | Developer machine (production use) | 3rd | PM + Dev Lead Sign-off |

> **Note:** This is a developer-local tool. "Production" means running on the developer's machine alongside Kiro IDE. There is no centralized server deployment.

---

## 2. Prerequisites

### 2.1 Infrastructure

| Requirement | Status | Notes |
|-------------|--------|-------|
| Developer machine (Windows/macOS/Linux) | Ready | Any modern OS with JVM support |
| Network access to OpenAI API | Required | For embedding generation |
| Docker (optional) | Optional | Only if using Qdrant instead of FAISS |

### 2.2 Software Dependencies

| Dependency | Version | Status |
|-----------|---------|--------|
| JDK | 21+ | Required — must be installed |
| Gradle | 8.x | Bundled via `gradlew` wrapper |
| Kotlin | 2.3.20 | Bundled in project |
| Ktor | 3.4.0 | Bundled in fat JAR |
| Docker | Latest | Optional — only for Qdrant |
| Qdrant | 1.9+ | Optional — FAISS is default fallback |

### 2.3 Access Requirements

| Access | Type | Who Needs It |
|--------|------|-------------|
| OpenAI API Key | API Key (`OPENAI_API_KEY`) | Developer/User |
| Qdrant (optional) | Local Docker container | Developer |
| Upstream MCP Servers | stdio/HTTP access | Configured per server |

### 2.4 Backup Requirements

- [ ] Previous JAR version saved (if upgrading)
- [ ] `application.yml` backed up before changes
- [ ] FAISS index backed up (`~/.mcp-orchestrator/faiss.index`)

---

## 3. Pre-Deployment Checklist

| # | Item | Responsible | Status |
|---|------|-------------|--------|
| 1 | Code merged to main/release branch | Developer | ☐ |
| 2 | All 118 tests passed (PBT + UT + IT + E2E-API) | QA | ☐ |
| 3 | UAT sign-off obtained | PO | ☐ |
| 4 | `application.yml` configured for target environment | DevOps | ☐ |
| 5 | `OPENAI_API_KEY` environment variable set | Developer | ☐ |
| 6 | Upstream MCP servers configured and accessible | Developer | ☐ |
| 7 | Rollback plan reviewed | Team | ☐ |
| 8 | Previous version JAR saved (if upgrading) | DevOps | ☐ |

---

## 4. Database Migration

### 4.1 Migration Scripts

> **No traditional database migration required.** This system uses:
> - **Qdrant** (Vector DB): Collection auto-created on first startup
> - **FAISS** (local fallback): Index file auto-created on first startup

| Order | Action | Description | Estimated Time |
|-------|--------|-------------|----------------|
| 1 | Qdrant collection creation (auto) | `mcp_tools` collection created if not exists | < 1s |
| 2 | FAISS index initialization (auto) | `~/.mcp-orchestrator/faiss.index` created if not exists | < 1s |
| 3 | Tool indexing (auto) | Upstream servers scanned, tools vectorized and stored | 5-30s per server |

### 4.2 Verification

```bash
# Verify Qdrant collection (if using Qdrant)
curl http://localhost:6333/collections/mcp_tools

# Verify FAISS index file exists
ls ~/.mcp-orchestrator/faiss.index
```

---

## 5. Application Deployment

### 5.1 Build Fat JAR

```bash
# From project root directory
./gradlew clean build

# Fat JAR location after build
ls build/libs/mcp-orchestrator-all.jar
```

Expected output: `mcp-orchestrator-all.jar` (~30-50 MB)

### 5.2 Deployment Steps

| Step | Action | Command | Verification |
|------|--------|---------|-------------|
| 1 | Build fat JAR | `./gradlew clean build` | `build/libs/mcp-orchestrator-all.jar` exists |
| 2 | Run all tests | `./gradlew test` | 118/118 tests pass |
| 3 | Copy JAR to deployment location | `cp build/libs/mcp-orchestrator-all.jar ~/mcp-orchestrator/` | File exists at target |
| 4 | Copy configuration | `cp src/main/resources/application.yml ~/mcp-orchestrator/` | Config file exists |
| 5 | Set environment variables | `export OPENAI_API_KEY=sk-...` | `echo $OPENAI_API_KEY` shows value |
| 6 | Start Qdrant (optional) | `docker run -d -p 6333:6333 qdrant/qdrant:v1.9.0` | `curl localhost:6333` returns OK |
| 7 | Start application (HTTP mode) | `java -jar ~/mcp-orchestrator/mcp-orchestrator-all.jar` | Log shows "Application started" |
| 8 | Health check | `curl http://localhost:8080/health` or MCP `ping` | Returns healthy status |

### 5.3 Running Modes

#### Mode A: stdio (Default — for Kiro IDE integration)

Configure in Kiro IDE's MCP settings:

```json
{
  "mcpServers": {
    "mcp-orchestrator": {
      "command": "java",
      "args": ["-jar", "/path/to/mcp-orchestrator-all.jar"],
      "env": {
        "OPENAI_API_KEY": "sk-..."
      }
    }
  }
}
```

Kiro spawns the Orchestrator as a subprocess. Communication via stdin/stdout.

#### Mode B: HTTP (Standalone server)

```bash
# Edit application.yml
# orchestrator.server.transport: http
# orchestrator.server.port: 8080

java -jar mcp-orchestrator-all.jar
```

Server listens on `http://localhost:8080`.

### 5.4 Docker Deployment (Optional)

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/mcp-orchestrator-all.jar app.jar
COPY src/main/resources/application.yml application.yml
EXPOSE 8080
ENV OPENAI_API_KEY=""
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# Build Docker image
docker build -t mcp-orchestrator:1.0.0 .

# Run with Docker
docker run -d \
  --name mcp-orchestrator \
  -p 8080:8080 \
  -e OPENAI_API_KEY=sk-... \
  mcp-orchestrator:1.0.0

# Verify
docker logs mcp-orchestrator --tail 20
```

---

## 6. Configuration Changes

### 6.1 Environment Variables

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| `OPENAI_API_KEY` | OpenAI API key for embedding generation | Yes (if using OpenAI embeddings) | None |

### 6.2 Application Properties (`application.yml`)

| Property | Description | Default | Notes |
|----------|-------------|---------|-------|
| `orchestrator.server.port` | HTTP server port | `8080` | Only used in HTTP mode |
| `orchestrator.server.transport` | Transport mode | `stdio` | `stdio` or `http` |
| `orchestrator.discovery.top_k` | Max search results | `5` | 1-20 |
| `orchestrator.discovery.similarity_threshold` | Min similarity score | `0.7` | 0.0-1.0 |
| `orchestrator.discovery.fallback_to_keyword` | Enable keyword fallback | `true` | When Vector DB unavailable |
| `orchestrator.execution.timeout_seconds` | Upstream call timeout | `30` | Per-tool execution |
| `orchestrator.execution.validate_arguments` | Validate args against schema | `true` | |
| `orchestrator.embedding.provider` | Embedding provider | `openai` | `openai` supported |
| `orchestrator.embedding.model` | Embedding model | `text-embedding-3-small` | 768 dimensions |
| `orchestrator.embedding.dimensions` | Vector dimensions | `768` | Must match model |
| `orchestrator.vector_db.provider` | Vector DB provider | `qdrant` | `qdrant` or `faiss` |
| `orchestrator.vector_db.host` | Qdrant host | `localhost` | |
| `orchestrator.vector_db.port` | Qdrant port | `6333` | |
| `orchestrator.vector_db.collection_name` | Qdrant collection | `mcp_tools` | |
| `orchestrator.health.check_interval_seconds` | Health check interval | `30` | Seconds |
| `orchestrator.health.auto_reconnect` | Auto-reconnect on failure | `true` | |
| `orchestrator.health.max_reconnect_attempts` | Max reconnect retries | `5` | |
| `orchestrator.upstream_servers` | List of upstream MCP servers | `[]` | See Section 6.3 |

### 6.3 Upstream Server Configuration Example

```yaml
orchestrator:
  upstream_servers:
    - name: jira-server
      transport: stdio
      command: npx
      args: ["-y", "@anthropic/jira-mcp-server"]
      env:
        JIRA_URL: "https://your-jira.atlassian.net"
        JIRA_TOKEN: "${JIRA_TOKEN}"

    - name: git-server
      transport: http
      url: "http://localhost:3001"

    - name: db-server
      transport: stdio
      command: python
      args: ["-m", "db_mcp_server"]
```

---

## 7. Post-Deployment Verification

### 7.1 Health Checks

| Check | Method | Expected Result | Timeout |
|-------|--------|-----------------|---------|
| Application started | Check logs | "Application started" in stdout | 30s |
| MCP initialize | Send `initialize` JSON-RPC | Protocol version `2024-11-05` returned | 5s |
| MCP tools/list | Send `tools/list` JSON-RPC | 2 tools: `find_tools`, `execute_dynamic_tool` | 5s |
| Upstream connectivity | Check health monitor logs | Upstream servers show CONNECTED status | 60s |

### 7.2 Smoke Tests

| # | Scenario | Steps | Expected Result |
|---|----------|-------|-----------------|
| 1 | Tool discovery | Call `find_tools(query: "test")` | Returns tool list (may be empty if no upstreams configured) |
| 2 | Tool execution | Call `execute_dynamic_tool(tool_name: "known_tool", arguments: {...})` | Returns upstream result or appropriate error |
| 3 | Fallback search | Stop Qdrant, call `find_tools` | Falls back to keyword search, returns results |
| 4 | Invalid tool | Call `execute_dynamic_tool(tool_name: "nonexistent")` | Returns `TOOL_NOT_FOUND` error |

### 7.3 Log Verification

| Log Entry | Level | Expected | Location |
|-----------|-------|----------|----------|
| Application started | INFO | Within 10s of start | stdout/logback |
| Tool indexing complete | INFO | After upstream connection | stdout/logback |
| Health check results | INFO | Every 30s | stdout/logback |
| No ERROR/FATAL entries | — | No unexpected errors | stdout/logback |

### 7.4 Monitoring

- [ ] Application logs visible (stdout or logback file)
- [ ] No unexpected ERROR entries in logs
- [ ] Upstream server health status shows CONNECTED
- [ ] Tool count matches expected (from upstream servers)

---

## 8. Rollback Plan

### 8.1 Rollback Decision Criteria

| Condition | Action |
|-----------|--------|
| Application fails to start | Rollback to previous JAR |
| Upstream servers cannot connect | Check config, rollback config if needed |
| Tool discovery returns no results | Check Vector DB, rollback if index corrupted |
| Performance degradation > 50% | Rollback to previous version |

### 8.2 Rollback Steps

| Step | Action | Command | Verification |
|------|--------|---------|-------------|
| 1 | Stop current application | `Ctrl+C` or `kill <PID>` or `docker stop mcp-orchestrator` | Process terminated |
| 2 | Restore previous JAR | `cp ~/mcp-orchestrator/mcp-orchestrator-all.jar.bak ~/mcp-orchestrator/mcp-orchestrator-all.jar` | Previous version restored |
| 3 | Restore previous config | `cp ~/mcp-orchestrator/application.yml.bak ~/mcp-orchestrator/application.yml` | Previous config restored |
| 4 | Restore FAISS index (if corrupted) | `cp ~/.mcp-orchestrator/faiss.index.bak ~/.mcp-orchestrator/faiss.index` | Previous index restored |
| 5 | Start previous version | `java -jar ~/mcp-orchestrator/mcp-orchestrator-all.jar` | Application starts successfully |
| 6 | Verify rollback | Run smoke tests (Section 7.2) | All checks pass |

### 8.3 Rollback Time Estimate

| Action | Estimated Time |
|--------|---------------|
| Stop application | < 5s |
| Restore files | < 10s |
| Start previous version | < 30s |
| Verification | < 2 min |
| **Total** | **< 3 minutes** |

---

## 9. Environment-Specific Notes

### 9.1 DEV (Local Development)

- Run directly from IDE or `./gradlew run`
- Uses FAISS fallback (no Qdrant needed)
- `OPENAI_API_KEY` can be set in IDE run configuration
- Hot-reload config changes supported

### 9.2 UAT (Developer Machine — Testing)

- Build fat JAR: `./gradlew clean build`
- Run with test upstream servers configured
- Verify all 118 tests pass before UAT
- Use stdio mode for Kiro IDE integration testing

### 9.3 PROD (Developer Machine — Production Use)

- **Deployment Window:** Any time (local tool, no shared infrastructure)
- **Backup:** Save previous JAR and config before upgrading
- **Configuration:** Ensure `OPENAI_API_KEY` is set in shell profile (`.bashrc`, `.zshrc`, or Windows environment)
- **Qdrant (recommended for production):** Run Qdrant Docker container for better search quality
  ```bash
  docker run -d --name qdrant -p 6333:6333 -v qdrant_data:/qdrant/storage qdrant/qdrant:v1.9.0
  ```

---

## 10. Appendix

### Contacts

| Role | Name | Contact |
|------|------|---------|
| Dev Lead | Duc Nguyen Minh | MTO Project |
| DevOps | DevOps Agent | Automated |

### Related Tickets

| Ticket | Summary | Relationship |
|--------|---------|-------------|
| MTO-5 | Create MCP Tool Orchestration | Main ticket |

### Build Commands Quick Reference

```bash
# Build fat JAR
./gradlew clean build

# Run tests only
./gradlew test

# Run application (dev mode)
./gradlew run

# Fat JAR location
build/libs/mcp-orchestrator-all.jar
```
