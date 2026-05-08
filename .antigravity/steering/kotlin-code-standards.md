---
name: Kotlin Code Standards
description: Bộ quy tắc và tiêu chuẩn lập trình Kotlin áp dụng cho toàn bộ dự án .
---

# Kotlin Code Standards

Tuân thủ nghiêm ngặt các quy tắc sau để đảm bảo chất lượng code và tính nhất quán.

## 1. Cấu trúc File & Class

- Một file nên chứa một class chính hoặc một tập hợp các functions/properties liên quan chặt chẽ.
- Sử dụng PascalCase cho tên Class và camelCase cho tên biến/function.
- Thứ tự: Properties -> Init blocks -> Constructors -> Functions.

## 2. Xử lý lỗi (Error Handling)

- Ưu tiên sử dụng `Result<T>` hoặc custom sealed classes thay vì ném exception.
- Luôn có log chi tiết cho các trường hợp lỗi.

## 3. UI Components (React/KotlinJS)

- Sử dụng Functional Components và Hooks.
- Tách biệt logic xử lý dữ liệu và UI rendering.
- Sử dụng CSS-in-JS hoặc Tailwind classes theo quy chuẩn dự án.

## 4. Kiểm thử (Testing)

- Mọi logic nghiệp vụ mới đều phải có Unit Test đi kèm.
- Sử dụng MockK để giả lập các dependencies.
---
