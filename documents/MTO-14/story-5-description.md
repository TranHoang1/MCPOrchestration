## Mô tả

Implement Attachment Processor — background coroutine worker xử lý attachment queue: download, extract text, vectorize và ingest vào KB.

## Scope

### AttachmentProcessor Class

- Background coroutine loop (long-running)
- Poll jira_attachment_queue WHERE status = 'pending' ORDER BY created_at
- Configurable batch size (default: 5)
- Configurable poll interval (default: 30s khi idle)

### Processing Pipeline

1. **Dequeue**: Lấy batch attachments từ queue, mark status = 'processing'
2. **Download**: Stream download từ Jira (dùng JiraRestClient.downloadAttachment)
3. **Extract Text**: Dựa vào mime_type:
   - `application/pdf` → Apache PDFBox extract text
   - `application/vnd.openxmlformats-officedocument.wordprocessingml.document` → Apache POI extract text
   - `image/*` → OCR (Tesseract hoặc skip nếu chưa có)
   - `text/*` → Read directly
   - Unsupported types → skip, mark as 'skipped'
4. **Vectorize & Ingest**: 
   - Format: Title = "{issue_key} - {filename}", Content = extracted text
   - Tags: attachment, issue_key, mime_type
   - Ingest vào KB vector DB
5. **Mark Complete**: Update status = 'completed', processed_at = NOW()

### Error Handling

- Download failure → retry 3 times, then mark 'failed'
- Extract failure → mark 'failed' with error message
- Ingest failure → retry, then mark 'failed'
- Failed items can be retried manually (reset status to 'pending')

### Rate Limiting

- Max concurrent downloads: 3 (Semaphore)
- Respect Jira rate limits (delegate to JiraRestClient)
- Backoff khi queue empty (exponential up to 5 minutes)

### Configuration

```yaml
attachmentProcessor:
  enabled: true
  batchSize: 5
  pollInterval: 30s
  maxConcurrentDownloads: 3
  maxRetries: 3
  supportedMimeTypes:
    - application/pdf
    - application/vnd.openxmlformats-officedocument.wordprocessingml.document
    - text/plain
    - text/markdown
```

## Acceptance Criteria

- [ ] Background worker polls queue và processes attachments
- [ ] PDF text extraction hoạt động
- [ ] DOCX text extraction hoạt động
- [ ] Text files read directly
- [ ] Unsupported types skipped gracefully
- [ ] Failed items marked với error message
- [ ] Rate limiting (max concurrent downloads)
- [ ] Graceful shutdown (finish current batch, stop polling)
- [ ] Unit tests cho extraction logic
- [ ] Integration test với sample files

## Story Points: 8

## Dependencies

- **Blocked by:** Story 1 (Database Schema) — cần attachment_queue table
- **Blocked by:** Story 2 (Jira REST Client) — cần downloadAttachment
