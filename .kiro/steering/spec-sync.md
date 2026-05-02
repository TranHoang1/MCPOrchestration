---
inclusion: auto
description: Tự động cập nhật spec documents (requirements, design) sau khi hoàn thành thay đổi code
---

# Spec Sync — Cập nhật Requirements & Design sau thay đổi

## Quy tắc bắt buộc

Sau khi hoàn thành bất kỳ thay đổi code nào liên quan đến một spec đã có trong `.kiro/specs/`, PHẢI cập nhật các documents tương ứng để phản ánh trạng thái thực tế của code.

## Khi nào áp dụng

- Khi user yêu cầu thay đổi trực tiếp (không qua spec workflow) mà ảnh hưởng đến feature đã có spec
- Khi fix bug hoặc cải thiện UX cho feature đã có spec
- Khi thêm/sửa/xóa acceptance criteria trong quá trình implement
- Khi thay đổi kiến trúc hoặc design decisions

## Cần cập nhật gì

### requirements.md
- Thêm/sửa acceptance criteria nếu behavior thay đổi
- Cập nhật user stories nếu scope mở rộng
- Cập nhật glossary nếu có khái niệm mới
- Đánh dấu requirements đã bị thay đổi so với bản gốc

### design.md
- Cập nhật component interfaces nếu API thay đổi
- Cập nhật data models nếu có fields mới
- Cập nhật architecture diagrams nếu flow thay đổi
- Cập nhật error handling nếu có cases mới

### tasks.md
- KHÔNG cập nhật tasks đã hoàn thành (giữ nguyên lịch sử)
- Có thể thêm tasks mới nếu cần follow-up

## Cách xác định spec liên quan

- Kiểm tra file đang sửa thuộc module/feature nào
- Tìm spec tương ứng trong `.kiro/specs/`
- Nếu không có spec → không cần cập nhật

## Ví dụ

Khi sửa `GraphFilterPanel.kt` để thêm dynamic node types:
1. Tìm spec: `.kiro/specs/graph-filter-focus-mode/`
2. Cập nhật `requirements.md`: sửa Requirement 1 từ hardcoded 4 types → dynamic từ backend
3. Cập nhật `design.md`: thêm `NodeTypeInfo` data model, cập nhật `GraphFilterPanel` interface
