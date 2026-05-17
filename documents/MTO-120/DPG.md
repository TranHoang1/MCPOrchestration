# Deployment Guide (DPG)

## MTO-120: Local Code Intelligence — SQLite Index + Semantic Search

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-120 |
| Title | Local Code Intelligence — Deployment Guide |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2025-07-10 |
| Status | Draft |
| Related TDD | TDD-v1-MTO-121.docx |

---

## 1. Deployment Overview

### 1.1 What's Being Deployed

The Local Code Intelligence feature adds SQLite-based code indexing to all bridge clients. This is a **client-side feature** — no server infrastructure changes required. Deployment consists of:

1. **Kotlin Bridge** (orchestrator-bridge) — new `codeintel` package with SQLite + Ollama integration
2. **Node.js Bridge** (mcp-client-bridge) — new `code-intel/` module with better-sqlite3
3. **Python Bridge** (mcp-bridge-python) — new `code_intel/` package with sqlite3
4. **PowerShell Bridge** (mcp-bridge-powershell) — new `code-intel.ps1` script
5. **Bash Bridge** (mcp-bridge-bash) — new `code-intel.sh` script

### 1.2 Deployment Type

| Aspect | Value |
|--------|-------|
| Type | Feature release (non-breaking) |
| Risk Level | Low — additive feature, no existing behavior changed |
| Rollback | Remove codeintel package / revert to previous version |
| Downtime | Zero — bridge restarts are instant |
| Dependencies | sqlite-jdbc 3.46.1.0 (new), Ollama (optional) |

---

## 2. Prerequisites

### 2.1 Build Requirements

| Component | Requirement |
|-----------|-------------|
| JDK | 21+ (Corretto recommended) |
| Gradle | 8.x (wrapper included) |
| Node.js | 20+ |
| Python | 3.11+ |
| SQLite CLI | 3.40+ (for PowerShell/Bash bridges) |

### 2.2 Runtime Requirements

| Component | Required | Optional |
|-----------|----------|----------|
| JVM heap | 256MB minimum | 512MB for large workspaces |
| Disk space | 50MB per workspace index | — |
| Ollama | No | Yes — for semantic search (Layer 2+3) |
| GPU | No | RTX 4060 8GB+ for embedding generation |

### 2.3 Ollama Setup (Optional)

```bash
# Install Ollama (if not already installed)
# Windows: Download from https://ollama.com/download
# Linux: curl -fsSL https://ollama.com/install.sh | sh

# Pull required models
ollama pull nomic-embed-text    # 768-dim embeddings (274MB)
ollama pull qwen3:8b            # AI summarization (4.9GB)

# Verify
ollama list
```

---

## 3. Build Steps

### 3.1 Build Kotlin Bridge (Shadow JAR)

```bash
cd MCPOrchestration
./gradlew :orchestrator-bridge:shadowJar
# Output: orchestrator-bridge/build/libs/orchestrator-bridge-all.jar
```

### 3.2 Build Server Bundle

```bash
./gradlew packageServerBundle
# Output: build/distributions/mcp-orchestration-server-1.3.1.zip
```

### 3.3 Build Node.js Bridge

```bash
cd mcp-client-bridge
npm ci
npm run build
npm test
```

### 3.4 Build Python Bridge

```bash
cd mcp-bridge-python
pip install -e .
python -m py_compile src/mcp_bridge/__main__.py
```

---

## 4. Deployment Steps

### 4.1 Version Bump (Pre-Release)

Per release-versioning rules, bump all publishable modules:

```bash
# 1. Update root build.gradle.kts
#    version = "1.4.0"  (or appropriate next version)

# 2. Update mcp-client-bridge/package.json
#    "version": "1.4.0"

# 3. Update mcp-bridge-python/pyproject.toml
#    version = "1.4.0"

# 4. Commit
git add -A
git commit -m "chore: bump versions to 1.4.0 for release"
```

### 4.2 Run Tests Locally

```bash
# Kotlin (bridge + kb-server)
./gradlew :orchestrator-bridge:test :kb-server:test

# Node.js
cd mcp-client-bridge && npm test

# Python
cd mcp-bridge-python && python -m py_compile src/mcp_bridge/__main__.py
```

### 4.3 Create Release Tag

```bash
git tag v1.4.0 -m "feat: Local Code Intelligence — SQLite Index + Semantic Search (MTO-120)"
git push origin master --tags
```

### 4.4 Monitor CI

```bash
gh run watch
# Wait for all jobs to pass:
# - build-server ✅
# - build-bridge-nodejs ✅
# - build-bridge-python ✅
# - lint-scripts ✅
```

### 4.5 Verify Release Artifacts

After CI completes, verify GitHub Release has:
- `mcp-orchestration-server-1.4.0.zip`
- `mcp-orchestrator-all.jar`
- `mcp-bridge-nodejs.tar.gz`
- `mcp-bridge.sh`
- `mcp-bridge.ps1`

### 4.6 Verify npm/PyPI Publish

```bash
# npm
npm info @orchestrator/mcp-bridge version
# Should show 1.4.0

# PyPI
pip index versions mcp-bridge-python
# Should show 1.4.0
```

---

## 5. Post-Deployment Verification

### 5.1 Sanity Test — Kotlin Bridge

