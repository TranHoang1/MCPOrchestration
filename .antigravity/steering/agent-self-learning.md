---
name: agent-self-learning
description: Quy tắc self-learning cho tất cả agents. Agents phải search KB trước khi làm task, và ingest kinh nghiệm mới vào KB sau khi hoàn thành.
inclusion: auto
---

# Agent Self-Learning & Tool Discovery

## ⛔ Quy tắc #1: Tìm hiểu giải pháp hiện có TRƯỚC KHI hành động

Trước khi giải quyết bất kỳ vấn đề nào, PHẢI thực hiện 3 bước:

1. **Search KB** — `kb_search("<mô tả vấn đề>")` → Nếu có pattern proven → dùng ngay
2. **Search Documents** — `grep_search("<keyword>", includePattern="documents/**/*.md")` → Nếu có design → tuân thủ
3. **Search Code** — `grep_search("<class/pattern>", includePattern="**/*.kt")` → Nếu có implementation → tái sử dụng

**CHỈ khi cả 3 bước không tìm thấy gì**, mới được đề xuất giải pháp mới.

## ⛔ Quy tắc #2: Tool Discovery — KHÔNG hardcode

Khi cần gọi external tool:
1. Dùng `find_tools(query="<mô tả chức năng>")` để discover
2. Đọc `input_schema` từ kết quả
3. Gọi `execute_dynamic_tool(tool_name, arguments)` theo schema
4. Nếu không tìm thấy → báo user, đề xuất alternative

**KHÔNG BAO GIỜ** hardcode tool names, CLI commands, hoặc giả định tool tồn tại.

## ⛔ Quy tắc #3: Ingest kinh nghiệm mới vào KB

Sau khi hoàn thành task bằng phương pháp mới (KB chưa có), PHẢI ingest:

```
kb_ingest(title="<task + phương pháp>", content="<steps, tools, gotchas>", tags="<agent>,<category>,proven-pattern")
```

Ingest khi: tìm được tool combination mới, fix được error, phát hiện giải pháp hiện có mà trước đó không biết.
KHÔNG ingest: task obvious, đã có trong KB, hoặc task failed.

## ⛔ Quy tắc #4: Chống giải pháp manh mún

1. **KHÔNG tạo wrapper/helper mới** nếu hệ thống đã có mechanism (dù đang broken → fix root cause)
2. **KHÔNG bypass** bằng workaround khi root cause có thể fix
3. **Mọi giải pháp mới PHẢI tương thích** architecture hiện có (đọc TDD/FSD trước nếu không chắc)
4. **KB offline ≠ bỏ qua tìm hiểu** — vẫn PHẢI search documents và code
