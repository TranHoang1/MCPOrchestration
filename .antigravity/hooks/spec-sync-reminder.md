---
name: Spec Sync Reminder
description: Sau khi hoàn thành code, kiểm tra và cập nhật tài liệu spec (BRD/FSD/TDD) để đồng bộ với thực tế code.
enabled: true
version: 1
when:
  type: agentStop
then:
  type: askAgent
  prompt: >
    Kiểm tra xem các thay đổi vừa thực hiện có liên quan đến spec nào không.
    Nếu có, hãy cập nhật requirements.md (BRD), functional.md (FSD) hoặc technical.md (TDD) để phản ánh đúng thực tế mã nguồn.
    Chỉ cập nhật nếu có sự sai khác ý nghĩa. Không cần cập nhật tasks.md.
---
