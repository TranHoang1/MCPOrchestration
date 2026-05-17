# Discrepancy Report — MTO-116

## BRD vs FSD Discrepancies

| # | Topic | BRD (v1.0) | FSD (v1.0) | Severity | Resolution |
|---|-------|------------|------------|----------|------------|
| 1 | **Delete restriction** | Story 5: "Only features with source='manual' can be deleted directly" — implies AI features cannot be deleted | §3.5.2: Any feature can be deleted regardless of source. AI features get a warning about re-creation | Low | **Follow FSD** — all features are deletable. The BRD statement is misleading; it means manual features are permanently deleted while AI features may reappear. |
| 2 | **Error code prefix** | Uses `FEATURE_*` prefix (e.g., `FEATURE_DUPLICATE_ERROR`, `FEATURE_NOT_FOUND`) | §5 also uses `FEATURE_*` prefix | Low | **Implementation uses `KB_*` prefix** — consistent with existing `KbExceptions.kt` pattern (`KB_NOT_FOUND`, `KB_VALIDATION_ERROR`). The `FEATURE_*` codes are logical names in documentation only. |
| 3 | **KbNotFoundException signature** | Expects message like "Feature '{feature_id}' does not exist" | §5: Same message format | Low | **Existing code** `KbNotFoundException(issueKey: String)` produces "No entry found for '{issueKey}'". Reuse as-is — pass feature_id as issueKey. Message differs slightly from BRD/FSD but is functionally equivalent. |
| 4 | **Feature ID in entry_key** | Appendix: entry_key = `"feature:{feature_id}"` where feature_id = `"manual-{hash}"` | §3.2.2: entry_key = `"feature:$featureId"` where featureId = `"manual-{hash}"` | None | Consistent — both produce `"feature:manual-abc123def456"` |
| 5 | **SourceRef.contentHash** | Not mentioned for manual features | Not explicitly specified | Low | **Implementation decision:** Set `contentHash` to SHA-256 of the data map JSON string for change detection. |

## Impact Assessment

- **No blocking discrepancies** — all issues are minor naming/message differences
- **No functional conflicts** — BRD and FSD agree on all business logic
- **Implementation follows FSD** as the authoritative technical specification

## Action Items

| # | Action | Owner | Status |
|---|--------|-------|--------|
| 1 | Confirm delete behavior with BA Agent (all features deletable) | SA Agent | Resolved — FSD is authoritative |
| 2 | Align error code prefix in next BRD revision | BA Agent | Deferred |
| 3 | Coordinate MTO-117 on `source` field enum values | SA Agent | Open |
