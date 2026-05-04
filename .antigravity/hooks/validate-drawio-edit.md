---
name: Validate Draw.io Edit
description: Tự động kiểm tra và sửa lỗi XML draw.io sau khi chỉnh sửa file.
enabled: true
version: 1
when:
  type: fileEdited
  patterns:
    - "**/*.drawio"
then:
  type: askAgent
  prompt: >
    Một file .drawio vừa được chỉnh sửa. Hãy kiểm tra các lỗi phổ biến (Self-call 3-waypoint, Self-closing edges, mxfile wrapper).
    
    Nếu tìm thấy lỗi: Sửa file ngay lập tức và xuất lại PNG.
    Nếu không có lỗi: Không làm gì cả.
---
