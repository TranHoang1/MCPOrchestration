---
name: Validate Draw.io XML
description: Tự động kiểm tra và sửa lỗi XML draw.io sau khi tạo file mới.
enabled: true
version: 1
when:
  type: fileCreated
  patterns:
    - "**/*.drawio"
then:
  type: askAgent
  prompt: >
    Một file .drawio vừa được tạo. Hãy kiểm tra các lỗi phổ biến:
    1. **Self-call 3-waypoint bug**: Xóa waypoint thứ 3 nếu source và target trùng nhau.
    2. **Self-closing edges**: Đảm bảo mọi cell edge="1" đều có child mxGeometry.
    3. **mxfile wrapper**: Loại bỏ tag <mxfile>, chỉ giữ lại <mxGraphModel>.
    4. **Alt box without arrow**: Thêm dashed return arrow nếu có alt box trống.
    
    Nếu tìm thấy lỗi: Sửa file ngay lập tức và xuất lại PNG bằng draw.io CLI.
    Nếu không có lỗi: Không làm gì cả.
---
