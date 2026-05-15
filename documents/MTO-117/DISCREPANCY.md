# Discrepancy Report — MTO-117

## Between BRD and FSD / Existing Implementation

---

### DISC-1: Field Naming Convention Mismatch

| Aspect | BRD | FSD | Existing Code (KbSyncTriggerHandler) |
|--------|-----|-----|--------------------------------------|
| Project key field | `projectKey` (camelCase) | `projectKey` (camelCase) | `project_key` (snake_case) |
| Full sync field | `fullSync` (camelCase) | `fullSync` (camelCase) | `full_sync` (snake_case) |
| Priority field | `priority` | `priority` | `priority` |

**Impact:** Agents currently calling `kb_sync_trigger` use `project_key` (snake_case). After migration to unified `jira_project_sync`, the field becomes `projectKey` (camelCase).

**Resolution:** The unified handler uses **camelCase** (`projectKey`) as specified in BRD. This is a breaking change for any agent that was calling `kb_sync_trigger` directly with `project_key`. However, since `kb_sync_trigger` is being removed (merged into `jira_project_sync`), agents must update their calls regardless. The TDD documents this in §15.2.

**Severity:** Low — `kb_sync_trigger` is being removed anyway; agents must update to use `jira_project_sync`.

---

### DISC-2: StatusToolHandler projectKey Requirement

| Aspect | BRD Story 2 | FSD §3.2 | Existing Code (StatusToolHandler) |
|--------|-------------|----------|-----------------------------------|
| `projectKey` required? | No (optional) | No (optional) | **Yes (required)** — returns error if missing |

**Impact:** The existing `StatusToolHandler` on orchestrator-server requires `projectKey` and returns an error if not provided. The BRD and FSD specify it as optional — when omitted, return global queue metrics only.

**Resolution:** The unified `JiraSyncStatusHandler` follows BRD/FSD — `projectKey` is **optional**. When provided: return project progress + queue metrics. When omitted: return queue metrics only. This is an enhancement over the current orchestrator behavior.

**Severity:** None — the unified handler is more capable (superset of current behavior).

---

### DISC-3: SyncToolHandler Response Format

| Aspect | BRD Story 1 | FSD §3.1 | Existing Code (SyncToolHandler) |
|--------|-------------|----------|----------------------------------|
| Response status | `"queued"` | `"queued"` | `"started"` |
| Response includes | `taskId`, `projectKey`, `priority` | `taskId`, `projectKey`, `priority`, `fullSync` | `estimatedIssues`, `projectKey`, `fullSync` |
| Task ID | UUID from queue | UUID from queue | Not returned (fire-and-forget) |

**Impact:** Agents parsing the response from `jira_project_sync` on orchestrator-server expect `{status:"started", estimatedIssues}`. After migration, they get `{status:"queued", taskId, projectKey, priority, fullSync}`.

**Resolution:** The unified handler uses the BRD/FSD response format (`status:"queued"` + `taskId`). Since the tool name remains `jira_project_sync`, agents that only check for success (no error) will work fine. Agents that parse specific fields need minor prompt updates.

**Severity:** Low — response is richer (more info), and the key indicator (no `isError`) remains the same.

---

### Summary

| # | Discrepancy | Severity | Resolution |
|---|-------------|----------|------------|
| DISC-1 | Field naming (camelCase vs snake_case) | Low | Use camelCase per BRD |
| DISC-2 | projectKey optional vs required | None | Follow BRD (optional) — enhancement |
| DISC-3 | Response format (started vs queued) | Low | Follow BRD (queued + taskId) |

All discrepancies are resolved in favor of the BRD specification, which represents the canonical business requirements. The FSD aligns with BRD. Only the existing code implementations differ, and those are being replaced.
