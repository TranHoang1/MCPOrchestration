---
name: mcp-tool-discovery
description: Hướng dẫn agents cách tự khám phá và sử dụng MCP tools mà không hardcode tool names. Áp dụng cho tất cả agents cần gọi external tools.
inclusion: manual
---

# MCP Tool Discovery — Agent Instructions

## Nguyên tắc cốt lõi

**KHÔNG BAO GIỜ hardcode tên tool.** Agents phải tự khám phá tools có sẵn trong môi trường hiện tại.

## Quy trình Tool Discovery

### Bước 1: Khám phá môi trường (Discovery Phase)

Khi bắt đầu workflow, trước khi thực hiện bất kỳ action nào cần external tool, agent PHẢI:

1. **Xác định capabilities cần thiết** — liệt kê các khả năng cần dùng cho task hiện tại. Ví dụ:
   - "Tôi cần: đọc Jira ticket, lưu vào knowledge base, export markdown sang DOCX"
   
2. **Tìm tools theo intent** — Dùng `find_tools` với mô tả chức năng (KHÔNG phải tên tool):
   ```
   find_tools(query: "get jira issue ticket details")
   find_tools(query: "store data knowledge base")
   find_tools(query: "export markdown to word docx")
   ```

3. **Ghi nhớ kết quả** — Lưu mapping: intent → tool_name + server_name + input_schema. Dùng mapping này cho toàn bộ session.

4. **Nếu không tìm thấy tool** — Hạ threshold xuống 0.3, thử query khác. Nếu vẫn không có → báo user rằng capability đó không available, đề xuất alternative (manual steps hoặc workaround).

### Bước 2: Sử dụng tool (Execution Phase)

Khi cần gọi tool:

1. **Tra cứu mapping** từ Discovery Phase
2. **Gọi `execute_dynamic_tool`** với tool_name và arguments đúng schema
3. **Xử lý lỗi** — nếu tool fail, thử tìm lại (tool có thể đã thay đổi)

### Bước 3: Fallback khi tool không tồn tại

Nếu một capability không có tool nào match:

| Capability cần | Fallback |
|---------------|----------|
| Đọc Jira ticket | Hỏi user cung cấp thông tin ticket manually |
| Lưu knowledge base | Lưu vào file local thay thế |
| Export DOCX | Tạo markdown, báo user export manual |
| Query database | Hỏi user chạy query và paste kết quả |
| Browser automation | Hướng dẫn user test manual |

## Ví dụ Discovery cho từng domain

### Jira / Project Management
```
find_tools(query: "get issue details from project tracker")
find_tools(query: "search issues with query")
find_tools(query: "transition issue status")
find_tools(query: "add comment to issue")
find_tools(query: "get issue attachments")
find_tools(query: "add attachment to issue")
```

### Knowledge Base / Storage
```
find_tools(query: "store document in knowledge base")
find_tools(query: "search knowledge base")
find_tools(query: "write entry to knowledge base")
```

### Document Export
```
find_tools(query: "convert markdown to docx word document")
find_tools(query: "export to excel spreadsheet")
```

### Database
```
find_tools(query: "execute SQL query on database")
find_tools(query: "list database schemas")
find_tools(query: "get table column details")
find_tools(query: "analyze database health indexes")
```

### Browser / UI Testing
```
find_tools(query: "navigate browser to URL")
find_tools(query: "click element in browser")
find_tools(query: "take browser screenshot")
find_tools(query: "type text in browser input")
```

### File Conversion
```
find_tools(query: "convert file to markdown")
```

## Quy tắc quan trọng

1. **Discovery chỉ chạy 1 lần** đầu workflow — không tìm lại mỗi lần gọi tool
2. **Nếu find_tools trả về nhiều tools cùng chức năng** — chọn tool có similarity_score cao nhất
3. **Nếu find_tools trả về tool từ nhiều servers** — ưu tiên server có status CONNECTED
4. **Ghi log discovery results** — để debug khi tool không hoạt động
5. **KHÔNG giả định tool tồn tại** — luôn verify qua discovery trước khi gọi
6. **Khi tool fail** — retry 1 lần với cùng params, nếu vẫn fail → báo user
