---
name: Code Intelligence Skill
description: Hướng dẫn cách sử dụng công cụ phân tích mã nguồn và tra cứu tri thức codebase.
---

# Code Intelligence Skill

Sử dụng bộ công cụ này để hiểu sâu về codebase mà không cần đọc từng file thủ công.

## Tìm kiếm tri thức

1. **mcp_knowledge_base_kb_search**: Tìm kiếm theo từ khóa hoặc ngữ nghĩa trong toàn bộ chỉ mục mã nguồn.
2. **mcp_knowledge_base_kb_get_details**: Lấy thông tin chi tiết về một class, function hoặc module cụ thể.

## Quy trình phân tích

- Luôn kiểm tra KB trước khi thực hiện thay đổi code lớn.
- Sử dụng kết quả từ `kb_search` để xác định các file liên quan cần chỉnh sửa.
- Nếu thông tin trong KB đã cũ (kiểm tra `last_indexed` timestamp), yêu cầu chạy `code-index-full` hook.
---
