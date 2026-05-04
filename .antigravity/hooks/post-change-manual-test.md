---
name: Verify Implementation After Completion
description: Sau khi hoàn thành code, yêu cầu agent xác minh trên localhost:3000.
enabled: true
version: 2.0.0
when:
  type: agentStop
then:
  type: askAgent
  prompt: >
    Nếu bạn vừa hoàn thành tất cả các task trong một spec hoặc sửa xong một bug ảnh hưởng đến UI/API:
    1. Biên dịch các module bị ảnh hưởng (./gradlew).
    2. Khởi động lại server.
    3. Gọi @sm-agent (hoặc agent phụ trách xác minh) để kiểm tra trên localhost:3000.
    4. Mở trình duyệt, đăng nhập, kiểm tra từng tiêu chí chấp nhận (AC), chụp ảnh màn hình.
    5. Nếu có lỗi: Sửa code và kiểm tra lại cho đến khi PASS.
    
    Bỏ qua nếu chỉ chỉnh sửa tài liệu.
---
