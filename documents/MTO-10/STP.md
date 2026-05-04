# Software Test Plan (STP)

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
| Related BRD | documents/MTO-10/BRD.md |
| Related FSD | documents/MTO-10/FSD.md |
| Related TDD | documents/MTO-10/TDD.md |

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
| 1.0 | 2026-05-03 | QA Agent | Initial draft — test plan for MTO-10 covering all 7 functional requirements |
| 1.1 | 2026-05-03 | QA Agent | Removed broken diagram references in Section 4 (Test Environment Verification); updated document version header for DOCX export compliance |
| 1.2 | 2026-05-04 | QA Agent | Integrated testing for Dual Operational Modes (Standalone SSE vs Local Bridge Stdio) and updated RTM. |

---

## 1. Introduction

### 1.1 Purpose
This Software Test Plan (STP) outlines the testing strategy, scope, levels, and execution flow for the MTO-10 upgrade. It ensures that the newly integrated local embedding services (Ollama/LMStudio), PostgreSQL pgvector vector database, and the new runtime tool management features (toggle, reset, auto-approve, config sync, and tool filtering) function correctly and securely without degrading existing system performance.

### 1.2 Scope
**In Scope:**
- **Local Embedding Services:** Ollama and LMStudio integration.
- **pgvector Vector Database:** Hybrid search (cosine similarity + tsvector keyword).
- **Tool Management:** `toggle_tool`, `reset_tools`, `manage_auto_approve` MCP tools.
- **Config-DB Sync:** Application startup synchronization logic.
- **Tool Filtering:** Allowlist and blocklist functionality.
- **Dual Operational Modes**: Verification of Unified Entry Point branching (Stdio vs SSE).
- **Configuration Merging**: Validation of JSON-based overrides for embedding and vector_db settings.
- **Local Discovery**: Testing semantic search using local providers (Ollama, LMStudio).
- **Regression:** Existing tools (`find_tools`, `execute_dynamic_tool`) compatibility and performance.

**Out of Scope:**
- External UI/dashboard logic (unless integrated via API).
- Load/stress testing of external systems (Ollama/LMStudio).

---

## 2. Test Strategy

We will employ a comprehensive 6-level testing approach, shifting left with property-based testing and ensuring robustness through automated E2E and SIT tests.

### 2.1 Test Levels

1. **Property-Based Testing (PBT)**: Test vector transformations, hybrid search scoring logic, and configuration parsing with randomized generative inputs.
2. **Unit Testing (UT)**: Test individual components (e.g., `OllamaEmbeddingService`, `ToolFilterService`, `ToolManagementService`) using mocks for external dependencies.
3. **Integration Testing**:
    - Verify `Main.kt` correctly selects transport based on configuration.
    - Verify `JsonConfigLoader` correctly merges client-side settings into the global config.
    - Verify vector padding/truncation during model swaps.
    - Test interactions with PostgreSQL pgvector (using Testcontainers) and Ktor HTTP clients against wiremocked Ollama/LMStudio APIs.
4. **E2E-API**: Test the exposed MCP endpoints via JSON-RPC, verifying end-to-end functionality of `toggle_tool`, `reset_tools`, `manage_auto_approve`, `find_tools`, and `execute_dynamic_tool`.
5. **E2E-UI**: (If applicable via Kiro IDE or MCP Inspector) Verify the tools render correctly in the MCP client and respond to operator inputs.
6. **System Integration Testing (SIT)**: Test the full orchestrated flow with a real Ollama instance, real PostgreSQL DB, and upstream MCP servers running.

### 2.2 Traceability Matrix (RTM)

| Req ID | Requirement Description | Test Levels | Test Case IDs |
|--------|-------------------------|-------------|---------------|
| FR-1 | Local Embedding Service (Ollama/LMStudio) | UT, IT, E2E-API | TC-EMB-01, TC-EMB-02 |
| FR-2 | PostgreSQL pgvector Vector Storage | PBT, UT, IT | TC-VEC-01, TC-VEC-02 |
| FR-3 | Runtime Tool Mgmt: toggle_tool | UT, IT, E2E-API, SIT | TC-TOG-01, TC-TOG-02 |
| FR-4 | Runtime Tool Mgmt: reset_tools | UT, IT, E2E-API | TC-RST-01, TC-RST-02 |
| FR-5 | Runtime Tool Mgmt: manage_auto_approve | UT, IT, E2E-API, SIT | TC-AAP-01, TC-AAP-02 |
| FR-6 | Config-DB Sync on startup | IT, SIT | TC-SNC-01 |
| FR-7 | Tool Filtering from Config | UT, IT | TC-FLT-01, TC-FLT-02 |
| Story 8| Dual Operational Modes | IT, E2E-API, SIT | TC-MOD-01, TC-MOD-02 |

---

## 3. Test Environment & Resources

### 3.1 Environment Setup
- **Database:** PostgreSQL 16+ with pgvector 0.7+ (via Docker/Testcontainers).
- **Embeddings:** Ollama running locally at `localhost:11434` with model `nomic-embed-text`. LMStudio mocked or local instance.
- **Config:** `mcp-servers.json` initialized with sample servers and tool filters.

### 3.2 Dependencies
- Testcontainers for PostgreSQL pgvector.
- WireMock for simulating Ollama/LMStudio HTTP API responses during IT.
- MCP client (e.g., MCP Inspector) for E2E-API verification.

---

## 4. Test Environment Verification

The test environment must be verified before each execution cycle to ensure all local services and database extensions are operational.

---

## 5. Execution & Reporting

- **Automation Goal:** 100% automation for PBT, UT, IT, E2E-API. 90% automation for SIT. Manual testing reserved for Edge UX cases.
- **Reporting:** Kotest/JUnit HTML reports, coverage reports (JaCoCo).
- **Exit Criteria:** 100% UT/IT pass rate. No critical or high severity bugs open. All BRD requirements verified in E2E-API/SIT.