```bash
# Start bridge
java -jar orchestrator-bridge-all.jar --workspace /path/to/project

# Verify code intelligence initialized
# Look for log: "Code intelligence initialized: {workspace}/.bridge/code-index.db"

# Verify MCP tools available
# Send tools/list request → should include code_search, code_symbols, etc.
```

### 5.2 Sanity Test — Node.js Bridge

```bash
cd mcp-client-bridge
node dist/index.js --workspace /path/to/project

# Verify: .bridge/code-index.db created
# Verify: code_search tool responds
```

### 5.3 Sanity Test — Code Intelligence Features

```bash
# 1. Verify index created
ls /path/to/project/.bridge/code-index.db

# 2. Verify FTS5 search works
# Call code_search(query="main") → should return results

# 3. Verify code_index_status
# Call code_index_status() → status should be "ready"

# 4. If Ollama available:
# Call code_context(query="authentication") → search_method should be "embedding"
```

---

## 6. Rollback Plan

### 6.1 Rollback Trigger

Rollback if:
- Bridge fails to start after update
- Existing MCP tools stop working (regression)
- SQLite causes excessive disk I/O or memory usage
- Critical bug in code intelligence affecting other features

### 6.2 Rollback Steps

```bash
# 1. Revert to previous version
git checkout v1.3.1
./gradlew :orchestrator-bridge:shadowJar

# 2. Replace JAR
cp orchestrator-bridge/build/libs/orchestrator-bridge-all.jar /deploy/path/

# 3. Restart bridge
# Bridge will start without code intelligence (module not present in old version)

# 4. Clean up SQLite databases (optional)
# Users can delete .bridge/code-index.db in each workspace
```

### 6.3 Rollback Verification

- Bridge starts successfully
- All existing MCP tools respond correctly
- No code intelligence tools registered (expected after rollback)

---

## 7. Configuration

### 7.1 Code Intelligence Config File

Location: `{workspace}/.bridge/code-intelligence.json`

```json
{
  "enabled": true,
  "exclude_patterns": ["node_modules/**", "build/**", ".git/**", "*.min.js"],
  "max_file_size_kb": 500,
  "max_depth": 20,
  "ollama": {
    "enabled": true,
    "endpoint": "http://localhost:11434",
    "embedding_model": "nomic-embed-text",
    "summarization_model": "qwen3:8b",
    "timeout_ms": 5000
  },
  "indexing": {
    "debounce_ms": 2000,
    "max_concurrent_files": 4,
    "full_scan_on_startup": true
  }
}
```

### 7.2 Disabling Code Intelligence

To disable without rollback:
```json
{
  "enabled": false
}
```

Or delete the `.bridge/` directory entirely.

---

## 8. Monitoring & Troubleshooting

### 8.1 Health Indicators

| Indicator | Healthy | Unhealthy |
|-----------|---------|-----------|
| code_index_status.status | "ready" | "error" |
| DB file size | < 50MB per 10K files | > 100MB |
| Search response time | < 200ms | > 1000ms |
| Memory usage | < 256MB heap | > 512MB |

### 8.2 Common Issues

| Issue | Cause | Fix |
|-------|-------|-----|
| "Code intelligence disabled" in logs | sqlite-jdbc not on classpath | Verify dependency in build.gradle.kts |
| Slow initial scan | Large workspace, no .gitignore | Add exclude patterns to config |
| "Ollama unavailable" | Ollama not running | Start Ollama or set ollama.enabled=false |
| DB locked errors | Concurrent write attempts | Verify WAL mode enabled (automatic) |
| High memory during indexing | Too many concurrent files | Reduce max_concurrent_files in config |

### 8.3 Log Levels

```
# Enable debug logging for code intelligence
-Dlogback.configurationFile=logback-debug.xml

# Or set in application.yml:
logging:
  level:
    com.orchestrator.mcp.bridge.codeintel: DEBUG
```

---

## 9. Security Considerations

| Concern | Mitigation |
|---------|-----------|
| SQLite file permissions | DB created with user-only permissions (600) |
| SQL injection via search | All queries use parameterized statements |
| Path traversal | All paths validated as relative, within workspace |
| Ollama network | Local-only (localhost:11434), no external calls |
| Sensitive code in index | Index stays local, never transmitted to server |

---

## 10. Appendix

### 10.1 New Dependencies Added

| Module | Dependency | Version | Purpose |
|--------|-----------|---------|---------|
| orchestrator-bridge | org.xerial:sqlite-jdbc | 3.46.1.0 | SQLite JDBC driver |
| mcp-client-bridge | better-sqlite3 | ^11.0.0 | Node.js SQLite |
| mcp-bridge-python | (stdlib sqlite3) | — | Python SQLite |

### 10.2 Files Changed

| Module | Files Added/Modified |
|--------|---------------------|
| orchestrator-bridge | codeintel/ (10 packages, ~30 files) |
| orchestrator-bridge | BridgeServer.kt (integration point) |
| mcp-client-bridge | src/code-intel/ (5 files) |
| mcp-bridge-python | src/mcp_bridge/code_intel/ (5 files) |
| mcp-bridge-powershell | code-intel.ps1 (1 file) |
| mcp-bridge-bash | code-intel.sh (1 file) |
