## Mô tả

Implement Ticket Crawler — lấy full content (description, comments) cho tickets, deduplication via content hash, build ticket graph, và ingest vào Knowledge Base.

## Scope

### TicketCrawler Class

- Lấy full content cho tickets chưa ingested hoặc content đã thay đổi
- Query: SELECT từ jira_ticket_cache WHERE content_hash IS NULL OR needs_refresh = true

### Content Deduplication

- SHA-256 hash của (description + comments concatenated)
- So sánh hash mới vs hash cũ trong jira_ticket_cache.content_hash
- Skip nếu hash không đổi (tiết kiệm KB ingestion cost)

### Deep Content Fetch

1. Gọi getIssue(key, fields="description,comment", expand="renderedFields")
2. Parse description (ADF → plain text hoặc markdown)
3. Parse comments (lấy body, author, created)
4. Compute content_hash
5. Update jira_ticket_cache với full content

### Ticket Graph Builder

- Parse issue links từ metadata_json
- Extract relationships: parent/child, blocks/is-blocked-by, relates-to, epic-link
- Upsert vào jira_ticket_graph table
- Bidirectional: nếu A blocks B → insert cả (A→B, blocks) và (B→A, is-blocked-by)

### KB Ingestion

- Format content cho vector DB ingestion:
  - Title: `{issue_key}: {summary}`
  - Content: description + comments (formatted)
  - Tags: project_key, issue_type, status, labels
  - Metadata: assignee, priority, sprint
- Gọi KB ingest tool (hoặc direct vector DB insert)

### Attachment Queue

- Scan attachments từ issue metadata
- Insert vào jira_attachment_queue (status = 'pending')
- Không download/process ở đây (Story 5 xử lý)

## Acceptance Criteria

- [ ] Fetch full content cho tickets chưa có content
- [ ] Content hash deduplication hoạt động (skip unchanged)
- [ ] Ticket graph edges được tạo đúng
- [ ] KB ingestion thành công (verify bằng KB search)
- [ ] Attachments được queue (không process)
- [ ] Unit tests cho hash logic, graph builder
- [ ] Integration test với KB

## Story Points: 8

## Dependencies

- **Blocked by:** Story 1 (Database Schema)
- **Blocked by:** Story 2 (Jira REST Client)
- **Blocked by:** Story 3 (Project Scanner) — cần ticket cache data
