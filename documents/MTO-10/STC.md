# Software Test Cases (STC)

## MCP Orchestration Server — MTO-10: Upgrade MCP Orchestrator: Local Embedding, pgvector, Tool Management & Auto-Approve

---

## Document Information

| Version | Value |
|-------|-------|
| Jira Ticket | MTO-10 |
| Title | Upgrade MCP Orchestrator: Local Embedding, pgvector, Tool Management & Auto-Approve |
| Author | QA Agent |
| Version | 1.3 |
| Date | 2026-05-04 |
| Status | TA Enriched |
| Related STP | documents/MTO-10/STP.md |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | QA Agent – Quality Assurance | Create document |
| Peer Reviewer | Duc Nguyen – Project Lead | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-03 | QA Agent | Initial draft — test cases for all 7 functional requirements of MTO-10 |
| 1.1 | 2026-05-03 | QA Agent | Updated document version header for DOCX export compliance |
| 1.2 | 2026-05-04 | QA Agent | Added test cases for Dual Operational Modes (Mode detection and Transport switching). |
| 1.3 | 2026-05-04 | QA Agent | Added Unified Entry Point and JSON config merge validation cases. |

---

## 1. Property-Based Testing (PBT)

| ID | Test Case Description | Test Data & Constraints | Expected Result |
|----|-----------------------|-------------------------|-----------------|
| TC-VEC-PBT-01 | Vector padding/truncation to 768 dimensions | Generate arbitrary float arrays of size 1 to 4096. | Adjusted array size must be exactly 768. Missing values zero-padded, extra values truncated without exception. |
| TC-HYB-PBT-02 | Hybrid scoring logic properties | Vector scores: 0.0 to 1.0; Keyword scores: 0.0 to 1.0; Weights: 0.7, 0.3 | Total score must be exactly `(V * 0.7) + (K * 0.3)`. Must never exceed 1.0. |

---

## 2. Unit Testing (UT)

| ID | Module | Scenario | Input / Action | Expected Result |
|----|--------|----------|----------------|-----------------|
| TC-EMB-UT-01 | `OllamaEmbeddingService` | Successful HTTP request | Mock client returns `{"embedding": [0.1, 0.2]}` | Service parses and returns `FloatArray` of size 768 properly zero-padded. |
| TC-EMB-UT-02 | `OllamaEmbeddingService` | Missing embedding field | Mock client returns `{}` | Throws `EmbeddingServiceException`. |
| TC-FLT-UT-01 | `ToolFilterService` | Blocklist mode | `mode="blocklist"`, `tools=["toolA"]`, input: `[toolA, toolB]` | Returns `[toolB]`. |
| TC-FLT-UT-02 | `ToolFilterService` | Allowlist mode | `mode="allowlist"`, `tools=["toolA"]`, input: `[toolA, toolB]` | Returns `[toolA]`. |

---

## 3. Integration Testing (IT)

| ID | Component | Scenario | Prerequisites & Steps | Expected Result |
|----|-----------|----------|-----------------------|-----------------|
| TC-VEC-IT-01 | `PgVectorDbClient` | Upsert and Vector Search | PostgreSQL Testcontainer with pgvector. 1. Upsert 3 `VectorPoint` records. 2. `search` with exact vector. | Returns records successfully. Nearest vector has highest score. Database schema is auto-created. |
| TC-VEC-IT-02 | `PgVectorDbClient` | Upsert conflict | 1. Upsert `(serverA, toolA)` payload. 2. Upsert same IDs with new payload. | Row count remains 1, updated timestamp changed, payload updated. |
| TC-SNC-IT-01 | `ConfigDbSyncService` | Sync initial config | 1. `mcp-servers.json` has 1 new server. 2. Run `sync()`. | DB `server_config` table has 1 record mirroring the JSON file. |

---

## 4. E2E-API Testing

| ID | Method/Tool | Scenario | Input JSON RPC | Expected Result |
|----|-------------|----------|----------------|-----------------|
| TC-TOG-API-01 | `toggle_tool` | Disable a specific tool | `{"tool_name":"jira_get_issue", "enabled":false}` | `{"toggled":"jira_get_issue","enabled":false,"scope":"tool"}`. Tool hidden from `find_tools`. |
| TC-TOG-API-02 | `execute_dynamic_tool` | Call disabled tool | Call `jira_get_issue` after TC-TOG-API-01 | Returns error code `TOOL_DISABLED`. |
| TC-RST-API-01 | `reset_tools` | Reset toggle states | `{"server_name":"atlassian","reindex":false}` | DB toggle table clears for the server. `find_tools` returns all tools. |
| TC-AAP-API-01 | `manage_auto_approve` | Add tool to auto_approve | `{"tool_name":"jira_edit_issue","auto_approve":true}` | `mcp-servers.json` updated immediately. Returns success. |

---

## 5. E2E-UI & SIT (System Integration)

| ID | Feature | Scenario | Steps | Expected Result |
|----|---------|----------|-------|-----------------|
| TC-SIT-01 | Full Orchestration Pipeline | End-to-end sync, discovery, search, toggle | 1. Start Server. 2. Sync runs. 3. Call `find_tools` (returns 10). 4. Call `toggle_tool` disable server. 5. Call `find_tools`. | Step 3 works. Step 5 returns 0 tools for that server. |
| TC-UX-01 | Config file safety | Write collision prevention | Run `manage_auto_approve` concurrently from 5 clients. | Config file is atomically written. No corrupted JSON. |

---

## 6. Mode & Transport Testing

| ID | Scenario | Input / Action | Expected Result |
|----|----------|----------------|-----------------|
| TC-MOD-01 | Standalone Mode Detection | Set `orchestrator.server.protocol: sse` in `application.yml` | Application starts Ktor SSE server on defined port. |
| TC-MOD-02 | Local Bridge Mode Detection | Set `orchestrator.server.protocol: stdio` (or leave default) | Application starts Stdio transport and waits for JSON-RPC over stdin. |
| TC-MOD-03 | Upstream Transport Mix | `mcp-servers.json` with 1 Stdio and 1 SSE server | Orchestrator successfully connects to and indexes tools from both servers. |
| TC-MOD-04 | Unified Entry Point Branching | Start app with `--port` or `protocol: sse` | `Main.kt` must branch to `Application.kt` (Ktor). Logs appear in `stdout`. |
| TC-MOD-05 | Stdio Output Safety | Start app in `stdio` mode | `Main.kt` must redirect `System.out` logs to `System.err`. `stdout` only contains valid JSON-RPC. |
| TC-MOD-06 | JSON Configuration Merging | Provide `config.json` with `embedding` overrides | App starts in `stdio` mode using the provider/model defined in `config.json` instead of YAML defaults. |
| TC-MOD-07 | Environment Variable Overrides | Set `EMBEDDING_PROVIDER=ollama` in environment | App uses `ollama` as the provider even if YAML/JSON specify `openai`. |

---

## 6. Test Data

- **Dummy `mcp-servers.json`:**
```json
{
  "mcpServers": {
    "test_server": {
      "command": "node",
      "args": ["dummy.js"],
      "toolFilter": { "mode": "blocklist", "tools": ["bad_tool"] },
      "autoApprove": []
    }
  }
}
```
- **Vector Points CSV:** Available in `src/test/resources/vectors.csv` for IT ingestion.
