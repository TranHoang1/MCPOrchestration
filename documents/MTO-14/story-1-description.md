## Mô tả

Tạo database schema và implement Sync State Manager cho Jira Project Sync Service.

## Scope

### Database Tables (Flyway Migration)

1. **jira_sync_state** — Tracking trạng thái sync job
   - project_key (PK), last_sync_time, last_offset, status, total_issues, synced_issues, error_message

2. **jira_ticket_cache** — Cache ticket data
   - issue_key (PK), project_key, summary, status, issue_type, priority, assignee, updated_at, content_hash, description, comments_json (JSONB), metadata_json (JSONB), synced_at

3. **jira_ticket_graph** — Relationships giữa tickets
   - id (PK), source_key, target_key, relationship_type
   - UNIQUE constraint (source_key, target_key, relationship_type)

4. **jira_attachment_queue** — Queue xử lý attachments
   - id (PK), issue_key, attachment_url, filename, mime_type, status, created_at, processed_at

### SyncStateManager Implementation

- `getState(projectKey): SyncState?`
- `updateProgress(projectKey, offset, syncedCount)`
- `markCompleted(projectKey)`
- `markFailed(projectKey, errorMessage)`
- `markRunning(projectKey, totalIssues)`
- `resetState(projectKey)`

### Indexes

- jira_ticket_cache: INDEX on (project_key, updated_at)
- jira_ticket_graph: INDEX on (source_key), INDEX on (target_key)
- jira_attachment_queue: INDEX on (status, created_at)

## Acceptance Criteria

- [ ] Flyway migration scripts tạo đúng 4 tables với indexes
- [ ] SyncStateManager CRUD operations hoạt động đúng
- [ ] Resumable: getState trả về last_offset để resume
- [ ] Unit tests cho SyncStateManager
- [ ] Integration test với Testcontainers PostgreSQL

## Story Points: 5

## Dependencies

- Không có dependency (foundation story)
