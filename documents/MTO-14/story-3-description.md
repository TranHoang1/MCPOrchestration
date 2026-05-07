## Mô tả

Implement Project Scanner — quét toàn bộ Jira project với JQL, lấy metadata nhẹ, hỗ trợ incremental và resumable.

## Scope

### ProjectScanner Class

- Scan toàn bộ project bằng JQL: `project = "{KEY}" ORDER BY updated DESC`
- Chỉ lấy metadata nhẹ: summary, status, type, priority, assignee, links, parent, updated
- Incremental: JQL filter `updated > "{last_sync_time}"`
- Resumable: bắt đầu từ `last_offset` (lưu trong jira_sync_state)

### Concurrency Model

- Kotlin Coroutines + Semaphore (configurable: default 5 concurrent requests)
- CoroutineScope với SupervisorJob (1 failure không kill toàn bộ)
- Structured concurrency: cancel toàn bộ khi job bị stop

### Processing Pipeline

1. Fetch page (50 issues/page) từ Jira
2. Parse response → list of JiraTicketMetadata
3. Upsert vào jira_ticket_cache (batch insert/update)
4. Update jira_sync_state (offset, synced_count)
5. Repeat until no more results

### Upsert Logic

- INSERT ON CONFLICT (issue_key) DO UPDATE
- Chỉ update nếu updated_at > existing updated_at
- Track total_issues vs synced_issues cho progress

### Error Handling

- Network error → retry (delegate to JiraRestClient)
- Partial failure → save checkpoint, resume later
- Rate limit hit → pause, wait, resume

## Acceptance Criteria

- [ ] Scan toàn bộ project (pagination hoạt động)
- [ ] Incremental: chỉ fetch tickets updated sau last_sync_time
- [ ] Resumable: stop giữa chừng, restart từ last_offset
- [ ] Concurrent processing với Semaphore
- [ ] Progress tracking (synced_issues / total_issues)
- [ ] Unit tests cho scan logic
- [ ] Integration test với mock Jira responses

## Story Points: 8

## Dependencies

- **Blocked by:** Story 1 (Database Schema) — cần tables để upsert
- **Blocked by:** Story 2 (Jira REST Client) — cần client để gọi API
