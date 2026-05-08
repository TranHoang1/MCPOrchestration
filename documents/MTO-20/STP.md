# Software Test Plan (STP)

## MCPOrchestration — MTO-20: MCP Tool Integration – Sync & Graph Tools

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-20 |
| Title | MCP Tool Integration – Sync & Graph Tools |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-20.docx |
| Related FSD | FSD-v1-MTO-20.docx |
| Related TDD | TDD-v1-MTO-20.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | QA Agent | Initial STP |

---

## 1. Introduction

### 1.1 Purpose

Test plan for three MCP tools: jira_project_sync, jira_sync_status, jira_ticket_graph — exposing Jira sync and graph capabilities to AI agents.

### 1.2 Test Objectives

- Verify tool registration and discovery via MCP protocol
- Validate input schema enforcement (JSON Schema validation)
- Ensure correct delegation to ProjectScanner and GraphDataRepository
- Confirm auto-approve configuration for read-only tools
- Verify error handling and response formats

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Tools | Automation |
|-------|-------|-------|------------|
| PBT | Input validation, schema edge cases | Kotest Property | 100% |
| UT | Tool handlers, schema validation, response mapping | Kotest + MockK | 100% |
| IT | MCP protocol integration, tool registration | Ktor testApplication | 100% |
| E2E-API | Full MCP tool call lifecycle | MCP client + Testcontainers | 100% |
| E2E-UI | N/A | — | — |
| SIT | Real MCP client (Claude/Kiro) calling tools | Manual | 0% |

---

## 3. Requirements Traceability Matrix (RTM)

| Requirement ID | Description | Test Case IDs | Coverage |
|----------------|-------------|---------------|----------|
| UC-01 | jira_project_sync tool | TC-001, TC-002, TC-003, TC-004 | ✅ |
| UC-02 | jira_sync_status tool | TC-005, TC-006, TC-007 | ✅ |
| UC-03 | jira_ticket_graph tool | TC-008, TC-009, TC-010, TC-011, TC-012 | ✅ |
| BR-01 | Async execution | TC-001 | ✅ |
| BR-02 | One sync per project | TC-002 | ✅ |
| BR-03 | Requires approval | TC-003 | ✅ |
| BR-04 | Return from DB | TC-005 | ✅ |
| BR-05 | never_synced status | TC-006 | ✅ |
| BR-06 | Phase breakdown | TC-007 | ✅ |
| BR-07 | BFS subgraph | TC-009 | ✅ |
| BR-08 | Full project graph | TC-008 | ✅ |
| BR-09 | Depth 1-5 | TC-010 | ✅ |
| BR-10 | Relationship filter | TC-011 | ✅ |
| BR-11 | Max 1000 nodes | TC-012 | ✅ |

---

## 4. Test Summary

| Level | Test Cases | Automated | Manual |
|-------|-----------|-----------|--------|
| PBT | 3 | 3 | 0 |
| UT | 10 | 10 | 0 |
| IT | 4 | 4 | 0 |
| E2E-API | 5 | 5 | 0 |
| SIT | 2 | 0 | 2 |
| **Total** | **24** | **22** | **2** |

---

## 5. Risk & Mitigation

| # | Risk | Impact | Mitigation |
|---|------|--------|------------|
| 1 | MCP SDK version incompatibility | High | Pin SDK version, test with specific client |
| 2 | Graph query performance with large datasets | Medium | Test with 1000+ nodes, add timeout |
| 3 | Auto-approve config not loaded | Medium | IT test verifies config loading |
