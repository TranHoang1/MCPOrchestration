---
name: Spec Synchronization Skill
description: Hướng dẫn đồng bộ tài liệu đặc tả (Spec) với thực tế mã nguồn.
---

# Spec Synchronization Skill

Đảm bảo tài liệu luôn phản ánh đúng những gì đã được code.

## Khi nào cần đồng bộ?

- Sau khi hoàn thành một task lớn hoặc sửa bug.
- Khi có thay đổi về API, Database Schema hoặc UI flow.

## Cách thực hiện

1. **Kiểm tra sai khác**: So sánh code hiện tại với mô tả trong BRD/FSD/TDD.
2. **Cập nhật BRD (requirements.md)**: Sửa đổi các yêu cầu nếu logic nghiệp vụ thay đổi.
3. **Cập nhật FSD (functional.md)**: Cập nhật luồng xử lý và màn hình UI.
4. **Cập nhật TDD (technical.md)**: Cập nhật API endpoints, class diagrams và sequence diagrams.

---
