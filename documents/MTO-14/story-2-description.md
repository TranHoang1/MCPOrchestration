## Mô tả

Implement Ktor HTTP Client gọi trực tiếp Jira REST API v3 (không qua MCP Atlassian) cho Sync Service.

## Scope

### JiraRestClient Class

- Ktor HttpClient với JSON serialization (kotlinx.serialization)
- Authentication: API Token (Basic Auth header)
- Base URL configuration từ application.yml

### API Endpoints

1. **searchIssues(jql, startAt, maxResults, fields)** — POST /rest/api/3/search
   - Pagination support (startAt, maxResults)
   - Field selection (chỉ lấy fields cần thiết)

2. **getIssue(issueKey, fields, expand)** — GET /rest/api/3/issue/{issueKey}
   - Full content: description, comments
   - Expand: changelog, renderedFields

3. **getAttachments(issueKey)** — GET /rest/api/3/issue/{issueKey}?fields=attachment
   - Trả về list attachment metadata (url, filename, mimeType, size)

4. **downloadAttachment(url)** — GET attachment content URL
   - Stream download (không load toàn bộ vào memory)
   - Return InputStream hoặc ByteArray

### Rate Limiting & Retry

- Token bucket rate limiter (configurable: default 10 req/s)
- Exponential backoff retry (max 3 retries)
- Handle 429 Too Many Requests (respect Retry-After header)
- Handle 5xx server errors
- Timeout configuration (connect: 10s, request: 30s)

### Configuration

```yaml
jira:
  baseUrl: https://your-domain.atlassian.net
  email: user@example.com
  apiToken: ${JIRA_API_TOKEN}
  rateLimit:
    requestsPerSecond: 10
  retry:
    maxAttempts: 3
    initialDelay: 1000ms
  timeout:
    connect: 10s
    request: 30s
```

## Acceptance Criteria

- [ ] Ktor HttpClient configured với JSON serialization
- [ ] Basic Auth header tự động inject
- [ ] searchIssues trả về paginated results
- [ ] getIssue trả về full content
- [ ] Rate limiter hoạt động (không vượt quá configured limit)
- [ ] Retry logic xử lý 429 và 5xx
- [ ] Unit tests với mock server (MockEngine)
- [ ] Integration test với real Jira (optional, manual trigger)

## Story Points: 5

## Dependencies

- Không có hard dependency (có thể develop song song với Story 1)
